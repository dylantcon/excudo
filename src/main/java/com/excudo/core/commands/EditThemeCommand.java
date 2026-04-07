package com.excudo.core.commands;

import com.excudo.core.themes.ThemeLoader;
import com.excudo.core.themes.ThemeDefinition;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GoF Command for editing theme properties (colors, fonts, displayName).
 * Refuses to edit builtin themes directly.
 */
public class EditThemeCommand implements Command {

    private final String themeId;
    private final String property;
    private final String value;
    private final CommandDisplay display;
    private boolean executed = false;

    public EditThemeCommand(String themeId, String property, String value, CommandDisplay display) {
        if (themeId == null || themeId.isBlank()) {
            throw new IllegalArgumentException("Theme ID must not be empty");
        }
        if (property == null || property.isBlank()) {
            throw new IllegalArgumentException("Property must not be empty");
        }
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Value must not be empty");
        }
        if (display == null) {
            throw new IllegalArgumentException("CommandDisplay cannot be null");
        }
        this.themeId = themeId.toLowerCase();
        this.property = property;
        this.value = value;
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
                display.displayError("Theme '" + themeId + "' is builtin. Use 'create-theme' to make a custom copy first.");
                executed = true;
                return;
            }

            ThemeDefinition theme = ThemeLoader.get(themeId);
            ThemeDefinition.Builder builder = theme.toBuilder();

            if (property.startsWith("color.")) {
                String colorName = property.substring(6);
                Map<String, String> colors = new LinkedHashMap<>(theme.getColorScheme());
                if (!colors.containsKey(colorName)) {
                    display.displayError("Unknown color name: '" + colorName + "'. Valid: " + colors.keySet());
                    executed = true;
                    return;
                }
                if (!value.matches("[0-9A-Fa-f]{6}")) {
                    display.displayError("Color value must be 6-digit hex (e.g., FF5733). Got: " + value);
                    executed = true;
                    return;
                }
                colors.put(colorName, value.toUpperCase());
                builder.colorScheme(colors);
                display.displaySuccess("Set " + themeId + " " + colorName + " = #" + value.toUpperCase());
            } else if ("majorFont".equals(property)) {
                builder.majorFont(value);
                display.displaySuccess("Set " + themeId + " majorFont = " + value);
            } else if ("minorFont".equals(property)) {
                builder.minorFont(value);
                display.displaySuccess("Set " + themeId + " minorFont = " + value);
            } else if ("displayName".equals(property)) {
                builder.displayName(value);
                display.displaySuccess("Set " + themeId + " displayName = " + value);
            } else {
                display.displayError("Unknown property: '" + property
                        + "'. Supported: color.<name>, majorFont, minorFont, displayName");
                executed = true;
                return;
            }

            ThemeDefinition updated = builder.build();
            ThemeLoader.saveCustomTheme(updated);

            executed = true;

        } catch (Exception e) {
            throw new CommandExecutionException(getDescription(), "execute",
                    "Failed to edit theme: " + e.getMessage(), e);
        }
    }

    @Override
    public void undo() {
        throw new CommandExecutionException(getDescription(), "undo",
                "Undo not supported for theme editing");
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
        return "Edit theme '" + themeId + "' " + property + "=" + value;
    }
}
