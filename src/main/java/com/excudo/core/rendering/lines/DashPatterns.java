package com.excudo.core.rendering.lines;

import com.excudo.core.rendering.surface.StrokeCap;

/**
 * OOXML preset dash patterns (ST_PresetLineDashVal), calibrated against
 * PowerPoint's own PDF export: the parity-corpus lines-dash-cap-join
 * ground truth emits literal {@code d} operators whose arrays are exact
 * multiples of the line width at every width (1pt {@code [4 3]}, 3pt
 * {@code [12 9]}, 6pt {@code [24 18]} for "dash", and so on). The
 * PDF-measured multiples, in (on, off) pairs of line-width units:
 *
 * <pre>
 *   dash          4 3            lgDash        8 3
 *   dashDot       4 3 1 3        lgDashDot     8 3 1 3
 *   dot           1 3            lgDashDotDot  8 3 1 3 1 3
 *   sysDash       3 1            sysDashDot    3 1 1 1
 *   sysDot        1 1            sysDashDotDot 3 1 1 1 1 1
 * </pre>
 *
 * The truth PDFs pair these with butt caps and phase 0. With a non-flat
 * cap PowerPoint compensates for the w/2-per-end cap overhang: every ON
 * entry shrinks by 1w and every OFF entry grows by 1w, preserving the
 * pitch (integration-generalist truth: 1.5pt cap="rnd" "dash" strokes
 * {@code 1 J [4.5 6] 0 d} -- [3w, 4w]).
 */
public final class DashPatterns {

    private DashPatterns() {}

    /** Fraction of a width an ON entry keeps when cap growth zeroes it. */
    private static final double MIN_ON_FRACTION = 0.01;

    /**
     * Dash array in device units for {@code prstDash} at the given line
     * width with a flat cap, or null for solid / unrecognized styles
     * (theme lnStyleLst entries pass "solid" through here).
     */
    public static double[] pattern(String prstDash, double lineWidth) {
        return pattern(prstDash, lineWidth, StrokeCap.BUTT);
    }

    /**
     * Dash array adjusted for the line cap. Round/square caps shrink ON
     * entries by 1w (clamped to a sliver -- a zero-length segment with a
     * round cap should raster as a dot, but AWT drops it) and the OFF
     * entry absorbs the difference so the pitch is exact.
     */
    public static double[] pattern(String prstDash, double lineWidth, StrokeCap cap) {
        double[] units = unitPattern(prstDash);
        if (units == null) return null;
        double[] out = new double[units.length];
        for (int i = 0; i < units.length; i++) out[i] = units[i] * lineWidth;
        if (cap == StrokeCap.BUTT || cap == null) return out;
        for (int i = 0; i + 1 < out.length; i += 2) {
            double on = Math.max(out[i] - lineWidth, MIN_ON_FRACTION * lineWidth);
            out[i + 1] += out[i] - on;
            out[i] = on;
        }
        return out;
    }

    /** The width-unit multiples for a preset, or null for solid/unknown. */
    static double[] unitPattern(String prstDash) {
        if (prstDash == null) return null;
        return switch (prstDash) {
            case "dash"          -> new double[]{4, 3};
            case "dashDot"       -> new double[]{4, 3, 1, 3};
            case "dot"           -> new double[]{1, 3};
            case "lgDash"        -> new double[]{8, 3};
            case "lgDashDot"     -> new double[]{8, 3, 1, 3};
            case "lgDashDotDot"  -> new double[]{8, 3, 1, 3, 1, 3};
            case "sysDash"       -> new double[]{3, 1};
            case "sysDashDot"    -> new double[]{3, 1, 1, 1};
            case "sysDashDotDot" -> new double[]{3, 1, 1, 1, 1, 1};
            case "sysDot"        -> new double[]{1, 1};
            default              -> null; // "solid" or unrecognized
        };
    }
}
