package com.excudo.core.mermaid.emitter;

import com.excudo.core.mermaid.ast.NodeShapeHint;

/**
 * Maps diagram node shape hints to OOXML preset geometry strings.
 */
public class ShapeHintMapper {

    private ShapeHintMapper() {}

    public static String toOoxmlPreset(NodeShapeHint hint) {
        return switch (hint) {
            case RECT -> "rect";
            case ROUND_RECT, STADIUM -> "roundRect";
            case DIAMOND -> "flowChartDecision";
            case CIRCLE -> "ellipse";
            case SUBROUTINE -> "flowChartPredefinedProcess";
            case CYLINDER -> "can";
            case ASYMMETRIC -> "homePlate";
            case PARALLELOGRAM -> "parallelogram";
            case PARALLELOGRAM_ALT -> "parallelogram";
            case TRAPEZOID -> "trapezoid";
            case TRAPEZOID_ALT -> "trapezoid";
            case HEXAGON -> "hexagon";
        };
    }
}
