package com.excudo.core.commands.meta;

import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandClassRegistry;
import com.excudo.core.commands.CommandContext;
import com.excudo.core.commands.CommandDisplay;
import com.excudo.core.commands.CommandExecutionException;
import com.excudo.core.commands.CommandSessionManager;
import com.excudo.core.parsing.CommandParameters;
import com.excudo.core.parsing.CommandSchema;

import java.io.File;
import java.util.Map;

/**
 * GoF Command for listing all active sessions.
 *
 * This command contains the actual session listing logic extracted from AbstractConsoleEngine.
 * Provides formatted display of session information without circular dependencies.
 * All external dependencies are provided via interfaces to avoid build order issues.
 *
 * <p>Self-registers via {@link CommandClassRegistry}: canonical name
 * {@code session-list} derives from the class.
 */
public class SessionListCommand implements Command {

    public static final CommandSchema SCHEMA = CommandSchema.builder()
        .description("List all active REPL sessions")
        .example("session-list")
        .build();

    public static final String NAME = CommandClassRegistry.nameOf(SessionListCommand.class);

    public static Command fromParameters(CommandParameters p, CommandContext ctx) {
        return new SessionListCommand(ctx.requireSessionManager(), ctx.requireDisplay());
    }
    
    private final CommandSessionManager sessionManager;
    private final CommandDisplay display;
    private boolean executed = false;
    
    
    /**
     * Create a SessionListCommand.
     * 
     * @param sessionManager the session manager for listing sessions
     * @param display the console display interface
     */
    public SessionListCommand(CommandSessionManager sessionManager, CommandDisplay display) {
        if (sessionManager == null) {
            throw new IllegalArgumentException("CommandSessionManager cannot be null");
        }
        if (display == null) {
            throw new IllegalArgumentException("CommandDisplay cannot be null");
        }
        this.sessionManager = sessionManager;
        this.display = display;
    }
    
    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute", "Command has already been executed");
        }
        
        try {
            Map<String, CommandSessionManager.SessionInfo> sessions = sessionManager.getAllSessions();
            
            if (sessions.isEmpty()) {
                display.displayMessage("No active sessions.");
            } else {
                display.displayMessage("Active sessions:");
                for (Map.Entry<String, CommandSessionManager.SessionInfo> entry : sessions.entrySet()) {
                    String sessionId = entry.getKey();
                    CommandSessionManager.SessionInfo info = entry.getValue();
                    
                    String filename = info.getFilename() != null ? info.getFilename() : "<empty>";
                    display.displayMessage("  " + sessionId + " - " + filename);
                }
            }
            
            executed = true;
            
        } catch (Exception e) {
            throw new CommandExecutionException(getDescription(), "execute", 
                "Failed to list sessions: " + e.getMessage(), e);
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
        return "List active sessions";
    }
}