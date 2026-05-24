package com.excudo.core.commands.readonly;

import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandClassRegistry;
import com.excudo.core.commands.CommandContext;
import com.excudo.core.commands.CommandDisplay;
import com.excudo.core.commands.CommandExecutionException;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.orchestration.SlideMetadata;
import com.excudo.core.model.SlideShape;
import com.excudo.core.inspection.SlideInspector;
import com.excudo.core.parsing.CommandParameters;
import com.excudo.core.parsing.CommandSchema;
import com.excudo.core.parsing.Parameter;
import java.util.List;
import java.util.Optional;

/**
 * GoF Command for displaying detailed information about a specific slide.
 *
 * This is a read-only query command that shows slide content, shapes,
 * and metadata using existing console utilities. Does not support undo
 * since it performs no mutations.
 *
 * <p>Self-registers via {@link CommandClassRegistry}: canonical name
 * {@code show-slide} derives from the class.
 */
public class ShowSlideCommand implements Command {

    static final Parameter<Integer> SLIDE = Parameter.ofInt("slide")
        .slideNumber().description("Slide number").required().build();

    public static final CommandSchema SCHEMA = CommandSchema.builder()
        .description("Show slide details")
        .parameter(SLIDE)
        .example("show-slide 1")
        .build();

    public static final String NAME = CommandClassRegistry.nameOf(ShowSlideCommand.class);

    public static Command fromParameters(CommandParameters p, CommandContext ctx) {
        return new ShowSlideCommand(ctx.orchestrator(), ctx.requireDisplay(), p.get(SLIDE));
    }
    
    private final PPTXOrchestrator orchestrator;
    private final CommandDisplay display;
    private final int slideNumber;
    private boolean executed = false;
    
    
    /**
     * Create a ShowSlideCommand.
     * 
     * @param orchestrator the PPTX orchestrator for querying slide data
     * @param display the console display interface
     * @param slideNumber the slide number to show (1-based)
     */
    public ShowSlideCommand(PPTXOrchestrator orchestrator, CommandDisplay display, int slideNumber) {
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
            
            // Get slide metadata
            Optional<SlideMetadata> slideMetadata = orchestrator.getSlideMetadata(slideNumber);
            if (slideMetadata.isEmpty()) {
                display.displayError(String.format("Unable to read metadata for slide %d", slideNumber));
                executed = true;
                return;
            }
            
            SlideMetadata slide = slideMetadata.get();
            
            // Display slide header
            display.displayMessage(String.format("=== Slide %d ===", slideNumber));
            display.displayMessage(String.format("Title: %s", 
                slide.getTitle() != null ? slide.getTitle() : "(No title)"));
            display.displayMessage(String.format("Layout: %s", slide.getLayoutName()));
            display.displayMessage(String.format("Type: %s", slide.getType()));
            display.displayMessage("");
            
            // Display shape information
            if (slide.getShapeCount() == 0) {
                display.displayMessage("No shapes on this slide.");
            } else {
                display.displayMessage(String.format("Shapes (%d):", slide.getShapeCount()));
                
                // Try to get detailed shape information using SlideInspector
                try {
                    List<SlideShape> shapes = SlideInspector.getSlideShapes(
                        orchestrator, slideNumber);
                    
                    if (shapes.isEmpty()) {
                        display.displayMessage("  SPIDs: " + slide.getSpids());
                    } else {
                        for (SlideShape shape : shapes) {
                            display.displayMessage(String.format("  SPID %d: %s",
                                shape.getSpid(), shape.getType()));
                            displayShapeText(shape);
                        }
                    }
                } catch (Exception e) {
                    // Fallback to basic SPID list
                    display.displayMessage("  SPIDs: " + slide.getSpids());
                }
            }
            
            // Display animation information
            if (slide.getAnimationCount() > 0) {
                display.displayMessage("");
                display.displayMessage(String.format("Animations: %d", slide.getAnimationCount()));
                display.displayMessage("  Use 'list-animations " + slideNumber + "' for detailed animation info");
            }
            
            executed = true;
            
        } catch (Exception e) {
            throw new CommandExecutionException(getDescription(), "execute", 
                "Failed to show slide " + slideNumber + ": " + e.getMessage(), e);
        }
    }
    
    @Override
    public void undo() {
        throw new CommandExecutionException(getDescription(), "undo", 
            "ShowSlideCommand is read-only and does not support undo");
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
        return "Show slide " + slideNumber;
    }

    private void displayShapeText(SlideShape shape) {
        String rendered = com.excudo.core.model.ShapeTextWriter.render(shape, "    ");
        for (String line : rendered.split("\\n", -1)) {
            if (!line.isEmpty()) display.displayMessage(line);
        }
    }
}