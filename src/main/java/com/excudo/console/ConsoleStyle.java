package com.excudo.console;

/**
 * Semantic style tag for console output.
 *
 * Instead of serializing severity/emphasis into ANSI escape codes that the
 * GUI would then have to parse back, callers pass a ConsoleStyle alongside
 * their message. Each ConsoleEngine implementation renders the style in its
 * native medium: TTYConsoleEngine emits ANSI sequences via ConsoleColors, the
 * GUI's UIConsoleEngine applies a JavaFX Color to a styled Text node.
 *
 * Styles are intentionally coarse -- we only model the semantic kinds of
 * message the console produces, not arbitrary color combinations.
 */
public enum ConsoleStyle {
    /** Plain text with no emphasis. */
    NONE,
    /** Error messages. Rendered in red. */
    ERROR,
    /** Success / confirmation messages. Rendered in green. */
    SUCCESS,
    /** Section headers / banner text. Rendered in bright cyan. */
    HEADER,
    /** Accented text (prompts, highlights). Rendered in yellow. */
    ACCENT,
    /** Dimmed text (progress notes, secondary info). Rendered in grey. */
    DIM,
    /** Bold text without a specific color. */
    BOLD
}
