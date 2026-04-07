package com.excudo.core.mermaid.ast;

/**
 * An edge connecting two nodes in a diagram.
 */
public class DiagramEdge {
    private final String fromId;
    private final String toId;
    private String label;
    private EdgeArrowType edgeType;
    private boolean bidirectional;

    public DiagramEdge(String fromId, String toId, EdgeArrowType edgeType) {
        this.fromId = fromId;
        this.toId = toId;
        this.edgeType = edgeType;
    }

    public DiagramEdge(String fromId, String toId, EdgeArrowType edgeType, String label) {
        this(fromId, toId, edgeType);
        this.label = label;
    }

    public String fromId() { return fromId; }
    public String toId() { return toId; }
    public String label() { return label; }
    public EdgeArrowType edgeType() { return edgeType; }
    public boolean bidirectional() { return bidirectional; }

    public void setLabel(String label) { this.label = label; }
    public void setEdgeType(EdgeArrowType edgeType) { this.edgeType = edgeType; }
    public void setBidirectional(boolean bidirectional) { this.bidirectional = bidirectional; }

    @Override
    public String toString() {
        return "DiagramEdge{" + fromId + " -[" + edgeType + "]-> " + toId +
               (label != null ? " '" + label + "'" : "") + "}";
    }
}
