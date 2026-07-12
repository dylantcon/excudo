package com.excudo.core.color;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.List;

/**
 * Canonical OOXML DrawingML color-modifier math. The ONE implementation
 * shared by the theme fillRef path ({@code ColorModifierUtils} delegates
 * here) and the direct-fill path ({@code ShapeStyleExtractor}).
 *
 * <h2>Color spaces (verified against PowerPoint's own PDF export)</h2>
 * The parity ground truth (PowerPoint COM &rarr; PDF &rarr; raster) exposes the
 * exact RGB values PowerPoint computes for each modifier, because the PDF
 * content stream carries the flattened fill colors. Sampling the
 * fills-solid-theme corpus deck established:
 * <ul>
 *   <li>{@code shade}/{@code tint} multiply toward black / blend toward
 *       white in <b>linearized sRGB</b> (piecewise IEC 61966-2-1 transfer
 *       function). E.g. accent1 4F81BD with shade 50000 exports as
 *       #385D8A &mdash; the linear-space result &mdash; not #28415F (naive RGB
 *       multiply) and not an HSL blend.</li>
 *   <li>{@code lumMod}/{@code lumOff}/{@code satMod}/{@code hueMod}
 *       operate on standard HSL components (gamma-encoded RGB basis),
 *       double precision throughout. E.g. 4F81BD lumMod 75000 &rarr;
 *       #376092, lumMod 60000 + lumOff 40000 &rarr; #95B3D7 &mdash; both match
 *       the PDF exactly.</li>
 *   <li>Modifiers chain in document order on an <b>unquantized</b> color
 *       state; rounding to bytes happens once at the end (shade 75000 +
 *       satMod 150000 on 4F81BD &rarr; #2C6FBE matches only without
 *       intermediate rounding).</li>
 * </ul>
 *
 * <h2>Gradient interpolation</h2>
 * PowerPoint's PDF export bakes gradient ramps into sampled functions.
 * Decoding those LUTs (fills-gradient corpus): adjacent stops interpolate
 * in <b>gamma-2.2-linearized RGB</b> with a <b>sinusoidal ease</b>
 * (raised-cosine) on the position axis. {@link #expandGradientStops}
 * reproduces that curve by inserting sub-stops, so both the AWT and the
 * JavaFX backend (which interpolate linearly in sRGB) draw the same ramp
 * PowerPoint does.
 */
public final class ColorTransforms {

    private ColorTransforms() {}

    /** One OOXML color modifier: element local name + val attribute
     *  (100,000ths; 50000 = 50%). */
    public record Modifier(String name, int value) {}

    /** Result of a modifier chain: '#RRGGBB' hex plus opacity 0..1. */
    public record ResolvedColor(String hex, double alpha) {}

    /** Modifier element names we apply. Anything else is skipped (same
     *  tolerant behavior the theme path always had). */
    private static final java.util.Set<String> KNOWN_MODIFIERS = java.util.Set.of(
        "lumMod", "lumOff", "shade", "tint", "satMod", "hueMod",
        "alpha", "alphaMod", "alphaOff");

    // ====================================================================
    // Modifier chain
    // ====================================================================

