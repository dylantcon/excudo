package com.excudo.core.mermaid.layout;

import com.excudo.core.mermaid.ast.DiagramEdge;

/**
 * A diagram edge with positioned source and target references.
 */
public record PositionedEdge(
    DiagramEdge edge
) {}
