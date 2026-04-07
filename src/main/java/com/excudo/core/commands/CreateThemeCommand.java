package com.excudo.core.commands;

import com.excudo.core.themes.ThemeLoader;
import com.excudo.core.themes.ThemeDefinition;

/**
 * GoF Command for creating a new theme by duplicating an existing one.
 */
public class CreateThemeCommand implements Command {

    private final String newId;
    private final String baseThemeId;
    private final String displayName;
    private final CommandDisplay display;
    private boolean executed = false;

    public CreateThemeCommand(String newId, String baseThemeId, String displayName, CommandDisplay display) {
        if (newId == null || newId.isBlank()) {
            throw new IllegalArgumentException("New theme ID must not be empty");
        }
        if (display == null) {
            throw new IllegalArgumentException("CommandDisplay cannot be null");
        }
        this.newId = newId.toLowerCase();
        this.baseThemeId = (baseThemeId != null && !baseThemeId.isBlank()) ? baseThemeId.toLowerCase() : "minimal";
        this.displayName = (displayName != null && !displayName.isBlank()) ? displayName : newId;
        this.display = display;
    }

    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute", "Command has already been executed");
        }

        try {
            if (ThemeLoader.exists(newId)) {
                display.displayError("Theme '" + newId + "' already exists. Choose a different ID.");
                executed = true;
                return;
            }

            if (!ThemeLoader.exists(baseThemeId)) {
                display.displayError("Base theme '" + baseThemeId + "' not found. Available: " + ThemeLoader.getAvailableIds());
                executed = true;
                return;
            }

            ThemeDefinition base = ThemeLoader.get(baseThemeId);
            ThemeDefinition newTheme = base.toBuilder()
                    .id(newId)
                    .displayName(displayName)
                    .build();

            ThemeLoader.saveCustomTheme(newTheme);

            display.displaySuccess("Created theme '" + newId + "' based on '" + baseThemeId + "'");
            display.displayMessage("  Display name: " + displayName);
            display.displayMessage("  Fonts: " + newTheme.getMajorFont() + " / " + newTheme.getMinorFont());
            display.displayMessage("  Layouts: " + newTheme.getLayouts().size());

            executed = true;

        } catch (Exception e) {
            throw new CommandExecutionException(getDescription(), "execute",
                    "Failed to create theme: " + e.getMessage(), e);
        }
    }

    @Override
    public void undo() {
        throw new CommandExecutionException(getDescription(), "undo",
                "Undo not supported for theme creation");
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
        return "Create theme '" + newId + "' from '" + baseThemeId + "'";
    }
}
