package com.excudo.view.animation;

import com.excudo.core.model.AnimationBinding;
import com.excudo.core.model.TimingNode;
import com.excudo.core.model.TimingTree;
import com.excudo.view.rendering.CoordinateMapper;
import javafx.animation.*;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.shape.Shape;
import javafx.scene.effect.Effect;
import javafx.scene.transform.Transform;
import javafx.util.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core animation engine that converts PowerPoint timing data into JavaFX Timeline objects.
 * Handles the conversion from OOXML animation definitions to executable JavaFX animations
 * with PowerPoint-accurate timing and visual effects.
 */
public class AnimationEngine {
    
    // ========== CONSTANTS ==========
    
    private static final double POWERPOINT_TIMING_UNIT = 1000.0; // PowerPoint uses milliseconds
    private static final Duration DEFAULT_ANIMATION_DURATION = Duration.millis(330); // PowerPoint default
    private static final Duration CLICK_TRIGGER_DELAY = Duration.millis(100); // Small delay for click responsiveness
    
    // ========== CORE COMPONENTS ==========
    
    private final CoordinateMapper coordinateMapper;
    private final Map<Integer, AnimationState> shapeAnimationStates;
    private final Map<Integer, Timeline> activeAnimations;
    private final List<AnimationSequence> animationSequences;
    
    // ========== ANIMATION STATE TRACKING ==========
    
    private int currentClickTrigger = 0;
    private boolean isPlaying = false;
    private boolean isPaused = false;
    private AnimationPlaybackListener playbackListener;
    
    public AnimationEngine(CoordinateMapper coordinateMapper) {
        this.coordinateMapper = coordinateMapper;
        this.shapeAnimationStates = new ConcurrentHashMap<>();
        this.activeAnimations = new ConcurrentHashMap<>();
        this.animationSequences = new ArrayList<>();
    }
    
    // ========== PUBLIC API ==========
    
    /**
     * Convert PowerPoint timing tree and animation bindings into executable JavaFX animations
     */
    public void buildAnimationSequences(TimingTree timingTree, List<AnimationBinding> animationBindings) {
        clearAnimationSequences();
        
        // Group animations by click trigger
        Map<Integer, List<AnimationBinding>> animationsByTrigger = groupAnimationsByTrigger(timingTree, animationBindings);
        
        // Build animation sequences for each click trigger
        for (Map.Entry<Integer, List<AnimationBinding>> entry : animationsByTrigger.entrySet()) {
            int clickTrigger = entry.getKey();
            List<AnimationBinding> bindings = entry.getValue();
            
            AnimationSequence sequence = buildAnimationSequence(clickTrigger, bindings);
            animationSequences.add(sequence);
        }
        
        // Initialize all shapes to their starting state
        initializeShapeStates();
        
        notifySequencesBuilt();
    }
    
    /**
     * Start playing animations from the beginning
     */
    public void playAnimations() {
        if (isPlaying) {
            return;
        }
        
        currentClickTrigger = 0;
        isPlaying = true;
        isPaused = false;
        
        notifyPlaybackStarted();
        
        if (!animationSequences.isEmpty()) {
            playNextSequence();
        }
    }
    
    /**
     * Advance to the next click trigger (user interaction)
     */
    public boolean advanceToNextTrigger() {
        if (!isPlaying || isPaused) {
            return false;
        }
        
        if (currentClickTrigger < animationSequences.size()) {
            playSequenceAtTrigger(currentClickTrigger);
            currentClickTrigger++;
            return true;
        }
        
        // All animations completed
        stopAnimations();
        return false;
    }
    
    /**
     * Pause current animations
     */
    public void pauseAnimations() {
        isPaused = true;
        
        for (Timeline timeline : activeAnimations.values()) {
            timeline.pause();
        }
        
        notifyPlaybackPaused();
    }
    
    /**
     * Resume paused animations
     */
    public void resumeAnimations() {
        if (!isPaused) {
            return;
        }
        
        isPaused = false;
        
        for (Timeline timeline : activeAnimations.values()) {
            timeline.play();
        }
        
        notifyPlaybackResumed();
    }
    
