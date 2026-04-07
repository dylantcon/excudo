package com.excudo.core.mermaid.layout;

import com.excudo.core.mermaid.ast.DiagramGraph;

/**
 * Interface for diagram layout algorithms.
 */
public interface LayoutEngine {
    PositionedDiagram layout(DiagramGraph graph, LayoutConfig config);
}
