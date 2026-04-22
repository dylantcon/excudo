package com.excudo.view.console;

import com.excudo.console.ConsoleStyle;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

/**
 * Thin JavaFX helper that renders styled console output into a TextFlow
 * wrapped in a ScrollPane.
 *
 * Each call to {@link #appendLine(String, ConsoleStyle)} creates a new
 * {@link Text} node with a fill color and font weight chosen from a static
 * map keyed by ConsoleStyle. The scroll pane auto-scrolls to the bottom so
 * new output stays visible.
 *
 * This deliberately does NOT parse ANSI codes -- the ConsoleStyle enum
 * carries the semantic styling from the producing command straight through
 * to render time without a serialize/parse round-trip.
 */
public class StyledConsoleView {

    private static final Font REGULAR_FONT = Font.font("Monospaced", 12);
    private static final Font BOLD_FONT = Font.font("Monospaced", FontWeight.BOLD, 12);

    private final TextFlow textFlow;
    private final ScrollPane scrollPane;

    public StyledConsoleView(TextFlow textFlow, ScrollPane scrollPane) {
        this.textFlow = textFlow;
        this.scrollPane = scrollPane;

        // Auto-scroll after every layout pass. Calling setVvalue(1.0)
        // inline with getChildren().add(...) scrolls to the PREVIOUS
        // bottom because the new Text node isn't measured until the next
        // FX pulse. Listening on the TextFlow's height gives us the right
        // moment to pin the scroll to the newest content. Works for any
        // content added here, via clear(), or by outside code.
        if (textFlow != null && scrollPane != null) {
            textFlow.heightProperty().addListener((obs, oldH, newH) ->
                scrollPane.setVvalue(1.0));
            installCopySupport();
        }
    }

    /**
     * Attach clipboard bindings + right-click menu to the console output.
     * Selection-aware drag-to-select is a TODO; for now the primary win
     * is "Copy All" (agent stack traces, error messages) via right-click
     * or Ctrl+C when the console has focus.
     */
    private void installCopySupport() {
        ContextMenu menu = new ContextMenu();
        MenuItem copyAll = new MenuItem("Copy All");
        copyAll.setOnAction(e -> copyAllToClipboard());
        MenuItem clearMenuItem = new MenuItem("Clear Console");
        clearMenuItem.setOnAction(e -> clear());
        menu.getItems().addAll(copyAll, clearMenuItem);

        // Install on both the TextFlow and its ScrollPane -- the hit
        // target differs depending on whether the user right-clicks on
        // rendered text or on the empty background below it.
        textFlow.setOnContextMenuRequested(e -> {
            menu.show(textFlow, e.getScreenX(), e.getScreenY());
            e.consume();
        });
        scrollPane.setOnContextMenuRequested(e -> {
            menu.show(scrollPane, e.getScreenX(), e.getScreenY());
            e.consume();
        });

        // Ctrl+C / Cmd+C copies all output. Bound at the ScrollPane
        // level so it fires regardless of which child has focus.
        KeyCombination copyShortcut = KeyCombination.keyCombination("Shortcut+C");
        Node focusHost = scrollPane;
        focusHost.setFocusTraversable(true);
        focusHost.setOnKeyPressed(e -> {
            if (copyShortcut.match(e)) {
                copyAllToClipboard();
                e.consume();
            } else if (e.getCode() == KeyCode.A && e.isShortcutDown()) {
                // Select-all doesn't apply here (no visible selection UI
                // yet), but behave like it by copying everything --
                // matches user intent when muscle-memory fires Ctrl+A
                // before Ctrl+C.
                copyAllToClipboard();
                e.consume();
            }
        });
    }

    /** Concatenate every {@link Text} child into a single string and
     *  place it on the system clipboard. Text nodes already carry
     *  trailing newlines from {@link #appendLine}. */
    public void copyAllToClipboard() {
        if (textFlow == null) return;
        StringBuilder sb = new StringBuilder();
        for (Node n : textFlow.getChildren()) {
            if (n instanceof Text t) sb.append(t.getText());
        }
        ClipboardContent content = new ClipboardContent();
        content.putString(sb.toString());
        Clipboard.getSystemClipboard().setContent(content);
    }

    /**
     * Append a styled line to the console. A trailing newline is added so
     * consecutive calls render on separate lines even though TextFlow itself
     * does not wrap on boundaries.
     */
    public void appendLine(String text, ConsoleStyle style) {
        if (textFlow == null) {
            return;
        }
        Runnable append = () -> {
            Text node = new Text(text + "\n");
            node.setFill(colorFor(style));
            node.setFont(fontFor(style));
            textFlow.getChildren().add(node);
            // No explicit setVvalue here -- the heightProperty listener
            // attached in the constructor fires on the next layout pass
            // and pins scroll to the new bottom then, which is after the
            // node has actually been measured.
        };
        if (Platform.isFxApplicationThread()) {
            append.run();
        } else {
            Platform.runLater(append);
        }
    }

    /**
     * Remove all previously appended output.
     */
    public void clear() {
        if (textFlow == null) {
            return;
        }
        if (Platform.isFxApplicationThread()) {
            textFlow.getChildren().clear();
        } else {
            Platform.runLater(textFlow.getChildren()::clear);
        }
    }

    /**
     * Map a ConsoleStyle to a JavaFX Color. Colors are chosen to match the
     * dark-terminal palette used by ConsoleColors so TTY and GUI output look
     * consistent even though no ANSI codes are involved in the GUI path.
     */
    private static Color colorFor(ConsoleStyle style) {
        if (style == null) {
            return Color.web("#d4d4d4");
        }
        switch (style) {
            case ERROR:   return Color.web("#f44747"); // red
            case SUCCESS: return Color.web("#4ec9b0"); // green
            case HEADER:  return Color.web("#569cd6"); // bright cyan/blue
            case ACCENT:  return Color.web("#dcdcaa"); // yellow
            case DIM:     return Color.web("#808080"); // grey
            case BOLD:    return Color.web("#d4d4d4"); // foreground, bold via font
            case NONE:
            default:      return Color.web("#d4d4d4"); // default foreground
        }
    }

    private static Font fontFor(ConsoleStyle style) {
        return style == ConsoleStyle.BOLD ? BOLD_FONT : REGULAR_FONT;
    }
}
