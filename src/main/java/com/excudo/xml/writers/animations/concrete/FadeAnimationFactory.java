package com.excudo.xml.writers.animations.concrete;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import javax.xml.xpath.XPathExpressionException;

import com.excudo.core.model.AnimationType;
import com.excudo.core.model.AnimationBinding;
import com.excudo.xml.writers.animations.abstracts.AbstractEffectAnimationFactory;
/**
 * AnimationFactory implementation for FADE animations.
 * Extends AbstractEffectAnimationFactory for writing.
 * Based on PowerPoint native OOXML structure from timing dump analysis.
 * 
 * PowerPoint FADE pattern (slide_003_Fade_In_By_Paragraph.xml, slide_047_Fade_Out_By_Paragraph.xml):
 * 1. p:set (visibility for entrance animations only) - handled by AbstractEffectAnimationFactory
 * 2. p:animEffect (fade filter) - implemented here
 * 
 * FADE IN: starts transparent, fades to visible
 * FADE OUT: starts visible, fades to transparent
 * 
 * Uses the "fade" filter with appropriate transition direction.
 * 
 * Parsing logic detects FADE animations by looking for:
 * - p:set[style.visibility] + p:animEffect[filter="fade"] pattern
 */
public class FadeAnimationFactory extends AbstractEffectAnimationFactory {

    /**
     * Animation types supported by this factory.
     * Only FADE animations - these require fade filter.
     */
    private static final AnimationType[] SUPPORTED_ANIMATIONS = {
        AnimationType.FADE
    };
    
    /**
     * Create fade effect for FADE animations.
     * Based on slide_003_Fade_In_By_Paragraph.xml structure.
     * PowerPoint uses "fade" filter with transition direction.
     * 
     * @param document The XML document
     * @param binding The animation binding
     * @return The p:animEffect element with fade filter
     */
    @Override
    protected Element createAnimationEffect(Document document, AnimationBinding binding) {
        String transition = binding.isEntranceAnimation() ? "in" : "out";
        return createStandardAnimEffect(document, binding, "fade", transition);
    }
    
    @Override
    public AnimationType[] getSupportedAnimationTypes() {
        return SUPPORTED_ANIMATIONS.clone();
    }
    
    @Override
    public boolean supportsAnimationType(AnimationType animationType) {
        return animationType == AnimationType.FADE;
    }
    
    @Override
    public String getOoxmlPattern() {
        return "p:set + p:animEffect (Effect pattern for FADE)";
    }
    
}