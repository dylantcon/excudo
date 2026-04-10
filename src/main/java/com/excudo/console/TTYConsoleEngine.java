package com.excudo.console;

import java.util.Scanner;
import com.excudo.core.commands.CommandInvoker;
import com.excudo.console.utils.ConsoleColors;

/**
 * Terminal/TTY implementation of ConsoleEngine for command-line usage.
 * Uses standard streams and ANSI escape sequences for color.
 */
public class TTYConsoleEngine extends AbstractConsoleEngine {

    /**
     * TTY-specific LLM command handling using scanner input
     */
    @Override
    protected void handleLLMCommand(String subCommand) {
        handleLLMCommand(subCommand, null);
    }

    /**
     * Handle LLM command with provided scanner to avoid input stream conflicts
     */
    protected void handleLLMCommand(String subCommand, Scanner sharedScanner) {
        CommandInvoker invoker = getCurrentCommandInvoker();
        if (invoker == null) {
            displayError("No active session. Use 'session create <file>' or 'session switch <id>' first.");
            return;
        }

        // CRITICAL FIX: Use shared scanner from main console loop instead of creating new one
        // This prevents multiple Scanner instances from competing for the same input stream
        Scanner scanner = sharedScanner != null ? sharedScanner : new Scanner(inputStream);
        llmHandler.handleCommand(subCommand, scanner, invoker);
    }

    /**
     * Render a styled message to the terminal using ANSI escape codes.
     *
     * TTY is the only place in the pipeline where ANSI wrapping happens; the
     * GUI path renders the same ConsoleStyle by applying a JavaFX Color to a
     * Text node, so the severity info never round-trips through escape codes.
     */
    @Override
    public void displayStyled(String message, ConsoleStyle style) {
        switch (style) {
            case ERROR:
                errorStream.println(ConsoleColors.error("[ERROR] " + message));
                break;
            case SUCCESS:
                outputStream.println(ConsoleColors.success("[OK] " + message));
                break;
            case HEADER:
                outputStream.println(ConsoleColors.header(message));
                break;
            case ACCENT:
                outputStream.println(ConsoleColors.accent(message));
                break;
            case DIM:
                outputStream.println(ConsoleColors.dim(message));
                break;
            case BOLD:
                outputStream.println(ConsoleColors.bold(message));
                break;
            case NONE:
            default:
                outputStream.println(message);
                break;
        }
    }
}
