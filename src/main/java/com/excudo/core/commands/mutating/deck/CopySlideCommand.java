package com.excudo.core.commands.mutating.deck;

import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandContext;
import com.excudo.core.commands.CommandExecutionException;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.parsing.CommandParameters;
import com.excudo.core.parsing.CommandSchema;
import com.excudo.core.parsing.Parameter;
import com.excudo.core.results.ExecutionResult;
import com.excudo.core.results.SlideExecutionResult;
import java.util.Map;
import java.util.UUID;

/**
 * GoF Command implementation for copying slides.
 *
 * This command encapsulates all the business logic for copying
 * a slide from one position to another with optional modifications.
 *
 * <p>Self-registers via {@link com.excudo.core.commands.CommandClassRegistry}:
 * the canonical name {@code copy-slide} derives from the class name.
 */
public class CopySlideCommand implements Command {

    static final Parameter<Integer> SLIDE = Parameter.ofInt("slide")
        .slideNumber().description("Source slide number").llmName("sourceSlide").required().build();
    static final Parameter<Integer> POSITION = Parameter.ofInt("position")
        .description("Target position for the copy").llmName("targetPosition").required().build();
    static final Parameter<String> TITLE = Parameter.ofString("title")
        .description("New title for the copied slide").llmName("newTitle").required(false).build();

    public static final CommandSchema SCHEMA = CommandSchema.builder()
        .description("Copy a slide to a new position")
        .llmEnabled(true)
        .parameter(SLIDE).parameter(POSITION).parameter(TITLE)
        .example("copy-slide 1 3")
        .example("copy-slide 2 5 \"Updated Title\"")
        .build();

    public static Command fromParameters(CommandParameters p, CommandContext ctx) {
        return new CopySlideCommand(p.get(SLIDE), p.get(POSITION),
            p.opt(TITLE).orElse(null), null, true, ctx.orchestrator());
    }
    
    private final String actionId;
    private final int sourceSlide;
    private final int targetPosition;
    private final String newTitle;
    private final Map<String, Object> modifications;
    private final boolean preserveAnimations;
    private final PPTXOrchestrator orchestrator;
    private boolean executed = false;
    private int createdSlideNumber = -1;
    
    /**
     * Create a CopySlideCommand.
     * 
     * @param sourceSlide the source slide number to copy
     * @param targetPosition the position where the copy should be inserted
     * @param newTitle optional new title for the copied slide
     * @param modifications optional modifications to apply to the copy
     * @param preserveAnimations whether to preserve animations
     * @param orchestrator the PPTX orchestrator for execution
     */
    public CopySlideCommand(int sourceSlide, int targetPosition, String newTitle, 
                           Map<String, Object> modifications, boolean preserveAnimations,
                           PPTXOrchestrator orchestrator) {
        if (orchestrator == null) {
            throw new IllegalArgumentException("PPTXOrchestrator cannot be null");
        }
        
        this.actionId = "slide-copy-" + UUID.randomUUID().toString();
        this.sourceSlide = sourceSlide;
        this.targetPosition = targetPosition;
        this.newTitle = newTitle;
        this.modifications = modifications != null ? Map.copyOf(modifications) : Map.of();
        this.preserveAnimations = preserveAnimations;
        this.orchestrator = orchestrator;
    }
    
    /**
     * Execute the slide copy command.
     * 
     * @throws CommandExecutionException if the operation fails
     */
    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute", "Command has already been executed");
        }
        
        try {
            SlideExecutionResult result = orchestrator.copySlide(
                sourceSlide, 
                targetPosition,
                newTitle
            );
            
            if (!result.isSuccess()) {
                throw new CommandExecutionException(
                    getDescription(), 
                    "execute", 
                    result.getMessage()
                );
            }
            
            // Track the created slide number for undo purposes
            createdSlideNumber = targetPosition;
            executed = true;
            
        } catch (Exception e) {
            throw new CommandExecutionException(
                getDescription(), 
                "execute", 
                "Failed to copy slide: " + e.getMessage(),
                e
            );
        }
    }
    
    /**
     * Undo the slide copy command by deleting the copied slide.
     * 
     * @throws CommandExecutionException if the undo operation fails
     */
    @Override
    public void undo() {
        if (!executed) {
            throw new CommandExecutionException(getDescription(), "undo", "Command has not been executed");
        }
        
        if (!canUndo()) {
            throw new CommandExecutionException(getDescription(), "undo", "Command cannot be undone");
        }
        
        try {
            // Undo by deleting the copied slide
            SlideExecutionResult result = orchestrator.deleteSlide(createdSlideNumber);
            
            if (!result.isSuccess()) {
                throw new CommandExecutionException(
                    getDescription(), 
                    "undo", 
                    "Failed to delete copied slide: " + result.getMessage()
                );
            }
            
            executed = false;
            createdSlideNumber = -1;
            
        } catch (Exception e) {
            throw new CommandExecutionException(
                getDescription(), 
                "undo", 
                "Failed to undo slide copy: " + e.getMessage(),
                e
            );
        }
    }
    
    /**
     * Check if this command can be undone.
     * Slide copy can be undone by deleting the copied slide.
     * 
     * @return true if the command can be undone
     */
    @Override
    public boolean canUndo() {
        return executed && createdSlideNumber > 0;
    }
    
    /**
     * Get the description of this command.
     * 
     * @return human-readable description of the copy operation
     */
    @Override
    public String getDescription() {
        StringBuilder desc = new StringBuilder();
        desc.append("Copy slide ").append(sourceSlide).append(" to position ").append(targetPosition);
        
        if (newTitle != null && !newTitle.trim().isEmpty()) {
            desc.append(" with new title '").append(newTitle).append("'");
        }
        
        if (!modifications.isEmpty()) {
            desc.append(" with ").append(modifications.size()).append(" modification(s)");
        }
        
        if (!preserveAnimations) {
            desc.append(" (excluding animations)");
        }
        
        return desc.toString();
    }
    
    /**
     * Check if this command has been executed.
     * 
     * @return true if execute() has been called successfully
     */
    @Override
    public boolean isExecuted() {
        return executed;
    }
    
    /**
     * Get the operation ID.
     * 
     * @return the unique operation identifier
     */
    public String getOperationId() {
        return actionId;
    }
    
    /**
     * Get the source slide number.
     * 
     * @return the source slide number
     */
    public int getSourceSlide() {
        return sourceSlide;
    }
    
    /**
     * Get the target position.
     * 
     * @return the target position
     */
    public int getTargetPosition() {
        return targetPosition;
    }
    
    /**
     * Get the new title for the copied slide.
     * 
     * @return the new title, or null if none specified
     */
    public String getNewTitle() {
        return newTitle;
    }
    
    /**
     * Get the modifications to apply.
     * 
     * @return the modifications map
     */
    public Map<String, Object> getModifications() {
        return modifications;
    }
    
    /**
     * Check if animations are preserved.
     * 
     * @return true if animations are preserved
     */
    public boolean isPreserveAnimations() {
        return preserveAnimations;
    }
    
    /**
     * Get the slide number that was created (available after execution).
     * 
     * @return the created slide number, or -1 if not executed
     */
    public int getCreatedSlideNumber() {
        return createdSlideNumber;
    }
}