    /**
     * Stop all animations and reset to initial state
     */
    public void stopAnimations() {
        isPlaying = false;
        isPaused = false;
        currentClickTrigger = 0;
        
        // Stop all active timelines
        for (Timeline timeline : activeAnimations.values()) {
            timeline.stop();
        }
        activeAnimations.clear();
        
        // Reset all shapes to initial state
        resetShapeStates();
        
        notifyPlaybackStopped();
    }
    
    /**
     * Register a JavaFX Node for animation (associates SPID with visual element)
     */
    public void registerShapeNode(int spid, Node node) {
        AnimationState state = shapeAnimationStates.computeIfAbsent(spid, k -> new AnimationState(spid));
        state.setTargetNode(node);
        
        // Set initial visibility based on whether shape has entrance animations
        boolean hasEntranceAnimation = animationSequences.stream()
            .flatMap(seq -> seq.getAnimations().stream())
            .anyMatch(anim -> anim.getTargetSpid() == spid && anim.isEntranceAnimation());
            
        if (hasEntranceAnimation) {
            node.setOpacity(0.0); // Hidden until entrance animation
            state.setCurrentVisibility(AnimationVisibility.HIDDEN);
        } else {
            node.setOpacity(1.0); // Visible by default
            state.setCurrentVisibility(AnimationVisibility.VISIBLE);
        }
    }
    
    /**
     * Set playback event listener
     */
    public void setPlaybackListener(AnimationPlaybackListener listener) {
        this.playbackListener = listener;
    }
    
    // ========== ANIMATION SEQUENCE BUILDING ==========
    
    private Map<Integer, List<AnimationBinding>> groupAnimationsByTrigger(TimingTree timingTree, 
                                                                         List<AnimationBinding> animationBindings) {
        Map<Integer, List<AnimationBinding>> result = new HashMap<>();
        
        // For now, we'll use a simple approach: group by delay patterns
        // In a full implementation, we'd parse the timing tree structure more carefully
        
        int currentTrigger = 0;
        for (AnimationBinding binding : animationBindings) {
            // Parse delay to determine click trigger grouping
            String delay = binding.getDelay();
            
            if ("indefinite".equals(delay) || delay == null) {
                // This starts a new click trigger
                currentTrigger++;
            }
            
            result.computeIfAbsent(currentTrigger, k -> new ArrayList<>()).add(binding);
        }
        
        return result;
    }
    
    private AnimationSequence buildAnimationSequence(int clickTrigger, List<AnimationBinding> bindings) {
        AnimationSequence sequence = new AnimationSequence(clickTrigger);
        
        for (AnimationBinding binding : bindings) {
            PowerPointAnimation animation = convertBindingToAnimation(binding);
            sequence.addAnimation(animation);
        }
        
        return sequence;
    }
    
    private PowerPointAnimation convertBindingToAnimation(AnimationBinding binding) {
        PowerPointAnimation animation = new PowerPointAnimation(binding);
        
        // Parse duration
        Duration duration = parseDuration(binding.getDuration());
        animation.setDuration(duration);
        
        // Parse delay within the trigger group
        Duration delay = parseDelay(binding.getDelay());
        animation.setDelay(delay);
        
        // Determine animation type and create appropriate JavaFX animation
        Timeline timeline = createTimelineForAnimation(binding);
        animation.setTimeline(timeline);
        
        return animation;
    }
    
    private Timeline createTimelineForAnimation(AnimationBinding binding) {
        int spid = binding.getTargetSpid();
        String animationType = binding.getAnimationType().toString();
        String transition = binding.getTransition();
        String filter = binding.getFilter();
        
        Timeline timeline = new Timeline();
        
        if ("in".equals(transition)) {
            // Entrance animation
            timeline.getKeyFrames().addAll(createEntranceAnimation(spid, animationType, filter));
        } else if ("out".equals(transition)) {
            // Exit animation
            timeline.getKeyFrames().addAll(createExitAnimation(spid, animationType, filter));
        } else {
            // Emphasis or motion animation (future implementation)
            timeline.getKeyFrames().addAll(createEmphasisAnimation(spid, animationType, filter));
        }
        
        return timeline;
    }
    
