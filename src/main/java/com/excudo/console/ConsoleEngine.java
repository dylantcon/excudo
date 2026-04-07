package com.excudo.console;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Scanner;

/**
 * Universal console engine interface for both TTY and UI console implementations.
 * Provides unified command processing with configurable input/output streams.
 */
public interface ConsoleEngine {
    
    /**
     * Execute a command with the configured input/output streams
     */
    void executeCommand(String command);
    
    /**
     * Set the input stream for interactive commands
     */
    void setInputStream(InputStream inputStream);
    
    /**
     * Set the output stream for command results
     */
    void setOutputStream(PrintStream outputStream);
    
    /**
     * Set the error stream for error messages
     */
    void setErrorStream(PrintStream errorStream);
    
    /**
     * Get list of available commands
     */
    List<String> getAvailableCommands();
    
    /**
     * Get help text for a specific command
     */
    String getCommandHelp(String command);
    
    /**
     * Initialize the engine with an orchestrator
     */
    void initialize(com.excudo.core.orchestration.PPTXOrchestrator orchestrator);
    
    /**
     * Check if a presentation is loaded
     */
    boolean isPresentationLoaded();
    
    /**
     * Get current presentation status
     */
    String getPresentationStatus();
    
    /**
     * Set the shared scanner to prevent multiple Scanner instances competing for the same InputStream
     * CRITICAL: This fixes race conditions in console input handling
     */
    void setSharedScanner(Scanner scanner);

    /**
     * Check if the current session has unsaved changes.
     *
     * @return true if there are modifications since the last save
     */
    boolean hasUnsavedChanges();

    /**
     * Clean up all resources: close active sessions, delete temp directories.
     * Must be called before the console process exits.
     */
    void shutdown();
}