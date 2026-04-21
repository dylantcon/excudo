package com.excudo.view.rendering.text;

import com.excudo.core.metrics.FontData;
import com.excudo.core.metrics.FontResolver;
import com.excudo.core.metrics.MeasuredText;
import com.excudo.core.metrics.TrueTypeFontParser;
import com.excudo.core.model.*;
import com.excudo.core.model.BulletType;
import com.excudo.core.themes.TextLevelStyle;
import com.excudo.view.rendering.CoordinateMapper;
import com.excudo.view.rendering.RenderingContext;
import com.excudo.view.rendering.ShapeStyleExtractor;
import com.excudo.view.rendering.SlideRenderContext;
import javafx.geometry.Rectangle2D;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Paints pre-measured text onto a JavaFX Canvas.
 * Does NOT extract text from DOM or measure text. Only draws.
 *
 * Pipeline:
 *   TextBodyExtractor.extract(xmlElement) -> TextBody
 *   TextMeasurer.measure(textBody, widthEmu) -> MeasuredText
 *   TextPainter.paint(textBody, measuredText, bounds, ctx, slideCtx)
 */
public final class TextPainter {

    private static final double EMU_PER_POINT = 12700.0;

    private TextPainter() {}

    /**
     * Paint a TextBody within the given pixel bounds.
     * Uses MeasuredText for line break positions and heights.
     */
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

        GraphicsContext gc = ctx.getGraphicsContext();
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
            gc.save();
            double cx = boundsPixels.getMinX() + boundsPixels.getWidth() / 2;
            double cy = boundsPixels.getMinY() + boundsPixels.getHeight() / 2;
            gc.translate(cx, cy);
            gc.rotate("vert270".equals(vertText) ? -90 : 90);
            gc.translate(-cx, -cy);
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

