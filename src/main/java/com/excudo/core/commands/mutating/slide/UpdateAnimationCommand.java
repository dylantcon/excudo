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

import java.util.Map;

/**
 * GoF Command for updating animation properties on a slide.
 *
 * Supports undo by capturing the previous value before applying changes.
 * Delegates to PPTXOrchestrator.updateAnimation() which is fully implemented
 * through AnimationOrchestrationManager and AnimationInjector.
 *
 * <p>Self-registers via {@link com.excudo.core.commands.CommandClassRegistry}:
 * the canonical name {@code update-animation} derives from the class name.
 */
public class UpdateAnimationCommand implements Command {

    private static final ComponentLogger logger = Logger.animation();

    static final Parameter<Integer> SLIDE = Parameter.ofInt("slide")
        .slideNumber().description("Slide number").llmName("slideNumber").required().build();
    static final Parameter<Integer> TIMING_NODE_ID = Parameter.ofInt("timingNodeId")
        .description("Timing node ID (cTn id) of the animation to update: the 'id' column "
            + "of get_slide_animations / list-animations").required().build();
    static final Parameter<String> PROPERTY = Parameter.ofString("property")
        .description("Property to update: duration (ms), delay (ms), transparency "
            + "(percent see-through, transparency emphasis only), presetSubtype")
        .validValues("duration", "delay", "transparency", "presetSubtype")
        .required().build();
    static final Parameter<String> VALUE = Parameter.ofString("value")
        .description("New value for the property").required().build();

    public static final CommandSchema SCHEMA = CommandSchema.builder()
        .description("Update properties of an existing animation")
        .llmEnabled(true)
        .llmDescription("Edit one existing animation in place: its duration, delay, or (for "
            + "the transparency emphasis) how transparent it makes the shape. Take the id from "
            + "get_slide_animations. To change the effect type or trigger, remove-animation "
            + "and add-animation instead.")
        .parameter(SLIDE)
        .parameter(TIMING_NODE_ID)
        .parameter(PROPERTY)
        .parameter(VALUE)
        .example("update-animation 1 15 duration 1000")
        .example("update-animation 1 15 transparency 50")
        .example("update-animation --slide 1 --timingNodeId 15 --property duration --value 1000")
        .build();

    public static final String NAME = CommandClassRegistry.nameOf(UpdateAnimationCommand.class);

    public static Command fromParameters(CommandParameters p, CommandContext ctx) {
        return new UpdateAnimationCommand(p.get(SLIDE), p.get(TIMING_NODE_ID),
            p.get(PROPERTY), p.get(VALUE), ctx.orchestrator());
    }

    private final int slideNumber;
    private final int timingNodeId;
    private final String property;
    private final String newValue;
    private final PPTXOrchestrator orchestrator;
    private boolean executed = false;
    /** The property's value before execute, when it can be read back; enables undo. */
    private String previousValue = null;

    public UpdateAnimationCommand(int slideNumber, int timingNodeId, String property,
                                  String newValue, PPTXOrchestrator orchestrator) {
        if (orchestrator == null) {
            throw new IllegalArgumentException("PPTXOrchestrator cannot be null");
        }
        if (slideNumber < 1) {
            throw new IllegalArgumentException("Slide number must be positive");
        }
        if (timingNodeId < 1) {
            throw new IllegalArgumentException("Timing node ID must be positive");
        }
        if (property == null || property.trim().isEmpty()) {
            throw new IllegalArgumentException("Property cannot be null or empty");
        }
        if (newValue == null) {
            throw new IllegalArgumentException("Value cannot be null");
        }
        this.slideNumber = slideNumber;
        this.timingNodeId = timingNodeId;
        this.property = property.trim();
        this.newValue = newValue;
        this.orchestrator = orchestrator;
    }

    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute", "Command has already been executed");
        }

        try {
            logger.debug("Updating animation: slide " + slideNumber + ", timingNodeId " + timingNodeId
                + ", property=" + property + ", value=" + newValue);

            String before = readCurrentValue();
            ExecutionResult<Void> result = orchestrator.updateAnimation(
                slideNumber, timingNodeId, Map.of(property, newValue));

            if (result.isSuccess()) {
                previousValue = before;
                executed = true;
                logger.info("Updated animation (timingNodeId=" + timingNodeId + ") on slide " + slideNumber
                    + ": " + property + "=" + newValue);
            } else {
                throw new CommandExecutionException(
                    getDescription(), "execute",
                    "Failed to update animation: " + result.getMessage()
                );
            }

        } catch (CommandExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new CommandExecutionException(
                getDescription(), "execute",
                "Failed to update animation: " + e.getMessage(), e
            );
        }
    }

    /**
     * Current value of the property, read from the parsed binding, or null
     * when it can't be read (presetSubtype isn't carried on the binding).
     */
    private String readCurrentValue() {
        try {
            for (AnimationBinding b : new SlideIntrospector(orchestrator).listAnimations(slideNumber)) {
                if (b.getTimingNodeId() != timingNodeId) continue;
                return switch (property) {
                    case "duration" -> b.getDuration();
                    case "delay" -> b.getDelay();
                    case "transparency" -> b.getEffectParam(AnimationBinding.PARAM_TRANSPARENCY, "25");
                    default -> null;
                };
            }
        } catch (Exception e) {
            logger.debug("Could not read animation " + timingNodeId + " for undo: " + e.getMessage());
        }
        return null;
    }

    @Override
    public void undo() {
        if (!canUndo()) {
            throw new CommandExecutionException(getDescription(), "undo",
                "Undo not available: the previous " + property + " could not be read");
        }
        ExecutionResult<Void> result = orchestrator.updateAnimation(
            slideNumber, timingNodeId, Map.of(property, previousValue));
        if (!result.isSuccess()) {
            throw new CommandExecutionException(getDescription(), "undo",
                "Failed to restore " + property + ": " + result.getMessage());
        }
        executed = false;
        previousValue = null;
    }

    @Override
    public boolean canUndo() {
        return executed && previousValue != null;
    }

    @Override
    public String getDescription() {
        return String.format("Update animation (timingNodeId=%d) on slide %d: %s=%s",
            timingNodeId, slideNumber, property, newValue);
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

    public String getProperty() {
        return property;
    }

    public String getNewValue() {
        return newValue;
    }
}
