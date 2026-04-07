package com.excudo.core.llm;

import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.ShapeStyle;
import com.excudo.core.model.SlideShape;
import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.results.ExecutionResult;
import com.excudo.test.utils.StubPPTXOrchestrator;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

public class MermaidDiagramToolTest {

    static class RecordingOrchestrator extends StubPPTXOrchestrator {
        int nextSpid = 100;

        record AddShapeCall(int slideNumber, SlideShape.ShapeType shapeType,
                            ShapeGeometry geometry, String text, String name, ShapeStyle style) {}
        record AddConnectorCall(int slideNumber, String connectorType, ShapeGeometry geometry,
                                String headEnd, String tailEnd, String lineColor,
                                Integer startSpid, Integer startIdx, Integer endSpid, Integer endIdx) {}

        final List<AddShapeCall> addShapeCalls = new ArrayList<>();
        final List<AddConnectorCall> addConnectorCalls = new ArrayList<>();

        @Override
        public ExecutionResult<Integer> addShape(int slideNumber, SlideShape.ShapeType shapeType,
                                                  ShapeGeometry geometry, String text, String shapeName,
                                                  ShapeStyle style) {
            addShapeCalls.add(new AddShapeCall(slideNumber, shapeType, geometry, text, shapeName, style));
            return ExecutionResult.success("AddShape", nextSpid++);
        }

        @Override
        public ExecutionResult<Integer> addConnector(int slideNumber, String connectorType,
                                                      ShapeGeometry geometry,
                                                      String headEnd, String tailEnd, String lineColor,
                                                      String lineStyle,
                                                      Integer startSpid, Integer startIdx,
                                                      Integer endSpid, Integer endIdx,
                                                      String customPath) {
            addConnectorCalls.add(new AddConnectorCall(slideNumber, connectorType, geometry,
                headEnd, tailEnd, lineColor, startSpid, startIdx, endSpid, endIdx));
            return ExecutionResult.success("AddConnector", nextSpid++);
        }
    }

    @Test
    public void testSimpleDiagram() {
        RecordingOrchestrator orch = new RecordingOrchestrator();
        MermaidDiagramTool tool = new MermaidDiagramTool(orch);

        String input = "{\"slideNumber\":1,\"mermaid\":\"graph TD\\nA[Start]-->B[End]\"}";
        String result = tool.createMermaidDiagram(input);

        assertTrue("Should report shapes created", result.contains("2 shapes"));
        assertTrue("Should report connectors created", result.contains("1 connectors"));
        assertEquals(2, orch.addShapeCalls.size());
        assertEquals(1, orch.addConnectorCalls.size());
        assertTrue("Should report default position", result.contains("using default content area position"));
    }

    @Test
    public void testShapeTypesUsed() {
        RecordingOrchestrator orch = new RecordingOrchestrator();
        MermaidDiagramTool tool = new MermaidDiagramTool(orch);

        String input = "{\"slideNumber\":1,\"mermaid\":\"graph TD\\nA[Rect]-->B{Decision}-->C((Circle))\"}";
        String result = tool.createMermaidDiagram(input);

        assertEquals(3, orch.addShapeCalls.size());

        // Find each shape call by name
        RecordingOrchestrator.AddShapeCall rectCall = orch.addShapeCalls.stream()
            .filter(c -> c.name().contains("A")).findFirst().orElseThrow();
        RecordingOrchestrator.AddShapeCall diamondCall = orch.addShapeCalls.stream()
            .filter(c -> c.name().contains("B")).findFirst().orElseThrow();
        RecordingOrchestrator.AddShapeCall circleCall = orch.addShapeCalls.stream()
            .filter(c -> c.name().contains("C")).findFirst().orElseThrow();

        assertTrue("Rect shape type", rectCall.shapeType().getOoxmlPreset().equals("rect"));
        assertTrue("Diamond shape type", diamondCall.shapeType().getOoxmlPreset().equals("flowChartDecision"));
        assertTrue("Circle shape type", circleCall.shapeType().getOoxmlPreset().equals("ellipse"));
    }

