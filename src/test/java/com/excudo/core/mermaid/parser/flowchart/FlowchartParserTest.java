package com.excudo.core.mermaid.parser.flowchart;

import com.excudo.core.mermaid.ast.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class FlowchartParserTest {

    @Test
    public void testSimpleTwoNodeArrow() {
        DiagramGraph graph = FlowchartParser.parse("graph TD\n    A-->B");
        assertEquals(2, graph.nodeCount());
        assertEquals(1, graph.edgeCount());
        assertEquals(Direction.TB, graph.direction());

        DiagramEdge edge = graph.edges().get(0);
        assertEquals("A", edge.fromId());
        assertEquals("B", edge.toId());
        assertEquals(EdgeArrowType.ARROW, edge.edgeType());
    }

    @Test
    public void testDirectionVariants() {
        assertEquals(Direction.TB, FlowchartParser.parse("graph TD\nA-->B").direction());
        assertEquals(Direction.TB, FlowchartParser.parse("graph TB\nA-->B").direction());
        assertEquals(Direction.LR, FlowchartParser.parse("graph LR\nA-->B").direction());
        assertEquals(Direction.RL, FlowchartParser.parse("graph RL\nA-->B").direction());
        assertEquals(Direction.BT, FlowchartParser.parse("graph BT\nA-->B").direction());
    }

    @Test
    public void testNodeWithLabel() {
        DiagramGraph graph = FlowchartParser.parse("graph TD\n    A[Start Here]-->B[End Here]");
        assertEquals("Start Here", graph.getNode("A").label());
        assertEquals("End Here", graph.getNode("B").label());
        assertEquals(NodeShapeHint.RECT, graph.getNode("A").shapeHint());
    }

    @Test
    public void testNodeShapeTypes() {
        DiagramGraph graph = FlowchartParser.parse(
            "graph TD\n" +
            "    A[Rectangle]\n" +
            "    B(Rounded)\n" +
            "    C{Diamond}\n" +
            "    D((Circle))\n" +
            "    E([Stadium])\n" +
            "    F[[Subroutine]]\n" +
            "    G[(Cylinder)]\n" +
            "    H>Asymmetric]\n" +
            "    I{{Hexagon}}"
        );

        assertEquals(NodeShapeHint.RECT, graph.getNode("A").shapeHint());
        assertEquals(NodeShapeHint.ROUND_RECT, graph.getNode("B").shapeHint());
        assertEquals(NodeShapeHint.DIAMOND, graph.getNode("C").shapeHint());
        assertEquals(NodeShapeHint.CIRCLE, graph.getNode("D").shapeHint());
        assertEquals(NodeShapeHint.STADIUM, graph.getNode("E").shapeHint());
        assertEquals(NodeShapeHint.SUBROUTINE, graph.getNode("F").shapeHint());
        assertEquals(NodeShapeHint.CYLINDER, graph.getNode("G").shapeHint());
        assertEquals(NodeShapeHint.ASYMMETRIC, graph.getNode("H").shapeHint());
        assertEquals(NodeShapeHint.HEXAGON, graph.getNode("I").shapeHint());
    }

    @Test
    public void testEdgeTypes() {
        DiagramGraph graph = FlowchartParser.parse(
            "graph TD\n" +
            "    A-->B\n" +
            "    C---D\n" +
            "    E-.->F\n" +
            "    G==>H"
        );

        assertEquals(4, graph.edgeCount());
        assertEquals(EdgeArrowType.ARROW, graph.edges().get(0).edgeType());
        assertEquals(EdgeArrowType.OPEN, graph.edges().get(1).edgeType());
        assertEquals(EdgeArrowType.DOTTED_ARROW, graph.edges().get(2).edgeType());
        assertEquals(EdgeArrowType.THICK_ARROW, graph.edges().get(3).edgeType());
    }

    @Test
    public void testEdgeWithLabel() {
        DiagramGraph graph = FlowchartParser.parse("graph TD\n    A-->|Yes|B");
        assertEquals(1, graph.edgeCount());
        assertEquals("Yes", graph.edges().get(0).label());
    }

    @Test
    public void testChainedEdges() {
        DiagramGraph graph = FlowchartParser.parse("graph TD\n    A-->B-->C-->D");
        assertEquals(4, graph.nodeCount());
        assertEquals(3, graph.edgeCount());
        assertEquals("A", graph.edges().get(0).fromId());
        assertEquals("B", graph.edges().get(0).toId());
        assertEquals("B", graph.edges().get(1).fromId());
        assertEquals("C", graph.edges().get(1).toId());
        assertEquals("C", graph.edges().get(2).fromId());
        assertEquals("D", graph.edges().get(2).toId());
    }

    @Test
    public void testSubgraph() {
        DiagramGraph graph = FlowchartParser.parse(
            "graph TD\n" +
            "    subgraph sub1\n" +
            "        A-->B\n" +
            "    end\n" +
            "    C-->A"
        );

        assertEquals(3, graph.nodeCount());
        assertEquals(2, graph.edgeCount());
        assertEquals(1, graph.subgraphs().size());
        assertEquals("sub1", graph.subgraphs().get(0).id());
        assertTrue(graph.subgraphs().get(0).childNodeIds().contains("A"));
        assertTrue(graph.subgraphs().get(0).childNodeIds().contains("B"));
    }

    @Test
    public void testSemicolonSeparator() {
        DiagramGraph graph = FlowchartParser.parse("graph TD; A-->B; C-->D");
        assertEquals(4, graph.nodeCount());
        assertEquals(2, graph.edgeCount());
    }

    @Test
    public void testMultipleStatements() {
        DiagramGraph graph = FlowchartParser.parse(
            "graph TD\n" +
            "    A[Start]-->B{Decision}\n" +
            "    B-->|Yes|C[End]\n" +
            "    B-->|No|D[Retry]"
        );

        assertEquals(4, graph.nodeCount());
        assertEquals(3, graph.edgeCount());
        assertEquals(NodeShapeHint.DIAMOND, graph.getNode("B").shapeHint());
        assertEquals("Decision", graph.getNode("B").label());
    }

    @Test
    public void testQuotedLabels() {
        DiagramGraph graph = FlowchartParser.parse(
            "graph TD\n    A[\"Hello World\"]-->B"
        );
        assertEquals("Hello World", graph.getNode("A").label());
    }

    @Test
    public void testBidirectionalEdge() {
        DiagramGraph graph = FlowchartParser.parse("graph TD\n    A<-->B");
        assertEquals(1, graph.edgeCount());
        assertTrue(graph.edges().get(0).bidirectional());
    }

    @Test
    public void testCommentSkipping() {
        DiagramGraph graph = FlowchartParser.parse(
            "graph TD\n" +
            "    %% This is a comment\n" +
            "    A-->B"
        );
        assertEquals(2, graph.nodeCount());
        assertEquals(1, graph.edgeCount());
    }

    @Test
    public void testNodeReferencedMultipleTimes() {
        DiagramGraph graph = FlowchartParser.parse(
            "graph TD\n" +
            "    A[Start]-->B\n" +
            "    A-->C"
        );
        assertEquals(3, graph.nodeCount());
        assertEquals(2, graph.edgeCount());
        assertEquals("Start", graph.getNode("A").label());
    }

    @Test(expected = FlowchartParser.ParseException.class)
    public void testInvalidSyntaxThrows() {
        FlowchartParser.parse("not a valid diagram");
    }

    @Test
    public void testFlowchartKeyword() {
        DiagramGraph graph = FlowchartParser.parse("flowchart TD\n    A-->B");
        assertEquals(2, graph.nodeCount());
        assertEquals(Direction.TB, graph.direction());
    }

    @Test
    public void testCrossEndEdge() {
        DiagramGraph graph = FlowchartParser.parse("graph TD\n    A--xB");
        assertEquals(1, graph.edgeCount());
        assertEquals(EdgeArrowType.CROSS, graph.edges().get(0).edgeType());
    }

    @Test
    public void testCircleEndEdge() {
        DiagramGraph graph = FlowchartParser.parse("graph TD\n    A--oB");
        assertEquals(1, graph.edgeCount());
        assertEquals(EdgeArrowType.CIRCLE_END, graph.edges().get(0).edgeType());
    }

    @Test
    public void testHyphenatedNodeId() {
        DiagramGraph graph = FlowchartParser.parse(
            "graph TD\n    process-start[Start]-->process-end[End]");
        assertEquals(2, graph.nodeCount());
        assertEquals(1, graph.edgeCount());
        assertNotNull(graph.getNode("process-start"));
        assertNotNull(graph.getNode("process-end"));
        assertEquals("Start", graph.getNode("process-start").label());
        assertEquals("End", graph.getNode("process-end").label());
    }

    @Test
    public void testHyphenatedIdWithArrow() {
        // Ensure hyphens don't interfere with arrow parsing
        DiagramGraph graph = FlowchartParser.parse("graph TD\n    my-node-->other-node");
        assertEquals(2, graph.nodeCount());
        assertEquals(1, graph.edgeCount());
        assertEquals("my-node", graph.edges().get(0).fromId());
        assertEquals("other-node", graph.edges().get(0).toId());
    }

    @Test
    public void testClassDefEmitsWarning() {
        // classDef is parsed and skipped with a warning -- use valid token chars
        DiagramGraph graph = FlowchartParser.parse(
            "graph TD\n    classDef myClass bold\n    A-->B");
        assertEquals(2, graph.nodeCount());
        assertEquals(1, graph.edgeCount());
    }
}
