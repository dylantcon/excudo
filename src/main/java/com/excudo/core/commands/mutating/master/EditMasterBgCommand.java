package com.excudo.core.commands.mutating.master;

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
 * GoF Command for editing the slide master background.
 * Updates p:bg/p:bgRef in slideMaster1.xml.
 */
public class EditMasterBgCommand implements Command {

    static final Parameter<Integer> FILL_IDX = Parameter.ofInt("fill-idx")
        .description("Background fill index (default 1001)").defaultValue("1001").build();
    static final Parameter<String> COLOR = Parameter.ofString("color")
        .description("Scheme color name (e.g. bg1, accent1) or hex").required().build();

    public static final CommandSchema SCHEMA = CommandSchema.builder()
        .description("Edit the master-slide background fill")
        .parameter(FILL_IDX).parameter(COLOR)
        .example("edit-master-bg accent1")
        .build();

    public static final String NAME = CommandClassRegistry.nameOf(EditMasterBgCommand.class);

    public static Command fromParameters(CommandParameters p, CommandContext ctx) {
        return new EditMasterBgCommand(p.get(FILL_IDX), p.get(COLOR), ctx.orchestrator());
    }


    private final int fillIndex;
    private final String schemeColor;
    private final PPTXOrchestrator orchestrator;
    private boolean executed = false;

    public EditMasterBgCommand(int fillIndex, String schemeColor, PPTXOrchestrator orchestrator) {
        if (orchestrator == null) {
            throw new IllegalArgumentException("PPTXOrchestrator cannot be null");
        }
        this.fillIndex = fillIndex;
        this.schemeColor = schemeColor != null ? schemeColor : "bg1";
        this.orchestrator = orchestrator;
    }

    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute", "Command has already been executed");
        }

        ExecutionResult<Void> result = orchestrator.setMasterBackground(fillIndex, schemeColor);
        if (!result.isSuccess()) {
            throw new CommandExecutionException(getDescription(), "execute", result.getMessage());
        }

        executed = true;
    }

    @Override
    public void undo() {
        throw new CommandExecutionException(getDescription(), UndoCommand.NAME,
            "Background undo not yet implemented -- use show-master to verify changes");
    }

    @Override
    public boolean canUndo() {
        return false;
    }

    @Override
    public boolean isExecuted() {
        return executed;
    }

    @Override
    public String getDescription() {
        return "Set master background idx=" + fillIndex + " color=" + schemeColor;
    }
}
