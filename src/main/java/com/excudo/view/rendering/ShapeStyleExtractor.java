package com.excudo.view.rendering;

import com.excudo.core.color.ColorTransforms;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.SlideShape;
import com.excudo.core.model.TextRun;
import com.excudo.core.model.TextColor;
import com.excudo.core.themes.FmtSchemeResolver;
import com.excudo.core.themes.ResolvedFill;
import com.excudo.core.themes.ThemeManager;
import com.excudo.core.rendering.lines.DashPatterns;
import com.excudo.core.rendering.lines.LineEnd;
import com.excudo.core.rendering.surface.GradientStops;
import com.excudo.core.rendering.surface.RenderSurface;
import com.excudo.core.rendering.surface.StrokeCap;
import com.excudo.core.rendering.surface.StrokeJoin;
import com.excudo.core.rendering.surface.SurfaceImage;
import com.excudo.core.rendering.surface.SurfacePaint;
import com.excudo.view.rendering.shapes.BlipFillResolver;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Extracts visual style properties from a shape's xmlElement for rendering.
 * Read-only -- does not modify the DOM. Returns backend-neutral
 * {@link SurfacePaint} values (the Canvas and AWT backends each translate
 * to their native types internally).
 *
 * Resolves the OOXML style hierarchy:
 *   shape spPr solidFill > shape p:style fillRef > theme default
 */
public final class ShapeStyleExtractor {

    private ShapeStyleExtractor() {}

    /**
     * Resolve the fill for a shape. Returns a {@link SurfacePaint.Solid}
     * or {@link SurfacePaint.LinearGradient}, or
     * {@link SurfacePaint.Transparent#INSTANCE} for no-fill.
     * Checks: spPr/solidFill > gradFill > noFill > theme accent1 fallback.
     */
    public static SurfacePaint resolveFillColor(SlideShape shape, SlideRenderContext slideCtx) {
        return resolveFillColor(shape, slideCtx, null);
    }

    /**
     * Resolve the fill for a shape. The {@code surface} is used only to
     * decode embedded images for picture fills ({@code a:blipFill});
     * pass null when no surface is available — picture fills then
     * degrade to transparent.
     */
    public static SurfacePaint resolveFillColor(SlideShape shape, SlideRenderContext slideCtx,
                                                RenderSurface surface) {
        if (shape == null || shape.getXmlElement() == null) {
            return SurfacePaint.Transparent.INSTANCE;
        }

        Element spEl = shape.getXmlElement();
        ShapeGeometry geom = shape.getGeometry();
        // Aspect ratio feeds gradient axis math; only the w:h ratio
        // matters, so EMU values are used directly.
        double aspectW = geom != null && geom.getWidth() > 0 ? geom.getWidth() : 1;
        double aspectH = geom != null && geom.getHeight() > 0 ? geom.getHeight() : 1;

        // Check for explicit fill in spPr
        Element spPr = getChild(spEl, "p:spPr");
        if (spPr != null) {
            if (getChild(spPr, "a:noFill") != null) {
                return SurfacePaint.Transparent.INSTANCE;
            }

            Element solidFill = getChild(spPr, "a:solidFill");
            if (solidFill != null) {
                return parseColorElement(solidFill, slideCtx);
            }

            Element gradFill = getChild(spPr, "a:gradFill");
            if (gradFill != null) {
                return parseGradientFill(gradFill, slideCtx, aspectW, aspectH);
            }

            Element pattFill = getChild(spPr, "a:pattFill");
            if (pattFill != null) {
                return parsePatternFill(pattFill, slideCtx);
            }

            Element blipFill = getChild(spPr, "a:blipFill");
            if (blipFill != null) {
                return parseBlipFill(blipFill, shape, slideCtx, surface);
            }
        }

        // If placeholder, default to transparent (inherits from layout/master)
        if (shape.getType() == SlideShape.ShapeType.PLACEHOLDER) {
            return SurfacePaint.Transparent.INSTANCE;
        }

        // Check p:style fillRef -- resolve through fmtScheme for full gradient/modifier support
        Element pStyle = getChild(spEl, "p:style");
        if (pStyle != null) {
            Element fillRef = getChild(pStyle, "a:fillRef");
            if (fillRef != null && slideCtx != null) {
                int idx = 0;
                try { idx = Integer.parseInt(fillRef.getAttribute("idx")); }
                catch (NumberFormatException ignored) {}

                // ECMA-376 20.1.4.2.14: idx 0 (and the 1000 boundary of
                // the bgFillStyleLst range) reference NO fill. Every
                // python-pptx connector authors fillRef idx=0.
                if (idx == 0 || idx == 1000) {
                    return SurfacePaint.Transparent.INSTANCE;
                }

                Element schemeClr = getChild(fillRef, "a:schemeClr");
                if (schemeClr != null && schemeClr.hasAttribute("val")) {
                    String phColorHex = slideCtx.resolveSchemeColor(schemeClr.getAttribute("val"));

                    if (idx > 0 && ThemeManager.isThemeLoaded()) {
                        ResolvedFill resolved = ThemeManager.resolveFillStyle(idx, phColorHex);
                        return convertToSurfacePaint(resolved, aspectW, aspectH);
                    }

                    // No fmtScheme -- flat scheme color, still honoring
                    // any modifiers on the ref's schemeClr.
                    return applyDomModifiers(phColorHex, schemeClr);
                }
            }
        }

        // No explicit fill and no p:style fillRef: per ECMA-376, the fill
        // choice under spPr is optional, and a shape with neither is
        // spec-valid. PowerPoint renders such shapes (canonical example:
        // a cNvSpPr/@txBox="1" plain text box with no spPr fill) as
        // transparent -- just the text. Previously this threw and the
        // renderer's catch block painted an empty red error rectangle,
        // suppressing text rendering entirely.
        return SurfacePaint.Transparent.INSTANCE;
    }

