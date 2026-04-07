package com.excudo.core.mermaid.ast.sequence;

import java.util.List;

/**
 * A note annotation in a sequence diagram.
 */
public record SequenceNote(
    NotePosition position,
    List<String> participantIds,
    String text
) implements SequenceElement {

    public enum NotePosition {
        LEFT, RIGHT, OVER
    }
}
