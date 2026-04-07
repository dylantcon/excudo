package com.excudo.core.mermaid.layout;

import com.excudo.core.mermaid.ast.DiagramNode;

/**
 * A diagram node with computed position and size in EMUs.
 */
public record PositionedNode(
    DiagramNode node,
    long x,
    long y,
    long width,
    long height
) {}
