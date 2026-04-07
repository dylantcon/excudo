package com.excudo.core.mermaid.layout.common;

import com.excudo.core.mermaid.ast.DiagramGraph;
import com.excudo.core.mermaid.ast.DiagramNode;
import com.excudo.core.mermaid.ast.Direction;
import com.excudo.core.mermaid.layout.LayoutConfig;
import com.excudo.core.mermaid.layout.PositionedNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Assigns X/Y coordinates to nodes based on rank layers and configuration.
 * Supports all four directions (TB, BT, LR, RL).
 */
public class CoordinateAssigner {

    private CoordinateAssigner() {}

    /**
     * Assign coordinates to all nodes.
     *
     * @param layers Nodes grouped by rank (from RankAssigner)
     * @param graph  The diagram graph (for node lookups)
     * @param config Layout configuration with bounding box and gaps
     * @param direction Flow direction
     * @return List of positioned nodes
     */
    public static List<PositionedNode> assign(List<List<String>> layers, DiagramGraph graph,
                                               LayoutConfig config, Direction direction) {
        List<PositionedNode> result = new ArrayList<>();

        boolean vertical = (direction == Direction.TB || direction == Direction.BT);
        int layerCount = layers.size();
        int maxNodesInLayer = layers.stream().mapToInt(List::size).max().orElse(1);

        // Compute node sizes that fit within bounding box
        long nodeW, nodeH, hGap, vGap;

        if (vertical) {
            // For TB/BT: layers stack vertically, nodes spread horizontally
            nodeW = config.nodeWidth();
            nodeH = config.nodeHeight();
            hGap = config.horizontalGap();
            vGap = config.verticalGap();

            // Single-column diagrams: widen nodes to use available space
            if (maxNodesInLayer == 1) {
                nodeW = Math.min(nodeW * 2, config.boundingWidth() - 2 * config.margin());
            }

            // Auto-shrink if needed to fit bounding box
            long totalH = layerCount * nodeH + (layerCount - 1) * vGap + 2 * config.margin();
            if (totalH > config.boundingHeight() && layerCount > 0) {
                long available = config.boundingHeight() - 2 * config.margin();
                vGap = Math.max(vGap / 2, 91440); // min 0.1"
                nodeH = Math.min(nodeH, (available - (layerCount - 1) * vGap) / layerCount);
            }

            long totalW = maxNodesInLayer * nodeW + (maxNodesInLayer - 1) * hGap + 2 * config.margin();
            if (totalW > config.boundingWidth() && maxNodesInLayer > 0) {
                long available = config.boundingWidth() - 2 * config.margin();
                hGap = Math.max(hGap / 2, 91440);
                nodeW = Math.min(nodeW, (available - (maxNodesInLayer - 1) * hGap) / maxNodesInLayer);
            }
        } else {
            // For LR/RL: layers stack horizontally, nodes spread vertically
            nodeW = config.nodeWidth();
            nodeH = config.nodeHeight();
            hGap = config.horizontalGap();
            vGap = config.verticalGap();

            long totalW = layerCount * nodeW + (layerCount - 1) * hGap + 2 * config.margin();
            if (totalW > config.boundingWidth() && layerCount > 0) {
                long available = config.boundingWidth() - 2 * config.margin();
                hGap = Math.max(hGap / 2, 91440);
                nodeW = Math.min(nodeW, (available - (layerCount - 1) * hGap) / layerCount);
            }

            long totalH = maxNodesInLayer * nodeH + (maxNodesInLayer - 1) * vGap + 2 * config.margin();
            if (totalH > config.boundingHeight() && maxNodesInLayer > 0) {
                long available = config.boundingHeight() - 2 * config.margin();
                vGap = Math.max(vGap / 2, 91440);
                nodeH = Math.min(nodeH, (available - (maxNodesInLayer - 1) * vGap) / maxNodesInLayer);
            }
        }

        for (int layerIdx = 0; layerIdx < layers.size(); layerIdx++) {
            List<String> layer = layers.get(layerIdx);
            int nodesInLayer = layer.size();

            for (int nodeIdx = 0; nodeIdx < layer.size(); nodeIdx++) {
                String nodeId = layer.get(nodeIdx);
                DiagramNode node = graph.getNode(nodeId);
                if (node == null) continue;

                long x, y;

                if (vertical) {
                    // Center nodes horizontally within their layer
                    long layerWidth = nodesInLayer * nodeW + (nodesInLayer - 1) * hGap;
                    long layerStartX = config.boundingX() + config.margin() +
                        (config.boundingWidth() - 2 * config.margin() - layerWidth) / 2;
                    x = layerStartX + nodeIdx * (nodeW + hGap);

                    int effectiveLayer = (direction == Direction.BT) ? (layerCount - 1 - layerIdx) : layerIdx;
                    y = config.boundingY() + config.margin() + effectiveLayer * (nodeH + vGap);
                } else {
                    // Center nodes vertically within their layer
                    long layerHeight = nodesInLayer * nodeH + (nodesInLayer - 1) * vGap;
                    long layerStartY = config.boundingY() + config.margin() +
                        (config.boundingHeight() - 2 * config.margin() - layerHeight) / 2;
                    y = layerStartY + nodeIdx * (nodeH + vGap);

                    int effectiveLayer = (direction == Direction.RL) ? (layerCount - 1 - layerIdx) : layerIdx;
                    x = config.boundingX() + config.margin() + effectiveLayer * (nodeW + hGap);
                }

                result.add(new PositionedNode(node, x, y, nodeW, nodeH));
            }
        }

        return result;
    }
}
