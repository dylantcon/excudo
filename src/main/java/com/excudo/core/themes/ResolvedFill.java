package com.excudo.core.themes;

import java.util.List;

/**
 * Result of resolving a theme fill style with phClr substitution.
 * No JavaFX dependency — the view layer converts these to Paint objects.
 */
public sealed interface ResolvedFill {

    record SolidFill(String hex, double alpha) implements ResolvedFill {}

    record GradientFill(
        List<GradientStop> stops,
        double angleDegrees,
        GradientType type
    ) implements ResolvedFill {}

    record NoFill() implements ResolvedFill {}

    /** Image/raster fill (a:blipFill). Carries the OPC relationship id
     *  pointing to the image part; the relationship id is nullable when
     *  the blipFill references a theme-embedded picture with no r:embed
     *  attribute. {@code duotoneHexes} is empty for straight image fills
     *  and two-entry ({shadowHex, highlightHex}, both '#'-prefixed) when
     *  the blip applies a duotone recolor. Consumers that can't render
     *  an image (e.g. solid-hex extractors) should fall back to the
     *  phClr. */
    record BlipFill(String relId, List<String> duotoneHexes) implements ResolvedFill {
        public BlipFill {
            duotoneHexes = List.copyOf(duotoneHexes);
        }

        /** Back-compat constructor for sites that don't need duotone info. */
        public BlipFill(String relId) {
            this(relId, List.of());
        }
    }

    enum GradientType { LINEAR, PATH }

    record GradientStop(double position, String hex, double alpha) {}
}
