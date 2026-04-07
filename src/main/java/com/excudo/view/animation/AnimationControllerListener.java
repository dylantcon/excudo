package com.excudo.view.animation;

import com.excudo.core.model.ParsedSlideData;

/**
 * Listener interface for animation controller events.
 * Allows UI components to respond to high-level animation state changes.
 */
public interface AnimationControllerListener {
    
    /**
     * Called when animations are loaded for a new slide
     * @param slideData The slide data that was loaded
     */
    void onAnimationsLoaded(ParsedSlideData slideData);
    
    /**
     * Called when animation sequences have been built
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
     * Called when auto-advance mode is enabled or disabled
     * @param enabled True if auto-advance is now enabled
     */
    void onAutoAdvanceChanged(boolean enabled);
    
    /**
     * Called when there's an error during animation control
     * @param error The error that occurred
     * @param context Additional context about where the error occurred
     */
    default void onControllerError(Exception error, String context) {
        // Default implementation does nothing
        System.err.println("Animation controller error in " + context + ": " + error.getMessage());
    }
}