package com.excudo.view.rendering.text;

import com.excudo.core.metrics.MeasuredText;
import com.excudo.core.metrics.TextStyleSource;
import com.excudo.core.model.*;
import com.excudo.core.model.BulletType;
import com.excudo.view.rendering.CoordinateMapper;
import com.excudo.view.rendering.RenderingContext;
import com.excudo.view.rendering.ShapeStyleExtractor;
import com.excudo.view.rendering.SlideRenderContext;
import com.excudo.core.rendering.surface.BulletFontMapper;
import com.excudo.core.rendering.surface.RenderSurface;
import com.excudo.core.rendering.surface.SurfaceFont;
import com.excudo.core.rendering.surface.SurfacePaint;
import javafx.geometry.Rectangle2D;

import java.util.List;

/**
 * Paints pre-measured text onto a {@link RenderSurface}.
 * Does NOT extract text from DOM or measure text layout. Only draws.
 *
 * Pipeline:
 *   TextBodyExtractor.extract(xmlElement) -> TextBody
 *   TextMeasurer.measure(textBody, widthEmu, styles) -> MeasuredText
 *   TextPainter.paint(textBody, measuredText, bounds, ctx, slideCtx, phType, styles)
 *
 * <h2>Single wrap authority</h2>
 * The painter draws exactly the lines {@link MeasuredText} carries —
 * per-line styled segments positioned in EMUs by the TTF-metric
 * measurement engine. It never re-wraps with the surface's own width
 * engine; the two engines used to disagree about line breaks, which
 * broke vertical centering and wrap position. Measured EMU offsets are
 * converted to pixels here for alignment/justification only.
 *
 * <h2>No text clipping</h2>
 * Text that overflows the shape is painted in full. PowerPoint's PDF
 * export does the same — the text-align-wrap and text-autofit corpus
 * truth images show overflow text continuing past the shape outline —
 * so clipping to the shape bounds would diverge from ground truth.
 *
 * <h2>Autofit</h2>
 * {@code normAutofit} fontScale / lnSpcReduction are applied during
 * measurement (the stored values are PowerPoint's own fit computation),
 * so the segments arriving here already carry the reduced sizes.
 * {@code spAutoFit} boxes need no shrink at all: the stored shape
 * geometry reflects the grown box and overflow simply paints.
 */
public final class TextPainter {

    private TextPainter() {}

    /**
     * Paint with default placeholder type detection.
     */
    public static void paint(TextBody textBody, MeasuredText measured,
                             Rectangle2D boundsPixels, RenderingContext ctx,
                             SlideRenderContext slideCtx) {
        paint(textBody, measured, boundsPixels, ctx, slideCtx, null, TextStyleSource.EMPTY);
    }

    /**
     * Paint with no inherited list-style context.
     *
     * @param placeholderType "title", "ctrTitle", "body", "subTitle", "obj", or null
     */
    public static void paint(TextBody textBody, MeasuredText measured,
                             Rectangle2D boundsPixels, RenderingContext ctx,
                             SlideRenderContext slideCtx, String placeholderType) {
        paint(textBody, measured, boundsPixels, ctx, slideCtx, placeholderType,
            TextStyleSource.EMPTY);
    }

    /**
     * Paint a TextBody within the given pixel bounds.
     *
     * @param placeholderType "title", "ctrTitle", "body", "subTitle", "obj", or null
     * @param styles          the SAME style source that was passed to
     *                        {@code TextMeasurer.measure} — the painter only
     *                        consults it for paint-side properties (colors,
     *                        bullets); all geometry comes from {@code measured}
     */
    public static void paint(TextBody textBody, MeasuredText measured,
                             Rectangle2D boundsPixels, RenderingContext ctx,
                             SlideRenderContext slideCtx, String placeholderType,
                             TextStyleSource styles) {
        if (textBody == null || measured == null || boundsPixels == null) return;
        if (styles == null) styles = TextStyleSource.EMPTY;

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
            // Center the text block (insets excluded) within the inset
            // text area. Using the inset-inclusive total height shifted
            // centered text up by half the vertical insets.
            double textHeightPx = emuToPixels(measured.getTextHeightEmu()) * zoom;
            double availableHeight = boundsPixels.getHeight() - topInsetPx - bottomInsetPx;
            if (textHeightPx < availableHeight) {
                if ("ctr".equals(vAlign)) {
                    startY += (availableHeight - textHeightPx) / 2;
                } else {
                    startY += availableHeight - textHeightPx;
                }
            }
        }

