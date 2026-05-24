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
import java.util.Map;

/**
 * GoF Command for editing the slide master's color mapping (p:clrMap).
 * Used to switch between dark and light theme modes.
 */
public class EditMasterClrMapCommand implements Command {

    static final Parameter<String> BG1 = Parameter.ofString("bg1")
        .description("Theme color for bg1 (e.g., dk1 / lt1)").required(false).build();
    static final Parameter<String> TX1 = Parameter.ofString("tx1")
        .description("Theme color for tx1 (e.g., lt1 / dk1)").required(false).build();
    static final Parameter<String> BG2 = Parameter.ofString("bg2")
        .description("Theme color for bg2").required(false).build();
    static final Parameter<String> TX2 = Parameter.ofString("tx2")
        .description("Theme color for tx2").required(false).build();

    public static final CommandSchema SCHEMA = CommandSchema.builder()
        .description("Change the slide master color mapping (dark/light switch)")
        .llmEnabled(true)
        .llmDescription("Edit the slide master clrMap to switch between dark and light backgrounds.")
        .parameter(BG1).parameter(TX1).parameter(BG2).parameter(TX2)
        .example("edit-master-clrmap --bg1 dk1 --tx1 lt1")
        .build();

    public static final String NAME = CommandClassRegistry.nameOf(EditMasterClrMapCommand.class);

    public static Command fromParameters(CommandParameters p, CommandContext ctx) {
        java.util.Map<String, String> mappings = new java.util.LinkedHashMap<>();
        p.opt(BG1).ifPresent(v -> mappings.put("bg1", v));
        p.opt(TX1).ifPresent(v -> mappings.put("tx1", v));
        p.opt(BG2).ifPresent(v -> mappings.put("bg2", v));
        p.opt(TX2).ifPresent(v -> mappings.put("tx2", v));
        if (mappings.isEmpty()) {
            throw new IllegalArgumentException(
                "edit-master-clrmap requires at least one mapping (--bg1, --tx1, --bg2, --tx2)");
        }
        return new EditMasterClrMapCommand(mappings, ctx.orchestrator());
    }


    private final Map<String, String> mappings;
    private final PPTXOrchestrator orchestrator;
    private boolean executed = false;

    public EditMasterClrMapCommand(Map<String, String> mappings, PPTXOrchestrator orchestrator) {
        if (orchestrator == null) {
            throw new IllegalArgumentException("PPTXOrchestrator cannot be null");
        }
        if (mappings == null || mappings.isEmpty()) {
            throw new IllegalArgumentException("At least one color mapping is required");
        }
        this.mappings = mappings;
        this.orchestrator = orchestrator;
    }

    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute", "Command has already been executed");
        }

        for (Map.Entry<String, String> entry : mappings.entrySet()) {
            ExecutionResult<Void> result = orchestrator.setClrMap(entry.getKey(), entry.getValue());
            if (!result.isSuccess()) {
                throw new CommandExecutionException(getDescription(), "execute",
                    "Failed to set " + entry.getKey() + "=" + entry.getValue() + ": " + result.getMessage());
            }
        }

        executed = true;
    }

    @Override
    public void undo() {
        throw new CommandExecutionException(getDescription(), UndoCommand.NAME,
            "ClrMap undo not yet implemented -- use show-master to verify changes");
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
        return "Edit master color map: " + mappings;
    }
}