    /**
     * Apply a modifier chain in list order. The working color is kept as
     * unquantized doubles; byte rounding happens once at the end.
     */
    public static ResolvedColor apply(String baseHex, List<Modifier> modifiers) {
        int[] rgbInt = parseHex(baseHex);
        // Working state: gamma-encoded sRGB channels 0..1.
        double r = rgbInt[0] / 255.0;
        double g = rgbInt[1] / 255.0;
        double b = rgbInt[2] / 255.0;
        double alpha = 1.0;

        for (Modifier mod : modifiers) {
            double v = mod.value() / 100000.0;
            switch (mod.name()) {
                case "shade" -> {
                    r = srgbEncode(srgbDecode(r) * v);
                    g = srgbEncode(srgbDecode(g) * v);
                    b = srgbEncode(srgbDecode(b) * v);
                }
                case "tint" -> {
                    r = srgbEncode(srgbDecode(r) * v + (1.0 - v));
                    g = srgbEncode(srgbDecode(g) * v + (1.0 - v));
                    b = srgbEncode(srgbDecode(b) * v + (1.0 - v));
                }
                case "lumMod" -> {
                    double[] hsl = rgbToHsl(r, g, b);
                    hsl[2] = clamp01(hsl[2] * v);
                    double[] rgb = hslToRgb(hsl[0], hsl[1], hsl[2]);
                    r = rgb[0]; g = rgb[1]; b = rgb[2];
                }
                case "lumOff" -> {
                    double[] hsl = rgbToHsl(r, g, b);
                    hsl[2] = clamp01(hsl[2] + v);
                    double[] rgb = hslToRgb(hsl[0], hsl[1], hsl[2]);
                    r = rgb[0]; g = rgb[1]; b = rgb[2];
                }
                case "satMod" -> {
                    double[] hsl = rgbToHsl(r, g, b);
                    hsl[1] = clamp01(hsl[1] * v);
                    double[] rgb = hslToRgb(hsl[0], hsl[1], hsl[2]);
                    r = rgb[0]; g = rgb[1]; b = rgb[2];
                }
                case "hueMod" -> {
                    double[] hsl = rgbToHsl(r, g, b);
                    hsl[0] = ((hsl[0] * v) % 360.0 + 360.0) % 360.0;
                    double[] rgb = hslToRgb(hsl[0], hsl[1], hsl[2]);
                    r = rgb[0]; g = rgb[1]; b = rgb[2];
                }
                case "alpha" -> alpha = clamp01(v);
                case "alphaMod" -> alpha = clamp01(alpha * v);
                case "alphaOff" -> alpha = clamp01(alpha + v);
                default -> { /* unknown modifier: skip */ }
            }
        }

        return new ResolvedColor(toHex(r, g, b), alpha);
    }

    /**
     * Collect the modifier children of a DrawingML color element
     * ({@code a:srgbClr} / {@code a:schemeClr} / {@code a:sysClr} / ...),
     * in document order. Children without a parseable {@code val} and
     * names outside the supported set are skipped.
     */
    public static List<Modifier> modifiersFromDom(Element colorElement) {
        List<Modifier> mods = new ArrayList<>();
        if (colorElement == null) return mods;
        for (Node n = colorElement.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (!(n instanceof Element el)) continue;
            String name = el.getLocalName();
            if (name == null) {
                String tag = el.getTagName();
                int colon = tag.indexOf(':');
                name = colon >= 0 ? tag.substring(colon + 1) : tag;
            }
            if (!KNOWN_MODIFIERS.contains(name) || !el.hasAttribute("val")) continue;
            try {
                mods.add(new Modifier(name, Integer.parseInt(el.getAttribute("val"))));
            } catch (NumberFormatException ignored) { /* malformed val: skip */ }
        }
        return mods;
    }

    // ====================================================================
    // Gradient interpolation primitives (PowerPoint ramp curve)
    // ====================================================================

    /**
     * PowerPoint's gradient position easing: raised-cosine (sinusoidal
     * ease-in-out), decoded from the sampled-function LUTs its PDF export
     * writes. {@code GradientStops} uses this together with
     * {@link #gammaLerpArgb} to expand stop pairs.
     */
    public static double sineEase(double t) {
        return (1.0 - Math.cos(Math.PI * clamp01(t))) / 2.0;
    }