        double currentY = startY;

        // Auto-number bullet counter. Restarts when a non-AUTONUMBER
        // paragraph breaks the sequence (PowerPoint behaviour: "1. 2. 3.
        // [plain paragraph] 1. 2." rather than continuing through) AND
        // when the numbering scheme changes (PowerPoint's export of
        // consecutive arabicPeriod/romanUcPeriod/alphaLcParenR lists
        // shows "1. 2. I. II. a) b)", not "1. 2. III. IV. e) f)").
        int autoNumCounter = 0;
        String lastAutonumType = null;

        for (int i = 0; i < paragraphs.size() && i < measurements.size(); i++) {
            TextParagraph para = paragraphs.get(i);
            MeasuredText.ParagraphMeasurement pm = measurements.get(i);

            if (para.isEmpty()) {
                currentY += emuToPixels(pm.getHeightEmu()) * zoom;
                continue;
            }

            TextStyleSource.LevelStyle ls = styles.levelStyle(para.getLevel());
            if (ls == null) ls = TextStyleSource.LevelStyle.EMPTY;

            Bullet bullet = resolveBullet(para, ls);
            int paraNumber = 0;
            if (bullet != null && bullet.autonumType() != null) {
                if (!bullet.autonumType().equals(lastAutonumType)) {
                    autoNumCounter = 0;
                }
                autoNumCounter++;
                paraNumber = autoNumCounter;
                lastAutonumType = bullet.autonumType();
            } else {
                autoNumCounter = 0;
                lastAutonumType = null;
            }

            currentY = paintParagraph(para, pm, ls, bullet, paraNumber, startX, currentY,
                maxWidth, surface, slideCtx, zoom, placeholderType);
        }

