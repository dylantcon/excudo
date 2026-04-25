package com.excudo.core.commands.mutating.slide;

import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandExecutionException;
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
import com.excudo.core.model.AutofitType;
import com.excudo.core.model.BodyProperties;
import com.excudo.core.model.ShapeFill;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.ShapeLine;
import com.excudo.core.model.ShapeStyle;
import com.excudo.core.model.SlideShape;
import com.excudo.core.model.TextBody;
import com.excudo.core.model.TextParagraph;
import com.excudo.core.model.TextRun;
import com.excudo.core.model.ThemeStyleRef;
import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.results.ExecutionResult;
import com.excudo.core.utils.ComponentLogger;
import com.excudo.core.utils.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GoF Command for creating a mermaid-syntax diagram (flowchart or
 * sequence) on a slide. Parses the source text, lays it out, allocates
 * shapes/connectors via the orchestrator, and groups the result.
 *
 * <p>Like {@link CreateCodeBoxCommand}, this exists so the compound
 * primitive is a Command-pattern citizen — every allocated SPID is
 * tracked for atomic rollback on partial failure (previously silent
 * leakage: the old MermaidDiagramTool just logged warnings on failed
 * shape creation and proceeded with whatever subset succeeded) and for
 * user-initiated undo of the entire diagram in one step.
 */
public class CreateMermaidDiagramCommand implements Command {

    private static final ComponentLogger logger = Logger.llm();

    private static final long DEFAULT_X = 838200L;
    private static final long DEFAULT_Y = 1825625L;
    private static final long DEFAULT_W = 7772400L;
    private static final long DEFAULT_H = 4525963L;

    private final int slideNumber;
    private final String mermaidText;
    private final long x;
    private final long y;
    private final long w;
    private final long h;
    private final boolean usingDefaults;
    private final PPTXOrchestrator orchestrator;

    // execute() captures every SPID it allocates plus a summary string
    // suitable for echoing back through the MCP tool response.
    private boolean executed = false;
    private final List<Integer> allocatedSpids = new ArrayList<>();
    private Integer groupSpid = null;
    private String resultSummary = null;

