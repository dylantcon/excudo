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
import com.excudo.core.model.LayoutInfo;
import com.excudo.core.themes.ThemeLoader;
import com.excudo.core.themes.ThemeDefinition;
import com.excudo.core.themes.LayoutDefinition;
import com.excudo.core.inspection.PresentationInspector;
import java.util.List;
import java.util.Optional;

/**
 * GoF Command for listing available slide layouts.
 * Supports two modes:
 * - With themeId: show layouts from a theme definition (no session needed)
 * - Without themeId: show layouts from the loaded presentation (requires session)
 */
public class ListLayoutsCommand implements Command {

    static final Parameter<String> THEME_ID = Parameter.ofString("themeId")
        .description("Optional theme ID to list layouts from (no session needed)")
        .required(false).build();

    public static final CommandSchema SCHEMA = CommandSchema.builder()
        .description("List available slide layouts")
        .parameter(THEME_ID)
        .example("list-layouts")
        .example("list-layouts corporate")
        .build();

    public static final String NAME = CommandClassRegistry.nameOf(ListLayoutsCommand.class);

    public static Command fromParameters(CommandParameters p, CommandContext ctx) {
        String themeId = p.opt(THEME_ID).orElse(null);
        return themeId != null
            ? new ListLayoutsCommand(ctx.orchestrator(), ctx.requireDisplay(), themeId)
            : new ListLayoutsCommand(ctx.orchestrator(), ctx.requireDisplay());
    }


    private final PPTXOrchestrator orchestrator;
    private final CommandDisplay display;
    private final String themeId;
    private boolean executed = false;

    /**
     * Create a ListLayoutsCommand for presentation layouts (requires session).
     */
    public ListLayoutsCommand(PPTXOrchestrator orchestrator, CommandDisplay display) {
        this(orchestrator, display, null);
    }

    /**
     * Create a ListLayoutsCommand with optional themeId.
     * When themeId is non-null, displays theme layouts without requiring a session.
     */
    public ListLayoutsCommand(PPTXOrchestrator orchestrator, CommandDisplay display, String themeId) {
        if (display == null) {
            throw new IllegalArgumentException("CommandDisplay cannot be null");
        }
        this.orchestrator = orchestrator;
        this.display = display;
        this.themeId = themeId;
    }

    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute", "Command has already been executed");
        }

        try {
            if (themeId != null && !themeId.isBlank()) {
                executeThemeMode();
            } else {
                executePresentationMode();
            }
            executed = true;
        } catch (CommandExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new CommandExecutionException(getDescription(), "execute",
                "Failed to list layouts: " + e.getMessage(), e);
        }
    }

    private void executeThemeMode() {
        if (!ThemeLoader.exists(themeId)) {
            display.displayError("Unknown theme: '" + themeId + "'. Available: " + ThemeLoader.getAvailableIds());
            return;
        }

        ThemeDefinition theme = ThemeLoader.get(themeId);
        List<LayoutDefinition> layouts = theme.getLayouts();

        display.displayMessage("Layouts for theme '" + theme.getDisplayName() + "':");
        display.displayMessage("");

        if (layouts.isEmpty()) {
            display.displayMessage("  No layouts defined.");
        } else {
            for (LayoutDefinition layout : layouts) {
                StringBuilder info = new StringBuilder();
                info.append("  ").append(layout.getLayoutId());
                info.append(" (").append(layout.getMatchingName()).append(")");
                info.append(" - ").append(layout.getPlaceholderCount()).append(" placeholders");
                display.displayMessage(info.toString());
            }
            display.displayMessage("");
            display.displayMessage(String.format("Total layouts: %d", layouts.size()));
        }
    }

    private void executePresentationMode() {
        if (orchestrator == null) {
            display.displayError("No presentation loaded and no themeId specified. Use 'list-layouts <themeId>' or load a presentation first.");
            return;
        }

        Optional<com.excudo.core.orchestration.OrchestrationContext> context = orchestrator.getContext();
        if (context.isEmpty()) {
            display.displayError("No presentation loaded. Use 'load <filename>' or specify a themeId: list-layouts <themeId>");
            return;
        }

        PresentationInspector.LayoutInspectionResult result = PresentationInspector.getAvailableLayouts(
            orchestrator);

        if (!result.isSuccess()) {
            display.displayError("Failed to get layout information: " + result.getErrorMessage());
            return;
        }

        List<LayoutInfo> layouts = result.getLayouts();

        display.displayMessage("Available slide layouts:");

        if (layouts.isEmpty()) {
            display.displayMessage("  No layouts found in this presentation.");
        } else {
            for (LayoutInfo layout : layouts) {
                StringBuilder info = new StringBuilder();
                info.append(String.format("  %s", layout.getLayoutId()));
                if (layout.getName() != null && !layout.getName().equals(layout.getLayoutId())) {
                    info.append(String.format(" (%s)", layout.getName()));
                }
                if (layout.getDescription() != null && !layout.getDescription().trim().isEmpty()) {
                    info.append(String.format(" - %s", layout.getDescription()));
                }
                display.displayMessage(info.toString());
            }
            display.displayMessage("");
            display.displayMessage(String.format("Total layouts: %d", layouts.size()));
            display.displayMessage("Use layout ID when creating slides: create <position> \"<title>\" <layoutId>");
        }
    }

    @Override
    public void undo() {
        throw new CommandExecutionException(getDescription(), UndoCommand.NAME,
            "ListLayoutsCommand is read-only and does not support undo");
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
        return "List available slide layouts";
    }
}