    // ========== ANIMATION TYPE IMPLEMENTATIONS ==========
    
    private List<KeyFrame> createEntranceAnimation(int spid, String animationType, String filter) {
        List<KeyFrame> keyFrames = new ArrayList<>();
        Duration duration = DEFAULT_ANIMATION_DURATION;
        
        AnimationState state = shapeAnimationStates.get(spid);
        if (state == null || state.getTargetNode() == null) {
            return keyFrames; // No node to animate
        }
        
        Node node = state.getTargetNode();
        
        if ("fade".equals(filter) || "p:animEffect".equals(animationType)) {
            // Fade in animation
            keyFrames.addAll(createFadeInAnimation(node, duration));
            
        } else if ("fly(left)".equals(filter) || "fly(right)".equals(filter) || 
                   "fly(up)".equals(filter) || "fly(down)".equals(filter)) {
            // Fly in animation from specified direction
            keyFrames.addAll(createFlyInAnimation(node, duration, filter));
            
        } else if ("wipe".equals(filter) || filter.startsWith("wipe(")) {
            // Wipe animation with directional clipping
            keyFrames.addAll(createWipeInAnimation(node, duration, filter));
            
        } else if ("appear".equals(filter)) {
            // Instant appear animation
            keyFrames.addAll(createAppearAnimation(node, duration));
            
        } else {
            // Default to fade for unknown animation types
            keyFrames.addAll(createFadeInAnimation(node, duration));
        }
        
        // Update animation state when animation completes
        KeyFrame completionFrame = new KeyFrame(duration, e -> {
            state.setCurrentVisibility(AnimationVisibility.VISIBLE);
            notifyAnimationCompleted(spid, true);
        });
        keyFrames.add(completionFrame);
        
        return keyFrames;
    }
    
    private List<KeyFrame> createExitAnimation(int spid, String animationType, String filter) {
        List<KeyFrame> keyFrames = new ArrayList<>();
        Duration duration = DEFAULT_ANIMATION_DURATION;
        
        AnimationState state = shapeAnimationStates.get(spid);
        if (state == null || state.getTargetNode() == null) {
            return keyFrames;
        }
        
        Node node = state.getTargetNode();
        
        if ("fade".equals(filter) || "p:animEffect".equals(animationType)) {
            // Fade out animation
            keyFrames.addAll(createFadeOutAnimation(node, duration));
            
        } else if ("fly(left)".equals(filter) || "fly(right)".equals(filter) || 
                   "fly(up)".equals(filter) || "fly(down)".equals(filter)) {
            // Fly out animation to specified direction
            keyFrames.addAll(createFlyOutAnimation(node, duration, filter));
            
        } else if ("wipe(down)".equals(filter) || filter.startsWith("wipe(")) {
            // Wipe out animation with directional clipping
            keyFrames.addAll(createWipeOutAnimation(node, duration, filter));
            
        } else if ("disappear".equals(filter)) {
            // Instant disappear animation
            keyFrames.addAll(createDisappearAnimation(node, duration));
            
        } else {
            // Default to fade out
            keyFrames.addAll(createFadeOutAnimation(node, duration));
        }
        
        // Update animation state when animation completes
        KeyFrame completionFrame = new KeyFrame(duration, e -> {
            state.setCurrentVisibility(AnimationVisibility.HIDDEN);
            notifyAnimationCompleted(spid, false);
        });
        keyFrames.add(completionFrame);
        
        return keyFrames;
    }
    
    private List<KeyFrame> createEmphasisAnimation(int spid, String animationType, String filter) {
        List<KeyFrame> keyFrames = new ArrayList<>();
        Duration duration = DEFAULT_ANIMATION_DURATION;
        
        AnimationState state = shapeAnimationStates.get(spid);
        if (state == null || state.getTargetNode() == null) {
            return keyFrames;
        }
        
        Node node = state.getTargetNode();
        
        if ("pulse".equals(filter)) {
            keyFrames.addAll(createPulseAnimation(node, duration));
        } else if ("grow".equals(filter) || "shrink".equals(filter)) {
            keyFrames.addAll(createScaleAnimation(node, duration, filter));
        } else if ("spin".equals(filter)) {
            keyFrames.addAll(createRotateAnimation(node, duration));
        }
        
        return keyFrames;
    }
    
