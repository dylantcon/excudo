package com.excudo.core.commands.mutating.slide;

import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandExecutionException;

import com.excudo.core.model.ShapeStyle;
import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.results.ExecutionResult;

/**
 * GoF Command for applying fill/line style overrides to an existing shape.
 *
 * Captures the full shape DOM element before modification so undo can
 * restore it exactly. Delegates to orchestrator.updateShapeStyle().
 */
public class SetStyleCommand implements Command {

    private final int slideNumber;
    private final int spid;
    private final ShapeStyle style;
    private final PPTXOrchestrator orchestrator;
    private boolean executed = false;
    private org.w3c.dom.Element capturedElement = null;

    /**
     * @param slideNumber Slide number (1-based)
     * @param spid        Shape SPID to re-style
     * @param style       ShapeStyle containing fill, line, and theme style ref overrides
     * @param orchestrator PPTXOrchestrator instance
     */
    public SetStyleCommand(int slideNumber, int spid, ShapeStyle style,
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
        if (style == null) {
            throw new IllegalArgumentException("ShapeStyle cannot be null");
        }
        this.slideNumber = slideNumber;
        this.spid = spid;
        this.style = style;
        this.orchestrator = orchestrator;
    }

    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute",
                "Command has already been executed");
        }

        ExecutionResult<org.w3c.dom.Element> captureResult =
            orchestrator.captureShapeElement(slideNumber, spid);
        if (!captureResult.isSuccess()) {
            throw new CommandExecutionException(getDescription(), "execute",
                "Failed to capture shape for undo: " + captureResult.getMessage());
        }
        capturedElement = captureResult.getData().orElse(null);

        ExecutionResult<Void> result =
            orchestrator.updateShapeStyle(slideNumber, spid, style);
        if (!result.isSuccess()) {
            capturedElement = null;
            throw new CommandExecutionException(getDescription(), "execute",
                "Failed to update shape style: " + result.getMessage());
        }

        executed = true;
    }

    @Override
    public void undo() {
        if (!executed) {
            throw new CommandExecutionException(getDescription(), "undo",
                "Command has not been executed");
        }
        if (!canUndo()) {
            throw new CommandExecutionException(getDescription(), "undo",
                "No captured state available for undo");
        }

        ExecutionResult<Void> removeResult = orchestrator.removeShape(slideNumber, spid);
        if (!removeResult.isSuccess()) {
            throw new CommandExecutionException(getDescription(), "undo",
                "Failed to remove styled shape during undo: " + removeResult.getMessage());
        }

        ExecutionResult<Void> restoreResult =
            orchestrator.restoreShape(slideNumber, capturedElement);
        if (!restoreResult.isSuccess()) {
            throw new CommandExecutionException(getDescription(), "undo",
                "Failed to restore shape: " + restoreResult.getMessage());
        }

        executed = false;
        capturedElement = null;
    }

    @Override
    public boolean canUndo() {
        return executed && capturedElement != null;
    }

    @Override
    public String getDescription() {
        return String.format("Set style on shape SPID %d on slide %d", spid, slideNumber);
    }

    @Override
    public boolean isExecuted() {
        return executed;
    }

    public int getSlideNumber() { return slideNumber; }
    public int getSpid() { return spid; }
    public ShapeStyle getStyle() { return style; }
}
