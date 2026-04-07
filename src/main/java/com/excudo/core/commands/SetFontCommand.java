package com.excudo.core.commands;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.results.ExecutionResult;
import java.util.Map;

/**
 * GoF Command for updating font properties on a shape's text runs.
 *
 * Captures the full shape DOM element before modification so undo can
 * restore it exactly. Delegates to orchestrator.updateShapeTextProperties().
 */
public class SetFontCommand implements Command {

    private final int slideNumber;
    private final int spid;
    private final Map<String, Object> fontProperties;
    private final PPTXOrchestrator orchestrator;
    private boolean executed = false;
    private org.w3c.dom.Element capturedElement = null;

    /**
     * @param slideNumber  Slide number (1-based)
     * @param spid         Shape SPID to modify
     * @param fontProperties  Map of font properties to apply. Recognized keys:
     *                        "family" (String), "size" (int pts), "bold" (Boolean),
     *                        "italic" (Boolean), "underline" (Boolean), "color" (String hex/scheme)
     * @param orchestrator PPTXOrchestrator instance
     */
    public SetFontCommand(int slideNumber, int spid, Map<String, Object> fontProperties,
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
        if (fontProperties == null || fontProperties.isEmpty()) {
            throw new IllegalArgumentException("Font properties map cannot be null or empty");
        }
        this.slideNumber = slideNumber;
        this.spid = spid;
        this.fontProperties = fontProperties;
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
            orchestrator.updateShapeTextProperties(slideNumber, spid, fontProperties);
        if (!result.isSuccess()) {
            capturedElement = null;
            throw new CommandExecutionException(getDescription(), "execute",
                "Failed to update font properties: " + result.getMessage());
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
                "Failed to remove modified shape during undo: " + removeResult.getMessage());
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
        return String.format("Set font properties on shape SPID %d on slide %d", spid, slideNumber);
    }

    @Override
    public boolean isExecuted() {
        return executed;
    }

    public int getSlideNumber() { return slideNumber; }
    public int getSpid() { return spid; }
}