    // ========== CORE ANIMATION IMPLEMENTATIONS ==========
    
    private List<KeyFrame> createFadeInAnimation(Node node, Duration duration) {
        List<KeyFrame> keyFrames = new ArrayList<>();
        
        KeyValue startOpacity = new KeyValue(node.opacityProperty(), 0.0);
        KeyValue endOpacity = new KeyValue(node.opacityProperty(), 1.0);
        
        keyFrames.add(new KeyFrame(Duration.ZERO, startOpacity));
        keyFrames.add(new KeyFrame(duration, endOpacity));
        
        return keyFrames;
    }
    
    private List<KeyFrame> createFadeOutAnimation(Node node, Duration duration) {
        List<KeyFrame> keyFrames = new ArrayList<>();
        
        KeyValue startOpacity = new KeyValue(node.opacityProperty(), 1.0);
        KeyValue endOpacity = new KeyValue(node.opacityProperty(), 0.0);
        
        keyFrames.add(new KeyFrame(Duration.ZERO, startOpacity));
        keyFrames.add(new KeyFrame(duration, endOpacity));
        
        return keyFrames;
    }
    
    private List<KeyFrame> createFlyInAnimation(Node node, Duration duration, String direction) {
        List<KeyFrame> keyFrames = new ArrayList<>();
        
        // Calculate fly-in distance based on canvas size
        double flyDistance = 300.0; // pixels outside viewport
        
        double startX = 0, startY = 0;
        
        // Determine starting position based on direction
        if ("fly(left)".equals(direction)) {
            startX = -flyDistance;
        } else if ("fly(right)".equals(direction)) {
            startX = flyDistance;
        } else if ("fly(up)".equals(direction)) {
            startY = -flyDistance;
        } else if ("fly(down)".equals(direction)) {
            startY = flyDistance;
        }
        
        // Animation: opacity 0->1 + translate from offset to 0
        KeyValue startOpacity = new KeyValue(node.opacityProperty(), 0.0);
        KeyValue endOpacity = new KeyValue(node.opacityProperty(), 1.0);
        KeyValue startTranslateX = new KeyValue(node.translateXProperty(), startX);
        KeyValue endTranslateX = new KeyValue(node.translateXProperty(), 0.0);
        KeyValue startTranslateY = new KeyValue(node.translateYProperty(), startY);
        KeyValue endTranslateY = new KeyValue(node.translateYProperty(), 0.0);
        
        keyFrames.add(new KeyFrame(Duration.ZERO, startOpacity, startTranslateX, startTranslateY));
        keyFrames.add(new KeyFrame(duration, endOpacity, endTranslateX, endTranslateY));
        
        return keyFrames;
    }
    
    private List<KeyFrame> createFlyOutAnimation(Node node, Duration duration, String direction) {
        List<KeyFrame> keyFrames = new ArrayList<>();
        
        double flyDistance = 300.0;
        double endX = 0, endY = 0;
        
        if ("fly(left)".equals(direction)) {
            endX = -flyDistance;
        } else if ("fly(right)".equals(direction)) {
            endX = flyDistance;
        } else if ("fly(up)".equals(direction)) {
            endY = -flyDistance;
        } else if ("fly(down)".equals(direction)) {
            endY = flyDistance;
        }
        
        KeyValue startOpacity = new KeyValue(node.opacityProperty(), 1.0);
        KeyValue endOpacity = new KeyValue(node.opacityProperty(), 0.0);
        KeyValue startTranslateX = new KeyValue(node.translateXProperty(), 0.0);
        KeyValue endTranslateX = new KeyValue(node.translateXProperty(), endX);
        KeyValue startTranslateY = new KeyValue(node.translateYProperty(), 0.0);
        KeyValue endTranslateY = new KeyValue(node.translateYProperty(), endY);
        
        keyFrames.add(new KeyFrame(Duration.ZERO, startOpacity, startTranslateX, startTranslateY));
        keyFrames.add(new KeyFrame(duration, endOpacity, endTranslateX, endTranslateY));
        
        return keyFrames;
    }
    
