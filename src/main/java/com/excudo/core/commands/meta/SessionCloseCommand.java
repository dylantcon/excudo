package com.excudo.core.commands.meta;

import com.excudo.core.commands.meta.UndoCommand;
import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandDisplay;
import com.excudo.core.commands.CommandExecutionException;
import com.excudo.core.commands.CommandSessionContext;
import com.excudo.core.commands.CommandSessionManager;

/**
 * GoF Command for closing an active session.
 * 
 * This command contains the actual session close logic extracted from AbstractConsoleEngine.
 * Handles session cleanup without circular dependencies.
 * Cannot be undone since it changes system state.
 */
public class SessionCloseCommand implements Command {
    
    private final CommandSessionManager sessionManager;
    private final CommandSessionContext sessionContext;
    private final CommandDisplay display;
    private final String sessionId;
    private boolean executed = false;
    
    
    /**
     * Create a SessionCloseCommand.
     * 
     * @param sessionManager the session manager for closing sessions
     * @param sessionContext the current session context
     * @param display the console display interface
     * @param sessionId the session ID to close
     */
    public SessionCloseCommand(CommandSessionManager sessionManager, CommandSessionContext sessionContext, 
                              CommandDisplay display, String sessionId) {
        if (sessionManager == null) {
            throw new IllegalArgumentException("CommandSessionManager cannot be null");
        }
        if (sessionContext == null) {
            throw new IllegalArgumentException("CommandSessionContext cannot be null");
        }
        if (display == null) {
            throw new IllegalArgumentException("CommandDisplay cannot be null");
        }
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new IllegalArgumentException("Session ID cannot be null or empty");
        }
        this.sessionManager = sessionManager;
        this.sessionContext = sessionContext;
        this.display = display;
        this.sessionId = sessionId.trim();
    }
    
    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute", "Command has already been executed");
        }
        
        try {
            // Close the session 
            boolean success = sessionManager.closeSession(sessionId);
            
            if (success) {
                display.displaySuccess("Session closed: " + sessionId);
                
                // Clear current session if this was the current one
                if (sessionId.equals(sessionContext.getCurrentSessionId())) {
                    sessionContext.clearCurrentSession();
                    display.displayMessage("No active session. Use 'load' or 'session create' to begin.");
                }
            } else {
                display.displayError("Failed to close session: " + sessionId);
            }
            
            executed = true;
            
        } catch (Exception e) {
            throw new CommandExecutionException(getDescription(), "execute", 
                "Failed to close session: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void undo() {
        throw new CommandExecutionException(getDescription(), UndoCommand.NAME, 
            "SessionCloseCommand cannot be undone - it changes system state");
    }
    
    @Override
    public boolean canUndo() {
        return false; // System operation - cannot be undone
    }
    
    @Override
    public boolean isExecuted() {
        return executed;
    }
    
    @Override
    public String getDescription() {
        return "Close session: " + sessionId;
    }
}