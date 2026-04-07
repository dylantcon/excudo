package com.excudo.core.commands;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.results.ExecutionResult;

/**
 * GoF Command for removing shapes from slides with full undo support.
 *
 * Before removal, the Command captures a deep clone of the shape's DOM element
 * via the orchestrator's captureShapeElement API. On undo, the captured element
 * is restored back into the slide's spTree. This keeps undo state entirely
 * within the Command, as the pattern requires.
 */
public class RemoveShapeCommand implements Command {

    private final int slideNumber;
    private final int spid;
    private final PPTXOrchestrator orchestrator;
    private boolean executed = false;
    private org.w3c.dom.Element capturedElement = null;

    public RemoveShapeCommand(int slideNumber, int spid, PPTXOrchestrator orchestrator) {
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
        this.orchestrator = orchestrator;
    }

    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute", "Command has already been executed");
        }

        // Snapshot the shape element before destroying it
        ExecutionResult<org.w3c.dom.Element> captureResult = orchestrator.captureShapeElement(slideNumber, spid);
        if (!captureResult.isSuccess()) {
            throw new CommandExecutionException(getDescription(), "execute",
                "Failed to capture shape for undo: " + captureResult.getMessage());
        }
        capturedElement = captureResult.getData().orElse(null);

        // Now remove
        ExecutionResult<Void> removeResult = orchestrator.removeShape(slideNumber, spid);
        if (!removeResult.isSuccess()) {
            capturedElement = null;
            throw new CommandExecutionException(getDescription(), "execute",
                "Failed to remove shape: " + removeResult.getMessage());
        }

        executed = true;
    }

    @Override
    public void undo() {
        if (!executed) {
            throw new CommandExecutionException(getDescription(), "undo", "Command has not been executed");
        }
        if (!canUndo()) {
            throw new CommandExecutionException(getDescription(), "undo", "No captured state available for undo");
        }

        ExecutionResult<Void> restoreResult = orchestrator.restoreShape(slideNumber, capturedElement);
        if (!restoreResult.isSuccess()) {
            throw new CommandExecutionException(getDescription(), "undo",
                "Failed to restore shape: " + restoreResult.getMessage());
        }

        executed = false;
    }

    @Override
    public boolean canUndo() {
        return executed && capturedElement != null;
    }

    @Override
    public String getDescription() {
        return "Remove shape SPID " + spid + " from slide " + slideNumber;
    }

    @Override
    public boolean isExecuted() {
        return executed;
    }

    public int getSlideNumber() { return slideNumber; }
    public int getSpid() { return spid; }
}
