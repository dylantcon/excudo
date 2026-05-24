package com.excudo.core.commands.mutating.slide;

import com.excudo.core.commands.meta.UndoCommand;
import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandExecutionException;

import com.excudo.core.model.ShapeRegistry;
import com.excudo.core.model.SlideShape;
import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.results.ExecutionResult;

/**
 * Rename a shape's {@code cNvPr/@name}. Snapshot-based undo: captures
 * the previous name at execute time and restores it on undo, mirroring
 * other shape-mutation commands.
 */
public class RenameShapeCommand implements Command {

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
