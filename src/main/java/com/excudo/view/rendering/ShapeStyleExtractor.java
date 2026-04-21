package com.excudo.view.rendering;

import com.excudo.core.model.SlideShape;
import com.excudo.core.model.TextRun;
import com.excudo.core.model.TextColor;
import com.excudo.core.themes.FmtSchemeResolver;
import com.excudo.core.themes.ResolvedFill;
import com.excudo.core.themes.ThemeManager;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Paint;
import javafx.scene.paint.Stop;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Extracts visual style properties from a shape's xmlElement for rendering.
 * Read-only -- does not modify the DOM. Returns JavaFX-ready Color objects.
 *
 * Resolves the OOXML style hierarchy:
 *   shape spPr solidFill > shape p:style fillRef > theme default
 */
public final class ShapeStyleExtractor {

    private ShapeStyleExtractor() {}

    /**
     * Resolve the fill for a shape. Returns Color (solid/transparent) or LinearGradient.
     * Checks: spPr/solidFill > gradFill > noFill > theme accent1 fallback.
     */
    public static Paint resolveFillColor(SlideShape shape, SlideRenderContext slideCtx) {
        if (shape == null || shape.getXmlElement() == null) {
            return Color.TRANSPARENT;
        }

        Element spEl = shape.getXmlElement();

        // Check for explicit fill in spPr
        Element spPr = getChild(spEl, "p:spPr");
        if (spPr != null) {
            // Check noFill
            if (getChild(spPr, "a:noFill") != null) {
                return Color.TRANSPARENT;
            }

            // Check solidFill
            Element solidFill = getChild(spPr, "a:solidFill");
            if (solidFill != null) {
                return parseColorElement(solidFill, slideCtx);
            }

            // Check gradFill
            Element gradFill = getChild(spPr, "a:gradFill");
            if (gradFill != null) {
                return parseGradientFill(gradFill, slideCtx);
            }
        }

        // If placeholder, default to transparent (inherits from layout/master)
        if (shape.getType() == SlideShape.ShapeType.PLACEHOLDER) {
            return Color.TRANSPARENT;
        }

        // Check p:style fillRef — resolve through fmtScheme for full gradient/modifier support
        Element pStyle = getChild(spEl, "p:style");
        if (pStyle != null) {
            Element fillRef = getChild(pStyle, "a:fillRef");
            if (fillRef != null && slideCtx != null) {
                int idx = 0;
                try { idx = Integer.parseInt(fillRef.getAttribute("idx")); }
                catch (NumberFormatException ignored) {}

                Element schemeClr = getChild(fillRef, "a:schemeClr");
                if (schemeClr != null && schemeClr.hasAttribute("val")) {
                    String phColorHex = slideCtx.resolveSchemeColor(schemeClr.getAttribute("val"));

                    // Use fmtScheme resolver for full fill style lookup
                    if (idx > 0 && ThemeManager.isThemeLoaded()) {
                        ResolvedFill resolved = ThemeManager.resolveFillStyle(idx, phColorHex);
                        return convertToJavaFX(resolved);
                    }

                    // No fmtScheme — flat scheme color
                    Color base = colorFromHex(phColorHex);
                    return applyAlpha(base, schemeClr);
                }
            }
        }

        throw new IllegalStateException("No fill resolved for shape '"
            + shape.getName() + "' (type=" + shape.getType() + "): no spPr fill, no p:style fillRef");
    }

