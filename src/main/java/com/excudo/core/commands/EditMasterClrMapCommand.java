package com.excudo.core.commands;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.results.ExecutionResult;
import java.util.Map;

/**
 * GoF Command for editing the slide master's color mapping (p:clrMap).
 * Used to switch between dark and light theme modes.
 */
public class EditMasterClrMapCommand implements Command {

    private final Map<String, String> mappings;
    private final PPTXOrchestrator orchestrator;
    private boolean executed = false;

    public EditMasterClrMapCommand(Map<String, String> mappings, PPTXOrchestrator orchestrator) {
        if (orchestrator == null) {
            throw new IllegalArgumentException("PPTXOrchestrator cannot be null");
        }
        if (mappings == null || mappings.isEmpty()) {
            throw new IllegalArgumentException("At least one color mapping is required");
        }
        this.mappings = mappings;
        this.orchestrator = orchestrator;
    }

    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute", "Command has already been executed");
        }

        for (Map.Entry<String, String> entry : mappings.entrySet()) {
            ExecutionResult<Void> result = orchestrator.setClrMap(entry.getKey(), entry.getValue());
            if (!result.isSuccess()) {
                throw new CommandExecutionException(getDescription(), "execute",
                    "Failed to set " + entry.getKey() + "=" + entry.getValue() + ": " + result.getMessage());
            }
        }

        executed = true;
    }

    @Override
    public void undo() {
        throw new CommandExecutionException(getDescription(), "undo",
            "ClrMap undo not yet implemented -- use show-master to verify changes");
    }

    @Override
    public boolean canUndo() {
        return false;
    }

    @Override
    public boolean isExecuted() {
        return executed;
    }

    @Override
    public String getDescription() {
        return "Edit master color map: " + mappings;
    }
}
