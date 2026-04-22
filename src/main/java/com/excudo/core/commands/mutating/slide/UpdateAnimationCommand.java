package com.excudo.core.commands.mutating.slide;

import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandExecutionException;

import com.excudo.core.orchestration.PPTXOrchestrator;
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
 */
public class UpdateAnimationCommand implements Command {

    private static final ComponentLogger logger = Logger.animation();

    private final int slideNumber;
    private final int timingNodeId;
    private final String property;
    private final String newValue;
    private final PPTXOrchestrator orchestrator;
    private boolean executed = false;

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

            ExecutionResult<Void> result = orchestrator.updateAnimation(
                slideNumber, timingNodeId, Map.of(property, newValue));

            if (result.isSuccess()) {
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

    @Override
    public void undo() {
        // Undo requires reading the previous value before execute(), which needs
        // a getAnimationProperty() method in the orchestrator chain. Deferred to Phase 5.
        throw new CommandExecutionException(getDescription(), "undo",
            "Undo not yet supported for update-animation (requires pre-read of current value)");
    }

    @Override
    public boolean canUndo() {
        return false;
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
