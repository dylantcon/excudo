package com.excudo.core.commands;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.results.ExecutionResult;

/**
 * GoF Command for editing the slide master background.
 * Updates p:bg/p:bgRef in slideMaster1.xml.
 */
public class EditMasterBgCommand implements Command {

    private final int fillIndex;
    private final String schemeColor;
    private final PPTXOrchestrator orchestrator;
    private boolean executed = false;

    public EditMasterBgCommand(int fillIndex, String schemeColor, PPTXOrchestrator orchestrator) {
        if (orchestrator == null) {
            throw new IllegalArgumentException("PPTXOrchestrator cannot be null");
        }
        this.fillIndex = fillIndex;
        this.schemeColor = schemeColor != null ? schemeColor : "bg1";
        this.orchestrator = orchestrator;
    }

    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute", "Command has already been executed");
        }

        ExecutionResult<Void> result = orchestrator.setMasterBackground(fillIndex, schemeColor);
        if (!result.isSuccess()) {
            throw new CommandExecutionException(getDescription(), "execute", result.getMessage());
        }

        executed = true;
    }

    @Override
    public void undo() {
        throw new CommandExecutionException(getDescription(), "undo",
            "Background undo not yet implemented -- use show-master to verify changes");
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
        return "Set master background idx=" + fillIndex + " color=" + schemeColor;
    }
}
