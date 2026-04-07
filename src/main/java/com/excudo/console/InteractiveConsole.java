package com.excudo.console;

import com.excudo.core.orchestration.*;
import com.excudo.exceptions.XMLParsingException;
import com.excudo.core.utils.Logger;
import com.excudo.core.utils.ComponentLogger;
import com.excudo.console.utils.ConsoleColors;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Scanner;

/**
 * Interactive console application for presentation editing
 * Supports both direct commands and LLM-powered natural language editing
 */
public class InteractiveConsole {

    private static final ComponentLogger logger = Logger.console();
    private ConsoleEngine consoleEngine;
    private BufferedReader reader;
    private Scanner scanner;
    private boolean running = true;

    public InteractiveConsole() {
        try {
            PPTXOrchestratorImpl orchestrator = new PPTXOrchestratorImpl();
            this.consoleEngine = new TTYConsoleEngine();
            this.consoleEngine.initialize(orchestrator);
        } catch (XMLParsingException e) {
            logger.error("Error initializing orchestrator: {}", e.getMessage());
            this.consoleEngine = null;
        }
        this.reader = new BufferedReader(new InputStreamReader(System.in));
        this.scanner = new Scanner(reader);

        if (this.consoleEngine != null) {
            this.consoleEngine.setSharedScanner(this.scanner);
        }
    }

    /**
     * Start the interactive console
     */
    public void start() {
        printWelcome();

        while (running) {
            boolean inArrangeMode = consoleEngine instanceof AbstractConsoleEngine ace
                && ace.isArrangeMode();

            System.out.print("\n" + buildPrompt());

            String command;
            try {
                if (inArrangeMode) {
                    command = readArrangeModeInput();
                } else {
                    command = reader.readLine();
                }
            } catch (IOException e) {
                break;
            }
            if (command == null) {
                System.out.println("Starting console mode...");
                break;
            }
            command = command.trim();

            if (command.isEmpty()) continue;

            try {
                // Re-check since the input read may have taken a while
                inArrangeMode = consoleEngine instanceof AbstractConsoleEngine ace2
                    && ace2.isArrangeMode();

                if (!inArrangeMode
                        && (command.toLowerCase().equals("exit") || command.toLowerCase().equals("quit"))) {
                    if (consoleEngine != null && consoleEngine.hasUnsavedChanges()) {
                        System.out.print(ConsoleColors.accent("You have unsaved changes. Exit anyway? (y/n) "));
                        try {
                            String answer = reader.readLine();
                            if (answer != null) {
                                answer = answer.trim().toLowerCase();
                                if (!answer.equals("y") && !answer.equals("yes")) {
                                    continue;
                                }
                            }
                        } catch (IOException ignored) {}
                    }
                    running = false;
                } else if (consoleEngine != null) {
                    consoleEngine.executeCommand(command);
                } else {
                    System.err.println("Console engine not initialized");
                }
            } catch (Exception e) {
                logger.error("Command error: {}", e.getMessage());
            }
        }

        if (consoleEngine != null) {
            consoleEngine.shutdown();
        }
        try { reader.close(); } catch (IOException ignored) {}
        System.out.println(ConsoleColors.dim("Goodbye."));
    }

    /** Pause (ms) between drain cycles during paste detection. */
    private static final int PASTE_SETTLE_MS = 50;
    /** Maximum time (ms) to wait for chunked paste data. */
    private static final int MAX_PASTE_WAIT_MS = 2000;

    /**
     * Read multi-line input in arrange mode.
     *
     * Two phases:
     *   1. Paste phase -- if reader.ready() after the first line, drain all
     *      buffered data (including blank lines) until the paste stream stops.
     *   2. Interactive phase -- show "... " continuation prompt and accumulate
     *      lines until a blank line is entered.
     *
     * This means pasted blank lines are preserved in the input, and the user
     * explicitly submits with a blank line only after the paste has settled.
     */
    private String readArrangeModeInput() throws IOException {
        String firstLine = reader.readLine();
        if (firstLine == null) return null;
        firstLine = firstLine.trim();

        // Slash-commands are single-line, send immediately.
        if (firstLine.startsWith("/")) {
            return firstLine;
        }

        if (firstLine.isEmpty()) {
            return "";
        }

        StringBuilder buffer = new StringBuilder(firstLine);
        int lineCount = 1;

        // Phase 1: drain pasted content (blank lines included).
        try {
            Thread.sleep(PASTE_SETTLE_MS);
            while (reader.ready()) {
                // Drain everything currently buffered.
                while (reader.ready()) {
                    String line = reader.readLine();
                    if (line == null) break;
                    buffer.append('\n').append(line);
                    lineCount++;
                }
                // Brief pause for more chunks.
                Thread.sleep(PASTE_SETTLE_MS);
            }
        } catch (InterruptedException ignored) {}

        if (lineCount > 1) {
            System.out.println(ConsoleColors.dim(
                "  (" + lineCount + " lines pasted)"));
        }

        // Phase 2: interactive accumulation -- blank line submits.
        while (true) {
            System.out.print(ConsoleColors.dim("... "));
            String line = reader.readLine();
            if (line == null) break;

            if (line.trim().isEmpty()) {
                break;
            }

            buffer.append('\n').append(line);
            lineCount++;
        }

        return buffer.toString().trim();
    }

    private String buildPrompt() {
        String arrangeIndicator = "";
        if (consoleEngine instanceof AbstractConsoleEngine ace && ace.isArrangeMode()) {
            arrangeIndicator = ConsoleColors.accent(" [arrange]");
        }

        if (consoleEngine != null && consoleEngine.isPresentationLoaded()
                && consoleEngine instanceof AbstractConsoleEngine ace) {
            java.io.File currentFile = ace.getCurrentFile();
            String filename = currentFile != null ? currentFile.getName() : "untitled";
            return ConsoleColors.accent("pptx") + ConsoleColors.dim(":" + filename)
                + arrangeIndicator + "> ";
        }
        return ConsoleColors.accent("pptx") + arrangeIndicator + "> ";
    }

    private void printWelcome() {
        System.out.println(getWelcomeMessage());
    }

    public static String getWelcomeMessage() {
        String banner = """
            ╔════════════════════════════════════════════════════╗
            ║                 Excudo Console                     ║
            ║          Comprehensive .pptx Toolchain             ║
            ╚════════════════════════════════════════════════════╝""";
        String tagline = "            Type 'help' for commands, 'help <topic>' for details, or 'load <file>' to start";
        return ConsoleColors.header(banner) + "\n\n" + ConsoleColors.dim(tagline);
    }

    public static void main(String[] args) {
        InteractiveConsole console = new InteractiveConsole();
        console.start();
    }
}
