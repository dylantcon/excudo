package com.excudo.core.commands.mutating.slide;

import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandExecutionException;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.results.ExecutionResult;
import java.util.List;

/**
 * GoF Command for dissolving a p:grpSp group shape back into its constituent children.
 *
 * Undo is implemented by re-grouping the returned child SPIDs. The new group will
 * receive a fresh SPID but is functionally equivalent to the original.
 */
public class UngroupCommand implements Command {

    private final int slideNumber;
    private final int spid;
    private final PPTXOrchestrator orchestrator;
    private boolean executed = false;
    private List<Integer> childSpids = null;

    public UngroupCommand(int slideNumber, int spid, PPTXOrchestrator orchestrator) {
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

        ExecutionResult<List<Integer>> result = orchestrator.ungroupShape(slideNumber, spid);
        if (!result.isSuccess()) {
            throw new CommandExecutionException(getDescription(), "execute",
                "Failed to ungroup shape: " + result.getMessage());
        }

        childSpids = result.getData().orElse(null);
        executed = true;
    }

    @Override
    public void undo() {
        if (!executed) {
            throw new CommandExecutionException(getDescription(), "undo", "Command has not been executed");
        }
        if (!canUndo()) {
            throw new CommandExecutionException(getDescription(), "undo", "No child SPIDs available for undo");
        }

        ExecutionResult<Integer> result = orchestrator.groupShapes(slideNumber, childSpids);
        if (!result.isSuccess()) {
            throw new CommandExecutionException(getDescription(), "undo",
                "Failed to re-group shapes on undo: " + result.getMessage());
        }

        executed = false;
        childSpids = null;
    }

    @Override
    public boolean canUndo() {
        return executed && childSpids != null && childSpids.size() >= 2;
    }

    @Override
    public String getDescription() {
        return "Ungroup shape SPID " + spid + " on slide " + slideNumber;
    }

    @Override
    public boolean isExecuted() {
        return executed;
    }

    public int getSlideNumber() { return slideNumber; }
    public int getSpid() { return spid; }
    public List<Integer> getChildSpids() { return childSpids; }
}
