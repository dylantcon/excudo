package com.excudo.core.commands;

import com.excudo.core.commands.meta.UndoCommand;

import java.util.List;
import java.util.ArrayList;
import com.excudo.core.utils.Logger;
import com.excudo.core.utils.ComponentLogger;
import java.util.Collections;

/**
 * GoF Composite Command Pattern Implementation
 * 
 * CompositeCommand allows grouping multiple commands into a single command
 * that can be executed, undone, and redone as an atomic unit. This provides
 * transaction-like behavior for complex operations.
 * 
 * Key features:
 * - All commands execute in order
 * - If any command fails, all previous commands are automatically undone
 * - Undo executes commands in reverse order
 * - Atomic behavior: either all commands succeed or all are rolled back
 */
public class CompositeCommand implements Command {
    
    private final List<Command> commands;
    private final String description;
    private boolean executed = false;
    private int lastExecutedIndex = -1;
    private static final ComponentLogger logger = Logger.getLogger("SPID"); // Track how many commands were executed
    
    /**
     * Create a CompositeCommand with a list of commands.
     * 
     * @param commands the commands to execute as a group
     * @param description description of the composite operation
     */
    public CompositeCommand(List<Command> commands, String description) {
        if (commands == null || commands.isEmpty()) {
            throw new IllegalArgumentException("Commands list cannot be null or empty");
        }
        if (description == null || description.trim().isEmpty()) {
            throw new IllegalArgumentException("Description cannot be null or empty");
        }
        
        this.commands = new ArrayList<>(commands); // Defensive copy
        this.description = description;
    }
    
    /**
     * Execute all commands in sequence.
     * If any command fails, all previously executed commands are undone.
     * 
     * @throws CommandExecutionException if any command fails
     */
    @Override
    public void execute() {
        logger.debug("COMPOSITE EXECUTE START: " + description + " (executed=" + executed + ")");
        
        if (executed) {
            throw new CommandExecutionException(description, "execute", "Command has already been executed");
        }
        
        lastExecutedIndex = -1;
        
        try {
            logger.debug("COMPOSITE EXECUTE: About to execute " + commands.size() + " commands");
            // Execute commands in order
            for (int i = 0; i < commands.size(); i++) {
                Command command = commands.get(i);
                logger.debug("COMPOSITE EXECUTE: Executing command " + (i+1) + "/" + commands.size() + " - " + command.getDescription());
                command.execute();
                logger.debug("COMPOSITE EXECUTE: Command " + (i+1) + " executed successfully - isExecuted=" + command.isExecuted());
                lastExecutedIndex = i;
            }
            
            logger.debug("COMPOSITE EXECUTE: All commands executed, setting executed=true");
            executed = true;
            logger.debug("COMPOSITE EXECUTE SUCCESS: executed=" + executed);
            
        } catch (Exception e) {
            logger.error("COMPOSITE EXECUTE FAILED: " + e.getMessage());
            // Rollback any commands that were executed
            rollbackExecutedCommands();
            
            throw new CommandExecutionException(
                description, 
                "execute", 
                String.format("Command %d failed, rolled back %d commands: %s", 
                    lastExecutedIndex + 1, lastExecutedIndex + 1, e.getMessage()),
                e
            );
        }
    }
    
    /**
     * Undo all commands in reverse order.
     * 
     * @throws CommandExecutionException if any command fails to undo
     */
    @Override
    public void undo() {
        if (!executed) {
            throw new CommandExecutionException(description, UndoCommand.NAME, "Command has not been executed");
        }
        
        // Undo commands in reverse order
        List<String> undoErrors = new ArrayList<>();
        
        for (int i = commands.size() - 1; i >= 0; i--) {
            Command command = commands.get(i);
            
            if (!command.canUndo()) {
                undoErrors.add(String.format("Command %d cannot be undone: %s", i + 1, command.getDescription()));
                continue;
            }
            
            try {
                command.undo();
            } catch (Exception e) {
                undoErrors.add(String.format("Command %d undo failed: %s - %s", i + 1, command.getDescription(), e.getMessage()));
            }
        }
        
        executed = false;
        lastExecutedIndex = -1;
        
        if (!undoErrors.isEmpty()) {
            throw new CommandExecutionException(
                description, 
                UndoCommand.NAME, 
                "Some commands failed to undo: " + String.join("; ", undoErrors)
            );
        }
    }
    
