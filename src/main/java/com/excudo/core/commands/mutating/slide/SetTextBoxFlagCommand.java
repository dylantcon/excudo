package com.excudo.core.commands.mutating.slide;

import com.excudo.core.commands.meta.UndoCommand;
import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandClassRegistry;
import com.excudo.core.commands.CommandContext;
import com.excudo.core.commands.CommandExecutionException;

import com.excudo.core.model.ShapeRegistry;
import com.excudo.core.model.SlideShape;
import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.parsing.CommandParameters;
import com.excudo.core.parsing.CommandSchema;
import com.excudo.core.parsing.Parameter;
import com.excudo.core.results.ExecutionResult;

/**
 * Toggle the {@code cNvSpPr/@txBox} marker on an existing shape.
 * Avoids the structural remove+add shuffle that would be needed if
 * only create-time textbox marking were supported; keeps the shape's
 * SPID and every other attribute stable. Snapshot-based undo.
 *
 * <p>Self-registers via {@link CommandClassRegistry}: the canonical name
 * {@code set-text-box-flag} derives from the class name.
 */
public class SetTextBoxFlagCommand implements Command {

    static final Parameter<Integer> SLIDE = Parameter.ofInt("slide")
        .slideNumber().description("Slide number").llmName("slideNumber").required().build();
    static final Parameter<Integer> SPID = Parameter.ofInt("spid")
        .spid().description("SPID of the shape to mark").llmName("targetSpid").required().build();
    static final Parameter<Boolean> FLAG = Parameter.ofBool("flag")
        .description("true to mark as a true textbox; false to mark as autoshape containing text")
        .llmName("textBox").required().build();

    public static final CommandSchema SCHEMA = CommandSchema.builder()
        .description("Toggle the cNvSpPr/@txBox marker on an existing shape (true textbox vs autoshape with text)")
        .llmEnabled(true)
        .llmDescription("Toggle whether a shape is a true textbox or an autoshape "
            + "containing text. Affects PowerPoint's word-wrap, auto-resize, and paragraph "
            + "default behaviour. SPID-stable -- no structural remove/add needed.")
        .parameter(SLIDE)
        .parameter(SPID)
        .parameter(FLAG)
        .example("set-text-box-flag 1 5 true")
        .build();

    public static final String NAME = CommandClassRegistry.nameOf(SetTextBoxFlagCommand.class);

    public static Command fromParameters(CommandParameters p, CommandContext ctx) {
        return new SetTextBoxFlagCommand(p.get(SLIDE), p.get(SPID), p.get(FLAG), ctx.orchestrator());
    }

    private final int slideNumber;
    private final int spid;
    private final boolean newFlag;
    private final PPTXOrchestrator orchestrator;
    private boolean executed = false;
    private Boolean originalFlag = null;

    public SetTextBoxFlagCommand(int slideNumber, int spid, boolean newFlag, PPTXOrchestrator orchestrator) {
        if (orchestrator == null) {
            throw new IllegalArgumentException("PPTXOrchestrator cannot be null");
        }
        if (slideNumber <= 0) {
            throw new IllegalArgumentException("Slide number must be positive");
        }
        if (spid <= 0) {
            throw new IllegalArgumentException("SPID must be positive");
        }
        this.slideNumber = slideNumber;
        this.spid = spid;
        this.newFlag = newFlag;
        this.orchestrator = orchestrator;
    }

    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute",
                "Command has already been executed");
        }
        ExecutionResult<ShapeRegistry> reg = orchestrator.getShapeRegistry(slideNumber);
        if (!reg.isSuccess()) {
            throw new CommandExecutionException(getDescription(), "execute",
                "Failed to get shape registry: " + reg.getMessage());
        }
        SlideShape shape = reg.getData().orElseThrow().getShape(spid);
        if (shape == null) {
            throw new CommandExecutionException(getDescription(), "execute",
                "Shape with SPID " + spid + " not found");
        }
        originalFlag = shape.isTextBox();

        ExecutionResult<Void> result = orchestrator.updateShapeTextBoxFlag(slideNumber, spid, newFlag);
        if (!result.isSuccess()) {
            originalFlag = null;
            throw new CommandExecutionException(getDescription(), "execute", result.getMessage());
        }
        executed = true;
    }

    @Override
    public void undo() {
        if (!executed) {
            throw new CommandExecutionException(getDescription(), UndoCommand.NAME,
                "Command has not been executed");
        }
        if (originalFlag == null) {
            throw new CommandExecutionException(getDescription(), UndoCommand.NAME,
                "No captured original flag for undo");
        }
        ExecutionResult<Void> r = orchestrator.updateShapeTextBoxFlag(slideNumber, spid, originalFlag);
        if (!r.isSuccess()) {
            throw new CommandExecutionException(getDescription(), UndoCommand.NAME, r.getMessage());
        }
        executed = false;
    }

    @Override public boolean canUndo() { return executed && originalFlag != null; }
    @Override public boolean isExecuted() { return executed; }

    @Override
    public String getDescription() {
        return "SetTextBoxFlag(slide=" + slideNumber + ", spid=" + spid + ", flag=" + newFlag + ")";
    }
}
