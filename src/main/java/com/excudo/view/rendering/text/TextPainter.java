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
        double zoom = ctx.getZoomedCoordinateMapper().getZoomLevel();

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

        for (int i = 0; i < paragraphs.size() && i < measurements.size(); i++) {
            TextParagraph para = paragraphs.get(i);
            MeasuredText.ParagraphMeasurement pm = measurements.get(i);

            if (para.isEmpty()) {
                currentY += emuToPixels(pm.getHeightEmu()) * zoom;
                continue;
            }

            boolean isTitle = "title".equals(placeholderType) || "ctrTitle".equals(placeholderType);
            currentY = paintParagraph(para, pm, startX, currentY, maxWidth, gc, slideCtx, zoom,
                placeholderType, isTitle);
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
                                          double zoom, String placeholderType, boolean isTitle) {
        int level = para.getLevel();

        // Use title style or body style based on placeholder type
        TextLevelStyle themeStyle = null;
        if (slideCtx != null) {
            themeStyle = isTitle ? slideCtx.getTitleStyle(level) : slideCtx.getBodyStyle(level);
        }

        // Resolve left margin and indent from paragraph or theme
        double marginLeftPx = 0;
        double indentPx = 0;
        if (para.getMarginLeft() != null) {
            marginLeftPx = emuToPixels(para.getMarginLeft()) * zoom;
        } else if (themeStyle != null) {
            marginLeftPx = emuToPixels(themeStyle.getMarginLeft()) * zoom;
            indentPx = emuToPixels(themeStyle.getIndent()) * zoom;
        }

        // Space before
        double spaceBeforePx = 0;
        if (para.getSpaceBefore() != null && para.getSpaceBefore() > 0) {
            spaceBeforePx = (para.getSpaceBefore() / 100.0) * zoom;
        } else if (themeStyle != null && themeStyle.getSpaceBefore() > 0) {
            spaceBeforePx = (themeStyle.getSpaceBefore() / 100.0) * zoom;
        }

        double currentY = startY + spaceBeforePx;
        double textX = startX + marginLeftPx;

        // Theme bullet inheritance is a placeholder-only behaviour in OOXML.
        // A rectangle / ellipse / text box / etc. whose paragraph doesn't
        // explicitly set <a:buChar> renders with NO bullet -- the inheritance
        // chain (paragraph -> layout -> master -> theme) only resolves
        // through a placeholder reference. Before this guard we were
        // pulling the theme body-style bullet into every non-placeholder
        // shape, producing phantom bullets that weren't in the PPTX and
        // sent agents chasing nonexistent issues.
        boolean isPlaceholder = placeholderType != null;

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
        }

        // Adjust X for text after bullet
        double runX = textX + marginLeftPx;
        if (indentPx < 0) {
            // Hanging indent: bullet at marginLeft + indent, text at marginLeft
            runX = textX;
        }

        // Resolve line height from measurements
        double lineHeightPx = pm.getLineCount() > 0
            ? (emuToPixels(pm.getHeightEmu()) * zoom) / pm.getLineCount()
            : 16.0 * zoom;

        // Available width for text wrapping (from current X to right edge of bounds)
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

            String[] words = run.getText().split("(?<=\\s)");
            for (String word : words) {
                double wordWidth = computeTextWidth(gc, word);

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

        Font requested = Font.font(family, weight, posture, scaledSize);

        // JavaFX silently substitutes a system default when the requested
        // family isn't installed -- typically a serif on Linux hosts where
        // Segoe UI / Consolas / Calibri / etc. aren't present. Detect the
        // substitution by family-name comparison and try the theme's
        // declared fallback. If that also doesn't match, accept whatever
        // JavaFX gave us -- we've done what we can.
        if (!familyMatches(requested, family) && slideCtx != null) {
            String fallback = isTitle ? slideCtx.getMajorFontFallback()
                                      : slideCtx.getMinorFontFallback();
            if (fallback != null && !fallback.isBlank() && !fallback.equalsIgnoreCase(family)) {
                Font fallbackFont = Font.font(fallback, weight, posture, scaledSize);
                if (familyMatches(fallbackFont, fallback)) {
                    return fallbackFont;
                }
            }
        }

        return requested;
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

    private static double computeTextWidth(GraphicsContext gc, String text) {
        // Approximate: use JavaFX text measurement
        javafx.scene.text.Text helper = new javafx.scene.text.Text(text);
        helper.setFont(gc.getFont());
        return helper.getLayoutBounds().getWidth();
    }

    private static double emuToPixels(long emu) {
        return CoordinateMapper.emuToPixels(emu);
    }

    private static double emuToPixels(int emu) {
        return CoordinateMapper.emuToPixels((long) emu);
    }
}
