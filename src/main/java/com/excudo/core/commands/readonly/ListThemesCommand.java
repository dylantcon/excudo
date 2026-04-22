package com.excudo.core.commands.readonly;

import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandDisplay;
import com.excudo.core.commands.CommandExecutionException;

import com.excudo.core.themes.ThemeLoader;
import com.excudo.core.themes.ThemeDefinition;

import java.util.List;
import java.util.Map;

/**
 * GoF Command for listing available bundled themes.
 * Displays theme names, color palette, and font families.
 */
public class ListThemesCommand implements Command {

    private final CommandDisplay display;
    private boolean executed = false;

    public ListThemesCommand(CommandDisplay display) {
        if (display == null) {
            throw new IllegalArgumentException("CommandDisplay cannot be null");
        }
        this.display = display;
    }

    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute", "Command has already been executed");
        }

        try {
            List<ThemeDefinition> themes = ThemeLoader.getAll();

            display.displayMessage("");
            display.displayMessage("Available Themes:");
            display.displayMessage("");

            for (ThemeDefinition theme : themes) {
                display.displayMessage("  " + theme.getId() + " - " + theme.getDisplayName());
                display.displayMessage("    Fonts: " + theme.getMajorFont() + " (headings), " + theme.getMinorFont() + " (body)");

                Map<String, String> colors = theme.getColorScheme();
                StringBuilder palette = new StringBuilder("    Colors: ");
                String[] preview = {"dk1", "lt1", "accent1", "accent2", "accent3", "accent4"};
                for (String key : preview) {
                    String hex = colors.getOrDefault(key, "------");
                    palette.append(key).append("=#").append(hex).append(" ");
                }
                display.displayMessage(palette.toString().trim());

                // Per-theme layout names
                List<com.excudo.core.themes.LayoutDefinition> layouts = theme.getLayouts();
                StringBuilder layoutNames = new StringBuilder("    Layouts (" + layouts.size() + "): ");
                for (int i = 0; i < layouts.size(); i++) {
                    if (i > 0) layoutNames.append(", ");
                    layoutNames.append(layouts.get(i).getMatchingName());
                }
                display.displayMessage(layoutNames.toString());
                display.displayMessage("");
            }

            display.displayMessage("Usage: apply-theme <themeId>");
            display.displayMessage("Example: apply-theme minimal");

            executed = true;

        } catch (Exception e) {
            throw new CommandExecutionException(getDescription(), "execute",
                    "Failed to list themes: " + e.getMessage(), e);
        }
    }

    @Override
    public void undo() {
        throw new CommandExecutionException(getDescription(), "undo",
                "Cannot undo a read-only list operation");
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
        return "List Themes";
    }
}
