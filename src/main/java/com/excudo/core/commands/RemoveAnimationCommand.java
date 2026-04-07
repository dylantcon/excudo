package com.excudo.core.commands;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.results.ExecutionResult;
import com.excudo.core.utils.ComponentLogger;
import com.excudo.core.utils.Logger;

/**
 * GoF Command for removing animations from slides by timing node ID.
 *
 * Delegates to PPTXOrchestrator.removeAnimation() which is fully implemented
 * through AnimationOrchestrationManager and AnimationInjector.
 */
public class RemoveAnimationCommand implements Command {

    private static final ComponentLogger logger = Logger.animation();

    private final int slideNumber;
    private final int timingNodeId;
    private final PPTXOrchestrator orchestrator;
    private boolean executed = false;

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

            ExecutionResult<Void> result = orchestrator.removeAnimation(slideNumber, timingNodeId);

            if (result.isSuccess()) {
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

    @Override
    public void undo() {
        throw new CommandExecutionException(getDescription(), "undo",
            "Undo not supported for remove-animation (requires XML snapshot)");
    }

    @Override
    public boolean canUndo() {
        return false;
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
