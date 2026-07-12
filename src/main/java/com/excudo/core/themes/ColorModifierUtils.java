package com.excudo.core.themes;

import com.excudo.core.color.ColorTransforms;

import java.util.List;

/**
 * OOXML DrawingML color modifier math — thin adapter over
 * {@link ColorTransforms}, the single canonical implementation shared
 * with the direct-fill rendering path. Kept for API compatibility with
 * the theme fmtScheme pipeline ({@code FmtSchemeParser} /
 * {@code FmtSchemeResolver}); no color math lives here anymore.
 *
 * Values are in 100,000ths (50000 = 50%). Per PowerPoint's own PDF
 * export (see ColorTransforms class doc): shade/tint operate in
 * linearized sRGB; lumMod/lumOff/satMod operate in HSL; a modifier
 * chain runs on an unquantized color state.
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
        return ColorTransforms.parseHex(hex);
    }

    public static String toHex(int r, int g, int b) {
        return String.format("#%02X%02X%02X", clamp255(r), clamp255(g), clamp255(b));
    }

    // ========== HSL CONVERSION ==========

    /**
     * RGB [0-255] to HSL [h: 0-360, s: 0-1, l: 0-1].
     */
    public static double[] rgbToHsl(int r, int g, int b) {
        return ColorTransforms.rgbToHsl(r / 255.0, g / 255.0, b / 255.0);
    }

    /**
     * HSL [h: 0-360, s: 0-1, l: 0-1] to RGB [0-255].
     */
    public static int[] hslToRgb(double h, double s, double l) {
        double[] rgb = ColorTransforms.hslToRgb(h, s, l);
        return new int[]{
            clamp255((int) Math.round(rgb[0] * 255)),
            clamp255((int) Math.round(rgb[1] * 255)),
            clamp255((int) Math.round(rgb[2] * 255))
        };
    }

    // ========== INDIVIDUAL MODIFIERS ==========

    /** Tint: blend toward white in linearized sRGB (PowerPoint semantics). */
    public static String applyTint(String hexColor, int tintVal) {
        return applySingle(hexColor, "tint", tintVal);
    }

    /** Shade: scale toward black in linearized sRGB (PowerPoint semantics). */
    public static String applyShade(String hexColor, int shadeVal) {
        return applySingle(hexColor, "shade", shadeVal);
    }

    /** Saturation modulation: multiply saturation in HSL space. */
    public static String applySatMod(String hexColor, int satModVal) {
        return applySingle(hexColor, "satMod", satModVal);
    }

    /** Luminance modulation: multiply luminance in HSL space. */
    public static String applyLumMod(String hexColor, int lumModVal) {
        return applySingle(hexColor, "lumMod", lumModVal);
    }

    /** Luminance offset: shift luminance in HSL space. */
    public static String applyLumOff(String hexColor, int lumOffVal) {
        return applySingle(hexColor, "lumOff", lumOffVal);
    }

    private static String applySingle(String hexColor, String name, int val) {
        return ColorTransforms.apply(hexColor,
            List.of(new ColorTransforms.Modifier(name, val))).hex();
    }

    // ========== COMPOSITE ==========

    /**
     * Apply a chain of modifiers in document order.
     * Alpha modifiers affect opacity, not the RGB hex.
     */
    public static ColorWithAlpha applyModifiers(String baseHex, List<ColorModifier> modifiers) {
        List<ColorTransforms.Modifier> mods = modifiers.stream()
            .map(m -> new ColorTransforms.Modifier(m.name(), m.value()))
            .toList();
        ColorTransforms.ResolvedColor result = ColorTransforms.apply(baseHex, mods);
        return new ColorWithAlpha(result.hex(), result.alpha());
    }

    // ========== CLAMPING ==========

    private static int clamp255(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
