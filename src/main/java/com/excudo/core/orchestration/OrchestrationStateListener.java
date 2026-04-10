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
}
