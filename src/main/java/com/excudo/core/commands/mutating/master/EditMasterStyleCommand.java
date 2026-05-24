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
 * GoF Command for editing text style levels on the slide master.
 * Modifies p:txStyles (titleStyle/bodyStyle/otherStyle) in slideMaster1.xml.
 */
public class EditMasterStyleCommand implements Command {

    static final Parameter<String> TARGET = Parameter.ofString("target")
        .description("Style target: title, body, or other")
        .validValues("title", "body", "other").required().build();
    static final Parameter<Integer> LEVEL = Parameter.ofInt("level")
        .description("Style level (1-9)").required().build();
    static final Parameter<Integer> FONT_SIZE = Parameter.ofInt("fontSize")
        .description("Font size in points").required(false).build();
    static final Parameter<String> BOLD = Parameter.ofString("bold")
        .description("Bold text (true/false)").required(false).build();
    static final Parameter<String> COLOR = Parameter.ofString("color")
        .description("Color scheme reference (e.g., tx1, dk1)").required(false).build();
    static final Parameter<String> BULLET = Parameter.ofString("bullet")
        .description("Bullet character").required(false).build();
    static final Parameter<String> BULLET_FONT = Parameter.ofString("bulletFont")
        .description("Bullet font name").required(false).build();
    static final Parameter<Integer> MARGIN = Parameter.ofInt("margin")
        .description("Left margin in EMUs").required(false).build();
    static final Parameter<Integer> INDENT = Parameter.ofInt("indent")
        .description("Text indent in EMUs").required(false).build();

    public static final CommandSchema SCHEMA = CommandSchema.builder()
        .description("Edit text style levels on the slide master")
        .llmEnabled(true)
        .llmDescription("Edit slide master text styles (titleStyle, bodyStyle, otherStyle) per level.")
        .parameter(TARGET).parameter(LEVEL)
        .parameter(FONT_SIZE).parameter(BOLD).parameter(COLOR)
        .parameter(BULLET).parameter(BULLET_FONT).parameter(MARGIN).parameter(INDENT)
        .example("edit-master-style title --level 1 --fontSize 36 --bold true --color tx1")
        .build();

    public static final String NAME = CommandClassRegistry.nameOf(EditMasterStyleCommand.class);

    public static Command fromParameters(CommandParameters p, CommandContext ctx) {
        java.util.Map<String, Object> updates = new java.util.HashMap<>();
        p.opt(FONT_SIZE).ifPresent(v -> updates.put("fontSize", v));
        p.opt(BOLD).ifPresent(v -> updates.put("bold", v));
        p.opt(COLOR).ifPresent(v -> updates.put("color", v));
        p.opt(BULLET).ifPresent(v -> updates.put("bullet", v));
        p.opt(BULLET_FONT).ifPresent(v -> updates.put("bulletFont", v));
        p.opt(MARGIN).ifPresent(v -> updates.put("margin", v));
        p.opt(INDENT).ifPresent(v -> updates.put("indent", v));
        return new EditMasterStyleCommand(p.get(TARGET), p.get(LEVEL), updates, ctx.orchestrator());
    }


    private final String target;
    private final int level;
    private final Map<String, Object> updates;
    private final PPTXOrchestrator orchestrator;
    private boolean executed = false;

    public EditMasterStyleCommand(String target, int level, Map<String, Object> updates,
                                  PPTXOrchestrator orchestrator) {
        if (orchestrator == null) {
            throw new IllegalArgumentException("PPTXOrchestrator cannot be null");
        }
        if (target == null || target.isEmpty()) {
            throw new IllegalArgumentException("Target must be title, body, or other");
        }
        if (level < 1 || level > 9) {
            throw new IllegalArgumentException("Level must be 1-9, got: " + level);
        }
        this.target = target;
        this.level = level;
        this.updates = updates;
        this.orchestrator = orchestrator;
    }

    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute", "Command has already been executed");
        }

        ExecutionResult<Void> result = orchestrator.editMasterStyle(target, level, updates);
        if (!result.isSuccess()) {
            throw new CommandExecutionException(getDescription(), "execute", result.getMessage());
        }

        executed = true;
    }

    @Override
    public void undo() {
        throw new CommandExecutionException(getDescription(), UndoCommand.NAME,
            "Master style undo not yet implemented -- use show-master to verify changes");
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
        return "Edit master " + target + " style level " + level;
    }
}
