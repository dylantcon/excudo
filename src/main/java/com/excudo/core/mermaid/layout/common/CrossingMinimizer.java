package com.excudo.core.mermaid.layout.common;

import com.excudo.core.mermaid.ast.DiagramEdge;
import com.excudo.core.mermaid.ast.DiagramGraph;

import java.util.*;

/**
 * Minimizes edge crossings using the barycenter heuristic.
 * Iterates through layers top-down, reordering each layer to minimize
 * crossings with the previous layer.
 */
public class CrossingMinimizer {

    private CrossingMinimizer() {}

    /**
     * Reorder nodes within each layer to minimize edge crossings.
     * Modifies the layers list in place.
     */
    public static void minimize(List<List<String>> layers, DiagramGraph graph) {
        if (layers.size() <= 1) return;

        // Build adjacency: for each node, positions of its neighbors in the adjacent layer
        Map<String, Set<String>> successors = new HashMap<>();
        Map<String, Set<String>> predecessors = new HashMap<>();
        for (DiagramEdge edge : graph.edges()) {
            successors.computeIfAbsent(edge.fromId(), k -> new HashSet<>()).add(edge.toId());
            predecessors.computeIfAbsent(edge.toId(), k -> new HashSet<>()).add(edge.fromId());
        }

        // Two passes: top-down then bottom-up
        for (int pass = 0; pass < 2; pass++) {
            if (pass == 0) {
                // Top-down: order each layer based on predecessor positions
                for (int i = 1; i < layers.size(); i++) {
                    List<String> prevLayer = layers.get(i - 1);
                    reorderByBarycenter(layers.get(i), prevLayer, predecessors);
                }
            } else {
                // Bottom-up: order each layer based on successor positions
                for (int i = layers.size() - 2; i >= 0; i--) {
                    List<String> nextLayer = layers.get(i + 1);
                    reorderByBarycenter(layers.get(i), nextLayer, successors);
                }
            }
        }
    }

    private static void reorderByBarycenter(List<String> layer, List<String> referenceLayer,
                                             Map<String, Set<String>> connections) {
        // Build position index for reference layer
        Map<String, Integer> refPos = new HashMap<>();
        for (int i = 0; i < referenceLayer.size(); i++) {
            refPos.put(referenceLayer.get(i), i);
        }

        // Calculate barycenter for each node in current layer
        Map<String, Double> barycenters = new HashMap<>();
        for (String nodeId : layer) {
            Set<String> neighbors = connections.getOrDefault(nodeId, Set.of());
            double sum = 0;
            int count = 0;
            for (String neighbor : neighbors) {
                Integer pos = refPos.get(neighbor);
                if (pos != null) {
                    sum += pos;
                    count++;
                }
            }
            barycenters.put(nodeId, count > 0 ? sum / count : layer.indexOf(nodeId));
        }

        // Sort by barycenter, preserving original order for ties
        layer.sort(Comparator.comparingDouble(id -> barycenters.getOrDefault(id, 0.0)));
    }
}
