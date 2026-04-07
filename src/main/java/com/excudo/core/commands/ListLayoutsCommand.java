package com.excudo.core.commands;

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
        throw new CommandExecutionException(getDescription(), "undo",
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
