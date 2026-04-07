package com.excudo.core.animations;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import com.excudo.core.model.AnimationType;
import com.excudo.core.model.AnimationBinding;
import com.excudo.core.model.ShapeGeometry;

import java.util.List;

/**
 * Interface for animation writing functionality, following Single Responsibility Principle.
 * Separates write concerns from read concerns in the animation factory system.
 * 
 * This interface defines the contract for creating OOXML animation elements,
 * ensuring clean separation between animation creation and parsing responsibilities.
 * 
 * Based on Gang of Four design patterns and code review feedback emphasizing
 * the importance of separating read/write concerns for maintainability.
 */
public interface AnimationWriter {
    
    /**
     * Create animation elements for a given animation binding.
     * Returns list of elements since some animations require multiple coordinated elements.
     * 
     * @param document The XML document for creating elements
     * @param binding The animation binding with all parameters
     * @param geometry The target shape geometry for coordinate calculations
     * @return List of animation elements (p:set, p:anim, p:animEffect, etc.)
     */
    List<Element> createAnimationElements(Document document, AnimationBinding binding, ShapeGeometry geometry);
    
    /**
     * Create the timing container wrapper for animation elements.
     * All animations use identical 4×p:par + 1×p:seq container hierarchy.
     * 
     * @param document The XML document
     * @param binding The animation binding
     * @return The timing container element
     */
    Element createTimingContainer(Document document, AnimationBinding binding);
    
    /**
     * Create target element with shape and paragraph targeting.
     * Common structure for all animation types.
     * 
     * @param document The XML document
     * @param binding The animation binding
     * @return The target element (p:tgtEl)
     */
    Element createTargetElement(Document document, AnimationBinding binding);
    
    /**
     * Create build list entry for this animation.
     * Required for PowerPoint animation sequencing.
     * 
     * @param document The XML document
     * @param binding The animation binding
     * @return The build list entry element
     */
    Element createBuildListEntry(Document document, AnimationBinding binding);
    
    /**
     * Check if this writer supports the given animation type.
     * 
     * @param animationType The animation type to check
     * @return true if this writer can create the animation type
     */
    boolean supportsAnimationType(AnimationType animationType);
    
    /**
     * Get the animation types supported by this writer.
     * 
     * @return Array of supported animation types
     */
    AnimationType[] getSupportedAnimationTypes();
    
    /**
     * Get the OOXML element pattern this writer generates.
     * Used for debugging and validation against timing dumps.
     * 
     * @return Human-readable pattern description (e.g., "p:animEffect + p:set")
     */
    String getOoxmlPattern();
}