package com.excudo.core.commands.mutating.layout;

import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandExecutionException;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.results.ExecutionResult;

/**
 * GoF Command for duplicating a slide layout.
 */
public class DuplicateLayoutCommand implements Command {

    private final String sourceLayoutId;
    private final String newName;
    private final PPTXOrchestrator orchestrator;
    private boolean executed = false;
    private String createdLayoutId = null;

    public DuplicateLayoutCommand(String sourceLayoutId, String newName, PPTXOrchestrator orchestrator) {
        if (orchestrator == null) {
            throw new IllegalArgumentException("PPTXOrchestrator cannot be null");
        }
        if (sourceLayoutId == null || sourceLayoutId.trim().isEmpty()) {
            throw new IllegalArgumentException("Source layout ID cannot be null or empty");
        }
        if (newName == null || newName.trim().isEmpty()) {
            throw new IllegalArgumentException("New name cannot be null or empty");
        }
        this.sourceLayoutId = sourceLayoutId;
        this.newName = newName;
        this.orchestrator = orchestrator;
    }

    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute", "Command has already been executed");
        }

        ExecutionResult<String> result = orchestrator.duplicateLayout(sourceLayoutId, newName);
        if (!result.isSuccess()) {
            throw new CommandExecutionException(getDescription(), "execute",
                "Failed to duplicate layout: " + result.getMessage());
        }

        createdLayoutId = result.getData().orElse(null);
        executed = true;
    }

    @Override
    public void undo() {
        if (!executed || createdLayoutId == null) {
            throw new CommandExecutionException(getDescription(), "undo", "Command has not been executed");
        }

        ExecutionResult<Void> result = orchestrator.deleteLayout(createdLayoutId);
        if (!result.isSuccess()) {
            throw new CommandExecutionException(getDescription(), "undo",
                "Failed to undo layout duplication: " + result.getMessage());
        }

        executed = false;
        createdLayoutId = null;
    }

    @Override
    public boolean canUndo() {
        return executed && createdLayoutId != null;
    }

    @Override
    public String getDescription() {
        return "Duplicate layout " + sourceLayoutId + " as \"" + newName + "\""
            + (createdLayoutId != null ? " -> " + createdLayoutId : "");
    }

    @Override
    public boolean isExecuted() {
        return executed;
    }

    public String getCreatedLayoutId() {
        return createdLayoutId;
    }
}
