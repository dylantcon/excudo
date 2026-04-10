package com.excudo.core.commands;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.orchestration.SessionManager;
import com.excudo.core.model.SlideShape;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.ShapeStyle;
import com.excudo.core.results.ExecutionResult;

/**
 * GoF Command for adding new shapes to slides.
 * 
 * This command leverages the ShapeFactory system to create shapes with proper
 * shape types, geometry, and text content with full undo capability.
 */
public class AddShapeCommand implements Command {
    
    private final int slideNumber;
    private final SlideShape.ShapeType shapeType;
    private final ShapeGeometry geometry;
    private final String text;
    private final String shapeName;
    private final ShapeStyle style;
    private final PPTXOrchestrator orchestrator;
    private boolean executed = false;
    private Integer createdSpid = null;

    public AddShapeCommand(int slideNumber, SlideShape.ShapeType shapeType, ShapeGeometry geometry,
                          String text, String shapeName, PPTXOrchestrator orchestrator) {
        this(slideNumber, shapeType, geometry, text, shapeName, null, orchestrator);
    }

    public AddShapeCommand(int slideNumber, SlideShape.ShapeType shapeType, ShapeGeometry geometry,
                          String text, String shapeName, ShapeStyle style, PPTXOrchestrator orchestrator) {
        if (orchestrator == null) {
            throw new IllegalArgumentException("PPTXOrchestrator cannot be null");
        }
        if (shapeType == null) {
            throw new IllegalArgumentException("ShapeType cannot be null");
        }
        if (geometry == null) {
            throw new IllegalArgumentException("ShapeGeometry cannot be null");
        }
        
        this.slideNumber = slideNumber;
        this.shapeType = shapeType;
        this.geometry = geometry;
        this.text = text;
        this.shapeName = shapeName != null ? shapeName : "Shape_" + System.currentTimeMillis();
        this.style = style;
        this.orchestrator = orchestrator;
    }
    
    /**
     * Execute the add shape command.
     * 
     * @throws CommandExecutionException if the operation fails
     */
    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute", "Command has already been executed");
        }
        
        try {
            // Add shape using orchestrator - this will delegate to SlideXMLWriter which uses ShapeFactory
            ExecutionResult<Integer> result = orchestrator
                .addShape(slideNumber, shapeType, geometry, text, shapeName, style);
            
            if (result.isSuccess()) {
                createdSpid = result.getData().orElse(null);
                if (createdSpid == null) {
                    throw new CommandExecutionException(
                        getDescription(),
                        "execute",
                        "Shape creation succeeded but no SPID was returned"
                    );
                }
                executed = true;

                // Notify state listeners that the slide's contents changed.
                SessionManager.getInstance().fireSlideModified(slideNumber);
            } else {
                throw new CommandExecutionException(
                    getDescription(), 
                    "execute", 
                    "Failed to add shape: " + result.getMessage()
                );
            }
            
        } catch (CommandExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new CommandExecutionException(
                getDescription(), 
                "execute", 
                "Failed to add shape: " + e.getMessage(),
                e
            );
        }
    }
    
    /**
     * Undo the add shape command by removing the created shape.
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
            // Remove the shape using orchestrator
            ExecutionResult<Void> result = orchestrator
                .removeShape(slideNumber, createdSpid);
            
            if (result.isSuccess()) {
                executed = false;
                createdSpid = null;
            } else {
                throw new CommandExecutionException(
                    getDescription(), 
                    "undo", 
                    "Failed to undo add shape: " + result.getMessage()
                );
            }
            
        } catch (Exception e) {
            throw new CommandExecutionException(
                getDescription(), 
                "undo", 
                "Failed to undo add shape: " + e.getMessage(),
                e
            );
        }
    }
    
    /**
     * Check if this command can be undone.
     * Shape addition can be undone by removing the created shape.
     * 
     * @return true if the command can be undone
     */
    @Override
    public boolean canUndo() {
        return executed && createdSpid != null;
    }
    
    /**
     * Get the description of this command.
     * 
     * @return description of the add shape operation
     */
    @Override
    public String getDescription() {
        return String.format("Add %s shape '%s' to slide %d at (%d,%d) size %dx%d", 
                           shapeType.name(), shapeName, slideNumber, 
                           geometry.getX(), geometry.getY(), 
                           geometry.getWidth(), geometry.getHeight());
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
     * Get the shape type.
     * 
     * @return the shape type
     */
    public SlideShape.ShapeType getShapeType() {
        return shapeType;
    }
    
    /**
     * Get the shape geometry.
     * 
     * @return the shape geometry
     */
    public ShapeGeometry getGeometry() {
        return geometry;
    }
    
    /**
     * Get the text content.
     * 
     * @return the text content
     */
    public String getText() {
        return text;
    }
    
    /**
     * Get the shape name.
     * 
     * @return the shape name
     */
    public String getShapeName() {
        return shapeName;
    }
    
    /**
     * Get the created SPID (available after execution).
     * 
     * @return the created SPID, or null if not executed
     */
    public Integer getCreatedSpid() {
        return createdSpid;
    }
}