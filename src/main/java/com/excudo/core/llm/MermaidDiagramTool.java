package com.excudo.core.llm;

import com.excudo.core.model.*;
import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.results.ExecutionResult;
import com.excudo.core.utils.Logger;
import com.excudo.core.utils.ComponentLogger;
import com.excudo.core.mermaid.ast.DiagramGraph;
import com.excudo.core.mermaid.ast.sequence.SequenceDiagram;
import com.excudo.core.mermaid.emitter.ConnectorSpec;
import com.excudo.core.mermaid.emitter.DiagramOutput;
import com.excudo.core.mermaid.emitter.OOXMLEmitter;
import com.excudo.core.mermaid.emitter.SequenceEmitter;
import com.excudo.core.mermaid.emitter.ShapeSpec;
import com.excudo.core.mermaid.layout.LayoutConfig;
import com.excudo.core.mermaid.layout.PositionedDiagram;
import com.excudo.core.mermaid.layout.flowchart.LayeredGraphLayout;
import com.excudo.core.mermaid.layout.sequence.SequenceDiagramLayout;
import com.excudo.core.mermaid.layout.sequence.SequenceLayoutResult;
import com.excudo.core.mermaid.parser.DiagramDetector;
import com.excudo.core.mermaid.parser.DiagramType;
import com.excudo.core.mermaid.parser.flowchart.FlowchartParser;
import com.excudo.core.mermaid.parser.sequence.SequenceParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.excudo.core.utils.JsonHelper;
import com.google.gson.JsonObject;

/**
 * Bridge between the mermaid-ooxml library and the Excudo orchestrator.
 * Parses mermaid syntax, lays out the diagram, and creates native OOXML shapes
 * with bound connectors via the PPTXOrchestrator.
 *
 * This is the ONLY file in the mermaid pipeline that imports Excudo types.
 */
public class MermaidDiagramTool {

    private static final ComponentLogger logger = Logger.llm();
    private final PPTXOrchestrator orchestrator;

