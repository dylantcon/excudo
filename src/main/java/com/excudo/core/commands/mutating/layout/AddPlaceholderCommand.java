package com.excudo.core.commands.mutating.layout;

import com.excudo.core.commands.meta.UndoCommand;
import com.excudo.core.commands.Command;
import com.excudo.core.parsing.Parameter;
import com.excudo.core.parsing.CommandSchema;
import com.excudo.core.parsing.CommandParameters;
import com.excudo.core.commands.CommandContext;
import com.excudo.core.commands.CommandClassRegistry;
import com.excudo.core.commands.CommandExecutionException;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.results.ExecutionResult;

/**
 * GoF Command for adding a placeholder to an existing layout.
 */
public class AddPlaceholderCommand implements Command {

    static final Parameter<String> LAYOUT_ID = Parameter.ofString("layoutId")
        .description("Layout ID (e.g., slideLayout11)").required().build();
    static final Parameter<String> TYPE = Parameter.ofString("type")
        .description("Placeholder type (obj, pic, title, body, chart, tbl)")
        .validValues("obj", "pic", "title", "body", "ctrTitle", "subTitle",
            "chart", "tbl", "dt", "ftr", "sldNum")
        .required(false).build();
    static final Parameter<Integer> IDX = Parameter.ofInt("idx")
        .description("Placeholder index").required().build();
    static final Parameter<Double> X = Parameter.ofDouble("x")
        .description("X position in EMUs").required().build();
    static final Parameter<Double> Y = Parameter.ofDouble("y")
        .description("Y position in EMUs").required().build();
    static final Parameter<Double> CX = Parameter.ofDouble("cx")
        .description("Width in EMUs").required().build();
    static final Parameter<Double> CY = Parameter.ofDouble("cy")
        .description("Height in EMUs").required().build();

    public static final CommandSchema SCHEMA = CommandSchema.builder()
        .description("Add a placeholder to an existing layout")
        .llmEnabled(true)
        .llmDescription("Add a placeholder shape to a layout.")
        .parameter(LAYOUT_ID).parameter(TYPE).parameter(IDX)
        .parameter(X).parameter(Y).parameter(CX).parameter(CY)
        .example("add-placeholder slideLayout11 --type pic --idx 3 --x 838200 --y 1825625 --cx 4838700 --cy 4351338")
        .build();

    public static final String NAME = CommandClassRegistry.nameOf(AddPlaceholderCommand.class);

    public static Command fromParameters(CommandParameters p, CommandContext ctx) {
        return new AddPlaceholderCommand(p.get(LAYOUT_ID),
            p.opt(TYPE).orElse("obj"), p.get(IDX),
            p.get(X).longValue(), p.get(Y).longValue(),
            p.get(CX).longValue(), p.get(CY).longValue(),
            ctx.orchestrator());
    }


    private final String layoutId;
    private final String type;
    private final int idx;
    private final long x, y, cx, cy;
    private final PPTXOrchestrator orchestrator;
    private boolean executed = false;

    public AddPlaceholderCommand(String layoutId, String type, int idx,
                                 long x, long y, long cx, long cy,
                                 PPTXOrchestrator orchestrator) {
        if (orchestrator == null) {
            throw new IllegalArgumentException("PPTXOrchestrator cannot be null");
        }
        if (layoutId == null || layoutId.trim().isEmpty()) {
            throw new IllegalArgumentException("Layout ID cannot be null or empty");
        }
        this.layoutId = layoutId;
        this.type = type != null ? type : "obj";
        this.idx = idx;
        this.x = x;
        this.y = y;
        this.cx = cx;
        this.cy = cy;
        this.orchestrator = orchestrator;
    }

    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute", "Command has already been executed");
        }

        ExecutionResult<Void> result = orchestrator.addPlaceholder(layoutId, type, idx, x, y, cx, cy);
        if (!result.isSuccess()) {
            throw new CommandExecutionException(getDescription(), "execute",
                "Failed to add placeholder: " + result.getMessage());
        }

        executed = true;
    }

    @Override
    public void undo() {
        if (!executed) {
            throw new CommandExecutionException(getDescription(), UndoCommand.NAME, "Command has not been executed");
        }

        ExecutionResult<Void> result = orchestrator.removePlaceholder(layoutId, idx);
        if (!result.isSuccess()) {
            throw new CommandExecutionException(getDescription(), UndoCommand.NAME,
                "Failed to undo add placeholder: " + result.getMessage());
        }

        executed = false;
    }

    @Override
    public boolean canUndo() {
        return executed;
    }

    @Override
    public String getDescription() {
        return "Add placeholder type=" + type + " idx=" + idx + " to " + layoutId;
    }

    @Override
    public boolean isExecuted() {
        return executed;
    }
}
