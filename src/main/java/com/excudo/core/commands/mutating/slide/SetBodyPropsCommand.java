package com.excudo.core.commands.mutating.slide;

import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandExecutionException;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.model.BodyProperties;
import com.excudo.core.results.ExecutionResult;

/**
 * GoF Command that updates body properties (a:bodyPr) on a shape,
 * and optionally marks it as a textbox via cNvSpPr/@txBox.
 *
 * Before mutation, the command captures a deep clone of the shape's DOM element
 * via the orchestrator's captureShapeElement API. On undo, the original element
 * is restored back into the slide's spTree.
 */
public class SetBodyPropsCommand implements Command {

    private final int slideNumber;
    private final int spid;
    private final BodyProperties bodyProperties;
    private final boolean textBox;
    private final PPTXOrchestrator orchestrator;
    private boolean executed = false;
    private org.w3c.dom.Element originalShapeElement = null;

    public SetBodyPropsCommand(int slideNumber, int spid, BodyProperties bodyProperties,
                               boolean textBox, PPTXOrchestrator orchestrator) {
        if (orchestrator == null) throw new IllegalArgumentException("PPTXOrchestrator cannot be null");
        if (bodyProperties == null) throw new IllegalArgumentException("BodyProperties cannot be null");
        if (spid <= 0) throw new IllegalArgumentException("SPID must be positive");
        this.slideNumber = slideNumber;
        this.spid = spid;
        this.bodyProperties = bodyProperties;
        this.textBox = textBox;
        this.orchestrator = orchestrator;
    }

    @Override
    public void execute() {
        if (executed) throw new CommandExecutionException(getDescription(), "execute", "Already executed");
        try {
            // Capture shape for undo
            ExecutionResult<org.w3c.dom.Element> captureResult = orchestrator.captureShapeElement(slideNumber, spid);
            if (captureResult.isSuccess() && captureResult.getData().isPresent()) {
                originalShapeElement = captureResult.getData().get();
            }

            ExecutionResult<Void> result = orchestrator.setBodyProperties(slideNumber, spid, bodyProperties, textBox);
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
        return String.format("Set body properties on SPID %d slide %d (textBox=%s)", spid, slideNumber, textBox);
    }

    @Override
    public boolean isExecuted() { return executed; }

    public int getSlideNumber() { return slideNumber; }
    public int getSpid() { return spid; }
}