    /**
     * Resolve line/border style for a shape: color/width through the
     * OOXML hierarchy (explicit a:ln fill > p:style lnRef > 1pt black
     * when a:ln exists), plus the a:ln stroke details -- prstDash
     * (PDF-calibrated arrays via {@link DashPatterns}), cap, join,
     * compound, and head/tail line ends. PowerPoint's default join is
     * ROUND: every stroke in every parity truth PDF carries {@code 1 j}
     * unless a:bevel or a:miter is authored.
     */
    public static LineStyle resolveLineStyle(SlideShape shape, SlideRenderContext slideCtx) {
        if (shape == null || shape.getXmlElement() == null) {
            return LineStyle.NONE;
        }

        Element spPr = getChild(shape.getXmlElement(), "p:spPr");
        Element ln = spPr != null ? getChild(spPr, "a:ln") : null;

        // Explicit noFill on the line always wins.
        if (ln != null && getChild(ln, "a:noFill") != null) {
            return LineStyle.NONE;
        }

        // Explicit width/dash/cap/join/cmpd/ends on a:ln override
        // anything the style ref says.
        LnDetails details = parseLnDetails(ln);

        // Explicit line fill in a:ln.
        Element solidFill = ln != null ? getChild(ln, "a:solidFill") : null;
        if (solidFill != null) {
            SurfacePaint parsed = parseColorElement(solidFill, slideCtx);
            SurfacePaint.Solid lineColor =
                parsed instanceof SurfacePaint.Solid s ? s : SurfacePaint.Solid.rgb(0, 0, 0);
            double widthPixels = emuToPx(details.widthEmu() != null ? details.widthEmu() : 12700);
            return assemble(lineColor, widthPixels, details.dashName(), details);
        }

        // No explicit line fill: fall back to p:style lnRef. This also
        // covers shapes with NO a:ln at all -- PowerPoint strokes those
        // from the style reference (every python-pptx add_shape() shape:
        // lnRef idx=2 over shaded accent1), and skipping them left every
        // themed preset shape borderless.
        Element pStyle = getChild(shape.getXmlElement(), "p:style");
        Element lnRef = pStyle != null ? getChild(pStyle, "a:lnRef") : null;
        if (lnRef != null && slideCtx != null) {
            int idx = 0;
            try { idx = Integer.parseInt(lnRef.getAttribute("idx")); }
            catch (NumberFormatException ignored) {}

            Element schemeClr = getChild(lnRef, "a:schemeClr");
            if (schemeClr != null && schemeClr.hasAttribute("val")) {
                // The ref's color (with ITS modifiers -- e.g. the shade
                // 50000 python-pptx puts on accent1) is the phClr input
                // to the theme's lnStyleLst entry.
                String phColorHex = slideCtx.resolveSchemeColor(schemeClr.getAttribute("val"));
                double phAlpha = 1.0;
                var mods = com.excudo.core.color.ColorTransforms.modifiersFromDom(schemeClr);
                if (!mods.isEmpty()) {
                    var rc = com.excudo.core.color.ColorTransforms.apply(phColorHex, mods);
                    phColorHex = rc.hex();
                    phAlpha = rc.alpha();
                }

                if (idx > 0 && ThemeManager.isThemeLoaded()) {
                    FmtSchemeResolver.ResolvedLineStyle resolved =
                        ThemeManager.resolveLineStyle(idx, phColorHex);
                    SurfacePaint.Solid lineColor = solidFromHex(resolved.colorHex());
                    double alpha = resolved.alpha() * phAlpha;
                    if (alpha < 1.0) {
                        lineColor = lineColor.withAlpha(alpha);
                    }
                    double widthPixels = emuToPx(details.widthEmu() != null
                        ? details.widthEmu() : resolved.widthEmu());
                    String dash = details.dashName() != null
                        ? details.dashName() : resolved.dashStyle();
                    return assemble(lineColor, widthPixels, dash, details);
                }

                // No fmtScheme: flat ref color at explicit/default width.
                SurfacePaint.Solid lineColor = solidFromHex(phColorHex);
                if (phAlpha < 1.0) lineColor = lineColor.withAlpha(phAlpha);
                double widthPixels = emuToPx(details.widthEmu() != null ? details.widthEmu() : 12700);
                return assemble(lineColor, widthPixels, details.dashName(), details);
            }
        }

        // a:ln present but fill-less and no usable style ref: the legacy
        // 1pt black default. Without a:ln there is nothing to stroke.
        if (ln != null) {
            double widthPixels = emuToPx(details.widthEmu() != null ? details.widthEmu() : 12700);
            return assemble(SurfacePaint.Solid.rgb(0, 0, 0), widthPixels,
                details.dashName(), details);
        }
        return LineStyle.NONE;
    }

    /** EMU to pixels at 96 DPI. */
    private static double emuToPx(double emu) {
        return emu / 914400.0 * 96.0;
    }

    /** The stroke-detail attributes of one a:ln element. */
    private record LnDetails(Double widthEmu, String dashName, StrokeCap cap,
                             StrokeJoin join, double miterLimit, String compound,
                             LineEnd headEnd, LineEnd tailEnd) {
        static final LnDetails DEFAULTS = new LnDetails(null, null, StrokeCap.BUTT,
            StrokeJoin.ROUND, 10, "sng", LineEnd.NONE, LineEnd.NONE);
    }

