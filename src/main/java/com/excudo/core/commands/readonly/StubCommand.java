package com.excudo.core.commands.readonly;

import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandExecutionException;

/**
 * Stub Command for operations that are not yet implemented.
 * 
 * This command provides graceful failure messaging for operations
 * that have been identified but not yet implemented in the system.
 */
public class StubCommand implements Command {
    
    private final String actionType;
    private final String description;
    private boolean executed = false;
    
    /**
     * Create a StubCommand for an unimplemented operation.
     * 
     * @param actionType the type of operation being stubbed
     * @param description human-readable description of the operation
     */
    public StubCommand(String actionType, String description) {
        this.actionType = actionType;
        this.description = description;
    }
    
    /**
     * Execute the stub command (always fails with informative message).
     * 
     * @throws CommandExecutionException always, with informative message
     */
    @Override
    public void execute() {
        throw new CommandExecutionException(
            getDescription(),
            "execute",
            String.format("Operation '%s' is not yet implemented. This is a known limitation that will be addressed in future updates.", actionType)
        );
    }
    
    /**
     * Undo is not supported for stub commands.
     * 
     * @throws CommandExecutionException always
     */
    @Override
    public void undo() {
        throw new CommandExecutionException(
            getDescription(),
            "undo", 
            "Stub commands cannot be undone"
        );
    }
    
    /**
     * Stub commands cannot be undone.
     * 
     * @return false always
     */
    @Override
    public boolean canUndo() {
        return false;
    }
    
    /**
     * Get the description of this stub command.
     * 
     * @return description of the stubbed operation
     */
    @Override
    public String getDescription() {
        return description;
    }
    
    /**
     * Check if this stub command has been executed.
     * Note: Stub commands always fail, so this will remain false.
     * 
     * @return false always (stub commands cannot execute successfully)
     */
    @Override
    public boolean isExecuted() {
        return executed;
    }
    
    /**
     * Get the operation type.
     * 
     * @return the operation type
     */
    public String getActionType() {
        return actionType;
    }
}