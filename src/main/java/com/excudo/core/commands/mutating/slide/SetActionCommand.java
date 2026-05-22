package com.excudo.core.commands.mutating.slide;

import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandContext;
import com.excudo.core.commands.CommandExecutionException;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.parsing.CommandParameters;
import com.excudo.core.parsing.CommandSchema;
import com.excudo.core.parsing.Parameter;
import com.excudo.core.results.ExecutionResult;

/**
 * GoF Command that sets a hyperlink action on a shape.
 * Supports navigation actions (next slide, previous slide, etc.) and optional audio embedding.
 *
 * Before mutation, the command captures a deep clone of the shape's DOM element
 * via the orchestrator's captureShapeElement API. On undo, the original element
 * is restored back into the slide's spTree.
 *
 * <p>Self-registers via {@link com.excudo.core.commands.CommandClassRegistry}:
 * the canonical name {@code set-action} derives from the class name.
 */
public class SetActionCommand implements Command {

    static final Parameter<Integer> SLIDE = Parameter.ofInt("slide")
        .slideNumber().description("Slide number").required().build();
    static final Parameter<Integer> SPID = Parameter.ofInt("spid")
        .spid().description("Shape ID").required().build();
    static final Parameter<String> ACTION = Parameter.ofString("action")
        .description("Action type: nextslide, previousslide, firstslide, lastslide, endshow, noaction")
        .required().build();
    static final Parameter<String> SOUND = Parameter.ofString("sound")
        .description("Audio file path to embed").required(false).build();

    public static final CommandSchema SCHEMA = CommandSchema.builder()
        .description("Set hyperlink action on a shape")
        .parameter(SLIDE)
        .parameter(SPID)
        .parameter(ACTION)
        .parameter(SOUND)
        .example("set-action 1 5 nextslide")
        .example("set-action 1 5 noaction --sound applause.wav")
        .build();

    public static Command fromParameters(CommandParameters p, CommandContext ctx) {
        return new SetActionCommand(p.get(SLIDE), p.get(SPID), p.get(ACTION),
            p.opt(SOUND).orElse(null), ctx.orchestrator());
    }

    private final int slideNumber;
    private final int spid;
    private final String actionType;
    private final String soundFile;
    private final PPTXOrchestrator orchestrator;
    private boolean executed = false;
    private org.w3c.dom.Element originalShapeElement = null;

    public SetActionCommand(int slideNumber, int spid, String actionType, String soundFile,
                            PPTXOrchestrator orchestrator) {
        if (orchestrator == null) throw new IllegalArgumentException("PPTXOrchestrator cannot be null");
        if (actionType == null || actionType.isEmpty()) throw new IllegalArgumentException("Action type required");
        if (spid <= 0) throw new IllegalArgumentException("SPID must be positive");
        this.slideNumber = slideNumber;
        this.spid = spid;
        this.actionType = actionType;
        this.soundFile = soundFile;
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

            ExecutionResult<Void> result = orchestrator.setAction(slideNumber, spid, actionType, soundFile);
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
        return String.format("Set action '%s' on SPID %d slide %d%s", actionType, spid, slideNumber,
            soundFile != null ? " with sound " + soundFile : "");
    }

    @Override
    public boolean isExecuted() { return executed; }

    public int getSlideNumber() { return slideNumber; }
    public int getSpid() { return spid; }
    public String getActionType() { return actionType; }
    public String getSoundFile() { return soundFile; }
}
