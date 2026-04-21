package com.excudo.core.orchestration;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Pins the six invariants of SessionManager's active-session pointer
 * introduced in the Session Unification refactor (Phase 1):
 *
 * 1. SessionManager is the sole authoritative pointer; setActiveSession
 *    moves it atomically and fires on every registered listener.
 * 2. setActiveSession(null) is valid and fires (null, null).
 * 3. closeSession(activeSessionId) implicitly clears the active pointer
 *    and fires (null, null). Closing a non-active session does not.
 * 4. Listeners fire on the caller's thread (no Platform.runLater).
 * 5. setActiveSession is idempotent -- setting the same id still fires.
 * 6. getActiveOrchestrator never throws -- returns null for every
 *    failure mode.
 */
public class SessionManagerActiveSessionTest {

    private SessionManager manager;
    private RecordingListener listener;
    private String sessionA;
    private String sessionB;

    @Before
    public void setUp() throws Exception {
        // Singleton -- clear any residual active pointer + sessions from
        // previous tests so assertions are deterministic.
        manager = SessionManager.getInstance();
        manager.setActiveSession(null);
        for (String id : new ArrayList<>(manager.getActiveSessionIds())) {
            manager.closeSession(id);
        }
        listener = new RecordingListener();
        manager.addStateListener(listener);

        // Create two sessions via the empty-session path -- fast, no
        // PPTX file needed.
        sessionA = manager.createNewSession();
        sessionB = manager.createNewSession();
        // The create-session path fires onPresentationLoaded; drain
        // that from the recording listener so subsequent assertions
        // only see onActiveSessionChanged events.
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

    // --- Invariant 1: single authoritative pointer ---

    @Test
    public void setActiveSession_updatesPointerAndFiresListener() {
        manager.setActiveSession(sessionA);

        assertEquals(sessionA, manager.getActiveSessionId());
        assertNotNull("orchestrator should be resolvable for active session",
            manager.getActiveOrchestrator());
        assertEquals(1, listener.activeChanges.size());
        assertEquals(sessionA, listener.activeChanges.get(0).sessionId);
        assertSame("listener receives the active session's orchestrator",
            manager.getActiveOrchestrator(), listener.activeChanges.get(0).orchestrator);
    }

    // --- Invariant 2: null is valid ---

    @Test
    public void setActiveSession_nullIsValidAndFires() {
        manager.setActiveSession(sessionA);
        listener.activeChanges.clear();

        manager.setActiveSession(null);

        assertNull(manager.getActiveSessionId());
        assertNull(manager.getActiveOrchestrator());
        assertEquals(1, listener.activeChanges.size());
        assertNull(listener.activeChanges.get(0).sessionId);
        assertNull(listener.activeChanges.get(0).orchestrator);
    }

    // --- Invariant 3: close-active clears; close-non-active does not ---

    @Test
    public void closeSession_onActive_clearsPointer() {
        manager.setActiveSession(sessionA);
        listener.activeChanges.clear();

        manager.closeSession(sessionA);

        assertNull("active pointer cleared after closing the active session",
            manager.getActiveSessionId());
        // Listener receives a single (null, null) onActiveSessionChanged.
        assertEquals(1, listener.activeChanges.size());
        assertNull(listener.activeChanges.get(0).sessionId);
        assertNull(listener.activeChanges.get(0).orchestrator);
    }

    @Test
    public void closeSession_onNonActive_doesNotFireActiveChange() {
        manager.setActiveSession(sessionA);
        listener.activeChanges.clear();

        manager.closeSession(sessionB);

        assertEquals("active pointer unchanged when a non-active session closes",
            sessionA, manager.getActiveSessionId());
        assertEquals(0, listener.activeChanges.size());
    }

    // --- Invariant 4: listener fires on caller thread ---

    @Test
    public void listenerFiresOnCallerThread() throws Exception {
        final Thread[] firedOn = new Thread[1];
        OrchestrationStateListener inline = new OrchestrationStateListener() {
            @Override
            public void onActiveSessionChanged(String sessionId, PPTXOrchestrator orch) {
                firedOn[0] = Thread.currentThread();
            }
        };
        manager.addStateListener(inline);

        Thread runner = new Thread(() -> manager.setActiveSession(sessionA), "test-caller");
        runner.start();
        runner.join();

        assertSame("listener fired on the thread that called setActiveSession",
            runner, firedOn[0]);
        manager.removeStateListener(inline);
    }

    // --- Invariant 5: idempotent ---

    @Test
    public void setActiveSession_idempotentStillFires() {
        manager.setActiveSession(sessionA);
        listener.activeChanges.clear();

        manager.setActiveSession(sessionA);

        assertEquals("re-setting the same id still fires the listener",
            1, listener.activeChanges.size());
        assertEquals(sessionA, listener.activeChanges.get(0).sessionId);
    }

    // --- Invariant 6: getActiveOrchestrator never throws ---

    @Test
    public void getActiveOrchestrator_nullWhenNoActive() {
        manager.setActiveSession(null);
        assertNull(manager.getActiveOrchestrator());
    }

    @Test
    public void getActiveOrchestrator_nullWhenUnknownId() {
        // Manually set to an id that was never in activeSessions. The
        // pointer is a string; there's no referential-integrity check.
        manager.setActiveSession("no-such-session-id");

        assertNull("getActiveOrchestrator returns null for unknown session id",
            manager.getActiveOrchestrator());
        assertTrue("getActiveSession returns empty for unknown id",
            manager.getActiveSession().isEmpty());
    }

    // --- Test harness ---

    /** Records every onActiveSessionChanged call for assertion. */
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
