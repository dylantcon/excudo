package com.excudo.core.mermaid.ast.sequence;

/**
 * Sealed interface for all sequence diagram elements in timeline order.
 */
public sealed interface SequenceElement
    permits SequenceMessage, SequenceNote, SequenceBlock, ActivationChange {}
