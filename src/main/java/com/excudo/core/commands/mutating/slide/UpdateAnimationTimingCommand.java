package com.excudo.core.commands.mutating.slide;

import com.excudo.core.commands.meta.UndoCommand;
import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandExecutionException;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.results.ExecutionResult;

import java.util.HashMap;
import java.util.Map;

/**
 * Update the duration / delay attributes of an existing animation in
 * place, scoped by its {@code cTn} id. Uses
 * {@link PPTXOrchestrator#updateAnimation(int, int, Map)} under the
 * hood; no full remove+add, which would churn the timing tree and
 * renumber unrelated nodes.
 *
 * <p>Snapshot-based undo: the command doesn't currently snapshot the
 * previous duration/delay because the orchestrator's update path
 * doesn't expose a "read current value" primitive at this layer. If
 * round-trip undo becomes a hard requirement we'll add a lightweight
 * read here; the synthesizer itself never needs undo on this command
 * because the whole script rolls back through the runner's executed
 * list rather than per-command.
 */
public class UpdateAnimationTimingCommand implements Command {

    private final int slideNumber;
    private final int timingNodeId;
    private final String newDuration;
    private final String newDelay;
    private final PPTXOrchestrator orchestrator;
    private boolean executed = false;

    public UpdateAnimationTimingCommand(int slideNumber, int timingNodeId,
                                        String newDuration, String newDelay,
                                        PPTXOrchestrator orchestrator) {
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
        this.newDuration = newDuration;
        this.newDelay = newDelay;
        this.orchestrator = orchestrator;
    }

    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute",
                "Command has already been executed");
        }
        Map<String, String> props = new HashMap<>();
        if (newDuration != null) props.put("duration", newDuration);
        if (newDelay != null)    props.put("delay", newDelay);
        if (props.isEmpty()) {
            // No-op: nothing to update. Mark executed so the runner's
            // post-execute logic doesn't treat this as a failure.
            executed = true;
            return;
        }
        ExecutionResult<Void> r = orchestrator.updateAnimation(slideNumber, timingNodeId, props);
        if (!r.isSuccess()) {
            throw new CommandExecutionException(getDescription(), "execute", r.getMessage());
        }
        executed = true;
    }

    @Override public void undo() {
        throw new CommandExecutionException(getDescription(), UndoCommand.NAME,
            "Undo not yet supported for animation timing update");
    }

    @Override public boolean canUndo() { return false; }
    @Override public boolean isExecuted() { return executed; }

    @Override
    public String getDescription() {
        return "UpdateAnimationTiming(slide=" + slideNumber + ", timingNodeId="
            + timingNodeId + ", duration=" + newDuration + ", delay=" + newDelay + ")";
    }
}
