package com.excudo.core.commands.mutating.slide;

import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandClassRegistry;
import com.excudo.core.commands.CommandContext;
import com.excudo.core.commands.CommandExecutionException;
import com.excudo.core.model.BlipRef;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.parsing.CommandParameters;
import com.excudo.core.parsing.CommandSchema;
import com.excudo.core.parsing.Parameter;
import com.excudo.core.results.ExecutionResult;

/**
 * GoF Command for adding embedded picture shapes ({@code <p:pic>}) to a
 * slide pointing at media bytes that already live in the deck. The
 * companion of {@link com.excudo.core.synthesis.spec.CommandSpec.AddPictureSpec}
 * at the command surface; the synthesizer emits the spec, the runner
 * maps it through {@link com.excudo.core.synthesis.spec.SpecToCommandMapper}
 * to this command.
 *
 * <p>Self-registers via {@link CommandClassRegistry}: the canonical name
 * {@code add-picture} derives from the class name.
 *
 * <p>This command does not embed new media bytes. Use the smart-content
 * paths (SmartContentEnhancer, icon injection) when media bytes need to
 * be copied in from disk.
 */
public class AddPictureCommand implements Command {

    static final Parameter<Integer> SLIDE = Parameter.ofInt("slide")
        .slideNumber().description("Slide number").llmName("slideNumber").required().build();
    static final Parameter<String> MEDIA_PART = Parameter.ofString("media-part")
        .description("OPC part name of the media (e.g. ppt/media/image1.png). Must already exist in the deck.")
        .llmName("mediaPartName").required().build();
    static final Parameter<Long> X = Parameter.ofUnit("x")
        .description("X position in EMU/points/inches").required().build();
    static final Parameter<Long> Y = Parameter.ofUnit("y")
        .description("Y position in EMU/points/inches").required().build();
    static final Parameter<Long> WIDTH = Parameter.ofUnit("width")
        .description("Width in EMU/points/inches").required().build();
    static final Parameter<Long> HEIGHT = Parameter.ofUnit("height")
        .description("Height in EMU/points/inches").required().build();
    static final Parameter<String> NAME = Parameter.ofString("name")
        .description("Shape name; defaults to 'Picture {SPID}'").required(false).build();
    static final Parameter<String> MIME = Parameter.ofString("mime-type")
        .description("MIME type for cross-deck export; ignored when media part already in deck.")
        .required(false).build();

    public static final CommandSchema SCHEMA = CommandSchema.builder()
        .description("Add an embedded picture pointing at media already in the deck")
        .llmEnabled(true)
        .llmDescription("Add a picture shape that references existing media bytes in the deck.")
        .parameter(SLIDE)
        .parameter(MEDIA_PART)
        .parameter(X)
        .parameter(Y)
        .parameter(WIDTH)
        .parameter(HEIGHT)
        .parameter(NAME)
        .parameter(MIME)
        .example("add-picture 1 ppt/media/image1.png 1in 1in 4in 3in")
        .example("add-picture 2 ppt/media/image1.png 0 0 9144000 6858000 --name Hero")
        .build();

    public static final String NAME_ID = CommandClassRegistry.nameOf(AddPictureCommand.class);

    public static Command fromParameters(CommandParameters p, CommandContext ctx) {
        ShapeGeometry geometry = new ShapeGeometry(
            p.get(X), p.get(Y), p.get(WIDTH), p.get(HEIGHT));
        BlipRef blipRef = new BlipRef(p.get(MEDIA_PART), p.opt(MIME).orElse(null), null);
        return new AddPictureCommand(p.get(SLIDE), blipRef, geometry,
            p.opt(NAME).orElse(null), ctx.orchestrator());
    }

    private final int slideNumber;
    private final BlipRef blipRef;
    private final ShapeGeometry geometry;
    private final String name;
    private final PPTXOrchestrator orchestrator;
    private boolean executed = false;
    private Integer createdSpid = null;

    public AddPictureCommand(int slideNumber, BlipRef blipRef,
            ShapeGeometry geometry, String name, PPTXOrchestrator orchestrator) {
        if (orchestrator == null) throw new IllegalArgumentException("orchestrator required");
        if (blipRef == null) throw new IllegalArgumentException("blipRef required");
        if (geometry == null) throw new IllegalArgumentException("geometry required");
        if (slideNumber <= 0) throw new IllegalArgumentException("slideNumber must be positive");
        this.slideNumber = slideNumber;
        this.blipRef = blipRef;
        this.geometry = geometry;
        this.name = name;
        this.orchestrator = orchestrator;
    }

    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute",
                "Command has already been executed");
        }
        ExecutionResult<Integer> result = orchestrator.addPictureShape(
            slideNumber, blipRef, geometry, name);
        if (!result.isSuccess()) {
            throw new CommandExecutionException(getDescription(), "execute",
                "Failed to add picture: " + result.getMessage());
        }
        createdSpid = result.getData().orElse(null);
        if (createdSpid == null) {
            throw new CommandExecutionException(getDescription(), "execute",
                "Add picture succeeded but no SPID was returned");
        }
        executed = true;
    }

    @Override
    public void undo() {
        if (!executed || !canUndo()) {
            throw new CommandExecutionException(getDescription(), "undo",
                "Cannot undo: not executed or no SPID recorded");
        }
        ExecutionResult<Void> r = orchestrator.removeShape(slideNumber, createdSpid);
        if (!r.isSuccess()) {
            throw new CommandExecutionException(getDescription(), "undo",
                "Failed to remove picture SPID " + createdSpid + ": " + r.getMessage());
        }
        executed = false;
        createdSpid = null;
    }

    @Override
    public boolean canUndo() { return executed && createdSpid != null; }

    @Override
    public boolean isExecuted() { return executed; }

    @Override
    public String getDescription() {
        return "AddPicture(slide=" + slideNumber
            + ", media=" + blipRef.mediaPartName()
            + ", geom=" + geometry + ")";
    }

    public Integer getCreatedSpid() { return createdSpid; }
}
