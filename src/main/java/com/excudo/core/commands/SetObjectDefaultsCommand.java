package com.excudo.core.commands;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.results.ExecutionResult;

/**
 * GoF Command for populating a:objectDefaults in theme1.xml.
 * Ensures non-placeholder shapes get consistent default styling.
 */
public class SetObjectDefaultsCommand implements Command {

    private final String fontColor;
    private final Integer lineWidth;
    private final String fillColor;
    private final PPTXOrchestrator orchestrator;
    private boolean executed = false;

    public SetObjectDefaultsCommand(String fontColor, Integer lineWidth, String fillColor,
                                    PPTXOrchestrator orchestrator) {
        if (orchestrator == null) {
            throw new IllegalArgumentException("PPTXOrchestrator cannot be null");
        }
        this.fontColor = fontColor != null ? fontColor : "tx1";
        this.lineWidth = lineWidth;
        this.fillColor = fillColor;
        this.orchestrator = orchestrator;
    }

    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute", "Command has already been executed");
        }

        ExecutionResult<Void> result = orchestrator.setObjectDefaults(fontColor, lineWidth);
        if (!result.isSuccess()) {
            throw new CommandExecutionException(getDescription(), "execute", result.getMessage());
        }

        executed = true;
    }

    @Override
    public void undo() {
        throw new CommandExecutionException(getDescription(), "undo",
            "Object defaults undo not yet implemented -- use show-master to verify changes");
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
        return "Set object defaults: fontColor=" + fontColor
            + (lineWidth != null ? " lineWidth=" + lineWidth : "");
    }
}
