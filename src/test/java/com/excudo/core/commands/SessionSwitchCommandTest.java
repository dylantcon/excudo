package com.excudo.core.commands;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SessionSwitchCommand.
 */
public class SessionSwitchCommandTest {

    // ========== Stub implementations ==========

    private static class StubSessionInfo implements CommandSessionManager.SessionInfo {
        private final String sessionId;
        private final String filename;

        StubSessionInfo(String sessionId, String filename) {
            this.sessionId = sessionId;
            this.filename = filename;
        }

        @Override public String getSessionId() { return sessionId; }
        @Override public String getFilename() { return filename; }
        @Override public boolean isActive() { return true; }
        @Override public long getCreationTime() { return 0; }
        @Override public int getSlideCount() { return 5; }
        @Override public String getTitle() { return "Test"; }
    }

    private static class StubSessionManager implements CommandSessionManager {
        final Map<String, SessionInfo> sessions = new HashMap<>();
        SessionResult lastSwitchResult;

        @Override public SessionResult createSession(String f) { return null; }
        @Override public SessionResult createEmptySession() { return null; }
        @Override public Map<String, SessionInfo> getAllSessions() { return sessions; }
        @Override public SessionInfo getSessionInfo(String id) { return sessions.get(id); }
        @Override public boolean closeSession(String id) { return false; }

        @Override
        public SessionResult switchToSession(String sessionId) {
            if (!sessions.containsKey(sessionId)) {
                lastSwitchResult = new SimpleResult(false, null, "Session not found: " + sessionId);
            } else {
                lastSwitchResult = new SimpleResult(true, sessionId, null);
            }
            return lastSwitchResult;
        }
    }

    private static class SimpleResult implements SessionResult {
        private final boolean success;
        private final String sessionId;
        private final String error;

        SimpleResult(boolean success, String sessionId, String error) {
            this.success = success;
            this.sessionId = sessionId;
            this.error = error;
        }

        @Override public boolean isSuccess() { return success; }
        @Override public String getSessionId() { return sessionId; }
        @Override public com.excudo.core.orchestration.PPTXOrchestrator getOrchestrator() { return null; }
        @Override public Object getLlmHandler() { return null; }
        @Override public File getCurrentFile() { return null; }
        @Override public String getErrorMessage() { return error; }
    }

    private static class StubSessionContext implements CommandSessionContext {
        String currentSessionId = "session-1";
        String lastSetSessionId;

        @Override public String getCurrentSessionId() { return currentSessionId; }
        @Override public com.excudo.core.orchestration.PPTXOrchestrator getCurrentOrchestrator() { return null; }
        @Override public File getCurrentFile() { return null; }
        @Override public void setCurrentFile(File f) {}
        @Override public CommandInvoker getCurrentCommandInvoker() { return null; }
        @Override public void clearCurrentSession() { currentSessionId = null; }

        @Override
        public void setCurrentSession(String id, com.excudo.core.orchestration.PPTXOrchestrator o, Object l, File f) {
            lastSetSessionId = id;
            currentSessionId = id;
        }
    }

    private static class StubDisplay implements CommandDisplay {
        final List<String> messages = new ArrayList<>();
        final List<String> errors = new ArrayList<>();
        final List<String> successes = new ArrayList<>();

        @Override public void displayMessage(String m) { messages.add(m); }
        @Override public void displayError(String m) { errors.add(m); }
        @Override public void displaySuccess(String m) { successes.add(m); }
    }

    // ========== Tests ==========

    @Test
    void switchesToExistingSession() {
        StubSessionManager mgr = new StubSessionManager();
        mgr.sessions.put("session-2", new StubSessionInfo("session-2", "deck.pptx"));

        StubSessionContext ctx = new StubSessionContext();
        StubDisplay display = new StubDisplay();

        SessionSwitchCommand cmd = new SessionSwitchCommand(mgr, ctx, display, "session-2");
        cmd.execute();

        assertTrue(cmd.isExecuted());
        assertEquals("session-2", ctx.lastSetSessionId);
        assertTrue(display.errors.isEmpty(), "No errors expected");
    }

    @Test
    void displayErrorForNonexistentSession() {
        StubSessionManager mgr = new StubSessionManager();
        StubSessionContext ctx = new StubSessionContext();
        StubDisplay display = new StubDisplay();

        SessionSwitchCommand cmd = new SessionSwitchCommand(mgr, ctx, display, "no-such-session");
        cmd.execute();

        assertTrue(cmd.isExecuted());
        assertFalse(display.errors.isEmpty(), "Should display error for missing session");
        assertNull(ctx.lastSetSessionId, "Should not update context for missing session");
    }

    @Test
    void skipsWhenAlreadyInTargetSession() {
        StubSessionManager mgr = new StubSessionManager();
        mgr.sessions.put("session-1", new StubSessionInfo("session-1", "deck.pptx"));

        StubSessionContext ctx = new StubSessionContext();
        ctx.currentSessionId = "session-1";
        StubDisplay display = new StubDisplay();

        SessionSwitchCommand cmd = new SessionSwitchCommand(mgr, ctx, display, "session-1");
        cmd.execute();

        assertTrue(cmd.isExecuted());
        assertFalse(display.messages.isEmpty(), "Should display already-in-session message");
    }

    @Test
    void cannotUndo() {
        StubSessionManager mgr = new StubSessionManager();
        StubSessionContext ctx = new StubSessionContext();
        StubDisplay display = new StubDisplay();

        SessionSwitchCommand cmd = new SessionSwitchCommand(mgr, ctx, display, "session-2");
        assertFalse(cmd.canUndo());
        assertThrows(CommandExecutionException.class, cmd::undo);
    }

    @Test
    void rejectsNullTargetSessionId() {
        StubSessionManager mgr = new StubSessionManager();
        StubSessionContext ctx = new StubSessionContext();
        StubDisplay display = new StubDisplay();

        assertThrows(IllegalArgumentException.class,
            () -> new SessionSwitchCommand(mgr, ctx, display, null));
    }

    @Test
    void cannotExecuteTwice() {
        StubSessionManager mgr = new StubSessionManager();
        mgr.sessions.put("s2", new StubSessionInfo("s2", "x.pptx"));

        StubSessionContext ctx = new StubSessionContext();
        StubDisplay display = new StubDisplay();

        SessionSwitchCommand cmd = new SessionSwitchCommand(mgr, ctx, display, "s2");
        cmd.execute();

        assertThrows(CommandExecutionException.class, cmd::execute);
    }
}
