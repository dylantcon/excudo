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
    public int startMcpCalls = 0;
    public int stopMcpCalls = 0;
    public int deregisterCalls = 0;
    public int createEmptySessionCalls = 0;

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

    @Override
    public void startMCPHttpServer() {
        startMcpCalls++;
        // Bind (allocates an ephemeral port, registers contexts) but do NOT
        // call serve() -- no accept loop starts. getUrl()/getPort()/getToken()
        // become valid so dispatcher tests that exercise /status don't hit
        // the unbound-transport guard. stopMCPHttpServer() releases the port.
        try {
            com.excudo.mcp.MCPHttpSseTransport t = new com.excudo.mcp.MCPHttpSseTransport();
            t.bind();
            this.activeMcpTransport = t;
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to bind stub MCP transport in test", e);
        }
    }

    @Override
    public void stopMCPHttpServer() {
        stopMcpCalls++;
        if (activeMcpTransport != null) {
            activeMcpTransport.stop(); // releases the bound port
            activeMcpTransport = null;
        }
    }

    @Override
    protected void deregisterFromClaudeDesktop() {
        // Don't actually touch the user's real Claude Desktop config from tests.
        deregisterCalls++;
    }

    @Override
    protected void createEmptySessionDirect() {
        // The real path needs a ConsoleSessionManager (not stood up in these
        // dispatcher-level tests); just record that autoStartMcpServer asked
        // to seed a session when none was active.
        createEmptySessionCalls++;
    }

    // ========== Inspection helpers ==========

    public void clearRecordings() {
        entries.clear();
        arrangeInputs.clear();
        enterArrangeCalls = 0;
        exitArrangeCalls = 0;
        startMcpCalls = 0;
        stopMcpCalls = 0;
        deregisterCalls = 0;
        createEmptySessionCalls = 0;
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
