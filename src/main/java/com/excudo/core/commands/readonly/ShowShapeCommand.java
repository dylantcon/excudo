package com.excudo.core.commands.readonly;

import com.excudo.core.commands.Command;
import com.excudo.core.parsing.Parameter;
import com.excudo.core.parsing.CommandSchema;
import com.excudo.core.parsing.CommandParameters;
import com.excudo.core.commands.CommandContext;
import com.excudo.core.commands.CommandClassRegistry;
import com.excudo.core.commands.CommandDisplay;
import com.excudo.core.commands.CommandExecutionException;
import com.excudo.core.commands.CommandSessionContext;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.results.ExecutionResult;
import com.excudo.core.model.ShapeRegistry;
import com.excudo.core.model.ShapeTextWriter;
import com.excudo.core.model.SlideShape;
import java.util.Optional;

/**
 * GoF Command for displaying detailed shape information.
 * 
 * This command contains the actual show-shape logic extracted from AbstractConsoleEngine.
 * Provides comprehensive shape details without circular dependencies.
 */
public class ShowShapeCommand implements Command {

    static final Parameter<Integer> SLIDE = Parameter.ofInt("slide")
        .slideNumber().description("Slide number").required().build();
    static final Parameter<String> SPID = Parameter.ofString("spid")
        .spid().description("Shape ID").required().build();

    public static final CommandSchema SCHEMA = CommandSchema.builder()
        .description("Show details for a specific shape")
        .parameter(SLIDE).parameter(SPID)
        .example("show-shape 1 42")
        .build();

    public static final String NAME = CommandClassRegistry.nameOf(ShowShapeCommand.class);

    public static Command fromParameters(CommandParameters p, CommandContext ctx) {
        return new ShowShapeCommand(ctx.requireSession(), ctx.requireDisplay(),
            p.get(SLIDE), p.get(SPID));
    }

    
    private final CommandSessionContext sessionContext;
    private final CommandDisplay display;
    private final int slideNumber;
    private final String spid;
    private boolean executed = false;
    
    /**
     * Create a ShowShapeCommand.
     * 
     * @param sessionContext the current session context
     * @param display the console display interface
     * @param slideNumber the slide number (1-based)
     * @param spid the shape ID to display
     */
    public ShowShapeCommand(CommandSessionContext sessionContext, CommandDisplay display, int slideNumber, String spid) {
        if (sessionContext == null) {
            throw new IllegalArgumentException("CommandSessionContext cannot be null");
        }
        if (display == null) {
            throw new IllegalArgumentException("CommandDisplay cannot be null");
        }
        if (slideNumber <= 0) {
            throw new IllegalArgumentException("Slide number must be positive");
        }
        if (spid == null || spid.trim().isEmpty()) {
            throw new IllegalArgumentException("SPID cannot be null or empty");
        }
        this.sessionContext = sessionContext;
        this.display = display;
        this.slideNumber = slideNumber;
        this.spid = spid.trim();
    }
    
    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute", "Command has already been executed");
        }
        
        try {
            PPTXOrchestrator orchestrator = sessionContext.getCurrentOrchestrator();
            if (orchestrator == null) {
                display.displayError("No active session. Use 'load' or 'session create' first.");
                executed = true;
                return;
            }
            
            ExecutionResult<ShapeRegistry> registryResult = orchestrator.getShapeRegistry(slideNumber);
            if (!registryResult.isSuccess()) {
                display.displayError("Failed to get shapes for slide " + slideNumber + ": " + registryResult.getMessage());
                executed = true;
                return;
            }
            
            ShapeRegistry registry = registryResult.getData().orElse(null);
            if (registry == null) {
                display.displayError("No shape registry available for slide " + slideNumber);
                executed = true;
                return;
            }
            
            Optional<SlideShape> shapeOpt = registry.getShapeBySpid(spid);
            if (!shapeOpt.isPresent()) {
                display.displayError("Shape not found: " + spid + " on slide " + slideNumber);
                executed = true;
                return;
            }
            
            SlideShape shape = shapeOpt.get();
            
            display.displayMessage("Shape Details (Slide " + slideNumber + "):");
            display.displayMessage("  SPID: " + shape.getSpid());
            display.displayMessage("  Type: " + shape.getType());
            display.displayMessage("  Name: " + (shape.getName() != null ? shape.getName() : "<unnamed>"));
            
            if (shape.getGeometry() != null) {
                var geom = shape.getGeometry();
                display.displayMessage("  Position: (" + geom.getXInPoints() + ", " + geom.getYInPoints() + ") points");
                display.displayMessage("  Size: " + geom.getWidthInPoints() + " x " + geom.getHeightInPoints() + " points");
                display.displayMessage("  EMU Position: (" + geom.getX() + ", " + geom.getY() + ")");
                display.displayMessage("  EMU Size: " + geom.getWidth() + " x " + geom.getHeight());
            } else {
                display.displayMessage("  Geometry: <unavailable>");
            }
            
            String rendered = ShapeTextWriter.render(shape, "    ");
            if (!rendered.isEmpty()) {
                display.displayMessage("  Content:");
                // ShapeTextWriter emits trailing '\n' per line; strip the
                // last one so displayMessage doesn't double-space.
                for (String line : rendered.split("\\n", -1)) {
                    if (!line.isEmpty()) display.displayMessage(line);
                }
            }
            
            executed = true;
            
        } catch (Exception e) {
            throw new CommandExecutionException(getDescription(), "execute", 
                "Failed to show shape: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void undo() {
        // Read-only operation - no undo needed
        executed = false;
    }
    
    @Override
    public boolean canUndo() {
        return true; // Read-only operation
    }
    
    @Override
    public boolean isExecuted() {
        return executed;
    }
    
    @Override
    public String getDescription() {
        return "Show shape " + spid + " on slide " + slideNumber;
    }
}