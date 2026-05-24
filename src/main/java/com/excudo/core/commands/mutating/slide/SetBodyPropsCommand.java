package com.excudo.core.commands.mutating.slide;

import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandClassRegistry;
import com.excudo.core.commands.CommandContext;
import com.excudo.core.commands.CommandExecutionException;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.model.AutofitType;
import com.excudo.core.model.BodyProperties;
import com.excudo.core.parsing.CommandParameters;
import com.excudo.core.parsing.CommandSchema;
import com.excudo.core.parsing.Parameter;
import com.excudo.core.results.ExecutionResult;

/**
 * GoF Command that updates body properties (a:bodyPr) on a shape,
 * and optionally marks it as a textbox via cNvSpPr/@txBox.
 *
 * Before mutation, the command captures a deep clone of the shape's DOM element
 * via the orchestrator's captureShapeElement API. On undo, the original element
 * is restored back into the slide's spTree.
 *
 * <p>Self-registers via {@link com.excudo.core.commands.CommandClassRegistry}:
 * the canonical name {@code set-body-props} derives from the class name.
 */
public class SetBodyPropsCommand implements Command {

    static final Parameter<Integer> SLIDE = Parameter.ofInt("slide")
        .slideNumber().description("Slide number").required().build();
    static final Parameter<Integer> SPID = Parameter.ofInt("spid")
        .spid().description("Shape ID").required().build();
    static final Parameter<String> ANCHOR = Parameter.ofString("anchor")
        .description("Vertical alignment: t, ctr, b").required(false).build();
    static final Parameter<String> VERT = Parameter.ofString("vert")
        .description("Text direction: vert, vert270, wordArtVert").required(false).build();
    static final Parameter<String> AUTOFIT = Parameter.ofString("autofit")
        .description("Autofit mode: none, normal, shape").required(false).build();
    static final Parameter<Integer> COLUMNS = Parameter.ofInt("columns")
        .description("Number of text columns").required(false).build();
    static final Parameter<String> WRAP = Parameter.ofString("wrap")
        .description("Text wrap mode: square, none").required(false).build();
    static final Parameter<String> TEXTBOX = Parameter.ofString("textbox")
        .description("Mark shape as textbox (true/false)").required(false).build();

    public static final CommandSchema SCHEMA = CommandSchema.builder()
        .description("Set body properties on a shape")
        .parameter(SLIDE)
        .parameter(SPID)
        .parameter(ANCHOR)
        .parameter(VERT)
        .parameter(AUTOFIT)
        .parameter(COLUMNS)
        .parameter(WRAP)
        .parameter(TEXTBOX)
        .example("set-body-props 1 3 --anchor ctr")
        .example("set-body-props 1 3 --autofit shape --textbox true")
        .example("set-body-props 1 3 --vert vert270 --columns 2")
        .build();

    public static final String NAME = CommandClassRegistry.nameOf(SetBodyPropsCommand.class);

    public static Command fromParameters(CommandParameters p, CommandContext ctx) {
        BodyProperties.Builder bp = BodyProperties.builder();
        p.opt(ANCHOR).ifPresent(bp::verticalAlignment);
        p.opt(VERT).ifPresent(bp::verticalText);
        p.opt(WRAP).ifPresent(bp::wrap);
        p.opt(COLUMNS).ifPresent(bp::numColumns);
        p.opt(AUTOFIT).ifPresent(a -> {
            switch (a.toLowerCase()) {
                case "none": bp.autofit(AutofitType.NONE); break;
                case "normal": bp.autofit(AutofitType.NORMAL); break;
                case "shape": bp.autofit(AutofitType.SHAPE); break;
            }
        });
        boolean isTextBox = p.opt(TEXTBOX)
            .map(s -> "true".equalsIgnoreCase(s) || "1".equals(s)).orElse(false);
        return new SetBodyPropsCommand(p.get(SLIDE), p.get(SPID), bp.build(),
            isTextBox, ctx.orchestrator());
    }

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
