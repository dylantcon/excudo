package com.excudo.core.commands;

/**
 * Exception thrown when a Command cannot be executed or undone.
 * 
 * This runtime exception is used to signal failures in command execution
 * or undo operations, maintaining the clean interface of the Command pattern
 * while providing detailed error information.
 */
public class CommandExecutionException extends RuntimeException {
    
    private final String commandDescription;
    private final String operation; // "execute" or "undo"
    
    /**
     * Create a new CommandExecutionException.
     * 
     * @param commandDescription description of the command that failed
     * @param operation the operation that failed ("execute" or "undo")
     * @param message detailed error message
     */
    public CommandExecutionException(String commandDescription, String operation, String message) {
        super(String.format("Command '%s' failed during %s: %s", commandDescription, operation, message));
        this.commandDescription = commandDescription;
        this.operation = operation;
    }
    
    /**
     * Create a new CommandExecutionException with a cause.
     * 
     * @param commandDescription description of the command that failed
     * @param operation the operation that failed ("execute" or "undo")
     * @param message detailed error message
     * @param cause the underlying exception that caused the failure
     */
    public CommandExecutionException(String commandDescription, String operation, String message, Throwable cause) {
        super(String.format("Command '%s' failed during %s: %s", commandDescription, operation, message), cause);
        this.commandDescription = commandDescription;
        this.operation = operation;
    }
    
    /**
     * Get the description of the command that failed.
     * 
     * @return command description
     */
    public String getCommandDescription() {
        return commandDescription;
    }
    
    /**
     * Get the operation that failed.
     * 
     * @return "execute" or "undo"
     */
    public String getOperation() {
        return operation;
    }
}