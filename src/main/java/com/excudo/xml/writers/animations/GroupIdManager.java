package com.excudo.xml.writers.animations;

/**
 * Interface for managing animation group ID allocation within a slide.
 * 
 * Group IDs are critical for PowerPoint animation integrity. Native PowerPoint
 * uses sequential group IDs starting from 0, with each animation on a slide
 * getting a unique group ID regardless of its type (entrance/exit/emphasis).
 * 
 * This interface follows the Single Responsibility Principle by separating
 * group ID management from animation factory concerns.
 */
public interface GroupIdManager {
    
    /**
     * Allocate the next available group ID for an animation.
     * 
     * Native PowerPoint uses sequential allocation starting from 0.
     * Each animation on a slide gets a unique group ID to prevent
     * animation conflicts and PowerPoint integrity violations.
     * 
     * @return the next available group ID (0, 1, 2, ...)
     */
    int getNextGroupId();
    
    /**
     * Reset group ID allocation for a new slide.
     * 
     * Group IDs are slide-scoped, so each slide should start
     * group ID allocation from 0.
     */
    void resetForNewSlide();
    
    /**
     * Get the current group ID without incrementing.
     * 
     * Used for debugging and validation purposes.
     * 
     * @return the current group ID value
     */
    int getCurrentGroupId();
}