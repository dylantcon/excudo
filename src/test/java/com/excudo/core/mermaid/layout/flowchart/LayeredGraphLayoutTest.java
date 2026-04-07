package com.excudo.core.mermaid.layout.flowchart;

import com.excudo.core.mermaid.ast.*;
import com.excudo.core.mermaid.layout.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class LayeredGraphLayoutTest {

    @Test
    public void testTwoNodeLayout() {
        DiagramGraph graph = new DiagramGraph(Direction.TB);
        graph.addNode(new DiagramNode("A", "Start", NodeShapeHint.RECT));
        graph.addNode(new DiagramNode("B", "End", NodeShapeHint.RECT));
        graph.addEdge(new DiagramEdge("A", "B", EdgeArrowType.ARROW));

        LayoutConfig config = LayoutConfig.slideContentArea();
        PositionedDiagram result = new LayeredGraphLayout().layout(graph, config);

        assertEquals(2, result.nodes().size());
        assertEquals(1, result.edges().size());

        PositionedNode nodeA = findNode(result, "A");
        PositionedNode nodeB = findNode(result, "B");

        // A should be above B in TB direction
        assertTrue("A should be above B", nodeA.y() < nodeB.y());
    }

    @Test
    public void testNoOverlap() {
        DiagramGraph graph = new DiagramGraph(Direction.TB);
        graph.addNode(new DiagramNode("A", "A", NodeShapeHint.RECT));
        graph.addNode(new DiagramNode("B", "B", NodeShapeHint.RECT));
        graph.addNode(new DiagramNode("C", "C", NodeShapeHint.RECT));
        graph.addEdge(new DiagramEdge("A", "B", EdgeArrowType.ARROW));
        graph.addEdge(new DiagramEdge("A", "C", EdgeArrowType.ARROW));

        LayoutConfig config = LayoutConfig.slideContentArea();
        PositionedDiagram result = new LayeredGraphLayout().layout(graph, config);

        // B and C are on the same rank -- they should not overlap
        PositionedNode nodeB = findNode(result, "B");
        PositionedNode nodeC = findNode(result, "C");

        boolean overlaps = nodeB.x() < nodeC.x() + nodeC.width() &&
                           nodeB.x() + nodeB.width() > nodeC.x() &&
                           nodeB.y() < nodeC.y() + nodeC.height() &&
                           nodeB.y() + nodeB.height() > nodeC.y();
        assertFalse("Nodes B and C should not overlap", overlaps);
    }

    @Test
    public void testLRDirection() {
        DiagramGraph graph = new DiagramGraph(Direction.LR);
        graph.addNode(new DiagramNode("A", "Start", NodeShapeHint.RECT));
        graph.addNode(new DiagramNode("B", "End", NodeShapeHint.RECT));
        graph.addEdge(new DiagramEdge("A", "B", EdgeArrowType.ARROW));

        LayoutConfig config = LayoutConfig.slideContentArea();
        PositionedDiagram result = new LayeredGraphLayout().layout(graph, config);

        PositionedNode nodeA = findNode(result, "A");
        PositionedNode nodeB = findNode(result, "B");

        // A should be to the left of B in LR direction
        assertTrue("A should be left of B", nodeA.x() < nodeB.x());
    }

    @Test
    public void testPositionsWithinBoundingBox() {
        DiagramGraph graph = new DiagramGraph(Direction.TB);
        for (int i = 0; i < 6; i++) {
            String id = "N" + i;
            graph.addNode(new DiagramNode(id, "Node " + i, NodeShapeHint.RECT));
            if (i > 0) graph.addEdge(new DiagramEdge("N" + (i - 1), id, EdgeArrowType.ARROW));
        }

        LayoutConfig config = new LayoutConfig(100000, 200000, 8000000, 5000000);
        PositionedDiagram result = new LayeredGraphLayout().layout(graph, config);

        for (PositionedNode pn : result.nodes()) {
            assertTrue("Node " + pn.node().id() + " x should be >= bounding x",
                pn.x() >= config.boundingX());
            assertTrue("Node " + pn.node().id() + " y should be >= bounding y",
                pn.y() >= config.boundingY());
        }
    }

    @Test
    public void testLayerOrdering() {
        // A -> B -> C -> D: should produce 4 layers
        DiagramGraph graph = new DiagramGraph(Direction.TB);
        graph.addNode(new DiagramNode("A", "A", NodeShapeHint.RECT));
        graph.addNode(new DiagramNode("B", "B", NodeShapeHint.RECT));
        graph.addNode(new DiagramNode("C", "C", NodeShapeHint.RECT));
        graph.addNode(new DiagramNode("D", "D", NodeShapeHint.RECT));
        graph.addEdge(new DiagramEdge("A", "B", EdgeArrowType.ARROW));
        graph.addEdge(new DiagramEdge("B", "C", EdgeArrowType.ARROW));
        graph.addEdge(new DiagramEdge("C", "D", EdgeArrowType.ARROW));

        LayoutConfig config = LayoutConfig.slideContentArea();
        PositionedDiagram result = new LayeredGraphLayout().layout(graph, config);

        PositionedNode a = findNode(result, "A");
        PositionedNode b = findNode(result, "B");
        PositionedNode c = findNode(result, "C");
        PositionedNode d = findNode(result, "D");

        assertTrue("Layer ordering: A < B < C < D",
            a.y() < b.y() && b.y() < c.y() && c.y() < d.y());
    }

    @Test
    public void testDiamondShape() {
        // A -> B, A -> C, B -> D, C -> D
        DiagramGraph graph = new DiagramGraph(Direction.TB);
        graph.addNode(new DiagramNode("A", "A", NodeShapeHint.RECT));
        graph.addNode(new DiagramNode("B", "B", NodeShapeHint.RECT));
        graph.addNode(new DiagramNode("C", "C", NodeShapeHint.RECT));
        graph.addNode(new DiagramNode("D", "D", NodeShapeHint.RECT));
        graph.addEdge(new DiagramEdge("A", "B", EdgeArrowType.ARROW));
        graph.addEdge(new DiagramEdge("A", "C", EdgeArrowType.ARROW));
        graph.addEdge(new DiagramEdge("B", "D", EdgeArrowType.ARROW));
        graph.addEdge(new DiagramEdge("C", "D", EdgeArrowType.ARROW));

        LayoutConfig config = LayoutConfig.slideContentArea();
        PositionedDiagram result = new LayeredGraphLayout().layout(graph, config);

        assertEquals(4, result.nodes().size());
        assertEquals(4, result.edges().size());
    }

    private PositionedNode findNode(PositionedDiagram diagram, String id) {
        return diagram.nodes().stream()
            .filter(n -> n.node().id().equals(id))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Node not found: " + id));
    }
}
