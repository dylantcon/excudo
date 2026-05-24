package com.excudo.core.commands.mutating.theme;

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
 * GoF Command for populating a:objectDefaults in theme1.xml.
 * Ensures non-placeholder shapes get consistent default styling.
 */
public class SetObjectDefaultsCommand implements Command {

    static final Parameter<String> FONT_COLOR = Parameter.ofString("font-color")
        .description("Scheme color for default shape text (e.g., tx1)").required(false).build();
    static final Parameter<Integer> LINE_WIDTH = Parameter.ofInt("line-width")
        .description("Default line width in EMUs (e.g., 25400 for 2pt)").required(false).build();
    static final Parameter<String> FILL_COLOR = Parameter.ofString("fill-color")
        .description("Scheme color for default shape fill").required(false).build();

    public static final CommandSchema SCHEMA = CommandSchema.builder()
        .description("Populate theme objectDefaults for consistent shape styling")
        .llmEnabled(true)
        .llmDescription("Set a:objectDefaults in theme XML so non-placeholder shapes inherit theme-consistent styling.")
        .parameter(FONT_COLOR).parameter(LINE_WIDTH).parameter(FILL_COLOR)
        .example("set-object-defaults --font-color tx1 --line-width 25400")
        .build();

    public static final String NAME = CommandClassRegistry.nameOf(SetObjectDefaultsCommand.class);

    public static Command fromParameters(CommandParameters p, CommandContext ctx) {
        return new SetObjectDefaultsCommand(p.opt(FONT_COLOR).orElse(null),
            p.opt(LINE_WIDTH).orElse(null), p.opt(FILL_COLOR).orElse(null),
            ctx.orchestrator());
    }


    private final String fontColor;
    private final Integer lineWidth;
    private final String fillColor;
    private final PPTXOrchestrator orchestrator;
    private boolean executed = false;

    public SetObjectDefaultsCommand(String fontColor, Integer lineWidth, String fillColor,
                                    PPTXOrchestrator orchestrator) {
        if (orchestrator == null) {
            throw new IllegalArgumentException("PPTXOrchestrator cannot be null");
        }
        this.fontColor = fontColor != null ? fontColor : "tx1";
        this.lineWidth = lineWidth;
        this.fillColor = fillColor;
        this.orchestrator = orchestrator;
    }

    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute", "Command has already been executed");
        }

        ExecutionResult<Void> result = orchestrator.setObjectDefaults(fontColor, lineWidth);
        if (!result.isSuccess()) {
            throw new CommandExecutionException(getDescription(), "execute", result.getMessage());
        }

        executed = true;
    }

    @Override
    public void undo() {
        throw new CommandExecutionException(getDescription(), UndoCommand.NAME,
            "Object defaults undo not yet implemented -- use show-master to verify changes");
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
        return "Set object defaults: fontColor=" + fontColor
            + (lineWidth != null ? " lineWidth=" + lineWidth : "");
    }
}
