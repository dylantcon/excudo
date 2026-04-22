package com.excudo.core.commands.mutating.slide;

import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandExecutionException;

import com.excudo.core.model.ShapeRegistry;
import com.excudo.core.model.SlideShape;
import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.results.ExecutionResult;

/**
 * Toggle the {@code cNvSpPr/@txBox} marker on an existing shape.
 * Avoids the structural remove+add shuffle that would be needed if
 * only create-time textbox marking were supported; keeps the shape's
 * SPID and every other attribute stable. Snapshot-based undo.
 */
public class SetTextBoxFlagCommand implements Command {

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
            throw new CommandExecutionException(getDescription(), "undo",
                "Command has not been executed");
        }
        if (originalFlag == null) {
            throw new CommandExecutionException(getDescription(), "undo",
                "No captured original flag for undo");
        }
        ExecutionResult<Void> r = orchestrator.updateShapeTextBoxFlag(slideNumber, spid, originalFlag);
        if (!r.isSuccess()) {
            throw new CommandExecutionException(getDescription(), "undo", r.getMessage());
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
