package com.excudo.core.commands.mutating.slide;

import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandClassRegistry;
import com.excudo.core.commands.CommandContext;
import com.excudo.core.commands.CommandExecutionException;

import com.excudo.core.introspection.SlideIntrospector;
import com.excudo.core.model.AnimationBinding;
import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.parsing.CommandParameters;
import com.excudo.core.parsing.CommandSchema;
import com.excudo.core.parsing.Parameter;
import com.excudo.core.results.ExecutionResult;
import com.excudo.core.utils.ComponentLogger;
import com.excudo.core.utils.Logger;

/**
 * GoF Command for removing animations from slides by timing node ID.
 *
 * Delegates to PPTXOrchestrator.removeAnimation() which is fully implemented
 * through AnimationOrchestrationManager and AnimationInjector.
 *
 * <p>Self-registers via {@link com.excudo.core.commands.CommandClassRegistry}:
 * the canonical name {@code remove-animation} derives from the class name.
 */
public class RemoveAnimationCommand implements Command {

    private static final ComponentLogger logger = Logger.animation();

    static final Parameter<Integer> SLIDE = Parameter.ofInt("slide")
        .slideNumber().description("Slide number").llmName("slideNumber").required().build();
    static final Parameter<Integer> TIMING_NODE_ID = Parameter.ofInt("timingNodeId")
        .description("Timing node ID (cTn id) of the animation to remove: the 'id' column "
            + "of get_slide_animations / list-animations").required().build();

    public static final CommandSchema SCHEMA = CommandSchema.builder()
        .description("Remove an animation from a slide by timing node ID")
        .llmEnabled(true)
        .llmDescription("Remove one animation from a slide, leaving the shape and its other "
            + "animations alone. Take the id from get_slide_animations. Undo re-adds the "
            + "effect at the end of the slide's sequence.")
        .parameter(SLIDE)
        .parameter(TIMING_NODE_ID)
        .example("remove-animation 1 15")
        .example("remove-animation --slide 1 --timingNodeId 15")
        .build();

    public static final String NAME = CommandClassRegistry.nameOf(RemoveAnimationCommand.class);

    public static Command fromParameters(CommandParameters p, CommandContext ctx) {
        return new RemoveAnimationCommand(p.get(SLIDE), p.get(TIMING_NODE_ID), ctx.orchestrator());
    }

    private final int slideNumber;
    private final int timingNodeId;
    private final PPTXOrchestrator orchestrator;
    private boolean executed = false;
    /** The removed effect, captured before removal so undo can re-add it. */
    private AnimationBinding removedBinding = null;

    public RemoveAnimationCommand(int slideNumber, int timingNodeId, PPTXOrchestrator orchestrator) {
        if (orchestrator == null) {
            throw new IllegalArgumentException("PPTXOrchestrator cannot be null");
        }
        if (slideNumber < 1) {
            throw new IllegalArgumentException("Slide number must be positive");
        }
        if (timingNodeId < 1) {
            throw new IllegalArgumentException("Timing node ID must be positive");
        }
        this.slideNumber = slideNumber;
        this.timingNodeId = timingNodeId;
        this.orchestrator = orchestrator;
    }

    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute", "Command has already been executed");
        }

        try {
            logger.debug("Removing animation: slide " + slideNumber + ", timingNodeId " + timingNodeId);
            AnimationBinding before = findBinding();

            ExecutionResult<Void> result = orchestrator.removeAnimation(slideNumber, timingNodeId);

            if (result.isSuccess()) {
                removedBinding = before;
                executed = true;
                logger.info("Removed animation (timingNodeId=" + timingNodeId + ") from slide " + slideNumber);
            } else {
                throw new CommandExecutionException(
                    getDescription(), "execute",
                    "Failed to remove animation: " + result.getMessage()
                );
            }

        } catch (CommandExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new CommandExecutionException(
                getDescription(), "execute",
                "Failed to remove animation: " + e.getMessage(), e
            );
        }
    }

    /** The binding with this command's timing node id, or null if it can't be read. */
    private AnimationBinding findBinding() {
        try {
            for (AnimationBinding b : new SlideIntrospector(orchestrator).listAnimations(slideNumber)) {
                if (b.getTimingNodeId() == timingNodeId) return b;
            }
        } catch (Exception e) {
            logger.debug("Could not snapshot animation " + timingNodeId + " for undo: " + e.getMessage());
        }
        return null;
    }

    /**
     * Re-add the removed effect (same shape, type, trigger, parameters and
     * duration). It goes to the end of the slide's sequence and gets a new id.
     */
    @Override
    public void undo() {
        if (!canUndo()) {
            throw new CommandExecutionException(getDescription(), "undo",
                "Undo not available: the removed animation could not be captured");
        }
        AnimationBinding.Builder restore = AnimationBinding.builder(removedBinding);
        String dur = removedBinding.getDuration();
        if (dur != null && !dur.isEmpty() && dur.chars().allMatch(Character::isDigit)
                && removedBinding.getEffectParam(AnimationBinding.PARAM_DURATION_MS) == null) {
            restore.effectParam(AnimationBinding.PARAM_DURATION_MS, dur);
        }
        AddAnimationCommand.fromBinding(slideNumber, restore.build(), orchestrator, null).execute();
        executed = false;
        removedBinding = null;
    }

    @Override
    public boolean canUndo() {
        return executed && removedBinding != null;
    }

    @Override
    public String getDescription() {
        return String.format("Remove animation (timingNodeId=%d) from slide %d", timingNodeId, slideNumber);
    }

    @Override
    public boolean isExecuted() {
        return executed;
    }

    public int getSlideNumber() {
        return slideNumber;
    }

    public int getTimingNodeId() {
        return timingNodeId;
    }
}
