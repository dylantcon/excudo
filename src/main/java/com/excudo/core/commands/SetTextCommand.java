package com.excudo.core.commands;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.model.TextBody;
import com.excudo.core.results.ExecutionResult;

/**
 * GoF Command that replaces a shape's text body with a richly-formatted TextBody.
 * Supports undo by capturing the original shape element before modification.
 */
public class SetTextCommand implements Command {

    private final int slideNumber;
    private final int spid;
    private final TextBody textBody;
    private final PPTXOrchestrator orchestrator;
    private boolean executed = false;
    private org.w3c.dom.Element originalShapeElement = null;

    public SetTextCommand(int slideNumber, int spid, TextBody textBody, PPTXOrchestrator orchestrator) {
        if (orchestrator == null) throw new IllegalArgumentException("PPTXOrchestrator cannot be null");
        if (textBody == null) throw new IllegalArgumentException("TextBody cannot be null");
        if (spid <= 0) throw new IllegalArgumentException("SPID must be positive");
        this.slideNumber = slideNumber;
        this.spid = spid;
        this.textBody = textBody;
        this.orchestrator = orchestrator;
    }

    @Override
    public void execute() {
        if (executed) throw new CommandExecutionException(getDescription(), "execute", "Already executed");
        try {
            // Capture shape element for undo
            ExecutionResult<org.w3c.dom.Element> captureResult = orchestrator.captureShapeElement(slideNumber, spid);
            if (captureResult.isSuccess() && captureResult.getData().isPresent()) {
                originalShapeElement = captureResult.getData().get();
            }

            ExecutionResult<Void> result = orchestrator.setTextBody(slideNumber, spid, textBody);
            if (result.isSuccess()) {
                executed = true;
            } else {
                throw new CommandExecutionException(getDescription(), "execute", "Failed: " + result.getMessage());
            }
        } catch (CommandExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new CommandExecutionException(getDescription(), "execute", e.getMessage(), e);
        }
    }

    @Override
    public void undo() {
        if (!executed) throw new CommandExecutionException(getDescription(), "undo", "Not executed");
        if (!canUndo()) throw new CommandExecutionException(getDescription(), "undo", "Cannot undo");
        try {
            orchestrator.removeShape(slideNumber, spid);
            orchestrator.restoreShape(slideNumber, originalShapeElement);
            executed = false;
        } catch (Exception e) {
            throw new CommandExecutionException(getDescription(), "undo", e.getMessage(), e);
        }
    }

    @Override
    public boolean canUndo() { return executed && originalShapeElement != null; }

    @Override
    public String getDescription() {
        return String.format("Set rich text on SPID %d slide %d (%d paragraphs)", spid, slideNumber, textBody.getParagraphs().size());
    }

    @Override
    public boolean isExecuted() { return executed; }

    public int getSlideNumber() { return slideNumber; }
    public int getSpid() { return spid; }
}
