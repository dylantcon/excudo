package com.excudo.core.commands.meta;

import com.excudo.core.commands.meta.UndoCommand;
import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandDisplay;
import com.excudo.core.commands.CommandExecutionException;
import com.excudo.core.commands.CommandSessionContext;
import com.excudo.core.commands.CommandSessionManager;
import com.excudo.core.commands.LLMHandler;
import com.excudo.core.commands.SessionResult;

import com.excudo.core.orchestration.PPTXOrchestrator;
import java.io.File;

/**
 * GoF Command for creating a new session.
 * 
 * This command contains the actual session creation logic extracted from AbstractConsoleEngine.
 * Handles both file-based and empty session creation without circular dependencies.
 * Does not support undo since it changes system state fundamentally.
 * 
 * NOTE: All external dependencies (SessionManager, LLMHandler) are provided via interfaces
 * to avoid circular dependencies with the console package.
 */
public class SessionCreateCommand implements Command {
    
    private final CommandSessionManager sessionManager;
    private final CommandSessionContext sessionContext;
    private final CommandDisplay display;
    private final String filename; // null for empty session
    private boolean executed = false;
    
    
    /**
     * Create a SessionCreateCommand for file-based session.
     * 
     * @param sessionManager the session manager for creating sessions
     * @param sessionContext the current session context
     * @param display the console display interface
     * @param filename the presentation file to load (null for empty session)
     */
    public SessionCreateCommand(CommandSessionManager sessionManager, CommandSessionContext sessionContext, 
                               CommandDisplay display, String filename) {
        if (sessionManager == null) {
            throw new IllegalArgumentException("CommandSessionManager cannot be null");
        }
        if (sessionContext == null) {
            throw new IllegalArgumentException("CommandSessionContext cannot be null");
        }
        if (display == null) {
            throw new IllegalArgumentException("CommandDisplay cannot be null");
        }
        this.sessionManager = sessionManager;
        this.sessionContext = sessionContext;
        this.display = display;
        this.filename = filename != null ? filename.trim() : null;
    }
    
    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute", "Command has already been executed");
        }
        
        try {
            SessionResult result;
            
            if (filename == null || filename.isEmpty()) {
                result = sessionManager.createEmptySession();
            } else {
                result = sessionManager.createSession(filename);
            }

            if (result.isSuccess()) {
                sessionContext.setCurrentSession(
                    result.getSessionId(),
                    result.getOrchestrator(),
                    result.getLlmHandler(),
                    result.getCurrentFile()
                );

                if (filename != null && !filename.isEmpty()) {
                    int slideCount = 0;
                    try {
                        var metadata = result.getOrchestrator().getPresentationMetadata();
                        slideCount = metadata.getSlideCount();
                    } catch (Exception ignored) {}
                    display.displaySuccess("Session created: " + new java.io.File(filename).getName() + " (" + slideCount + " slides)");
                } else {
                    display.displaySuccess("Empty session created");
                }
            } else {
                display.displayError("Failed to create session: " + result.getErrorMessage());
            }
            
            executed = true;
            
        } catch (Exception e) {
            throw new CommandExecutionException(getDescription(), "execute", 
                "Failed to create session: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void undo() {
        throw new CommandExecutionException(getDescription(), UndoCommand.NAME, 
            "SessionCreateCommand cannot be undone - it changes system state completely");
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
        return filename != null ? "Create session with file: " + filename : "Create empty session";
    }
}