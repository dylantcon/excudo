package com.excudo.core.mermaid.ast.sequence;

/**
 * An explicit activation or deactivation of a participant's lifeline.
 */
public record ActivationChange(
    String participantId,
    boolean activate
) implements SequenceElement {}