    /**
     * Interpolate two packed 0xAARRGGBB colors at fraction {@code t} in
     * gamma-2.2-linearised RGB (alpha interpolates linearly) — the color
     * curve PowerPoint's gradient LUTs follow.
     */
    public static int gammaLerpArgb(int argb0, int argb1, double t) {
        int r = (int) Math.round(gammaLerpChannel((argb0 >>> 16) & 0xFF, (argb1 >>> 16) & 0xFF, t));
        int g = (int) Math.round(gammaLerpChannel((argb0 >>> 8) & 0xFF, (argb1 >>> 8) & 0xFF, t));
        int b = (int) Math.round(gammaLerpChannel(argb0 & 0xFF, argb1 & 0xFF, t));
        int a0 = (argb0 >>> 24) & 0xFF;
        int a1 = (argb1 >>> 24) & 0xFF;
        int a = (int) Math.round(a0 + (a1 - a0) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static double gammaLerpChannel(int v0, int v1, double t) {
        double l0 = Math.pow(v0 / 255.0, 2.2);
        double l1 = Math.pow(v1 / 255.0, 2.2);
        return 255.0 * Math.pow(l0 + (l1 - l0) * t, 1.0 / 2.2);
    }

    // ====================================================================
    // sRGB transfer function (piecewise, IEC 61966-2-1)
    // ====================================================================

    /** Gamma-encoded sRGB channel (0..1) to linear light. */
    public static double srgbDecode(double c) {
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    /** Linear-light channel (0..1) to gamma-encoded sRGB. */
    public static double srgbEncode(double c) {
        c = clamp01(c);
        return c <= 0.0031308 ? c * 12.92 : 1.055 * Math.pow(c, 1.0 / 2.4) - 0.055;
    }

    // ====================================================================
    // HSL conversion (double precision, no quantization)
    // ====================================================================

    /** Gamma-encoded RGB channels (0..1) to HSL [h: 0-360, s: 0-1, l: 0-1]. */
    public static double[] rgbToHsl(double r, double g, double b) {
        double max = Math.max(r, Math.max(g, b));
        double min = Math.min(r, Math.min(g, b));
        double l = (max + min) / 2.0;
        if (max == min) return new double[]{0, 0, l};

        double d = max - min;
        double s = l > 0.5 ? d / (2.0 - max - min) : d / (max + min);
        double h;
        if (max == r) {
            h = (g - b) / d + (g < b ? 6 : 0);
        } else if (max == g) {
            h = (b - r) / d + 2;
        } else {
            h = (r - g) / d + 4;
        }
        return new double[]{h * 60.0, s, l};
    }

    /** HSL [h: 0-360, s: 0-1, l: 0-1] to gamma-encoded RGB channels (0..1). */
    public static double[] hslToRgb(double h, double s, double l) {
        if (s == 0) return new double[]{l, l, l};
        double q = l < 0.5 ? l * (1 + s) : l + s - l * s;
        double p = 2 * l - q;
        double hn = h / 360.0;
        return new double[]{
            hueToRgb(p, q, hn + 1.0 / 3.0),
            hueToRgb(p, q, hn),
            hueToRgb(p, q, hn - 1.0 / 3.0)
        };
    }

    private static double hueToRgb(double p, double q, double t) {
        if (t < 0) t += 1;
        if (t > 1) t -= 1;
        if (t < 1.0 / 6.0) return p + (q - p) * 6 * t;
        if (t < 1.0 / 2.0) return q;
        if (t < 2.0 / 3.0) return p + (q - p) * (2.0 / 3.0 - t) * 6;
        return p;
    }

    // ====================================================================
    // Hex helpers
    // ====================================================================

    /** '#'-tolerant RRGGBB parser to int[3]. */
    public static int[] parseHex(String hex) {
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        if (h.length() != 6) {
            throw new IllegalArgumentException("Invalid hex color: " + hex);
        }
        return new int[]{
            Integer.parseInt(h.substring(0, 2), 16),
            Integer.parseInt(h.substring(2, 4), 16),
            Integer.parseInt(h.substring(4, 6), 16)
        };
    }

    private static String toHex(double r, double g, double b) {
        return String.format("#%02X%02X%02X",
            (int) Math.round(clamp01(r) * 255),
            (int) Math.round(clamp01(g) * 255),
            (int) Math.round(clamp01(b) * 255));
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
