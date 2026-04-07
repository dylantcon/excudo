package com.excudo.core.mermaid.ast;

import java.util.ArrayList;
import java.util.List;

/**
 * A subgraph container grouping child nodes.
 */
public class Subgraph {
    private final String id;
    private String label;
    private final List<String> childNodeIds;

    public Subgraph(String id) {
        this.id = id;
        this.label = id;
        this.childNodeIds = new ArrayList<>();
    }

    public Subgraph(String id, String label) {
        this.id = id;
        this.label = label;
        this.childNodeIds = new ArrayList<>();
    }

    public String id() { return id; }
    public String label() { return label; }
    public List<String> childNodeIds() { return childNodeIds; }

    public void setLabel(String label) { this.label = label; }
    public void addChildNodeId(String nodeId) { this.childNodeIds.add(nodeId); }
}
