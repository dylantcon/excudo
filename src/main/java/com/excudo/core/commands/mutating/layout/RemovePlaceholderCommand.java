package com.excudo.core.commands.mutating.layout;

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

/**
 * GoF Command for removing a placeholder from a layout by idx.
 */
public class RemovePlaceholderCommand implements Command {

    static final Parameter<String> LAYOUT_ID = Parameter.ofString("layoutId")
        .description("Layout ID containing the placeholder").required().build();
    static final Parameter<Integer> IDX = Parameter.ofInt("idx")
        .description("Placeholder idx to remove").required().build();

    public static final CommandSchema SCHEMA = CommandSchema.builder()
        .description("Remove a placeholder from a slide layout")
        .parameter(LAYOUT_ID).parameter(IDX)
        .example("remove-placeholder slideLayout2 1")
        .build();

    public static final String NAME = CommandClassRegistry.nameOf(RemovePlaceholderCommand.class);

    public static Command fromParameters(CommandParameters p, CommandContext ctx) {
        return new RemovePlaceholderCommand(p.get(LAYOUT_ID), p.get(IDX), ctx.orchestrator());
    }


    private final String layoutId;
    private final int idx;
    private final PPTXOrchestrator orchestrator;
    private boolean executed = false;

    public RemovePlaceholderCommand(String layoutId, int idx, PPTXOrchestrator orchestrator) {
        if (orchestrator == null) {
            throw new IllegalArgumentException("PPTXOrchestrator cannot be null");
        }
        if (layoutId == null || layoutId.trim().isEmpty()) {
            throw new IllegalArgumentException("Layout ID cannot be null or empty");
        }
        this.layoutId = layoutId;
        this.idx = idx;
        this.orchestrator = orchestrator;
    }

    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute", "Command has already been executed");
        }

        ExecutionResult<Void> result = orchestrator.removePlaceholder(layoutId, idx);
        if (!result.isSuccess()) {
            throw new CommandExecutionException(getDescription(), "execute",
                "Failed to remove placeholder: " + result.getMessage());
        }

        executed = true;
    }

    @Override
    public void undo() {
        // Undo would require storing the removed placeholder's full DOM
        throw new CommandExecutionException(getDescription(), UndoCommand.NAME,
            "Placeholder removal undo not supported");
    }

    @Override
    public boolean canUndo() {
        return false;
    }

    @Override
    public String getDescription() {
        return "Remove placeholder idx=" + idx + " from " + layoutId;
    }

    @Override
    public boolean isExecuted() {
        return executed;
    }
}
