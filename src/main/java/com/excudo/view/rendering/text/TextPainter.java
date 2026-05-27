package com.excudo.view.rendering.text;

import com.excudo.core.metrics.MeasuredText;
import com.excudo.core.model.*;
import com.excudo.core.model.BulletType;
import com.excudo.core.themes.TextLevelStyle;
import com.excudo.view.rendering.CoordinateMapper;
import com.excudo.view.rendering.RenderingContext;
import com.excudo.view.rendering.ShapeStyleExtractor;
import com.excudo.view.rendering.SlideRenderContext;
import com.excudo.core.rendering.surface.BulletFontMapper;
import com.excudo.core.rendering.surface.RenderSurface;
import com.excudo.core.rendering.surface.SurfaceFont;
import com.excudo.core.rendering.surface.SurfacePaint;
import javafx.geometry.Rectangle2D;

import java.util.ArrayList;
import java.util.List;

/**
 * Paints pre-measured text onto a {@link RenderSurface}.
 * Does NOT extract text from DOM or measure text layout. Only draws.
 *
 * Pipeline:
 *   TextBodyExtractor.extract(xmlElement) -> TextBody
 *   TextMeasurer.measure(textBody, widthEmu) -> MeasuredText
 *   TextPainter.paint(textBody, measuredText, bounds, ctx, slideCtx)
 *
 * As of the Bridge refactor, the only JavaFX-specific state this class
 * touches is (a) {@link Color} as an input type from
 * {@link ShapeStyleExtractor} -- converted to {@link SurfacePaint} via
 * the Phase-B adapter -- and (b) {@link Rectangle2D} / {@link javafx.geometry.Point2D}
 * which are pure-geometry types without rendering implications. The
 * JavaFX Font cache and JavaFX-Text-node width measurement cache that
 * used to live here migrated into {@link com.excudo.view.rendering.surface.CanvasRenderSurface}.
 */
public final class TextPainter {

    private TextPainter() {}

    /**
     * Paint with default placeholder type detection.
     */
    public static void paint(TextBody textBody, MeasuredText measured,
                             Rectangle2D boundsPixels, RenderingContext ctx,
                             SlideRenderContext slideCtx) {
        paint(textBody, measured, boundsPixels, ctx, slideCtx, null);
    }

