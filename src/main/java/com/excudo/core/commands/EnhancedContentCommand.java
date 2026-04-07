package com.excudo.core.commands;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.results.ExecutionResult;
import com.excudo.core.utils.ComponentLogger;
import com.excudo.core.utils.Logger;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

/**
 * GoF Command for enhanced content operations (SmartContent injection).
 * 
 * This command allows injecting smart content like icons, images, or other
 * enhancements to slides with undo capability.
 */
public class EnhancedContentCommand implements Command {
    
    private static final ComponentLogger logger = Logger.llm();
    
    private final int slideNumber;
    private final String iconKeyword;
    private final String templateStyle;
    private final Map<String, Object> geometry;
    private final PPTXOrchestrator orchestrator;
    private boolean executed = false;
    private List<Integer> addedSpids = new ArrayList<>();
    
    /**
     * Create an EnhancedContentCommand.
     * 
     * @param slideNumber the slide number to enhance
     * @param iconKeyword the keyword for icon search
     * @param templateStyle the template style to apply
     * @param geometry the geometry parameters for placement
     * @param orchestrator the PPTX orchestrator for execution
     */
    public EnhancedContentCommand(int slideNumber, String iconKeyword, String templateStyle, 
                                 Map<String, Object> geometry, PPTXOrchestrator orchestrator) {
        if (orchestrator == null) {
            throw new IllegalArgumentException("PPTXOrchestrator cannot be null");
        }
        if (iconKeyword == null || iconKeyword.trim().isEmpty()) {
            throw new IllegalArgumentException("Icon keyword cannot be null or empty");
        }
        
        this.slideNumber = slideNumber;
        this.iconKeyword = iconKeyword;
        this.templateStyle = templateStyle;
        this.geometry = geometry;
        this.orchestrator = orchestrator;
    }
    
    /**
     * Execute the enhanced content command.
     * 
     * @throws CommandExecutionException if the operation fails
     */
    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute", "Command has already been executed");
        }
        
        try {
            logger.debug("Starting enhanced content injection for slide " + slideNumber + ", keyword: " + iconKeyword);
            // Call the orchestrator's enhanced content injection method
            ExecutionResult<List<Integer>> result = orchestrator.injectEnhancedContent(
                slideNumber, iconKeyword, templateStyle, geometry);
            
            logger.debug("injectEnhancedContent returned - success: " + result.isSuccess() + ", message: " + result.getMessage());
            
            if (result.isSuccess()) {
                // Store the added SPIDs for undo purposes
                addedSpids = result.getData().orElse(new ArrayList<>());
                executed = true;
                logger.debug("Successfully executed, added SPIDs: " + addedSpids);
            } else {
                logger.error("Enhanced content injection failed: " + result.getMessage());
                throw new CommandExecutionException(
                    getDescription(), 
                    "execute", 
                    "Failed to inject enhanced content: " + result.getMessage()
                );
            }
            
        } catch (CommandExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new CommandExecutionException(
                getDescription(), 
                "execute", 
                "Failed to inject enhanced content: " + e.getMessage(),
                e
            );
        }
    }
    
    /**
     * Undo the enhanced content injection by removing added content.
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
                    logger.warn("Failed to remove shape {} during undo: {}", spid, removeResult.getMessage());
                }
            }
            executed = false;
            addedSpids.clear();
            
        } catch (Exception e) {
            throw new CommandExecutionException(
                getDescription(), 
                "undo", 
                "Failed to undo enhanced content injection: " + e.getMessage(),
                e
            );
        }
    }
    
    /**
     * Check if this command can be undone.
     * Enhanced content injection can be undone by removing the added content.
     * 
     * @return true if the command can be undone
     */
    @Override
    public boolean canUndo() {
        // Enhanced content injection will be undoable once implemented
        return executed && !addedSpids.isEmpty();
    }
    
    /**
     * Get the description of this command.
     * 
     * @return description of the enhanced content operation
     */
    @Override
    public String getDescription() {
        StringBuilder desc = new StringBuilder();
        desc.append("Inject enhanced content on slide ").append(slideNumber);
        desc.append(" with keyword '").append(iconKeyword).append("'");
        
        if (templateStyle != null && !templateStyle.trim().isEmpty()) {
            desc.append(" using style '").append(templateStyle).append("'");
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
     * Get the icon keyword.
     * 
     * @return the icon keyword
     */
    public String getIconKeyword() {
        return iconKeyword;
    }
    
    /**
     * Get the template style.
     * 
     * @return the template style
     */
    public String getTemplateStyle() {
        return templateStyle;
    }
    
    /**
     * Get the geometry parameters.
     * 
     * @return the geometry parameters
     */
    public Map<String, Object> getGeometry() {
        return geometry;
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