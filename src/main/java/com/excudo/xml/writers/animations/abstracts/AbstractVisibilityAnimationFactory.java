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
 * Abstract factory for animations using the Visibility pattern.
 * Handles animations that only require p:set elements for visibility/property changes.
 * 
 * 
 * Based on PowerPoint timing dump analysis, this pattern includes:
 * - p:set element only
 * - No coordinate movement (p:anim)
 * - No filter effects (p:animEffect)
 * - No color changes (p:animClr)
 * 
 * This pattern is used by:
 * - APPEAR animations (style.visibility = "visible")
 * - Other simple property-based animations
 * 
 * Template method pattern: concrete classes implement property-specific logic,
 * this class handles the common OOXML structure.
 */
public abstract class AbstractVisibilityAnimationFactory extends AbstractAnimationFactory {
    
    
    /**
     * Create animation elements for visibility animations using template method pattern.
     * Template method that defines the structure, delegates specifics to subclasses.
     */
    @Override
    public final List<Element> createAnimationElements(Document document, AnimationBinding binding, ShapeGeometry geometry) {
        List<Element> elements = new ArrayList<>();
        
        // Add visibility/property set element (abstract - implemented by subclasses)
        elements.add(createSetElement(document, binding));
        
        return elements;
    }
    
    /**
     * Create the p:set element for this visibility animation type.
     * Subclasses implement this to create specific property or visibility sets.
     * 
     * @param document The XML document
     * @param binding The animation binding
     * @return The p:set element
     */
    protected abstract Element createSetElement(Document document, AnimationBinding binding);
    
    /**
     * Create p:set element for property changes.
     * Helper method for subclasses that need property control.
     * 
     * @param document The XML document
     * @param binding The animation binding
     * @param attributeName The CSS/PowerPoint attribute name
     * @param value The target value
     * @return The p:set element
     */
    protected final Element createPropertySet(Document document, AnimationBinding binding, String attributeName, String value) {
        Element set = document.createElementNS(PRESENTATION_NS, "p:set");
        
        // Create common behavior for p:set (includes stCondLst per oracle pattern)
        Element cBhvr = createCommonBehaviorForSet(document, binding, "0", binding.getDuration());
        set.appendChild(cBhvr);
        
        // Add attribute name list
        Element attrNameLst = document.createElementNS(PRESENTATION_NS, "p:attrNameLst");
        cBhvr.appendChild(attrNameLst);
        
        Element attrName = document.createElementNS(PRESENTATION_NS, "p:attrName");
        attrName.setTextContent(attributeName);
        attrNameLst.appendChild(attrName);
        
        // Add target value
        Element to = document.createElementNS(PRESENTATION_NS, "p:to");
        set.appendChild(to);
        
        Element strVal = document.createElementNS(PRESENTATION_NS, "p:strVal");
        strVal.setAttribute("val", value);
        to.appendChild(strVal);
        
        return set;
    }
    
    
    
}