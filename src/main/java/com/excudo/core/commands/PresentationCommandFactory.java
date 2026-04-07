package com.excudo.core.commands;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.parsing.ParsedCommand;
import java.util.Set;
import java.util.HashSet;

/**
 * Factory for creating presentation file and session management commands.
 * 
 * This factory handles commands that work with presentation files and
 * session management. These commands primarily interact with 
 * PresentationOrchestrationManager as the receiver.
 * 
 * Handled commands:
 * - load/open: Load presentation files
 * - save: Save current presentation
 * - session: Session management (create, list, info, close, switch)
 * 
 * Note: These are console-only commands as the LLM doesn't manage
 * files or sessions directly.
 */
public class PresentationCommandFactory extends AbstractCommandFactory {
    
    private static final Set<String> HANDLED_COMMANDS = new HashSet<>();
    
    static {
        // File operations
        HANDLED_COMMANDS.add("load");
        HANDLED_COMMANDS.add("open");
        HANDLED_COMMANDS.add("save");
        
        // Create from scratch
        HANDLED_COMMANDS.add("new");

        // Session management
        HANDLED_COMMANDS.add("session");
    }
    
    /**
     * Create a PresentationCommandFactory.
     * 
     * @param orchestrator the PPTX orchestrator
     */
    public PresentationCommandFactory(PPTXOrchestrator orchestrator) {
        super(orchestrator);
    }
    
    @Override
    public boolean handlesCommand(String commandName) {
        return HANDLED_COMMANDS.contains(commandName);
    }
    
    @Override
    public Command createFromParsedCommand(ParsedCommand parsedCommand, Object displayAdapter) {
        String commandName = parsedCommand.getCommandName();
        
        switch (commandName) {
            case "load":
            case "open":
                return createLoad(parsedCommand, displayAdapter);
                
            case "save":
                return createSave(parsedCommand, displayAdapter);

            case "new":
                return createNew(parsedCommand, displayAdapter);

            case "session":
                return createSession(parsedCommand, displayAdapter);
                
            default:
                return null;
        }
    }
    
    // ========== FILE OPERATIONS ==========
    
    /**
     * Create a load command.
     */
    private Command createLoad(ParsedCommand parsedCommand, Object displayAdapter) {
        String filename = parsedCommand.getString("filename");
        return new LoadCommand(
            (CommandSessionManager) displayAdapter, 
            (CommandSessionContext) displayAdapter, 
            (CommandDisplay) displayAdapter, 
            filename);
    }
    
    /**
     * Create a save command.
     */
    private Command createSave(ParsedCommand parsedCommand, Object displayAdapter) {
        String saveFilename = parsedCommand.getString("filename");
        return new SaveCommand(
            (CommandSessionContext) displayAdapter, 
            (CommandDisplay) displayAdapter, 
            saveFilename);
    }
    
    /**
     * Create a new-presentation command.
     */
    private Command createNew(ParsedCommand parsedCommand, Object displayAdapter) {
        String themeId = parsedCommand.getString("themeId");
        if (themeId == null) themeId = "minimal";
        return new NewPresentationCommand(
            (CommandSessionManager) displayAdapter,
            (CommandSessionContext) displayAdapter,
            (CommandDisplay) displayAdapter,
            themeId);
    }

    // ========== SESSION MANAGEMENT ==========
    
    /**
     * Create a session management command.
     */
    private Command createSession(ParsedCommand parsedCommand, Object displayAdapter) {
        String sessionSubcommand = parsedCommand.getString("operation");
        if (sessionSubcommand == null) {
            throw new IllegalArgumentException("Session command requires subcommand: create, list, info, close, switch");
        }

        switch (sessionSubcommand) {
            case "create":
                return createSessionCreate(parsedCommand, displayAdapter);
                
            case "list":
                return createSessionList(displayAdapter);
                
            case "info":
                return createSessionInfo(parsedCommand, displayAdapter);
                
            case "close":
                return createSessionClose(parsedCommand, displayAdapter);
                
            case "switch":
                return createSessionSwitch(parsedCommand, displayAdapter);
                
            default:
                throw new IllegalArgumentException("Unknown session subcommand: " + sessionSubcommand);
        }
    }
    
    /**
     * Create a session create command.
     */
    private Command createSessionCreate(ParsedCommand parsedCommand, Object displayAdapter) {
        String sessionFilename = parsedCommand.getString("args");
        return new SessionCreateCommand(
            (CommandSessionManager) displayAdapter,
            (CommandSessionContext) displayAdapter,
            (CommandDisplay) displayAdapter,
            sessionFilename);
    }
    
    /**
     * Create a session list command.
     */
    private Command createSessionList(Object displayAdapter) {
        return new SessionListCommand(
            (CommandSessionManager) displayAdapter,
            (CommandDisplay) displayAdapter);
    }
    
    /**
     * Create a session info command.
     */
    private Command createSessionInfo(ParsedCommand parsedCommand, Object displayAdapter) {
        String infoSessionId = parsedCommand.getString("args");
        return new SessionInfoCommand(
            (CommandSessionManager) displayAdapter,
            (CommandDisplay) displayAdapter,
            infoSessionId);
    }

    /**
     * Create a session close command.
     */
    private Command createSessionClose(ParsedCommand parsedCommand, Object displayAdapter) {
        String closeSessionId = parsedCommand.getString("args");
        return new SessionCloseCommand(
            (CommandSessionManager) displayAdapter,
            (CommandSessionContext) displayAdapter,
            (CommandDisplay) displayAdapter,
            closeSessionId);
    }

    /**
     * Create a session switch command.
     */
    private Command createSessionSwitch(ParsedCommand parsedCommand, Object displayAdapter) {
        String switchSessionId = parsedCommand.getString("args");
        return new SessionSwitchCommand(
            (CommandSessionManager) displayAdapter,
            (CommandSessionContext) displayAdapter,
            (CommandDisplay) displayAdapter,
            switchSessionId);
    }
}