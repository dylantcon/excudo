package com.excudo.view.console;

import com.excudo.console.ConsoleStyle;
import javafx.application.Platform;
import javafx.scene.control.ScrollPane;
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
            if (scrollPane != null) {
                scrollPane.setVvalue(1.0);
            }
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
