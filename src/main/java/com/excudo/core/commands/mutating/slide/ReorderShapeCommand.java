package com.excudo.core.commands.mutating.slide;

import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandContext;
import com.excudo.core.commands.CommandExecutionException;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.parsing.CommandParameters;
import com.excudo.core.parsing.CommandSchema;
import com.excudo.core.parsing.Parameter;
import com.excudo.core.results.ExecutionResult;

/**
 * Self-registers via {@link com.excudo.core.commands.CommandClassRegistry}:
 * the canonical name {@code reorder-shape} derives from the class name.
 */
public class ReorderShapeCommand implements Command {

    static final Parameter<Integer> SLIDE = Parameter.ofInt("slide")
        .slideNumber().description("Slide number").llmName("slideNumber").required().build();
    static final Parameter<Integer> SPID = Parameter.ofInt("spid")
        .spid().description("Shape ID").llmName("targetSpid").required().build();
    // Custom parse: accepted tokens (front/back/forward/backward) differ from
    // the enum constant names, so validValues is declared explicitly for the
    // LLM JSON schema rather than auto-derived from the enum.
    static final Parameter<ZOrderOperation> DIRECTION =
        Parameter.of("direction", ZOrderOperation::parse)
            .description("Direction: front, back, forward, backward")
            .validValues("front", "back", "forward", "backward")
            .required().build();

    public static final CommandSchema SCHEMA = CommandSchema.builder()
        .description("Change z-order of a shape")
        .llmEnabled(true)
        .llmDescription("Change z-order of a shape.")
        .parameter(SLIDE)
        .parameter(SPID)
        .parameter(DIRECTION)
        .example("reorder-shape 1 5 front")
        .build();

    public static Command fromParameters(CommandParameters p, CommandContext ctx) {
        return new ReorderShapeCommand(p.get(SLIDE), p.get(SPID), p.get(DIRECTION), ctx.orchestrator());
    }

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