    /**
     * Paint a TextBody within the given pixel bounds.
     *
     * @param placeholderType "title", "ctrTitle", "body", "subTitle", "obj", or null
     */
    public static void paint(TextBody textBody, MeasuredText measured,
                             Rectangle2D boundsPixels, RenderingContext ctx,
                             SlideRenderContext slideCtx, String placeholderType) {
        if (textBody == null || measured == null || boundsPixels == null) return;

        RenderSurface surface = ctx.getSurface();
        // Composite scale (canvas-fit * user-zoom). Matches what
        // mapToCanvas applies to shape geometry, so font sizes, insets
        // and indents track the shape rect when the viewport resizes.
        double zoom = ctx.getZoomedCoordinateMapper().getEffectiveScale();

        // Body property insets (default: 91440 left/right, 45720 top/bottom EMU)
        BodyProperties bp = textBody.getBodyProperties();
        double leftInsetPx = emuToPixels(bp != null && bp.getLeftInset() != null
            ? bp.getLeftInset() : 91440) * zoom;
        double topInsetPx = emuToPixels(bp != null && bp.getTopInset() != null
            ? bp.getTopInset() : 45720) * zoom;
        double rightInsetPx = emuToPixels(bp != null && bp.getRightInset() != null
            ? bp.getRightInset() : 91440) * zoom;

        double bottomInsetPx = emuToPixels(bp != null && bp.getBottomInset() != null
            ? bp.getBottomInset() : 45720) * zoom;

        // Vertical text: rotate canvas 90 degrees and swap layout dimensions
        String vertText = bp != null ? bp.getVerticalText() : null;
        boolean isVertical = "vert".equals(vertText) || "eaVert".equals(vertText)
                          || "vert270".equals(vertText);
        if (isVertical) {
            surface.save();
            double cx = boundsPixels.getMinX() + boundsPixels.getWidth() / 2;
            double cy = boundsPixels.getMinY() + boundsPixels.getHeight() / 2;
            surface.translate(cx, cy);
            surface.rotate("vert270".equals(vertText) ? -90 : 90);
            surface.translate(-cx, -cy);
            // Swap effective width/height for text layout
            double halfW = boundsPixels.getWidth() / 2;
            double halfH = boundsPixels.getHeight() / 2;
            boundsPixels = new Rectangle2D(
                cx - halfH, cy - halfW, boundsPixels.getHeight(), boundsPixels.getWidth());
            // Recompute insets for swapped dimensions
            double tmp = leftInsetPx; leftInsetPx = topInsetPx; topInsetPx = tmp;
            tmp = rightInsetPx; rightInsetPx = bottomInsetPx; bottomInsetPx = tmp;
        }

        double startX = boundsPixels.getMinX() + leftInsetPx;
        double startY = boundsPixels.getMinY() + topInsetPx;
        double maxWidth = boundsPixels.getWidth() - leftInsetPx - rightInsetPx;

        List<TextParagraph> paragraphs = textBody.getParagraphs();
        List<MeasuredText.ParagraphMeasurement> measurements = measured.getParagraphs();

        // Autofit: shrink text proportionally if it exceeds available height
        if (bp != null && bp.getAutofit() == AutofitType.NORMAL) {
            double totalTextHeightPx = emuToPixels(measured.getTotalHeightEmu()) * zoom;
            double availableHeight = boundsPixels.getHeight() - topInsetPx - bottomInsetPx;
            if (totalTextHeightPx > availableHeight && totalTextHeightPx > 0) {
                double scale = bp.getFontScale() != null
                    ? bp.getFontScale() / 100000.0
                    : availableHeight / totalTextHeightPx;
                zoom *= Math.max(scale, 0.3); // floor at 30% to prevent invisible text
            }
        }

        // Vertical alignment: offset startY for center/bottom anchors.
        // When the shape's own bodyPr omits the anchor attribute, ECMA-376
        // (MS-OI29500 section 2.1.1379) requires walking layout -> master
        // to inherit it. Without this, title placeholders authored with
        // negative-Y offsets render clipped against the slide's top edge
        // because the master's anchor="ctr" is ignored.
        String vAlign = bp != null ? bp.getVerticalAlignment() : null;
        if (vAlign == null && placeholderType != null && slideCtx != null) {
            vAlign = slideCtx.resolveInheritedBodyPrAnchor(placeholderType, null);
        }
        if ("ctr".equals(vAlign) || "b".equals(vAlign)) {
            double totalTextHeightPx = emuToPixels(measured.getTotalHeightEmu()) * zoom;
            double availableHeight = boundsPixels.getHeight() - topInsetPx - bottomInsetPx;
            if (totalTextHeightPx < availableHeight) {
                if ("ctr".equals(vAlign)) {
                    startY += (availableHeight - totalTextHeightPx) / 2;
                } else {
                    startY += availableHeight - totalTextHeightPx;
                }
            }
        }

        double currentY = startY;

        // Auto-number bullet counter. Restarts when a non-AUTONUMBER
        // paragraph breaks the sequence (PowerPoint behaviour: "1. 2. 3.
        // [plain paragraph] 1. 2." rather than continuing through).
        int autoNumCounter = 0;

        for (int i = 0; i < paragraphs.size() && i < measurements.size(); i++) {
            TextParagraph para = paragraphs.get(i);
            MeasuredText.ParagraphMeasurement pm = measurements.get(i);

            if (para.isEmpty()) {
                currentY += emuToPixels(pm.getHeightEmu()) * zoom;
                continue;
            }

            int paraNumber = 0;
            if (para.getBulletType() == BulletType.AUTONUMBER) {
                autoNumCounter++;
                paraNumber = autoNumCounter;
            } else {
                autoNumCounter = 0;
            }

            boolean isTitle = "title".equals(placeholderType) || "ctrTitle".equals(placeholderType);
            currentY = paintParagraph(para, pm, startX, currentY, maxWidth, surface, slideCtx, zoom,
                placeholderType, isTitle, paraNumber);
        }

        if (isVertical) {
            surface.restore();
        }
    }

