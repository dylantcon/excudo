package com.excudo.xml.writers.animations;

/**
 * Sequential implementation of GroupIdManager matching native PowerPoint behavior.
 * 
 * This implementation provides sequential group ID allocation starting from 0,
 * which matches how native PowerPoint assigns group IDs for animations.
 * 
 * Based on timing dump analysis, native PowerPoint uses:
 * - First animation: grpId="0"
 * - Second animation: grpId="1" 
 * - Third animation: grpId="2"
 * - etc.
 * 
 * This is regardless of animation type (fade, fly-in-left, zoom all get sequential IDs).
 */
public class SequentialGroupIdManager implements GroupIdManager {
    
    private final String instanceId;
    private int nextGroupId = 0;
    
    public SequentialGroupIdManager() {
        this(0);
    }

    public SequentialGroupIdManager(int startFrom) {
        this.instanceId = "GroupIdManager-" + System.currentTimeMillis() + "-" + System.nanoTime();
        this.nextGroupId = startFrom;
    }
    
    /**
     * Allocate the next sequential group ID.
     * 
     * @return the next group ID (0, 1, 2, ...)
     */
    @Override
    public int getNextGroupId() {
        int result = nextGroupId++;
        return result;
    }
    
    /**
     * Reset group ID allocation to start from 0 for a new slide.
     */
    @Override
    public void resetForNewSlide() {
        nextGroupId = 0;
    }
    
    /**
     * Get the current group ID value without incrementing.
     * 
     * @return the current group ID (the last allocated ID)
     * @throws IllegalStateException if no group has been allocated yet (invalid animation sequence)
     */
    @Override
    public int getCurrentGroupId() {
        if (nextGroupId == 0) {
            throw new IllegalStateException(
                "Cannot get current group ID: no animation group has been allocated yet. " +
                "The first animation on a slide must use 'on-click' trigger, not 'with-previous' or 'after-previous'."
            );
        }
        
        int result = nextGroupId - 1;
        return result;
    }
}