        if (isVertical) {
            surface.restore();
        }
    }

    // ========== PARAGRAPH ==========

    /**
     * Resolved bullet descriptor: exactly one of {@code character} /
     * {@code autonumType} / {@code imageRelId} is set.
     */
    private record Bullet(String character, String fontFamily, String autonumType,
                          String imageRelId, Integer sizePct, TextColor color) {}

    /**
     * Resolve the paragraph's effective bullet. Explicit paragraph
     * properties win; {@link BulletType#INHERITED} falls through to the
     * level style resolved from the lstStyle chain (which already
     * encodes layout/master/defaultTextStyle precedence), so plain text
     * boxes no longer need placeholder gating — their chain simply has
     * no bullet unless one was authored.
     */
    private static Bullet resolveBullet(TextParagraph para, TextStyleSource.LevelStyle ls) {
        Integer sizePct = para.getBulletSizePercent() != null
            ? para.getBulletSizePercent() : ls.bulletSizePct();
        TextColor color = para.getBulletColor() != null
            ? para.getBulletColor() : ls.bulletColor();

        switch (para.getBulletType()) {
            case CHARACTER:
                String ch = para.getBulletChar();
                String font = para.getBulletFont();
                if (ch == null) { ch = ls.bulletChar(); font = ls.bulletFont(); }
                return ch != null ? new Bullet(ch, font, null, null, sizePct, color) : null;
            case AUTONUMBER:
                return new Bullet(null, para.getBulletFont(), para.getAutonumType(),
                    null, sizePct, color);
            case PICTURE:
                return para.getBulletImageRelId() != null
                    ? new Bullet(null, null, null, para.getBulletImageRelId(), sizePct, color)
                    : null;
            case INHERITED:
                if (Boolean.TRUE.equals(ls.bulletNone())) return null;
                if (ls.bulletChar() != null) {
                    return new Bullet(ls.bulletChar(), ls.bulletFont(), null, null,
                        sizePct, color);
                }
                if (ls.autonumType() != null) {
                    return new Bullet(null, ls.bulletFont(), ls.autonumType(), null,
                        sizePct, color);
                }
                return null;
            default:
                return null;
        }
    }

    /**
     * Paint a single paragraph from its measured line layout. Returns
     * the Y position after this paragraph.
     */
    private static double paintParagraph(TextParagraph para, MeasuredText.ParagraphMeasurement pm,
                                         TextStyleSource.LevelStyle ls, Bullet bullet,
                                         int autoNumber, double startX, double startY,
                                         double maxWidth, RenderSurface surface,
                                         SlideRenderContext slideCtx, double zoom,
                                         String placeholderType) {
        List<MeasuredText.Line> lines = pm.getLines();
        if (lines.isEmpty()) {
            return startY + emuToPixels(pm.getHeightEmu()) * zoom;
        }

        double marginLeftPx = emuToPixels(pm.getMarginLeftEmu()) * zoom;
        double indentPx = emuToPixels(pm.getIndentEmu()) * zoom;
        double textOriginX = startX + marginLeftPx;

        // Width alignment distributes within: the measured wrap width,
        // capped at the text area (wrap="none" measures against an
        // effectively infinite width).
        double wrapWidthPx = Math.min(emuToPixels(pm.getWrapWidthEmu()) * zoom,
            Math.max(1, maxWidth - marginLeftPx));

        double currentY = startY + emuToPixels(pm.getSpaceBeforeEmu()) * zoom;

        // Bullet (or auto-number) rendered against the first line's baseline.
        double firstLineBulletShift = 0;
        if (bullet != null) {
            MeasuredText.Segment firstInk = firstInkSegment(lines);
            if (firstInk != null) {
                double bulletAdvance = paintBullet(bullet, autoNumber, firstInk, para, ls,
                    textOriginX + indentPx,
                    currentY + emuToPixels(lines.get(0).baselineEmu()) * zoom,
                    surface, slideCtx, zoom, placeholderType);
                // No hanging indent: the bullet occupies the first line's
                // leading space and pushes the text right (PowerPoint
                // renders "1.item" / "•item" flush when marL == indent == 0).
                if (pm.getIndentEmu() >= 0) {
                    firstLineBulletShift = bulletAdvance;
                }
            }
        }

        String alignment = pm.getAlignment();

        for (int li = 0; li < lines.size(); li++) {
            MeasuredText.Line line = lines.get(li);
            double lineTop = currentY;
            double baselineY = lineTop + emuToPixels(line.baselineEmu()) * zoom;
            double advancePx = emuToPixels(line.advanceEmu()) * zoom;
            double lineWidthPx = emuToPixels(line.widthEmu()) * zoom;

            double lineX = textOriginX;
            if (li == 0) {
                if (pm.getIndentEmu() > 0) lineX += indentPx;
                lineX += firstLineBulletShift;
            }

            // Justification: distribute the residual width across the
            // inter-word gaps of every line that wrapped (not the last
            // line, not lines ended by an explicit <a:br/>).
            double extraPerGap = 0;
            int lastInkIdx = -1;
            if ("just".equals(alignment) && li < lines.size() - 1 && !line.forcedBreak()) {
                List<MeasuredText.Segment> segs = line.segments();
                int gaps = 0;
                for (int siIdx = segs.size() - 1; siIdx >= 0; siIdx--) {
                    if (!segs.get(siIdx).text().isBlank()) { lastInkIdx = siIdx; break; }
                }
                for (int siIdx = 0; siIdx < lastInkIdx; siIdx++) {
                    if (segs.get(siIdx).text().isBlank()) gaps++;
                }
                double residual = wrapWidthPx - lineWidthPx;
                if (gaps > 0 && residual > 0) {
                    extraPerGap = residual / gaps;
                }
            } else if ("ctr".equals(alignment)) {
                lineX += (wrapWidthPx - lineWidthPx) / 2;
            } else if ("r".equals(alignment)) {
                lineX += wrapWidthPx - lineWidthPx;
            }

            double justifyShift = 0;
            List<MeasuredText.Segment> segs = line.segments();
            for (int si = 0; si < segs.size(); si++) {
                MeasuredText.Segment seg = segs.get(si);
                double segX = lineX + emuToPixels(seg.xEmu()) * zoom + justifyShift;
                paintSegment(seg, segX, baselineY, lineTop, advancePx, surface, slideCtx,
                    ls, zoom, placeholderType);
                if (extraPerGap > 0 && si < lastInkIdx && seg.text().isBlank()) {
                    justifyShift += extraPerGap;
                }
            }

            currentY += advancePx;
        }

        return currentY + emuToPixels(pm.getSpaceAfterEmu()) * zoom;
    }

    // ========== SEGMENT ==========

    private static void paintSegment(MeasuredText.Segment seg, double segX, double baselineY,
                                     double lineTop, double lineAdvancePx, RenderSurface surface,
                                     SlideRenderContext slideCtx, TextStyleSource.LevelStyle ls,
                                     double zoom, String placeholderType) {
        String text = seg.text();
        if (text == null || text.isEmpty()) return;

        double fontPx = CoordinateMapper.centipointsToPixels(seg.fontSizeCentiPt()) * zoom;
        SurfaceFont font = surfaceFont(seg, fontPx, slideCtx, placeholderType);
        surface.setFont(font);

        double widthPx = emuToPixels(seg.widthEmu()) * zoom;
        double shiftPx = emuToPixels(seg.baselineShiftEmu()) * zoom;
        double drawBaseline = baselineY + shiftPx;
        TextRun run = seg.run();

        // Highlight background (drawn before text, covering the line box)
        if (run != null && run.getHighlight() != null) {
            TextColor hlColor = run.getHighlight();
            SurfacePaint hl = hlColor.getHexVal() != null
                ? SurfacePaint.Solid.fromHex(hlColor.getHexVal())
                : SurfacePaint.Solid.rgb(255, 255, 0); // YELLOW
            surface.setFill(hl);
            surface.fillRect(segX, lineTop, widthPx, lineAdvancePx);
        }

        if (text.isBlank()) return; // nothing visible; highlight already painted

        SurfacePaint color = resolveSegmentColor(run, ls, slideCtx, placeholderType);
        surface.setFill(color);

        if (seg.charAdvancesEmu() != null) {
            // Tracking applied: place each glyph at its measured advance so
            // painted positions match measured line widths.
            double x = segX;
            long[] adv = seg.charAdvancesEmu();
            for (int ci = 0; ci < text.length() && ci < adv.length; ci++) {
                String ch = String.valueOf(text.charAt(ci));
                if (!ch.isBlank()) {
                    surface.fillText(ch, x, drawBaseline);
                }
                x += emuToPixels(adv[ci]) * zoom;
            }
        } else {
            surface.fillText(text, segX, drawBaseline);
        }

        // Underline
        if (run != null && run.getUnderline() != null && !"none".equals(run.getUnderline())) {
            double underlineY = drawBaseline + Math.max(1.5, fontPx * 0.08);
            surface.setStroke(color);
            surface.setLineWidth("heavy".equals(run.getUnderline())
                ? Math.max(2.0, fontPx * 0.08) : Math.max(1.0, fontPx * 0.05));
            surface.strokeLine(segX, underlineY, segX + widthPx, underlineY);
        }

        // Strikethrough (sngStrike or dblStrike)
        if (run != null && run.getStrikethrough() != null) {
            double strikeY = drawBaseline - fontPx * 0.3;
            surface.setStroke(color);
            surface.setLineWidth(Math.max(1.0, fontPx * 0.05));
            surface.strokeLine(segX, strikeY, segX + widthPx, strikeY);
            if ("dblStrike".equals(run.getStrikethrough())) {
                surface.strokeLine(segX, strikeY + 2, segX + widthPx, strikeY + 2);
            }
        }
    }

    private static SurfaceFont surfaceFont(MeasuredText.Segment seg, double fontPx,
                                           SlideRenderContext slideCtx, String placeholderType) {
        boolean isTitle = "title".equals(placeholderType) || "ctrTitle".equals(placeholderType);
        String fallback = slideCtx == null ? null
            : (isTitle ? slideCtx.getMajorFontFallback() : slideCtx.getMinorFontFallback());
        return new SurfaceFont(seg.fontFamily(), fallback,
            seg.bold() ? SurfaceFont.Weight.BOLD : SurfaceFont.Weight.NORMAL,
            seg.italic() ? SurfaceFont.Posture.ITALIC : SurfaceFont.Posture.REGULAR,
            Math.max(1, fontPx));
    }

    /**
     * Run color > lstStyle-chain level color > theme default for the
     * placeholder type. Never returns null.
     */
    private static SurfacePaint resolveSegmentColor(TextRun run, TextStyleSource.LevelStyle ls,
                                                    SlideRenderContext slideCtx,
                                                    String placeholderType) {
        if (run != null && run.getColor() != null) {
            SurfacePaint p = paintFromTextColor(run.getColor(), slideCtx);
            if (p != null) return p;
        }
        if (ls.color() != null) {
            SurfacePaint p = paintFromTextColor(ls.color(), slideCtx);
            if (p != null) return p;
        }
        if (slideCtx != null) {
            return ShapeStyleExtractor.resolveTextRunColor(null, placeholderType, slideCtx);
        }
        return SurfacePaint.Solid.rgb(0, 0, 0);
    }

    private static SurfacePaint paintFromTextColor(TextColor tc, SlideRenderContext slideCtx) {
        if (tc == null) return null;
        if (tc.getHexVal() != null) {
            return SurfacePaint.Solid.fromHex(tc.getHexVal());
        }
        if (tc.isScheme() && slideCtx != null) {
            return SurfacePaint.Solid.fromHex(slideCtx.resolveSchemeColor(tc.getSchemeVal()));
        }
        return null;
    }

    // ========== BULLET ==========

    /**
     * Paint the bullet character or auto-number for a paragraph. Returns
     * the bullet's advance width in pixels (used to push the first line's
     * text right when there is no hanging indent).
     *
     * <p>Size: {@code buSzPct} scales the FIRST run's effective font
     * size (thousandths of a percent, default 100%). Color: {@code buClr}
     * beats the level style's bullet color beats the first run's text
     * color — PowerPoint's bullet inherits the text color unless
     * overridden. Font: {@code buFont}, with legacy symbol fonts
     * (Wingdings/Symbol) translated to Unicode equivalents rendered in
     * the run font.
     */
    private static double paintBullet(Bullet bullet, int autoNumber,
                                      MeasuredText.Segment firstInk, TextParagraph para,
                                      TextStyleSource.LevelStyle ls, double bulletX,
                                      double baselineY, RenderSurface surface,
                                      SlideRenderContext slideCtx, double zoom,
                                      String placeholderType) {
        double sizePctFactor = bullet.sizePct() != null ? bullet.sizePct() / 100000.0 : 1.0;

        // Picture bullet (a:buBlip): draw the referenced image part in a
        // square box the size of the (buSzPct-scaled) first-run font,
        // aspect-preserved, bottom-aligned to the text baseline.
        if (bullet.imageRelId() != null) {
            if (slideCtx == null || slideCtx.getDocument() == null) return 0;
            com.excudo.core.rendering.surface.SurfaceImage img =
                com.excudo.view.rendering.shapes.BlipFillResolver.resolve(
                    surface, slideCtx.getDocument(), slideCtx.getSlideNumber(),
                    bullet.imageRelId());
            if (img == null) return 0;
            double boxPx = CoordinateMapper.centipointsToPixels(
                firstInk.fontSizeCentiPt() * sizePctFactor) * zoom;
            double h = boxPx;
            double w = img.heightPx() > 0 ? boxPx * img.widthPx() / img.heightPx() : boxPx;
            surface.drawImage(img, bulletX, baselineY - h, w, h);
            return w;
        }

        String bulletText;
        String bulletFontFamily = bullet.fontFamily();
        if (bullet.autonumType() != null) {
            bulletText = formatAutoNumber(autoNumber, bullet.autonumType());
        } else {
            bulletText = bullet.character();
            if (BulletFontMapper.isSymbolFont(bulletFontFamily)) {
                // Wingdings / Symbol store bullets as ASCII-range codepoints
                // that only render correctly with the symbol font installed.
                // Translate to Unicode equivalents and render in the body
                // font — host-font-independent, PowerPoint-style appearance.
                bulletText = BulletFontMapper.translate(bulletFontFamily, bulletText);
                bulletFontFamily = null;
            }
        }
        if (bulletText == null || bulletText.isEmpty()) return 0;

        double bulletFontPx = CoordinateMapper.centipointsToPixels(
            firstInk.fontSizeCentiPt() * sizePctFactor) * zoom;
        String family = bulletFontFamily != null && !bulletFontFamily.isEmpty()
            ? bulletFontFamily : firstInk.fontFamily();

        surface.setFont(SurfaceFont.of(family, Math.max(1, bulletFontPx)));

        SurfacePaint color = bullet.color() != null
            ? paintFromTextColor(bullet.color(), slideCtx)
            : null;
        if (color == null) {
            color = resolveSegmentColor(firstInk.run(), ls, slideCtx, placeholderType);
        }
        surface.setFill(color);
        surface.fillText(bulletText, bulletX, baselineY);
        return surface.measureAdvance(bulletText);
    }

    private static MeasuredText.Segment firstInkSegment(List<MeasuredText.Line> lines) {
        for (MeasuredText.Line line : lines) {
            for (MeasuredText.Segment seg : line.segments()) {
                if (!seg.text().isBlank()) return seg;
            }
        }
        return null;
    }

    // ========== HELPERS ==========

    private static double emuToPixels(long emu) {
        return CoordinateMapper.emuToPixels(emu);
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
