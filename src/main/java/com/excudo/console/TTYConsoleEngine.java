package com.excudo.console;

import java.util.Scanner;
import com.excudo.core.commands.CommandInvoker;
import com.excudo.console.utils.ConsoleColors;

/**
 * Terminal/TTY implementation of ConsoleEngine for command-line usage.
 * Uses standard streams and basic text output.
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
     * TTY-specific display methods using standard streams
     */
    @Override
    public void displayMessage(String message) {
        outputStream.println(message);
    }
    
    @Override
    public void displayError(String message) {
        errorStream.println(ConsoleColors.error("[ERROR] " + message));
    }

    @Override
    public void displaySuccess(String message) {
        outputStream.println(ConsoleColors.success("[OK] " + message));
    }
    
}