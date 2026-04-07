package com.excudo.view.animation;

/**
 * Listener interface for animation playback events.
 * Allows UI components to respond to animation state changes and provide user feedback.
 */
public interface AnimationPlaybackListener {
    
    /**
     * Called when animation sequences have been built from PowerPoint data
     * @param sequenceCount Total number of click trigger sequences
     */
    void onSequencesBuilt(int sequenceCount);
    
    /**
     * Called when animation playback starts
     */
    void onPlaybackStarted();
    
    /**
     * Called when animation playback is paused
     */
    void onPlaybackPaused();
    
    /**
     * Called when animation playback is resumed from pause
     */
    void onPlaybackResumed();
    
    /**
     * Called when animation playback stops (either completed or user-stopped)
     */
    void onPlaybackStopped();
    
    /**
     * Called when a specific animation sequence is played
     * @param triggerIndex The click trigger index that was played
     */
    void onSequencePlayed(int triggerIndex);
    
    /**
     * Called when an individual animation completes
     * @param spid The shape ID that was animated
     * @param isEntrance True if this was an entrance animation, false for exit
     */
    void onAnimationCompleted(int spid, boolean isEntrance);
    
    /**
     * Called when there's an error during animation playback
     * @param error The error that occurred
     * @param context Additional context about where the error occurred
     */
    default void onPlaybackError(Exception error, String context) {
        // Default implementation does nothing
        System.err.println("Animation playback error in " + context + ": " + error.getMessage());
    }
    
    /**
     * Called when animation progress updates (for progress bars, etc.)
     * @param currentTrigger Current click trigger being played
     * @param totalTriggers Total number of click triggers
     * @param progress Progress percentage (0.0 to 1.0)
     */
    default void onPlaybackProgress(int currentTrigger, int totalTriggers, double progress) {
        // Default implementation does nothing
    }
}