package com.excudo.core.commands.readonly;

import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandDisplay;
import com.excudo.core.commands.CommandExecutionException;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.orchestration.SlideMetadata;
import com.excudo.core.model.SlideShape;
import com.excudo.core.inspection.SlideInspector;
import java.util.List;
import java.util.Optional;

/**
 * GoF Command for listing shape IDs (SPIDs) on a specific slide.
 * 
 * This is a read-only query command that displays shape information
 * using existing console utilities. Does not support undo since it
 * performs no mutations.
 */
public class ListSpidsCommand implements Command {
    
    private final PPTXOrchestrator orchestrator;
    private final CommandDisplay display;
    private final int slideNumber;
    private boolean executed = false;
    
    
    /**
     * Create a ListSpidsCommand.
     * 
     * @param orchestrator the PPTX orchestrator for querying slide data
     * @param display the console display interface
     * @param slideNumber the slide number (1-based)
     */
    public ListSpidsCommand(PPTXOrchestrator orchestrator, CommandDisplay display, int slideNumber) {
        if (orchestrator == null) {
            throw new IllegalArgumentException("PPTXOrchestrator cannot be null");
        }
        if (display == null) {
            throw new IllegalArgumentException("CommandDisplay cannot be null");
        }
        if (slideNumber < 1) {
            throw new IllegalArgumentException("Slide number must be positive");
        }
        this.orchestrator = orchestrator;
        this.display = display;
        this.slideNumber = slideNumber;
    }
    
    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute", "Command has already been executed");
        }
        
        try {
            // Check if presentation is loaded
            Optional<com.excudo.core.orchestration.OrchestrationContext> context = orchestrator.getContext();
            if (context.isEmpty()) {
                display.displayError("No presentation loaded. Use 'load <filename>' to open a presentation.");
                executed = true;
                return;
            }
            
            // Validate slide number
            int slideCount = orchestrator.getPresentationMetadata().getSlideCount();
            if (slideNumber > slideCount) {
                display.displayError(String.format("Slide %d does not exist. Presentation has %d slides.", 
                    slideNumber, slideCount));
                executed = true;
                return;
            }
            
            // Get slide metadata for basic SPID list
            Optional<SlideMetadata> slideMetadata = orchestrator.getSlideMetadata(slideNumber);
            if (slideMetadata.isEmpty()) {
                display.displayError(String.format("Unable to read metadata for slide %d", slideNumber));
                executed = true;
                return;
            }
            
            SlideMetadata slide = slideMetadata.get();
            
            // Display header
            display.displayMessage(String.format("Shape IDs on slide %d:", slideNumber));
            
            if (slide.getShapeCount() == 0) {
                display.displayMessage("  No shapes found on this slide.");
                executed = true;
                return;
            }
            
            // Try to get detailed shape information using SlideInspector
            try {
                List<SlideShape> shapes = SlideInspector.getSlideShapes(
                    orchestrator, slideNumber);
                
                if (shapes.isEmpty()) {
                    // Fallback to basic SPID list from metadata
                    display.displayMessage("  SPIDs: " + slide.getSpids());
                } else {
                    // Display detailed shape information
                    for (SlideShape shape : shapes) {
                        StringBuilder info = new StringBuilder();
                        info.append(String.format("  SPID %d: %s", shape.getSpid(), shape.getType()));
                        
                        // Add shape name if available
                        if (shape.getName() != null && !shape.getName().trim().isEmpty()) {
                            info.append(String.format(" (%s)", shape.getName()));
                        }
                        
                        // Add text preview if shape has text content
                        if (shape.getTextContent() != null && !shape.getTextContent().trim().isEmpty()) {
                            String preview = shape.getTextContent().length() > 30 
                                ? shape.getTextContent().substring(0, 27) + "..." 
                                : shape.getTextContent();
                            info.append(String.format(" - \"%s\"", preview));
                        }
                        
                        display.displayMessage(info.toString());
                    }
                }
                
                // Display summary
                display.displayMessage("");
                display.displayMessage(String.format("Total shapes: %d", slide.getShapeCount()));
                
            } catch (Exception e) {
                // Fallback to basic SPID list if detailed inspection fails
                display.displayMessage("  SPIDs: " + slide.getSpids());
                display.displayMessage(String.format("  Total shapes: %d", slide.getShapeCount()));
                display.displayMessage("  (Use 'show " + slideNumber + "' for detailed shape information)");
            }
            
            executed = true;
            
        } catch (Exception e) {
            throw new CommandExecutionException(getDescription(), "execute", 
                "Failed to list SPIDs for slide " + slideNumber + ": " + e.getMessage(), e);
        }
    }
    
    @Override
    public void undo() {
        throw new CommandExecutionException(getDescription(), "undo", 
            "ListSpidsCommand is read-only and does not support undo");
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
        return "List SPIDs for slide " + slideNumber;
    }
}