    /**
     * Paint a single paragraph. Returns the Y position after this paragraph.
     */
    private static double paintParagraph(TextParagraph para, MeasuredText.ParagraphMeasurement pm,
                                          double startX, double startY, double maxWidth,
                                          RenderSurface surface, SlideRenderContext slideCtx,
                                          double zoom, String placeholderType, boolean isTitle,
                                          int autoNumber) {
        int level = para.getLevel();

        // Use title style or body style based on placeholder type
        TextLevelStyle themeStyle = null;
        if (slideCtx != null) {
            themeStyle = isTitle ? slideCtx.getTitleStyle(level) : slideCtx.getBodyStyle(level);
        }

        // OOXML inheritance: theme paragraph styles (margin / indent /
        // spacing) only apply to placeholder shapes. A plain RECTANGLE or
        // text box that doesn't set its own marL inherits NOTHING from
        // theme -- defaults are zero, not the theme's body-style margin.
        // Without this guard a 13pt-wide text box on a slide with a 12pt
        // theme body margin had its text pushed past the right edge.
        // Same pattern as the phantom-bullet inheritance fix.
        boolean isPlaceholder = placeholderType != null;

        // Resolve marL and indent INDEPENDENTLY. The old code only read
        // para.getMarginLeft() and left indentPx=0 whenever the paragraph
        // overrode marL, so explicit <a:pPr marL=X indent=-X> on a
        // placeholder body paragraph lost its hanging indent and the
        // bullet landed at textX on top of the first letter. Theme
        // fallback still only applies to placeholders per OOXML scoping.
        double marginLeftPx = 0;
        double indentPx = 0;
        if (para.getMarginLeft() != null) {
            marginLeftPx = emuToPixels(para.getMarginLeft()) * zoom;
        } else if (themeStyle != null && isPlaceholder) {
            marginLeftPx = emuToPixels(themeStyle.getMarginLeft()) * zoom;
        }
        if (para.getIndent() != null) {
            indentPx = emuToPixels(para.getIndent()) * zoom;
        } else if (themeStyle != null && isPlaceholder) {
            indentPx = emuToPixels(themeStyle.getIndent()) * zoom;
        }

        // Space before
        double spaceBeforePx = 0;
        if (para.getSpaceBefore() != null && para.getSpaceBefore() > 0) {
            spaceBeforePx = (para.getSpaceBefore() / 100.0) * zoom;
        } else if (themeStyle != null && isPlaceholder && themeStyle.getSpaceBefore() > 0) {
            spaceBeforePx = (themeStyle.getSpaceBefore() / 100.0) * zoom;
        }

        double currentY = startY + spaceBeforePx;
        double textX = startX + marginLeftPx;

        // Render bullet character if present
        if (para.getBulletType() == BulletType.CHARACTER
            || (para.getBulletType() == BulletType.INHERITED && isPlaceholder)) {
            String bulletChar = para.getBulletChar();
            String bulletFontFamily = para.getBulletFont();

            // For INHERITED bullets, always look up from theme
            if (para.getBulletType() == BulletType.INHERITED
                && isPlaceholder && themeStyle != null && themeStyle.hasBullet()) {
                bulletChar = themeStyle.getBulletChar();
                bulletFontFamily = themeStyle.getBulletFont();
            }
            // Theme-level bullet inheritance (paragraph doesn't specify its own).
            // Still gated on placeholder so a non-placeholder shape that
            // happens to have CHARACTER-type bullet override still renders it,
            // but one without any explicit bullet doesn't leak theme bullets.
            if (bulletChar == null && isPlaceholder
                && themeStyle != null && themeStyle.hasBullet()) {
                bulletChar = themeStyle.getBulletChar();
                if (bulletFontFamily == null && themeStyle.getBulletFont() != null) {
                    bulletFontFamily = themeStyle.getBulletFont();
                }
            }
            if (bulletChar != null) {
                // Wingdings / Wingdings 2 / Wingdings 3 / Symbol bullets are
                // stored in OOXML as ASCII-range codepoints that only render
                // correctly when the corresponding symbol font is installed.
                // On Linux hosts (and headless CI) those fonts usually aren't
                // present, so a literal 'l' renders as the letter "l" instead
                // of "●". Translate to Unicode equivalents and render in the
                // body font, which has the geometric/check/cross glyphs
                // natively. PowerPoint-style appearance, host-font-independent.
                String renderedBulletChar = bulletChar;
                String renderedBulletFontFamily = bulletFontFamily;
                if (BulletFontMapper.isSymbolFont(bulletFontFamily)) {
                    renderedBulletChar = BulletFontMapper.translate(bulletFontFamily, bulletChar);
                    renderedBulletFontFamily = null; // fall through to body font
                }

                SurfaceFont bulletFont;
                double bulletSizePt = themeStyle != null ? themeStyle.getFontSize() / 100.0 : 18.0;
                if (renderedBulletFontFamily != null && !renderedBulletFontFamily.isEmpty()) {
                    bulletFont = SurfaceFont.of(renderedBulletFontFamily, bulletSizePt * zoom);
                } else {
                    bulletFont = resolveFont(null, slideCtx, zoom, level, isTitle);
                }
                surface.setFont(bulletFont);
                SurfacePaint textColor = resolveDefaultTextColor(slideCtx, placeholderType);
                surface.setFill(textColor);
                surface.fillText(renderedBulletChar, textX + indentPx, currentY + bulletFont.sizePx());
            }
        } else if (para.getBulletType() == BulletType.AUTONUMBER && autoNumber > 0) {
            // Auto-numbered list. PowerPoint renders these as "1.", "2.",
            // "(1)", "i.", etc. depending on autonumType. Without this
            // branch, code blocks with line numbers (auto-numbered
            // paragraphs) rendered with no number prefix at all.
            String numberStr = formatAutoNumber(autoNumber, para.getAutonumType());
            SurfaceFont numberFont = resolveFont(null, slideCtx, zoom, level, isTitle);
            surface.setFont(numberFont);
            SurfacePaint textColor = resolveDefaultTextColor(slideCtx, placeholderType);
            surface.setFill(textColor);
            surface.fillText(numberStr, textX + indentPx, currentY + numberFont.sizePx());
        }

        // Per OOXML: marL is where the TEXT starts (from the shape's text
        // area left edge), and indent is the FIRST-LINE offset relative to
        // marL. textX already encodes startX + marL, so the text x-pos is
        // just textX -- adding marginLeftPx again here pushed text by 2*marL,
        // which on narrow shapes positioned text past the right edge.
        // Bullets render at textX + indentPx (negative indent = bullet
        // before text in a hanging-indent layout).
        double runX = textX;

        // Resolve line height from measurements
        double lineHeightPx = pm.getLineCount() > 0
            ? (emuToPixels(pm.getHeightEmu()) * zoom) / pm.getLineCount()
            : 16.0 * zoom;

        // Available width for text wrapping (text area right edge minus textX).
        // textX is at startX + marL, so what's left is maxWidth - marL.
        // marR pulls the wrap boundary in from the right edge (symmetric
        // to marL); on hanging indent it still applies, unlike marL which
        // only affects non-hanging paragraphs.
        double marginRightPx = para.getMarginRight() != null
            ? emuToPixels(Math.max(0, para.getMarginRight())) * zoom
            : 0;
        double availableWidth = maxWidth - marginLeftPx - marginRightPx;
        if (indentPx < 0) {
            availableWidth = maxWidth - marginRightPx; // Hanging indent: text area is full width on the left, still bounded on the right
        }

        // --- Pass 1: collect words into physical lines ---
        // Each "word segment" tracks its text, font, color, run, and width.
        record WordSegment(String text, SurfaceFont font, SurfacePaint color, TextRun run, double width) {}
        List<List<WordSegment>> lines = new ArrayList<>();
        List<WordSegment> currentLine = new ArrayList<>();
        double currentLineWidth = 0;

        for (TextRun run : para.getRuns()) {
            String runText = run.getDisplayText();
            if (runText == null || runText.isEmpty()) continue;

            SurfaceFont font = resolveFont(run, slideCtx, zoom, level, isTitle);
            surface.setFont(font);
            SurfacePaint color = ShapeStyleExtractor.resolveTextRunColor(run, placeholderType, slideCtx);

            String[] words = runText.split("(?<=\\s)");
            for (String word : words) {
                // Width comes from the surface -- same Font object that
                // will render the text, so measurement tracks rendering.
                double wordWidth = surface.measureAdvance(word);

                if (currentLineWidth + wordWidth > availableWidth && !currentLine.isEmpty()) {
                    lines.add(currentLine);
                    currentLine = new ArrayList<>();
                    currentLineWidth = 0;
                }

                currentLine.add(new WordSegment(word, font, color, run, wordWidth));
                currentLineWidth += wordWidth;
            }
        }
        if (!currentLine.isEmpty()) {
            lines.add(currentLine);
        }

        // --- Pass 2: draw lines with alignment ---
        String alignment = para.getAlignment();
        double lineY = currentY;

        for (List<WordSegment> line : lines) {
            double lineWidth = line.stream().mapToDouble(WordSegment::width).sum();

            // Compute X offset based on alignment
            double lineX;
            if ("ctr".equals(alignment)) {
                lineX = runX + (availableWidth - lineWidth) / 2;
            } else if ("r".equals(alignment)) {
                lineX = runX + availableWidth - lineWidth;
            } else {
                lineX = runX; // left or justify (treat justify as left for now)
            }

            for (WordSegment seg : line) {
                surface.setFont(seg.font());

                // Highlight background (drawn before text)
                if (seg.run().getHighlight() != null) {
                    TextColor hlColor = seg.run().getHighlight();
                    SurfacePaint hl = hlColor.getHexVal() != null
                        ? SurfacePaint.Solid.fromHex(hlColor.getHexVal())
                        : SurfacePaint.Solid.rgb(255, 255, 0); // YELLOW
                    surface.setFill(hl);
                    surface.fillRect(lineX, lineY, seg.width(), seg.font().sizePx() + 2);
                }

                // Baseline shift: rPr/@baseline is percent*1000 of the
                // font size. Positive = superscript (y up = negative
                // screen delta); negative = subscript (y down = positive
                // screen delta). Decorations (underline, strikethrough)
                // shift with the text so they land relative to the run,
                // not the paragraph baseline.
                Integer baselineAttr = seg.run().getBaseline();
                double baselineShift = baselineAttr != null
                    ? -baselineAttr / 100000.0 * seg.font().sizePx()
                    : 0.0;

                surface.setFill(seg.color());
                surface.fillText(seg.text(), lineX, lineY + seg.font().sizePx() + baselineShift);

                // Underline
                if (seg.run().getUnderline() != null && !"none".equals(seg.run().getUnderline())) {
                    double underlineY = lineY + seg.font().sizePx() + 2 + baselineShift;
                    surface.setStroke(seg.color());
                    surface.setLineWidth("heavy".equals(seg.run().getUnderline()) ? 2.0 : 1.0);
                    surface.strokeLine(lineX, underlineY, lineX + seg.width(), underlineY);
                }

                // Strikethrough (sngStrike or dblStrike)
                if (seg.run().getStrikethrough() != null) {
                    double strikeY = lineY + seg.font().sizePx() * 0.6 + baselineShift;
                    surface.setStroke(seg.color());
                    surface.setLineWidth(1.0);
                    surface.strokeLine(lineX, strikeY, lineX + seg.width(), strikeY);
                    if ("dblStrike".equals(seg.run().getStrikethrough())) {
                        surface.strokeLine(lineX, strikeY + 2, lineX + seg.width(), strikeY + 2);
                    }
                }

                lineX += seg.width();
            }
            lineY += lineHeightPx;
        }

        // Advance Y by the actual rendered height (at least one line)
        double renderedHeight = Math.max(lineHeightPx, lines.size() * lineHeightPx);
        currentY += renderedHeight;

        // Space after paragraph
        if (para.getSpaceAfter() != null && para.getSpaceAfter() > 0) {
            currentY += (para.getSpaceAfter() / 100.0) * zoom;
        }

        return currentY;
    }

