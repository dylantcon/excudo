package com.excudo.console;

import java.util.ArrayList;
import java.util.List;

/**
 * Reusable test fixture for {@link AbstractConsoleEngine}. Captures
 * styled output and records invocations of the hooks that would
 * otherwise require a full orchestrator / LLM stack, so dispatcher-
 * level tests can run without standing either up.
 *
 * Subclass and override further methods as needed for tests that
 * want to observe additional behaviour (e.g., MCP server start).
 * Inspection fields are public so tests can assert directly on
 * them without an access-ceremony layer.
 */
public class RecordingConsoleEngine extends AbstractConsoleEngine {

    public final List<Entry> entries = new ArrayList<>();
    public final List<String> arrangeInputs = new ArrayList<>();
    public int enterArrangeCalls = 0;
    public int exitArrangeCalls = 0;

    public record Entry(String message, ConsoleStyle style) {}

    @Override
    public void displayStyled(String message, ConsoleStyle style) {
        entries.add(new Entry(message, style));
    }

    @Override
    protected void handleLLMCommand(String subCommand) {
        // no-op for tests
    }

    @Override
    protected void handleArrangeModeInput(String input) {
        // Skip the real LLM round-trip; just record what was passed in.
        arrangeInputs.add(input);
    }

    @Override
    public void enterArrangeMode() {
        // Skip the real llmHandler precondition; flip the flag so dispatcher
        // tests can observe routing decisions without a configured LLM.
        enterArrangeCalls++;
        this.arrangeMode = true;
    }

    @Override
    public void exitArrangeMode() {
        exitArrangeCalls++;
        this.arrangeMode = false;
    }

    // ========== Inspection helpers ==========

    public void clearRecordings() {
        entries.clear();
        arrangeInputs.clear();
        enterArrangeCalls = 0;
        exitArrangeCalls = 0;
    }

    public Entry last() {
        return entries.isEmpty() ? null : entries.get(entries.size() - 1);
    }

    public boolean hasMessageContaining(String substring) {
        return entries.stream().anyMatch(e -> e.message.contains(substring));
    }

    public long countByStyle(ConsoleStyle style) {
        return entries.stream().filter(e -> e.style == style).count();
    }
}
