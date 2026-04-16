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

    enum GradientType { LINEAR, PATH }

    record GradientStop(double position, String hex, double alpha) {}
}