    /**
     * Resolve a SurfaceFont descriptor from run properties + theme
     * fallback. Backend caching (JavaFX Font resolution + the
     * substitution-check loop on Linux hosts without the requested
     * family) lives inside {@link com.excudo.view.rendering.surface.CanvasRenderSurface}.
     */
    private static SurfaceFont resolveFont(TextRun run, SlideRenderContext slideCtx,
                                            double zoom, int level, boolean isTitle) {
        String family = null;
        double sizePt = 18.0;
        boolean bold = false;
        boolean italic = false;

        if (run != null) {
            if (run.getFontFamily() != null) family = run.getFontFamily();
            if (run.getFontSize() != null) sizePt = run.getFontSize() / 100.0;
            if (run.getBold() != null) bold = run.getBold();
            if (run.getItalic() != null) italic = run.getItalic();
        }

        if (slideCtx != null) {
            TextLevelStyle style = isTitle ? slideCtx.getTitleStyle(level) : slideCtx.getBodyStyle(level);
            if (style != null) {
                if (run == null || run.getFontSize() == null) {
                    sizePt = style.getFontSize() / 100.0;
                }
                if (run == null || run.getBold() == null) {
                    bold = style.isBold();
                }
            }
            if (family == null) {
                family = isTitle ? slideCtx.getMajorFont() : slideCtx.getMinorFont();
            }
        }

        if (family == null) {
            throw new IllegalStateException("No font family resolved: run.fontFamily="
                + (run != null ? run.getFontFamily() : "null") + ", slideCtx="
                + (slideCtx != null) + ", isTitle=" + isTitle);
        }

        double scaledSize = Math.max(1, sizePt * zoom);
        String fallback = slideCtx == null ? null
            : (isTitle ? slideCtx.getMajorFontFallback() : slideCtx.getMinorFontFallback());

        return new SurfaceFont(family, fallback,
            bold ? SurfaceFont.Weight.BOLD : SurfaceFont.Weight.NORMAL,
            italic ? SurfaceFont.Posture.ITALIC : SurfaceFont.Posture.REGULAR,
            scaledSize);
    }

