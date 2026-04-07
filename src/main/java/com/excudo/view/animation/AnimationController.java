package com.excudo.view.animation;

import com.excudo.core.model.AnimationBinding;
import com.excudo.core.model.ParsedSlideData;
import com.excudo.core.model.TimingTree;
import com.excudo.view.rendering.CoordinateMapper;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Group;
import javafx.scene.shape.Shape;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-level controller for managing animation playback and user interaction.
 * Coordinates between the AnimationEngine and the UI components.
 */
public class AnimationController implements AnimationPlaybackListener {
    
    // ========== CORE COMPONENTS ==========
    
    private final AnimationEngine animationEngine;
    private final Map<Integer, Node> registeredShapes;
    private final List<AnimationControllerListener> listeners;
    
    // ========== STATE ==========
    
    private ParsedSlideData currentSlideData;
    private Group slideSceneGraph;
    private boolean autoAdvanceEnabled = false;
    private long autoAdvanceDelayMs = 2000; // 2 seconds between auto-advance
    
    public AnimationController(CoordinateMapper coordinateMapper) {
        this.animationEngine = new AnimationEngine(coordinateMapper);
        this.registeredShapes = new ConcurrentHashMap<>();
        this.listeners = new ArrayList<>();
        
        // Set this controller as the animation engine's listener
        animationEngine.setPlaybackListener(this);
    }
    
    // ========== PUBLIC API ==========
    
    /**
     * Load animation data from parsed slide and prepare for playback
     */
    public void loadSlideAnimations(ParsedSlideData slideData, Group sceneGraph) {
        this.currentSlideData = slideData;
        this.slideSceneGraph = sceneGraph;
        
        // Stop any current animations
        stopAnimations();
        
        // Clear previous registrations
        registeredShapes.clear();
        
        // Build animation sequences from slide data
        TimingTree timingTree = slideData.getTimingTree();
        List<AnimationBinding> animationBindings = slideData.getAnimationBindings();
        
        animationEngine.buildAnimationSequences(timingTree, animationBindings);
        
        // Register shape nodes from scene graph
        registerShapeNodes(sceneGraph);
        
        notifyAnimationsLoaded();
    }
    
    /**
     * Register a shape node for animation
     */
    public void registerShapeNode(int spid, Node node) {
        registeredShapes.put(spid, node);
        animationEngine.registerShapeNode(spid, node);
    }
    
    /**
     * Start playing animations from the beginning
     */
    public void playAnimations() {
        animationEngine.playAnimations();
    }
    
    /**
     * Advance to the next click trigger (user clicks "Next")
     */
    public boolean nextTrigger() {
        return animationEngine.advanceToNextTrigger();
    }
    
    /**
     * Pause current animations
     */
    public void pauseAnimations() {
        animationEngine.pauseAnimations();
    }
    
    /**
     * Resume paused animations
     */
    public void resumeAnimations() {
        animationEngine.resumeAnimations();
    }
    
    /**
     * Stop all animations and reset to initial state
     */
    public void stopAnimations() {
        animationEngine.stopAnimations();
    }
    
    /**
     * Reset animations to beginning without playing
     */
    public void resetAnimations() {
        stopAnimations();
        if (currentSlideData != null) {
            // Rebuild sequences to reset state
            TimingTree timingTree = currentSlideData.getTimingTree();
            List<AnimationBinding> animationBindings = currentSlideData.getAnimationBindings();
            animationEngine.buildAnimationSequences(timingTree, animationBindings);
        }
    }
    
    /**
     * Enable or disable auto-advance mode
     */
    public void setAutoAdvanceEnabled(boolean enabled) {
        this.autoAdvanceEnabled = enabled;
        notifyAutoAdvanceChanged(enabled);
    }
    
    /**
     * Set auto-advance delay in milliseconds
     */
    public void setAutoAdvanceDelay(long delayMs) {
        this.autoAdvanceDelayMs = delayMs;
    }
    
    /**
     * Get current animation status
     */
    public AnimationStatus getAnimationStatus() {
        boolean isPlaying = animationEngine.isPlaying();
        boolean isPaused = animationEngine.isPaused();
        int currentTrigger = animationEngine.getCurrentClickTrigger();
        int totalTriggers = animationEngine.getTotalClickTriggers();
        
        return new AnimationStatus(isPlaying, isPaused, currentTrigger, totalTriggers);
    }
    
