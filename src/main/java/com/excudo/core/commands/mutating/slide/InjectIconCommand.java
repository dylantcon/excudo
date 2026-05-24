package com.excudo.core.commands.mutating.slide;

import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandClassRegistry;
import com.excudo.core.commands.CommandContext;
import com.excudo.core.commands.CommandExecutionException;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.parsing.CommandParameters;
import com.excudo.core.parsing.CommandSchema;
import com.excudo.core.parsing.Parameter;
import com.excudo.core.results.ExecutionResult;
import com.excudo.core.utils.ComponentLogger;
import com.excudo.core.utils.Logger;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

/**
 * GoF Command for icon injection operations (SmartContent icon injection).
 * 
 * This command allows injecting icons into slides using SmartContent
 * with undo capability by removing the added icon shapes.
 * 
 * Based on the pattern established by EnhancedContentCommand but specifically
 * focused on icon injection operations.
 */
public class InjectIconCommand implements Command {

    static final Parameter<Integer> SLIDE = Parameter.ofInt("slide")
        .slideNumber().description("Slide number").required().build();
    static final Parameter<String> KEYWORD = Parameter.ofString("keyword")
        .description("Content keyword").required().build();
    static final Parameter<Double> X = Parameter.ofDouble("x")
        .description("X position in EMUs").required(false).build();
    static final Parameter<Double> Y = Parameter.ofDouble("y")
        .description("Y position in EMUs").required(false).build();
    static final Parameter<Double> WIDTH = Parameter.ofDouble("width")
        .description("Width in EMUs").required(false).build();
    static final Parameter<Double> HEIGHT = Parameter.ofDouble("height")
        .description("Height in EMUs").required(false).build();
    static final Parameter<String> POSITION = Parameter.ofString("position")
        .description("Placement hint (e.g. 'top-right', 'bottom-left')")
        .required(false).build();

    public static final CommandSchema SCHEMA = CommandSchema.builder()
        .description("Inject enhanced content (icons, images)")
        .parameter(SLIDE).parameter(KEYWORD)
        .parameter(X).parameter(Y).parameter(WIDTH).parameter(HEIGHT)
        .parameter(POSITION)
        .example("inject-icon 1 \"business icon\"")
        .build();

    public static final String NAME = CommandClassRegistry.nameOf(InjectIconCommand.class);

    public static Command fromParameters(CommandParameters p, CommandContext ctx) {
        Map<String, Object> placement = new HashMap<>();
        p.opt(X).ifPresent(v -> placement.put("x", v));
        p.opt(Y).ifPresent(v -> placement.put("y", v));
        p.opt(WIDTH).ifPresent(v -> placement.put("width", v));
        p.opt(HEIGHT).ifPresent(v -> placement.put("height", v));
        p.opt(POSITION).ifPresent(v -> placement.put("position", v));
        return new InjectIconCommand(p.get(SLIDE), p.get(KEYWORD), placement, ctx.orchestrator());
    }

    private static final ComponentLogger logger = Logger.llm();
    
    private final int slideNumber;
    private final String iconQuery;
    private final Map<String, Object> placementOptions;
    private final PPTXOrchestrator orchestrator;
    private boolean executed = false;
    private List<Integer> addedSpids = new ArrayList<>();
    
    /**
     * Create an InjectIconCommand.
     * 
     * @param slideNumber the slide number to inject icon into
     * @param iconQuery the query/keyword for icon search
     * @param placementOptions optional placement options (position, size, etc.)
     * @param orchestrator the PPTX orchestrator for execution
     */
    public InjectIconCommand(int slideNumber, String iconQuery, 
                            Map<String, Object> placementOptions, PPTXOrchestrator orchestrator) {
        if (orchestrator == null) {
            throw new IllegalArgumentException("PPTXOrchestrator cannot be null");
        }
        if (iconQuery == null || iconQuery.trim().isEmpty()) {
            throw new IllegalArgumentException("Icon query cannot be null or empty");
        }
        
        this.slideNumber = slideNumber;
        this.iconQuery = iconQuery;
        this.placementOptions = placementOptions;
        this.orchestrator = orchestrator;
    }
    
    /**
     * Execute the icon injection command.
     * 
     * @throws CommandExecutionException if the operation fails
     */
    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute", "Command has already been executed");
        }
        
        try {
            logger.debug("Starting icon injection for slide " + slideNumber + ", query: " + iconQuery);
            
            // Call the orchestrator's icon injection method
            // Note: This method may need to be implemented in the orchestrator
            // For now, we'll use the enhanced content injection as a fallback
            ExecutionResult<List<Integer>> result = orchestrator.injectEnhancedContent(
                slideNumber, iconQuery, "icon", placementOptions);
            
            logger.debug("Icon injection returned - success: " + result.isSuccess() + ", message: " + result.getMessage());
            
            if (result.isSuccess()) {
                // Store the added SPIDs for undo purposes
                addedSpids = result.getData().orElse(new ArrayList<>());
                executed = true;
                logger.debug("Successfully executed icon injection, added SPIDs: " + addedSpids);
            } else {
                logger.error("Icon injection failed: " + result.getMessage());
                throw new CommandExecutionException(
                    getDescription(), 
                    "execute", 
                    "Failed to inject icon: " + result.getMessage()
                );
            }
            
        } catch (CommandExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new CommandExecutionException(
                getDescription(), 
                "execute", 
                "Failed to inject icon: " + e.getMessage(),
                e
            );
        }
    }
    
    /**
     * Undo the icon injection by removing added icon shapes.
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
            // Remove all shapes that were added by this command
            for (Integer spid : addedSpids) {
                ExecutionResult<Void> removeResult = orchestrator.removeShape(slideNumber, spid);
                if (!removeResult.isSuccess()) {
                    // Log warning but continue with other shapes
                    logger.warn("Failed to remove icon shape " + spid + " during undo: " + removeResult.getMessage());
                }
            }
            executed = false;
            addedSpids.clear();
            
        } catch (Exception e) {
            throw new CommandExecutionException(
                getDescription(), 
                "undo", 
                "Failed to undo icon injection: " + e.getMessage(),
                e
            );
        }
    }
    
    /**
     * Check if this command can be undone.
     * Icon injection can be undone by removing the added shapes.
     * 
     * @return true if the command can be undone
     */
    @Override
    public boolean canUndo() {
        return executed && !addedSpids.isEmpty();
    }
    
    /**
     * Get the description of this command.
     * 
     * @return description of the icon injection operation
     */
    @Override
    public String getDescription() {
        StringBuilder desc = new StringBuilder();
        desc.append("Inject icon on slide ").append(slideNumber);
        desc.append(" with query '").append(iconQuery).append("'");
        
        if (placementOptions != null && !placementOptions.isEmpty()) {
            desc.append(" with custom placement");
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
     * Get the slide number.
     * 
     * @return the slide number
     */
    public int getSlideNumber() {
        return slideNumber;
    }
    
    /**
     * Get the icon query.
     * 
     * @return the icon query
     */
    public String getIconQuery() {
        return iconQuery;
    }
    
    /**
     * Get the placement options.
     * 
     * @return the placement options
     */
    public Map<String, Object> getPlacementOptions() {
        return placementOptions;
    }
    
    /**
     * Get the added SPIDs (available after execution).
     * 
     * @return list of added SPIDs, or empty list if not executed
     */
    public List<Integer> getAddedSpids() {
        return new ArrayList<>(addedSpids);
    }
}
