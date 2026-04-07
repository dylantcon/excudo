package com.excudo.view.animation;

import com.excudo.core.model.AnimationBinding;
import javafx.animation.Timeline;
import javafx.util.Duration;

/**
 * Represents a single PowerPoint animation converted to JavaFX Timeline.
 * Wraps the original AnimationBinding data with JavaFX-specific properties.
 */
public class PowerPointAnimation {
    
    private final AnimationBinding originalBinding;
    private Timeline timeline;
    private Duration duration;
    private Duration delay;
    private AnimationType animationType;
    private AnimationDirection direction;
    
    public PowerPointAnimation(AnimationBinding binding) {
        this.originalBinding = binding;
        this.duration = Duration.millis(330); // Default PowerPoint duration
        this.delay = Duration.ZERO;
        
        // Parse animation type from binding
        parseAnimationType(binding);
    }
    
    private void parseAnimationType(AnimationBinding binding) {
        String transition = binding.getTransition();
        String filter = binding.getFilter();
        
        if ("in".equals(transition)) {
            if ("fade".equals(filter)) {
                this.animationType = AnimationType.FADE_IN;
            } else if ("wipe".equals(filter)) {
                this.animationType = AnimationType.WIPE_IN;
            } else if (filter != null && filter.startsWith("fly")) {
                this.animationType = AnimationType.FLY_IN;
                parseDirection(filter);
            } else {
                this.animationType = AnimationType.APPEAR;
            }
        } else if ("out".equals(transition)) {
            if ("fade".equals(filter)) {
                this.animationType = AnimationType.FADE_OUT;
            } else if (filter != null && filter.contains("wipe")) {
                this.animationType = AnimationType.WIPE_OUT;
                parseDirection(filter);
            } else if (filter != null && filter.startsWith("fly")) {
                this.animationType = AnimationType.FLY_OUT;
                parseDirection(filter);
            } else {
                this.animationType = AnimationType.DISAPPEAR;
            }
        } else {
            // Emphasis or motion animation
            this.animationType = AnimationType.EMPHASIS;
        }
    }
    
    private void parseDirection(String filter) {
        if (filter == null) {
            this.direction = AnimationDirection.NONE;
            return;
        }
        
        String filterLower = filter.toLowerCase();
        if (filterLower.contains("up")) {
            this.direction = AnimationDirection.UP;
        } else if (filterLower.contains("down")) {
            this.direction = AnimationDirection.DOWN;
        } else if (filterLower.contains("left")) {
            this.direction = AnimationDirection.LEFT;
        } else if (filterLower.contains("right")) {
            this.direction = AnimationDirection.RIGHT;
        } else if (filterLower.contains("top")) {
            this.direction = AnimationDirection.UP;
        } else if (filterLower.contains("bottom")) {
            this.direction = AnimationDirection.DOWN;
        } else {
            this.direction = AnimationDirection.NONE;
        }
    }
    
    // ========== GETTERS AND SETTERS ==========
    
    public AnimationBinding getOriginalBinding() {
        return originalBinding;
    }
    
    public int getTargetSpid() {
        return originalBinding.getTargetSpid();
    }
    
    public boolean isEntranceAnimation() {
        return originalBinding.isEntranceAnimation();
    }
    
    public boolean isExitAnimation() {
        return originalBinding.isExitAnimation();
    }
    
    public Timeline getTimeline() {
        return timeline;
    }
    
    public void setTimeline(Timeline timeline) {
        this.timeline = timeline;
    }
    
    public Duration getDuration() {
        return duration;
    }
    
    public void setDuration(Duration duration) {
        this.duration = duration;
    }
    
    public Duration getDelay() {
        return delay;
    }
    
    public void setDelay(Duration delay) {
        this.delay = delay;
    }
    
    public AnimationType getAnimationType() {
        return animationType;
    }
    
    public void setAnimationType(AnimationType animationType) {
        this.animationType = animationType;
    }
    
    public AnimationDirection getDirection() {
        return direction;
    }
    
    public void setDirection(AnimationDirection direction) {
        this.direction = direction;
    }
    
    // ========== UTILITY METHODS ==========
    
    /**
     * Get human-readable description of this animation
     */
    public String getDescription() {
        StringBuilder sb = new StringBuilder();
        
        // Add animation type
        switch (animationType) {
            case FADE_IN -> sb.append("Fade In");
            case FADE_OUT -> sb.append("Fade Out");
            case WIPE_IN -> sb.append("Wipe In");
            case WIPE_OUT -> sb.append("Wipe Out");
            case FLY_IN -> sb.append("Fly In");
            case FLY_OUT -> sb.append("Fly Out");
            case APPEAR -> sb.append("Appear");
            case DISAPPEAR -> sb.append("Disappear");
            case EMPHASIS -> sb.append("Emphasis");
            default -> sb.append("Unknown");
        }
        
        // Add direction if applicable
        if (direction != AnimationDirection.NONE) {
            sb.append(" (").append(direction.toString().toLowerCase()).append(")");
        }
        
        // Add duration
        sb.append(" - ").append((int) duration.toMillis()).append("ms");
        
        return sb.toString();
    }
    
    /**
     * Check if this animation affects shape visibility
     */
    public boolean affectsVisibility() {
        return isEntranceAnimation() || isExitAnimation() || 
               animationType == AnimationType.APPEAR || 
               animationType == AnimationType.DISAPPEAR;
    }
    
    /**
     * Check if this animation involves movement
     */
    public boolean involvesMovement() {
        return animationType == AnimationType.FLY_IN || 
               animationType == AnimationType.FLY_OUT ||
               animationType == AnimationType.WIPE_IN ||
               animationType == AnimationType.WIPE_OUT;
    }
    
    @Override
    public String toString() {
        return String.format("PowerPointAnimation{spid=%d, type=%s, duration=%dms, delay=%dms}", 
            getTargetSpid(), animationType, (int) duration.toMillis(), (int) delay.toMillis());
    }
    
    // ========== ENUMS ==========
    
    public enum AnimationType {
        // Entrance animations
        FADE_IN,
        WIPE_IN,
        FLY_IN,
        APPEAR,
        
        // Exit animations
        FADE_OUT,
        WIPE_OUT,
        FLY_OUT,
        DISAPPEAR,
        
        // Emphasis animations
        EMPHASIS,
        PULSE,
        COLOR_CHANGE,
        GROW_SHRINK,
        
        // Motion animations
        MOTION_PATH,
        TRANSFORM
    }
    
    public enum AnimationDirection {
        NONE,
        UP,
        DOWN,
        LEFT,
        RIGHT,
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT
    }
}