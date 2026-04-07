package com.excudo.core.mermaid.ast.sequence;

/**
 * Arrow types for sequence diagram messages.
 */
public enum MessageArrowType {
    SOLID_ARROW,    // ->>
    DASHED_ARROW,   // -->>
    SOLID_OPEN,     // ->
    DASHED_OPEN,    // -->
    CROSS,          // -x
    ASYNC           // -)
}
