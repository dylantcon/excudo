package com.excudo.xml.writers.animations;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import com.excudo.core.model.AnimationType;
import com.excudo.core.model.AnimationBinding;
import com.excudo.core.model.ShapeGeometry;

import java.util.List;

/**
 * Abstract Factory pattern for creating OOXML animation elements.
 * This factory encapsulates the creation logic for different animation types,
 * removing the responsibility from SlideXMLWriter and enabling extensibility.
 * 
 * Based on timing dump analysis of 154 PowerPoint animations, each factory
 * handles a distinct OOXML element pattern to ensure PowerPoint compatibility.
 * 
 * Factories implementing this interface should extend AbstractAnimationFactory
 * for common functionality and consistent XML structure.
 */
public interface AnimationFactory {
    
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
     * Check if this factory supports the given animation type.
     * 
     * @param animationType The animation type to check
     * @return true if this factory can create the animation type
     */
    boolean supportsAnimationType(AnimationType animationType);
    
    /**
     * Get the animation types supported by this factory.
     * 
     * @return Array of supported animation types
     */
    AnimationType[] getSupportedAnimationTypes();
    
    /**
     * Get the OOXML element pattern this factory generates.
     * Used for debugging and validation against timing dumps.
     * 
     * @return Human-readable pattern description (e.g., "p:animEffect + p:set")
     */
    String getOoxmlPattern();
}