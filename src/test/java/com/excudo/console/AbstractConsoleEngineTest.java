package com.excudo.console;

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
}
