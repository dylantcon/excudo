package com.excudo.core.commands.readonly;

import com.excudo.core.commands.meta.UndoCommand;
import com.excudo.core.commands.Command;
import com.excudo.core.parsing.Parameter;
import com.excudo.core.parsing.CommandSchema;
import com.excudo.core.parsing.CommandParameters;
import com.excudo.core.commands.CommandContext;
import com.excudo.core.commands.CommandClassRegistry;
import com.excudo.core.commands.CommandDisplay;
import com.excudo.core.commands.CommandExecutionException;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.themes.TextLevelStyle;
import java.util.Map;
import java.util.Optional;

/**
 * GoF Command for displaying the current slide master state.
 * Read-only -- does not support undo.
 */
public class ShowMasterCommand implements Command {

    public static final CommandSchema SCHEMA = CommandSchema.builder()
        .description("Show the slide master details")
        .example("show-master")
        .build();

    public static final String NAME = CommandClassRegistry.nameOf(ShowMasterCommand.class);

    public static Command fromParameters(CommandParameters p, CommandContext ctx) {
        return new ShowMasterCommand(ctx.orchestrator(), ctx.requireDisplay());
    }


    private final PPTXOrchestrator orchestrator;
    private final CommandDisplay display;
    private boolean executed = false;

    public ShowMasterCommand(PPTXOrchestrator orchestrator, CommandDisplay display) {
        if (orchestrator == null) {
            throw new IllegalArgumentException("PPTXOrchestrator cannot be null");
        }
        if (display == null) {
            throw new IllegalArgumentException("CommandDisplay cannot be null");
        }
        this.orchestrator = orchestrator;
        this.display = display;
    }

    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute", "Command has already been executed");
        }

        Optional<com.excudo.core.orchestration.OrchestrationContext> ctx = orchestrator.getContext();
        if (ctx.isEmpty()) {
            display.displayError("No presentation loaded. Use 'new <theme>' or 'load <file>' first.");
            executed = true;
            return;
        }

        Map<String, Object> info = orchestrator.getMasterInfo();

        display.displayMessage("=== Slide Master ===");

        // Color map
        @SuppressWarnings("unchecked")
        Map<String, String> clrMap = (Map<String, String>) info.get("clrMap");
        if (clrMap != null && !clrMap.isEmpty()) {
            display.displayMessage("");
            display.displayMessage("Color Map:");
            for (Map.Entry<String, String> entry : clrMap.entrySet()) {
                display.displayMessage(String.format("  %s -> %s", entry.getKey(), entry.getValue()));
            }
        }

        // Background
        String bgIdx = (String) info.get("bgRefIdx");
        String bgColor = (String) info.get("bgColor");
        if (bgIdx != null) {
            display.displayMessage("");
            display.displayMessage(String.format("Background: idx=%s color=%s",
                bgIdx, bgColor != null ? bgColor : "(none)"));
        }

        // Placeholder count
        Object phCount = info.get("placeholderCount");
        if (phCount != null) {
            display.displayMessage(String.format("Placeholders: %s", phCount));
        }

        // Text styles
        @SuppressWarnings("unchecked")
        Map<String, String> txStyles = (Map<String, String>) info.get("txStyles");
        if (txStyles != null && !txStyles.isEmpty()) {
            display.displayMessage("");
            display.displayMessage("Text Styles:");
            for (Map.Entry<String, String> entry : txStyles.entrySet()) {
                display.displayMessage(String.format("  %s: %s", entry.getKey(), entry.getValue()));
            }
        }

        // Detailed style output
        Map<String, TextLevelStyle[]> styles = orchestrator.getMasterStyles();
        if (!styles.isEmpty()) {
            display.displayMessage("");
            for (Map.Entry<String, TextLevelStyle[]> entry : styles.entrySet()) {
                display.displayMessage(entry.getKey() + " (all levels):");
                TextLevelStyle[] levels = entry.getValue();
                for (int i = 0; i < levels.length; i++) {
                    if (levels[i] == null) continue;
                    TextLevelStyle lvl = levels[i];
                    StringBuilder sb = new StringBuilder();
                    sb.append(String.format("  L%d: %dpt", i + 1, lvl.getFontSize() / 100));
                    if (lvl.isBold()) sb.append(" bold");
                    sb.append(" ").append(lvl.getColorRef());
                    if (lvl.hasBullet()) {
                        sb.append(" bullet=").append(lvl.getBulletChar());
                    }
                    if (lvl.getMarginLeft() > 0) {
                        sb.append(" marL=").append(lvl.getMarginLeft());
                    }
                    display.displayMessage(sb.toString());
                }
            }
        }

        // Object defaults
        Object objDefaults = info.get("objectDefaultsPopulated");
        if (objDefaults != null) {
            display.displayMessage("");
            display.displayMessage("Object Defaults: " +
                (Boolean.TRUE.equals(objDefaults) ? "populated" : "empty (shapes use built-in defaults)"));
        }

        executed = true;
    }

    @Override
    public void undo() {
        throw new CommandExecutionException(getDescription(), UndoCommand.NAME,
            "ShowMasterCommand is read-only and does not support undo");
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
        return "Show slide master";
    }
}
