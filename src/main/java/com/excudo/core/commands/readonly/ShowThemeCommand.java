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

import com.excudo.core.themes.ThemeLoader;
import com.excudo.core.themes.ThemeDefinition;
import com.excudo.core.themes.LayoutDefinition;

import java.util.Map;

/**
 * GoF Command for displaying complete theme details.
 * Gives LLM agents full inspection before editing.
 */
public class ShowThemeCommand implements Command {

    static final Parameter<String> THEME_ID = Parameter.ofString("themeId")
        .description("Theme ID to inspect").required().build();

    public static final CommandSchema SCHEMA = CommandSchema.builder()
        .description("Show complete theme details")
        .parameter(THEME_ID)
        .example("show-theme minimal")
        .build();

    public static final String NAME = CommandClassRegistry.nameOf(ShowThemeCommand.class);

    public static Command fromParameters(CommandParameters p, CommandContext ctx) {
        return new ShowThemeCommand(p.get(THEME_ID), ctx.requireDisplay());
    }


    private final String themeId;
    private final CommandDisplay display;
    private boolean executed = false;

    public ShowThemeCommand(String themeId, CommandDisplay display) {
        if (themeId == null || themeId.isBlank()) {
            throw new IllegalArgumentException("Theme ID must not be empty. Available: " + ThemeLoader.getAvailableIds());
        }
        if (display == null) {
            throw new IllegalArgumentException("CommandDisplay cannot be null");
        }
        this.themeId = themeId.toLowerCase();
        this.display = display;
    }

    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute", "Command has already been executed");
        }

        try {
            if (!ThemeLoader.exists(themeId)) {
                display.displayError("Unknown theme: '" + themeId + "'. Available: " + ThemeLoader.getAvailableIds());
                executed = true;
                return;
            }

            ThemeDefinition theme = ThemeLoader.get(themeId);
            String tag = ThemeLoader.isCustomTheme(themeId) ? "custom" : "builtin";

            display.displayMessage("");
            display.displayMessage("Theme: " + theme.getDisplayName() + " [" + tag + "]");
            display.displayMessage("  ID: " + theme.getId());
            display.displayMessage("");

            // Fonts
            display.displayMessage("  Fonts:");
            display.displayMessage("    Major (headings): " + theme.getMajorFont()
                    + (theme.getMajorFontFallback() != null ? " (fallback: " + theme.getMajorFontFallback() + ")" : ""));
            display.displayMessage("    Minor (body):     " + theme.getMinorFont()
                    + (theme.getMinorFontFallback() != null ? " (fallback: " + theme.getMinorFontFallback() + ")" : ""));
            display.displayMessage("");

            // Colors
            display.displayMessage("  Colors:");
            Map<String, String> colors = theme.getColorScheme();
            for (Map.Entry<String, String> entry : colors.entrySet()) {
                display.displayMessage("    " + String.format("%-10s", entry.getKey()) + "#" + entry.getValue());
            }
            display.displayMessage("");

            // Layouts
            display.displayMessage("  Layouts (" + theme.getLayouts().size() + "):");
            for (LayoutDefinition layout : theme.getLayouts()) {
                display.displayMessage("    " + layout.getLayoutId() + " - " + layout.getMatchingName()
                        + " (" + layout.getPlaceholderCount() + " placeholders)");
            }

            display.displayMessage("");
            display.displayMessage("  Background fill index: " + theme.getBackgroundFillIndex());

            executed = true;

        } catch (Exception e) {
            throw new CommandExecutionException(getDescription(), "execute",
                    "Failed to show theme: " + e.getMessage(), e);
        }
    }

    @Override
    public void undo() {
        throw new CommandExecutionException(getDescription(), UndoCommand.NAME,
                "Cannot undo a read-only show operation");
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
        return "Show theme '" + themeId + "'";
    }
}
