package com.excudo.core.commands.mutating.slide;

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
import java.util.Map;

/**
 * GoF Command for updating font properties on a shape's text runs.
 *
 * Captures the full shape DOM element before modification so undo can
 * restore it exactly. Delegates to orchestrator.updateShapeTextProperties().
 */
public class SetFontCommand implements Command {

    static final Parameter<Integer> SLIDE = Parameter.ofInt("slide")
        .slideNumber().description("Slide number").llmName("slideNumber").required().build();
    static final Parameter<Integer> SPID = Parameter.ofInt("spid")
        .spid().description("Shape ID").llmName("targetSpid").required().build();
    static final Parameter<String> FAMILY = Parameter.ofString("family")
        .description("Font family name (e.g. Arial, Calibri)").required(false).build();
    static final Parameter<Integer> SIZE = Parameter.ofInt("size")
        .description("Font size in points").required(false).build();
    static final Parameter<String> BOLD = Parameter.ofString("bold")
        .description("Bold: true or false").required(false).build();
    static final Parameter<String> ITALIC = Parameter.ofString("italic")
        .description("Italic: true or false").required(false).build();
    static final Parameter<String> UNDERLINE = Parameter.ofString("underline")
        .description("Underline: true or false").required(false).build();
    static final Parameter<String> COLOR = Parameter.ofString("color")
        .description("Font color as hex (e.g. FF0000) or scheme name (e.g. accent1)")
        .required(false).build();

    public static final CommandSchema SCHEMA = CommandSchema.builder()
        .description("Set font properties on a shape's text")
        .llmEnabled(true)
        .llmDescription("Set font properties on a shape.")
        .parameter(SLIDE).parameter(SPID)
        .parameter(FAMILY).parameter(SIZE).parameter(BOLD).parameter(ITALIC)
        .parameter(UNDERLINE).parameter(COLOR)
        .example("set-font 1 2 --family Arial --size 24 --bold true")
        .example("set-font 1 2 --color FF0000 --italic true")
        .build();

    public static final String NAME = CommandClassRegistry.nameOf(SetFontCommand.class);

    public static Command fromParameters(CommandParameters p, CommandContext ctx) {
        java.util.Map<String, Object> props = new java.util.HashMap<>();
        p.opt(FAMILY).ifPresent(v -> props.put("family", v));
        p.opt(SIZE).ifPresent(v -> props.put("size", v));
        p.opt(BOLD).ifPresent(v -> props.put("bold", "true".equalsIgnoreCase(v) || "1".equals(v)));
        p.opt(ITALIC).ifPresent(v -> props.put("italic", "true".equalsIgnoreCase(v) || "1".equals(v)));
        p.opt(UNDERLINE).ifPresent(v -> props.put("underline", "true".equalsIgnoreCase(v) || "1".equals(v)));
        p.opt(COLOR).ifPresent(v -> props.put("color", v));
        return new SetFontCommand(p.get(SLIDE), p.get(SPID), props, ctx.orchestrator());
    }


    private final int slideNumber;
    private final int spid;
    private final Map<String, Object> fontProperties;
    private final PPTXOrchestrator orchestrator;
    private boolean executed = false;
    private org.w3c.dom.Element capturedElement = null;

    /**
     * @param slideNumber  Slide number (1-based)
     * @param spid         Shape SPID to modify
     * @param fontProperties  Map of font properties to apply. Recognized keys:
     *                        "family" (String), "size" (int pts), "bold" (Boolean),
     *                        "italic" (Boolean), "underline" (Boolean), "color" (String hex/scheme)
     * @param orchestrator PPTXOrchestrator instance
     */
    public SetFontCommand(int slideNumber, int spid, Map<String, Object> fontProperties,
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
        if (fontProperties == null || fontProperties.isEmpty()) {
            throw new IllegalArgumentException("Font properties map cannot be null or empty");
        }
        this.slideNumber = slideNumber;
        this.spid = spid;
        this.fontProperties = fontProperties;
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
            orchestrator.updateShapeTextProperties(slideNumber, spid, fontProperties);
        if (!result.isSuccess()) {
            capturedElement = null;
            throw new CommandExecutionException(getDescription(), "execute",
                "Failed to update font properties: " + result.getMessage());
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
                "Failed to remove modified shape during undo: " + removeResult.getMessage());
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
        return String.format("Set font properties on shape SPID %d on slide %d", spid, slideNumber);
    }

    @Override
    public boolean isExecuted() {
        return executed;
    }

    public int getSlideNumber() { return slideNumber; }
    public int getSpid() { return spid; }
}
