package com.excudo.core.mermaid.ast;

/**
 * Edge arrow types derived from mermaid edge syntax.
 */
public enum EdgeArrowType {
    /** {@code -->} solid line with arrow */
    ARROW,
    /** {@code ---} solid line, no arrow */
    OPEN,
    /** {@code -.->} dotted line with arrow */
    DOTTED_ARROW,
    /** {@code -.-} dotted line, no arrow */
    DOTTED,
    /** {@code ==>} thick line with arrow */
    THICK_ARROW,
    /** {@code ===} thick line, no arrow */
    THICK,
    /** {@code --x} solid line with cross end */
    CROSS,
    /** {@code --o} solid line with circle end */
    CIRCLE_END;
}
