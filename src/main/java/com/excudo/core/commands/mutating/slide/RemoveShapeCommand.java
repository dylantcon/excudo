package com.excudo.core.commands.mutating.slide;

import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandClassRegistry;
import com.excudo.core.commands.CommandContext;
import com.excudo.core.commands.CommandExecutionException;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.parsing.CommandParameters;
import com.excudo.core.parsing.CommandSchema;
import com.excudo.core.parsing.Parameter;
import com.excudo.core.results.ExecutionResult;

/**
 * GoF Command for removing shapes from slides with full undo support.
 *
 * Before removal, the Command captures a deep clone of the shape's DOM element
 * via the orchestrator's captureShapeElement API. On undo, the captured element
 * is restored back into the slide's spTree. This keeps undo state entirely
 * within the Command, as the pattern requires.
 *
 * <p>Self-registers via {@link com.excudo.core.commands.CommandClassRegistry}:
 * the canonical name {@code remove-shape} derives from the class name.
 */
public class RemoveShapeCommand implements Command {

    static final Parameter<Integer> SLIDE = Parameter.ofInt("slide")
        .slideNumber().description("Slide number").llmName("slideNumber").required().build();
    static final Parameter<Integer> SPID = Parameter.ofInt("spid")
        .spid().description("Shape ID to remove").llmName("targetSpid").required().build();

    public static final CommandSchema SCHEMA = CommandSchema.builder()
        .description("Remove a shape from a slide")
        .llmEnabled(true)
        .parameter(SLIDE)
        .parameter(SPID)
        .example("remove-shape 1 5")
        .build();

    public static final String NAME = CommandClassRegistry.nameOf(RemoveShapeCommand.class);

    public static Command fromParameters(CommandParameters p, CommandContext ctx) {
        return new RemoveShapeCommand(p.get(SLIDE), p.get(SPID), ctx.orchestrator());
    }

    private final int slideNumber;
    private final int spid;
    private final PPTXOrchestrator orchestrator;
    private boolean executed = false;
    private org.w3c.dom.Element capturedElement = null;
    /** The slide's timeline before removal: removing a shape removes its animations too. */
    private org.w3c.dom.Element capturedTiming = null;

    public RemoveShapeCommand(int slideNumber, int spid, PPTXOrchestrator orchestrator) {
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
        this.orchestrator = orchestrator;
    }

    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute", "Command has already been executed");
        }

        // Snapshot the shape element before destroying it
        ExecutionResult<org.w3c.dom.Element> captureResult = orchestrator.captureShapeElement(slideNumber, spid);
        if (!captureResult.isSuccess()) {
            throw new CommandExecutionException(getDescription(), "execute",
                "Failed to capture shape for undo: " + captureResult.getMessage());
        }
        capturedElement = captureResult.getData().orElse(null);
        org.w3c.dom.Element timing = timingOf(slideDom());
        capturedTiming = timing == null ? null : (org.w3c.dom.Element) timing.cloneNode(true);

        // Now remove
        ExecutionResult<Void> removeResult = orchestrator.removeShape(slideNumber, spid);
        if (!removeResult.isSuccess()) {
            capturedElement = null;
            throw new CommandExecutionException(getDescription(), "execute",
                "Failed to remove shape: " + removeResult.getMessage());
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

        ExecutionResult<Void> restoreResult = orchestrator.restoreShape(slideNumber, capturedElement);
        if (!restoreResult.isSuccess()) {
            throw new CommandExecutionException(getDescription(), "undo",
                "Failed to restore shape: " + restoreResult.getMessage());
        }
        restoreTiming();

        executed = false;
    }

    private org.w3c.dom.Document slideDom() {
        return orchestrator.getContext()
            .map(c -> c.getDocument())
            .filter(d -> d != null && d.hasSlide(slideNumber))
            .map(d -> d.getSlideDocument(slideNumber))
            .orElse(null);
    }

    private static org.w3c.dom.Element timingOf(org.w3c.dom.Document dom) {
        if (dom == null) return null;
        for (org.w3c.dom.Node n = dom.getDocumentElement().getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof org.w3c.dom.Element e && "timing".equals(e.getLocalName())) return e;
        }
        return null;
    }

    /** Put back the timeline as it was, animations of the removed shape included. */
    private void restoreTiming() {
        if (capturedTiming == null) return;
        org.w3c.dom.Document dom = slideDom();
        if (dom == null) return;
        org.w3c.dom.Element root = dom.getDocumentElement();
        org.w3c.dom.Element current = timingOf(dom);
        org.w3c.dom.Element restored = (org.w3c.dom.Element) capturedTiming.cloneNode(true);
        if (current != null) {
            root.replaceChild(restored, current);
        } else {
            // p:timing sits after p:transition and before p:extLst
            org.w3c.dom.Node before = null;
            for (org.w3c.dom.Node n = root.getFirstChild(); n != null; n = n.getNextSibling()) {
                if (n instanceof org.w3c.dom.Element e && "extLst".equals(e.getLocalName())) before = e;
            }
            root.insertBefore(restored, before);
        }
        orchestrator.getContext().get().getDocument().putSlideDocument(slideNumber, dom);
    }

    @Override
    public boolean canUndo() {
        return executed && capturedElement != null;
    }

    @Override
    public String getDescription() {
        return "Remove shape SPID " + spid + " from slide " + slideNumber;
    }

    @Override
    public boolean isExecuted() {
        return executed;
    }

    public int getSlideNumber() { return slideNumber; }
    public int getSpid() { return spid; }
}