    @Test
    public void testConnectorSPIDBinding() {
        RecordingOrchestrator orch = new RecordingOrchestrator();
        MermaidDiagramTool tool = new MermaidDiagramTool(orch);

        String input = "{\"slideNumber\":1,\"mermaid\":\"graph TD\\nA-->B-->C\"}";
        tool.createMermaidDiagram(input);

        assertEquals(3, orch.addShapeCalls.size());
        assertEquals(2, orch.addConnectorCalls.size());

        // SPIDs should start at 100 (nextSpid)
        // A=100, B=101, C=102
        RecordingOrchestrator.AddConnectorCall conn1 = orch.addConnectorCalls.get(0);
        assertEquals(Integer.valueOf(100), conn1.startSpid());
        assertEquals(Integer.valueOf(101), conn1.endSpid());

        RecordingOrchestrator.AddConnectorCall conn2 = orch.addConnectorCalls.get(1);
        assertEquals(Integer.valueOf(101), conn2.startSpid());
        assertEquals(Integer.valueOf(102), conn2.endSpid());
    }

    @Test
    public void testConnectionIndicesTB() {
        RecordingOrchestrator orch = new RecordingOrchestrator();
        MermaidDiagramTool tool = new MermaidDiagramTool(orch);

        String input = "{\"slideNumber\":1,\"mermaid\":\"graph TD\\nA-->B\"}";
        tool.createMermaidDiagram(input);

        RecordingOrchestrator.AddConnectorCall conn = orch.addConnectorCalls.get(0);
        assertEquals("Source exit: bottom", Integer.valueOf(2), conn.startIdx());
        assertEquals("Target entry: top", Integer.valueOf(0), conn.endIdx());
    }

    @Test
    public void testCustomBoundingBox() {
        RecordingOrchestrator orch = new RecordingOrchestrator();
        MermaidDiagramTool tool = new MermaidDiagramTool(orch);

        String input = "{\"slideNumber\":2,\"mermaid\":\"graph TD\\nA-->B\",\"x\":100000,\"y\":200000,\"width\":5000000,\"height\":3000000}";
        tool.createMermaidDiagram(input);

        assertEquals(2, orch.addShapeCalls.size());
        // Shapes should be positioned within the specified bounding box
        for (RecordingOrchestrator.AddShapeCall call : orch.addShapeCalls) {
            assertTrue("x >= 100000", call.geometry().getX() >= 100000);
            assertTrue("y >= 200000", call.geometry().getY() >= 200000);
        }
    }

    @Test
    public void testMissingMermaidField() {
        RecordingOrchestrator orch = new RecordingOrchestrator();
        MermaidDiagramTool tool = new MermaidDiagramTool(orch);

        String input = "{\"slideNumber\":1}";
        String result = tool.createMermaidDiagram(input);

        assertTrue("Should report error", result.startsWith("Error"));
        assertEquals(0, orch.addShapeCalls.size());
    }

    @Test
    public void testInvalidMermaidSyntax() {
        RecordingOrchestrator orch = new RecordingOrchestrator();
        MermaidDiagramTool tool = new MermaidDiagramTool(orch);

        String input = "{\"slideNumber\":1,\"mermaid\":\"not valid mermaid\"}";
        String result = tool.createMermaidDiagram(input);

        assertTrue("Should report parsing error", result.contains("Error"));
        assertEquals(0, orch.addShapeCalls.size());
    }