    /**
     * Resolve line/border style for a shape.
     */
    public static LineStyle resolveLineStyle(SlideShape shape, SlideRenderContext slideCtx) {
        if (shape == null || shape.getXmlElement() == null) {
            return LineStyle.NONE;
        }

        Element spPr = getChild(shape.getXmlElement(), "p:spPr");
        if (spPr == null) return LineStyle.NONE;

        Element ln = getChild(spPr, "a:ln");
        if (ln == null) return LineStyle.NONE;

        // Check noFill on line
        if (getChild(ln, "a:noFill") != null) {
            return LineStyle.NONE;
        }

        // Width in EMUs (default 12700 = 1pt)
        double widthEmu = 12700;
        if (ln.hasAttribute("w")) {
            try {
                widthEmu = Double.parseDouble(ln.getAttribute("w"));
            } catch (NumberFormatException ignored) {}
        }
        // Convert EMU to approximate pixels (96 DPI)
        double widthPixels = widthEmu / 914400.0 * 96.0;

        // Line color
        Element solidFill = getChild(ln, "a:solidFill");
        Color lineColor;
        if (solidFill != null) {
            lineColor = parseColorElement(solidFill, slideCtx);
        } else {
            // Check p:style lnRef — resolve color through fmtScheme
            Element pStyle = getChild(shape.getXmlElement(), "p:style");
            Element lnRef = pStyle != null ? getChild(pStyle, "a:lnRef") : null;
            if (lnRef != null && slideCtx != null) {
                int idx = 0;
                try { idx = Integer.parseInt(lnRef.getAttribute("idx")); }
                catch (NumberFormatException ignored) {}

                Element schemeClr = getChild(lnRef, "a:schemeClr");
                if (schemeClr != null && schemeClr.hasAttribute("val") && idx > 0
                        && ThemeManager.isThemeLoaded()) {
                    String phColorHex = slideCtx.resolveSchemeColor(schemeClr.getAttribute("val"));
                    FmtSchemeResolver.ResolvedLineStyle resolved =
                        ThemeManager.resolveLineStyle(idx, phColorHex);
                    lineColor = colorFromHex(resolved.colorHex());
                    if (resolved.alpha() < 1.0) {
                        lineColor = lineColor.deriveColor(0, 1, 1, resolved.alpha());
                    }
                } else if (schemeClr != null && schemeClr.hasAttribute("val")) {
                    lineColor = colorFromHex(slideCtx.resolveSchemeColor(schemeClr.getAttribute("val")));
                } else {
                    lineColor = Color.BLACK;
                }
            } else {
                lineColor = Color.BLACK;
            }
        }

        // Dash pattern
        double[] dashPattern = null;
        Element prstDash = getChild(ln, "a:prstDash");
        if (prstDash != null && prstDash.hasAttribute("val")) {
            dashPattern = mapDashStyle(prstDash.getAttribute("val"), widthPixels);
        }

        return new LineStyle(lineColor, widthPixels, dashPattern);
    }

    /**
     * Map OOXML dash style name to JavaFX dash array (scaled by line width).
     */
    private static double[] mapDashStyle(String style, double lineWidth) {
        double u = Math.max(lineWidth, 1); // scale dashes by line width
        return switch (style) {
            case "dot"          -> new double[]{u, u * 2};
            case "dash"         -> new double[]{u * 4, u * 2};
            case "lgDash"       -> new double[]{u * 8, u * 2};
            case "dashDot"      -> new double[]{u * 4, u * 2, u, u * 2};
            case "lgDashDot"    -> new double[]{u * 8, u * 2, u, u * 2};
            case "lgDashDotDot" -> new double[]{u * 8, u * 2, u, u * 2, u, u * 2};
            case "sysDot"       -> new double[]{u, u};
            case "sysDash"      -> new double[]{u * 3, u};
            case "sysDashDot"   -> new double[]{u * 3, u, u, u};
            case "sysDashDotDot"-> new double[]{u * 3, u, u, u, u, u};
            default             -> null; // solid
        };
    }

    /**
     * Resolve a text run's rendered color.
     * Checks: run rPr solidFill > scheme color > theme default for placeholder type.
     */
    public static Color resolveTextRunColor(TextRun run, String placeholderType,
                                             SlideRenderContext slideCtx) {
        if (run != null && run.getColor() != null) {
            TextColor tc = run.getColor();
            if (tc.getHexVal() != null) {
                return colorFromHex("#" + tc.getHexVal());
            }
            if (tc.isScheme() && slideCtx != null) {
                return colorFromHex(slideCtx.resolveSchemeColor(tc.getSchemeVal()));
            }
        }

        // Default: resolve from theme via SlideRenderContext
        if (slideCtx != null) {
            if ("title".equals(placeholderType) || "ctrTitle".equals(placeholderType)) {
                return colorFromHex(slideCtx.getTitleTextColorHex());
            }
            return colorFromHex(slideCtx.getBodyTextColorHex());
        }
        throw new IllegalStateException("No text color: slideCtx is null, placeholder='" + placeholderType + "'");
    }

    // ========== INTERNAL ==========

