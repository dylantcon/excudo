package com.excudo.core.commands;

import com.excudo.core.commands.meta.UndoCommand;

import java.util.Stack;
import java.util.List;
import java.util.ArrayList;
import com.excudo.core.utils.Logger;
import com.excudo.core.utils.ComponentLogger;

/**
 * GoF Command Pattern Invoker
 * 
 * The CommandInvoker is responsible for:
 * - Executing commands
 * - Maintaining command history for undo/redo operations
 * - Providing clean separation between command sources and command execution
 * 
 * This is a pure implementation of the GoF Command pattern invoker.
 */
public class CommandInvoker {
    
    private final Stack<Command> executedCommands = new Stack<>();
    private final Stack<Command> undoneCommands = new Stack<>();
    private final int maxHistorySize;
    private int savePointSize = 0;
    private static final ComponentLogger logger = Logger.getLogger("SPID");
    
    /**
     * Create a CommandInvoker with default history size.
     */
    public CommandInvoker() {
        this(100); // Default to 100 commands in history
    }
    
    /**
     * Create a CommandInvoker with specified history size.
     * 
     * @param maxHistorySize maximum number of commands to keep in history
     */
    public CommandInvoker(int maxHistorySize) {
        this.maxHistorySize = maxHistorySize;
    }
    
    /**
     * Execute a command.
     * 
     * The command will be executed and added to the undo history.
     * Any previously undone commands will be cleared from redo history.
     * 
     * @param command the command to execute
     * @throws CommandExecutionException if the command fails to execute
     */
    public void executeCommand(Command command) {
        if (command == null) {
            throw new IllegalArgumentException("Command cannot be null");
        }
        
        logger.debug("INVOKER EXECUTE START: " + command.getDescription() + " (class=" + command.getClass().getSimpleName() + ")");
        
        try {
            logger.debug("INVOKER EXECUTE: About to call command.execute()");
            command.execute();
            logger.debug("INVOKER EXECUTE: command.execute() completed, checking isExecuted()");
            logger.debug("INVOKER EXECUTE: command.isExecuted()=" + command.isExecuted());
            
            // Only add to executed history if the command supports undo
            if (command.canUndo()) {
                executedCommands.push(command);
                logger.debug("INVOKER EXECUTE: Command supports undo, added to history");
                
                // Clear redo history only when we add a new undoable command
                // This breaks the redo chain since we've diverged from the previous path
                undoneCommands.clear();
            } else {
                logger.debug("INVOKER EXECUTE: Command does not support undo, not added to history");
            }
            
            // Maintain history size limit
            maintainHistoryLimit();
            
            logger.debug("INVOKER EXECUTE SUCCESS: Command added to history");
            
        } catch (Exception e) {
            logger.error("INVOKER EXECUTE FAILED: " + e.getMessage());
            throw new CommandExecutionException(
                command.getDescription(), 
                "execute", 
                e.getMessage(), 
                e
            );
        }
    }
    
    /**
     * Undo the last executed command.
     * 
     * @return true if a command was undone, false if no commands to undo
     * @throws CommandExecutionException if the undo operation fails
     */
    public boolean undo() {
        if (executedCommands.isEmpty()) {
            return false;
        }
        
        Command command = executedCommands.pop();
        
        if (!command.canUndo()) {
            // Put it back since we can't undo it
            executedCommands.push(command);
            throw new CommandExecutionException(
                command.getDescription(), 
                UndoCommand.NAME, 
                "Command does not support undo operation"
            );
        }
        
        try {
            command.undo();
            undoneCommands.push(command);
            return true;
            
        } catch (Exception e) {
            // Put it back in executed stack since undo failed
            executedCommands.push(command);
            throw new CommandExecutionException(
                command.getDescription(), 
                UndoCommand.NAME, 
                e.getMessage(), 
                e
            );
        }
    }
    
    /**
     * Redo the last undone command.
     * 
     * @return true if a command was redone, false if no commands to redo
     * @throws CommandExecutionException if the redo operation fails
     */
    public boolean redo() {
        if (undoneCommands.isEmpty()) {
            return false;
        }
        
        Command command = undoneCommands.pop();
        
        try {
            command.execute();
            executedCommands.push(command);
            return true;
            
        } catch (Exception e) {
            // Put it back in undone stack since redo failed
            undoneCommands.push(command);
            throw new CommandExecutionException(
                command.getDescription(), 
                "execute", 
                "Redo failed: " + e.getMessage(), 
                e
            );
        }
    }
    
    /**
     * Check if there are commands that can be undone.
     * 
     * @return true if undo() would succeed, false otherwise
     */
    public boolean canUndo() {
        return !executedCommands.isEmpty() && 
               executedCommands.peek().canUndo();
    }
    
    /**
     * Check if there are commands that can be redone.
     * 
     * @return true if redo() would succeed, false otherwise
     */
    public boolean canRedo() {
        return !undoneCommands.isEmpty();
    }
    
    /**
     * Get the description of the next command that would be undone.
     * 
     * @return description of the command, or null if no commands to undo
     */
    public String getUndoDescription() {
        if (executedCommands.isEmpty()) {
            return null;
        }
        return executedCommands.peek().getDescription();
    }
    
    /**
     * Get the description of the next command that would be redone.
     * 
     * @return description of the command, or null if no commands to redo
     */
    public String getRedoDescription() {
        if (undoneCommands.isEmpty()) {
            return null;
        }
        return undoneCommands.peek().getDescription();
    }
    
    /**
     * Get the current size of the command history.
     * 
     * @return number of executed commands in history
     */
    public int getHistorySize() {
        return executedCommands.size();
    }
    
    /**
     * Get the current size of the redo history.
     * 
     * @return number of undone commands available for redo
     */
    public int getRedoHistorySize() {
        return undoneCommands.size();
    }
    
    /**
     * Mark the current history position as a save point.
     * Called after a successful save operation.
     */
    public void markSavePoint() {
        savePointSize = executedCommands.size();
    }

    /**
     * Check if there are unsaved changes since the last save point.
     *
     * @return true if the history has diverged from the save point
     */
    public boolean hasUnsavedChanges() {
        return executedCommands.size() != savePointSize;
    }

    /**
     * Clear all command history.
     * This will clear both undo and redo histories.
     */
    public void clearHistory() {
        executedCommands.clear();
        undoneCommands.clear();
    }
    
    /**
     * Get a list of command descriptions in the undo history.
     * Most recent commands appear first.
     * 
     * @return list of command descriptions
     */
    public List<String> getUndoHistory() {
        List<String> history = new ArrayList<>();
        // Iterate from top of stack (most recent) to bottom
        for (int i = executedCommands.size() - 1; i >= 0; i--) {
            history.add(executedCommands.get(i).getDescription());
        }
        return history;
    }
    
    /**
     * Get a list of command descriptions in the redo history.
     * Most recently undone commands appear first.
     * 
     * @return list of command descriptions
     */
    public List<String> getRedoHistory() {
        List<String> history = new ArrayList<>();
        // Iterate from top of stack (most recently undone) to bottom
        for (int i = undoneCommands.size() - 1; i >= 0; i--) {
            history.add(undoneCommands.get(i).getDescription());
        }
        return history;
    }
    
    /**
     * Maintain the history size limit by removing oldest commands.
     */
    private void maintainHistoryLimit() {
        while (executedCommands.size() > maxHistorySize) {
            // Remove the oldest command (bottom of stack)
            executedCommands.remove(0);
        }
    }
}