    public CreateMermaidDiagramCommand(int slideNumber, String mermaidText,
                                        Long xOrNull, Long yOrNull,
                                        Long wOrNull, Long hOrNull,
                                        PPTXOrchestrator orchestrator) {
        if (orchestrator == null) throw new IllegalArgumentException("PPTXOrchestrator cannot be null");
        if (slideNumber <= 0) throw new IllegalArgumentException("slideNumber must be positive");
        if (mermaidText == null || mermaidText.isBlank()) {
            throw new IllegalArgumentException("mermaidText cannot be empty");
        }
        this.slideNumber = slideNumber;
        this.mermaidText = mermaidText;
        this.usingDefaults = xOrNull == null || yOrNull == null
                          || wOrNull == null || hOrNull == null;
        this.x = xOrNull != null ? xOrNull : DEFAULT_X;
        this.y = yOrNull != null ? yOrNull : DEFAULT_Y;
        this.w = wOrNull != null ? wOrNull : DEFAULT_W;
        this.h = hOrNull != null ? hOrNull : DEFAULT_H;
        this.orchestrator = orchestrator;
    }

    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute",
                "Command has already been executed");
        }
        try {
            if (usingDefaults) {
                logger.info("Using default content area position (x={}, y={}, w={}, h={})", x, y, w, h);
            }

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

            // Theme-aware coordinated fill + text colors. Light nodes on
            // dark masters, dark nodes on light masters.
            boolean isDark = orchestrator.isDarkTheme();
            String nodeFillScheme = isDark ? "lt1" : "dk1";
            String nodeTextScheme = isDark ? "dk1" : "lt1";

            // Step 1: nodes (subgraphs first so they render behind nodes).
            Map<String, Integer> spidMap = new HashMap<>();
            int subgraphCount = 0;
            for (ShapeSpec spec : output.shapes()) {
                SlideShape.ShapeType shapeType = SlideShape.ShapeType.fromOoxmlPreset(spec.ooxmlPreset());
                ShapeGeometry geometry = new ShapeGeometry(
                    spec.x(), spec.y(), spec.width(), spec.height());

                boolean isSubgraph = spec.nodeId().startsWith("subgraph_");
                ShapeStyle style;
                if (isSubgraph) {
                    style = ShapeStyle.of(ShapeFill.noFill(),
                        ShapeLine.thin("BFBFBF"), ThemeStyleRef.NONE);
                } else if (spec.fillColor() != null) {
                    style = ShapeStyle.withFill(ShapeFill.solid(spec.fillColor()));
                } else {
                    style = ShapeStyle.of(ShapeFill.scheme(nodeFillScheme),
                        null, ThemeStyleRef.NONE);
                }

                ExecutionResult<Integer> result = orchestrator.addShape(
                    slideNumber, shapeType, geometry, spec.label(),
                    "mermaid_" + spec.nodeId(), style);
                if (result == null || result.getData().isEmpty()) {
                    throw new CommandExecutionException(getDescription(), "execute",
                        "Failed to create shape for node " + spec.nodeId());
                }
                int spid = result.getData().get();
                allocatedSpids.add(spid);
                spidMap.put(spec.nodeId(), spid);

                if (isSubgraph) {
                    subgraphCount++;
                } else {
                    setMermaidNodeText(orchestrator, slideNumber, spid, spec.label(), nodeTextScheme);
                }
            }

            // Step 2: edge labels as text boxes.
            int labelCount = 0;
            for (ShapeSpec labelSpec : output.labelShapes()) {
                ShapeGeometry labelGeom = new ShapeGeometry(
                    labelSpec.x(), labelSpec.y(),
                    labelSpec.width(), labelSpec.height());
                ExecutionResult<Integer> result = orchestrator.addShape(
                    slideNumber, SlideShape.ShapeType.RECTANGLE, labelGeom,
                    labelSpec.label(),
                    "mermaid_" + labelSpec.nodeId(), ShapeStyle.textBox());
                if (result == null || result.getData().isEmpty()) {
                    throw new CommandExecutionException(getDescription(), "execute",
                        "Failed to create edge label " + labelSpec.nodeId());
                }
                allocatedSpids.add(result.getData().get());
                labelCount++;
            }

            // Step 3: connectors. Build the diagram-spids list as we go
            // so the group call sees nodes + connectors together.
            List<Integer> diagramSpids = new ArrayList<>(spidMap.values());
            int connectorCount = 0;
            for (ConnectorSpec conn : output.connectors()) {
                Integer startSpid = spidMap.get(conn.startNodeId());
                Integer endSpid = spidMap.get(conn.endNodeId());
                if (startSpid == null || endSpid == null) {
                    logger.warn("Skipping connector: missing SPID for "
                        + conn.startNodeId() + " -> " + conn.endNodeId());
                    continue;
                }

                ShapeGeometry connGeom = new ShapeGeometry(
                    conn.x(), conn.y(), conn.width(), conn.height());
                String flipPath = null;
                if (conn.flipH() || conn.flipV()) {
                    String flags = (conn.flipH() ? "H" : "") + (conn.flipV() ? "V" : "");
                    flipPath = "flip:" + flags;
                }

                ExecutionResult<Integer> result = orchestrator.addConnector(
                    slideNumber, conn.connectorType(), connGeom,
                    conn.headEnd(), conn.tailEnd(),
                    conn.lineColor(), conn.lineStyle(),
                    startSpid, conn.startIdx(),
                    endSpid, conn.endIdx(),
                    flipPath);
                if (result == null || result.getData().isEmpty()) {
                    throw new CommandExecutionException(getDescription(), "execute",
                        "Failed to create connector " + conn.startNodeId()
                            + " -> " + conn.endNodeId());
                }
                int connSpid = result.getData().get();
                allocatedSpids.add(connSpid);
                diagramSpids.add(connSpid);
                connectorCount++;
            }

            int nodeShapeCount = spidMap.size() - subgraphCount;

            // Step 4: group all diagram shapes so the LLM can move/resize
            // the diagram as one unit. Partial-success acceptable -- if
            // grouping fails, the children remain individually addressable.
            if (diagramSpids.size() >= 2) {
                ExecutionResult<Integer> groupResult =
                    orchestrator.groupShapes(slideNumber, diagramSpids);
                if (groupResult != null && groupResult.getData().isPresent()) {
                    this.groupSpid = groupResult.getData().get();
                }
            }

            this.resultSummary = buildSummary(
                nodeShapeCount, connectorCount, labelCount, subgraphCount,
                directionInfo, groupSpid);
            executed = true;
        } catch (FlowchartParser.ParseException | SequenceParser.ParseException parseEx) {
            // Mermaid syntax errors: nothing was allocated yet, no rollback needed.
            throw new CommandExecutionException(getDescription(), "execute",
                "Error parsing mermaid syntax: " + parseEx.getMessage(), parseEx);
        } catch (RuntimeException e) {
            rollbackChildren();
            allocatedSpids.clear();
            throw e;
        }
    }

    @Override
    public void undo() {
        if (!executed) {
            throw new CommandExecutionException(getDescription(), "undo",
                "Command has not been executed");
        }
        if (groupSpid != null) {
            orchestrator.removeShape(slideNumber, groupSpid);
            groupSpid = null;
        }
        rollbackChildren();
        executed = false;
    }

    @Override
    public boolean canUndo() {
        return executed;
    }

    @Override
    public boolean isExecuted() {
        return executed;
    }

    @Override
    public String getDescription() {
        return "Create mermaid diagram on slide " + slideNumber;
    }

    /** Result summary string for the MCP tool response. Null until executed. */
    public String getResultSummary() {
        return resultSummary;
    }

    /** Group SPID, or null if grouping failed / wasn't attempted. */
    public Integer getGroupSpid() {
        return groupSpid;
    }

    /** Best-effort: remove every SPID we allocated, in reverse order. */
    private void rollbackChildren() {
        for (int i = allocatedSpids.size() - 1; i >= 0; i--) {
            try {
                orchestrator.removeShape(slideNumber, allocatedSpids.get(i));
            } catch (RuntimeException ignored) {
                // Swallow -- the original failure (or the user's undo)
                // is what should surface, not a cleanup error.
            }
        }
        allocatedSpids.clear();
    }

    private String buildSummary(int nodeShapeCount, int connectorCount, int labelCount,
                                 int subgraphCount, String directionInfo, Integer group) {
        StringBuilder sb = new StringBuilder();
        if (group != null) {
            sb.append("Created diagram (group SPID ").append(group).append("): ");
        } else {
            sb.append("Created diagram: ");
        }
        sb.append(nodeShapeCount).append(" shapes, ")
          .append(connectorCount).append(" connectors (type: ")
          .append(directionInfo).append(")");
        if (labelCount > 0) {
            sb.append(", ").append(labelCount).append(" edge label(s)");
        }
        if (subgraphCount > 0) {
            sb.append(", ").append(subgraphCount).append(" subgraph(s)");
        }
        if (group != null) {
            sb.append(". Use SPID ").append(group)
              .append(" to move or resize the entire diagram.");
        } else if (usingDefaults) {
            sb.append(" [using default content area position]");
        }
        return sb.toString();
    }

    // ====================================================================
    // Static helpers (extracted from the old MermaidDiagramTool instance).
    // Pure functions of (orchestrator, slideNumber, spid, label, scheme).
    // ====================================================================

    /** Set a mermaid node's text body with theme-contrasting color and
     *  normAutofit. Markdown formatting (** *) becomes runs. */
    static void setMermaidNodeText(PPTXOrchestrator orchestrator, int slideNumber,
                                    int spid, String label, String textScheme) {
        try {
            BodyProperties bodyProps = BodyProperties.builder()
                .autofit(AutofitType.NORMAL)
                .build();
            TextBody.Builder bodyBuilder = TextBody.builder().bodyProperties(bodyProps);

            String text = label != null ? label : "";
            String[] lines = text.split("\\\\n|\n");
            for (String line : lines) {
                TextParagraph.Builder paraBuilder = TextParagraph.builder().alignment("ctr");
                parseMarkdownRuns(line, textScheme, paraBuilder);
                bodyBuilder.addParagraph(paraBuilder.build());
            }
            orchestrator.setTextBody(slideNumber, spid, bodyBuilder.build());
        } catch (Exception e) {
            logger.warn("Failed to set mermaid node text for SPID {}: {}", spid, e.getMessage());
        }
    }

    /** Parse inline markdown ({@code ***both***}, {@code **bold**},
     *  {@code *italic*}) into TextRun objects. */
    static void parseMarkdownRuns(String text, String textScheme, TextParagraph.Builder paraBuilder) {
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("\\*\\*\\*(.+?)\\*\\*\\*|\\*\\*(.+?)\\*\\*|\\*(.+?)\\*")
            .matcher(text);

        int lastEnd = 0;
        while (m.find()) {
            if (m.start() > lastEnd) {
                paraBuilder.addRun(TextRun.builder(text.substring(lastEnd, m.start()))
                    .schemeColor(textScheme).build());
            }
            if (m.group(1) != null) {
                paraBuilder.addRun(TextRun.builder(m.group(1))
                    .bold(true).italic(true).schemeColor(textScheme).build());
            } else if (m.group(2) != null) {
                paraBuilder.addRun(TextRun.builder(m.group(2))
                    .bold(true).schemeColor(textScheme).build());
            } else if (m.group(3) != null) {
                paraBuilder.addRun(TextRun.builder(m.group(3))
                    .italic(true).schemeColor(textScheme).build());
            }
            lastEnd = m.end();
        }
        if (lastEnd < text.length()) {
            paraBuilder.addRun(TextRun.builder(text.substring(lastEnd))
                .schemeColor(textScheme).build());
        }
        if (lastEnd == 0 && text.isEmpty()) {
            paraBuilder.addRun(TextRun.builder("").schemeColor(textScheme).build());
        }
    }
}
