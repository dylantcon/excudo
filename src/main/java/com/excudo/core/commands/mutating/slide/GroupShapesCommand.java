package com.excudo.core.commands.mutating.slide;

import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandExecutionException;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.results.ExecutionResult;
import java.util.List;

/**
 * GoF Command for grouping multiple shapes into a single p:grpSp element.
 *
 * On execute, calls orchestrator.groupShapes and stores the returned group SPID
 * so that undo can dissolve the group by calling orchestrator.ungroupShape.
 */
public class GroupShapesCommand implements Command {

    private final int slideNumber;
    private final List<Integer> spids;
    private final PPTXOrchestrator orchestrator;
    private boolean executed = false;
    private Integer groupSpid = null;

    public GroupShapesCommand(int slideNumber, List<Integer> spids, PPTXOrchestrator orchestrator) {
        if (orchestrator == null) {
            throw new IllegalArgumentException("PPTXOrchestrator cannot be null");
        }
        if (slideNumber <= 0) {
            throw new IllegalArgumentException("Slide number must be positive");
        }
        if (spids == null || spids.size() < 2) {
            throw new IllegalArgumentException("At least two SPIDs must be provided to group");
        }
        this.slideNumber = slideNumber;
        this.spids = spids;
        this.orchestrator = orchestrator;
    }

    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute", "Command has already been executed");
        }

        ExecutionResult<Integer> result = orchestrator.groupShapes(slideNumber, spids);
        if (!result.isSuccess()) {
            throw new CommandExecutionException(getDescription(), "execute",
                "Failed to group shapes: " + result.getMessage());
        }

        groupSpid = result.getData().orElse(null);
        if (groupSpid == null) {
            throw new CommandExecutionException(getDescription(), "execute",
                "Group operation succeeded but no group SPID was returned");
        }

        executed = true;
    }

    @Override
    public void undo() {
        if (!executed) {
            throw new CommandExecutionException(getDescription(), "undo", "Command has not been executed");
        }
        if (!canUndo()) {
            throw new CommandExecutionException(getDescription(), "undo", "No group SPID available for undo");
        }

        ExecutionResult<List<Integer>> result = orchestrator.ungroupShape(slideNumber, groupSpid);
        if (!result.isSuccess()) {
            throw new CommandExecutionException(getDescription(), "undo",
                "Failed to ungroup shapes: " + result.getMessage());
        }

        executed = false;
        groupSpid = null;
    }

    @Override
    public boolean canUndo() {
        return executed && groupSpid != null;
    }

    @Override
    public String getDescription() {
        return "Group shapes " + spids + " on slide " + slideNumber;
    }

    @Override
    public boolean isExecuted() {
        return executed;
    }

    public int getSlideNumber() { return slideNumber; }
    public List<Integer> getSpids() { return spids; }
    public Integer getGroupSpid() { return groupSpid; }
}
