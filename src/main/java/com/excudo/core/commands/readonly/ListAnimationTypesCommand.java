package com.excudo.core.commands.readonly;

import com.excudo.core.commands.meta.UndoCommand;
import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandClassRegistry;
import com.excudo.core.commands.CommandContext;
import com.excudo.core.parsing.CommandParameters;
import com.excudo.core.parsing.CommandSchema;
import com.excudo.core.commands.CommandDisplay;
import com.excudo.core.commands.CommandExecutionException;

import com.excudo.core.model.AnimationType;
import com.excudo.core.orchestration.PPTXOrchestrator;
import java.util.List;

/**
 * GoF Command for listing all available animation types.
 * 
 * This is a static utility command that displays all animation types
 * with their descriptions. Requires no orchestrator or session state.
 * Does not support undo since it performs no mutations.
 */
public class ListAnimationTypesCommand implements Command {

    public static final CommandSchema SCHEMA = CommandSchema.builder()
        .description("List all available animation types")
        .example("list-animation-types")
        .build();

    public static final String NAME = CommandClassRegistry.nameOf(ListAnimationTypesCommand.class);

    public static Command fromParameters(CommandParameters p, CommandContext ctx) {
        return new ListAnimationTypesCommand(ctx.requireDisplay());
    }

    
    private final CommandDisplay display;
    private boolean executed = false;
    
    
    /**
     * Create a ListAnimationTypesCommand.
     * 
     * @param display the console display interface
     */
    public ListAnimationTypesCommand(CommandDisplay display) {
        if (display == null) {
            throw new IllegalArgumentException("CommandDisplay cannot be null");
        }
        this.display = display;
    }
    
    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute", "Command has already been executed");
        }
        
        try {
            // Get animation types directly without orchestrator dependency for sessionless operation
            List<AnimationType> animationTypes = java.util.Arrays.asList(AnimationType.values());
            
            display.displayMessage("");
            display.displayMessage("Available Animation Types:");
            display.displayMessage("");
            
            // Group by category (matching original implementation exactly)
            display.displayMessage("ENTRANCE ANIMATIONS:");
            for (AnimationType type : animationTypes) {
                if (type.canBeEntrance() && !type.isEmphasis()) {
                    display.displayMessage(String.format("  %-20s - %s", 
                        type.getUserFriendlyName(), 
                        type.getDescription()));
                }
            }
            
            display.displayMessage("");
            display.displayMessage("EMPHASIS ANIMATIONS:");
            for (AnimationType type : animationTypes) {
                if (type.isEmphasis()) {
                    display.displayMessage(String.format("  %-20s - %s", 
                        type.getUserFriendlyName(), 
                        type.getDescription()));
                }
            }
            
            display.displayMessage("");
            display.displayMessage("MOTION PATH ANIMATIONS:");
            for (AnimationType type : animationTypes) {
                if (type.isMotionPath()) {
                    display.displayMessage(String.format("  %-20s - %s", 
                        type.getUserFriendlyName(), 
                        type.getDescription()));
                }
            }
            
            display.displayMessage("");
            display.displayMessage("Note: Most entrance animations can also be used as exit animations");
            
            executed = true;
            
        } catch (Exception e) {
            throw new CommandExecutionException(getDescription(), "execute", 
                "Failed to list animation types: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void undo() {
        throw new CommandExecutionException(getDescription(), UndoCommand.NAME, 
            "Cannot undo a read-only list operation");
    }
    
    @Override
    public boolean canUndo() {
        return false; // Read-only operation cannot be undone
    }
    
    @Override
    public boolean isExecuted() {
        return executed;
    }
    
    @Override
    public String getDescription() {
        return "List Animation Types";
    }
}