    /**
     * Get animation sequences for display/debugging
     */
    public List<AnimationSequence> getAnimationSequences() {
        return animationEngine.getAnimationSequences();
    }
    
    /**
     * Add listener for animation controller events
     */
    public void addListener(AnimationControllerListener listener) {
        listeners.add(listener);
    }
    
    /**
     * Remove listener
     */
    public void removeListener(AnimationControllerListener listener) {
        listeners.remove(listener);
    }
    
    // ========== SHAPE REGISTRATION ==========
    
    private void registerShapeNodes(Group sceneGraph) {
        if (sceneGraph == null || currentSlideData == null) {
            return;
        }
        
        // This is a simplified approach - in a full implementation,
        // we'd need to map rendered shapes back to their SPIDs
        // For now, we'll register nodes based on their userData or other identification
        
        registerNodesRecursively(sceneGraph);
    }
    
    private void registerNodesRecursively(Node node) {
        // Check if this node has SPID information
        Object userData = node.getUserData();
        if (userData instanceof Integer spid) {
            registerShapeNode(spid, node);
        } else if (userData instanceof String userDataStr) {
            // Try to parse SPID from string
            try {
                if (userDataStr.startsWith("spid:")) {
                    int spid = Integer.parseInt(userDataStr.substring(5));
                    registerShapeNode(spid, node);
                }
            } catch (NumberFormatException e) {
                // Ignore invalid SPID strings
            }
        }
        
        // Recursively register child nodes
        if (node instanceof Group group) {
            for (Node child : group.getChildren()) {
                registerNodesRecursively(child);
            }
        }
    }
    
    // ========== ANIMATION ENGINE LISTENER IMPLEMENTATION ==========
    
    @Override
    public void onSequencesBuilt(int sequenceCount) {
        Platform.runLater(() -> {
            notifySequencesBuilt(sequenceCount);
        });
    }
    
    @Override
    public void onPlaybackStarted() {
        Platform.runLater(() -> {
            notifyPlaybackStarted();
        });
    }
    
    @Override
    public void onPlaybackPaused() {
        Platform.runLater(() -> {
            notifyPlaybackPaused();
        });
    }
    
    @Override
    public void onPlaybackResumed() {
        Platform.runLater(() -> {
            notifyPlaybackResumed();
        });
    }
    
    @Override
    public void onPlaybackStopped() {
        Platform.runLater(() -> {
            notifyPlaybackStopped();
        });
    }
    
    @Override
    public void onSequencePlayed(int triggerIndex) {
        Platform.runLater(() -> {
            notifySequencePlayed(triggerIndex);
            
            // Handle auto-advance if enabled
            if (autoAdvanceEnabled && !animationEngine.isPaused()) {
                scheduleAutoAdvance();
            }
        });
    }
    
    @Override
    public void onAnimationCompleted(int spid, boolean isEntrance) {
        Platform.runLater(() -> {
            notifyAnimationCompleted(spid, isEntrance);
        });
    }
    
    // ========== AUTO-ADVANCE FUNCTIONALITY ==========
    
