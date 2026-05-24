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
 * GoF Command for duplicating a slide layout.
 */
public class DuplicateLayoutCommand implements Command {

    static final Parameter<String> SOURCE_LAYOUT_ID = Parameter.ofString("sourceLayoutId")
        .description("Source layout ID (e.g., slideLayout2)").required().build();
    static final Parameter<String> NEW_NAME = Parameter.ofString("name")
        .description("Display name for the new layout").required().build();

    public static final CommandSchema SCHEMA = CommandSchema.builder()
        .description("Duplicate an existing slide layout with a new name")
        .llmEnabled(true)
        .llmDescription("Duplicate a slide layout. Use to create custom layouts before creating slides.")
        .parameter(SOURCE_LAYOUT_ID).parameter(NEW_NAME)
        .example("duplicate-layout slideLayout2 \"My Custom Layout\"")
        .build();

    public static final String NAME = CommandClassRegistry.nameOf(DuplicateLayoutCommand.class);

    public static Command fromParameters(CommandParameters p, CommandContext ctx) {
        return new DuplicateLayoutCommand(p.get(SOURCE_LAYOUT_ID), p.get(NEW_NAME), ctx.orchestrator());
    }


    private final String sourceLayoutId;
    private final String newName;
    private final PPTXOrchestrator orchestrator;
    private boolean executed = false;
    private String createdLayoutId = null;

    public DuplicateLayoutCommand(String sourceLayoutId, String newName, PPTXOrchestrator orchestrator) {
        if (orchestrator == null) {
            throw new IllegalArgumentException("PPTXOrchestrator cannot be null");
        }
        if (sourceLayoutId == null || sourceLayoutId.trim().isEmpty()) {
            throw new IllegalArgumentException("Source layout ID cannot be null or empty");
        }
        if (newName == null || newName.trim().isEmpty()) {
            throw new IllegalArgumentException("New name cannot be null or empty");
        }
        this.sourceLayoutId = sourceLayoutId;
        this.newName = newName;
        this.orchestrator = orchestrator;
    }

    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute", "Command has already been executed");
        }

        ExecutionResult<String> result = orchestrator.duplicateLayout(sourceLayoutId, newName);
        if (!result.isSuccess()) {
            throw new CommandExecutionException(getDescription(), "execute",
                "Failed to duplicate layout: " + result.getMessage());
        }

        createdLayoutId = result.getData().orElse(null);
        executed = true;
    }

    @Override
    public void undo() {
        if (!executed || createdLayoutId == null) {
            throw new CommandExecutionException(getDescription(), UndoCommand.NAME, "Command has not been executed");
        }

        ExecutionResult<Void> result = orchestrator.deleteLayout(createdLayoutId);
        if (!result.isSuccess()) {
            throw new CommandExecutionException(getDescription(), UndoCommand.NAME,
                "Failed to undo layout duplication: " + result.getMessage());
        }

        executed = false;
        createdLayoutId = null;
    }

    @Override
    public boolean canUndo() {
        return executed && createdLayoutId != null;
    }

    @Override
    public String getDescription() {
        return "Duplicate layout " + sourceLayoutId + " as \"" + newName + "\""
            + (createdLayoutId != null ? " -> " + createdLayoutId : "");
    }

    @Override
    public boolean isExecuted() {
        return executed;
    }

    public String getCreatedLayoutId() {
        return createdLayoutId;
    }
}
