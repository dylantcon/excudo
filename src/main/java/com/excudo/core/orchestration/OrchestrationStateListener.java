package com.excudo.core.orchestration;

/**
 * Listener for orchestration state changes.
 *
 * Subscribers (typically the GUI's MainController) receive callbacks when
 * the underlying presentation state changes via console commands, menu
 * actions, or programmatic flows. This gives the UI a single notification
 * path so any load/structural change triggers a refresh regardless of which
 * code path caused it.
 *
 * All methods are default no-ops so implementers only override what they need.
 * Implementations must not assume they run on any particular thread; GUI code
 * should route refreshes through Platform.runLater when necessary.
 */
public interface OrchestrationStateListener {

    /**
     * Fired when a presentation has been loaded into the current session
     * (either a new file or a replacement load).
     */
    default void onPresentationLoaded() {}

    /**
     * Fired when the active presentation session is closed and state is
     * no longer valid.
     */
    default void onPresentationClosed() {}

    /**
     * Fired when the slide list changes (slides added, removed, reordered).
     * Listeners should refresh any view that displays the slide hierarchy.
     */
    default void onPresentationStructureChanged() {}

    /**
     * Fired when a single slide's contents are modified but the slide list
     * itself is unchanged.
     *
     * @param slideNumber the 1-based slide number that was modified
     */
    default void onSlideModified(int slideNumber) {}

    /**
     * Fired when the global active-session pointer changes. The active
     * session is the one every console engine + GUI surface should
     * read from; this callback is the sole handoff between the engine
     * that created/switched to a session and any observer that cares
     * ({@code MainController}, {@code PresentationExplorerController},
     * {@code ToolDispatcher}, ...).
     *
     * <p>Both arguments may be null: {@code sessionId} is null when no
     * session is active (startup, after the last session closes).
     * {@code orchestrator} is null iff {@code sessionId} is null or the
     * referenced session has no orchestrator attached. Implementations
     * must null-check both.
     *
     * <p>Fires on the caller's thread. GUI implementers are responsible
     * for their own {@code Platform.runLater} hop.
     */
    default void onActiveSessionChanged(String sessionId, PPTXOrchestrator orchestrator) {}
}
