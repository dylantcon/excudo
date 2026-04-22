package com.excudo.core.commands;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.results.ExecutionResult;

/**
 * Move an existing top-level shape into an existing group, preserving
 * its visual slide-space position via the group's inverse coordinate
 * transform. Structural DOM move plus coordinate rewrite.
 *
 * <p>v1 limitations: rotation on the group isn't factored in; nested
 * groups aren't supported. Throws cleanly rather than producing a
 * visually-wrong transform when either is detected upstream.
 */
public class AddToGroupCommand implements Command {

    private final int slideNumber;
    private final int groupSpid;
    private final int childSpid;
    private final PPTXOrchestrator orchestrator;
    private boolean executed = false;

    public AddToGroupCommand(int slideNumber, int groupSpid, int childSpid,
                             PPTXOrchestrator orchestrator) {
        if (orchestrator == null) {
            throw new IllegalArgumentException("PPTXOrchestrator cannot be null");
        }
        if (slideNumber <= 0 || groupSpid <= 0 || childSpid <= 0) {
            throw new IllegalArgumentException("slide / group / child must be positive");
        }
        if (groupSpid == childSpid) {
            throw new IllegalArgumentException("group and child SPIDs must differ");
        }
        this.slideNumber = slideNumber;
        this.groupSpid = groupSpid;
        this.childSpid = childSpid;
        this.orchestrator = orchestrator;
    }

    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute",
                "Command has already been executed");
        }
        ExecutionResult<Void> r = orchestrator.addToGroup(slideNumber, groupSpid, childSpid);
        if (!r.isSuccess()) {
            throw new CommandExecutionException(getDescription(), "execute", r.getMessage());
        }
        executed = true;
    }

    @Override
    public void undo() {
        if (!executed) {
            throw new CommandExecutionException(getDescription(), "undo",
                "Command has not been executed");
        }
        ExecutionResult<Void> r = orchestrator.detachFromGroup(slideNumber, childSpid);
        if (!r.isSuccess()) {
            throw new CommandExecutionException(getDescription(), "undo", r.getMessage());
        }
        executed = false;
    }

    @Override public boolean canUndo() { return executed; }
    @Override public boolean isExecuted() { return executed; }

    @Override
    public String getDescription() {
        return "AddToGroup(slide=" + slideNumber + ", group=" + groupSpid + ", child=" + childSpid + ")";
    }
}
