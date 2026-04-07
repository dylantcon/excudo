package com.excudo.xml.writers.animations;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import com.excudo.core.model.AnimationType;
import com.excudo.core.model.AnimationBinding;
import com.excudo.core.model.ShapeGeometry;

import java.util.ArrayList;
import java.util.List;

/**
 * Concrete AnimationFactory implementation for pure rotation animations.
 * Handles spin and oscillating rotation animations using p:animRot pattern.
 *
 * Supported animations:
 * - SPIN: Single p:animRot with 360-degree continuous rotation
 * - TEETER: 5 sequenced p:animRot elements creating oscillating rocking motion
 *   (oracle slide 91, presetID=32: +2deg, -4deg, +4deg, -4deg, +2deg)
 *
 * OOXML Pattern: p:animRot (+ optional p:set for entrance/exit)
 *
 * Note: Excludes composite animations like Grow_and_Turn which use
 * coordinated p:anim + p:animEffect patterns - those go to CompositeEntranceFactory.
 */
public class RotationAnimationFactory extends AbstractAnimationFactory {

    private static final AnimationType[] SUPPORTED_ANIMATIONS = {
        AnimationType.SPIN,
        AnimationType.TEETER
    };

    /**
     * TEETER keyframe definitions from oracle slide 1 (presetID=32).
     * Each entry: { byValue (60000 units per degree), delay, duration }.
     * Sequence produces oscillation: +2deg, -4deg, +4deg, -4deg, +2deg.
     * Oracle delays: {0, 200, 400, 600, 800} (cumulative absolute).
     */
    private static final int[][] TEETER_KEYFRAMES = {
        { 120000,   0, 100 },   // +2 degrees
        { -240000, 200, 200 },  // -4 degrees
        { 240000,  400, 200 },  // +4 degrees
        { -240000, 600, 200 },  // -4 degrees
        { 120000,  800, 200 }   // +2 degrees
    };

    @Override
    public List<Element> createAnimationElements(Document document, AnimationBinding binding, ShapeGeometry geometry) {
        List<Element> elements = new ArrayList<>();

        if (binding.getAnimationType() == AnimationType.TEETER) {
            createTeeterElements(document, binding, elements);
            return elements;
        }

        // SPIN path: entrance visibility + single rotation + exit visibility
        if (binding.isEntranceAnimation()) {
            elements.add(createVisibilitySet(document, binding, "visible"));
        }

        elements.add(createSpinRotation(document, binding));

        if (binding.isExitAnimation()) {
            elements.add(createVisibilitySet(document, binding, "hidden"));
        }

        return elements;
    }

    /**
     * Create single p:animRot for SPIN (360-degree continuous rotation).
     * Reads rotationDegrees from effectParams, defaulting to 360.
     */
    private Element createSpinRotation(Document document, AnimationBinding binding) {
        int degrees = 360;
        String degreesParam = binding.getEffectParam(AnimationBinding.PARAM_ROTATION_DEGREES);
        if (degreesParam != null) {
            try { degrees = Integer.parseInt(degreesParam); } catch (NumberFormatException ignored) {}
        }
        Element animRot = document.createElementNS(PRESENTATION_NS, "p:animRot");
        animRot.setAttribute("by", String.valueOf(degrees * 60000L)); // 60000 units/deg

        Element cBhvr = createCommonBehavior(document, binding, "0", binding.getDuration());
        cBhvr.setAttribute("additive", "base");
        animRot.appendChild(cBhvr);

        // MS-OE376 4.6.5(d,f): animRot must have attrNameLst with attrName = "r"
        Element attrNameLst = document.createElementNS(PRESENTATION_NS, "p:attrNameLst");
        cBhvr.appendChild(attrNameLst);
        Element attrName = document.createElementNS(PRESENTATION_NS, "p:attrName");
        attrName.setTextContent("r");
        attrNameLst.appendChild(attrName);

        return animRot;
    }

    /**
     * Create 5 sequenced p:animRot elements for TEETER oscillation.
     * Oracle pattern: staggered delays with alternating rotation values.
     * No visibility sets (emphasis animation, not entrance/exit).
     */
    private void createTeeterElements(Document document, AnimationBinding binding, List<Element> elements) {
        for (int[] keyframe : TEETER_KEYFRAMES) {
            Element animRot = document.createElementNS(PRESENTATION_NS, "p:animRot");
            animRot.setAttribute("by", String.valueOf(keyframe[0]));

            // Oracle pattern: TEETER cBhvr has NO additive attribute.
            // Use createCommonBehaviorForSet to always include stCondLst (even delay=0).
            Element cBhvr = createCommonBehaviorForSet(document, binding,
                    String.valueOf(keyframe[1]), String.valueOf(keyframe[2]));
            animRot.appendChild(cBhvr);

            // MS-OE376 4.6.5(d,f): animRot must have attrNameLst with attrName = "r"
            Element attrNameLst = document.createElementNS(PRESENTATION_NS, "p:attrNameLst");
            cBhvr.appendChild(attrNameLst);
            Element attrName = document.createElementNS(PRESENTATION_NS, "p:attrName");
            attrName.setTextContent("r");
            attrNameLst.appendChild(attrName);

            elements.add(animRot);
        }
    }

    @Override
    public AnimationType[] getSupportedAnimationTypes() {
        return SUPPORTED_ANIMATIONS.clone();
    }

    @Override
    public String getOoxmlPattern() {
        return "p:animRot";
    }

    @Override
    public boolean supportsAnimationType(AnimationType animationType) {
        return switch (animationType) {
            case SPIN, TEETER -> true;
            default -> false;
        };
    }

}
