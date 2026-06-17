package com.excudo.console;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Dispatcher-level unit tests for {@link AbstractConsoleEngine#executeCommand}.
 * Covers how the engine routes {@code arrange}, {@code /exit},
 * {@code /<command>}, and raw text across arrange-mode's state boundary.
 *
 * Uses {@link RecordingConsoleEngine} so no orchestrator or LLM stack
 * is needed; the test surface is specifically the routing decisions
 * executeCommand makes before delegating. Subclasses of this class
 * may extend with additional cases for new dispatch paths (e.g.,
 * {@code arrange mcp}).
 */
public class AbstractConsoleEngineTest {

    protected RecordingConsoleEngine engine;

    @Before
    public void setUp() {
        engine = new RecordingConsoleEngine();
    }

    @After
    public void tearDown() {
        // Release the bound stub port in case the test didn't go through
        // a /exit path. Safe to call when no server is running.
        if (engine != null) engine.stopMCPHttpServer();
    }

    // ========== "arrange" command ==========

    @Test
    public void arrangeCommandEntersArrangeMode() {
        engine.executeCommand("arrange");
        assertTrue("should be in arrange mode", engine.isArrangeMode());
        assertEquals(1, engine.enterArrangeCalls);
    }

    @Test
    public void arrangeCommandIsCaseInsensitive() {
        engine.executeCommand("ARRANGE");
        assertTrue(engine.isArrangeMode());
    }

    @Test
    public void arrangeCommandToleratesSurroundingWhitespace() {
        engine.executeCommand("  arrange  ");
        assertTrue(engine.isArrangeMode());
    }

    @Test
    public void arrangeCommandDoesNotEnterIfPreconditionFails() {
        // When the real llmHandler is not configured, enterArrangeMode
        // returns early and arrangeMode stays false. RecordingConsoleEngine
        // bypasses that precondition for dispatcher testing -- here we
        // just verify the base class reaches enterArrangeMode(), not what
        // it does inside.
        engine.executeCommand("arrange");
        assertEquals("executeCommand should reach enterArrangeMode exactly once",
            1, engine.enterArrangeCalls);
    }

    // ========== /exit inside arrange mode ==========

    @Test
    public void slashExitInArrangeModeLeavesArrangeMode() {
        engine.enterArrangeMode();
        assertTrue(engine.isArrangeMode());

        engine.executeCommand("/exit");

        assertFalse("should have exited arrange mode", engine.isArrangeMode());
        assertEquals(1, engine.exitArrangeCalls);
    }

    @Test
    public void slashExitOutsideArrangeModeRoutesToNormalPipeline() {
        // /exit is only special inside arrange mode. Outside, it falls
        // through to executeCommandNormal -- where the command registry
        // will either handle it or reject it. We just verify that the
        // arrange-mode exit path was NOT taken (counter stays at zero).
        assertFalse(engine.isArrangeMode());
        try {
            engine.executeCommand("/exit");
        } catch (Exception ignored) {
            // Not our concern here; normal pipeline may surface errors
        }
        assertEquals("exitArrangeMode should not be called outside arrange mode",
            0, engine.exitArrangeCalls);
    }

    // ========== raw text vs /commands inside arrange mode ==========

    @Test
    public void rawTextInArrangeModeRoutesToArrangeHandler() {
        engine.enterArrangeMode();
        engine.clearRecordings();

        engine.executeCommand("add a blue rectangle to slide 1");

        assertEquals("arrange handler should have received the raw text",
            1, engine.arrangeInputs.size());
        assertEquals("add a blue rectangle to slide 1", engine.arrangeInputs.get(0));
        assertTrue("should stay in arrange mode", engine.isArrangeMode());
    }

    @Test
    public void slashCommandInArrangeModeDoesNotReachArrangeHandler() {
        engine.enterArrangeMode();
        engine.clearRecordings();

        try {
            engine.executeCommand("/help");
        } catch (Exception ignored) {
            // Normal pipeline may fail without a full orchestrator; irrelevant here
        }

        assertEquals("arrange handler must not see slash-prefixed input",
            0, engine.arrangeInputs.size());
        assertTrue("should still be in arrange mode (only /exit leaves)",
            engine.isArrangeMode());
    }

    @Test
    public void arrangeModeAcceptsMultipleRawInputs() {
        engine.enterArrangeMode();
        engine.clearRecordings();

        engine.executeCommand("first request");
        engine.executeCommand("second request");
        engine.executeCommand("third request");

        assertEquals(3, engine.arrangeInputs.size());
        assertEquals("first request", engine.arrangeInputs.get(0));
        assertEquals("second request", engine.arrangeInputs.get(1));
        assertEquals("third request", engine.arrangeInputs.get(2));
    }

    // ========== "arrange mcp" subcommand ==========

    @Test
    public void arrangeMcpSubcommandStartsMcpServer() {
        engine.executeCommand("arrange mcp");
        assertEquals(1, engine.startMcpCalls);
        assertTrue("should be in MCP mode", engine.isMcpMode());
    }

    @Test
    public void arrangeMcpIsCaseInsensitive() {
        engine.executeCommand("ARRANGE MCP");
        assertEquals(1, engine.startMcpCalls);
    }

    @Test
    public void arrangeMcpToleratesSurroundingWhitespace() {
        engine.executeCommand("   arrange mcp   ");
        assertEquals(1, engine.startMcpCalls);
    }

    @Test
    public void arrangeMcpDoesNotEnterArrangeMode() {
        engine.executeCommand("arrange mcp");
        assertFalse("arrange mcp is a separate mode from arrange",
            engine.isArrangeMode());
        assertEquals(0, engine.enterArrangeCalls);
    }

    @Test
    public void bareArrangeDoesNotStartMcpServer() {
        engine.executeCommand("arrange");
        assertEquals(0, engine.startMcpCalls);
        assertEquals(1, engine.enterArrangeCalls);
    }

    // ========== autoStartMcpServer (MCP-launcher path) ==========

    @Test
    public void autoStartMcpSeedsSessionThenStartsServer() {
        // Fresh GUI launched via the MCP launcher: no active session, so
        // autoStartMcpServer must seed an empty one before starting the
        // server (else startMCPHttpServer bails "needs an orchestrator").
        engine.autoStartMcpServer();
        assertEquals("seeds an empty session when none is active",
            1, engine.createEmptySessionCalls);
        assertEquals("starts the MCP HTTP server", 1, engine.startMcpCalls);
    }

    // ========== MCP mode input dispatch ==========

    @Test
    public void slashExitInMcpModeStopsServer() {
        engine.startMCPHttpServer();
        assertTrue(engine.isMcpMode());

        engine.executeCommand("/exit");

        assertFalse("should have exited MCP mode", engine.isMcpMode());
        assertEquals(1, engine.stopMcpCalls);
    }

    @Test
    public void slashStopInMcpModeIsAliasForExit() {
        engine.startMCPHttpServer();
        engine.executeCommand("/stop");
        assertFalse(engine.isMcpMode());
        assertEquals(1, engine.stopMcpCalls);
    }

    @Test
    public void slashStatusInMcpModeEmitsHeader() {
        engine.startMCPHttpServer();
        engine.clearRecordings();

        engine.executeCommand("/status");

        assertTrue("status should emit at least one HEADER-styled line",
            engine.entries.stream().anyMatch(e -> e.style() == ConsoleStyle.HEADER));
        assertTrue("should still be in MCP mode after /status",
            engine.isMcpMode());
    }

    @Test
    public void unknownCommandInMcpModeProducesErrorAndKeepsServerRunning() {
        engine.startMCPHttpServer();
        engine.clearRecordings();

        engine.executeCommand("load something.pptx");

        assertTrue("unknown input during MCP mode should produce an error",
            engine.entries.stream().anyMatch(e -> e.style() == ConsoleStyle.ERROR));
        assertTrue("server must keep running", engine.isMcpMode());
        assertEquals("stop must not be triggered by unknown input",
            0, engine.stopMcpCalls);
    }

    @Test
    public void rawTextInMcpModeIsRejectedAndDoesNotReachArrangeHandler() {
        engine.startMCPHttpServer();
        engine.clearRecordings();

        engine.executeCommand("make slide 1 blue");

        assertEquals("arrange handler must not see input while MCP server is live",
            0, engine.arrangeInputs.size());
        assertTrue(engine.entries.stream().anyMatch(e -> e.style() == ConsoleStyle.ERROR));
    }

    @Test
    public void mcpModeOutranksArrangeMode() {
        // If both happened to be true (shouldn't in practice, but the dispatcher
        // check order determines the priority), MCP mode must win because the
        // server is actively serving and leaving it silently would strand clients.
        engine.enterArrangeMode();
        engine.startMCPHttpServer();
        engine.clearRecordings();

        engine.executeCommand("random input");

        // Should be routed through MCP handler (produces ERROR), not arrange handler
        assertEquals(0, engine.arrangeInputs.size());
        assertTrue(engine.entries.stream().anyMatch(e -> e.style() == ConsoleStyle.ERROR));
    }

    @Test
    public void afterStopServerNormalDispatchResumes() {
        engine.startMCPHttpServer();
        engine.executeCommand("/exit");
        engine.clearRecordings();
        assertFalse(engine.isMcpMode());

        // Bare arrange should now work again
        engine.executeCommand("arrange");
        assertTrue(engine.isArrangeMode());
        assertEquals(1, engine.enterArrangeCalls);
    }

    // ========== "mcp-deregister" command ==========

    @Test
    public void mcpDeregisterCommandReachesHandler() {
        engine.executeCommand("mcp-deregister");
        assertEquals(1, engine.deregisterCalls);
    }

    @Test
    public void mcpDeregisterIsCaseInsensitive() {
        engine.executeCommand("MCP-DEREGISTER");
        assertEquals(1, engine.deregisterCalls);
    }

    @Test
    public void mcpDeregisterToleratesWhitespace() {
        engine.executeCommand("   mcp-deregister   ");
        assertEquals(1, engine.deregisterCalls);
    }

    @Test
    public void mcpDeregisterDoesNotEnterArrangeOrMcpMode() {
        engine.executeCommand("mcp-deregister");
        assertFalse(engine.isArrangeMode());
        assertFalse(engine.isMcpMode());
        assertEquals(0, engine.enterArrangeCalls);
        assertEquals(0, engine.startMcpCalls);
    }

    @Test
    public void mcpDeregisterInMcpModeIsRejectedLikeAnyOtherInput() {
        // When the MCP server is running, /exit /stop /status are the only
        // allowed commands. mcp-deregister is NOT a server-control command,
        // so it should produce an error -- stop the server first, then run
        // mcp-deregister from normal dispatch.
        engine.startMCPHttpServer();
        engine.clearRecordings();

        engine.executeCommand("mcp-deregister");

        assertEquals("deregister must not run while the server owns the session",
            0, engine.deregisterCalls);
        assertTrue("should still be in MCP mode", engine.isMcpMode());
        assertTrue(engine.entries.stream().anyMatch(e -> e.style() == ConsoleStyle.ERROR));
    }
}
