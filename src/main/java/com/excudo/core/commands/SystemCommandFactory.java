package com.excudo.core.commands;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.parsing.ParsedCommand;
import java.util.Set;
import java.util.HashSet;

/**
 * Factory for creating system/meta-operation commands.
 * 
 * This factory handles commands that work with the Command pattern infrastructure
 * itself (undo/redo/help/history) rather than with presentation content. These
 * commands primarily interact with CommandInvoker and CommandSessionContext
 * rather than OrchestrationManagers.
 * 
 * Handled commands:
 * - help: Display help information
 * - undo: Undo last command
 * - redo: Redo last undone command
 * - history: Show command history
 * 
 * Note: These are console-only commands as the LLM doesn't manage
 * command history or help display.
 */
public class SystemCommandFactory extends AbstractCommandFactory {
    
    private static final Set<String> HANDLED_COMMANDS = new HashSet<>();
    
    static {
        // Help command
        HANDLED_COMMANDS.add("help");
        HANDLED_COMMANDS.add("?");
        
        // Command pattern operations
        HANDLED_COMMANDS.add("undo");
        HANDLED_COMMANDS.add("redo");
        HANDLED_COMMANDS.add("history");
    }
    
    /**
     * Create a SystemCommandFactory.
     * 
     * @param orchestrator the PPTX orchestrator
     */
    public SystemCommandFactory(PPTXOrchestrator orchestrator) {
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
            case "help":
            case "?":
                return createHelp(parsedCommand, displayAdapter);
                
            case "undo":
                return createUndo(displayAdapter);
                
            case "redo":
                return createRedo(displayAdapter);
                
            case "history":
                return createHistory(displayAdapter);
                
            default:
                return null;
        }
    }
    
    // ========== HELP COMMAND ==========
    
    /**
     * Create a help command.
     */
    private Command createHelp(ParsedCommand parsedCommand, Object displayAdapter) {
        CommandDisplay display = (CommandDisplay) displayAdapter;
        String topic = parsedCommand.getString("topic");
        
        if (topic != null) {
            return new HelpCommand(display, topic);
        } else {
            return new HelpCommand(display);
        }
    }
    
    // ========== UNDO/REDO COMMANDS ==========
    
    /**
     * Create an undo command.
     */
    private Command createUndo(Object displayAdapter) {
        return new UndoCommand(
            (CommandSessionContext) displayAdapter,
            (CommandDisplay) displayAdapter);
    }
    
    /**
     * Create a redo command.
     */
    private Command createRedo(Object displayAdapter) {
        return new RedoCommand(
            (CommandSessionContext) displayAdapter,
            (CommandDisplay) displayAdapter);
    }
    
    // ========== HISTORY COMMAND ==========
    
    /**
     * Create a history command.
     */
    private Command createHistory(Object displayAdapter) {
        return new HistoryCommand(
            (CommandSessionContext) displayAdapter,
            (CommandDisplay) displayAdapter);
    }
    
    // ========== PUBLIC FACTORY METHODS (for direct use) ==========
    
    /**
     * Create a help command for general help.
     * 
     * @param display the console display interface
     * @return HelpCommand
     */
    public HelpCommand createHelp(CommandDisplay display) {
        return new HelpCommand(display);
    }
    
    /**
     * Create a help command for specific topic.
     * 
     * @param display the console display interface
     * @param topic the help topic
     * @return HelpCommand
     */
    public HelpCommand createHelp(CommandDisplay display, String topic) {
        return new HelpCommand(display, topic);
    }
    
    /**
     * Create an undo command.
     * 
     * @param sessionContext the session context for undo operations
     * @param display the console display interface
     * @return UndoCommand
     */
    public UndoCommand createUndo(CommandSessionContext sessionContext, CommandDisplay display) {
        return new UndoCommand(sessionContext, display);
    }
    
    /**
     * Create a redo command.
     * 
     * @param sessionContext the session context for redo operations
     * @param display the console display interface
     * @return RedoCommand
     */
    public RedoCommand createRedo(CommandSessionContext sessionContext, CommandDisplay display) {
        return new RedoCommand(sessionContext, display);
    }
    
    /**
     * Create a history command.
     * 
     * @param sessionContext the session context for history
     * @param display the console display interface
     * @return HistoryCommand
     */
    public HistoryCommand createHistory(CommandSessionContext sessionContext, CommandDisplay display) {
        return new HistoryCommand(sessionContext, display);
    }
}