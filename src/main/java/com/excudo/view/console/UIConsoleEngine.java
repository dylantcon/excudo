package com.excudo.view.console;

import com.excudo.console.AbstractConsoleEngine;
import com.excudo.console.ConsoleStyle;
import com.excudo.core.commands.CommandInvoker;
import com.excudo.core.orchestration.PPTXOrchestrator;
import javafx.application.Platform;
import javafx.scene.control.TextArea;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * JavaFX/UI implementation of ConsoleEngine for GUI integration.
 *
 * Delegates command parsing and execution to the shared AbstractConsoleEngine,
 * then routes output through a typed styled handler so the hosting controller
 * can render each segment as a JavaFX Text node with the appropriate Color.
 *
 * Unlike the TTY engine, no ANSI escape codes are ever emitted -- the
 * ConsoleStyle enum is passed directly to the handler, which consults a
 * style->color map. Parsing ANSI on the UI side would be a lossy round-trip
 * between two implementations of the same abstraction in the same JVM.
 *
 * The legacy LLM-mode / approval-workflow surface has been removed; interactive
 * LLM editing is now handled by the console's 'arrange' command, which is
 * identical across TTY and GUI.
 */
public class UIConsoleEngine extends AbstractConsoleEngine {

    private final TextArea legacyOutputArea;
    private BiConsumer<String, ConsoleStyle> styledHandler;
    private Consumer<String> statusHandler;
    private Consumer<Boolean> modeChangeHandler;

    public UIConsoleEngine(TextArea outputArea) {
        this.legacyOutputArea = outputArea;
        // Default handler writes plain text to the legacy TextArea so the
        // engine still works during the transition window before a hosting
        // controller wires in its styled handler.
        this.styledHandler = (text, style) -> appendFallback(text);
        this.statusHandler = text -> {};
        this.modeChangeHandler = isArrange -> {};
    }

    @Override
    public void initialize(PPTXOrchestrator orchestrator) {
        super.initialize(orchestrator);
    }

    // ========== Arrange mode hook ==========
    //
    // Arrange mode already lives in AbstractConsoleEngine (see
    // enterArrangeMode / exitArrangeMode). We override here to fire a
    // simple Consumer<Boolean> callback so the hosting controller can
    // change the command input's style/prompt without a full listener
    // framework for a single state bit.

    @Override
    public void enterArrangeMode() {
        super.enterArrangeMode();
        if (Platform.isFxApplicationThread()) {
            modeChangeHandler.accept(true);
        } else {
            Platform.runLater(() -> modeChangeHandler.accept(true));
        }
    }

    @Override
    public void exitArrangeMode() {
        super.exitArrangeMode();
        if (Platform.isFxApplicationThread()) {
            modeChangeHandler.accept(false);
        } else {
            Platform.runLater(() -> modeChangeHandler.accept(false));
        }
    }

    /**
     * Handle the 'llm' command. The GUI has no interactive scanner, so we
     * delegate to the LLM handler without one. For interactive multi-turn
     * editing, users should use the console's 'arrange' command instead.
     */
    @Override
    protected void handleLLMCommand(String subCommand) {
        CommandInvoker invoker = getCurrentCommandInvoker();
        if (invoker == null) {
            displayError("No active session. Use 'load <filename>' or 'new' to start a session first.");
            return;
        }
        if (llmHandler == null) {
            displayError("LLM handler not available.");
            return;
        }
        llmHandler.handleCommand(subCommand, null, invoker);
    }

    @Override
    public void executeCommand(String command) {
        // Execute on JavaFX Application Thread for UI updates
        Platform.runLater(() -> {
            // Echo the command itself with ACCENT so it stands out in the log
            styledHandler.accept("> " + command, ConsoleStyle.ACCENT);

            switch (command.trim().toLowerCase()) {
                case "clear":
                    clearOutput();
                    return;
                case "status":
                    showStatus();
                    return;
                default:
                    super.executeCommand(command);
            }
        });
    }

    /**
     * Single rendering hook. Invoked by the parent class's displayMessage/
     * displayError/displaySuccess forwarders, and by any caller that needs a
     * specific style (DIM, HEADER, ACCENT). Hops to the FX Application Thread
     * so background commands can call this directly.
     */
    @Override
    public void displayStyled(String message, ConsoleStyle style) {
        if (Platform.isFxApplicationThread()) {
            styledHandler.accept(message, style);
        } else {
            Platform.runLater(() -> styledHandler.accept(message, style));
        }
    }

    private void showStatus() {
        if (orchestrator == null) {
            displayMessage("System: Not initialized");
            return;
        }
        displayMessage("");
        displayStyled("=== System Status ===", ConsoleStyle.HEADER);
        displayMessage("Presentation: " + getPresentationStatus());
        displayMessage("");
    }

    private void clearOutput() {
        if (legacyOutputArea != null) {
            Platform.runLater(legacyOutputArea::clear);
        }
    }

    /**
     * Fallback sink used before a hosting controller registers its styled
     * handler. Writes plain text to the legacy TextArea so the engine still
     * produces visible output during construction.
     */
    private void appendFallback(String text) {
        if (legacyOutputArea != null) {
            Platform.runLater(() -> legacyOutputArea.appendText(text + "\n"));
        }
    }

    // ========== Handler registration ==========

    /**
     * Register the styled output sink. The hosting controller typically
     * passes a method reference to a StyledConsoleView::appendLine.
     */
    public void setStyledHandler(BiConsumer<String, ConsoleStyle> handler) {
        this.styledHandler = handler != null ? handler : ((text, style) -> appendFallback(text));
    }

    public void setStatusHandler(Consumer<String> statusHandler) {
        this.statusHandler = statusHandler != null ? statusHandler : (text -> {});
    }

    /**
     * Register a callback invoked when the console enters or leaves arrange
     * mode. The hosting controller typically uses this to change the command
     * input prompt / style so the user can see they're in a multi-line agentic
     * editing session.
     */
    public void setModeChangeHandler(Consumer<Boolean> modeChangeHandler) {
        this.modeChangeHandler = modeChangeHandler != null ? modeChangeHandler : (b -> {});
    }
}
