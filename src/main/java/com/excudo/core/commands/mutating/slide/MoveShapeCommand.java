package com.excudo.core.commands.mutating.slide;

import com.excudo.core.commands.Command;
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
 * GoF Command for moving a shape to a new position on a slide.
 *
 * Captures the original geometry before moving so that undo can restore
 * the shape to its previous position. Width and height are preserved.
 *
 * <p>Self-registers via {@link com.excudo.core.commands.CommandClassRegistry}:
 * the canonical name {@code move-shape} derives from the class name.
 */
public class MoveShapeCommand implements Command {

    static final Parameter<Integer> SLIDE = Parameter.ofInt("slide")
        .slideNumber().description("Slide number").llmName("slideNumber").required().build();
    static final Parameter<Integer> SPID = Parameter.ofInt("spid")
        .spid().description("Shape ID").llmName("targetSpid").required().build();
    static final Parameter<Long> X = Parameter.ofUnit("x")
        .description("New X position (points, EMU, or inches: 100pt, 1270000emu, 1.5in)").required().build();
    static final Parameter<Long> Y = Parameter.ofUnit("y")
        .description("New Y position (points, EMU, or inches: 100pt, 1270000emu, 1.5in)").required().build();

    public static final CommandSchema SCHEMA = CommandSchema.builder()
        .description("Move a shape to a new position")
        .llmEnabled(true)
        .llmDescription("Move a shape to a position.")
        .parameter(SLIDE)
        .parameter(SPID)
        .parameter(X)
        .parameter(Y)
        .example("move-shape 1 5 100pt 200pt")
        .build();

    public static Command fromParameters(CommandParameters p, CommandContext ctx) {
        return new MoveShapeCommand(p.get(SLIDE), p.get(SPID), p.get(X), p.get(Y), ctx.orchestrator());
    }

    private final int slideNumber;
    private final int spid;
    private final long newX;
    private final long newY;
    private final PPTXOrchestrator orchestrator;
    private boolean executed = false;
    private ShapeGeometry originalGeometry = null;

    public MoveShapeCommand(int slideNumber, int spid, long newX, long newY, PPTXOrchestrator orchestrator) {
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
        this.newX = newX;
        this.newY = newY;
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

        ShapeGeometry newGeometry = new ShapeGeometry(newX, newY,
            originalGeometry.getWidth(), originalGeometry.getHeight(),
            originalGeometry.getRotation());

        ExecutionResult<Void> moveResult = orchestrator.updateShapeGeometry(slideNumber, spid, newGeometry);
        if (!moveResult.isSuccess()) {
            originalGeometry = null;
            throw new CommandExecutionException(getDescription(), "execute",
                "Failed to move shape: " + moveResult.getMessage());
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
                "Failed to restore shape position: " + restoreResult.getMessage());
        }

        executed = false;
    }

    @Override
    public boolean canUndo() {
        return executed && originalGeometry != null;
    }

    @Override
    public String getDescription() {
        return "Move shape SPID " + spid + " on slide " + slideNumber + " to (" + newX + ", " + newY + ")";
    }

    @Override
    public boolean isExecuted() {
        return executed;
    }

    public int getSlideNumber() { return slideNumber; }
    public int getSpid() { return spid; }
}
