package com.excudo.core.commands;

/**
 * Unified interface for console display operations used by all Command implementations.
 * 
 * This replaces the 19+ duplicate ConsoleDisplay interfaces that were scattered
 * across individual command classes, providing a single point of contract
 * for console output operations.
 * 
 * Implementations should handle display formatting, colors, and output routing
 * appropriate for their console environment (TTY, UI, test, etc.).
 */
public interface CommandDisplay {
    
    /**
     * Display a normal informational message to the user.
     * 
     * @param message the message to display
     */
    void displayMessage(String message);
    
    /**
     * Display an error message to the user.
     * Implementations should format this distinctly from normal messages
     * (e.g., different color, prefix, stderr routing).
     * 
     * @param message the error message to display
     */
    void displayError(String message);
    
    /**
     * Display a success message to the user.
     * Implementations should format this distinctly to indicate successful
     * completion of an operation (e.g., green color, success prefix).
     * 
     * @param message the success message to display
     */
    void displaySuccess(String message);
}