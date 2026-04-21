package com.excudo.integration;

import com.excudo.core.orchestration.OrchestrationStateListener;
import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.orchestration.SessionManager;
import com.excudo.view.console.UIConsoleEngine;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Codifies the bug class that the Session Unification refactor closed:
 * a session created on one surface (e.g. MCP or the GUI's embedded
 * console) must be visible to every other surface via
 * {@link SessionManager#getActiveOrchestrator()}, without any surface-
 * specific handoff callback.
 *
 * <p>Concrete scenario from the user report: MCP loads a deck, GUI
 * should be able to pick slides + save. Before the refactor, the GUI's
 * MainController held its own sessionless {@code PPTXOrchestrator}
 * instance and was only informed of the MCP-created session via a
 * UIConsoleEngine-local callback that MCPConsoleEngine never fired.
 *
 * <p>This test simulates the GUI by registering an
 * {@link OrchestrationStateListener} that records every
 * {@code onActiveSessionChanged} fire, and asserts:
 *  1. creating a session via a UIConsoleEngine-equivalent path fires
 *     the listener with the new id + orchestrator;
 *  2. the listener's received orchestrator is identity-equal to
 *     {@code SessionManager.getActiveOrchestrator()} after the event;
 *  3. switching to a second session re-fires with the new id;
 *  4. closing the active session fires with {@code (null, null)}.
 */
public class CrossSurfaceSessionTest {

    private SessionManager manager;
    private RecordingListener gui;

    @Before
    public void setUp() {
        manager = SessionManager.getInstance();
        // Reset global active pointer + close any residual sessions so
        // the assertions are deterministic regardless of prior tests.
        manager.setActiveSession(null);
        for (String id : new ArrayList<>(manager.getActiveSessionIds())) {
            manager.closeSession(id);
        }
        gui = new RecordingListener();
        manager.addStateListener(gui);
    }

    @After
    public void tearDown() {
        if (manager != null) {
            manager.setActiveSession(null);
            for (String id : new ArrayList<>(manager.getActiveSessionIds())) {
                manager.closeSession(id);
            }
            if (gui != null) manager.removeStateListener(gui);
        }
    }

    @Test
    public void engineCreatesSession_guiListenerSeesIt() throws Exception {
        UIConsoleEngine engine = bootEngine();

        gui.activeChanges.clear();
        String sessionId = createEmptySessionVia(engine);

        // 1) Listener got exactly one active-session fire.
        assertEquals("GUI listener received a single active-session change",
            1, gui.activeChanges.size());

        // 2) The id + orchestrator match the new session.
        RecordingListener.Change change = gui.activeChanges.get(0);
        assertEquals(sessionId, change.sessionId);
        assertNotNull("orchestrator delivered to listener", change.orchestrator);

        // 3) The orchestrator the listener got is the same one
        //    SessionManager.getActiveOrchestrator() returns -- the GUI
        //    can read from SessionManager directly and always see the
        //    engine-created session.
        assertSame("listener orchestrator == SessionManager active orchestrator",
            manager.getActiveOrchestrator(), change.orchestrator);
        assertSame("listener orchestrator == engine's current orchestrator",
            engine.getCurrentOrchestrator(), change.orchestrator);
    }

    @Test
    public void switchingSessions_listenerSeesEachMove() throws Exception {
        UIConsoleEngine engine = bootEngine();

        String first = createEmptySessionVia(engine);
        gui.activeChanges.clear();

        String second = createEmptySessionVia(engine);
        assertEquals("second session -> one fire with new id",
            1, gui.activeChanges.size());
        assertEquals(second, gui.activeChanges.get(0).sessionId);
        assertNotEquals("ids are distinct", first, second);
    }

    @Test
    public void closingActiveSession_firesNullChange() throws Exception {
        UIConsoleEngine engine = bootEngine();

        String sessionId = createEmptySessionVia(engine);
        gui.activeChanges.clear();

        boolean closed = manager.closeSession(sessionId);
        assertTrue(closed);

        assertEquals("closing the active session fires the listener",
            1, gui.activeChanges.size());
        assertNull("fire carries a null session id",
            gui.activeChanges.get(0).sessionId);
        assertNull("fire carries a null orchestrator",
            gui.activeChanges.get(0).orchestrator);
        assertNull("SessionManager.getActiveOrchestrator now returns null",
            manager.getActiveOrchestrator());
    }

    /**
     * Construct + initialise a UIConsoleEngine the same way the GUI
     * and the MCP server do. initialize() requires a non-null
     * orchestrator because AbstractCommandFactory validates against it.
     */
    private UIConsoleEngine bootEngine() throws Exception {
        UIConsoleEngine engine = new UIConsoleEngine(null);
        engine.initialize(new com.excudo.core.orchestration.PPTXOrchestratorImpl());
        return engine;
    }

    /**
     * Create an empty session through the console-engine entry point
     * (protected createEmptySessionDirect), matching what an MCP
     * {@code new presentation} or GUI-menu-create would trigger.
     */
    private String createEmptySessionVia(UIConsoleEngine engine) throws Exception {
        java.lang.reflect.Method m = engine.getClass().getSuperclass()
            .getDeclaredMethod("createEmptySessionDirect");
        m.setAccessible(true);
        m.invoke(engine);
        return manager.getActiveSessionId();
    }

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
