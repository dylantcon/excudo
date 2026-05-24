package com.excudo.core.commands.mutating.slide;

import com.excudo.core.commands.meta.UndoCommand;
import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandExecutionException;

import com.excudo.core.introspection.TransitionDescriptor;
import com.excudo.core.introspection.TransitionReader;
import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.results.ExecutionResult;

/**
 * Remove any slide-level {@code <p:transition>} override from a slide,
 * causing it to fall back to its layout / master transition (if any).
 *
 * <p>Distinct from {@link SetTransitionCommand} with
 * {@code TransitionType.NONE}: that path writes an empty
 * {@code <p:transition/>} element which <b>overrides</b> inheritance
 * with "explicitly no transition." Clearing removes the element
 * entirely so inheritance can resume.
 *
 * <p>Snapshot-based undo: reads the current slide-level transition (if
 * any) before clearing, then re-installs it on undo. If the slide had
 * no slide-level override to begin with, undo is a no-op (which is
 * semantically correct -- the post-clear state was already the
 * pre-execute state).
 */
public class ClearTransitionCommand implements Command {

    private final int slideNumber;
    private final PPTXOrchestrator orchestrator;
    private boolean executed = false;
    private TransitionDescriptor snapshot = null;
    private boolean hadSlideLevelOverride = false;

    public ClearTransitionCommand(int slideNumber, PPTXOrchestrator orchestrator) {
        if (orchestrator == null) {
            throw new IllegalArgumentException("PPTXOrchestrator cannot be null");
        }
        if (slideNumber <= 0) {
            throw new IllegalArgumentException("Slide number must be positive");
        }
        this.slideNumber = slideNumber;
        this.orchestrator = orchestrator;
    }

    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute",
                "Already executed");
        }
        TransitionDescriptor current = new TransitionReader(orchestrator).read(slideNumber);
        if (current != null && current.source() == TransitionDescriptor.Source.SLIDE) {
            snapshot = current;
            hadSlideLevelOverride = true;
        } else {
            snapshot = null;
            hadSlideLevelOverride = false;
        }
        ExecutionResult<Void> result = orchestrator.removeTransition(slideNumber);
        if (!result.isSuccess()) {
            snapshot = null;
            hadSlideLevelOverride = false;
            throw new CommandExecutionException(getDescription(), "execute", result.getMessage());
        }
        executed = true;
    }

    @Override
    public void undo() {
        if (!executed) {
            throw new CommandExecutionException(getDescription(), UndoCommand.NAME,
                "Command has not been executed");
        }
        if (hadSlideLevelOverride && snapshot != null) {
            ExecutionResult<Void> result = orchestrator.setTransition(slideNumber,
                snapshot.type(), snapshot.speed(), snapshot.autoAdvanceMs());
            if (!result.isSuccess()) {
                throw new CommandExecutionException(getDescription(), UndoCommand.NAME, result.getMessage());
            }
        }
        executed = false;
        snapshot = null;
        hadSlideLevelOverride = false;
    }

    @Override
    public boolean canUndo() { return executed; }

    @Override
    public boolean isExecuted() { return executed; }

    @Override
    public String getDescription() {
        return "Clear slide-level transition on slide " + slideNumber;
    }
}
