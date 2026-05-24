package com.excudo.core.commands.mutating.slide;

import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandContext;
import com.excudo.core.commands.CommandExecutionException;

import com.excudo.core.model.TransitionType;
import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.parsing.CommandParameters;
import com.excudo.core.parsing.CommandSchema;
import com.excudo.core.parsing.Parameter;
import com.excudo.core.results.ExecutionResult;

/**
 * Command for setting a slide transition effect.
 *
 * <p>Self-registers via {@link com.excudo.core.commands.CommandClassRegistry}:
 * the canonical name {@code set-transition} derives from the class name.
 */
public class SetTransitionCommand implements Command {

    static final Parameter<Integer> SLIDE = Parameter.ofInt("slide")
        .slideNumber().description("Slide number").llmName("slideNumber").required().build();
    // Custom parser: TransitionType.parseType accepts fuzzy tokens (fade,
    // wipe-left, push-down, dissolve, ...) -- folded into the key so the
    // factory body stays pure construction.
    static final Parameter<TransitionType> TYPE = Parameter.of("type", TransitionType::parseType)
        .description("Transition type (fade, wipe-left, push-down, dissolve, etc.)")
        .required().build();
    static final Parameter<String> SPEED = Parameter.ofString("speed")
        .description("Transition speed")
        .validValues("slow", "med", "fast")
        .required(false).build();
    static final Parameter<Integer> ADVANCE = Parameter.ofInt("advance")
        .description("Auto-advance time in milliseconds (e.g., 5000 = 5 seconds)")
        .llmName("advanceMs")
        .required(false).build();

    public static final CommandSchema SCHEMA = CommandSchema.builder()
        .description("Set a slide transition effect")
        .llmEnabled(true)
        .llmDescription("Set slide transition effect.")
        .parameter(SLIDE)
        .parameter(TYPE)
        .parameter(SPEED)
        .parameter(ADVANCE)
        .example("set-transition 1 fade")
        .example("set-transition 3 wipe-left --speed slow --advance 5000")
        .build();

    public static Command fromParameters(CommandParameters p, CommandContext ctx) {
        return new SetTransitionCommand(p.get(SLIDE), p.get(TYPE),
            p.opt(SPEED).orElse(null), p.opt(ADVANCE).orElse(null), ctx.orchestrator());
    }

    private final int slideNumber;
    private final TransitionType transitionType;
    private final String speed;
    private final Integer advanceTimeMs;
    private final PPTXOrchestrator orchestrator;
    private boolean executed = false;

    public SetTransitionCommand(int slideNumber, TransitionType transitionType,
                                String speed, Integer advanceTimeMs,
                                PPTXOrchestrator orchestrator) {
        if (orchestrator == null) {
            throw new IllegalArgumentException("PPTXOrchestrator cannot be null");
        }
        this.slideNumber = slideNumber;
        this.transitionType = transitionType;
        this.speed = speed;
        this.advanceTimeMs = advanceTimeMs;
        this.orchestrator = orchestrator;
    }

    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute", "Already executed");
        }
        ExecutionResult<Void> result = orchestrator.setTransition(slideNumber, transitionType, speed, advanceTimeMs);
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
        ExecutionResult<Void> result = orchestrator.removeTransition(slideNumber);
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
        return "Set " + transitionType.getUserFriendlyName() + " transition on slide " + slideNumber;
    }
}
