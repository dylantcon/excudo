package com.excudo.core.commands.readonly;

import com.excudo.core.commands.CommandInvoker;
import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandClassRegistry;
import com.excudo.core.commands.CommandContext;
import com.excudo.core.commands.CommandDisplay;
import com.excudo.core.commands.CommandExecutionException;
import com.excudo.core.commands.CommandSessionContext;
import com.excudo.core.parsing.CommandParameters;
import com.excudo.core.parsing.CommandSchema;

/**
 * GoF Command for displaying command history.
 *
 * This command shows the command execution history from the CommandInvoker,
 * providing visibility into undo/redo operations and executed commands.
 *
 * <p>Self-registers via {@link CommandClassRegistry}: canonical name
 * {@code history} derives from the class.
 */
public class HistoryCommand implements Command {

    public static final CommandSchema SCHEMA = CommandSchema.builder()
        .description("Show command history")
        .example("history")
        .build();

    public static final String NAME = CommandClassRegistry.nameOf(HistoryCommand.class);

    public static Command fromParameters(CommandParameters p, CommandContext ctx) {
        return new HistoryCommand(ctx.requireSession(), ctx.requireDisplay());
    }
    
    private final CommandSessionContext sessionContext;
    private final CommandDisplay display;
    private boolean executed = false;
    
    /**
     * Create a HistoryCommand.
     * 
     * @param sessionContext the session context for accessing CommandInvoker
     * @param display the console display interface
     */
    public HistoryCommand(CommandSessionContext sessionContext, CommandDisplay display) {
        if (sessionContext == null) {
            throw new IllegalArgumentException("CommandSessionContext cannot be null");
        }
        if (display == null) {
            throw new IllegalArgumentException("CommandDisplay cannot be null");
        }
        this.sessionContext = sessionContext;
        this.display = display;
    }
    
    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute", "Command has already been executed");
        }
        
        try {
            if (sessionContext.getCurrentSessionId() == null) {
                display.displayError("No active session. Use 'load' or 'session create' first.");
                executed = true;
                return;
            }
            
            CommandInvoker invoker = sessionContext.getCurrentCommandInvoker();
            if (invoker == null) {
                display.displayError("No command invoker available in current session.");
                executed = true;
                return;
            }
            
            // Display undo history (executed commands)
            java.util.List<String> undoHistory = invoker.getUndoHistory();
            if (undoHistory.isEmpty()) {
                display.displayMessage("Command History: (empty)");
            } else {
                display.displayMessage("Command History (most recent first):");
                for (int i = 0; i < undoHistory.size(); i++) {
                    display.displayMessage("  " + (i + 1) + ". " + undoHistory.get(i));
                }
            }

            // Display redo stack
            java.util.List<String> redoHistory = invoker.getRedoHistory();
            display.displayMessage("");
            if (redoHistory.isEmpty()) {
                display.displayMessage("Redo Stack: (empty)");
            } else {
                display.displayMessage("Redo Stack:");
                for (int i = 0; i < redoHistory.size(); i++) {
                    display.displayMessage("  " + (i + 1) + ". " + redoHistory.get(i));
                }
            }
            
            executed = true;
            
        } catch (Exception e) {
            throw new CommandExecutionException(getDescription(), "execute", 
                "Failed to display history: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void undo() {
        throw new CommandExecutionException(getDescription(), "undo", 
            "HistoryCommand is a read-only operation and cannot be undone");
    }
    
    @Override
    public boolean canUndo() {
        return false; // Read-only operation
    }
    
    @Override
    public boolean isExecuted() {
        return executed;
    }
    
    @Override
    public String getDescription() {
        return "Show command history";
    }
}