package com.excudo.xml.writers.animations.concrete;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import javax.xml.xpath.XPathExpressionException;

import com.excudo.core.model.AnimationType;
import com.excudo.core.model.AnimationBinding;
import com.excudo.xml.writers.animations.abstracts.AbstractEffectAnimationFactory;
/**
 * AnimationFactory implementation for WIPE animations.
 * Extends AbstractEffectAnimationFactory for writing functionality.
 * Based on PowerPoint native OOXML structure from timing dump analysis.
 * 
 * PowerPoint WIPE pattern (slide_149_Wipe_In_From_Bottom.xml, slide_153_Wipe_Out_From_Bottom.xml):
 * 1. p:set (visibility for entrance animations only) - handled by AbstractEffectAnimationFactory
 * 2. p:animEffect (wipe filter with direction) - implemented here
 * 
 * WIPE IN: content appears from specified direction
 * WIPE OUT: content disappears toward specified direction
 * 
 * Uses the "wipe" filter with appropriate transition direction.
 * Direction is encoded in the filter attribute based on animation type.
 * 
 * Parsing logic detects WIPE animations by looking for:
 * - p:set[style.visibility] + p:animEffect[filter="wipe(direction)"] pattern
 */
public class WipeAnimationFactory extends AbstractEffectAnimationFactory {

    /**
     * Animation types supported by this factory.
     * All WIPE variants with directional filters.
     */
    private static final AnimationType[] SUPPORTED_ANIMATIONS = {
        AnimationType.WIPE_LEFT,
        AnimationType.WIPE_RIGHT,
        AnimationType.WIPE_UP,
        AnimationType.WIPE_DOWN
    };
    
    /**
     * Create wipe effect for WIPE animations.
     * Based on slide_149_Wipe_In_From_Bottom.xml structure.
     * PowerPoint uses "wipe" filter with direction encoding.
     * 
     * @param document The XML document
     * @param binding The animation binding
     * @return The p:animEffect element with wipe filter and direction
     */
    @Override
    protected Element createAnimationEffect(Document document, AnimationBinding binding) {
        // PowerPoint encodes direction in filter attribute for wipe animations
        String filter = getWipeFilter(binding.getAnimationType());
        String transition = binding.isEntranceAnimation() ? "in" : "out";
        
        Element animEffect = document.createElementNS(PRESENTATION_NS, "p:animEffect");
        animEffect.setAttribute("filter", filter);
        animEffect.setAttribute("transition", transition);
        
        // Create common behavior without fill="hold" per oracle pattern for animEffect
        Element cBhvr = createCommonBehaviorNoFill(document, binding, "0", binding.getDuration());
        animEffect.appendChild(cBhvr);
        
        return animEffect;
    }
    
    /**
     * Get PowerPoint filter string for wipe direction.
     * Based on timing dump analysis of wipe animations.
     * 
     * @param animationType The wipe animation type
     * @return The filter string for PowerPoint OOXML
     */
    private String getWipeFilter(AnimationType animationType) {
        switch (animationType) {
            case WIPE_LEFT:
                return "wipe(left)";
            case WIPE_RIGHT:
                return "wipe(right)";
            case WIPE_UP:
                return "wipe(up)";
            case WIPE_DOWN:
                return "wipe(down)";
            default:
                return "wipe";
        }
    }
    
    @Override
    public AnimationType[] getSupportedAnimationTypes() {
        return SUPPORTED_ANIMATIONS.clone();
    }
    
    @Override
    public boolean supportsAnimationType(AnimationType animationType) {
        switch (animationType) {
            case WIPE_LEFT:
            case WIPE_RIGHT:
            case WIPE_UP:
            case WIPE_DOWN:
                return true;
            default:
                return false;
        }
    }
    
    @Override
    public String getOoxmlPattern() {
        return "p:set + p:animEffect (Effect pattern for WIPE with direction)";
    }
    
}