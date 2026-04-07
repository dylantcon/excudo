package com.excudo.core.mermaid.ast;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Universal intermediate representation for all diagram types.
 * Contains nodes, edges, direction, and optional subgraphs.
 */
public class DiagramGraph {
    private Direction direction;
    private final Map<String, DiagramNode> nodes;
    private final List<DiagramEdge> edges;
    private final List<Subgraph> subgraphs;

    public DiagramGraph(Direction direction) {
        this.direction = direction;
        this.nodes = new LinkedHashMap<>();
        this.edges = new ArrayList<>();
        this.subgraphs = new ArrayList<>();
    }

    public Direction direction() { return direction; }
    public Map<String, DiagramNode> nodeMap() { return nodes; }
    public List<DiagramNode> nodes() { return new ArrayList<>(nodes.values()); }
    public List<DiagramEdge> edges() { return edges; }
    public List<Subgraph> subgraphs() { return subgraphs; }

    public void setDirection(Direction direction) { this.direction = direction; }

    public DiagramNode getOrCreateNode(String id) {
        return nodes.computeIfAbsent(id, DiagramNode::new);
    }

    public void addNode(DiagramNode node) {
        nodes.put(node.id(), node);
    }

    public void addEdge(DiagramEdge edge) {
        edges.add(edge);
        // Ensure referenced nodes exist
        getOrCreateNode(edge.fromId());
        getOrCreateNode(edge.toId());
    }

    public void addSubgraph(Subgraph subgraph) {
        subgraphs.add(subgraph);
    }

    public DiagramNode getNode(String id) {
        return nodes.get(id);
    }

    public int nodeCount() { return nodes.size(); }
    public int edgeCount() { return edges.size(); }
}
