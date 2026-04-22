package com.excudo.core.commands.mutating.slide;

import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandExecutionException;

import com.excudo.core.model.TextRun;
import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.results.ExecutionResult;

/**
 * Update the formatting of a specific text run inside a shape in
 * place. Scoped by (paragraph index, run index). Synthesizer emits
 * this in place of a full {@code SetTextSpec} when the only difference
 * between two text bodies is per-run formatting -- so the script
 * touches exactly the runs that changed.
 *
 * <p>Snapshot-based undo is intentionally not implemented yet (same
 * rationale as {@link UpdateAnimationTimingCommand}): the synthesizer's
 * rollback path lives at the script-runner level and walks the
 * composite's executed list.
 */
public class UpdateRunFormatCommand implements Command {

    private final int slideNumber;
    private final int spid;
    private final int paragraphIdx;
    private final int runIdx;
    private final TextRun newRun;
    private final PPTXOrchestrator orchestrator;
    private boolean executed = false;

    public UpdateRunFormatCommand(int slideNumber, int spid, int paragraphIdx, int runIdx,
                                  TextRun newRun, PPTXOrchestrator orchestrator) {
        if (orchestrator == null) {
            throw new IllegalArgumentException("PPTXOrchestrator cannot be null");
        }
        if (slideNumber <= 0) {
            throw new IllegalArgumentException("Slide number must be positive");
        }
        if (spid <= 0) {
            throw new IllegalArgumentException("SPID must be positive");
        }
        if (paragraphIdx < 0 || runIdx < 0) {
            throw new IllegalArgumentException("paragraph + run indices must be non-negative");
        }
        if (newRun == null) {
            throw new IllegalArgumentException("newRun must not be null");
        }
        this.slideNumber = slideNumber;
        this.spid = spid;
        this.paragraphIdx = paragraphIdx;
        this.runIdx = runIdx;
        this.newRun = newRun;
        this.orchestrator = orchestrator;
    }

    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute",
                "Command has already been executed");
        }
        ExecutionResult<Void> r = orchestrator.updateRunFormat(
            slideNumber, spid, paragraphIdx, runIdx, newRun);
        if (!r.isSuccess()) {
            throw new CommandExecutionException(getDescription(), "execute", r.getMessage());
        }
        executed = true;
    }

    @Override public void undo() {
        throw new CommandExecutionException(getDescription(), "undo",
            "Undo not yet supported for per-run format updates");
    }

    @Override public boolean canUndo() { return false; }
    @Override public boolean isExecuted() { return executed; }

    @Override
    public String getDescription() {
        return "UpdateRunFormat(slide=" + slideNumber + ", spid=" + spid
            + ", pIdx=" + paragraphIdx + ", rIdx=" + runIdx + ")";
    }
}
