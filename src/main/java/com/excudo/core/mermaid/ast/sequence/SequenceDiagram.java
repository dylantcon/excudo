package com.excudo.core.mermaid.ast.sequence;

import java.util.ArrayList;
import java.util.List;

/**
 * Top-level AST for a parsed sequence diagram.
 * Contains ordered participants and timeline elements.
 */
public class SequenceDiagram {

    /**
     * A participant declaration with optional alias and actor flag.
     */
    public record Participant(String id, String label, boolean isActor) {}

    private final List<Participant> participants = new ArrayList<>();
    private final List<SequenceElement> elements = new ArrayList<>();

    public List<Participant> participants() { return participants; }
    public List<SequenceElement> elements() { return elements; }

    public void addParticipant(Participant p) {
        // Deduplicate by id
        for (Participant existing : participants) {
            if (existing.id().equals(p.id())) return;
        }
        participants.add(p);
    }

    public void addElement(SequenceElement element) {
        elements.add(element);
    }

    /**
     * Ensure a participant exists (auto-register from message references).
     */
    public void ensureParticipant(String id) {
        addParticipant(new Participant(id, id, false));
    }
}