    private static SurfacePaint resolveDefaultTextColor(SlideRenderContext slideCtx, String phType) {
        if (slideCtx == null) return SurfacePaint.Solid.rgb(0, 0, 0);
        String hex = ("title".equals(phType) || "ctrTitle".equals(phType))
            ? slideCtx.getTitleTextColorHex()
            : slideCtx.getBodyTextColorHex();
        if (hex == null || hex.isBlank()) return SurfacePaint.Solid.rgb(0, 0, 0);
        // Tolerate leading '#' in the theme's hex strings.
        return SurfacePaint.Solid.fromHex(hex);
    }

    private static double emuToPixels(long emu) {
        return CoordinateMapper.emuToPixels(emu);
    }

    private static double emuToPixels(int emu) {
        return CoordinateMapper.emuToPixels((long) emu);
    }

    /**
     * Format an auto-number bullet per the OOXML autonumType. Handles the
     * arabic + roman + alpha variants the spec lists; falls back to
     * "{n}." for unknown / null types.
     */
    private static String formatAutoNumber(int n, String autonumType) {
        if (autonumType == null) return n + ".";
        return switch (autonumType) {
            case "arabicPlain"      -> Integer.toString(n);
            case "arabicPeriod"     -> n + ".";
            case "arabicParenR"     -> n + ")";
            case "arabicParenBoth"  -> "(" + n + ")";
            case "romanLcPeriod"    -> toRoman(n).toLowerCase() + ".";
            case "romanUcPeriod"    -> toRoman(n) + ".";
            case "romanLcParenR"    -> toRoman(n).toLowerCase() + ")";
            case "romanUcParenR"    -> toRoman(n) + ")";
            case "alphaLcPeriod"    -> toAlpha(n).toLowerCase() + ".";
            case "alphaUcPeriod"    -> toAlpha(n) + ".";
            case "alphaLcParenR"    -> toAlpha(n).toLowerCase() + ")";
            case "alphaUcParenR"    -> toAlpha(n) + ")";
            default                 -> n + ".";
        };
    }

    private static String toRoman(int n) {
        if (n <= 0 || n >= 4000) return Integer.toString(n);
        int[] values   = { 1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1 };
        String[] syms  = { "M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I" };
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            while (n >= values[i]) { sb.append(syms[i]); n -= values[i]; }
        }
        return sb.toString();
    }

    private static String toAlpha(int n) {
        // 1=A, 26=Z, 27=AA, 28=AB ... (spreadsheet column style)
        StringBuilder sb = new StringBuilder();
        while (n > 0) {
            int rem = (n - 1) % 26;
            sb.insert(0, (char) ('A' + rem));
            n = (n - 1) / 26;
        }
        return sb.toString();
    }
}
