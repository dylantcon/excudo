package com.excudo.view.animation;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a sequence of animations that are triggered together (e.g., on a single click).
 * Corresponds to PowerPoint's click trigger groupings.
 */
public class AnimationSequence {
    
    private final int clickTrigger;
    private final List<PowerPointAnimation> animations;
    private String description;
    
    public AnimationSequence(int clickTrigger) {
        this.clickTrigger = clickTrigger;
        this.animations = new ArrayList<>();
    }
    
    /**
     * Add an animation to this sequence
     */
    public void addAnimation(PowerPointAnimation animation) {
        animations.add(animation);
    }
    
    /**
     * Get all animations in this sequence
     */
    public List<PowerPointAnimation> getAnimations() {
        return new ArrayList<>(animations);
    }
    
    /**
     * Get the click trigger number for this sequence
     */
    public int getClickTrigger() {
        return clickTrigger;
    }
    
    /**
     * Get human-readable description of this sequence
     */
    public String getDescription() {
        if (description != null) {
            return description;
        }
        
        // Auto-generate description based on animations
        StringBuilder sb = new StringBuilder();
        sb.append("Click ").append(clickTrigger).append(": ");
        
        long entranceCount = animations.stream().filter(a -> a.isEntranceAnimation()).count();
        long exitCount = animations.stream().filter(a -> a.isExitAnimation()).count();
        
        if (entranceCount > 0) {
            sb.append(entranceCount).append(" entrance");
            if (entranceCount > 1) sb.append("s");
        }
        
        if (exitCount > 0) {
            if (entranceCount > 0) sb.append(", ");
            sb.append(exitCount).append(" exit");
            if (exitCount > 1) sb.append("s");
        }
        
        if (entranceCount == 0 && exitCount == 0) {
            sb.append(animations.size()).append(" effect");
            if (animations.size() > 1) sb.append("s");
        }
        
        return sb.toString();
    }
    
    /**
     * Set custom description for this sequence
     */
    public void setDescription(String description) {
        this.description = description;
    }
    
    /**
     * Get the number of animations in this sequence
     */
    public int getAnimationCount() {
        return animations.size();
    }
    
    /**
     * Check if this sequence is empty
     */
    public boolean isEmpty() {
        return animations.isEmpty();
    }
    
    /**
     * Get all SPIDs that are animated in this sequence
     */
    public List<Integer> getAnimatedSpids() {
        return animations.stream()
            .map(PowerPointAnimation::getTargetSpid)
            .distinct()
            .toList();
    }
    
    @Override
    public String toString() {
        return String.format("AnimationSequence{trigger=%d, animations=%d, description='%s'}", 
            clickTrigger, animations.size(), getDescription());
    }
}