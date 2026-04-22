package com.excudo.core.commands.mutating.slide;

import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandExecutionException;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.results.ExecutionResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GoF Command for copying the visual style (spPr) from one shape to one or more target shapes.
 *
 * Before applying the style, each target's current DOM element is captured as a deep clone.
 * Undo removes each restyled shape and restores the original captured element.
 */
public class CopyStyleCommand implements Command {

    private final int slideNumber;
    private final int sourceSpid;
    private final List<Integer> targetSpids;
    private final PPTXOrchestrator orchestrator;
    private boolean executed = false;
    private final Map<Integer, org.w3c.dom.Element> capturedElements = new LinkedHashMap<>();

    public CopyStyleCommand(int slideNumber, int sourceSpid, List<Integer> targetSpids,
                            PPTXOrchestrator orchestrator) {
        if (orchestrator == null) {
            throw new IllegalArgumentException("PPTXOrchestrator cannot be null");
        }
        if (slideNumber <= 0) {
            throw new IllegalArgumentException("Slide number must be positive");
        }
        if (sourceSpid <= 0) {
            throw new IllegalArgumentException("Source SPID must be positive");
        }
        if (targetSpids == null || targetSpids.isEmpty()) {
            throw new IllegalArgumentException("Target SPIDs cannot be null or empty");
        }
        this.slideNumber = slideNumber;
        this.sourceSpid = sourceSpid;
        this.targetSpids = targetSpids;
        this.orchestrator = orchestrator;
    }

    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute", "Command has already been executed");
        }

        // Capture each target's current element before applying style changes
        capturedElements.clear();
        for (int targetSpid : targetSpids) {
            ExecutionResult<org.w3c.dom.Element> captureResult =
                orchestrator.captureShapeElement(slideNumber, targetSpid);
            if (!captureResult.isSuccess()) {
                throw new CommandExecutionException(getDescription(), "execute",
                    "Failed to capture target shape SPID " + targetSpid + " for undo: " + captureResult.getMessage());
            }
            org.w3c.dom.Element captured = captureResult.getData().orElse(null);
            if (captured == null) {
                throw new CommandExecutionException(getDescription(), "execute",
                    "Captured element for SPID " + targetSpid + " was null");
            }
            capturedElements.put(targetSpid, captured);
        }

        ExecutionResult<Void> result = orchestrator.copyShapeStyle(slideNumber, sourceSpid, targetSpids);
        if (!result.isSuccess()) {
            capturedElements.clear();
            throw new CommandExecutionException(getDescription(), "execute",
                "Failed to copy style: " + result.getMessage());
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

        // Restore in reverse order: remove the restyled shape then restore the original snapshot
        List<Integer> reverseOrder = new ArrayList<>(capturedElements.keySet());
        for (int i = reverseOrder.size() - 1; i >= 0; i--) {
            int targetSpid = reverseOrder.get(i);
            org.w3c.dom.Element original = capturedElements.get(targetSpid);

            ExecutionResult<Void> removeResult = orchestrator.removeShape(slideNumber, targetSpid);
            if (!removeResult.isSuccess()) {
                throw new CommandExecutionException(getDescription(), "undo",
                    "Failed to remove restyled shape SPID " + targetSpid + ": " + removeResult.getMessage());
            }

            ExecutionResult<Void> restoreResult = orchestrator.restoreShape(slideNumber, original);
            if (!restoreResult.isSuccess()) {
                throw new CommandExecutionException(getDescription(), "undo",
                    "Failed to restore original shape SPID " + targetSpid + ": " + restoreResult.getMessage());
            }
        }

        executed = false;
        capturedElements.clear();
    }

    @Override
    public boolean canUndo() {
        return executed && !capturedElements.isEmpty();
    }

    @Override
    public String getDescription() {
        return "Copy style from shape SPID " + sourceSpid + " to " + targetSpids + " on slide " + slideNumber;
    }

    @Override
    public boolean isExecuted() {
        return executed;
    }

    public int getSlideNumber() { return slideNumber; }
    public int getSourceSpid() { return sourceSpid; }
    public List<Integer> getTargetSpids() { return targetSpids; }
}