        // Vertical alignment: offset startY for center/bottom anchors
        String vAlign = bp != null ? bp.getVerticalAlignment() : null;
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
            currentY = paintParagraph(para, pm, startX, currentY, maxWidth, gc, slideCtx, zoom,
                placeholderType, isTitle, paraNumber);
        }

        if (isVertical) {
            gc.restore();
        }
    }

    /**
     * Paint a single paragraph. Returns the Y position after this paragraph.
     */
    private static double paintParagraph(TextParagraph para, MeasuredText.ParagraphMeasurement pm,
                                          double startX, double startY, double maxWidth,
                                          GraphicsContext gc, SlideRenderContext slideCtx,
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
                // Use the bullet-specific font (Wingdings, Symbol, Arial, etc.)
                Font bulletFont;
                double bulletSizePt = themeStyle != null ? themeStyle.getFontSize() / 100.0 : 18.0;
                if (bulletFontFamily != null && !bulletFontFamily.isEmpty()) {
                    bulletFont = Font.font(bulletFontFamily, bulletSizePt * zoom);
                } else {
                    bulletFont = resolveFont(null, slideCtx, zoom, level, isTitle);
                }
                gc.setFont(bulletFont);
                Color textColor = resolveDefaultTextColor(slideCtx, placeholderType);
                gc.setFill(textColor);
                gc.fillText(bulletChar, textX + indentPx, currentY + bulletFont.getSize());
            }
        } else if (para.getBulletType() == BulletType.AUTONUMBER && autoNumber > 0) {
            // Auto-numbered list. PowerPoint renders these as "1.", "2.",
            // "(1)", "i.", etc. depending on autonumType. Without this
            // branch, code blocks with line numbers (auto-numbered
            // paragraphs) rendered with no number prefix at all.
            String numberStr = formatAutoNumber(autoNumber, para.getAutonumType());
            Font numberFont = resolveFont(null, slideCtx, zoom, level, isTitle);
            gc.setFont(numberFont);
            Color textColor = resolveDefaultTextColor(slideCtx, placeholderType);
            gc.setFill(textColor);
            gc.fillText(numberStr, textX + indentPx, currentY + numberFont.getSize());
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
        double availableWidth = maxWidth - marginLeftPx;
        if (indentPx < 0) {
            availableWidth = maxWidth; // Hanging indent: text area is full width
        }

        // --- Pass 1: collect words into physical lines ---
        // Each "word segment" tracks its text, font, color, run, and width.
        record WordSegment(String text, Font font, Color color, TextRun run, double width) {}
        List<List<WordSegment>> lines = new ArrayList<>();
        List<WordSegment> currentLine = new ArrayList<>();
        double currentLineWidth = 0;

        for (TextRun run : para.getRuns()) {
            if (run.getText() == null || run.getText().isEmpty()) continue;

            Font font = resolveFont(run, slideCtx, zoom, level, isTitle);
            gc.setFont(font);
            Color color = ShapeStyleExtractor.resolveTextRunColor(run, placeholderType, slideCtx);

            // Fast measurement path: use the run's unscaled OOXML point size
            // plus FontData's glyph-advance table. measureStringWidthEmu is
            // microseconds per word. The previous computeTextWidth path was
            // allocating a javafx.scene.text.Text node per word and calling
            // getLayoutBounds(), which forces a full JavaFX layout pass each
            // call -- ~50-100ms per word, multiplied by the hundreds of words
            // on code-heavy slides, was the 15-20s bottleneck.
            int sizeHundredths = resolveRunSizeHundredths(run, slideCtx, level, isTitle);
            com.excudo.core.metrics.FontData fd = resolveRunFontData(run, slideCtx, isTitle);

            String[] words = run.getText().split("(?<=\\s)");
            for (String word : words) {
                double wordWidth = measureWordWidth(word, gc, fd, sizeHundredths, zoom);

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
                gc.setFont(seg.font());

                // Highlight background (drawn before text)
                if (seg.run().getHighlight() != null) {
                    TextColor hlColor = seg.run().getHighlight();
                    Color hl = hlColor.getHexVal() != null
                        ? Color.web("#" + hlColor.getHexVal())
                        : Color.YELLOW;
                    gc.setFill(hl);
                    gc.fillRect(lineX, lineY, seg.width(), seg.font().getSize() + 2);
                }

                gc.setFill(seg.color());
                gc.fillText(seg.text(), lineX, lineY + seg.font().getSize());

                // Underline
                if (seg.run().getUnderline() != null && !"none".equals(seg.run().getUnderline())) {
                    double underlineY = lineY + seg.font().getSize() + 2;
                    gc.setStroke(seg.color());
                    gc.setLineWidth("heavy".equals(seg.run().getUnderline()) ? 2.0 : 1.0);
                    gc.strokeLine(lineX, underlineY, lineX + seg.width(), underlineY);
                }

                // Strikethrough (sngStrike or dblStrike)
                if (seg.run().getStrikethrough() != null) {
                    double strikeY = lineY + seg.font().getSize() * 0.6;
                    gc.setStroke(seg.color());
                    gc.setLineWidth(1.0);
                    gc.strokeLine(lineX, strikeY, lineX + seg.width(), strikeY);
                    if ("dblStrike".equals(seg.run().getStrikethrough())) {
                        gc.strokeLine(lineX, strikeY + 2, lineX + seg.width(), strikeY + 2);
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

    // Cache for resolved JavaFX Font objects. Every text run on every render
    // calls resolveFont, and on code-heavy slides with syntax highlighting we
    // can hit 80+ runs per slide all sharing the same (family, size, style).
    // Without a cache, each run re-runs Font.font() (a JavaFX registry lookup
    // that's not free) and the familyMatches substitution check (another
    // Font.font call + lazy metadata load). 166 calls per render turned into
    // 16 seconds of lag on Consolas-heavy code slides. With this cache the
    // first run on a novel combo pays the cost; every subsequent run is O(1).
    private static final java.util.concurrent.ConcurrentHashMap<String, Font> FONT_CACHE =
        new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Resolve a JavaFX Font from run properties + theme fallback.
     */
    private static Font resolveFont(TextRun run, SlideRenderContext slideCtx,
                                     double zoom, int level, boolean isTitle) {
        String family = null;
        double sizePt = 18.0; // Default
        boolean bold = false;
        boolean italic = false;

        // Run-level overrides
        if (run != null) {
            if (run.getFontFamily() != null) family = run.getFontFamily();
            if (run.getFontSize() != null) sizePt = run.getFontSize() / 100.0;
            if (run.getBold() != null) bold = run.getBold();
            if (run.getItalic() != null) italic = run.getItalic();
        }

        // Theme-level defaults (OOXML inheritance: run > paragraph > theme)
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

        double scaledSize = sizePt * zoom;
        if (scaledSize < 1) scaledSize = 1;

        FontWeight weight = bold ? FontWeight.BOLD : FontWeight.NORMAL;
        FontPosture posture = italic ? FontPosture.ITALIC : FontPosture.REGULAR;

        // Check the cache first -- size is quantised to 0.1pt so we don't
        // explode the cache across zoom increments that round identically
        // to the same pixel height. The fallback family (if any) is included
        // in the key because it affects the final resolved Font.
        String fallbackFamily = slideCtx == null ? null
            : (isTitle ? slideCtx.getMajorFontFallback() : slideCtx.getMinorFontFallback());
        String cacheKey = family + "|" + weight + "|" + posture
            + "|" + Math.round(scaledSize * 10)
            + "|" + (fallbackFamily == null ? "" : fallbackFamily);
        Font cached = FONT_CACHE.get(cacheKey);
        if (cached != null) return cached;

        Font requested = Font.font(family, weight, posture, scaledSize);
        Font resolved = requested;

        // JavaFX silently substitutes a system default when the requested
        // family isn't installed -- typically a serif on Linux hosts where
        // Segoe UI / Consolas / Calibri / etc. aren't present. Detect the
        // substitution by family-name comparison and try the theme's
        // declared fallback. If that also doesn't match, accept whatever
        // JavaFX gave us -- we've done what we can.
        if (!familyMatches(requested, family) && fallbackFamily != null
                && !fallbackFamily.isBlank() && !fallbackFamily.equalsIgnoreCase(family)) {
            Font fallbackFont = Font.font(fallbackFamily, weight, posture, scaledSize);
            if (familyMatches(fallbackFont, fallbackFamily)) {
                resolved = fallbackFont;
            }
        }

        FONT_CACHE.put(cacheKey, resolved);
        return resolved;
    }

    /**
     * JavaFX's returned family may include the style (e.g. "Segoe UI Bold"
     * when we asked for "Segoe UI" with BOLD weight). Check for prefix /
     * equality, case-insensitively.
     */
    private static boolean familyMatches(Font actual, String requestedFamily) {
        if (actual == null) return false;
        String actualFamily = actual.getFamily();
        if (actualFamily == null) return false;
        return actualFamily.equalsIgnoreCase(requestedFamily)
            || actualFamily.toLowerCase().startsWith(requestedFamily.toLowerCase() + " ")
            || actualFamily.toLowerCase().startsWith(requestedFamily.toLowerCase() + "-");
    }

    private static Color resolveDefaultTextColor(SlideRenderContext slideCtx, String phType) {
        if (slideCtx == null) return Color.BLACK;
        if ("title".equals(phType) || "ctrTitle".equals(phType)) {
            return Color.web(slideCtx.getTitleTextColorHex());
        }
        return Color.web(slideCtx.getBodyTextColorHex());
    }

    /**
     * Measure one word's width in screen pixels at the current zoom. Prefers
     * FontData (glyph-advance table, microseconds per call, already cached
     * per font path). Falls back to the JavaFX Text-node helper only when
     * FontData isn't available -- which means the font either couldn't be
     * resolved from a TTF on disk or the parse failed. For normal TTF fonts
     * on the host, the fast path applies.
     */
    private static double measureWordWidth(String word, GraphicsContext gc,
                                            com.excudo.core.metrics.FontData fd,
                                            int sizeHundredths, double zoom) {
        // Width measurement MUST track what fillText actually renders.
        // The raw TTF advance-width sum from FontData is only guaranteed
        // to match rendering when: (a) the same font file backs both, and
        // (b) the renderer does no hinting / sub-pixel snapping that
        // shifts glyph advances off the nominal values. (a) fails on
        // Linux where FontResolver fc-match'd to DejaVu Sans Mono while
        // JavaFX substituted to "System"; (b) fails on Windows with
        // DirectWrite + ClearType at fractional point sizes where the
        // per-char drift accumulates into per-word gaps. Both surface
        // the same visible symptom: wide whitespace between adjacent
        // WordSegments because lineX advances by measured width past
        // where the glyph actually ended.
        //
        // Going through JavaFX's Text node uses the same Font object
        // that fillText uses, so measurement and render agree by
        // construction. Results are memoised per (font-cache-key, text)
        // so the cost stays bounded -- first render of a slide pays
        // the Text-allocation hit once per unique word; every subsequent
        // render for the same slide (or another slide with repeated
        // tokens) is a HashMap lookup.
        //
        // FontData is still used for line-height / baseline metrics
        // elsewhere; that path remains unchanged.
        return computeTextWidth(gc, word);
    }

    /**
     * Compute the OOXML-hundredths-point size for a run using the same
     * inheritance order as resolveFont: run > theme style > default.
     */
    private static int resolveRunSizeHundredths(TextRun run, SlideRenderContext slideCtx,
                                                 int level, boolean isTitle) {
        if (run != null && run.getFontSize() != null) return run.getFontSize();
        if (slideCtx != null) {
            TextLevelStyle style = isTitle ? slideCtx.getTitleStyle(level) : slideCtx.getBodyStyle(level);
            if (style != null) return style.getFontSize();
        }
        return 1800; // 18pt default, matches resolveFont
    }

    /**
     * Resolve FontData (glyph advance + metrics tables) for a run. Uses the
     * same family-resolution logic as resolveFont, with fallback to the
     * theme's *Fallback family if the primary isn't available. FontResolver
     * and TrueTypeFontParser both cache internally, so repeated calls for
     * the same family are O(1).
     */
    private static com.excudo.core.metrics.FontData resolveRunFontData(TextRun run,
                                                                        SlideRenderContext slideCtx,
                                                                        boolean isTitle) {
        String family = null;
        if (run != null && run.getFontFamily() != null) family = run.getFontFamily();
        if (family == null && slideCtx != null) {
            family = isTitle ? slideCtx.getMajorFont() : slideCtx.getMinorFont();
        }
        if (family == null) return null;

        com.excudo.core.metrics.FontData fd = loadFontData(family);
        if (fd == null && slideCtx != null) {
            String fallback = isTitle ? slideCtx.getMajorFontFallback() : slideCtx.getMinorFontFallback();
            if (fallback != null && !fallback.isBlank() && !fallback.equalsIgnoreCase(family)) {
                fd = loadFontData(fallback);
            }
        }
        return fd;
    }

    private static com.excudo.core.metrics.FontData loadFontData(String family) {
        try {
            java.nio.file.Path path = com.excudo.core.metrics.FontResolver.resolve(family, false, false);
            if (path != null) {
                return com.excudo.core.metrics.TrueTypeFontParser.parse(path);
            }
        } catch (java.io.IOException ignored) {}
        return null;
    }

    // Per-(font, text) width memoisation. First call for a unique word
    // at a given font + size pays the Text allocation + layout pass;
    // repeats hit the map. Slides with repeated tokens (code blocks,
    // long prose with common words) amortise the cost to near-zero.
    // Bounded implicitly by the product of fonts-in-use * unique words
    // across the session -- low thousands at most for typical decks.
    private static final java.util.concurrent.ConcurrentHashMap<String, Double> WIDTH_CACHE =
        new java.util.concurrent.ConcurrentHashMap<>();

    private static double computeTextWidth(GraphicsContext gc, String text) {
        javafx.scene.text.Font font = gc.getFont();
        // Size quantised to 0.01pt so widths cache across tiny zoom
        // increments that round to the same sub-pixel placement.
        String key = (font == null ? "null" : font.getFamily() + "|" + font.getStyle()
            + "|" + Math.round(font.getSize() * 100)) + "|" + text;
        Double cached = WIDTH_CACHE.get(key);
        if (cached != null) return cached;
        javafx.scene.text.Text helper = new javafx.scene.text.Text(text);
        if (font != null) helper.setFont(font);
        double width = helper.getLayoutBounds().getWidth();
        WIDTH_CACHE.put(key, width);
        return width;
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
