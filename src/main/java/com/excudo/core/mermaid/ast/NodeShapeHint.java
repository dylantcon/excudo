package com.excudo.core.mermaid.ast;

/**
 * Shape hints derived from mermaid node syntax.
 * Each hint maps to an OOXML preset geometry via the emitter layer.
 */
public enum NodeShapeHint {
    /** {@code [text]} */
    RECT,
    /** {@code (text)} */
    ROUND_RECT,
    /** {@code {text}} */
    DIAMOND,
    /** {@code ((text))} */
    CIRCLE,
    /** {@code ([text])} */
    STADIUM,
    /** {@code [[text]]} */
    SUBROUTINE,
    /** {@code [(text)]} */
    CYLINDER,
    /** {@code >text]} */
    ASYMMETRIC,
    /** {@code [/text/]} */
    PARALLELOGRAM,
    /** {@code [\text\]} */
    PARALLELOGRAM_ALT,
    /** {@code [/text\]} */
    TRAPEZOID,
    /** {@code [\text/]} */
    TRAPEZOID_ALT,
    /** {@code {{text}}} */
    HEXAGON;
}
