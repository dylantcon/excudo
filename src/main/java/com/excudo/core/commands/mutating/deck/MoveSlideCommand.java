package com.excudo.core.commands.mutating.deck;

import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandClassRegistry;
import com.excudo.core.commands.CommandContext;
import com.excudo.core.commands.CommandExecutionException;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.parsing.CommandParameters;
import com.excudo.core.parsing.CommandSchema;
import com.excudo.core.parsing.Parameter;
import com.excudo.core.results.SlideExecutionResult;

/**
 * Command for moving a slide from one position to another.
 *
 * <p>Self-registers via {@link com.excudo.core.commands.CommandClassRegistry}:
 * the canonical name {@code move-slide} derives from the class name.
 */
public class MoveSlideCommand implements Command {

    static final Parameter<Integer> FROM = Parameter.ofInt("from")
        .slideNumber().description("Current slide position").required().build();
    static final Parameter<Integer> TO = Parameter.ofInt("to")
        .type(Parameter.ParameterType.INTEGER).description("Target position").required().build();

    public static final CommandSchema SCHEMA = CommandSchema.builder()
        .description("Move a slide to a new position")
        .parameter(FROM)
        .parameter(TO)
        .example("move-slide 3 1")
        .example("move-slide 5 2")
        .build();

    public static final String NAME = CommandClassRegistry.nameOf(MoveSlideCommand.class);

    public static Command fromParameters(CommandParameters p, CommandContext ctx) {
        return new MoveSlideCommand(p.get(FROM), p.get(TO), ctx.orchestrator());
    }

    private final int fromPosition;
    private final int toPosition;
    private final PPTXOrchestrator orchestrator;
    private boolean executed = false;

    public MoveSlideCommand(int fromPosition, int toPosition, PPTXOrchestrator orchestrator) {
        if (orchestrator == null) {
            throw new IllegalArgumentException("PPTXOrchestrator cannot be null");
        }
        this.fromPosition = fromPosition;
        this.toPosition = toPosition;
        this.orchestrator = orchestrator;
    }

    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute", "Already executed");
        }
        SlideExecutionResult result = orchestrator.moveSlide(fromPosition, toPosition);
        if (!result.isSuccess()) {
            throw new CommandExecutionException(getDescription(), "execute", result.getMessage());
        }
        executed = true;
    }

    @Override
    public void undo() {
        if (!executed) {
            throw new CommandExecutionException(getDescription(), "undo", "Not yet executed");
        }
        // Reverse the move
        SlideExecutionResult result = orchestrator.moveSlide(toPosition, fromPosition);
        if (!result.isSuccess()) {
            throw new CommandExecutionException(getDescription(), "undo", result.getMessage());
        }
        executed = false;
    }

    @Override
    public boolean canUndo() { return executed; }

    @Override
    public boolean isExecuted() { return executed; }

    @Override
    public String getDescription() {
        return "Move slide " + fromPosition + " to position " + toPosition;
    }
}
