package com.excudo.core.rendering.surface;

import com.excudo.core.color.ColorTransforms;

import java.util.ArrayList;
import java.util.List;

/**
 * Gradient stop utilities shared by every gradient producer (direct
 * {@code a:gradFill} parsing, theme fmtScheme fills, slide backgrounds).
 *
 * <p>PowerPoint does not interpolate gradient stops linearly in sRGB.
 * Its PDF export (the parity ground truth) bakes each ramp into a
 * sampled-function LUT; decoding those LUTs shows adjacent stops
 * interpolate in <b>gamma-2.2-linearised RGB</b> with a <b>raised-cosine
 * ease</b> on the position axis. AWT and JavaFX gradients both
 * interpolate linearly in sRGB, so {@link #expand} inserts sub-stops
 * that follow PowerPoint's curve closely enough (8 subdivisions keeps
 * the deviation from the reference LUT within ~1/255 per channel).
 */
public final class GradientStops {

    private GradientStops() {}

    /** Sub-segments inserted between each adjacent stop pair. */
    private static final int SUBDIVISIONS = 8;

    /**
     * Expand sorted gradient stops with PowerPoint's interpolation curve
     * (see class doc). First/last stops are preserved verbatim; pairs
     * with identical colors are not subdivided.
     */
    public static List<SurfacePaint.LinearGradient.Stop> expand(
            List<SurfacePaint.LinearGradient.Stop> stops) {
        if (stops.size() < 2) return stops;
        List<SurfacePaint.LinearGradient.Stop> out = new ArrayList<>();
        for (int i = 0; i < stops.size() - 1; i++) {
            SurfacePaint.LinearGradient.Stop s0 = stops.get(i);
            SurfacePaint.LinearGradient.Stop s1 = stops.get(i + 1);
            out.add(s0);
            if (s0.color().equals(s1.color()) || s1.position() - s0.position() <= 0) {
                continue; // nothing to interpolate
            }
            for (int j = 1; j < SUBDIVISIONS; j++) {
                double t = (double) j / SUBDIVISIONS;
                double pos = s0.position() + (s1.position() - s0.position()) * t;
                int argb = ColorTransforms.gammaLerpArgb(
                    s0.color().argb(), s1.color().argb(), ColorTransforms.sineEase(t));
                out.add(new SurfacePaint.LinearGradient.Stop(pos, new SurfacePaint.Solid(argb)));
            }
        }
        out.add(stops.get(stops.size() - 1));
        return out;
    }
}
