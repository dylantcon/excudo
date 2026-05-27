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
 * Rename a shape's {@code cNvPr/@name}. Snapshot-based undo: captures
 * the previous name at execute time and restores it on undo, mirroring
 * other shape-mutation commands.
 *
 * <p>Self-registers via {@link CommandClassRegistry}: the canonical name
 * {@code rename-shape} derives from the class name.
 */
public class RenameShapeCommand implements Command {

    static final Parameter<Integer> SLIDE = Parameter.ofInt("slide")
        .slideNumber().description("Slide number").llmName("slideNumber").required().build();
    static final Parameter<Integer> SPID = Parameter.ofInt("spid")
        .spid().description("SPID of the shape to rename").llmName("targetSpid").required().build();
    static final Parameter<String> NEW_NAME = Parameter.ofString("name")
        .description("New cNvPr/@name value for the shape").llmName("newName").required().build();

    public static final CommandSchema SCHEMA = CommandSchema.builder()
        .description("Rename a shape's cNvPr/@name")
        .llmEnabled(true)
        .llmDescription("Rename a shape's cNvPr/@name attribute. SPID and all other "
            + "shape attributes are preserved. Undoable.")
        .parameter(SLIDE)
        .parameter(SPID)
        .parameter(NEW_NAME)
        .example("rename-shape 1 5 \"Title Banner\"")
        .build();

    public static final String NAME = CommandClassRegistry.nameOf(RenameShapeCommand.class);

    public static Command fromParameters(CommandParameters p, CommandContext ctx) {
        return new RenameShapeCommand(p.get(SLIDE), p.get(SPID), p.get(NEW_NAME), ctx.orchestrator());
    }

    private final int slideNumber;
    private final int spid;
    private final String newName;
    private final PPTXOrchestrator orchestrator;
    private boolean executed = false;
    private String originalName = null;

    public RenameShapeCommand(int slideNumber, int spid, String newName, PPTXOrchestrator orchestrator) {
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
        this.newName = newName;
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
        originalName = shape.getName();

        ExecutionResult<Void> result = orchestrator.updateShapeName(slideNumber, spid, newName);
        if (!result.isSuccess()) {
            originalName = null;
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
        if (!canUndo()) {
            throw new CommandExecutionException(getDescription(), UndoCommand.NAME,
                "No captured original name for undo");
        }
        ExecutionResult<Void> result = orchestrator.updateShapeName(slideNumber, spid, originalName);
        if (!result.isSuccess()) {
            throw new CommandExecutionException(getDescription(), UndoCommand.NAME, result.getMessage());
        }
        executed = false;
    }

    @Override
    public boolean canUndo() { return executed && originalName != null; }

    @Override
    public boolean isExecuted() { return executed; }

    @Override
    public String getDescription() {
        return "Rename shape SPID " + spid + " on slide " + slideNumber + " to '" + newName + "'";
    }
}
