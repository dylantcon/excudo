package com.excudo.core.commands.meta;

import com.excudo.core.commands.meta.UndoCommand;
import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandClassRegistry;
import com.excudo.core.commands.CommandContext;
import com.excudo.core.commands.CommandDisplay;
import com.excudo.core.commands.CommandExecutionException;
import com.excudo.core.commands.CommandSessionContext;
import com.excudo.core.commands.CommandSessionManager;
import com.excudo.core.commands.SessionResult;
import com.excudo.core.parsing.CommandParameters;
import com.excudo.core.parsing.CommandSchema;
import com.excudo.core.parsing.Parameter;

import java.io.File;

/**
 * GoF Command for switching between active sessions.
 *
 * This command contains the actual session switch logic extracted from AbstractConsoleEngine.
 * Handles session context switching without circular dependencies.
 * Cannot be undone since it changes system state.
 *
 * <p>Self-registers via {@link CommandClassRegistry}: canonical name
 * {@code session-switch} derives from the class.
 */
public class SessionSwitchCommand implements Command {

    static final Parameter<String> SESSION_ID = Parameter.ofString("sessionId")
        .description("Target session ID to switch to").required().build();

    public static final CommandSchema SCHEMA = CommandSchema.builder()
        .description("Switch the active session to a different one")
        .parameter(SESSION_ID)
        .example("session-switch abc-123")
        .build();

    public static final String NAME = CommandClassRegistry.nameOf(SessionSwitchCommand.class);

    public static Command fromParameters(CommandParameters p, CommandContext ctx) {
        return new SessionSwitchCommand(ctx.requireSessionManager(), ctx.requireSession(),
            ctx.requireDisplay(), p.get(SESSION_ID));
    }
    
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
        throw new CommandExecutionException(getDescription(), UndoCommand.NAME, 
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