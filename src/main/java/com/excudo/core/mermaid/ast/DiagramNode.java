package com.excudo.core.mermaid.ast;

import java.util.ArrayList;
import java.util.List;

/**
 * A node in a diagram graph.
 */
public class DiagramNode {
    private final String id;
    private String label;
    private NodeShapeHint shapeHint;
    private final List<String> styleClasses;
    private String inlineStyle;

    public DiagramNode(String id) {
        this.id = id;
        this.label = id;
        this.shapeHint = NodeShapeHint.RECT;
        this.styleClasses = new ArrayList<>();
    }

    public DiagramNode(String id, String label, NodeShapeHint shapeHint) {
        this.id = id;
        this.label = label;
        this.shapeHint = shapeHint;
        this.styleClasses = new ArrayList<>();
    }

    public String id() { return id; }
    public String label() { return label; }
    public NodeShapeHint shapeHint() { return shapeHint; }
    public List<String> styleClasses() { return styleClasses; }
    public String inlineStyle() { return inlineStyle; }

    public void setLabel(String label) { this.label = label; }
    public void setShapeHint(NodeShapeHint shapeHint) { this.shapeHint = shapeHint; }
    public void setInlineStyle(String inlineStyle) { this.inlineStyle = inlineStyle; }
    public void addStyleClass(String cls) { this.styleClasses.add(cls); }

    @Override
    public String toString() {
        return "DiagramNode{id='" + id + "', label='" + label + "', shape=" + shapeHint + "}";
    }
}
