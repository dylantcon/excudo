package com.excudo.view.components;

import com.excudo.core.orchestration.OrchestrationStateListener;
import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.orchestration.SessionManager;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Session-tab UI. One tab per active session; clicking a tab routes
 * through {@link SessionManager#setActiveSession(String)}, whose
 * listener fan-out already refreshes every panel. A {@code +} button
 * creates a new empty session; tabs are closable (x on the tab itself)
 * with a save/discard/cancel dirty prompt when the session carries
 * unsaved changes.
 *
 * <p>State is mirrored from {@link SessionManager} via the
 * {@link OrchestrationStateListener} hook. The controller never owns
 * session state directly -- the single source of truth is the manager.
 * Tab ordering follows SessionManager's iteration order, which is
 * insertion-ordered by {@link java.util.LinkedHashMap}.
 */
public class SessionTabController implements OrchestrationStateListener {

    private TabPane tabPane;
    private Button newSessionButton;
    private final Map<String, Tab> tabsByLayerSessionId = new HashMap<>();
    private boolean suppressActivation = false;

    public SessionTabController() {
        SessionManager.getInstance().addStateListener(this);
    }

    public void setTabPane(TabPane tabPane) {
        this.tabPane = tabPane;
        if (tabPane != null) {
            // When the user clicks a tab, activate the backing session.
            tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
                if (suppressActivation || newTab == null) return;
                String sessionId = (String) newTab.getUserData();
                if (sessionId != null) {
                    try {
                        SessionManager.getInstance().setActiveSession(sessionId);
                    } catch (RuntimeException ex) {
                        showError("Could not switch session: " + ex.getMessage());
                    }
                }
            });
            // Intercept tab close requests so dirty sessions prompt first.
            tabPane.addEventFilter(Tab.TAB_CLOSE_REQUEST_EVENT, e -> {
                // Event filter runs before default close handler; not
                // wired here because JavaFX's tab close routing is
                // actually on each Tab via setOnCloseRequest.
            });
        }
        refreshFromManager();
    }

    public void setNewSessionButton(Button newSessionButton) {
        this.newSessionButton = newSessionButton;
        if (newSessionButton != null) {
            newSessionButton.setOnAction(e -> createNewSession());
        }
    }

    // ========== Listener methods ==========

    @Override
    public void onActiveSessionChanged(String sessionId, PPTXOrchestrator orchestrator) {
        Platform.runLater(this::refreshFromManager);
    }

    @Override
    public void onPresentationLoaded() {
        Platform.runLater(this::refreshFromManager);
    }

    @Override
    public void onPresentationClosed() {
        Platform.runLater(this::refreshFromManager);
    }

    // ========== Sync logic ==========

    /** Rebuild the tab list from SessionManager's current snapshot. */
    private void refreshFromManager() {
        if (tabPane == null) return;
        suppressActivation = true;
        try {
            SessionManager mgr = SessionManager.getInstance();
            var ids = mgr.getActiveSessionIds();
            // Remove tabs whose backing session disappeared.
            tabPane.getTabs().removeIf(t -> !ids.contains(t.getUserData()));
            tabsByLayerSessionId.keySet().retainAll(ids);
            // Add tabs for any new session.
            for (String id : ids) {
                if (tabsByLayerSessionId.containsKey(id)) continue;
                Tab t = buildTab(id);
                tabsByLayerSessionId.put(id, t);
                tabPane.getTabs().add(t);
            }
            // Refresh tab labels (file may have been saved-as, rename).
            for (String id : ids) {
                Tab t = tabsByLayerSessionId.get(id);
                if (t != null) t.setText(labelFor(id));
            }
            // Highlight the active tab.
            String active = mgr.getActiveSessionId();
            if (active != null && tabsByLayerSessionId.containsKey(active)) {
                tabPane.getSelectionModel().select(tabsByLayerSessionId.get(active));
            }
        } finally {
            suppressActivation = false;
        }
    }

    private Tab buildTab(String sessionId) {
        Tab tab = new Tab(labelFor(sessionId));
        tab.setUserData(sessionId);
        tab.setClosable(true);
        tab.setOnCloseRequest(e -> {
            if (!handleCloseRequest(sessionId)) {
                e.consume(); // cancel the close
            }
        });
        return tab;
    }

    /** @return true if the close should proceed, false to cancel. */
    private boolean handleCloseRequest(String sessionId) {
        Optional<SessionManager.ManagedSession> sessOpt =
            SessionManager.getInstance().getSession(sessionId);
        if (sessOpt.isEmpty()) return true;
        PPTXOrchestrator orch = sessOpt.get().getOrchestrator();
        boolean dirty = orch != null
            && orch.getContext().isPresent()
            && orch.getContext().get().getDocument() != null
            && orch.getContext().get().getDocument().hasUnsavedChanges();
        if (!dirty) {
            SessionManager.getInstance().closeSession(sessionId);
            return true;
        }
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Unsaved changes");
        alert.setHeaderText("This presentation has unsaved changes.");
        alert.setContentText("Save before closing?");
        ButtonType save    = new ButtonType("Save");
        ButtonType discard = new ButtonType("Discard");
        ButtonType cancel  = new ButtonType("Cancel", ButtonType.CANCEL.getButtonData());
        alert.getButtonTypes().setAll(save, discard, cancel);
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isEmpty() || result.get() == cancel) return false;
        if (result.get() == save) {
            // Route through the orchestrator's save API. If the session
            // has no original file, fall back to discard + log; the File
            // menu's "Save As" is the path to save-from-untitled and a
            // session-tab prompt shouldn't trigger a file-picker.
            File originalFile = sessOpt.get().getOriginalFile();
            if (originalFile == null) {
                showError("Untitled presentation has no save path. Use File > Save As first.");
                return false;
            }
            try {
                orch.savePresentation(originalFile);
            } catch (Exception ex) {
                showError("Save failed: " + ex.getMessage());
                return false;
            }
        }
        SessionManager.getInstance().closeSession(sessionId);
        return true;
    }

    private void createNewSession() {
        try {
            SessionManager.getInstance().createNewSession();
        } catch (Exception e) {
            showError("Failed to create session: " + e.getMessage());
        }
    }

    private String labelFor(String sessionId) {
        Optional<SessionManager.ManagedSession> opt =
            SessionManager.getInstance().getSession(sessionId);
        if (opt.isEmpty()) return sessionId;
        File file = opt.get().getOriginalFile();
        if (file != null) return file.getName();
        return "Untitled (" + shortId(sessionId) + ")";
    }

    private static String shortId(String sessionId) {
        return sessionId.length() > 6 ? sessionId.substring(0, 6) : sessionId;
    }

    private void showError(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg);
        a.setHeaderText(null);
        a.showAndWait();
    }
}
