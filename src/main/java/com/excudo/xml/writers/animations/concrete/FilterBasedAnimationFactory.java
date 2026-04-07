package com.excudo.xml.writers.animations.concrete;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.excudo.core.model.AnimationType;
import com.excudo.core.model.AnimationBinding;
import com.excudo.xml.writers.animations.abstracts.AbstractEffectAnimationFactory;

/**
 * Generic factory for filter-based animations that use the p:animEffect + p:set pattern.
 *
 * These animations share identical OOXML structure -- the only variation is the filter
 * attribute string on the p:animEffect element. The filter string is already defined
 * in each AnimationType's getPowerPointFilter() method.
 *
 * Covers: BLINDS_HORIZONTAL, BLINDS_VERTICAL, SPLIT_HORIZONTAL, SPLIT_VERTICAL,
 * CHECKERBOARD, BOX_IN, DIAMOND_IN, WHEEL_4, WHEEL_8, RANDOM_BARS_HORIZONTAL,
 * RANDOM_BARS_VERTICAL, DISSOLVE
 *
 * Note: FADE and WIPE have dedicated factories (FadeAnimationFactory, WipeAnimationFactory)
 * that were registered first. This factory handles the remaining filter-based types.
 */
public class FilterBasedAnimationFactory extends AbstractEffectAnimationFactory {

    private static final AnimationType[] SUPPORTED_ANIMATIONS = {
        AnimationType.BLINDS_HORIZONTAL,
        AnimationType.BLINDS_VERTICAL,
        AnimationType.SPLIT_HORIZONTAL,
        AnimationType.SPLIT_VERTICAL,
        AnimationType.CHECKERBOARD,
        AnimationType.BOX_IN,
        AnimationType.DIAMOND_IN,
        AnimationType.WHEEL_4,
        AnimationType.WHEEL_8,
        AnimationType.RANDOM_BARS_HORIZONTAL,
        AnimationType.RANDOM_BARS_VERTICAL,
        AnimationType.DISSOLVE
    };

    @Override
    protected Element createAnimationEffect(Document document, AnimationBinding binding) {
        AnimationType type = binding.getAnimationType();
        String filter = type.getPowerPointFilter();
        String transition = binding.isEntranceAnimation() ? "in" : "out";
        return createStandardAnimEffect(document, binding, filter, transition);
    }

    @Override
    public AnimationType[] getSupportedAnimationTypes() {
        return SUPPORTED_ANIMATIONS.clone();
    }

    @Override
    public boolean supportsAnimationType(AnimationType animationType) {
        for (AnimationType supported : SUPPORTED_ANIMATIONS) {
            if (supported == animationType) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String getOoxmlPattern() {
        return "p:set + p:animEffect (Filter-based entrance/exit animations)";
    }
}
