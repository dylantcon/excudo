package com.excudo.core.orchestration;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Codifies the close-time semantics of the active-session pointer
 * (invariants 2 and 3 of the Session Unification plan):
 *
 * <ul>
 *   <li>Closing the active session clears the pointer and fires
 *       {@code onActiveSessionChanged(null, null)}.</li>
 *   <li>Closing a non-active session leaves the pointer intact and
 *       does NOT fire {@code onActiveSessionChanged}.</li>
 * </ul>
 *
 * Separate from {@link SessionManagerActiveSessionTest} because the
 * close path exercises slightly different integration surface
 * ({@link SessionManager#closeSession} vs direct
 * {@link SessionManager#setActiveSession} calls).
 */
public class ActiveSessionCloseSemanticsTest {

    private SessionManager manager;
    private RecordingListener listener;
    private String sessionA;
    private String sessionB;

    @Before
    public void setUp() throws Exception {
        manager = SessionManager.getInstance();
        manager.setActiveSession(null);
        for (String id : new ArrayList<>(manager.getActiveSessionIds())) {
            manager.closeSession(id);
        }
        listener = new RecordingListener();
        manager.addStateListener(listener);

        sessionA = manager.createNewSession();
        sessionB = manager.createNewSession();
        listener.activeChanges.clear();
    }

    @After
    public void tearDown() {
        if (manager != null) {
            manager.setActiveSession(null);
            for (String id : new ArrayList<>(manager.getActiveSessionIds())) {
                manager.closeSession(id);
            }
            if (listener != null) manager.removeStateListener(listener);
        }
    }

    @Test
    public void closeActiveSession_clearsPointerAndFiresNullChange() {
        manager.setActiveSession(sessionB);
        listener.activeChanges.clear();

        boolean closed = manager.closeSession(sessionB);

        assertTrue("close returned true", closed);
        assertNull("active pointer cleared", manager.getActiveSessionId());
        assertNull("active orchestrator is null", manager.getActiveOrchestrator());
        assertEquals("one active-change event fired", 1, listener.activeChanges.size());
        assertNull(listener.activeChanges.get(0).sessionId);
        assertNull(listener.activeChanges.get(0).orchestrator);
    }

    @Test
    public void closeNonActiveSession_leavesPointerIntact() {
        manager.setActiveSession(sessionA);
        listener.activeChanges.clear();

        boolean closed = manager.closeSession(sessionB);

        assertTrue(closed);
        assertEquals("active pointer unchanged", sessionA, manager.getActiveSessionId());
        assertEquals("no active-change event fired", 0, listener.activeChanges.size());
    }

    @Test
    public void closeNonExistentSession_isNoOp() {
        manager.setActiveSession(sessionA);
        listener.activeChanges.clear();

        boolean closed = manager.closeSession("does-not-exist");

        assertFalse("close-non-existent returned false", closed);
        assertEquals("active pointer unchanged", sessionA, manager.getActiveSessionId());
        assertEquals("no active-change event fired", 0, listener.activeChanges.size());
    }

    private static final class RecordingListener implements OrchestrationStateListener {
        final List<Change> activeChanges = new ArrayList<>();
        @Override
        public void onActiveSessionChanged(String sessionId, PPTXOrchestrator orchestrator) {
            activeChanges.add(new Change(sessionId, orchestrator));
        }
        static final class Change {
            final String sessionId;
            final PPTXOrchestrator orchestrator;
            Change(String s, PPTXOrchestrator o) { this.sessionId = s; this.orchestrator = o; }
        }
    }
}
