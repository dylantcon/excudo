package com.excudo.core.commands.mutating.slide;

import com.excudo.core.commands.meta.UndoCommand;
import com.excudo.core.commands.Command;
import com.excudo.core.parsing.Parameter;
import com.excudo.core.parsing.CommandSchema;
import com.excudo.core.parsing.CommandParameters;
import com.excudo.core.commands.CommandContext;
import com.excudo.core.commands.CommandClassRegistry;
import com.excudo.core.commands.CommandExecutionException;

import com.excudo.core.model.ShapeStyle;
import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.results.ExecutionResult;

/**
 * GoF Command for applying fill/line style overrides to an existing shape.
 *
 * Captures the full shape DOM element before modification so undo can
 * restore it exactly. Delegates to orchestrator.updateShapeStyle().
 */
public class SetStyleCommand implements Command {

    static final Parameter<Integer> SLIDE = Parameter.ofInt("slide")
        .slideNumber().description("Slide number").llmName("slideNumber").required().build();
    static final Parameter<Integer> SPID = Parameter.ofInt("spid")
        .spid().description("Shape ID").llmName("targetSpid").required().build();
    static final Parameter<String> FILL_COLOR = Parameter.ofString("fill-color")
        .description("Fill color: hex (FF0000) or scheme name (accent1)")
        .llmName("fillColor").required(false).build();
    static final Parameter<String> LINE_COLOR = Parameter.ofString("line-color")
        .description("Line/border color: hex (FF0000) or scheme name (accent1)")
        .llmName("lineColor").required(false).build();
    static final Parameter<Integer> FILL_ALPHA = Parameter.ofInt("fill-alpha")
        .description("Fill opacity 0-100 (percent)")
        .llmName("fillAlpha").required(false).build();
    static final Parameter<Integer> LINE_ALPHA = Parameter.ofInt("line-alpha")
        .description("Line opacity 0-100 (percent)")
        .llmName("lineAlpha").required(false).build();

    public static final CommandSchema SCHEMA = CommandSchema.builder()
        .description("Set fill and line style on an existing shape")
        .llmEnabled(true)
        .llmDescription("Set fill and line style on a shape.")
        .parameter(SLIDE).parameter(SPID)
        .parameter(FILL_COLOR).parameter(LINE_COLOR).parameter(FILL_ALPHA).parameter(LINE_ALPHA)
        .example("set-style 1 3 --fill-color FF5733")
        .example("set-style 1 3 --fill-color accent2 --line-color dk1")
        .build();

    public static final String NAME = CommandClassRegistry.nameOf(SetStyleCommand.class);

    public static Command fromParameters(CommandParameters p, CommandContext ctx) {
        com.excudo.core.model.ShapeStyle style =
            com.excudo.core.commands.ShapeCommandFactory.parseShapeStyle(
                p.opt(FILL_COLOR).orElse(null), p.opt(LINE_COLOR).orElse(null),
                p.opt(FILL_ALPHA).orElse(null), p.opt(LINE_ALPHA).orElse(null));
        if (style == null) style = com.excudo.core.model.ShapeStyle.defaultStyle();
        return new SetStyleCommand(p.get(SLIDE), p.get(SPID), style, ctx.orchestrator());
    }


    private final int slideNumber;
    private final int spid;
    private final ShapeStyle style;
    private final PPTXOrchestrator orchestrator;
    private boolean executed = false;
    private org.w3c.dom.Element capturedElement = null;

    /**
     * @param slideNumber Slide number (1-based)
     * @param spid        Shape SPID to re-style
     * @param style       ShapeStyle containing fill, line, and theme style ref overrides
     * @param orchestrator PPTXOrchestrator instance
     */
    public SetStyleCommand(int slideNumber, int spid, ShapeStyle style,
                           PPTXOrchestrator orchestrator) {
        if (orchestrator == null) {
            throw new IllegalArgumentException("PPTXOrchestrator cannot be null");
        }
        if (slideNumber <= 0) {
            throw new IllegalArgumentException("Slide number must be positive");
        }
        if (spid <= 0) {
            throw new IllegalArgumentException("SPID must be positive");
        }
        if (style == null) {
            throw new IllegalArgumentException("ShapeStyle cannot be null");
        }
        this.slideNumber = slideNumber;
        this.spid = spid;
        this.style = style;
        this.orchestrator = orchestrator;
    }

    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute",
                "Command has already been executed");
        }

        ExecutionResult<org.w3c.dom.Element> captureResult =
            orchestrator.captureShapeElement(slideNumber, spid);
        if (!captureResult.isSuccess()) {
            throw new CommandExecutionException(getDescription(), "execute",
                "Failed to capture shape for undo: " + captureResult.getMessage());
        }
        capturedElement = captureResult.getData().orElse(null);

        ExecutionResult<Void> result =
            orchestrator.updateShapeStyle(slideNumber, spid, style);
        if (!result.isSuccess()) {
            capturedElement = null;
            throw new CommandExecutionException(getDescription(), "execute",
                "Failed to update shape style: " + result.getMessage());
        }

        executed = true;
    }

    @Override
    public void undo() {
        if (!executed) {
            throw new CommandExecutionException(getDescription(), UndoCommand.NAME,
                "Command has not been executed");
        }
        if (!canUndo()) {
            throw new CommandExecutionException(getDescription(), UndoCommand.NAME,
                "No captured state available for undo");
        }

        ExecutionResult<Void> removeResult = orchestrator.removeShape(slideNumber, spid);
        if (!removeResult.isSuccess()) {
            throw new CommandExecutionException(getDescription(), UndoCommand.NAME,
                "Failed to remove styled shape during undo: " + removeResult.getMessage());
        }

        ExecutionResult<Void> restoreResult =
            orchestrator.restoreShape(slideNumber, capturedElement);
        if (!restoreResult.isSuccess()) {
            throw new CommandExecutionException(getDescription(), UndoCommand.NAME,
                "Failed to restore shape: " + restoreResult.getMessage());
        }

        executed = false;
        capturedElement = null;
    }

    @Override
    public boolean canUndo() {
        return executed && capturedElement != null;
    }

    @Override
    public String getDescription() {
        return String.format("Set style on shape SPID %d on slide %d", spid, slideNumber);
    }

    @Override
    public boolean isExecuted() {
        return executed;
    }

    public int getSlideNumber() { return slideNumber; }
    public int getSpid() { return spid; }
    public ShapeStyle getStyle() { return style; }
}
