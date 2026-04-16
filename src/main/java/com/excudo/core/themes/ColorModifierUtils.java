package com.excudo.core.themes;

import java.util.List;

/**
 * OOXML DrawingML color modifier math.
 * Applies tint, shade, satMod, lumMod, lumOff, alpha transforms per ECMA-376.
 * Values are in 100,000ths (50000 = 50%).
 *
 * Tint operates in HSL space (matches PowerPoint behavior).
 * Shade operates in RGB space (scale toward black).
 * SatMod/LumMod/LumOff operate in HSL space.
 */
public final class ColorModifierUtils {

    private ColorModifierUtils() {}

    // ========== RECORDS ==========

    public record ColorModifier(String name, int value) {}

    public record ColorWithAlpha(String hex, double alpha) {
        public ColorWithAlpha(String hex) { this(hex, 1.0); }
    }

    // ========== HEX PARSING ==========

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

    public static String toHex(int r, int g, int b) {
        return String.format("#%02X%02X%02X", clamp255(r), clamp255(g), clamp255(b));
    }

    // ========== HSL CONVERSION ==========

    /**
     * RGB [0-255] to HSL [h: 0-360, s: 0-1, l: 0-1].
     */
    public static double[] rgbToHsl(int r, int g, int b) {
        double rd = r / 255.0;
        double gd = g / 255.0;
        double bd = b / 255.0;

        double max = Math.max(rd, Math.max(gd, bd));
        double min = Math.min(rd, Math.min(gd, bd));
        double l = (max + min) / 2.0;

        if (max == min) {
            return new double[]{0, 0, l}; // achromatic
        }

        double d = max - min;
        double s = l > 0.5 ? d / (2.0 - max - min) : d / (max + min);

        double h;
        if (max == rd) {
            h = (gd - bd) / d + (gd < bd ? 6 : 0);
        } else if (max == gd) {
            h = (bd - rd) / d + 2;
        } else {
            h = (rd - gd) / d + 4;
        }
        h *= 60;

        return new double[]{h, s, l};
    }

    /**
     * HSL [h: 0-360, s: 0-1, l: 0-1] to RGB [0-255].
     */
    public static int[] hslToRgb(double h, double s, double l) {
        if (s == 0) {
            int v = (int) Math.round(l * 255);
            return new int[]{v, v, v};
        }

        double q = l < 0.5 ? l * (1 + s) : l + s - l * s;
        double p = 2 * l - q;
        double hNorm = h / 360.0;

        int r = (int) Math.round(hueToRgb(p, q, hNorm + 1.0 / 3.0) * 255);
        int g = (int) Math.round(hueToRgb(p, q, hNorm) * 255);
        int b = (int) Math.round(hueToRgb(p, q, hNorm - 1.0 / 3.0) * 255);

        return new int[]{clamp255(r), clamp255(g), clamp255(b)};
    }

    private static double hueToRgb(double p, double q, double t) {
        if (t < 0) t += 1;
        if (t > 1) t -= 1;
        if (t < 1.0 / 6.0) return p + (q - p) * 6 * t;
        if (t < 1.0 / 2.0) return q;
        if (t < 2.0 / 3.0) return p + (q - p) * (2.0 / 3.0 - t) * 6;
        return p;
    }

    // ========== INDIVIDUAL MODIFIERS ==========

    /**
     * Tint: blend toward white in HSL space.
     * L' = L * (T/100000) + (1 - T/100000)
     */
    public static String applyTint(String hexColor, int tintVal) {
        double t = tintVal / 100000.0;
        int[] rgb = parseHex(hexColor);
        double[] hsl = rgbToHsl(rgb[0], rgb[1], rgb[2]);
        hsl[2] = hsl[2] * t + (1.0 - t);
        hsl[2] = clamp01(hsl[2]);
        int[] out = hslToRgb(hsl[0], hsl[1], hsl[2]);
        return toHex(out[0], out[1], out[2]);
    }

    /**
     * Shade: scale toward black in RGB space.
     * R' = R * (S/100000)
     */
    public static String applyShade(String hexColor, int shadeVal) {
        double s = shadeVal / 100000.0;
        int[] rgb = parseHex(hexColor);
        return toHex(
            (int) Math.round(rgb[0] * s),
            (int) Math.round(rgb[1] * s),
            (int) Math.round(rgb[2] * s)
        );
    }

    /**
     * Saturation modulation: multiply saturation in HSL space.
     * S' = S * (M/100000)
     */
    public static String applySatMod(String hexColor, int satModVal) {
        double m = satModVal / 100000.0;
        int[] rgb = parseHex(hexColor);
        double[] hsl = rgbToHsl(rgb[0], rgb[1], rgb[2]);
        hsl[1] = clamp01(hsl[1] * m);
        int[] out = hslToRgb(hsl[0], hsl[1], hsl[2]);
        return toHex(out[0], out[1], out[2]);
    }

    /**
     * Luminance modulation: multiply luminance in HSL space.
     * L' = L * (M/100000)
     */
    public static String applyLumMod(String hexColor, int lumModVal) {
        double m = lumModVal / 100000.0;
        int[] rgb = parseHex(hexColor);
        double[] hsl = rgbToHsl(rgb[0], rgb[1], rgb[2]);
        hsl[2] = clamp01(hsl[2] * m);
        int[] out = hslToRgb(hsl[0], hsl[1], hsl[2]);
        return toHex(out[0], out[1], out[2]);
    }

    /**
     * Luminance offset: shift luminance in HSL space.
     * L' = L + (O/100000)
     */
    public static String applyLumOff(String hexColor, int lumOffVal) {
        double o = lumOffVal / 100000.0;
        int[] rgb = parseHex(hexColor);
        double[] hsl = rgbToHsl(rgb[0], rgb[1], rgb[2]);
        hsl[2] = clamp01(hsl[2] + o);
        int[] out = hslToRgb(hsl[0], hsl[1], hsl[2]);
        return toHex(out[0], out[1], out[2]);
    }

    // ========== COMPOSITE ==========

    /**
     * Apply a chain of modifiers in document order.
     * Alpha modifiers affect opacity, not the RGB hex.
     */
    public static ColorWithAlpha applyModifiers(String baseHex, List<ColorModifier> modifiers) {
        String hex = baseHex.startsWith("#") ? baseHex : "#" + baseHex;
        double alpha = 1.0;

        for (ColorModifier mod : modifiers) {
            switch (mod.name()) {
                case "tint" -> hex = applyTint(hex, mod.value());
                case "shade" -> hex = applyShade(hex, mod.value());
                case "satMod" -> hex = applySatMod(hex, mod.value());
                case "lumMod" -> hex = applyLumMod(hex, mod.value());
                case "lumOff" -> hex = applyLumOff(hex, mod.value());
                case "alpha" -> alpha = mod.value() / 100000.0;
                case "alphaMod" -> alpha = alpha * mod.value() / 100000.0;
                case "alphaOff" -> alpha = clamp01(alpha + mod.value() / 100000.0);
                // hueMod, hueOff, satOff, comp, inv, gray — not commonly used in fmtScheme
                default -> {} // skip unknown modifiers
            }
        }

        return new ColorWithAlpha(hex, alpha);
    }

    // ========== CLAMPING ==========

    private static int clamp255(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
