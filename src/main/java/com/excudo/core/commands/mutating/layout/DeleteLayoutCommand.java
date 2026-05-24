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
 * GoF Command for deleting a slide layout.
 * Deletion fails if any slides reference the layout.
 */
public class DeleteLayoutCommand implements Command {

    static final Parameter<String> LAYOUT_ID = Parameter.ofString("layoutId")
        .description("Layout ID to delete (e.g. slideLayout2)").required().build();

    public static final CommandSchema SCHEMA = CommandSchema.builder()
        .description("Delete a slide layout")
        .parameter(LAYOUT_ID)
        .example("delete-layout slideLayout2")
        .build();

    public static final String NAME = CommandClassRegistry.nameOf(DeleteLayoutCommand.class);

    public static Command fromParameters(CommandParameters p, CommandContext ctx) {
        return new DeleteLayoutCommand(p.get(LAYOUT_ID), ctx.orchestrator());
    }


    private final String layoutId;
    private final PPTXOrchestrator orchestrator;
    private boolean executed = false;

    public DeleteLayoutCommand(String layoutId, PPTXOrchestrator orchestrator) {
        if (orchestrator == null) {
            throw new IllegalArgumentException("PPTXOrchestrator cannot be null");
        }
        if (layoutId == null || layoutId.trim().isEmpty()) {
            throw new IllegalArgumentException("Layout ID cannot be null or empty");
        }
        this.layoutId = layoutId;
        this.orchestrator = orchestrator;
    }

    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute", "Command has already been executed");
        }

        ExecutionResult<Void> result = orchestrator.deleteLayout(layoutId);
        if (!result.isSuccess()) {
            throw new CommandExecutionException(getDescription(), "execute",
                result.getMessage());
        }

        executed = true;
    }

    @Override
    public void undo() {
        // Layout deletion undo would require storing the full layout XML + rels
        // which is complex. For now, undo is not supported.
        throw new CommandExecutionException(getDescription(), UndoCommand.NAME,
            "Layout deletion cannot be undone");
    }

    @Override
    public boolean canUndo() {
        return false;
    }

    @Override
    public String getDescription() {
        return "Delete layout " + layoutId;
    }

    @Override
    public boolean isExecuted() {
        return executed;
    }
}
