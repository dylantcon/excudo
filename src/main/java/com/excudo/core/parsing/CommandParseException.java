package com.excudo.core.parsing;

/**
 * Exception thrown when command parsing fails.
 * Provides detailed error messages to guide users.
 */
public class CommandParseException extends Exception {
    
    public CommandParseException(String message) {
        super(message);
    }
    
    public CommandParseException(String message, Throwable cause) {
        super(message, cause);
    }
}