    /**
     * Check if this composite command can be undone.
     * Returns true only if ALL child commands can be undone.
     * 
     * @return true if all commands can be undone, false otherwise
     */
    @Override
    public boolean canUndo() {
        if (!executed) {
            return false;
        }
        
        return commands.stream().allMatch(Command::canUndo);
    }
    
    /**
     * Get the description of this composite command.
     * 
     * @return description of the composite operation
     */
    @Override
    public String getDescription() {
        return description;
    }
    
    /**
     * Check if this composite command has been executed.
     * 
     * @return true if all commands have been executed successfully
     */
    @Override
    public boolean isExecuted() {
        logger.debug("COMPOSITE isExecuted() called: executed=" + executed);
        return executed;
    }
    
    /**
     * Get the number of commands in this composite.
     * 
     * @return number of child commands
     */
    public int getCommandCount() {
        return commands.size();
    }
    
    /**
     * Get a read-only list of the child commands.
     * 
     * @return unmodifiable list of commands
     */
    public List<Command> getCommands() {
        return Collections.unmodifiableList(commands);
    }
    
    /**
     * Get the index of the last successfully executed command.
     * Useful for debugging partial execution failures.
     * 
     * @return index of last executed command, or -1 if none were executed
     */
    public int getLastExecutedIndex() {
        return lastExecutedIndex;
    }
    
    /**
     * Get detailed execution status for each command.
     * 
     * @return list of strings describing the status of each command
     */
    public List<String> getExecutionStatus() {
        List<String> status = new ArrayList<>();
        
        for (int i = 0; i < commands.size(); i++) {
            Command command = commands.get(i);
            String state;
            
            if (!executed) {
                state = i <= lastExecutedIndex ? "EXECUTED (then rolled back)" : "NOT EXECUTED";
            } else {
                state = "EXECUTED";
            }
            
            status.add(String.format("Command %d: %s - %s", i + 1, command.getDescription(), state));
        }
        
        return status;
    }
    
    /**
     * Rollback any commands that were executed before a failure occurred.
     */
    private void rollbackExecutedCommands() {
        // Undo in reverse order, only the commands that were executed
        for (int i = lastExecutedIndex; i >= 0; i--) {
            Command command = commands.get(i);
            
            if (command.canUndo()) {
                try {
                    command.undo();
                } catch (Exception undoException) {
                    // Log the undo failure but continue trying to undo other commands
                    logger.warn("Failed to undo command during rollback: {} - {}",
                                     command.getDescription(), undoException.getMessage());
                }
            }
        }
        
        lastExecutedIndex = -1;
    }
    
    /**
     * Builder for creating CompositeCommands.
     */
    public static class Builder {
        private final List<Command> commands = new ArrayList<>();
        private String description = "Composite Command";
        
        /**
         * Add a command to the composite.
         * 
         * @param command the command to add
         * @return this builder for method chaining
         */
        public Builder addCommand(Command command) {
            if (command != null) {
                commands.add(command);
            }
            return this;
        }
        
        /**
         * Add multiple commands to the composite.
         * 
         * @param commands the commands to add
         * @return this builder for method chaining
         */
        public Builder addCommands(List<Command> commands) {
            if (commands != null) {
                commands.stream().filter(cmd -> cmd != null).forEach(this.commands::add);
            }
            return this;
        }
        
        /**
         * Set the description for the composite command.
         * 
         * @param description description of the composite operation
         * @return this builder for method chaining
         */
        public Builder description(String description) {
            this.description = description;
            return this;
        }
        
        /**
         * Build the CompositeCommand.
         * 
         * @return new CompositeCommand instance
         * @throws IllegalArgumentException if no commands were added
         */
        public CompositeCommand build() {
            return new CompositeCommand(commands, description);
        }
    }
    
    /**
     * Create a new builder for CompositeCommand.
     * 
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }
}