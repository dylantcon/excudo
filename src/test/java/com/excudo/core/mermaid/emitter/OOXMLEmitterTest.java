package com.excudo.core.mermaid.emitter;

import com.excudo.core.mermaid.ast.*;
import com.excudo.core.mermaid.layout.*;
import com.excudo.core.mermaid.layout.flowchart.LayeredGraphLayout;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.List;

public class OOXMLEmitterTest {

    @Test
    public void testEmitShapesAndConnectors() {
        DiagramGraph graph = new DiagramGraph(Direction.TB);
        graph.addNode(new DiagramNode("A", "Start", NodeShapeHint.RECT));
        graph.addNode(new DiagramNode("B", "Decision", NodeShapeHint.DIAMOND));
        graph.addEdge(new DiagramEdge("A", "B", EdgeArrowType.ARROW));

        LayoutConfig config = LayoutConfig.slideContentArea();
        PositionedDiagram positioned = new LayeredGraphLayout().layout(graph, config);
        DiagramOutput output = OOXMLEmitter.emit(positioned);

        assertEquals(2, output.shapes().size());
        assertEquals(1, output.connectors().size());

        // Verify shape presets
        ShapeSpec shapeA = output.shapes().stream()
            .filter(s -> s.nodeId().equals("A")).findFirst().orElseThrow();
        assertEquals("rect", shapeA.ooxmlPreset());
        assertEquals("Start", shapeA.label());

        ShapeSpec shapeB = output.shapes().stream()
            .filter(s -> s.nodeId().equals("B")).findFirst().orElseThrow();
        assertEquals("flowChartDecision", shapeB.ooxmlPreset());
    }

    @Test
    public void testConnectorIndicesTB() {
        DiagramGraph graph = new DiagramGraph(Direction.TB);
        graph.addNode(new DiagramNode("A", "A", NodeShapeHint.RECT));
        graph.addNode(new DiagramNode("B", "B", NodeShapeHint.RECT));
        graph.addEdge(new DiagramEdge("A", "B", EdgeArrowType.ARROW));

        LayoutConfig config = LayoutConfig.slideContentArea();
        PositionedDiagram positioned = new LayeredGraphLayout().layout(graph, config);
        DiagramOutput output = OOXMLEmitter.emit(positioned);

        ConnectorSpec conn = output.connectors().get(0);
        assertEquals(2, conn.startIdx());  // bottom
        assertEquals(0, conn.endIdx());    // top
        assertEquals("none", conn.headEnd());
        assertEquals("triangle", conn.tailEnd());
    }

    @Test
    public void testConnectorIndicesLR() {
        DiagramGraph graph = new DiagramGraph(Direction.LR);
        graph.addNode(new DiagramNode("A", "A", NodeShapeHint.RECT));
        graph.addNode(new DiagramNode("B", "B", NodeShapeHint.RECT));
        graph.addEdge(new DiagramEdge("A", "B", EdgeArrowType.ARROW));

        LayoutConfig config = LayoutConfig.slideContentArea();
        PositionedDiagram positioned = new LayeredGraphLayout().layout(graph, config);
        DiagramOutput output = OOXMLEmitter.emit(positioned);

        ConnectorSpec conn = output.connectors().get(0);
        assertEquals(3, conn.startIdx());  // right
        assertEquals(1, conn.endIdx());    // left
    }

    @Test
    public void testShapePositionsNonNegative() {
        DiagramGraph graph = new DiagramGraph(Direction.TB);
        for (int i = 0; i < 5; i++) {
            String id = "N" + i;
            graph.addNode(new DiagramNode(id, "Node " + i, NodeShapeHint.RECT));
            if (i > 0) graph.addEdge(new DiagramEdge("N" + (i - 1), id, EdgeArrowType.ARROW));
        }

        LayoutConfig config = LayoutConfig.slideContentArea();
        PositionedDiagram positioned = new LayeredGraphLayout().layout(graph, config);
        DiagramOutput output = OOXMLEmitter.emit(positioned);

        for (ShapeSpec shape : output.shapes()) {
            assertTrue("x >= 0 for " + shape.nodeId(), shape.x() >= 0);
            assertTrue("y >= 0 for " + shape.nodeId(), shape.y() >= 0);
            assertTrue("width > 0 for " + shape.nodeId(), shape.width() > 0);
            assertTrue("height > 0 for " + shape.nodeId(), shape.height() > 0);
        }
    }

