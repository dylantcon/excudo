package com.excudo.core.commands.mutating.theme;

import com.excudo.core.commands.meta.UndoCommand;
import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandDisplay;
import com.excudo.core.commands.CommandExecutionException;

import com.excudo.core.themes.ThemeLoader;

/**
 * GoF Command for deleting a custom theme. Refuses to delete builtin themes.
 */
public class DeleteThemeCommand implements Command {

    private final String themeId;
    private final CommandDisplay display;
    private boolean executed = false;

    public DeleteThemeCommand(String themeId, CommandDisplay display) {
        if (themeId == null || themeId.isBlank()) {
            throw new IllegalArgumentException("Theme ID must not be empty");
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

            if (!ThemeLoader.isCustomTheme(themeId)) {
                display.displayError("Cannot delete builtin theme '" + themeId + "'. Only custom themes can be deleted.");
                executed = true;
                return;
            }

            boolean deleted = ThemeLoader.deleteCustomTheme(themeId);
            if (deleted) {
                display.displaySuccess("Deleted custom theme '" + themeId + "'");
            } else {
                display.displayError("Failed to delete theme '" + themeId + "'");
            }

            executed = true;

        } catch (Exception e) {
            throw new CommandExecutionException(getDescription(), "execute",
                    "Failed to delete theme: " + e.getMessage(), e);
        }
    }

    @Override
    public void undo() {
        throw new CommandExecutionException(getDescription(), UndoCommand.NAME,
                "Undo not supported for theme deletion");
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
        return "Delete theme '" + themeId + "'";
    }
}
