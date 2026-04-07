package com.excudo.xml.writers.animations;

/**
 * Interface for generating unique timing node IDs for OOXML animations.
 * This ensures proper ID management across all animation elements.
 */
public interface TimingNodeIdGenerator {
    
    /**
     * Generate the next unique timing node ID.
     * 
     * @return The next available timing node ID
     */
    int getNextId();
}