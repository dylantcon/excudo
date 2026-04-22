package com.excudo.core.commands.mutating.slide;

import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandExecutionException;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.results.ExecutionResult;

public class ReorderShapeCommand implements Command {

    public enum ZOrderOperation {
        BRING_FRONT, SEND_BACK, BRING_FORWARD, SEND_BACKWARD;

        public static ZOrderOperation parse(String s) {
            switch (s.toLowerCase()) {
                case "front": return BRING_FRONT;
                case "back": return SEND_BACK;
                case "forward": return BRING_FORWARD;
                case "backward": return SEND_BACKWARD;
                default: throw new IllegalArgumentException(
                    "Unknown z-order direction: " + s + " (valid: front, back, forward, backward)");
            }
        }

        public ZOrderOperation inverse() {
            switch (this) {
                case BRING_FRONT: return SEND_BACK;
                case SEND_BACK: return BRING_FRONT;
                case BRING_FORWARD: return SEND_BACKWARD;
                case SEND_BACKWARD: return BRING_FORWARD;
                default: throw new IllegalStateException();
            }
        }
    }

    private final int slideNumber;
    private final int spid;
    private final ZOrderOperation operation;
    private final PPTXOrchestrator orchestrator;
    private boolean executed = false;

    public ReorderShapeCommand(int slideNumber, int spid, ZOrderOperation operation,
                                PPTXOrchestrator orchestrator) {
        if (orchestrator == null) throw new IllegalArgumentException("PPTXOrchestrator cannot be null");
        this.slideNumber = slideNumber;
        this.spid = spid;
        this.operation = operation;
        this.orchestrator = orchestrator;
    }

    @Override
    public void execute() {
        if (executed) throw new CommandExecutionException(getDescription(), "execute", "Already executed");

        ExecutionResult<Void> result = orchestrator.reorderShape(slideNumber, spid, operation.name());
        if (!result.isSuccess()) {
            throw new CommandExecutionException(getDescription(), "execute",
                "Failed to reorder shape: " + result.getMessage());
        }
        executed = true;
    }

    @Override
    public void undo() {
        if (!executed) throw new CommandExecutionException(getDescription(), "undo", "Not executed");

        ExecutionResult<Void> result = orchestrator.reorderShape(slideNumber, spid, operation.inverse().name());
        if (!result.isSuccess()) {
            throw new CommandExecutionException(getDescription(), "undo",
                "Failed to undo reorder: " + result.getMessage());
        }
        executed = false;
    }

    @Override
    public boolean canUndo() {
        return executed;
    }

    @Override
    public String getDescription() {
        return "Reorder shape SPID " + spid + " " + operation.name() + " on slide " + slideNumber;
    }

    @Override
    public boolean isExecuted() {
        return executed;
    }

    public int getSlideNumber() { return slideNumber; }
    public int getSpid() { return spid; }
}
