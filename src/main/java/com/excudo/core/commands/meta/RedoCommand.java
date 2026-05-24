package com.excudo.core.commands.meta;

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
 * GoF Command for redoing the last undone command.
 *
 * This is a system operation that delegates to CommandInvoker.redo().
 * Since redo is the negation of undo, it cannot itself be undone
 * (use UndoCommand instead). This prevents philosophical paradoxes
 * and infinite recursion.
 *
 * <p>Self-registers via {@link CommandClassRegistry}: canonical name
 * {@code redo} derives from the class.
 */
public class RedoCommand implements Command {

    public static final CommandSchema SCHEMA = CommandSchema.builder()
        .description("Redo the last undone command")
        .example("redo")
        .build();

    public static final String NAME = CommandClassRegistry.nameOf(RedoCommand.class);

    public static Command fromParameters(CommandParameters p, CommandContext ctx) {
        return new RedoCommand(ctx.requireSession(), ctx.requireDisplay());
    }
    
    private final CommandSessionContext sessionContext;
    private final CommandDisplay display;
    private boolean executed = false;
    
    
    /**
     * Create a RedoCommand.
     * 
     * @param sessionContext the session context to get CommandInvoker
     * @param display the console display interface
     */
    public RedoCommand(CommandSessionContext sessionContext, CommandDisplay display) {
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
            
            boolean success = invoker.redo();
            if (success) {
                display.displayMessage("Redo successful");
            } else {
                display.displayMessage("Nothing to redo");
            }
            executed = true;
            
        } catch (CommandExecutionException e) {
            // CommandInvoker throws CommandExecutionException with details
            display.displayError("Redo failed: " + e.getMessage());
            throw e;
        } catch (Exception e) {
            throw new CommandExecutionException(getDescription(), "execute", 
                "Redo operation failed: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void undo() {
        throw new CommandExecutionException(getDescription(), "undo", 
            "RedoCommand cannot be undone - use UndoCommand instead");
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
        return "Redo last undone command";
    }
}