package com.excudo.core.commands.meta;

import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandDisplay;
import com.excudo.core.commands.CommandExecutionException;
import com.excudo.core.commands.CommandSessionContext;
import com.excudo.core.commands.CommandSessionManager;
import com.excudo.core.commands.SessionResult;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.orchestration.PresentationMetadata;
import com.excudo.core.orchestration.PresentationScaffolder;
import com.excudo.core.orchestration.SessionManager;
import com.excudo.core.results.ExecutionResult;

import java.io.File;

/**
 * GoF Command for creating a new presentation from scratch.
 * Uses PresentationScaffolder to generate all required PPTX parts,
 * then initializes a session with the scaffolded directory.
 */
public class NewPresentationCommand implements Command {

    private final CommandSessionManager sessionManager;
    private final CommandSessionContext sessionContext;
    private final CommandDisplay display;
    private final String themeId;
    private boolean executed = false;

    public NewPresentationCommand(CommandSessionManager sessionManager,
                                  CommandSessionContext sessionContext,
                                  CommandDisplay display, String themeId) {
        if (sessionManager == null) {
            throw new IllegalArgumentException("CommandSessionManager cannot be null");
        }
        if (sessionContext == null) {
            throw new IllegalArgumentException("CommandSessionContext cannot be null");
        }
        if (display == null) {
            throw new IllegalArgumentException("CommandDisplay cannot be null");
        }
        if (themeId == null || themeId.isBlank()) {
            throw new IllegalArgumentException("Theme ID must not be empty");
        }
        this.sessionManager = sessionManager;
        this.sessionContext = sessionContext;
        this.display = display;
        this.themeId = themeId.toLowerCase();
    }

    @Override
    public void execute() {
        if (executed) {
            throw new CommandExecutionException(getDescription(), "execute", "Command has already been executed");
        }

        try {
            // Use in-memory scaffolding (no temp directory)
            com.excudo.core.model.PPTXDocument doc =
                PresentationScaffolder.scaffoldDocument(themeId);

            // If no session exists yet, register the current orchestrator as a session
            if (sessionContext.getCurrentSessionId() == null) {
                SessionResult result = sessionManager.createEmptySession();
                if (!result.isSuccess()) {
                    display.displayError("Failed to create session: " + result.getErrorMessage());
                    executed = true;
                    return;
                }
                sessionContext.setCurrentSession(
                        result.getSessionId(),
                        result.getOrchestrator(),
                        result.getLlmHandler(),
                        null
                );
            }

            // Use the session's orchestrator -- one orchestrator per session
            PPTXOrchestrator orchestrator = sessionContext.getCurrentOrchestrator();

            // Initialize with the in-memory document
            ExecutionResult<?> initResult = orchestrator.initialize(doc);
            if (!initResult.isSuccess()) {
                display.displayError("Failed to initialize: " + initResult.getMessage());
                executed = true;
                return;
            }

            display.displaySuccess("Created new presentation (theme: " + themeId + ")");

            // Notify state listeners that a new presentation is loaded.
            SessionManager.getInstance().firePresentationLoaded();

            // Evaluate theme color scheme for visibility issues using WCAG contrast ratios
            try {
                com.excudo.core.themes.ThemeDefinition theme = com.excudo.core.themes.ThemeLoader.get(themeId);
                java.util.List<String> contrastWarnings = com.excudo.core.themes.ThemeContrastChecker.check(theme);
                if (!contrastWarnings.isEmpty()) {
                    for (String warning : contrastWarnings) {
                        display.displayMessage("[VISIBILITY] " + warning);
                    }
                    java.util.List<String> passing = com.excudo.core.themes.ThemeContrastChecker.getPassingThemeIds();
                    passing.remove(themeId);
                    if (!passing.isEmpty()) {
                        display.displayMessage("[VISIBILITY] Consider modifying " + themeId
                            + " or using " + String.join(", ", passing));
                    }
                }
            } catch (Exception e) {
                // Theme contrast check is advisory -- don't block on failure
            }

            executed = true;

        } catch (Exception e) {
            throw new CommandExecutionException(getDescription(), "execute",
                    "Failed to create new presentation: " + e.getMessage(), e);
        }
    }

    @Override
    public void undo() {
        throw new CommandExecutionException(getDescription(), "undo",
                "Cannot undo new presentation creation");
    }

    @Override
    public boolean canUndo() {
        return false;
    }

    @Override
    public String getDescription() {
        return "Create new presentation with theme '" + themeId + "'";
    }

    @Override
    public boolean isExecuted() {
        return executed;
    }

}
