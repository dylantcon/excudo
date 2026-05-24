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
 * GoF Command for renaming a slide layout's display name.
 */
public class RenameLayoutCommand implements Command {

    static final Parameter<String> LAYOUT_ID = Parameter.ofString("layoutId")
        .description("Layout ID to rename").required().build();
    static final Parameter<String> NEW_NAME = Parameter.ofString("name")
        .description("New display name").required().build();

    public static final CommandSchema SCHEMA = CommandSchema.builder()
        .description("Rename a slide layout")
        .parameter(LAYOUT_ID).parameter(NEW_NAME)
        .example("rename-layout slideLayout2 \"My Layout\"")
        .build();

    public static final String NAME = CommandClassRegistry.nameOf(RenameLayoutCommand.class);

    public static Command fromParameters(CommandParameters p, CommandContext ctx) {
        return new RenameLayoutCommand(p.get(LAYOUT_ID), p.get(NEW_NAME), ctx.orchestrator());
    }


    private final String layoutId;
    private final String newName;
    private final PPTXOrchestrator orchestrator;
    private boolean executed = false;

    public RenameLayoutCommand(String layoutId, String newName, PPTXOrchestrator orchestrator) {
        if (orchestrator == null) {
            throw new IllegalArgumentException("PPTXOrchestrator cannot be null");
        }
        if (layoutId == null || layoutId.trim().isEmpty()) {
            throw new IllegalArgumentException("Layout ID cannot be null or empty");
        }
        if (newName == null || newName.trim().isEmpty()) {
            throw new IllegalArgumentException("New name cannot be null or empty");
        }
        this.layoutId = layoutId;
        this.newName = newName;
        this.orchestrator = orchestrator;
    }

    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute", "Command has already been executed");
        }

        ExecutionResult<Void> result = orchestrator.renameLayout(layoutId, newName);
        if (!result.isSuccess()) {
            throw new CommandExecutionException(getDescription(), "execute",
                "Failed to rename layout: " + result.getMessage());
        }

        executed = true;
    }

    @Override
    public void undo() {
        throw new CommandExecutionException(getDescription(), UndoCommand.NAME,
            "Layout rename undo not supported");
    }

    @Override
    public boolean canUndo() {
        return false;
    }

    @Override
    public String getDescription() {
        return "Rename layout " + layoutId + " to \"" + newName + "\"";
    }

    @Override
    public boolean isExecuted() {
        return executed;
    }
}
