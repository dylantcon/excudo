package com.excudo.core.commands.mutating.slide;

import com.excudo.core.commands.meta.UndoCommand;
import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandExecutionException;

import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.ShapeRegistry;
import com.excudo.core.model.SlideShape;
import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.results.ExecutionResult;

/**
 * GoF Command for rotating a shape. Position and size are preserved;
 * only the rotation attribute changes. Snapshot-based undo matches
 * the pattern in {@link MoveShapeCommand} and {@link ResizeShapeCommand}.
 *
 * <p>{@code newRotationDegrees} is in the user-facing degrees unit
 * (the ShapeGeometry convention). Storage at the OOXML layer is
 * 60000ths of a degree; the conversion happens here, not in the spec
 * layer, so CommandSpec records stay at the user-intent abstraction.
 */
public class RotateShapeCommand implements Command {

    private final int slideNumber;
    private final int spid;
    private final double newRotationDegrees;
    private final PPTXOrchestrator orchestrator;
    private boolean executed = false;
    private ShapeGeometry originalGeometry = null;

    public RotateShapeCommand(int slideNumber, int spid, double newRotationDegrees,
                              PPTXOrchestrator orchestrator) {
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
        this.newRotationDegrees = newRotationDegrees;
        this.orchestrator = orchestrator;
    }

    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute",
                "Command has already been executed");
        }
        ExecutionResult<ShapeRegistry> registryResult = orchestrator.getShapeRegistry(slideNumber);
        if (!registryResult.isSuccess()) {
            throw new CommandExecutionException(getDescription(), "execute",
                "Failed to get shape registry: " + registryResult.getMessage());
        }
        ShapeRegistry registry = registryResult.getData().orElse(null);
        if (registry == null) {
            throw new CommandExecutionException(getDescription(), "execute",
                "Shape registry is empty");
        }
        SlideShape shape = registry.getShape(spid);
        if (shape == null) {
            throw new CommandExecutionException(getDescription(), "execute",
                "Shape with SPID " + spid + " not found");
        }
        originalGeometry = shape.getGeometry();

        int rawRotation = (int) Math.round(newRotationDegrees * 60000.0);
        ShapeGeometry updated = new ShapeGeometry(
            originalGeometry.getX(), originalGeometry.getY(),
            originalGeometry.getWidth(), originalGeometry.getHeight(),
            rawRotation);

        ExecutionResult<Void> result = orchestrator.updateShapeGeometry(slideNumber, spid, updated);
        if (!result.isSuccess()) {
            originalGeometry = null;
            throw new CommandExecutionException(getDescription(), "execute",
                "Failed to rotate shape: " + result.getMessage());
        }
        executed = true;
    }

    @Override
    public void undo() {
        if (!executed) {
            throw new CommandExecutionException(getDescription(), UndoCommand.NAME,
                "Command has not been executed");
        }
        if (!canUndo()) {
            throw new CommandExecutionException(getDescription(), UndoCommand.NAME,
                "No captured state available for undo");
        }
        ExecutionResult<Void> restore = orchestrator.updateShapeGeometry(slideNumber, spid, originalGeometry);
        if (!restore.isSuccess()) {
            throw new CommandExecutionException(getDescription(), UndoCommand.NAME,
                "Failed to restore rotation: " + restore.getMessage());
        }
        executed = false;
    }

    @Override
    public boolean canUndo() {
        return executed && originalGeometry != null;
    }

    @Override
    public boolean isExecuted() {
        return executed;
    }

    @Override
    public String getDescription() {
        return String.format("Rotate shape SPID %d on slide %d to %.2f degrees",
            spid, slideNumber, newRotationDegrees);
    }

    public int getSlideNumber() { return slideNumber; }
    public int getSpid() { return spid; }
    public double getNewRotationDegrees() { return newRotationDegrees; }
}