    private List<KeyFrame> createWipeInAnimation(Node node, Duration duration, String direction) {
        List<KeyFrame> keyFrames = new ArrayList<>();
        
        // Wipe animation using clip bounds
        // For JavaFX, we'll simulate with scale transforms
        if (node instanceof Shape shape) {
            // Use scale animation to simulate wipe effect
            double startScaleX = "wipe(left)".equals(direction) || "wipe(right)".equals(direction) ? 0.0 : 1.0;
            double startScaleY = "wipe(up)".equals(direction) || "wipe(down)".equals(direction) ? 0.0 : 1.0;
            
            KeyValue startOpacity = new KeyValue(node.opacityProperty(), 0.5);
            KeyValue endOpacity = new KeyValue(node.opacityProperty(), 1.0);
            KeyValue startSX = new KeyValue(node.scaleXProperty(), startScaleX);
            KeyValue endSX = new KeyValue(node.scaleXProperty(), 1.0);
            KeyValue startSY = new KeyValue(node.scaleYProperty(), startScaleY);
            KeyValue endSY = new KeyValue(node.scaleYProperty(), 1.0);
            
            keyFrames.add(new KeyFrame(Duration.ZERO, startOpacity, startSX, startSY));
            keyFrames.add(new KeyFrame(duration, endOpacity, endSX, endSY));
        } else {
            // Fallback to fade for non-shapes
            keyFrames.addAll(createFadeInAnimation(node, duration));
        }
        
        return keyFrames;
    }
    
    private List<KeyFrame> createWipeOutAnimation(Node node, Duration duration, String direction) {
        List<KeyFrame> keyFrames = new ArrayList<>();
        
        if (node instanceof Shape shape) {
            double endScaleX = "wipe(left)".equals(direction) || "wipe(right)".equals(direction) ? 0.0 : 1.0;
            double endScaleY = "wipe(up)".equals(direction) || "wipe(down)".equals(direction) ? 0.0 : 1.0;
            
            KeyValue startOpacity = new KeyValue(node.opacityProperty(), 1.0);
            KeyValue endOpacity = new KeyValue(node.opacityProperty(), 0.5);
            KeyValue startSX = new KeyValue(node.scaleXProperty(), 1.0);
            KeyValue endSX = new KeyValue(node.scaleXProperty(), endScaleX);
            KeyValue startSY = new KeyValue(node.scaleYProperty(), 1.0);
            KeyValue endSY = new KeyValue(node.scaleYProperty(), endScaleY);
            
            keyFrames.add(new KeyFrame(Duration.ZERO, startOpacity, startSX, startSY));
            keyFrames.add(new KeyFrame(duration, endOpacity, endSX, endSY));
        } else {
            keyFrames.addAll(createFadeOutAnimation(node, duration));
        }
        
        return keyFrames;
    }
    
    private List<KeyFrame> createAppearAnimation(Node node, Duration duration) {
        List<KeyFrame> keyFrames = new ArrayList<>();
        
        // Instant appear - no gradual transition
        KeyValue endOpacity = new KeyValue(node.opacityProperty(), 1.0);
        keyFrames.add(new KeyFrame(Duration.millis(1), endOpacity)); // Nearly instant
        
        return keyFrames;
    }
    
    private List<KeyFrame> createDisappearAnimation(Node node, Duration duration) {
        List<KeyFrame> keyFrames = new ArrayList<>();
        
        // Instant disappear
        KeyValue endOpacity = new KeyValue(node.opacityProperty(), 0.0);
        keyFrames.add(new KeyFrame(Duration.millis(1), endOpacity));
        
        return keyFrames;
    }
    
