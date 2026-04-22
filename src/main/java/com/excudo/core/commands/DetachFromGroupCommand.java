package com.excudo.core.commands;

import com.excudo.core.model.ShapeRegistry;
import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.results.ExecutionResult;

/**
 * Move a grouped child shape out to the top-level {@code spTree},
 * preserving its slide-space visual position via the group's forward
 * coordinate transform.
 *
 * <p>Snapshot-based undo: captures the parent group's SPID at execute
 * time so undo re-groups the child cleanly.
 */
public class DetachFromGroupCommand implements Command {

    private final int slideNumber;
    private final int childSpid;
    private final PPTXOrchestrator orchestrator;
    private boolean executed = false;
    private Integer originalGroupSpid = null;

    public DetachFromGroupCommand(int slideNumber, int childSpid, PPTXOrchestrator orchestrator) {
        if (orchestrator == null) {
            throw new IllegalArgumentException("PPTXOrchestrator cannot be null");
        }
        if (slideNumber <= 0 || childSpid <= 0) {
            throw new IllegalArgumentException("slide / child must be positive");
        }
        this.slideNumber = slideNumber;
        this.childSpid = childSpid;
        this.orchestrator = orchestrator;
    }

    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute",
                "Command has already been executed");
        }
        ExecutionResult<ShapeRegistry> reg = orchestrator.getShapeRegistry(slideNumber);
        if (!reg.isSuccess()) {
            throw new CommandExecutionException(getDescription(), "execute",
                "Failed to get shape registry: " + reg.getMessage());
        }
        int parent = reg.getData().orElseThrow().getParentSpid(childSpid);
        if (parent <= 0) {
            throw new CommandExecutionException(getDescription(), "execute",
                "Shape SPID " + childSpid + " is already at top level");
        }
        originalGroupSpid = parent;

        ExecutionResult<Void> r = orchestrator.detachFromGroup(slideNumber, childSpid);
        if (!r.isSuccess()) {
            originalGroupSpid = null;
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
        if (originalGroupSpid == null) {
            throw new CommandExecutionException(getDescription(), "undo",
                "No captured original group for undo");
        }
        ExecutionResult<Void> r = orchestrator.addToGroup(slideNumber, originalGroupSpid, childSpid);
        if (!r.isSuccess()) {
            throw new CommandExecutionException(getDescription(), "undo", r.getMessage());
        }
        executed = false;
    }

    @Override public boolean canUndo() { return executed && originalGroupSpid != null; }
    @Override public boolean isExecuted() { return executed; }

    @Override
    public String getDescription() {
        return "DetachFromGroup(slide=" + slideNumber + ", child=" + childSpid + ")";
    }
}
