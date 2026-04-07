package com.excudo.core.mermaid.ast.sequence;

import java.util.ArrayList;
import java.util.List;

/**
 * A block (alt/opt/loop/par/critical/break) in a sequence diagram.
 * Contains child elements and optional alternate sections (else/and/option).
 */
public final class SequenceBlock implements SequenceElement {

    public enum BlockType {
        ALT, OPT, LOOP, PAR, CRITICAL, BREAK
    }

    private final BlockType type;
    private final String label;
    private final List<SequenceElement> elements;
    private final List<SequenceBlock> alternates;

    public SequenceBlock(BlockType type, String label) {
        this.type = type;
        this.label = label;
        this.elements = new ArrayList<>();
        this.alternates = new ArrayList<>();
    }

    public BlockType type() { return type; }
    public String label() { return label; }
    public List<SequenceElement> elements() { return elements; }
    public List<SequenceBlock> alternates() { return alternates; }

    public void addElement(SequenceElement element) {
        elements.add(element);
    }

    public void addAlternate(SequenceBlock alternate) {
        alternates.add(alternate);
    }
}
