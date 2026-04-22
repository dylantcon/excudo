package com.excudo.core.commands.mutating.deck;

import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandExecutionException;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.results.SlideExecutionResult;

/**
 * Command for moving a slide from one position to another.
 */
public class MoveSlideCommand implements Command {

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
