package com.excudo.xml.writers.animations.concrete;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import javax.xml.xpath.XPathExpressionException;

import com.excudo.core.model.AnimationType;
import com.excudo.core.model.AnimationBinding;
import com.excudo.xml.writers.animations.abstracts.AbstractVisibilityAnimationFactory;
/**
 * AnimationFactory implementation for APPEAR animations.
 * Extends AbstractVisibilityAnimationFactory for writing functionality.
 * Based on PowerPoint native OOXML structure from timing dump analysis.
 * 
 * PowerPoint APPEAR pattern (slide_001_Appear_By_Paragraph.xml, slide_002_Appear_All_At_Once.xml):
 * 1. p:set (style.visibility = "visible") - implemented here
 * 
 * APPEAR: instantly makes content visible without any visual transition
 * 
 * Uses the simplest OOXML pattern - single p:set element for visibility control.
 * 
 * Parsing logic detects APPEAR animations by looking for:
 * - Only p:set[style.visibility] (no additional effects)
 */
public class AppearAnimationFactory extends AbstractVisibilityAnimationFactory {

    /**
     * Animation types supported by this factory.
     * Only APPEAR animations - these require simple visibility control.
     */
    private static final AnimationType[] SUPPORTED_ANIMATIONS = {
        AnimationType.APPEAR
    };
    
    /**
     * Create visibility set for APPEAR animations.
     * Based on slide_001_Appear_By_Paragraph.xml structure.
     * PowerPoint uses style.visibility = "visible" for appear animations.
     * 
     * @param document The XML document
     * @param binding The animation binding
     * @return The p:set element with visibility control
     */
    @Override
    protected Element createSetElement(Document document, AnimationBinding binding) {
        return createVisibilitySet(document, binding, "visible");
    }
    
    @Override
    public AnimationType[] getSupportedAnimationTypes() {
        return SUPPORTED_ANIMATIONS.clone();
    }
    
    @Override
    public boolean supportsAnimationType(AnimationType animationType) {
        return animationType == AnimationType.APPEAR;
    }
    
    @Override
    public String getOoxmlPattern() {
        return "p:set (Visibility pattern for APPEAR)";
    }
    
}