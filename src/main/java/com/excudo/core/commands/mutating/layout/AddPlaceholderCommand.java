package com.excudo.core.commands.mutating.layout;

import com.excudo.core.commands.meta.UndoCommand;
import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandExecutionException;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.results.ExecutionResult;

/**
 * GoF Command for adding a placeholder to an existing layout.
 */
public class AddPlaceholderCommand implements Command {

    private final String layoutId;
    private final String type;
    private final int idx;
    private final long x, y, cx, cy;
    private final PPTXOrchestrator orchestrator;
    private boolean executed = false;

    public AddPlaceholderCommand(String layoutId, String type, int idx,
                                 long x, long y, long cx, long cy,
                                 PPTXOrchestrator orchestrator) {
        if (orchestrator == null) {
            throw new IllegalArgumentException("PPTXOrchestrator cannot be null");
        }
        if (layoutId == null || layoutId.trim().isEmpty()) {
            throw new IllegalArgumentException("Layout ID cannot be null or empty");
        }
        this.layoutId = layoutId;
        this.type = type != null ? type : "obj";
        this.idx = idx;
        this.x = x;
        this.y = y;
        this.cx = cx;
        this.cy = cy;
        this.orchestrator = orchestrator;
    }

    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute", "Command has already been executed");
        }

        ExecutionResult<Void> result = orchestrator.addPlaceholder(layoutId, type, idx, x, y, cx, cy);
        if (!result.isSuccess()) {
            throw new CommandExecutionException(getDescription(), "execute",
                "Failed to add placeholder: " + result.getMessage());
        }

        executed = true;
    }

    @Override
    public void undo() {
        if (!executed) {
            throw new CommandExecutionException(getDescription(), UndoCommand.NAME, "Command has not been executed");
        }

        ExecutionResult<Void> result = orchestrator.removePlaceholder(layoutId, idx);
        if (!result.isSuccess()) {
            throw new CommandExecutionException(getDescription(), UndoCommand.NAME,
                "Failed to undo add placeholder: " + result.getMessage());
        }

        executed = false;
    }

    @Override
    public boolean canUndo() {
        return executed;
    }

    @Override
    public String getDescription() {
        return "Add placeholder type=" + type + " idx=" + idx + " to " + layoutId;
    }

    @Override
    public boolean isExecuted() {
        return executed;
    }
}