    @Test
    public void testSlideNumberPassedThrough() {
        RecordingOrchestrator orch = new RecordingOrchestrator();
        MermaidDiagramTool tool = new MermaidDiagramTool(orch);

        String input = "{\"slideNumber\":3,\"mermaid\":\"graph TD\\nA-->B\"}";
        tool.createMermaidDiagram(input);

        for (RecordingOrchestrator.AddShapeCall call : orch.addShapeCalls) {
            assertEquals(3, call.slideNumber());
        }
        for (RecordingOrchestrator.AddConnectorCall call : orch.addConnectorCalls) {
            assertEquals(3, call.slideNumber());
        }
    }

    @Test
    public void testArrowHeadTypes() {
        RecordingOrchestrator orch = new RecordingOrchestrator();
        MermaidDiagramTool tool = new MermaidDiagramTool(orch);

        String input = "{\"slideNumber\":1,\"mermaid\":\"graph TD\\nA-->B\"}";
        tool.createMermaidDiagram(input);

        RecordingOrchestrator.AddConnectorCall conn = orch.addConnectorCalls.get(0);
        assertEquals("none", conn.headEnd());
        assertEquals("triangle", conn.tailEnd());
    }

    @Test
    public void testEdgeLabelProducesTextShape() {
        RecordingOrchestrator orch = new RecordingOrchestrator();
        MermaidDiagramTool tool = new MermaidDiagramTool(orch);

        String input = "{\"slideNumber\":1,\"mermaid\":\"graph TD\\nA-->|Yes|B\"}";
        String result = tool.createMermaidDiagram(input);

        // 2 node shapes + 1 label shape = 3 shapes total
        assertEquals("Should create 3 shapes (2 nodes + 1 label)", 3, orch.addShapeCalls.size());
        assertEquals("Should create 1 connector", 1, orch.addConnectorCalls.size());
        assertTrue("Should report edge label", result.contains("1 edge label"));

        // The label shape should have the text "Yes"
        RecordingOrchestrator.AddShapeCall labelCall = orch.addShapeCalls.get(2);
        assertTrue("Label shape name should contain 'label'", labelCall.name().contains("label"));
        assertEquals("Label text should be 'Yes'", "Yes", labelCall.text());
    }

    @Test
    public void testSubgraphProducesBoundingBox() {
        RecordingOrchestrator orch = new RecordingOrchestrator();
        MermaidDiagramTool tool = new MermaidDiagramTool(orch);

        String input = "{\"slideNumber\":1,\"mermaid\":\"graph TD\\nsubgraph cluster1\\nA-->B\\nend\\nC-->A\"}";
        String result = tool.createMermaidDiagram(input);

        // 1 subgraph shape + 3 node shapes + 0 label shapes = 4 shapes
        assertTrue("Should report subgraph(s)", result.contains("1 subgraph"));

        // Find the subgraph shape
        boolean foundSubgraph = orch.addShapeCalls.stream()
            .anyMatch(c -> c.name().contains("subgraph_"));
        assertTrue("Should create subgraph bounding box shape", foundSubgraph);
    }

    @Test
    public void testHyphenatedNodeId() {
        RecordingOrchestrator orch = new RecordingOrchestrator();
        MermaidDiagramTool tool = new MermaidDiagramTool(orch);

        String input = "{\"slideNumber\":1,\"mermaid\":\"graph TD\\nprocess-start[Start]-->process-end[End]\"}";
        String result = tool.createMermaidDiagram(input);

        assertFalse("Should not error", result.startsWith("Error"));
        assertEquals("Should create 2 shapes", 2, orch.addShapeCalls.size());
    }

    @Test
    public void testExplicitPositionNoDefaultWarning() {
        RecordingOrchestrator orch = new RecordingOrchestrator();
        MermaidDiagramTool tool = new MermaidDiagramTool(orch);

        String input = "{\"slideNumber\":1,\"mermaid\":\"graph TD\\nA-->B\","
            + "\"x\":100000,\"y\":200000,\"width\":5000000,\"height\":3000000}";
        String result = tool.createMermaidDiagram(input);

        assertFalse("Should not mention defaults when all positions specified",
            result.contains("using default"));
    }
}
