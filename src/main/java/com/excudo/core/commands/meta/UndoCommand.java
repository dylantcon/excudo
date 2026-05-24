package com.excudo.core.commands.meta;

import com.excudo.core.commands.CommandInvoker;
import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandClassRegistry;
import com.excudo.core.commands.CommandContext;
import com.excudo.core.commands.CommandDisplay;
import com.excudo.core.commands.CommandExecutionException;
import com.excudo.core.commands.CommandSessionContext;
import com.excudo.core.parsing.CommandSchema;
import com.excudo.core.parsing.CommandParameters;

/**
 * GoF Command for undoing the last executed command.
 *
 * This is a system operation that delegates to CommandInvoker.undo().
 * Since undo is the negation of execute, it cannot itself be undone
 * (use RedoCommand instead). This prevents philosophical paradoxes
 * and infinite recursion.
 *
 * <p>Self-registers via {@link com.excudo.core.commands.CommandClassRegistry}:
 * the canonical name {@code undo} derives from the class name. Needs a REPL
 * session context + display, pulled from {@link CommandContext}.
 */
public class UndoCommand implements Command {

    public static final CommandSchema SCHEMA = CommandSchema.builder()
        .description("Undo the last command")
        .example("undo")
        .build();

    public static final String NAME = CommandClassRegistry.nameOf(UndoCommand.class);

    public static Command fromParameters(CommandParameters p, CommandContext ctx) {
        return new UndoCommand(ctx.requireSession(), ctx.requireDisplay());
    }

    private final CommandSessionContext sessionContext;
    private final CommandDisplay display;
    private boolean executed = false;

    
    /**
     * Create an UndoCommand.
     * 
     * @param sessionContext the session context to get CommandInvoker
     * @param display the console display interface
     */
    public UndoCommand(CommandSessionContext sessionContext, CommandDisplay display) {
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
            
            boolean success = invoker.undo();
            if (success) {
                display.displayMessage("Undo successful");
            } else {
                display.displayMessage("Nothing to undo");
            }
            executed = true;
            
        } catch (CommandExecutionException e) {
            // CommandInvoker throws CommandExecutionException with details
            display.displayError("Undo failed: " + e.getMessage());
            throw e;
        } catch (Exception e) {
            throw new CommandExecutionException(getDescription(), "execute", 
                "Undo operation failed: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void undo() {
        throw new CommandExecutionException(getDescription(), "undo", 
            "UndoCommand cannot be undone - use RedoCommand instead");
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
        return "Undo last command";
    }
}
