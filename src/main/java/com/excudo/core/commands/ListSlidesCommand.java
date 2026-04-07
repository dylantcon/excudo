package com.excudo.core.commands;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.orchestration.PresentationMetadata;
import com.excudo.core.orchestration.SlideMetadata;
import java.util.Optional;

/**
 * GoF Command for listing slides in a presentation.
 * 
 * This is a read-only query command that displays slide information
 * using existing console utilities. Does not support undo since it
 * performs no mutations.
 */
public class ListSlidesCommand implements Command {
    
    private final PPTXOrchestrator orchestrator;
    private final CommandDisplay display;
    private final boolean verbose;
    private boolean executed = false;
    
    
    /**
     * Create a ListSlidesCommand.
     * 
     * @param orchestrator the PPTX orchestrator for querying slides
     * @param display the console display interface
     * @param verbose whether to show detailed information
     */
    public ListSlidesCommand(PPTXOrchestrator orchestrator, CommandDisplay display, boolean verbose) {
        if (orchestrator == null) {
            throw new IllegalArgumentException("PPTXOrchestrator cannot be null");
        }
        if (display == null) {
            throw new IllegalArgumentException("CommandDisplay cannot be null");
        }
        this.orchestrator = orchestrator;
        this.display = display;
        this.verbose = verbose;
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
            
            // Get presentation metadata
            PresentationMetadata metadata = orchestrator.getPresentationMetadata();
            
            if (metadata.getSlideCount() == 0) {
                display.displayMessage("Presentation contains no slides.");
                executed = true;
                return;
            }
            
            // Display presentation summary
            display.displayMessage(String.format("Presentation: %s (%d slides)", 
                metadata.getTitle() != null ? metadata.getTitle() : "Untitled", 
                metadata.getSlideCount()));
            display.displayMessage("");
            
            // List slides using orchestrator's slide metadata
            for (int slideNum = 1; slideNum <= metadata.getSlideCount(); slideNum++) {
                try {
                    Optional<SlideMetadata> slideMetadata = orchestrator.getSlideMetadata(slideNum);
                    if (slideMetadata.isPresent()) {
                        SlideMetadata slide = slideMetadata.get();
                        
                        if (verbose) {
                            // Verbose output with shape counts and layout info
                            display.displayMessage(String.format("  Slide %d: %s", 
                                slideNum, 
                                slide.getTitle() != null ? slide.getTitle() : "(No title)"));
                            display.displayMessage(String.format("    Layout: %s", slide.getLayoutName()));
                            display.displayMessage(String.format("    Shapes: %d, Animations: %d", 
                                slide.getShapeCount(), slide.getAnimationCount()));
                            if (!slide.getSpids().isEmpty()) {
                                display.displayMessage(String.format("    SPIDs: %s", slide.getSpids()));
                            }
                        } else {
                            // Compact output
                            display.displayMessage(String.format("  Slide %d: %s (%d shapes)", 
                                slideNum, 
                                slide.getTitle() != null ? slide.getTitle() : "(No title)",
                                slide.getShapeCount()));
                        }
                    } else {
                        display.displayMessage(String.format("  Slide %d: (Unable to read slide metadata)", slideNum));
                    }
                } catch (Exception e) {
                    display.displayMessage(String.format("  Slide %d: (Error reading slide: %s)", slideNum, e.getMessage()));
                }
            }
            
            executed = true;
            
        } catch (Exception e) {
            throw new CommandExecutionException(getDescription(), "execute", 
                "Failed to list slides: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void undo() {
        throw new CommandExecutionException(getDescription(), "undo", 
            "ListSlidesCommand is read-only and does not support undo");
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
        return verbose ? "List slides (verbose)" : "List slides";
    }
}