    /**
     * Parse a gradient fill into a JavaFX LinearGradient.
     * Reads a:gsLst for color stops and a:lin for angle.
     */
    private static Paint parseGradientFill(Element gradFill, SlideRenderContext slideCtx) {
        Element gsLst = getChild(gradFill, "a:gsLst");
        if (gsLst == null) return Color.LIGHTGRAY;

        // Collect gradient stops
        List<Stop> stops = new ArrayList<>();
        NodeList gsNodes = gradFill.getElementsByTagName("a:gs");
        for (int i = 0; i < gsNodes.getLength(); i++) {
            Element gs = (Element) gsNodes.item(i);
            double pos = 0;
            if (gs.hasAttribute("pos")) {
                try { pos = Integer.parseInt(gs.getAttribute("pos")) / 100000.0; }
                catch (NumberFormatException ignored) {}
            }
            Color color = parseColorElement(gs, slideCtx);
            stops.add(new Stop(pos, color));
        }

        if (stops.isEmpty()) return Color.LIGHTGRAY;
        if (stops.size() == 1) return stops.get(0).getColor();
        stops.sort(Comparator.comparingDouble(Stop::getOffset));

        // Gradient angle (a:lin/@ang in 60000ths of a degree, default 0 = left-to-right)
        double angleDeg = 0;
        Element lin = getChild(gradFill, "a:lin");
        if (lin != null && lin.hasAttribute("ang")) {
            try { angleDeg = Integer.parseInt(lin.getAttribute("ang")) / 60000.0; }
            catch (NumberFormatException ignored) {}
        }

        // Convert angle to start/end points (OOXML 0 = top-to-bottom, 90 = left-to-right)
        double angleRad = Math.toRadians(angleDeg);
        double startX = 0.5 - 0.5 * Math.sin(angleRad);
        double startY = 0.5 - 0.5 * Math.cos(angleRad);
        double endX = 0.5 + 0.5 * Math.sin(angleRad);
        double endY = 0.5 + 0.5 * Math.cos(angleRad);

        return new LinearGradient(startX, startY, endX, endY, true,
            CycleMethod.NO_CYCLE, stops);
    }

    /**
     * Parse a color from an OOXML fill element (contains a:srgbClr or a:schemeClr).
     * Handles alpha transparency via a:alpha child.
     */
    private static Color parseColorElement(Element fillElement, SlideRenderContext slideCtx) {
        // Check srgbClr
        Element srgb = getChild(fillElement, "a:srgbClr");
        if (srgb != null && srgb.hasAttribute("val")) {
            Color base = colorFromHex("#" + srgb.getAttribute("val"));
            return applyAlpha(base, srgb);
        }

        // Check schemeClr
        Element scheme = getChild(fillElement, "a:schemeClr");
        if (scheme != null && scheme.hasAttribute("val") && slideCtx != null) {
            Color base = colorFromHex(slideCtx.resolveSchemeColor(scheme.getAttribute("val")));
            return applyAlpha(base, scheme);
        }

        throw new IllegalStateException("Color element has no a:srgbClr or a:schemeClr child");
    }

    /**
     * Apply alpha transparency from a:alpha child element.
     * OOXML alpha: 0 = fully transparent, 100000 = fully opaque.
     */
    private static Color applyAlpha(Color color, Element colorElement) {
        Element alphaEl = getChild(colorElement, "a:alpha");
        if (alphaEl != null && alphaEl.hasAttribute("val")) {
            try {
                int alphaVal = Integer.parseInt(alphaEl.getAttribute("val"));
                double opacity = alphaVal / 100000.0;
                return color.deriveColor(0, 1, 1, opacity);
            } catch (NumberFormatException ignored) {}
        }
        return color;
    }

    /**
     * Convert a core ResolvedFill to a JavaFX Paint.
     */
    private static Paint convertToJavaFX(ResolvedFill fill) {
        return switch (fill) {
            case ResolvedFill.SolidFill solid -> {
                Color c = colorFromHex(solid.hex());
                yield solid.alpha() < 1.0 ? c.deriveColor(0, 1, 1, solid.alpha()) : c;
            }
            case ResolvedFill.GradientFill grad -> {
                List<Stop> stops = new ArrayList<>();
                for (var gs : grad.stops()) {
                    Color c = colorFromHex(gs.hex());
                    if (gs.alpha() < 1.0) c = c.deriveColor(0, 1, 1, gs.alpha());
                    stops.add(new Stop(gs.position(), c));
                }
                if (stops.isEmpty()) yield Color.TRANSPARENT;
                if (stops.size() == 1) yield stops.get(0).getColor();
                stops.sort(Comparator.comparingDouble(Stop::getOffset));

                double angleRad = Math.toRadians(grad.angleDegrees());
                double startX = 0.5 - 0.5 * Math.sin(angleRad);
                double startY = 0.5 - 0.5 * Math.cos(angleRad);
                double endX = 0.5 + 0.5 * Math.sin(angleRad);
                double endY = 0.5 + 0.5 * Math.cos(angleRad);

                yield new LinearGradient(startX, startY, endX, endY, true,
                    CycleMethod.NO_CYCLE, stops);
            }
            case ResolvedFill.NoFill ignored -> Color.TRANSPARENT;
        };
    }

