package com.excudo.core.mermaid.layout.flowchart;

import com.excudo.core.mermaid.ast.DiagramEdge;
import com.excudo.core.mermaid.ast.DiagramGraph;
import com.excudo.core.mermaid.layout.*;
import com.excudo.core.mermaid.layout.common.CoordinateAssigner;
import com.excudo.core.mermaid.layout.common.CrossingMinimizer;
import com.excudo.core.mermaid.layout.common.RankAssigner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Sugiyama-lite layered graph layout algorithm.
 *
 * Pipeline:
 *   1. Rank assignment (longest-path layering via topological sort)
 *   2. Crossing minimization (barycenter heuristic)
 *   3. Coordinate assignment (centered within bounding box)
 */
public class LayeredGraphLayout implements LayoutEngine {

    @Override
    public PositionedDiagram layout(DiagramGraph graph, LayoutConfig config) {
        // Step 1: Assign ranks
        Map<String, Integer> ranks = RankAssigner.assignRanks(graph);
        List<List<String>> layers = RankAssigner.groupByRank(ranks);

        // Step 2: Minimize crossings
        CrossingMinimizer.minimize(layers, graph);

        // Step 3: Assign coordinates
        List<PositionedNode> nodes = CoordinateAssigner.assign(
            layers, graph, config, graph.direction());

        // Step 4: Create positioned edges
        List<PositionedEdge> edges = new ArrayList<>();
        for (DiagramEdge edge : graph.edges()) {
            edges.add(new PositionedEdge(edge));
        }

        // Compute total bounds
        long maxX = 0, maxY = 0;
        for (PositionedNode pn : nodes) {
            maxX = Math.max(maxX, pn.x() + pn.width());
            maxY = Math.max(maxY, pn.y() + pn.height());
        }

        return new PositionedDiagram(nodes, edges, graph.direction(), maxX, maxY);
    }
}
