package com.excudo.view.rendering;

import com.excudo.core.model.SlideShape;
import com.excudo.core.model.TextRun;
import com.excudo.core.model.TextColor;
import com.excudo.core.themes.FmtSchemeResolver;
import com.excudo.core.themes.ResolvedFill;
import com.excudo.core.themes.ThemeManager;
import com.excudo.core.rendering.surface.SurfacePaint;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
        if (shape == null || shape.getXmlElement() == null) {
            return SurfacePaint.Transparent.INSTANCE;
        }

        Element spEl = shape.getXmlElement();

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
                return parseGradientFill(gradFill, slideCtx);
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

                Element schemeClr = getChild(fillRef, "a:schemeClr");
                if (schemeClr != null && schemeClr.hasAttribute("val")) {
                    String phColorHex = slideCtx.resolveSchemeColor(schemeClr.getAttribute("val"));

                    if (idx > 0 && ThemeManager.isThemeLoaded()) {
                        ResolvedFill resolved = ThemeManager.resolveFillStyle(idx, phColorHex);
                        return convertToSurfacePaint(resolved);
                    }

                    // No fmtScheme -- flat scheme color
                    SurfacePaint.Solid base = solidFromHex(phColorHex);
                    return applyAlpha(base, schemeClr);
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
        SurfacePaint.Solid lineColor;
        if (solidFill != null) {
            SurfacePaint parsed = parseColorElement(solidFill, slideCtx);
            lineColor = parsed instanceof SurfacePaint.Solid s ? s : SurfacePaint.Solid.rgb(0, 0, 0);
        } else {
            // Check p:style lnRef -- resolve color through fmtScheme
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
                    lineColor = solidFromHex(resolved.colorHex());
                    if (resolved.alpha() < 1.0) {
                        lineColor = lineColor.withAlpha(resolved.alpha());
                    }
                } else if (schemeClr != null && schemeClr.hasAttribute("val")) {
                    lineColor = solidFromHex(slideCtx.resolveSchemeColor(schemeClr.getAttribute("val")));
                } else {
                    lineColor = SurfacePaint.Solid.rgb(0, 0, 0);
                }
            } else {
                lineColor = SurfacePaint.Solid.rgb(0, 0, 0);
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
     * Map OOXML dash style name to a pixel-space dash array (scaled by line width).
     */
    private static double[] mapDashStyle(String style, double lineWidth) {
        double u = Math.max(lineWidth, 1);
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
     * Parse a gradient fill into a {@link SurfacePaint.LinearGradient}.
     * Reads a:gsLst for color stops and a:lin for angle.
     */
    private static SurfacePaint parseGradientFill(Element gradFill, SlideRenderContext slideCtx) {
        Element gsLst = getChild(gradFill, "a:gsLst");
        if (gsLst == null) return SurfacePaint.Solid.rgb(211, 211, 211); // LIGHTGRAY

        // Collect gradient stops
        List<SurfacePaint.LinearGradient.Stop> stops = new ArrayList<>();
        NodeList gsNodes = gradFill.getElementsByTagName("a:gs");
        for (int i = 0; i < gsNodes.getLength(); i++) {
            Element gs = (Element) gsNodes.item(i);
            double pos = 0;
            if (gs.hasAttribute("pos")) {
                try { pos = Integer.parseInt(gs.getAttribute("pos")) / 100000.0; }
                catch (NumberFormatException ignored) {}
            }
            SurfacePaint color = parseColorElement(gs, slideCtx);
            SurfacePaint.Solid solid = color instanceof SurfacePaint.Solid s ? s : SurfacePaint.Solid.rgb(0, 0, 0);
            stops.add(new SurfacePaint.LinearGradient.Stop(pos, solid));
        }

        if (stops.isEmpty()) return SurfacePaint.Solid.rgb(211, 211, 211);
        if (stops.size() == 1) return stops.get(0).color();
        stops.sort(Comparator.comparingDouble(SurfacePaint.LinearGradient.Stop::position));

        // Gradient angle (a:lin/@ang in 60000ths of a degree, default 0 = left-to-right)
        double angleDeg = 0;
        Element lin = getChild(gradFill, "a:lin");
        if (lin != null && lin.hasAttribute("ang")) {
            try { angleDeg = Integer.parseInt(lin.getAttribute("ang")) / 60000.0; }
            catch (NumberFormatException ignored) {}
        }

        // Convert angle to start/end points (OOXML 0 = top-to-bottom, 90 = left-to-right).
        // Normalised 0..1 coordinates (same convention as JavaFX proportional=true).
        double angleRad = Math.toRadians(angleDeg);
        double startX = 0.5 - 0.5 * Math.sin(angleRad);
        double startY = 0.5 - 0.5 * Math.cos(angleRad);
        double endX = 0.5 + 0.5 * Math.sin(angleRad);
        double endY = 0.5 + 0.5 * Math.cos(angleRad);

        return new SurfacePaint.LinearGradient(startX, startY, endX, endY, stops);
    }

    /**
     * Parse a color from an OOXML fill element (contains a:srgbClr or a:schemeClr).
     * Handles alpha transparency via a:alpha child. Returns a {@link SurfacePaint.Solid}
     * (typed as {@link SurfacePaint} only to let gradient stops reuse this).
     */
    private static SurfacePaint parseColorElement(Element fillElement, SlideRenderContext slideCtx) {
        Element srgb = getChild(fillElement, "a:srgbClr");
        if (srgb != null && srgb.hasAttribute("val")) {
            SurfacePaint.Solid base = solidFromHex(srgb.getAttribute("val"));
            return applyAlpha(base, srgb);
        }

        Element scheme = getChild(fillElement, "a:schemeClr");
        if (scheme != null && scheme.hasAttribute("val") && slideCtx != null) {
            SurfacePaint.Solid base = solidFromHex(slideCtx.resolveSchemeColor(scheme.getAttribute("val")));
            return applyAlpha(base, scheme);
        }

        throw new IllegalStateException("Color element has no a:srgbClr or a:schemeClr child");
    }

    /**
     * Apply alpha transparency from a:alpha child element.
     * OOXML alpha: 0 = fully transparent, 100000 = fully opaque.
     */
    private static SurfacePaint.Solid applyAlpha(SurfacePaint.Solid color, Element colorElement) {
        Element alphaEl = getChild(colorElement, "a:alpha");
        if (alphaEl != null && alphaEl.hasAttribute("val")) {
            try {
                int alphaVal = Integer.parseInt(alphaEl.getAttribute("val"));
                double opacity = alphaVal / 100000.0;
                return color.withAlpha(opacity);
            } catch (NumberFormatException ignored) {}
        }
        return color;
    }

    /**
     * Convert a core {@link ResolvedFill} to a {@link SurfacePaint}.
     * Mirrors the logic that used to produce JavaFX types; output shape
     * is identical to what {@link #parseGradientFill} / {@link #parseColorElement}
     * would produce for equivalent XML.
     */
    private static SurfacePaint convertToSurfacePaint(ResolvedFill fill) {
        return switch (fill) {
            case ResolvedFill.SolidFill solid -> {
                SurfacePaint.Solid c = solidFromHex(solid.hex());
                yield solid.alpha() < 1.0 ? c.withAlpha(solid.alpha()) : c;
            }
            case ResolvedFill.GradientFill grad -> {
                List<SurfacePaint.LinearGradient.Stop> stops = new ArrayList<>();
                for (var gs : grad.stops()) {
                    SurfacePaint.Solid c = solidFromHex(gs.hex());
                    if (gs.alpha() < 1.0) c = c.withAlpha(gs.alpha());
                    stops.add(new SurfacePaint.LinearGradient.Stop(gs.position(), c));
                }
                if (stops.isEmpty()) yield SurfacePaint.Transparent.INSTANCE;
                if (stops.size() == 1) yield stops.get(0).color();
                stops.sort(Comparator.comparingDouble(SurfacePaint.LinearGradient.Stop::position));

                double angleRad = Math.toRadians(grad.angleDegrees());
                double startX = 0.5 - 0.5 * Math.sin(angleRad);
                double startY = 0.5 - 0.5 * Math.cos(angleRad);
                double endX = 0.5 + 0.5 * Math.sin(angleRad);
                double endY = 0.5 + 0.5 * Math.cos(angleRad);

                yield new SurfacePaint.LinearGradient(startX, startY, endX, endY, stops);
            }
            case ResolvedFill.NoFill ignored -> SurfacePaint.Transparent.INSTANCE;
            // BlipFill (image) isn't rendered here yet — shape fill for
            // theme-blip references falls back to transparent until the
            // picture-rendering path on the renderer takes over. This
            // was previously a crash site; now a graceful degradation.
            case ResolvedFill.BlipFill ignored -> SurfacePaint.Transparent.INSTANCE;
        };
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

        return new ShadowStyle(offsetX, offsetY, shadowColor);
    }

    /** Immutable line style result with optional dash pattern. */
    public record LineStyle(SurfacePaint.Solid color, double widthPixels, double[] dashPattern) {
        public static final LineStyle NONE = new LineStyle(
            SurfacePaint.Solid.rgba(0, 0, 0, 0), 0, null);
        /** Visible if the line has positive width and a non-transparent color. */
        public boolean isVisible() { return widthPixels > 0 && color.alpha() > 0; }
    }

    /** Immutable shadow style result. */
    public record ShadowStyle(double offsetX, double offsetY, SurfacePaint.Solid color) {}
}