    private List<KeyFrame> createPulseAnimation(Node node, Duration duration) {
        List<KeyFrame> keyFrames = new ArrayList<>();
        
        // Pulse: scale up then back to normal
        Duration halfDuration = duration.divide(2);
        
        KeyValue startScale = new KeyValue(node.scaleXProperty(), 1.0);
        KeyValue pulseScaleX = new KeyValue(node.scaleXProperty(), 1.2);
        KeyValue pulseScaleY = new KeyValue(node.scaleYProperty(), 1.2);
        KeyValue endScaleX = new KeyValue(node.scaleXProperty(), 1.0);
        KeyValue endScaleY = new KeyValue(node.scaleYProperty(), 1.0);
        
        keyFrames.add(new KeyFrame(Duration.ZERO, startScale));
        keyFrames.add(new KeyFrame(halfDuration, pulseScaleX, pulseScaleY));
        keyFrames.add(new KeyFrame(duration, endScaleX, endScaleY));
        
        return keyFrames;
    }
    
    private List<KeyFrame> createScaleAnimation(Node node, Duration duration, String type) {
        List<KeyFrame> keyFrames = new ArrayList<>();
        
        double targetScale = "grow".equals(type) ? 1.2 : 0.8; // grow or shrink
        
        KeyValue startScaleX = new KeyValue(node.scaleXProperty(), 1.0);
        KeyValue startScaleY = new KeyValue(node.scaleYProperty(), 1.0);
        KeyValue endScaleX = new KeyValue(node.scaleXProperty(), targetScale);
        KeyValue endScaleY = new KeyValue(node.scaleYProperty(), targetScale);
        
        keyFrames.add(new KeyFrame(Duration.ZERO, startScaleX, startScaleY));
        keyFrames.add(new KeyFrame(duration, endScaleX, endScaleY));
        
        return keyFrames;
    }
    
    private List<KeyFrame> createRotateAnimation(Node node, Duration duration) {
        List<KeyFrame> keyFrames = new ArrayList<>();
        
        // Full 360-degree rotation
        KeyValue startRotate = new KeyValue(node.rotateProperty(), 0.0);
        KeyValue endRotate = new KeyValue(node.rotateProperty(), 360.0);
        
        keyFrames.add(new KeyFrame(Duration.ZERO, startRotate));
        keyFrames.add(new KeyFrame(duration, endRotate));
        
        return keyFrames;
    }
    
    // ========== ANIMATION PLAYBACK ==========
    
    private void playNextSequence() {
        if (currentClickTrigger < animationSequences.size()) {
            // Auto-advance first sequence, then wait for user clicks
            if (currentClickTrigger == 0) {
                playSequenceAtTrigger(0);
                currentClickTrigger++;
            }
        }
    }
    
    private void playSequenceAtTrigger(int triggerIndex) {
        if (triggerIndex >= animationSequences.size()) {
            return;
        }
        
        AnimationSequence sequence = animationSequences.get(triggerIndex);
        
        for (PowerPointAnimation animation : sequence.getAnimations()) {
            int spid = animation.getTargetSpid();
            Timeline timeline = animation.getTimeline();
            
            if (timeline != null) {
                // Apply delay if specified
                Duration delay = animation.getDelay();
                if (delay != null && delay.greaterThan(Duration.ZERO)) {
                    Timeline delayedTimeline = new Timeline(
                        new KeyFrame(delay, e -> {
                            timeline.play();
                            activeAnimations.put(spid, timeline);
                        })
                    );
                    delayedTimeline.play();
                } else {
                    timeline.play();
                    activeAnimations.put(spid, timeline);
                }
            }
        }
        
        notifySequencePlayed(triggerIndex);
    }
    
    // ========== STATE MANAGEMENT ==========
    
    private void initializeShapeStates() {
        for (AnimationState state : shapeAnimationStates.values()) {
            state.resetToInitialState();
        }
    }
    
    private void resetShapeStates() {
        for (AnimationState state : shapeAnimationStates.values()) {
            Node node = state.getTargetNode();
            if (node != null) {
                // Reset all visual properties to defaults
                node.setOpacity(1.0);
                node.setScaleX(1.0);
                node.setScaleY(1.0);
                node.setRotate(0.0);
                node.setTranslateX(0.0);
                node.setTranslateY(0.0);
            }
            state.setCurrentVisibility(AnimationVisibility.VISIBLE);
        }
    }
    
