package com.excudo.xml.writers.animations.abstracts;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import com.excudo.core.model.AnimationType;
import com.excudo.core.model.AnimationBinding;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.xml.writers.animations.AbstractAnimationFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Abstract factory for animations using the Effect pattern.
 * Handles animations that use p:animEffect + p:set pattern.
 * 
 * 
 * Based on PowerPoint timing dump analysis, this pattern includes:
 * - p:set (visibility for entrance/exit animations)
 * - p:animEffect (filter-based visual transition)
 * 
 * This pattern is used by:
 * - FADE animations (fade filter)
 * - WIPE animations (wipe filter with direction)
 * - Other filter-based effect animations
 * 
 * Template method pattern: concrete classes implement filter-specific logic,
 * this class handles the common OOXML structure.
 */
public abstract class AbstractEffectAnimationFactory extends AbstractAnimationFactory {
    
    
    /**
     * Create animation elements for effect animations.
     * Returns p:set (visibility) + p:animEffect (filter) elements.
     * 
     * @param document The XML document
     * @param binding The animation binding
     * @param geometry The target shape geometry (not used for effect animations)
     * @return List containing p:set and p:animEffect elements
     */
    @Override
    public List<Element> createAnimationElements(Document document, AnimationBinding binding, ShapeGeometry geometry) {
        List<Element> elements = new ArrayList<>();
        
        // For entrance animations, add visibility set first
        if (binding.isEntranceAnimation()) {
            elements.add(createVisibilitySet(document, binding, "visible"));
        }
        
        // Add the animation effect (abstract - implemented by subclasses)
        elements.add(createAnimationEffect(document, binding));
        
        // For exit animations, add visibility set after effect
        if (binding.isExitAnimation()) {
            elements.add(createVisibilitySet(document, binding, "hidden"));
        }
        
        return elements;
    }
    
    /**
     * Create p:animEffect element for filter-based transitions.
     * Subclasses implement this to create specific effects with proper filters.
     * 
     * @param document The XML document
     * @param binding The animation binding
     * @return The p:animEffect element
     */
     protected abstract Element createAnimationEffect(Document document, AnimationBinding binding);
     
     /**
      * Helper method for creating standard p:animEffect elements.
      * Concrete classes can use this to create consistent animEffect structure.
      * 
      * @param document The XML document
      * @param binding The animation binding
      * @param filter The effect filter (fade, wipe, etc.)
      * @param transition The transition direction (in, out)
      * @return The p:animEffect element
      */
     protected final Element createStandardAnimEffect(Document document, AnimationBinding binding, 
                                                     String filter, String transition) {
         Element animEffect = document.createElementNS(PRESENTATION_NS, "p:animEffect");
         animEffect.setAttribute("filter", filter);
         animEffect.setAttribute("transition", transition);
         
         // Create common behavior without fill="hold" per oracle pattern for animEffect
         Element cBhvr = createCommonBehaviorNoFill(document, binding, "0", binding.getDuration());
         animEffect.appendChild(cBhvr);
         
         return animEffect;
     }
    
    
    
}