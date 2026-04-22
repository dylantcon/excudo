package com.excudo.core.commands.meta;

import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandDisplay;
import com.excudo.core.commands.CommandExecutionException;
import com.excudo.core.commands.CommandSessionManager;

/**
 * GoF Command for displaying detailed session information.
 * 
 * This command contains the actual session info logic extracted from AbstractConsoleEngine.
 * Provides comprehensive session details without circular dependencies.
 */
public class SessionInfoCommand implements Command {
    
    private final CommandSessionManager sessionManager;
    private final CommandDisplay display;
    private final String sessionId;
    private boolean executed = false;
    
    
    /**
     * Create a SessionInfoCommand.
     * 
     * @param sessionManager the session manager for retrieving session info
     * @param display the console display interface
     * @param sessionId the session ID to display info for
     */
    public SessionInfoCommand(CommandSessionManager sessionManager, CommandDisplay display, String sessionId) {
        if (sessionManager == null) {
            throw new IllegalArgumentException("CommandSessionManager cannot be null");
        }
        if (display == null) {
            throw new IllegalArgumentException("CommandDisplay cannot be null");
        }
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new IllegalArgumentException("Session ID cannot be null or empty");
        }
        this.sessionManager = sessionManager;
        this.display = display;
        this.sessionId = sessionId.trim();
    }
    
    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute", "Command has already been executed");
        }
        
        try {
            // Get session info and display it
            CommandSessionManager.SessionInfo sessionInfo = sessionManager.getSessionInfo(sessionId);
            if (sessionInfo == null) {
                display.displayError("Session not found: " + sessionId);
            } else {
                display.displayMessage("Session Info: " + sessionId);
                display.displayMessage("  Session ID: " + sessionInfo.getSessionId());
                display.displayMessage("  Filename: " + (sessionInfo.getFilename() != null ? sessionInfo.getFilename() : "<empty>"));
                display.displayMessage("  Active: " + sessionInfo.isActive());
                display.displayMessage("  Slide Count: " + sessionInfo.getSlideCount());
                display.displayMessage("  Title: " + (sessionInfo.getTitle() != null ? sessionInfo.getTitle() : "<none>"));
                display.displayMessage("  Created: " + java.time.Instant.ofEpochMilli(sessionInfo.getCreationTime()));
            }
            executed = true;
            
        } catch (Exception e) {
            throw new CommandExecutionException(getDescription(), "execute", 
                "Failed to get session info: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void undo() {
        // Read-only operation - no undo needed
        executed = false;
    }
    
    @Override
    public boolean canUndo() {
        return true; // Read-only operation
    }
    
    @Override
    public boolean isExecuted() {
        return executed;
    }
    
    @Override
    public String getDescription() {
        return "Get session info: " + sessionId;
    }
}