    private void clearAnimationSequences() {
        stopAnimations();
        animationSequences.clear();
        shapeAnimationStates.clear();
    }
    
    // ========== UTILITY METHODS ==========
    
    private Duration parseDuration(String durationStr) {
        if (durationStr == null || durationStr.trim().isEmpty()) {
            return DEFAULT_ANIMATION_DURATION;
        }
        
        try {
            // PowerPoint durations are in milliseconds
            double millis = Double.parseDouble(durationStr);
            return Duration.millis(millis);
        } catch (NumberFormatException e) {
            return DEFAULT_ANIMATION_DURATION;
        }
    }
    
    private Duration parseDelay(String delayStr) {
        if (delayStr == null || delayStr.trim().isEmpty() || "indefinite".equals(delayStr)) {
            return Duration.ZERO;
        }
        
        try {
            double millis = Double.parseDouble(delayStr);
            return Duration.millis(millis);
        } catch (NumberFormatException e) {
            return Duration.ZERO;
        }
    }
    
    // ========== EVENT NOTIFICATIONS ==========
    
    private void notifySequencesBuilt() {
        if (playbackListener != null) {
            playbackListener.onSequencesBuilt(animationSequences.size());
        }
    }
    
    private void notifyPlaybackStarted() {
        if (playbackListener != null) {
            playbackListener.onPlaybackStarted();
        }
    }
    
    private void notifyPlaybackPaused() {
        if (playbackListener != null) {
            playbackListener.onPlaybackPaused();
        }
    }
    
    private void notifyPlaybackResumed() {
        if (playbackListener != null) {
            playbackListener.onPlaybackResumed();
        }
    }
    
    private void notifyPlaybackStopped() {
        if (playbackListener != null) {
            playbackListener.onPlaybackStopped();
        }
    }
    
    private void notifySequencePlayed(int triggerIndex) {
        if (playbackListener != null) {
            playbackListener.onSequencePlayed(triggerIndex);
        }
    }
    
    private void notifyAnimationCompleted(int spid, boolean isEntrance) {
        if (playbackListener != null) {
            playbackListener.onAnimationCompleted(spid, isEntrance);
        }
    }
    
    // ========== GETTERS ==========
    
    public boolean isPlaying() { return isPlaying; }
    public boolean isPaused() { return isPaused; }
    public int getCurrentClickTrigger() { return currentClickTrigger; }
    public int getTotalClickTriggers() { return animationSequences.size(); }
    public List<AnimationSequence> getAnimationSequences() { return new ArrayList<>(animationSequences); }
    
    // ========== INNER CLASSES ==========
    
    /**
     * Represents the animation state of a shape
     */
    public static class AnimationState {
        private final int spid;
        private Node targetNode;
        private AnimationVisibility currentVisibility;
        private Map<String, Object> properties;
        
        public AnimationState(int spid) {
            this.spid = spid;
            this.currentVisibility = AnimationVisibility.VISIBLE;
            this.properties = new HashMap<>();
        }
        
        public void resetToInitialState() {
            currentVisibility = AnimationVisibility.VISIBLE;
            properties.clear();
        }
        
        // Getters and setters
        public int getSpid() { return spid; }
        public Node getTargetNode() { return targetNode; }
        public void setTargetNode(Node targetNode) { this.targetNode = targetNode; }
        public AnimationVisibility getCurrentVisibility() { return currentVisibility; }
        public void setCurrentVisibility(AnimationVisibility visibility) { this.currentVisibility = visibility; }
        public Map<String, Object> getProperties() { return properties; }
    }
    
    /**
     * Visibility states for animated shapes
     */
    public enum AnimationVisibility {
        HIDDEN,      // Shape is not visible (before entrance or after exit)
        ANIMATING,   // Shape is currently being animated
        VISIBLE      // Shape is fully visible and static
    }
}