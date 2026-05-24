package com.excudo.core.commands.mutating.master;

import com.excudo.core.commands.meta.UndoCommand;
import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandExecutionException;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.results.ExecutionResult;
import java.util.Map;

/**
 * GoF Command for editing text style levels on the slide master.
 * Modifies p:txStyles (titleStyle/bodyStyle/otherStyle) in slideMaster1.xml.
 */
public class EditMasterStyleCommand implements Command {

    private final String target;
    private final int level;
    private final Map<String, Object> updates;
    private final PPTXOrchestrator orchestrator;
    private boolean executed = false;

    public EditMasterStyleCommand(String target, int level, Map<String, Object> updates,
                                  PPTXOrchestrator orchestrator) {
        if (orchestrator == null) {
            throw new IllegalArgumentException("PPTXOrchestrator cannot be null");
        }
        if (target == null || target.isEmpty()) {
            throw new IllegalArgumentException("Target must be title, body, or other");
        }
        if (level < 1 || level > 9) {
            throw new IllegalArgumentException("Level must be 1-9, got: " + level);
        }
        this.target = target;
        this.level = level;
        this.updates = updates;
        this.orchestrator = orchestrator;
    }

    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute", "Command has already been executed");
        }

        ExecutionResult<Void> result = orchestrator.editMasterStyle(target, level, updates);
        if (!result.isSuccess()) {
            throw new CommandExecutionException(getDescription(), "execute", result.getMessage());
        }

        executed = true;
    }

    @Override
    public void undo() {
        throw new CommandExecutionException(getDescription(), UndoCommand.NAME,
            "Master style undo not yet implemented -- use show-master to verify changes");
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
        return "Edit master " + target + " style level " + level;
    }
}
