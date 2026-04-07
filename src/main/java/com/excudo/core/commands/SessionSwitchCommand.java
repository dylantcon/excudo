package com.excudo.core.commands;

import java.io.File;

/**
 * GoF Command for switching between active sessions.
 * 
 * This command contains the actual session switch logic extracted from AbstractConsoleEngine.
 * Handles session context switching without circular dependencies.
 * Cannot be undone since it changes system state.
 */
public class SessionSwitchCommand implements Command {
    
    private final CommandSessionManager sessionManager;
    private final CommandSessionContext sessionContext;
    private final CommandDisplay display;
    private final String targetSessionId;
    private boolean executed = false;
    
    
    /**
     * Create a SessionSwitchCommand.
     * 
     * @param sessionManager the session manager for retrieving sessions
     * @param sessionContext the current session context
     * @param display the console display interface
     * @param targetSessionId the session ID to switch to
     */
    public SessionSwitchCommand(CommandSessionManager sessionManager, CommandSessionContext sessionContext, 
                               CommandDisplay display, String targetSessionId) {
        if (sessionManager == null) {
            throw new IllegalArgumentException("CommandSessionManager cannot be null");
        }
        if (sessionContext == null) {
            throw new IllegalArgumentException("CommandSessionContext cannot be null");
        }
        if (display == null) {
            throw new IllegalArgumentException("CommandDisplay cannot be null");
        }
        if (targetSessionId == null || targetSessionId.trim().isEmpty()) {
            throw new IllegalArgumentException("Target session ID cannot be null or empty");
        }
        this.sessionManager = sessionManager;
        this.sessionContext = sessionContext;
        this.display = display;
        this.targetSessionId = targetSessionId.trim();
    }
    
    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute", "Command has already been executed");
        }
        
        try {
            // Check if already in target session
            if (targetSessionId.equals(sessionContext.getCurrentSessionId())) {
                display.displayMessage("Already in session: " + targetSessionId);
                executed = true;
                return;
            }
            
            // Delegate to the session manager which handles orchestrator/llmHandler wiring
            SessionResult result = sessionManager.switchToSession(targetSessionId);

            if (!result.isSuccess()) {
                display.displayError(result.getErrorMessage());
            } else {
                // Update the session context with the switched session's state
                sessionContext.setCurrentSession(
                    result.getSessionId(),
                    result.getOrchestrator(),
                    result.getLlmHandler(),
                    result.getCurrentFile()
                );
            }
            
            executed = true;
            
        } catch (Exception e) {
            throw new CommandExecutionException(getDescription(), "execute", 
                "Failed to switch session: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void undo() {
        throw new CommandExecutionException(getDescription(), "undo", 
            "SessionSwitchCommand cannot be undone - it changes system state completely");
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
        return "Switch to session: " + targetSessionId;
    }
}