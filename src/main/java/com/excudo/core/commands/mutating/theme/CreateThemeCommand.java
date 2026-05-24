package com.excudo.core.commands.mutating.theme;

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

/**
 * GoF Command for creating a new theme by duplicating an existing one.
 */
public class CreateThemeCommand implements Command {

    static final Parameter<String> ID = Parameter.ofString("id")
        .description("New theme ID").required().build();
    static final Parameter<String> BASE_THEME = Parameter.ofString("baseTheme")
        .description("Base theme to duplicate (default: minimal)")
        .required(false).build();
    static final Parameter<String> DISPLAY_NAME = Parameter.ofString("displayName")
        .description("Display name for the new theme").required(false).build();

    public static final CommandSchema SCHEMA = CommandSchema.builder()
        .description("Create a new theme by duplicating an existing one")
        .parameter(ID).parameter(BASE_THEME).parameter(DISPLAY_NAME)
        .example("create-theme my-theme")
        .example("create-theme my-theme corporate \"My Custom Theme\"")
        .build();

    public static final String NAME = CommandClassRegistry.nameOf(CreateThemeCommand.class);

    public static Command fromParameters(CommandParameters p, CommandContext ctx) {
        return new CreateThemeCommand(p.get(ID),
            p.opt(BASE_THEME).orElse(null),
            p.opt(DISPLAY_NAME).orElse(null),
            ctx.requireDisplay());
    }


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
        throw new CommandExecutionException(getDescription(), UndoCommand.NAME,
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
