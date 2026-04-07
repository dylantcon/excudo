package com.excudo.core.commands;

/**
 * GoF Command Pattern Interface
 * 
 * This interface defines the standard Command pattern from the Gang of Four
 * Design Patterns book. It provides execute/undo capabilities with proper
 * encapsulation of requests as objects.
 * 
 * Key principles:
 * - Encapsulates a request as an object
 * - Allows parameterization of clients with different requests
 * - Allows queuing of requests, logging, and undo operations
 * - Supports macro commands (composite commands)
 */
public interface Command {
    
    /**
     * Execute the command.
     * This method should perform the actual work of the command.
     * 
     * @throws CommandExecutionException if the command cannot be executed
     */
    void execute();
    
    /**
     * Undo the command.
     * This method should reverse the effects of execute().
     * Should only be called after execute() has been successfully called.
     * 
     * @throws CommandExecutionException if the command cannot be undone
     */
    void undo();
    
    /**
     * Check if this command can be undone.
     * 
     * @return true if undo() can be called safely, false otherwise
     */
    boolean canUndo();
    
    /**
     * Get a description of what this command does.
     * Useful for logging, debugging, and user interfaces.
     * 
     * @return human-readable description of the command
     */
    String getDescription();
    
    /**
     * Check if this command has been executed.
     * 
     * @return true if execute() has been called, false otherwise
     */
    boolean isExecuted();
}