    private static Color colorFromHex(String hex) {
        try {
            if (hex == null || hex.isEmpty()) return Color.BLACK;
            if (!hex.startsWith("#")) hex = "#" + hex;
            return Color.web(hex);
        } catch (Exception e) {
            return Color.BLACK;
        }
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
        if (spPr == null) return null;
        Element effectLst = getChild(spPr, "a:effectLst");
        if (effectLst == null) return null;
        Element outerShdw = getChild(effectLst, "a:outerShdw");
        if (outerShdw == null) return null;

        // Distance and direction
        double distEmu = 0;
        double dirDeg = 0;
        if (outerShdw.hasAttribute("dist")) {
            try { distEmu = Double.parseDouble(outerShdw.getAttribute("dist")); }
            catch (NumberFormatException ignored) {}
        }
        if (outerShdw.hasAttribute("dir")) {
            try { dirDeg = Integer.parseInt(outerShdw.getAttribute("dir")) / 60000.0; }
            catch (NumberFormatException ignored) {}
        }

        double distPx = distEmu / 914400.0 * 96.0;
        double offsetX = distPx * Math.cos(Math.toRadians(dirDeg));
        double offsetY = distPx * Math.sin(Math.toRadians(dirDeg));

        // Shadow color (default: semi-transparent black)
        Color shadowColor = Color.color(0, 0, 0, 0.4);
        Element colorEl = getChild(outerShdw, "a:srgbClr");
        if (colorEl == null) colorEl = getChild(outerShdw, "a:schemeClr");
        if (colorEl != null) {
            shadowColor = parseColorElement(outerShdw, slideCtx);
            // If no alpha was set on the color, apply default shadow alpha
            if (shadowColor.getOpacity() >= 1.0) {
                shadowColor = shadowColor.deriveColor(0, 1, 1, 0.4);
            }
        }

        return new ShadowStyle(offsetX, offsetY, shadowColor);
    }

    /** Immutable line style result with optional dash pattern. */
    public record LineStyle(Color color, double widthPixels, double[] dashPattern) {
        public static final LineStyle NONE = new LineStyle(Color.TRANSPARENT, 0, null);
        public boolean isVisible() { return widthPixels > 0 && !color.equals(Color.TRANSPARENT); }
    }

    /** Immutable shadow style result. */
    public record ShadowStyle(double offsetX, double offsetY, Color color) {}

    // ========== BRIDGE ADAPTER (Phase B only; removed in Phase C) ==========
    //
    // Renderers have already been rewired to the RenderSurface API, but
    // this extractor still returns JavaFX Paint/Color to minimise the
    // scope of any single phase. Phase C rewrites the extractor to
    // produce SurfacePaint directly, and these helpers are deleted.

    /**
     * Convert a JavaFX Paint to the neutral SurfacePaint abstraction.
     * Handles Color and LinearGradient; anything else (RadialGradient,
     * ImagePattern -- neither produced by this extractor today) falls
     * back to transparent.
     */
    public static com.excudo.view.rendering.surface.SurfacePaint toSurfacePaint(
            javafx.scene.paint.Paint paint) {
        if (paint == null) {
            return com.excudo.view.rendering.surface.SurfacePaint.Transparent.INSTANCE;
        }
        if (paint instanceof Color c) {
            return toSurfacePaint(c);
        }
        if (paint instanceof javafx.scene.paint.LinearGradient lg) {
            java.util.List<com.excudo.view.rendering.surface.SurfacePaint.LinearGradient.Stop> stops =
                new java.util.ArrayList<>(lg.getStops().size());
            for (javafx.scene.paint.Stop s : lg.getStops()) {
                stops.add(new com.excudo.view.rendering.surface.SurfacePaint.LinearGradient.Stop(
                    s.getOffset(), toSurfacePaint(s.getColor())));
            }
            return new com.excudo.view.rendering.surface.SurfacePaint.LinearGradient(
                lg.getStartX(), lg.getStartY(), lg.getEndX(), lg.getEndY(), stops);
        }
        return com.excudo.view.rendering.surface.SurfacePaint.Transparent.INSTANCE;
    }

    /** Convenience: Color-typed overload. */
    public static com.excudo.view.rendering.surface.SurfacePaint.Solid toSurfacePaint(Color c) {
        if (c == null || Color.TRANSPARENT.equals(c)) {
            return com.excudo.view.rendering.surface.SurfacePaint.Solid.rgba(0, 0, 0, 0);
        }
        int r = (int) Math.round(c.getRed()   * 255);
        int g = (int) Math.round(c.getGreen() * 255);
        int b = (int) Math.round(c.getBlue()  * 255);
        return com.excudo.view.rendering.surface.SurfacePaint.Solid.rgba(r, g, b, c.getOpacity());
    }
}
