package com.excudo.core.mermaid.ast.sequence;

/**
 * A message between two participants in a sequence diagram.
 */
public record SequenceMessage(
    String fromId,
    String toId,
    String label,
    MessageArrowType arrowType,
    boolean activateTarget,
    boolean deactivateSource
) implements SequenceElement {}
