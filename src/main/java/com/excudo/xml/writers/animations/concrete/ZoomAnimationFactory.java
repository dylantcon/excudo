package com.excudo.xml.writers.animations.concrete;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import javax.xml.xpath.XPathExpressionException;

import com.excudo.core.model.AnimationType;
import com.excudo.core.model.AnimationBinding;
import com.excudo.xml.writers.animations.abstracts.AbstractCoordinateEffectFactory;
import java.util.ArrayList;
import java.util.List;

/**
 * AnimationFactory implementation for ZOOM animations.
 * Extends AbstractCoordinateEffectFactory for writing functionality.
 * Based on PowerPoint native OOXML structure from timing dump analysis.
 * 
 * PowerPoint ZOOM pattern (slide_041_Zoom_In_Object_Center.xml, slide_085_Zoom_Out_Object_Center.xml):
 * 1. p:set (visibility for entrance animations only) - handled by AbstractCoordinateEffectFactory
 * 2. p:anim (width animation - ppt_w) - implemented here
 * 3. p:anim (height animation - ppt_h) - implemented here
 * 4. p:animEffect (fade effect) - implemented here
 * 
 * ZOOM IN: starts from 0 size, grows to normal size with fade in
 * ZOOM OUT: starts from normal size, shrinks to 0 with fade out
 * 
 * CRITICAL: This uses p:anim for ppt_w/ppt_h, NOT p:animScale!
 * PowerPoint's native zoom animations use coordinate-based size animation.
 * 
 * Parsing logic detects ZOOM animations by looking for:
 * - p:set[style.visibility] + p:anim[ppt_w] + p:anim[ppt_h] + p:animEffect[filter="fade"] pattern
 */
public class ZoomAnimationFactory extends AbstractCoordinateEffectFactory {

    /**
     * Animation types supported by this factory.
     * Only ZOOM animations - these require specific coordinate-based size animation.
     */
    private static final AnimationType[] SUPPORTED_ANIMATIONS = {
        AnimationType.ZOOM
    };
    
    /**
     * Create attribute-specific animations for ZOOM (ppt_w and ppt_h).
     * Based on slide_041_Zoom_In_Object_Center.xml structure.
     * 
     * @param document The XML document
     * @param binding The animation binding
     * @return List containing width and height p:anim elements
     */
    @Override
    protected List<Element> createAttributeAnimations(Document document, AnimationBinding binding) {
        List<Element> animations = new ArrayList<>();
        
        if (binding.isEntranceAnimation()) {
            // ZOOM IN: 0 → ppt_w/ppt_h
            animations.add(createAttributeAnimation(document, binding, "ppt_w", "0", "#ppt_w"));
            animations.add(createAttributeAnimation(document, binding, "ppt_h", "0", "#ppt_h"));
        } else {
            // ZOOM OUT: ppt_w/ppt_h → 0
            animations.add(createAttributeAnimation(document, binding, "ppt_w", "#ppt_w", "0"));
            animations.add(createAttributeAnimation(document, binding, "ppt_h", "#ppt_h", "0"));
        }
        
        return animations;
    }
    
    /**
     * Create fade effect for ZOOM animations.
     * Based on slide_041_Zoom_In_Object_Center.xml structure.
     * PowerPoint always includes fade effect with zoom animations.
     * 
     * @param document The XML document
     * @param binding The animation binding
     * @return The p:animEffect element with fade filter
     */
    @Override
    protected Element createVisualEffect(Document document, AnimationBinding binding) {
        Element animEffect = document.createElementNS(PRESENTATION_NS, "p:animEffect");
        animEffect.setAttribute("filter", "fade");
        animEffect.setAttribute("transition", binding.isEntranceAnimation() ? "in" : "out");
        
        // Create common behavior without fill="hold" per oracle pattern for animEffect
        Element cBhvr = createCommonBehaviorNoFill(document, binding, "0", binding.getDuration());
        animEffect.appendChild(cBhvr);
        
        return animEffect;
    }
    
    @Override
    public AnimationType[] getSupportedAnimationTypes() {
        return SUPPORTED_ANIMATIONS.clone();
    }
    
    @Override
    public boolean supportsAnimationType(AnimationType animationType) {
        return animationType == AnimationType.ZOOM;
    }
    
    @Override
    public String getOoxmlPattern() {
        return "p:set + p:anim×2 + p:animEffect (Coordinate+Effect pattern for ZOOM)";
    }
    
}