    private static LnDetails parseLnDetails(Element ln) {
        if (ln == null) return LnDetails.DEFAULTS;

        Double widthEmu = null;
        if (ln.hasAttribute("w")) {
            try { widthEmu = Double.parseDouble(ln.getAttribute("w")); }
            catch (NumberFormatException ignored) {}
        }

        Element prstDash = getChild(ln, "a:prstDash");
        String dashName = (prstDash != null && prstDash.hasAttribute("val"))
            ? prstDash.getAttribute("val") : null;

        // ST_LineCap: flat (default) / rnd / sq.
        StrokeCap cap = switch (ln.getAttribute("cap")) {
            case "rnd" -> StrokeCap.ROUND;
            case "sq" -> StrokeCap.SQUARE;
            default -> StrokeCap.BUTT;
        };

        // Join is an element choice; absent means round (truth PDFs
        // stroke 1 j everywhere a:bevel/a:miter is not authored).
        StrokeJoin join = StrokeJoin.ROUND;
        double miterLimit = 10;
        if (getChild(ln, "a:bevel") != null) {
            join = StrokeJoin.BEVEL;
        } else {
            Element miter = getChild(ln, "a:miter");
            if (miter != null) {
                join = StrokeJoin.MITER;
                if (miter.hasAttribute("lim")) {
                    try {
                        miterLimit = Math.max(1,
                            Integer.parseInt(miter.getAttribute("lim")) / 100000.0);
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        String cmpd = ln.hasAttribute("cmpd") ? ln.getAttribute("cmpd") : "sng";

        return new LnDetails(widthEmu, dashName, cap, join, miterLimit, cmpd,
            parseLineEnd(getChild(ln, "a:headEnd")),
            parseLineEnd(getChild(ln, "a:tailEnd")));
    }

    private static LineEnd parseLineEnd(Element end) {
        if (end == null) return LineEnd.NONE;
        return LineEnd.fromXml(end.getAttribute("type"),
            end.getAttribute("w"), end.getAttribute("len"));
    }

    private static LineStyle assemble(SurfacePaint.Solid color, double widthPixels,
                                      String dashName, LnDetails d) {
        return new LineStyle(color, widthPixels,
            DashPatterns.pattern(dashName, widthPixels, d.cap()),
            d.cap(), d.join(), d.miterLimit(), d.compound(), d.headEnd(), d.tailEnd());
    }

    /**
     * Resolve a text run's rendered color.
     * Checks: run rPr solidFill > scheme color > theme default for placeholder type.
     */
    public static SurfacePaint.Solid resolveTextRunColor(TextRun run, String placeholderType,
                                                         SlideRenderContext slideCtx) {
        if (run != null && run.getColor() != null) {
            TextColor tc = run.getColor();
            if (tc.getHexVal() != null) {
                return solidFromHex(tc.getHexVal());
            }
            if (tc.isScheme() && slideCtx != null) {
                return solidFromHex(slideCtx.resolveSchemeColor(tc.getSchemeVal()));
            }
        }

        // Default: resolve from theme via SlideRenderContext
        if (slideCtx != null) {
            if ("title".equals(placeholderType) || "ctrTitle".equals(placeholderType)) {
                return solidFromHex(slideCtx.getTitleTextColorHex());
            }
            return solidFromHex(slideCtx.getBodyTextColorHex());
        }
        throw new IllegalStateException("No text color: slideCtx is null, placeholder='" + placeholderType + "'");
    }

    // ========== INTERNAL ==========

    /**
     * Parse a gradient fill. Reads a:gsLst for color stops (each stop
     * color runs the full modifier pipeline), then either a:path
     * (radial/rect/shape gradients) or a:lin (linear angle).
     *
     * @param aspectW shape width  (any unit — only the w:h ratio is used)
     * @param aspectH shape height
     */
    private static SurfacePaint parseGradientFill(Element gradFill, SlideRenderContext slideCtx,
                                                  double aspectW, double aspectH) {
        Element gsLst = getChild(gradFill, "a:gsLst");
        if (gsLst == null) return SurfacePaint.Solid.rgb(211, 211, 211); // LIGHTGRAY

        // Collect gradient stops
        List<SurfacePaint.LinearGradient.Stop> stops = new ArrayList<>();
        NodeList gsNodes = gsLst.getElementsByTagName("a:gs");
        for (int i = 0; i < gsNodes.getLength(); i++) {
            Element gs = (Element) gsNodes.item(i);
            double pos = 0;
            if (gs.hasAttribute("pos")) {
                try { pos = Integer.parseInt(gs.getAttribute("pos")) / 100000.0; }
                catch (NumberFormatException ignored) {}
            }
            stops.add(new SurfacePaint.LinearGradient.Stop(pos, parseColorElement(gs, slideCtx)));
        }

        if (stops.isEmpty()) return SurfacePaint.Solid.rgb(211, 211, 211);
        if (stops.size() == 1) return stops.get(0).color();
        stops.sort(Comparator.comparingDouble(SurfacePaint.LinearGradient.Stop::position));

        // Expand with PowerPoint's interpolation curve (gamma-2.2 +
        // sinusoidal ease) so backends' linear-sRGB ramps match truth.
        List<SurfacePaint.LinearGradient.Stop> expanded = GradientStops.expand(stops);

        Element path = getChild(gradFill, "a:path");
        if (path != null) {
            return parsePathGradient(path, expanded);
        }

        // Linear gradient: a:lin/@ang in 60000ths of a degree, clockwise
        // from east (0 = pointing right). Default 0.
        double angleDeg = 0;
        boolean scaled = false;
        Element lin = getChild(gradFill, "a:lin");
        if (lin != null) {
            if (lin.hasAttribute("ang")) {
                try { angleDeg = Integer.parseInt(lin.getAttribute("ang")) / 60000.0; }
                catch (NumberFormatException ignored) {}
            }
            String s = lin.getAttribute("scaled");
            scaled = "1".equals(s) || "true".equals(s);
        }
        return linearGradient(expanded, angleDeg, scaled, aspectW, aspectH);
    }

    /**
     * Build a linear-gradient paint from an OOXML angle.
     *
     * <p>Angle convention (verified against ground truth: fills-gradient
     * corpus): 0&deg; points east (first stop on the left edge), positive
     * is clockwise (90&deg; = top&rarr;bottom). With {@code scaled="1"} the
     * angle is authored in a squashed-to-square space; the absolute axis
     * obeys tan&theta;' = (w/h)&middot;tan&theta; (measured from the PDF
     * export: ang=45&deg; scaled on a 1.265:1 shape produces a 51.7&deg;
     * axis). The gradient ramp spans the projection of the whole shape
     * onto the axis, exactly like PowerPoint's ShadingType-2 coords.
     *
     * <p>Coordinates are normalised 0..1 (backends multiply by the fill
     * bbox), so only the {@code w:h} ratio of the arguments matters.
     */
    public static SurfacePaint.LinearGradient linearGradient(
            List<SurfacePaint.LinearGradient.Stop> stops,
            double angleDeg, boolean scaled, double w, double h) {
        if (w <= 0) w = 1;
        if (h <= 0) h = 1;
        double rad = Math.toRadians(angleDeg);
        double dx = Math.cos(rad);
        double dy = Math.sin(rad);
        if (scaled) {
            // tan(theta') = (w/h) * tan(theta)  ->  direction ∝ (h·cos, w·sin)
            dx = h * Math.cos(rad);
            dy = w * Math.sin(rad);
            double n = Math.hypot(dx, dy);
            if (n > 1e-12) { dx /= n; dy /= n; }
        }
        // Half-extent of the shape's projection onto the axis.
        double ext = 0.5 * (w * Math.abs(dx) + h * Math.abs(dy));
        return new SurfacePaint.LinearGradient(
            0.5 - ext * dx / w, 0.5 - ext * dy / h,
            0.5 + ext * dx / w, 0.5 + ext * dy / h,
            stops);
    }

    /**
     * a:path gradients.
     *
     * <p>{@code path="circle"}: rendered as a circle centered on the
     * shape with radius = half-diagonal. This matches PowerPoint's PDF
     * pipeline — the parity ground truth — which flattens circle-path
     * gradients to exactly that (ShadingType 3, unit circle, uniform
     * matrix centered on the shape rect) for BOTH centered and
     * corner-focused a:fillToRect values, so the fillToRect position is
     * intentionally not honored here. If screen-parity (where PowerPoint
     * does focus corner gradients) ever becomes the target, wire
     * fillToRect into the center/focus fields — the paint API already
     * carries them.
     *
     * <p>{@code path="rect"} / {@code path="shape"}: PowerPoint draws
     * concentric rectangle / shape-outline contours. Approximated as a
     * bbox-scaled (elliptical) radial centered on the fillToRect
     * midpoint: the last stop lands on the bbox edges through the
     * center and clamps beyond, which tracks the true contours closely
     * everywhere except near the corners.
     */
    private static SurfacePaint parsePathGradient(Element path,
                                                  List<SurfacePaint.LinearGradient.Stop> stops) {
        String kind = path.hasAttribute("path") ? path.getAttribute("path") : "shape";
        if ("circle".equals(kind)) {
            return new SurfacePaint.RadialGradient(0.5, 0.5, 0.5, 0.5,
                SurfacePaint.RadialGradient.Geometry.CIRCLE, stops);
        }
        double cx = 0.5;
        double cy = 0.5;
        Element ftr = getChild(path, "a:fillToRect");
        if (ftr != null) {
            double l = per100k(ftr, "l");
            double t = per100k(ftr, "t");
            double r = per100k(ftr, "r");
            double b = per100k(ftr, "b");
            cx = clamp01((l + (1.0 - r)) / 2.0);
            cy = clamp01((t + (1.0 - b)) / 2.0);
        }
        return new SurfacePaint.RadialGradient(cx, cy, cx, cy,
            SurfacePaint.RadialGradient.Geometry.ELLIPSE, stops);
    }

    // ========== PATTERN FILLS ==========

    /**
     * OOXML preset pattern tiles as 8x8 bitmaps, bit (y*8+x) set =
     * foreground. Extracted from PowerPoint's own rasterisation: its PDF
     * export embeds each preset as a 16x16 image (a 2x-doubled 8x8
     * bitmap) tiled at 6pt — parity-corpus/fills-pattern/deck.pdf covers
     * all 54 presets and these constants are that data, downsampled 2:1.
     */
    private static final Map<String, Long> PATTERN_BITS = new HashMap<>();
    static {
        PATTERN_BITS.put("cross", 0x01010101010101FFL);
        PATTERN_BITS.put("dashDnDiag", 0x0000884422110000L);
        PATTERN_BITS.put("dashHorz", 0x000000F00000000FL);
        PATTERN_BITS.put("dashUpDiag", 0x0000112244880000L);
        PATTERN_BITS.put("dashVert", 0x1010101001010101L);
        PATTERN_BITS.put("diagBrick", 0x8142241810204080L);
        PATTERN_BITS.put("diagCross", 0x8142241818244281L);
        PATTERN_BITS.put("divot", 0x0180010008100800L);
        PATTERN_BITS.put("dkDnDiag", 0x99CC663399CC6633L);
        PATTERN_BITS.put("dkHorz", 0x0000FFFF0000FFFFL);
        PATTERN_BITS.put("dkUpDiag", 0x993366CC993366CCL);
        PATTERN_BITS.put("dkVert", 0x3333333333333333L);
        PATTERN_BITS.put("dnDiag", 0x8040201008040201L);
        PATTERN_BITS.put("dotDmnd", 0x0044001000440001L);
        PATTERN_BITS.put("dotGrid", 0x0001000100010055L);
        PATTERN_BITS.put("horz", 0x00000000000000FFL);
        PATTERN_BITS.put("horzBrick", 0x101010FF010101FFL);
        PATTERN_BITS.put("lgCheck", 0xF0F0F0F00F0F0F0FL);
        PATTERN_BITS.put("lgConfetti", 0xB130031BD8C00C8DL);
        PATTERN_BITS.put("lgGrid", 0x01010101010101FFL);
        PATTERN_BITS.put("ltDnDiag", 0x8844221188442211L);
        PATTERN_BITS.put("ltHorz", 0x000000FF000000FFL);
        PATTERN_BITS.put("ltUpDiag", 0x1122448811224488L);
        PATTERN_BITS.put("ltVert", 0x1111111111111111L);
        PATTERN_BITS.put("narHorz", 0x00FF00FF00FF00FFL);
        PATTERN_BITS.put("narVert", 0xAAAAAAAAAAAAAAAAL);
        PATTERN_BITS.put("openDmnd", 0x8041221408142241L);
        PATTERN_BITS.put("pct10", 0x0010000100100001L);
        PATTERN_BITS.put("pct20", 0x0044001100440011L);
        PATTERN_BITS.put("pct25", 0x4411441144114411L);
        PATTERN_BITS.put("pct30", 0x8855225588552255L);
        PATTERN_BITS.put("pct40", 0xA855AA558A55AA55L);
        PATTERN_BITS.put("pct5", 0x0000001000000001L);
        PATTERN_BITS.put("pct50", 0xAA55AA55AA55AA55L);
        PATTERN_BITS.put("pct60", 0xAADDAA77AADDAA77L);
        PATTERN_BITS.put("pct70", 0xBBEEBBEEBBEEBBEEL);
        PATTERN_BITS.put("pct75", 0xFFBBFFEEFFBBFFEEL);
        PATTERN_BITS.put("pct80", 0xFF7FFFF7FF7FFFF7L);
        PATTERN_BITS.put("pct90", 0xFEFFFFFFEFFFFFFFL);
        PATTERN_BITS.put("plaid", 0x0F0F0F0FAA55AA55L);
        PATTERN_BITS.put("shingle", 0x808040300C1221C0L);
        PATTERN_BITS.put("smCheck", 0x9966669999666699L);
        PATTERN_BITS.put("smConfetti", 0x2004800840021001L);
        PATTERN_BITS.put("smGrid", 0x111111FF111111FFL);
        PATTERN_BITS.put("solidDmnd", 0x00081C3E7F3E1C08L);
        PATTERN_BITS.put("sphere", 0x1F1F19EEF1F191EEL);
        PATTERN_BITS.put("trellis", 0x99FF66FF99FF66FFL);
        PATTERN_BITS.put("upDiag", 0x0102040810204080L);
        PATTERN_BITS.put("vert", 0x0101010101010101L);
        PATTERN_BITS.put("wave", 0x03A4180003A41800L);
        PATTERN_BITS.put("wdDnDiag", 0xC1E070381C0E0783L);
        PATTERN_BITS.put("wdUpDiag", 0x83070E1C3870E0C1L);
        PATTERN_BITS.put("weave", 0x8A442811A2442A11L);
        PATTERN_BITS.put("zigZag", 0x1824428118244281L);
    }

    /**
     * a:pattFill: preset name + fgClr/bgClr. Unknown presets draw as
     * pct50 so a pattern-filled shape never silently loses its fill.
     */
    private static SurfacePaint parsePatternFill(Element pattFill, SlideRenderContext slideCtx) {
        String prst = pattFill.hasAttribute("prst") ? pattFill.getAttribute("prst") : "pct5";
        Long bits = PATTERN_BITS.get(prst);
        if (bits == null) bits = PATTERN_BITS.get("pct50");

        SurfacePaint.Solid fg = SurfacePaint.Solid.rgb(0, 0, 0);
        SurfacePaint.Solid bg = SurfacePaint.Solid.rgb(255, 255, 255);
        Element fgClr = getChild(pattFill, "a:fgClr");
        if (fgClr != null) {
            try { fg = parseColorElement(fgClr, slideCtx); } catch (IllegalStateException ignored) {}
        }
        Element bgClr = getChild(pattFill, "a:bgClr");
        if (bgClr != null) {
            try { bg = parseColorElement(bgClr, slideCtx); } catch (IllegalStateException ignored) {}
        }
        return new SurfacePaint.PatternFill(bits, fg, bg);
    }

    // ========== PICTURE FILLS ==========

    private static final double EMU_PER_PIXEL = 9525.0;

    /**
     * a:blipFill inside p:spPr: picture fill on a shape. Resolves the
     * blip relationship via the shared {@link BlipFillResolver} pipeline
     * (same decode + cache the p:pic renderer uses). Honors a:stretch
     * (image over the shape bounds) and a:tile with sx/sy (tile at
     * native pixel size at 96 DPI, scaled by sx/sy, anchored at the
     * shape's top-left — matching PowerPoint's PDF export). Tile
     * tx/ty/flip/algn and a:srcRect cropping are not yet honored.
     * Non-rectangular shapes clip the fill to their geometry because
     * backends materialise this as a texture paint applied to the
     * shape's own fill path.
     */
    private static SurfacePaint parseBlipFill(Element blipFill, SlideShape shape,
                                              SlideRenderContext slideCtx, RenderSurface surface) {
        if (surface == null || slideCtx == null || slideCtx.getDocument() == null) {
            return SurfacePaint.Transparent.INSTANCE;
        }
        Element blip = getChild(blipFill, "a:blip");
        if (blip == null) return SurfacePaint.Transparent.INSTANCE;
        String rId = blip.getAttribute("r:embed");
        if (rId == null || rId.isEmpty()) {
            rId = blip.getAttributeNS(
                "http://schemas.openxmlformats.org/officeDocument/2006/relationships", "embed");
        }
        if (rId == null || rId.isEmpty()) return SurfacePaint.Transparent.INSTANCE;

        String partName = BlipFillResolver.resolveMediaPartName(
            slideCtx.getDocument(), slideCtx.getSlideNumber(), rId);
        if (partName == null) return SurfacePaint.Transparent.INSTANCE;
        SurfaceImage image = BlipFillResolver.loadCached(surface, slideCtx.getDocument(), partName);
        if (image == null) return SurfacePaint.Transparent.INSTANCE;

        Element tile = getChild(blipFill, "a:tile");
        ShapeGeometry geom = shape.getGeometry();
        if (tile != null && geom != null && geom.getWidth() > 0 && geom.getHeight() > 0) {
            // Tile geometry is defined against the image's NATIVE pixel
            // size; the decoded raster may be subsampled for small
            // surfaces, so read dimensions from the header.
            int[] nativeSize = BlipFillResolver.nativeSize(slideCtx.getDocument(), partName);
            double imgW = nativeSize != null ? nativeSize[0] : image.widthPx();
            double imgH = nativeSize != null ? nativeSize[1] : image.heightPx();
            if (imgW <= 0 || imgH <= 0) return new SurfacePaint.ImageFill(image, false, 0, 0);
            double sx = per100kAttr(tile, "sx", 100000);
            double sy = per100kAttr(tile, "sy", 100000);
            // One tile = native pixels at 96 DPI (9525 EMU/px) scaled by
            // sx/sy, expressed as a fraction of the shape so it stays
            // correct at any render zoom.
            double fracW = imgW * EMU_PER_PIXEL * sx / geom.getWidth();
            double fracH = imgH * EMU_PER_PIXEL * sy / geom.getHeight();
            if (fracW > 0 && fracH > 0) {
                return new SurfacePaint.ImageFill(image, true, fracW, fracH);
            }
        }
        return new SurfacePaint.ImageFill(image, false, 0, 0);
    }

    // ========== COLOR PARSING ==========

    /**
     * Parse a color from an OOXML fill element (contains a:srgbClr,
     * a:schemeClr, or a:sysClr) and run ALL modifier children (lumMod,
     * lumOff, shade, tint, satMod, hueMod, alpha, alphaMod, alphaOff)
     * through {@link ColorTransforms}. Every color consumer routes
     * through here: solid fills, line colors, gradient stops, pattern
     * fg/bg, shadow colors.
     */
    private static SurfacePaint.Solid parseColorElement(Element fillElement, SlideRenderContext slideCtx) {
        Element srgb = getChild(fillElement, "a:srgbClr");
        if (srgb != null && srgb.hasAttribute("val")) {
            return applyDomModifiers(srgb.getAttribute("val"), srgb);
        }

        Element scheme = getChild(fillElement, "a:schemeClr");
        if (scheme != null && scheme.hasAttribute("val") && slideCtx != null) {
            return applyDomModifiers(
                slideCtx.resolveSchemeColor(scheme.getAttribute("val")), scheme);
        }

        Element sys = getChild(fillElement, "a:sysClr");
        if (sys != null && sys.hasAttribute("lastClr")) {
            return applyDomModifiers(sys.getAttribute("lastClr"), sys);
        }

        throw new IllegalStateException("Color element has no a:srgbClr or a:schemeClr child");
    }

    /** Apply the modifier children of {@code colorEl} to {@code baseHex}. */
    private static SurfacePaint.Solid applyDomModifiers(String baseHex, Element colorEl) {
        List<ColorTransforms.Modifier> mods = ColorTransforms.modifiersFromDom(colorEl);
        if (mods.isEmpty()) return solidFromHex(baseHex);
        try {
            ColorTransforms.ResolvedColor res = ColorTransforms.apply(baseHex, mods);
            SurfacePaint.Solid s = solidFromHex(res.hex());
            return res.alpha() < 1.0 ? s.withAlpha(res.alpha()) : s;
        } catch (RuntimeException e) {
            return solidFromHex(baseHex); // malformed base hex: keep it tolerant
        }
    }

    /**
     * Convert a core {@link ResolvedFill} (theme fmtScheme result) to a
     * {@link SurfacePaint}, producing the same paint the direct-XML path
     * would for equivalent markup.
     */
    private static SurfacePaint convertToSurfacePaint(ResolvedFill fill,
                                                      double aspectW, double aspectH) {
        return switch (fill) {
            case ResolvedFill.SolidFill solid -> {
                SurfacePaint.Solid c = solidFromHex(solid.hex());
                yield solid.alpha() < 1.0 ? c.withAlpha(solid.alpha()) : c;
            }
            case ResolvedFill.GradientFill grad ->
                themeGradientPaint(grad.stops(), grad.angleDegrees(), grad.type(),
                    aspectW, aspectH);
            case ResolvedFill.NoFill ignored -> SurfacePaint.Transparent.INSTANCE;
            // BlipFill (image) isn't rendered here yet — shape fill for
            // theme-blip references falls back to transparent until the
            // picture-rendering path on the renderer takes over. This
            // was previously a crash site; now a graceful degradation.
            case ResolvedFill.BlipFill ignored -> SurfacePaint.Transparent.INSTANCE;
        };
    }

    /**
     * Build a gradient paint from theme-resolved gradient stops. Shared
     * by the p:style fillRef path and gradient slide backgrounds.
     * Theme a:lin elements overwhelmingly author scaled="1", which
     * FmtSchemeModel doesn't carry — treated as scaled.
     */
    public static SurfacePaint themeGradientPaint(List<ResolvedFill.GradientStop> themeStops,
                                                  double angleDegrees,
                                                  ResolvedFill.GradientType type,
                                                  double aspectW, double aspectH) {
        List<SurfacePaint.LinearGradient.Stop> stops = new ArrayList<>();
        for (ResolvedFill.GradientStop gs : themeStops) {
            SurfacePaint.Solid c = solidFromHex(gs.hex());
            if (gs.alpha() < 1.0) c = c.withAlpha(gs.alpha());
            stops.add(new SurfacePaint.LinearGradient.Stop(gs.position(), c));
        }
        return assembleGradient(stops, angleDegrees,
            type == ResolvedFill.GradientType.PATH, aspectW, aspectH);
    }

    /**
     * Build the paint for a {@link com.excudo.core.model.SlideBackground.Gradient}
     * (theme bgRef gradient) at the given slide bounds.
     */
    public static SurfacePaint backgroundGradientPaint(
            com.excudo.core.model.SlideBackground.Gradient bg, double wPx, double hPx) {
        List<SurfacePaint.LinearGradient.Stop> stops = new ArrayList<>();
        for (var gs : bg.stops()) {
            SurfacePaint.Solid c = solidFromHex(gs.hex());
            if (gs.alpha() < 1.0) c = c.withAlpha(gs.alpha());
            stops.add(new SurfacePaint.LinearGradient.Stop(gs.position(), c));
        }
        return assembleGradient(stops, bg.angleDegrees(), bg.radial(), wPx, hPx);
    }

    private static SurfacePaint assembleGradient(List<SurfacePaint.LinearGradient.Stop> stops,
                                                 double angleDegrees, boolean radial,
                                                 double aspectW, double aspectH) {
        if (stops.isEmpty()) return SurfacePaint.Transparent.INSTANCE;
        if (stops.size() == 1) return stops.get(0).color();
        stops.sort(Comparator.comparingDouble(SurfacePaint.LinearGradient.Stop::position));
        List<SurfacePaint.LinearGradient.Stop> expanded = GradientStops.expand(stops);
        if (radial) {
            return new SurfacePaint.RadialGradient(0.5, 0.5, 0.5, 0.5,
                SurfacePaint.RadialGradient.Geometry.CIRCLE, expanded);
        }
        return linearGradient(expanded, angleDegrees, /*scaled*/ true, aspectW, aspectH);
    }

    /**
     * Tolerant hex parser. Accepts "#RRGGBB", "RRGGBB", "#AARRGGBB",
     * "AARRGGBB" (or null/empty/garbage -> opaque black). Never throws.
     */
    private static SurfacePaint.Solid solidFromHex(String hex) {
        if (hex == null || hex.isEmpty()) return SurfacePaint.Solid.rgb(0, 0, 0);
        try {
            return SurfacePaint.Solid.fromHex(hex);
        } catch (Exception e) {
            return SurfacePaint.Solid.rgb(0, 0, 0);
        }
    }

    /** Read a 100,000ths attribute as a 0..1 fraction; missing/garbage = 0. */
    private static double per100k(Element el, String attr) {
        return per100kAttr(el, attr, 0);
    }

    private static double per100kAttr(Element el, String attr, int defaultVal) {
        int v = defaultVal;
        if (el.hasAttribute(attr)) {
            try { v = Integer.parseInt(el.getAttribute(attr)); }
            catch (NumberFormatException ignored) {}
        }
        return v / 100000.0;
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private static Element getChild(Element parent, String tagName) {
        for (org.w3c.dom.Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element el && tagName.equals(el.getTagName())) {
                return el;
            }
        }
        return null;
    }

    /**
     * Extract outer shadow properties from a shape's effectLst.
     * Returns null if no shadow is defined.
     */
    public static ShadowStyle resolveShadow(SlideShape shape, SlideRenderContext slideCtx) {
        if (shape == null || shape.getXmlElement() == null) return null;
        Element spPr = getChild(shape.getXmlElement(), "p:spPr");
        Element effectLst = spPr != null ? getChild(spPr, "a:effectLst") : null;
        Element outerShdw = effectLst != null ? getChild(effectLst, "a:outerShdw") : null;
        if (outerShdw == null) {
            // No explicit effect list: PowerPoint falls back to the
            // p:style effectRef -- the soft drop shadow every default-
            // styled shape carries (theme effectStyle idx 2).
            return resolveStyleRefShadow(shape, slideCtx);
        }

        // Distance, direction, blur
        double distEmu = 0;
        double dirDeg = 0;
        double blurEmu = 0;
        if (outerShdw.hasAttribute("dist")) {
            try { distEmu = Double.parseDouble(outerShdw.getAttribute("dist")); }
            catch (NumberFormatException ignored) {}
        }
        if (outerShdw.hasAttribute("dir")) {
            try { dirDeg = Integer.parseInt(outerShdw.getAttribute("dir")) / 60000.0; }
            catch (NumberFormatException ignored) {}
        }
        if (outerShdw.hasAttribute("blurRad")) {
            try { blurEmu = Double.parseDouble(outerShdw.getAttribute("blurRad")); }
            catch (NumberFormatException ignored) {}
        }

        double distPx = distEmu / 914400.0 * 96.0;
        double offsetX = distPx * Math.cos(Math.toRadians(dirDeg));
        double offsetY = distPx * Math.sin(Math.toRadians(dirDeg));

        // Shadow color (default: semi-transparent black at 40% opacity)
        SurfacePaint.Solid shadowColor = SurfacePaint.Solid.rgba(0, 0, 0, 0.4);
        Element colorEl = getChild(outerShdw, "a:srgbClr");
        if (colorEl == null) colorEl = getChild(outerShdw, "a:schemeClr");
        if (colorEl != null) {
            SurfacePaint parsed = parseColorElement(outerShdw, slideCtx);
            if (parsed instanceof SurfacePaint.Solid s) {
                shadowColor = s;
                // If no alpha was set on the color, apply default shadow alpha
                if (shadowColor.alpha() == 0xFF) {
                    shadowColor = shadowColor.withAlpha(0.4);
                }
            }
        }

        return new ShadowStyle(offsetX, offsetY, shadowColor, blurEmu / 914400.0 * 96.0);
    }

    /** Shadow from p:style effectRef via the theme effectStyleLst, or null. */
    private static ShadowStyle resolveStyleRefShadow(SlideShape shape, SlideRenderContext slideCtx) {
        if (slideCtx == null || !ThemeManager.isThemeLoaded()) return null;
        Element pStyle = getChild(shape.getXmlElement(), "p:style");
        Element effectRef = pStyle != null ? getChild(pStyle, "a:effectRef") : null;
        if (effectRef == null) return null;

        int idx = 0;
        try { idx = Integer.parseInt(effectRef.getAttribute("idx")); }
        catch (NumberFormatException ignored) {}
        if (idx <= 0) return null;

        // The ref's color (with modifiers) is the phClr input; theme
        // shadows are usually literal srgbClr black so it rarely matters.
        String phColorHex = "#000000";
        Element schemeClr = getChild(effectRef, "a:schemeClr");
        if (schemeClr != null && schemeClr.hasAttribute("val")) {
            phColorHex = slideCtx.resolveSchemeColor(schemeClr.getAttribute("val"));
            var mods = com.excudo.core.color.ColorTransforms.modifiersFromDom(schemeClr);
            if (!mods.isEmpty()) {
                phColorHex = com.excudo.core.color.ColorTransforms.apply(phColorHex, mods).hex();
            }
        }

        FmtSchemeResolver.ResolvedShadow resolved;
        try {
            resolved = ThemeManager.resolveEffectStyle(idx, phColorHex);
        } catch (IllegalStateException | IllegalArgumentException e) {
            return null; // theme without fmtScheme effects
        }
        if (resolved == null) return null;

        double distPx = resolved.distEmu() / 914400.0 * 96.0;
        double dirDeg = resolved.directionDegrees();
        SurfacePaint.Solid color = solidFromHex(resolved.colorHex());
        if (resolved.alpha() < 1.0) {
            color = color.withAlpha(resolved.alpha());
        }
        return new ShadowStyle(
            distPx * Math.cos(Math.toRadians(dirDeg)),
            distPx * Math.sin(Math.toRadians(dirDeg)),
            color,
            resolved.blurRadEmu() / 914400.0 * 96.0);
    }

    /**
     * Immutable line style result: paint, width, optional dash array,
     * cap/join/miter-limit, compound layout, and head/tail line ends.
     */
    public record LineStyle(SurfacePaint.Solid color, double widthPixels, double[] dashPattern,
                            StrokeCap cap, StrokeJoin join, double miterLimit,
                            String compound, LineEnd headEnd, LineEnd tailEnd) {
        public static final LineStyle NONE = new LineStyle(
            SurfacePaint.Solid.rgba(0, 0, 0, 0), 0, null,
            StrokeCap.BUTT, StrokeJoin.ROUND, 10, "sng", LineEnd.NONE, LineEnd.NONE);
        /** Visible if the line has positive width and a non-transparent color. */
        public boolean isVisible() { return widthPixels > 0 && color.alpha() > 0; }
        /** True when either end carries an arrowhead. */
        public boolean hasEnds() { return !headEnd.isNone() || !tailEnd.isNone(); }
    }

    /**
     * Immutable shadow style result. {@code blurPx} is the outerShdw
     * blur radius in pixels (0 = hard shadow); renderers approximate
     * the gaussian falloff.
     */
    public record ShadowStyle(double offsetX, double offsetY, SurfacePaint.Solid color,
                              double blurPx) {}
}
