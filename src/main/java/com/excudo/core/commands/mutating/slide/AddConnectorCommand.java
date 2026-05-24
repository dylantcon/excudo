package com.excudo.core.commands.mutating.slide;

import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandClassRegistry;
import com.excudo.core.commands.CommandContext;
import com.excudo.core.commands.CommandExecutionException;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.parsing.CommandParameters;
import com.excudo.core.parsing.CommandSchema;
import com.excudo.core.parsing.Parameter;
import com.excudo.core.results.ExecutionResult;

/**
 * GoF Command for adding connector shapes (p:cxnSp) to slides.
 *
 * Connectors in OOXML use p:cxnSp elements (not p:sp) and can optionally
 * bind to connection points on other shapes via start/end SPID and index.
 *
 * <p>Self-registers via {@link com.excudo.core.commands.CommandClassRegistry}:
 * the canonical name {@code add-connector} derives from the class name.
 */
public class AddConnectorCommand implements Command {

    static final Parameter<Integer> SLIDE = Parameter.ofInt("slide")
        .slideNumber().description("Slide number").required().build();
    static final Parameter<String> TYPE = Parameter.ofString("type")
        .description("Connector type: line, straight, elbow, curved").required().build();
    static final Parameter<Double> X = Parameter.ofDouble("x")
        .description("X position in EMUs").required().build();
    static final Parameter<Double> Y = Parameter.ofDouble("y")
        .description("Y position in EMUs").required().build();
    static final Parameter<Double> WIDTH = Parameter.ofDouble("width")
        .description("Width in EMUs").required().build();
    static final Parameter<Double> HEIGHT = Parameter.ofDouble("height")
        .description("Height in EMUs").required().build();
    static final Parameter<String> HEAD_END = Parameter.ofString("head-end")
        .description("Head end type: none, triangle, arrow").required(false).build();
    static final Parameter<String> TAIL_END = Parameter.ofString("tail-end")
        .description("Tail end type: none, triangle, arrow").required(false).build();
    static final Parameter<String> LINE_COLOR = Parameter.ofString("line-color")
        .description("Line color: hex (FF0000) or scheme (accent1)").required(false).build();
    static final Parameter<String> START = Parameter.ofString("start")
        .description("Start binding: spid:idx").required(false).build();
    static final Parameter<String> END = Parameter.ofString("end")
        .description("End binding: spid:idx").required(false).build();
    static final Parameter<String> PATH = Parameter.ofString("path")
        .description("Custom geometry path: M x y L x y C x1 y1 x2 y2 x y").required(false).build();

    public static final CommandSchema SCHEMA = CommandSchema.builder()
        .description("Add a connector shape to a slide")
        .parameter(SLIDE)
        .parameter(TYPE)
        .parameter(X)
        .parameter(Y)
        .parameter(WIDTH)
        .parameter(HEIGHT)
        .parameter(HEAD_END)
        .parameter(TAIL_END)
        .parameter(LINE_COLOR)
        .parameter(START)
        .parameter(END)
        .parameter(PATH)
        .example("add-connector 1 line 100 100 500 0")
        .example("add-connector 1 elbow 100 100 500 300 --tail-end triangle")
        .example("add-connector 1 line 100 100 500 0 --start 3:2 --end 5:0")
        .build();

    public static final String NAME = CommandClassRegistry.nameOf(AddConnectorCommand.class);

    public static Command fromParameters(CommandParameters p, CommandContext ctx) {
        ShapeGeometry geometry = new ShapeGeometry(
            p.get(X).longValue(), p.get(Y).longValue(),
            p.get(WIDTH).longValue(), p.get(HEIGHT).longValue());
        int[] startBind = parseEndpoint(p.opt(START).orElse(null));
        int[] endBind = parseEndpoint(p.opt(END).orElse(null));
        return new AddConnectorCommand(p.get(SLIDE), p.get(TYPE), geometry,
            p.opt(HEAD_END).orElse(null), p.opt(TAIL_END).orElse(null),
            p.opt(LINE_COLOR).orElse(null), null,
            startBind[0] < 0 ? null : startBind[0], startBind[1] < 0 ? null : startBind[1],
            endBind[0] < 0 ? null : endBind[0], endBind[1] < 0 ? null : endBind[1],
            p.opt(PATH).orElse(null), ctx.orchestrator());
    }

    private static int[] parseEndpoint(String binding) {
        if (binding == null || !binding.contains(":")) return new int[]{-1, -1};
        String[] parts = binding.split(":");
        return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
    }

    private final int slideNumber;
    private final String connectorType; // line, straight, elbow, curved
    private final ShapeGeometry geometry;
    private final String headEnd; // none, triangle, arrow, etc.
    private final String tailEnd;
    private final String lineColor; // hex or scheme
    private final String lineStyle; // null/solid/dash
    private final Integer startSpid; // connection start shape
    private final Integer startIdx;  // connection point index
    private final Integer endSpid;   // connection end shape
    private final Integer endIdx;
    private final String customPath; // for freeform connectors
    private final PPTXOrchestrator orchestrator;
    private boolean executed = false;
    private Integer createdSpid = null;

    public AddConnectorCommand(int slideNumber, String connectorType, ShapeGeometry geometry,
                               String headEnd, String tailEnd, String lineColor, String lineStyle,
                               Integer startSpid, Integer startIdx, Integer endSpid, Integer endIdx,
                               String customPath, PPTXOrchestrator orchestrator) {
        if (orchestrator == null) throw new IllegalArgumentException("PPTXOrchestrator cannot be null");
        if (geometry == null) throw new IllegalArgumentException("Geometry cannot be null");
        this.slideNumber = slideNumber;
        this.connectorType = connectorType != null ? connectorType : "line";
        this.geometry = geometry;
        this.headEnd = headEnd;
        this.tailEnd = tailEnd;
        this.lineColor = lineColor;
        this.lineStyle = lineStyle;
        this.startSpid = startSpid;
        this.startIdx = startIdx;
        this.endSpid = endSpid;
        this.endIdx = endIdx;
        this.customPath = customPath;
        this.orchestrator = orchestrator;
    }

    @Override
    public void execute() {
        if (executed) throw new CommandExecutionException(getDescription(), "execute", "Already executed");
        try {
            ExecutionResult<Integer> result = orchestrator.addConnector(slideNumber, connectorType, geometry,
                headEnd, tailEnd, lineColor, lineStyle, startSpid, startIdx, endSpid, endIdx, customPath);
            if (result.isSuccess()) {
                createdSpid = result.getData().orElse(null);
                executed = true;
            } else {
                throw new CommandExecutionException(getDescription(), "execute", "Failed: " + result.getMessage());
            }
        } catch (CommandExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new CommandExecutionException(getDescription(), "execute", e.getMessage(), e);
        }
    }

    @Override
    public void undo() {
        if (!executed || !canUndo()) throw new CommandExecutionException(getDescription(), "undo", "Cannot undo");
        try {
            orchestrator.removeShape(slideNumber, createdSpid);
            executed = false;
            createdSpid = null;
        } catch (Exception e) {
            throw new CommandExecutionException(getDescription(), "undo", e.getMessage(), e);
        }
    }

    @Override
    public boolean canUndo() { return executed && createdSpid != null; }

    @Override
    public String getDescription() {
        return String.format("Add %s connector on slide %d at (%d,%d)", connectorType, slideNumber, geometry.getX(), geometry.getY());
    }

    @Override
    public boolean isExecuted() { return executed; }

    public Integer getCreatedSpid() { return createdSpid; }
}