    public MermaidDiagramTool(PPTXOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    /**
     * Create a diagram from mermaid syntax on a slide.
     *
     * @param toolInput JSON with: mermaid (string), slideNumber (int),
     *                  optional x/y/width/height (long, EMUs)
     * @return Summary string for LLM consumption
     */
    public String createMermaidDiagram(String toolInput) {
        try {
            int slideNumber = extractInt(toolInput, "slideNumber");
            String mermaidText = extractString(toolInput, "mermaid");
            if (mermaidText == null || mermaidText.isBlank()) {
                return "Error: 'mermaid' field is required and must contain valid mermaid syntax";
            }

            long x = extractLong(toolInput, "x", -1);
            long y = extractLong(toolInput, "y", -1);
            long w = extractLong(toolInput, "width", -1);
            long h = extractLong(toolInput, "height", -1);

            boolean usingDefaults = (x == -1 || y == -1 || w == -1 || h == -1);
            if (x == -1) x = 838200;
            if (y == -1) y = 1825625;
            if (w == -1) w = 7772400;
            if (h == -1) h = 4525963;

            if (usingDefaults) {
                logger.info("Using default content area position (x={}, y={}, w={}, h={})", x, y, w, h);
            }

            // Step 1-3: Detect type, parse, layout, emit
            LayoutConfig config = new LayoutConfig(x, y, w, h);
            DiagramType diagramType = DiagramDetector.detect(mermaidText);
            DiagramOutput output;
            String directionInfo;

            if (diagramType == DiagramType.SEQUENCE) {
                SequenceDiagram seq = SequenceParser.parse(mermaidText);
                SequenceLayoutResult seqLayout = new SequenceDiagramLayout().layout(seq, config);
                output = SequenceEmitter.emit(seqLayout);
                directionInfo = "sequence";
            } else {
                DiagramGraph graph = FlowchartParser.parse(mermaidText);
                PositionedDiagram positioned = new LayeredGraphLayout().layout(graph, config);
                output = OOXMLEmitter.emit(positioned, graph.subgraphs());
                directionInfo = graph.direction().code();
            }

            // Detect dark/light theme for coordinated fill + text colors
            boolean isDark = orchestrator.isDarkTheme();
            String nodeFillScheme = isDark ? "lt1" : "dk1";
            String nodeTextScheme = isDark ? "dk1" : "lt1";

            // Step 4: Create shapes, collect SPID map
            // Subgraph bounding boxes are emitted first in the shapes list (rendered behind nodes)
            Map<String, Integer> spidMap = new HashMap<>();
            int subgraphCount = 0;
            for (ShapeSpec spec : output.shapes()) {
                SlideShape.ShapeType shapeType = SlideShape.ShapeType.fromOoxmlPreset(spec.ooxmlPreset());
                ShapeGeometry geometry = new ShapeGeometry(spec.x(), spec.y(), spec.width(), spec.height());

                boolean isSubgraph = spec.nodeId().startsWith("subgraph_");
                ShapeStyle style;
                if (isSubgraph) {
                    // Subgraph: no fill, thin gray border, no theme style
                    style = ShapeStyle.of(ShapeFill.noFill(), ShapeLine.thin("BFBFBF"), ThemeStyleRef.NONE);
                } else if (spec.fillColor() != null) {
                    style = ShapeStyle.withFill(ShapeFill.solid(spec.fillColor()));
                } else {
                    // Theme-aware fill: light nodes on dark bg, dark nodes on light bg
                    style = ShapeStyle.of(ShapeFill.scheme(nodeFillScheme), null, ThemeStyleRef.NONE);
                }

                ExecutionResult<Integer> result = orchestrator.addShape(
                    slideNumber, shapeType, geometry, spec.label(),
                    "mermaid_" + spec.nodeId(), style);

                if (result != null && result.getData().isPresent()) {
                    int spid = result.getData().get();
                    spidMap.put(spec.nodeId(), spid);
                    if (isSubgraph) {
                        subgraphCount++;
                    } else {
                        // Set text body with contrasting color and normAutofit
                        setMermaidNodeText(slideNumber, spid, spec.label(), nodeTextScheme);
                    }
                } else {
                    logger.warn("Failed to create shape for node: " + spec.nodeId());
                }
            }

            // Step 4b: Create edge label shapes as text boxes
            int labelCount = 0;
            for (ShapeSpec labelSpec : output.labelShapes()) {
                ShapeGeometry labelGeom = new ShapeGeometry(
                    labelSpec.x(), labelSpec.y(), labelSpec.width(), labelSpec.height());

                ExecutionResult<Integer> result = orchestrator.addShape(
                    slideNumber, SlideShape.ShapeType.RECTANGLE, labelGeom, labelSpec.label(),
                    "mermaid_" + labelSpec.nodeId(), ShapeStyle.textBox());

                if (result != null && result.getData().isPresent()) {
                    labelCount++;
                }
            }

            // Collect all SPIDs for grouping
            List<Integer> allDiagramSpids = new ArrayList<>(spidMap.values());

            // Step 5: Create connectors with SPID binding
            int connectorCount = 0;
            for (ConnectorSpec conn : output.connectors()) {
                Integer startSpid = spidMap.get(conn.startNodeId());
                Integer endSpid = spidMap.get(conn.endNodeId());

                if (startSpid == null || endSpid == null) {
                    logger.warn("Skipping connector: missing SPID for " +
                        conn.startNodeId() + " -> " + conn.endNodeId());
                    continue;
                }

                ShapeGeometry connGeom = new ShapeGeometry(
                    conn.x(), conn.y(), conn.width(), conn.height());

                // Encode flip flags as customPath prefix for ShapeWriter
                String flipPath = null;
                if (conn.flipH() || conn.flipV()) {
                    String flags = (conn.flipH() ? "H" : "") + (conn.flipV() ? "V" : "");
                    flipPath = "flip:" + flags;
                }

                ExecutionResult<Integer> result = orchestrator.addConnector(
                    slideNumber,
                    conn.connectorType(),
                    connGeom,
                    conn.headEnd(),
                    conn.tailEnd(),
                    conn.lineColor(),
                    conn.lineStyle(),
                    startSpid,
                    conn.startIdx(),
                    endSpid,
                    conn.endIdx(),
                    flipPath
                );

                if (result != null && result.getData().isPresent()) {
                    allDiagramSpids.add(result.getData().get());
                    connectorCount++;
                }
            }

            int nodeShapeCount = spidMap.size() - subgraphCount;
            StringBuilder summary = new StringBuilder();

            // Group all diagram shapes so the LLM can move/resize the diagram as one unit
            if (allDiagramSpids.size() >= 2) {
                ExecutionResult<Integer> groupResult = orchestrator.groupShapes(slideNumber, allDiagramSpids);
                if (groupResult != null && groupResult.getData().isPresent()) {
                    int groupSpid = groupResult.getData().get();
                    summary.append("Created diagram (group SPID ").append(groupSpid).append("): ");
                    summary.append(nodeShapeCount).append(" shapes, ")
                           .append(connectorCount).append(" connectors (type: ").append(directionInfo).append(")");
                    if (labelCount > 0) {
                        summary.append(", ").append(labelCount).append(" edge label(s)");
                    }
                    if (subgraphCount > 0) {
                        summary.append(", ").append(subgraphCount).append(" subgraph(s)");
                    }
                    summary.append(". Use SPID ").append(groupSpid).append(" to move or resize the entire diagram.");
                    return summary.toString();
                }
            }

            // Fallback if grouping failed or only 1 shape
            summary.append("Created diagram: ").append(nodeShapeCount).append(" shapes, ")
                   .append(connectorCount).append(" connectors (type: ").append(directionInfo).append(")");
            if (labelCount > 0) {
                summary.append(", ").append(labelCount).append(" edge label(s)");
            }
            if (subgraphCount > 0) {
                summary.append(", ").append(subgraphCount).append(" subgraph(s)");
            }
            if (usingDefaults) {
                summary.append(" [using default content area position]");
            }
            return summary.toString();

        } catch (FlowchartParser.ParseException | SequenceParser.ParseException e) {
            return "Error parsing mermaid syntax: " + e.getMessage();
        } catch (Exception e) {
            logger.error("Error creating mermaid diagram", e);
            return "Error creating mermaid diagram: " + e.getMessage();
        }
    }

    /**
     * Set a mermaid node's text body with theme-contrasting color and normAutofit.
     * Supports markdown formatting: newlines become separate paragraphs,
     * **bold**, *italic* are converted to text run properties.
     */
    private void setMermaidNodeText(int slideNumber, int spid, String label, String textScheme) {
        try {
            BodyProperties bodyProps = BodyProperties.builder()
                .autofit(AutofitType.NORMAL)
                .build();
            TextBody.Builder bodyBuilder = TextBody.builder().bodyProperties(bodyProps);

            String text = label != null ? label : "";
            // Split on literal \n (from mermaid labels) and actual newlines
            String[] lines = text.split("\\\\n|\n");

            for (String line : lines) {
                TextParagraph.Builder paraBuilder = TextParagraph.builder().alignment("ctr");
                // Parse inline markdown: **bold**, *italic*, ***both***
                parseMarkdownRuns(line, textScheme, paraBuilder);
                bodyBuilder.addParagraph(paraBuilder.build());
            }

            orchestrator.setTextBody(slideNumber, spid, bodyBuilder.build());
        } catch (Exception e) {
            logger.warn("Failed to set mermaid node text for SPID {}: {}", spid, e.getMessage());
        }
    }

    /**
     * Parse inline markdown (bold, italic) into TextRun objects.
     * Handles ***both***, **bold**, and *italic*.
     */
    private void parseMarkdownRuns(String text, String textScheme, TextParagraph.Builder paraBuilder) {
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("\\*\\*\\*(.+?)\\*\\*\\*|\\*\\*(.+?)\\*\\*|\\*(.+?)\\*")
            .matcher(text);

        int lastEnd = 0;
        while (m.find()) {
            // Plain text before this match
            if (m.start() > lastEnd) {
                paraBuilder.addRun(TextRun.builder(text.substring(lastEnd, m.start()))
                    .schemeColor(textScheme).build());
            }
            if (m.group(1) != null) {
                // ***bold italic***
                paraBuilder.addRun(TextRun.builder(m.group(1))
                    .bold(true).italic(true).schemeColor(textScheme).build());
            } else if (m.group(2) != null) {
                // **bold**
                paraBuilder.addRun(TextRun.builder(m.group(2))
                    .bold(true).schemeColor(textScheme).build());
            } else if (m.group(3) != null) {
                // *italic*
                paraBuilder.addRun(TextRun.builder(m.group(3))
                    .italic(true).schemeColor(textScheme).build());
            }
            lastEnd = m.end();
        }
        // Remaining plain text
        if (lastEnd < text.length()) {
            paraBuilder.addRun(TextRun.builder(text.substring(lastEnd))
                .schemeColor(textScheme).build());
        }
        // Empty line -> empty run so the paragraph exists
        if (lastEnd == 0 && text.isEmpty()) {
            paraBuilder.addRun(TextRun.builder("").schemeColor(textScheme).build());
        }
    }

    // --- JSON extraction helpers ---

    private int extractInt(String json, String key) {
        try {
            JsonObject obj = JsonHelper.parseObject(json);
            return JsonHelper.getInt(obj, key, 1);
        } catch (Exception e) { return 1; }
    }

    private long extractLong(String json, String key, long defaultValue) {
        try {
            JsonObject obj = JsonHelper.parseObject(json);
            return JsonHelper.getLong(obj, key, defaultValue);
        } catch (Exception e) { return defaultValue; }
    }

    private String extractString(String json, String key) {
        try {
            JsonObject obj = JsonHelper.parseObject(json);
            return JsonHelper.getString(obj, key);
        } catch (Exception e) { return null; }
    }
}