    private void scheduleAutoAdvance() {
        // Schedule next advance after delay
        Timer timer = new Timer(true);
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                Platform.runLater(() -> {
                    if (autoAdvanceEnabled && animationEngine.isPlaying() && !animationEngine.isPaused()) {
                        if (!nextTrigger()) {
                            // No more triggers, stop auto-advance
                            setAutoAdvanceEnabled(false);
                        }
                    }
                });
            }
        }, autoAdvanceDelayMs);
    }
    
    // ========== EVENT NOTIFICATIONS ==========
    
    private void notifyAnimationsLoaded() {
        for (AnimationControllerListener listener : listeners) {
            try {
                listener.onAnimationsLoaded(currentSlideData);
            } catch (Exception e) {
                System.err.println("Error notifying listener of animations loaded: " + e.getMessage());
            }
        }
    }
    
    private void notifySequencesBuilt(int sequenceCount) {
        for (AnimationControllerListener listener : listeners) {
            try {
                listener.onSequencesBuilt(sequenceCount);
            } catch (Exception e) {
                System.err.println("Error notifying listener of sequences built: " + e.getMessage());
            }
        }
    }
    
    private void notifyPlaybackStarted() {
        for (AnimationControllerListener listener : listeners) {
            try {
                listener.onPlaybackStarted();
            } catch (Exception e) {
                System.err.println("Error notifying listener of playback started: " + e.getMessage());
            }
        }
    }
    
    private void notifyPlaybackPaused() {
        for (AnimationControllerListener listener : listeners) {
            try {
                listener.onPlaybackPaused();
            } catch (Exception e) {
                System.err.println("Error notifying listener of playback paused: " + e.getMessage());
            }
        }
    }
    
    private void notifyPlaybackResumed() {
        for (AnimationControllerListener listener : listeners) {
            try {
                listener.onPlaybackResumed();
            } catch (Exception e) {
                System.err.println("Error notifying listener of playback resumed: " + e.getMessage());
            }
        }
    }
    
    private void notifyPlaybackStopped() {
        for (AnimationControllerListener listener : listeners) {
            try {
                listener.onPlaybackStopped();
            } catch (Exception e) {
                System.err.println("Error notifying listener of playback stopped: " + e.getMessage());
            }
        }
    }
    
    private void notifySequencePlayed(int triggerIndex) {
        for (AnimationControllerListener listener : listeners) {
            try {
                listener.onSequencePlayed(triggerIndex);
            } catch (Exception e) {
                System.err.println("Error notifying listener of sequence played: " + e.getMessage());
            }
        }
    }
    
    private void notifyAnimationCompleted(int spid, boolean isEntrance) {
        for (AnimationControllerListener listener : listeners) {
            try {
                listener.onAnimationCompleted(spid, isEntrance);
            } catch (Exception e) {
                System.err.println("Error notifying listener of animation completed: " + e.getMessage());
            }
        }
    }
    
    private void notifyAutoAdvanceChanged(boolean enabled) {
        for (AnimationControllerListener listener : listeners) {
            try {
                listener.onAutoAdvanceChanged(enabled);
            } catch (Exception e) {
                System.err.println("Error notifying listener of auto-advance changed: " + e.getMessage());
            }
        }
    }
    
    // ========== CLEANUP ==========
    
    /**
     * Clean up resources and stop any ongoing animations
     */
    public void cleanup() {
        // Stop all animations
        if (animationEngine != null) {
            animationEngine.stopAnimations();
        }
        
        // Clear references
        registeredShapes.clear();
        listeners.clear();
        currentSlideData = null;
        slideSceneGraph = null;
        autoAdvanceEnabled = false;
    }
    
    // ========== GETTERS ==========
    
    public ParsedSlideData getCurrentSlideData() { return currentSlideData; }
    public boolean isAutoAdvanceEnabled() { return autoAdvanceEnabled; }
    public long getAutoAdvanceDelay() { return autoAdvanceDelayMs; }
    public Map<Integer, Node> getRegisteredShapes() { return new HashMap<>(registeredShapes); }
    
    // ========== INNER CLASSES ==========
    
    /**
     * Current animation status information
     */
    public static class AnimationStatus {
        private final boolean playing;
        private final boolean paused;
        private final int currentTrigger;
        private final int totalTriggers;
        
        public AnimationStatus(boolean playing, boolean paused, int currentTrigger, int totalTriggers) {
            this.playing = playing;
            this.paused = paused;
            this.currentTrigger = currentTrigger;
            this.totalTriggers = totalTriggers;
        }
        
        public boolean isPlaying() { return playing; }
        public boolean isPaused() { return paused; }
        public int getCurrentTrigger() { return currentTrigger; }
        public int getTotalTriggers() { return totalTriggers; }
        public boolean isCompleted() { return !playing && currentTrigger >= totalTriggers; }
        public double getProgress() { 
            return totalTriggers > 0 ? (double) currentTrigger / totalTriggers : 0.0; 
        }
        
        @Override
        public String toString() {
            return String.format("AnimationStatus{playing=%s, paused=%s, trigger=%d/%d}", 
                playing, paused, currentTrigger, totalTriggers);
        }
    }
}