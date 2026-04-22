package com.excudo.core.commands;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.ShapeRegistry;
import com.excudo.core.model.SlideShape;
import com.excudo.core.results.ExecutionResult;

/**
 * GoF Command for resizing a shape on a slide.
 *
 * Captures the original geometry before resizing so that undo can restore
 * the shape to its previous dimensions. Position (x, y) is preserved.
 */
public class ResizeShapeCommand implements Command {

    private final int slideNumber;
    private final int spid;
    private final long newWidth;
    private final long newHeight;
    private final PPTXOrchestrator orchestrator;
    private boolean executed = false;
    private ShapeGeometry originalGeometry = null;

    public ResizeShapeCommand(int slideNumber, int spid, long newWidth, long newHeight, PPTXOrchestrator orchestrator) {
        if (orchestrator == null) {
            throw new IllegalArgumentException("PPTXOrchestrator cannot be null");
        }
        if (slideNumber <= 0) {
            throw new IllegalArgumentException("Slide number must be positive");
        }
        if (spid <= 0) {
            throw new IllegalArgumentException("SPID must be positive");
        }
        this.slideNumber = slideNumber;
        this.spid = spid;
        this.newWidth = newWidth;
        this.newHeight = newHeight;
        this.orchestrator = orchestrator;
    }

    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute", "Command has already been executed");
        }

        ExecutionResult<ShapeRegistry> registryResult = orchestrator.getShapeRegistry(slideNumber);
        if (!registryResult.isSuccess()) {
            throw new CommandExecutionException(getDescription(), "execute",
                "Failed to get shape registry: " + registryResult.getMessage());
        }
        ShapeRegistry registry = registryResult.getData().orElse(null);
        if (registry == null) {
            throw new CommandExecutionException(getDescription(), "execute", "Shape registry is empty");
        }
        SlideShape shape = registry.getShape(spid);
        if (shape == null) {
            throw new CommandExecutionException(getDescription(), "execute",
                "Shape with SPID " + spid + " not found");
        }
        originalGeometry = shape.getGeometry();

        ShapeGeometry newGeometry = new ShapeGeometry(originalGeometry.getX(), originalGeometry.getY(),
            newWidth, newHeight, originalGeometry.getRotation());

        ExecutionResult<Void> resizeResult = orchestrator.updateShapeGeometry(slideNumber, spid, newGeometry);
        if (!resizeResult.isSuccess()) {
            originalGeometry = null;
            throw new CommandExecutionException(getDescription(), "execute",
                "Failed to resize shape: " + resizeResult.getMessage());
        }

        executed = true;
    }

    @Override
    public void undo() {
        if (!executed) {
            throw new CommandExecutionException(getDescription(), "undo", "Command has not been executed");
        }
        if (!canUndo()) {
            throw new CommandExecutionException(getDescription(), "undo", "No captured state available for undo");
        }

        ExecutionResult<Void> restoreResult = orchestrator.updateShapeGeometry(slideNumber, spid, originalGeometry);
        if (!restoreResult.isSuccess()) {
            throw new CommandExecutionException(getDescription(), "undo",
                "Failed to restore shape dimensions: " + restoreResult.getMessage());
        }

        executed = false;
    }

    @Override
    public boolean canUndo() {
        return executed && originalGeometry != null;
    }

    @Override
    public String getDescription() {
        return "Resize shape SPID " + spid + " on slide " + slideNumber + " to " + newWidth + "x" + newHeight;
    }

    @Override
    public boolean isExecuted() {
        return executed;
    }

    public int getSlideNumber() { return slideNumber; }
    public int getSpid() { return spid; }
}
