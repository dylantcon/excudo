package com.excudo.core.commands.mutating.slide;

import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandClassRegistry;
import com.excudo.core.commands.CommandContext;
import com.excudo.core.commands.CommandExecutionException;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.ShapeRegistry;
import com.excudo.core.model.SlideShape;
import com.excudo.core.parsing.CommandParameters;
import com.excudo.core.parsing.CommandSchema;
import com.excudo.core.parsing.Parameter;
import com.excudo.core.results.ExecutionResult;

/**
 * GoF Command for resizing a shape on a slide.
 *
 * Captures the original geometry before resizing so that undo can restore
 * the shape to its previous dimensions. Position (x, y) is preserved.
 *
 * <p>Self-registers via {@link com.excudo.core.commands.CommandClassRegistry}:
 * the canonical name {@code resize-shape} derives from the class name.
 */
public class ResizeShapeCommand implements Command {

    static final Parameter<Integer> SLIDE = Parameter.ofInt("slide")
        .slideNumber().description("Slide number").llmName("slideNumber").required().build();
    static final Parameter<Integer> SPID = Parameter.ofInt("spid")
        .spid().description("Shape ID").llmName("targetSpid").required().build();
    static final Parameter<Long> WIDTH = Parameter.ofUnit("width")
        .description("New width (points, EMU, or inches)").required().build();
    static final Parameter<Long> HEIGHT = Parameter.ofUnit("height")
        .description("New height (points, EMU, or inches)").required().build();

    public static final CommandSchema SCHEMA = CommandSchema.builder()
        .description("Resize a shape")
        .llmEnabled(true)
        .llmDescription("Resize a shape.")
        .parameter(SLIDE)
        .parameter(SPID)
        .parameter(WIDTH)
        .parameter(HEIGHT)
        .example("resize-shape 1 5 400pt 300pt")
        .build();

    public static final String NAME = CommandClassRegistry.nameOf(ResizeShapeCommand.class);

    public static Command fromParameters(CommandParameters p, CommandContext ctx) {
        return new ResizeShapeCommand(p.get(SLIDE), p.get(SPID),
            p.get(WIDTH), p.get(HEIGHT), ctx.orchestrator());
    }

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
