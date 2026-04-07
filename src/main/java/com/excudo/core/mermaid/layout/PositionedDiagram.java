package com.excudo.core.mermaid.layout;

import com.excudo.core.mermaid.ast.Direction;

import java.util.List;

/**
 * A fully positioned diagram with nodes, edges, and computed bounds.
 */
public record PositionedDiagram(
    List<PositionedNode> nodes,
    List<PositionedEdge> edges,
    Direction direction,
    long totalWidth,
    long totalHeight
) {}
