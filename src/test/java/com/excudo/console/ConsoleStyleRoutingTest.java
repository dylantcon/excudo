package com.excudo.console;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

/**
 * Verifies that AbstractConsoleEngine's displayMessage/displayError/
 * displaySuccess forwarders route through displayStyled with the correct
 * ConsoleStyle.
 *
 * This is a headless test (no JavaFX, no TTY) using a minimal recording
 * stub that captures (message, style) pairs for inspection.
 */
public class ConsoleStyleRoutingTest {

    /** Stub engine that records styled output without doing any I/O. */
    private static class RecordingEngine extends AbstractConsoleEngine {
        final List<Entry> entries = new ArrayList<>();

        @Override
        public void displayStyled(String message, ConsoleStyle style) {
            entries.add(new Entry(message, style));
        }

        @Override
        protected void handleLLMCommand(String subCommand) {
            // no-op for test
        }
    }

    private static class Entry {
        final String message;
        final ConsoleStyle style;

        Entry(String message, ConsoleStyle style) {
            this.message = message;
            this.style = style;
        }
    }

    @Test
    public void displayMessageRoutesAsNone() {
        RecordingEngine engine = new RecordingEngine();
        engine.displayMessage("plain text");

        assertEquals(1, engine.entries.size());
        assertEquals("plain text", engine.entries.get(0).message);
        assertEquals(ConsoleStyle.NONE, engine.entries.get(0).style);
    }

    @Test
    public void displayErrorRoutesAsError() {
        RecordingEngine engine = new RecordingEngine();
        engine.displayError("boom");

        assertEquals(1, engine.entries.size());
        assertEquals("boom", engine.entries.get(0).message);
        assertEquals(ConsoleStyle.ERROR, engine.entries.get(0).style);
    }

    @Test
    public void displaySuccessRoutesAsSuccess() {
        RecordingEngine engine = new RecordingEngine();
        engine.displaySuccess("ok");

        assertEquals(1, engine.entries.size());
        assertEquals("ok", engine.entries.get(0).message);
        assertEquals(ConsoleStyle.SUCCESS, engine.entries.get(0).style);
    }

    @Test
    public void displayStyledPreservesExplicitStyle() {
        RecordingEngine engine = new RecordingEngine();
        engine.displayStyled("section", ConsoleStyle.HEADER);
        engine.displayStyled("progress...", ConsoleStyle.DIM);
        engine.displayStyled("hint", ConsoleStyle.ACCENT);

        assertEquals(3, engine.entries.size());
        assertEquals(ConsoleStyle.HEADER, engine.entries.get(0).style);
        assertEquals(ConsoleStyle.DIM, engine.entries.get(1).style);
        assertEquals(ConsoleStyle.ACCENT, engine.entries.get(2).style);
    }

    @Test
    public void consoleStyleEnumCoversAllSemanticKinds() {
        // Guard against accidental enum removals breaking consumers.
        ConsoleStyle[] styles = ConsoleStyle.values();
        assertTrue("ConsoleStyle should define at least 7 kinds", styles.length >= 7);

        // Verify named constants exist
        assertEquals(ConsoleStyle.NONE, ConsoleStyle.valueOf("NONE"));
        assertEquals(ConsoleStyle.ERROR, ConsoleStyle.valueOf("ERROR"));
        assertEquals(ConsoleStyle.SUCCESS, ConsoleStyle.valueOf("SUCCESS"));
        assertEquals(ConsoleStyle.HEADER, ConsoleStyle.valueOf("HEADER"));
        assertEquals(ConsoleStyle.ACCENT, ConsoleStyle.valueOf("ACCENT"));
        assertEquals(ConsoleStyle.DIM, ConsoleStyle.valueOf("DIM"));
        assertEquals(ConsoleStyle.BOLD, ConsoleStyle.valueOf("BOLD"));
    }
}
