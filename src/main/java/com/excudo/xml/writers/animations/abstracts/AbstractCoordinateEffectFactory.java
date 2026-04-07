package com.excudo.xml.writers.animations.abstracts;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import com.excudo.core.model.AnimationBinding;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.xml.writers.animations.AbstractAnimationFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Abstract factory for animations using the Coordinate + Effect pattern.
 * 
 * Based on PowerPoint timing dump analysis, this pattern includes:
 * - p:set (visibility for entrance animations)
 * - p:anim×2 (coordinate/size attribute animations)
 * - p:animEffect (visual effect like fade)
 * 
 * This pattern is used by:
 * - ZOOM animations (ppt_w/ppt_h attributes + fade effect)
 * - Other coordinate-based animations with visual effects
 * 
 * Template method pattern: concrete classes implement attribute-specific logic,
 * this class handles the common OOXML structure.
 */
public abstract class AbstractCoordinateEffectFactory extends AbstractAnimationFactory {
    
    /**
     * Create animation elements using Coordinate + Effect pattern.
     * Template method that defines the structure, delegates specifics to subclasses.
     */
    @Override
    public final List<Element> createAnimationElements(Document document, AnimationBinding binding, ShapeGeometry geometry) {
        List<Element> elements = new ArrayList<>();
        
        // Add visibility set for entrance animations (PowerPoint pattern)
        if (binding.isEntranceAnimation()) {
            elements.add(createVisibilitySet(document, binding, "visible"));
        }
        
        // Add coordinate/attribute animations (abstract - implemented by subclasses)
        elements.addAll(createAttributeAnimations(document, binding));
        
        // Add visual effect (abstract - implemented by subclasses)
        // Some animations (like FLY) may not need visual effects
        Element visualEffect = createVisualEffect(document, binding);
        if (visualEffect != null) {
            elements.add(visualEffect);
        }
        
        return elements;
    }
    
    /**
     * Create attribute-specific animations (p:anim elements).
     * Subclasses implement this to create animations for specific attributes
     * like ppt_w/ppt_h for zoom, ppt_x/ppt_y for movement, etc.
     * 
     * @param document The XML document
     * @param binding The animation binding
     * @return List of p:anim elements for this animation type
     */
    protected abstract List<Element> createAttributeAnimations(Document document, AnimationBinding binding);
    
    /**
     * Create visual effect element (p:animEffect).
     * Subclasses implement this to create specific effects like fade, blur, etc.
     * 
     * @param document The XML document
     * @param binding The animation binding
     * @return The p:animEffect element
     */
    protected abstract Element createVisualEffect(Document document, AnimationBinding binding);
    
    /**
     * Create a coordinate/attribute animation element (p:anim).
     * Helper method for subclasses to create consistent p:anim structure.
     * 
     * @param document The XML document
     * @param binding The animation binding
     * @param attributeName The PowerPoint attribute name (ppt_w, ppt_h, ppt_x, etc.)
     * @param startValue The starting value ("0", "#ppt_w", etc.)
     * @param endValue The ending value ("0", "#ppt_w", etc.)
     * @return The p:anim element
     */
    protected final Element createAttributeAnimation(Document document, AnimationBinding binding, 
                                                   String attributeName, String startValue, String endValue) {
        return createAttributeAnimation(document, binding, attributeName, startValue, endValue, false);
    }
    
    /**
     * Create a coordinate/attribute animation element (p:anim) with additive support.
     * Helper method for subclasses to create consistent p:anim structure.
     * 
     * @param document The XML document
     * @param binding The animation binding
     * @param attributeName The PowerPoint attribute name (ppt_w, ppt_h, ppt_x, etc.)
     * @param startValue The starting value ("0", "#ppt_w", etc.)
     * @param endValue The ending value ("0", "#ppt_w", etc.)
     * @param additive Whether to add additive="base" attribute (required for FLY animations)
     * @return The p:anim element
     */
    protected final Element createAttributeAnimation(Document document, AnimationBinding binding, 
                                                   String attributeName, String startValue, String endValue, boolean additive) {
        Element anim = document.createElementNS(PRESENTATION_NS, "p:anim");
        anim.setAttribute("calcmode", "lin");
        anim.setAttribute("valueType", "num");
        
        // Create common behavior with additive support
        Element cBhvr = createCommonBehavior(document, binding, "0", binding.getDuration());
        if (additive) {
            cBhvr.setAttribute("additive", "base");
        }
        anim.appendChild(cBhvr);
        
        // Add attribute name list
        Element attrNameLst = document.createElementNS(PRESENTATION_NS, "p:attrNameLst");
        Element attrName = document.createElementNS(PRESENTATION_NS, "p:attrName");
        attrName.setTextContent(attributeName);
        attrNameLst.appendChild(attrName);
        cBhvr.appendChild(attrNameLst);
        
        // Add timing/value list
        Element tavLst = document.createElementNS(PRESENTATION_NS, "p:tavLst");
        tavLst.appendChild(createAnimTimingValue(document, "0", startValue));
        tavLst.appendChild(createAnimTimingValue(document, "100000", endValue));
        anim.appendChild(tavLst);
        
        return anim;
    }
    
    /**
     * Create timing/value element for p:anim tavLst.
     * PowerPoint uses specific format: p:tav with tm attribute and p:val child.
     */
    protected final Element createAnimTimingValue(Document document, String time, String value) {
        Element tav = document.createElementNS(PRESENTATION_NS, "p:tav");
        tav.setAttribute("tm", time);
        
        Element val = document.createElementNS(PRESENTATION_NS, "p:val");
        
        if (value.equals("0")) {
            // Use fltVal for zero values (PowerPoint pattern)
            Element fltVal = document.createElementNS(PRESENTATION_NS, "p:fltVal");
            fltVal.setAttribute("val", "0");
            val.appendChild(fltVal);
        } else {
            // Use strVal for PowerPoint expressions like "#ppt_w"
            Element strVal = document.createElementNS(PRESENTATION_NS, "p:strVal");
            strVal.setAttribute("val", value);
            val.appendChild(strVal);
        }
        
        tav.appendChild(val);
        return tav;
    }
    
}