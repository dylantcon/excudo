package com.excudo.core.commands.mutating.slide;

import com.excudo.core.commands.meta.UndoCommand;
import com.excudo.core.commands.Command;
import com.excudo.core.parsing.Parameter;
import com.excudo.core.parsing.CommandSchema;
import com.excudo.core.parsing.CommandParameters;
import com.excudo.core.commands.CommandContext;
import com.excudo.core.commands.CommandClassRegistry;
import com.excudo.core.commands.CommandExecutionException;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.results.ExecutionResult;
import java.util.List;

/**
 * GoF Command for dissolving a p:grpSp group shape back into its constituent children.
 *
 * Undo is implemented by re-grouping the returned child SPIDs. The new group will
 * receive a fresh SPID but is functionally equivalent to the original.
 */
public class UngroupCommand implements Command {

    static final Parameter<Integer> SLIDE = Parameter.ofInt("slide")
        .slideNumber().description("Slide number").llmName("slideNumber").required().build();
    static final Parameter<Integer> SPID = Parameter.ofInt("spid")
        .spid().description("Group SPID to dissolve").llmName("targetSpid").required().build();

    public static final CommandSchema SCHEMA = CommandSchema.builder()
        .description("Ungroup a previously grouped shape")
        .llmEnabled(true)
        .parameter(SLIDE).parameter(SPID)
        .example("ungroup 1 5")
        .build();

    public static final String NAME = CommandClassRegistry.nameOf(UngroupCommand.class);

    public static Command fromParameters(CommandParameters p, CommandContext ctx) {
        return new UngroupCommand(p.get(SLIDE), p.get(SPID), ctx.orchestrator());
    }


    private final int slideNumber;
    private final int spid;
    private final PPTXOrchestrator orchestrator;
    private boolean executed = false;
    private List<Integer> childSpids = null;

    public UngroupCommand(int slideNumber, int spid, PPTXOrchestrator orchestrator) {
        if (orchestrator == null) {
            throw new IllegalArgumentException("PPTXOrchestrator cannot be null");
        }
        if (slideNumber <= 0) {
            throw new IllegalArgumentException("Slide number must be positive");
        }
        if (spid <= 0) {
            throw new IllegalArgumentException("SPID must be positive");
        }
        this.slideNumber = slideNumber;
        this.spid = spid;
        this.orchestrator = orchestrator;
    }

    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute", "Command has already been executed");
        }

        ExecutionResult<List<Integer>> result = orchestrator.ungroupShape(slideNumber, spid);
        if (!result.isSuccess()) {
            throw new CommandExecutionException(getDescription(), "execute",
                "Failed to ungroup shape: " + result.getMessage());
        }

        childSpids = result.getData().orElse(null);
        executed = true;
    }

    @Override
    public void undo() {
        if (!executed) {
            throw new CommandExecutionException(getDescription(), UndoCommand.NAME, "Command has not been executed");
        }
        if (!canUndo()) {
            throw new CommandExecutionException(getDescription(), UndoCommand.NAME, "No child SPIDs available for undo");
        }

        ExecutionResult<Integer> result = orchestrator.groupShapes(slideNumber, childSpids);
        if (!result.isSuccess()) {
            throw new CommandExecutionException(getDescription(), UndoCommand.NAME,
                "Failed to re-group shapes on undo: " + result.getMessage());
        }

        executed = false;
        childSpids = null;
    }

    @Override
    public boolean canUndo() {
        return executed && childSpids != null && childSpids.size() >= 2;
    }

    @Override
    public String getDescription() {
        return "Ungroup shape SPID " + spid + " on slide " + slideNumber;
    }

    @Override
    public boolean isExecuted() {
        return executed;
    }

    public int getSlideNumber() { return slideNumber; }
    public int getSpid() { return spid; }
    public List<Integer> getChildSpids() { return childSpids; }
}
