package com.excudo.core.model;

/**
 * Typed descriptor for a slide's resolved background fill. Produced by
 * {@code SlideMasterOrchestrationManager.getSlideBackground()} after
 * walking slide -> master p:bg elements and following any theme
 * fmtScheme / bgFillStyleLst references.
 *
 * <p>Two shapes today:
 * <ul>
 *   <li>{@link Solid}: a single hex color (covers solidFill, flat
 *       gradient fallback, and the default fallback to phClr).</li>
 *   <li>{@link BlipImage}: an image fill with optional two-color duotone
 *       recolor (ECMA-376 §20.1.8.23). The renderer is expected to
 *       decode {@link BlipImage#imageBytes}, apply duotone when
 *       {@code duotoneShadowHex}/{@code duotoneHighlightHex} are
 *       non-null, and stretch over the slide bounds.</li>
 * </ul>
 *
 * <p>{@link Gradient} carries the theme-resolved gradient stops so the
 * renderer can paint the real ramp instead of collapsing to the first
 * stop's color.
 */
public sealed interface SlideBackground {

    /** Solid color fill. {@code hex} is '#'-prefixed. */
    record Solid(String hex) implements SlideBackground {}

    /**
     * Gradient fill resolved through the theme's fmtScheme (bgRef).
     * Stops carry '#'-prefixed hex + alpha; {@code angleDegrees} is the
     * OOXML a:lin angle (clockwise from east); {@code radial} true for
     * a:path (PATH) gradients.
     */
    record Gradient(
            java.util.List<GradientStop> stops,
            double angleDegrees,
            boolean radial) implements SlideBackground {

        /** One gradient stop: position 0..1, '#'-prefixed hex, alpha 0..1. */
        public record GradientStop(double position, String hex, double alpha) {}
    }

    /**
     * Image fill, optionally with duotone recolor.
     *
     * @param imagePartName OPC part path (e.g. {@code ppt/media/image1.jpeg})
     * @param mimeType      content type from the embedded part
     * @param imageBytes    raw encoded image bytes (JPEG, PNG, etc.)
     * @param duotoneShadowHex   '#'-prefixed shadow color for duotone, or null
     * @param duotoneHighlightHex '#'-prefixed highlight color for duotone, or null
     */
    record BlipImage(
            String imagePartName,
            String mimeType,
            byte[] imageBytes,
            String duotoneShadowHex,
            String duotoneHighlightHex) implements SlideBackground {

        public boolean hasDuotone() {
            return duotoneShadowHex != null && duotoneHighlightHex != null;
        }
    }
}