    @Test
    public void testConnectorGeometryValid() {
        DiagramGraph graph = new DiagramGraph(Direction.TB);
        graph.addNode(new DiagramNode("A", "A", NodeShapeHint.RECT));
        graph.addNode(new DiagramNode("B", "B", NodeShapeHint.RECT));
        graph.addEdge(new DiagramEdge("A", "B", EdgeArrowType.ARROW));

        LayoutConfig config = LayoutConfig.slideContentArea();
        PositionedDiagram positioned = new LayeredGraphLayout().layout(graph, config);
        DiagramOutput output = OOXMLEmitter.emit(positioned);

        for (ConnectorSpec conn : output.connectors()) {
            // Vertical connectors in TB flow have cx=0 (straight down) -- that's correct.
            // At least one dimension must be non-zero.
            assertTrue("connector must have non-zero extent",
                conn.width() > 0 || conn.height() > 0);
            assertTrue("connector height >= 0", conn.height() >= 0);
            assertTrue("connector width >= 0", conn.width() >= 0);
        }
    }

    @Test
    public void testEdgeLabelEmitted() {
        DiagramGraph graph = new DiagramGraph(Direction.TB);
        graph.addNode(new DiagramNode("A", "A", NodeShapeHint.RECT));
        graph.addNode(new DiagramNode("B", "B", NodeShapeHint.RECT));
        graph.addEdge(new DiagramEdge("A", "B", EdgeArrowType.ARROW, "Yes"));

        LayoutConfig config = LayoutConfig.slideContentArea();
        PositionedDiagram positioned = new LayeredGraphLayout().layout(graph, config);
        DiagramOutput output = OOXMLEmitter.emit(positioned);

        assertEquals("Should have 1 label shape", 1, output.labelShapes().size());
        ShapeSpec label = output.labelShapes().get(0);
        assertEquals("Yes", label.label());
        assertTrue("Label nodeId should start with 'label_'", label.nodeId().startsWith("label_"));
    }

    @Test
    public void testSubgraphBoundingBox() {
        DiagramGraph graph = new DiagramGraph(Direction.TB);
        graph.addNode(new DiagramNode("A", "A", NodeShapeHint.RECT));
        graph.addNode(new DiagramNode("B", "B", NodeShapeHint.RECT));
        graph.addEdge(new DiagramEdge("A", "B", EdgeArrowType.ARROW));

        Subgraph sub = new Subgraph("cluster1", "My Cluster");
        sub.addChildNodeId("A");
        sub.addChildNodeId("B");
        graph.addSubgraph(sub);

        LayoutConfig config = LayoutConfig.slideContentArea();
        PositionedDiagram positioned = new LayeredGraphLayout().layout(graph, config);
        DiagramOutput output = OOXMLEmitter.emit(positioned, graph.subgraphs());

        // Should have 3 shapes: 1 subgraph box + 2 node shapes
        assertEquals(3, output.shapes().size());
        ShapeSpec subShape = output.shapes().get(0);
        assertEquals("subgraph_cluster1", subShape.nodeId());
        assertEquals("roundRect", subShape.ooxmlPreset());
        assertEquals("My Cluster", subShape.label());
    }

    @Test
    public void testNoLabelWhenEdgeHasNoLabel() {
        DiagramGraph graph = new DiagramGraph(Direction.TB);
        graph.addNode(new DiagramNode("A", "A", NodeShapeHint.RECT));
        graph.addNode(new DiagramNode("B", "B", NodeShapeHint.RECT));
        graph.addEdge(new DiagramEdge("A", "B", EdgeArrowType.ARROW));

        LayoutConfig config = LayoutConfig.slideContentArea();
        PositionedDiagram positioned = new LayeredGraphLayout().layout(graph, config);
        DiagramOutput output = OOXMLEmitter.emit(positioned);

        assertTrue("Should have no label shapes", output.labelShapes().isEmpty());
    }

    @Test
    public void testDottedEdgeStyle() {
        DiagramGraph graph = new DiagramGraph(Direction.TB);
        graph.addNode(new DiagramNode("A", "A", NodeShapeHint.RECT));
        graph.addNode(new DiagramNode("B", "B", NodeShapeHint.RECT));
        graph.addEdge(new DiagramEdge("A", "B", EdgeArrowType.DOTTED_ARROW));

        LayoutConfig config = LayoutConfig.slideContentArea();
        PositionedDiagram positioned = new LayeredGraphLayout().layout(graph, config);
        DiagramOutput output = OOXMLEmitter.emit(positioned);

        ConnectorSpec conn = output.connectors().get(0);
        assertEquals("dash", conn.lineStyle());
        assertEquals("triangle", conn.tailEnd());
    }
}
