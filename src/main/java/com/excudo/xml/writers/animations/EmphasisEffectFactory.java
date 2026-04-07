package com.excudo.xml.writers.animations;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import com.excudo.core.model.AnimationType;
import com.excudo.core.model.AnimationBinding;
import com.excudo.core.model.ShapeGeometry;

import java.util.ArrayList;
import java.util.List;

/**
 * Factory for emphasis animations that use p:animEffect and/or p:animScale patterns.
 * Handles TRANSPARENCY and PULSE, which use fundamentally different OOXML structures
 * from the p:animClr-based emphasis animations in ColorAnimationFactory.
 *
 * TRANSPARENCY (oracle slide 115, presetID=9, presetClass=emph):
 *   p:set[style.opacity = "0.75", dur=indefinite]
 *   p:animEffect[filter="image", prLst="opacity: 0.75", rctx="IE" on cBhvr, dur=indefinite]
 *   Outer cTn has no fill attribute.
 *
 * PULSE (oracle slide 89, presetID=26, presetClass=emph):
 *   p:animEffect[transition="out", filter="fade", tmFilter="0, 0; .2, .5; .8, .5; 1, 0"]
 *   p:animScale[autoRev="1", by x=105000 y=105000]
 *   Outer cTn has fill="hold".
 *
 * OOXML Pattern: p:set + p:animEffect (or) p:animEffect + p:animScale
 */
public class EmphasisEffectFactory extends AbstractAnimationFactory {

    private static final AnimationType[] SUPPORTED_ANIMATIONS = {
        AnimationType.TRANSPARENCY,
        AnimationType.PULSE
    };

    @Override
    public List<Element> createAnimationElements(Document document, AnimationBinding binding, ShapeGeometry geometry) {
        List<Element> elements = new ArrayList<>();

        AnimationType type = binding.getAnimationType();
        if (type == AnimationType.TRANSPARENCY) {
            createTransparencyElements(document, binding, elements);
        } else if (type == AnimationType.PULSE) {
            createPulseElements(document, binding, elements);
        }

        return elements;
    }

    /**
     * Create TRANSPARENCY emphasis elements.
     * Oracle: p:set (style.opacity) + p:animEffect (filter="image", prLst, rctx="IE")
     * Both elements use dur="indefinite".
     */
    private void createTransparencyElements(Document document, AnimationBinding binding, List<Element> elements) {
        // Read opacity from effectParams (0-100 percentage), default 75 -> 0.75 OOXML
        int opacityPct = 75;
        String opacityParam = binding.getEffectParam(AnimationBinding.PARAM_OPACITY);
        if (opacityParam != null) {
            try { opacityPct = Integer.parseInt(opacityParam); } catch (NumberFormatException ignored) {}
        }
        String opacity = String.format("%.2f", opacityPct / 100.0);

        // 1. p:set for style.opacity
        Element set = document.createElementNS(PRESENTATION_NS, "p:set");
        Element setCBhvr = createCommonBehaviorForSet(document, binding, "0", "indefinite");
        set.appendChild(setCBhvr);

        Element attrNameLst = document.createElementNS(PRESENTATION_NS, "p:attrNameLst");
        setCBhvr.appendChild(attrNameLst);
        Element attrName = document.createElementNS(PRESENTATION_NS, "p:attrName");
        attrName.setTextContent("style.opacity");
        attrNameLst.appendChild(attrName);

        Element to = document.createElementNS(PRESENTATION_NS, "p:to");
        set.appendChild(to);
        Element strVal = document.createElementNS(PRESENTATION_NS, "p:strVal");
        strVal.setAttribute("val", opacity);
        to.appendChild(strVal);

        elements.add(set);

        // 2. p:animEffect with filter="image" and prLst
        Element animEffect = document.createElementNS(PRESENTATION_NS, "p:animEffect");
        animEffect.setAttribute("filter", "image");
        animEffect.setAttribute("prLst", "opacity: " + opacity);

        Element effectCBhvr = createCommonBehaviorNoFill(document, binding, "0", "indefinite");
        effectCBhvr.setAttribute("rctx", "IE");
        animEffect.appendChild(effectCBhvr);

        elements.add(animEffect);
    }

    /**
     * Create PULSE emphasis elements.
     * Oracle: p:animEffect (fade + tmFilter) + p:animScale (autoRev, by 105000x105000)
     */
    private void createPulseElements(Document document, AnimationBinding binding, List<Element> elements) {
        String duration = binding.getDuration();

        // 1. p:animEffect with transition="out", filter="fade", and timing filter
        Element animEffect = document.createElementNS(PRESENTATION_NS, "p:animEffect");
        animEffect.setAttribute("transition", "out");
        animEffect.setAttribute("filter", "fade");

        Element effectCBhvr = createCommonBehaviorNoFill(document, binding, "0", duration);
        animEffect.appendChild(effectCBhvr);

        // Set tmFilter on inner cTn for the opacity pulse curve
        Element effectCTn = (Element) effectCBhvr.getFirstChild();
        effectCTn.setAttribute("tmFilter", "0, 0; .2, .5; .8, .5; 1, 0");

        elements.add(animEffect);

        // 2. p:animScale with autoRev for size pulse (105% scale bounce)
        Element animScale = document.createElementNS(PRESENTATION_NS, "p:animScale");

        // Half duration since autoRev doubles the effective time
        String halfDuration = calculateHalfDuration(duration);
        Element scaleCBhvr = createCommonBehavior(document, binding, "0", halfDuration);
        animScale.appendChild(scaleCBhvr);

        // Set autoRev and fill=hold on inner cTn
        Element scaleCTn = (Element) scaleCBhvr.getFirstChild();
        scaleCTn.setAttribute("autoRev", "1");
        scaleCTn.setAttribute("fill", "hold");

        // Read scalePercent from effectParams, default 105
        int scalePct = 105;
        String scaleParam = binding.getEffectParam(AnimationBinding.PARAM_SCALE_PERCENT);
        if (scaleParam != null) {
            try { scalePct = Integer.parseInt(scaleParam); } catch (NumberFormatException ignored) {}
        }
        String scaleVal = String.valueOf(scalePct * 1000); // 105% = 105000 PowerPoint units

        Element by = document.createElementNS(PRESENTATION_NS, "p:by");
        by.setAttribute("x", scaleVal);
        by.setAttribute("y", scaleVal);
        animScale.appendChild(by);

        elements.add(animScale);
    }

    @Override
    public AnimationType[] getSupportedAnimationTypes() {
        return SUPPORTED_ANIMATIONS.clone();
    }

    /**
     * Override outer fill per oracle patterns:
     * - TRANSPARENCY: no fill attribute (oracle slides 115-118)
     * - PULSE: fill="hold" (oracle slide 89) -- default from parent
     */
    @Override
    protected String getOuterFill(AnimationBinding binding) {
        if (binding.getAnimationType() == AnimationType.TRANSPARENCY) {
            return null; // Oracle: no fill on outer cTn for transparency
        }
        return super.getOuterFill(binding);
    }

    @Override
    public String getOoxmlPattern() {
        return "p:animEffect + p:animScale (or) p:set + p:animEffect";
    }

    @Override
    public boolean supportsAnimationType(AnimationType animationType) {
        return switch (animationType) {
            case TRANSPARENCY, PULSE -> true;
            default -> false;
        };
    }
}
