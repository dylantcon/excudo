package com.excudo.core.commands.mutating.slide;

import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandExecutionException;

import com.excudo.core.model.TransitionType;
import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.results.ExecutionResult;

/**
 * Command for setting a slide transition effect.
 */
public class SetTransitionCommand implements Command {

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
