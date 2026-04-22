package com.excudo.core.commands.readonly;

import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandDisplay;
import com.excudo.core.commands.CommandExecutionException;
import com.excudo.core.commands.CommandSessionContext;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.results.ExecutionResult;
import com.excudo.core.model.ShapeRegistry;
import com.excudo.core.model.SlideShape;
import com.excudo.core.model.ParagraphMetadata;
import java.util.Optional;

/**
 * GoF Command for displaying detailed shape information.
 * 
 * This command contains the actual show-shape logic extracted from AbstractConsoleEngine.
 * Provides comprehensive shape details without circular dependencies.
 */
public class ShowShapeCommand implements Command {
    
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
            
            ParagraphMetadata meta = shape.getParagraphMetadata();
            if (meta != null && meta.getParagraphCount() > 0) {
                display.displayMessage("  Content:");
                for (int i = 0; i < meta.getParagraphCount(); i++) {
                    String content = meta.getParagraphContent(i);
                    if (content == null || content.trim().isEmpty()) continue;
                    if (meta.isParagraphBullet(i)) {
                        String marker = meta.getBulletMarker(i);
                        display.displayMessage(String.format("    %s %s", marker != null ? marker : "-", content));
                    } else {
                        display.displayMessage("    " + content);
                    }
                }
            } else if (shape.getText() != null && !shape.getText().trim().isEmpty()) {
                display.displayMessage("  Text: \"" + shape.getText() + "\"");
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