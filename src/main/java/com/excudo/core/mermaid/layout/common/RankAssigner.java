package com.excudo.core.mermaid.layout.common;

import com.excudo.core.mermaid.ast.DiagramEdge;
import com.excudo.core.mermaid.ast.DiagramGraph;
import com.excudo.core.mermaid.ast.DiagramNode;

import java.util.*;

/**
 * Assigns ranks (layers) to nodes using topological sort with longest-path layering.
 * Nodes with no incoming edges get rank 0, others get max(predecessor rank) + 1.
 */
public class RankAssigner {

    private RankAssigner() {}

    /**
     * Assign ranks to all nodes in the graph.
     * Returns a map of nodeId -> rank (0-based).
     */
    public static Map<String, Integer> assignRanks(DiagramGraph graph) {
        Map<String, Integer> rank = new LinkedHashMap<>();
        Map<String, Set<String>> predecessors = new HashMap<>();

        // Build predecessor map
        for (DiagramNode node : graph.nodes()) {
            predecessors.put(node.id(), new HashSet<>());
        }
        for (DiagramEdge edge : graph.edges()) {
            predecessors.computeIfAbsent(edge.toId(), k -> new HashSet<>()).add(edge.fromId());
        }

        // Topological sort via Kahn's algorithm with longest-path ranking
        Queue<String> ready = new ArrayDeque<>();
        for (DiagramNode node : graph.nodes()) {
            if (predecessors.get(node.id()).isEmpty()) {
                ready.add(node.id());
                rank.put(node.id(), 0);
            }
        }

        Map<String, Set<String>> successors = new HashMap<>();
        for (DiagramEdge edge : graph.edges()) {
            successors.computeIfAbsent(edge.fromId(), k -> new HashSet<>()).add(edge.toId());
        }

        Set<String> processed = new HashSet<>();
        while (!ready.isEmpty()) {
            String nodeId = ready.poll();
            if (processed.contains(nodeId)) continue;
            processed.add(nodeId);

            int nodeRank = rank.getOrDefault(nodeId, 0);

            Set<String> succs = successors.getOrDefault(nodeId, Set.of());
            for (String succId : succs) {
                int newRank = nodeRank + 1;
                rank.merge(succId, newRank, Math::max);

                // Check if all predecessors are processed
                Set<String> preds = predecessors.get(succId);
                if (preds != null && processed.containsAll(preds)) {
                    ready.add(succId);
                }
            }
        }

        // Handle disconnected components: offset their ranks to avoid overlap
        if (processed.size() < graph.nodes().size()) {
            int maxRankSoFar = rank.values().stream().mapToInt(i -> i).max().orElse(0);

            // Find unprocessed nodes and run BFS on each disconnected component
            Set<String> remaining = new LinkedHashSet<>();
            for (DiagramNode node : graph.nodes()) {
                if (!processed.contains(node.id())) remaining.add(node.id());
            }

            while (!remaining.isEmpty()) {
                String startId = remaining.iterator().next();
                int componentOffset = maxRankSoFar + 1;

                // BFS within this disconnected component
                Queue<String> componentQueue = new ArrayDeque<>();
                componentQueue.add(startId);
                rank.put(startId, componentOffset);

                while (!componentQueue.isEmpty()) {
                    String nodeId = componentQueue.poll();
                    if (!remaining.remove(nodeId)) continue;
                    processed.add(nodeId);

                    int nodeRank = rank.getOrDefault(nodeId, componentOffset);
                    Set<String> succs = successors.getOrDefault(nodeId, Set.of());
                    for (String succId : succs) {
                        if (remaining.contains(succId)) {
                            int newRank = nodeRank + 1;
                            rank.merge(succId, newRank, Math::max);
                            componentQueue.add(succId);
                        }
                    }
                }

                maxRankSoFar = rank.values().stream().mapToInt(i -> i).max().orElse(maxRankSoFar);
            }
        }

        return rank;
    }

    /**
     * Group nodes by rank. Returns list of lists, index = rank.
     */
    public static List<List<String>> groupByRank(Map<String, Integer> ranks) {
        int maxRank = ranks.values().stream().mapToInt(i -> i).max().orElse(0);
        List<List<String>> layers = new ArrayList<>();
        for (int i = 0; i <= maxRank; i++) {
            layers.add(new ArrayList<>());
        }
        for (Map.Entry<String, Integer> entry : ranks.entrySet()) {
            layers.get(entry.getValue()).add(entry.getKey());
        }
        return layers;
    }
}
