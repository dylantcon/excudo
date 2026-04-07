package com.excudo.xml.writers.animations;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import com.excudo.core.model.AnimationType;
import com.excudo.core.model.AnimationBinding;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.utils.ComponentLogger;
import com.excudo.core.utils.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Factory for motion path animations using p:animMotion element.
 *
 * Oracle pattern (slides 129, 133, 154):
 * - p:animMotion with origin="layout", pathEditMode="relative"
 * - rAng="0" only for preset paths (linear/arc), absent for custom
 * - ptsTypes: one "A" per path command (M, L, C)
 * - p:rCtr child: direction-specific for presets, absent for custom
 * - Path strings in SVG-like syntax: M (moveto), L (lineto), C (curveto)
 * - presetClass="path" (always, regardless of transition direction)
 * - accel="50000" decel="50000" on outer cTn
 *
 * Supported: MOTION_LINEAR, MOTION_ARC, MOTION_CUSTOM
 *
 * OOXML Pattern: p:animMotion (+ optional p:set)
 */
public class MotionPathAnimationFactory extends AbstractAnimationFactory {

    private static final ComponentLogger logger = Logger.animation();

    private static final AnimationType[] SUPPORTED_ANIMATIONS = {
        AnimationType.MOTION_LINEAR,
        AnimationType.MOTION_ARC,
        AnimationType.MOTION_CUSTOM
    };

    // Oracle-verified default paths
    private static final String LINEAR_DEFAULT_PATH = "M -1.25E-6 3.33333E-6 L -1.25E-6 0.25 ";
    private static final String ARC_DEFAULT_PATH = "M -1.875E-6 3.33333E-6 L 0.06706 0.04004 C 0.08099 0.04907 0.10196 0.05393 0.12396 0.05393 C 0.14896 0.05393 0.16901 0.04907 0.18294 0.04004 L 0.25 3.33333E-6 ";
    private static final String CUSTOM_FALLBACK_PATH = "M 0 0 L 0.25 0.25 ";

    @Override
    protected String getPresetClass(String transition) {
        // Motion paths always use presetClass="path" regardless of direction
        return "path";
    }

    @Override
    protected void addExtraTimingAttributes(Element cTn, AnimationBinding binding) {
        // Acceleration/deceleration on outer cTn. AnimationBinding stores 0-100 range;
        // OOXML uses 0-100000 (thousandths of a percent).
        int accel = binding.getAcceleration();
        int decel = binding.getDeceleration();

        if (accel == 0 && decel == 0) {
            // Default to oracle pattern: 50000/50000 (50%/50%)
            accel = 50;
            decel = 50;
            logger.info("Motion path: no accel/decel specified, using oracle default 50%/50%");
        }

        // ECMA-376 safety net: accel + decel must not exceed 100%
        if (accel + decel > 100) {
            logger.warn("Motion path: accel(" + accel + ") + decel(" + decel + ") exceeds 100%, clamping proportionally");
            double total = accel + decel;
            accel = (int) Math.round(accel * 100.0 / total);
            decel = 100 - accel;
        }

        cTn.setAttribute("accel", String.valueOf(accel * 1000));
        cTn.setAttribute("decel", String.valueOf(decel * 1000));
    }

    @Override
    public List<Element> createAnimationElements(Document document, AnimationBinding binding, ShapeGeometry geometry) {
        List<Element> elements = new ArrayList<>();

        if (binding.isEntranceAnimation()) {
            elements.add(createVisibilitySet(document, binding, "visible"));
        }

        elements.add(createMotionPathAnimation(document, binding, geometry));

        if (binding.isExitAnimation()) {
            elements.add(createVisibilitySet(document, binding, "hidden"));
        }

        return elements;
    }

    private Element createMotionPathAnimation(Document document, AnimationBinding binding, ShapeGeometry geometry) {
        Element animMotion = document.createElementNS(PRESENTATION_NS, "p:animMotion");

        String path = calculateMotionPath(binding);
        animMotion.setAttribute("origin", "layout");
        animMotion.setAttribute("path", path);
        animMotion.setAttribute("pathEditMode", "relative");

        // Oracle: rAng="0" only for preset paths (linear/arc), absent for custom (slide 154)
        if (binding.getAnimationType() != AnimationType.MOTION_CUSTOM) {
            animMotion.setAttribute("rAng", "0");
        }

        animMotion.setAttribute("ptsTypes", calculatePtsTypes(binding.getAnimationType(), path));

        // Create common behavior (no additive="base" for motion paths per oracle)
        Element cBhvr = createCommonBehavior(document, binding, "0", binding.getDuration());
        animMotion.appendChild(cBhvr);

        // MS-OE376 4.6.4: animMotion cBhvr requires attrNameLst with ppt_x and ppt_y (oracle slide 141)
        Element attrNameLst = document.createElementNS(PRESENTATION_NS, "p:attrNameLst");
        cBhvr.appendChild(attrNameLst);
        Element attrNameX = document.createElementNS(PRESENTATION_NS, "p:attrName");
        attrNameX.setTextContent("ppt_x");
        attrNameLst.appendChild(attrNameX);
        Element attrNameY = document.createElementNS(PRESENTATION_NS, "p:attrName");
        attrNameY.setTextContent("ppt_y");
        attrNameLst.appendChild(attrNameY);

        // Oracle: p:rCtr only for preset paths, absent for custom (slide 154)
        addRotationCenter(document, animMotion, binding.getAnimationType());

        return animMotion;
    }

    /**
     * Add p:rCtr child element based on animation type.
     * Oracle: MOTION_LINEAR has x="0" y="12500" (slide 129),
     * MOTION_ARC has x="12500" y="2685" (slide 133),
     * MOTION_CUSTOM has no p:rCtr (slide 154).
     */
    private void addRotationCenter(Document document, Element animMotion, AnimationType type) {
        switch (type) {
            case MOTION_LINEAR -> {
                Element rCtr = document.createElementNS(PRESENTATION_NS, "p:rCtr");
                rCtr.setAttribute("x", "0");
                rCtr.setAttribute("y", "12500");
                animMotion.appendChild(rCtr);
            }
            case MOTION_ARC -> {
                Element rCtr = document.createElementNS(PRESENTATION_NS, "p:rCtr");
                rCtr.setAttribute("x", "12500");
                rCtr.setAttribute("y", "2685");
                animMotion.appendChild(rCtr);
            }
            case MOTION_CUSTOM -> {
                // No p:rCtr for custom paths (oracle slide 154)
            }
            default -> {
                // No rotation center for unknown types
            }
        }
    }

    /**
     * Calculate motion path string based on animation type and binding.
     * MOTION_CUSTOM uses the binding's motionPath field directly.
     */
    private String calculateMotionPath(AnimationBinding binding) {
        if (binding.getAnimationType() == AnimationType.MOTION_CUSTOM && binding.hasMotionPath()) {
            return binding.getMotionPath();
        }

        return switch (binding.getAnimationType()) {
            case MOTION_LINEAR -> LINEAR_DEFAULT_PATH;
            case MOTION_ARC -> ARC_DEFAULT_PATH;
            case MOTION_CUSTOM -> {
                logger.warn("MOTION_CUSTOM with no path provided -- using default diagonal fallback");
                yield CUSTOM_FALLBACK_PATH;
            }
            default -> CUSTOM_FALLBACK_PATH;
        };
    }

    /**
     * Calculate ptsTypes string matching path complexity.
     * Oracle format: one "A" per path command (M, L, C). No "1" or "E" characters.
     * Oracle slide 129 (Line Down): M..L.. = "AA"
     * Oracle slide 133 (Arc Down): M..L..C..C..L.. = "AAAAA"
     * Oracle slide 154 (Custom 9 commands): "AAAAAAAAA"
     */
    private String calculatePtsTypes(AnimationType type, String path) {
        int anchors = 0;
        for (char c : path.toCharArray()) {
            if (c == 'M' || c == 'L' || c == 'C') {
                anchors++;
            }
        }
        return "A".repeat(Math.max(1, anchors));
    }

    @Override
    public AnimationType[] getSupportedAnimationTypes() {
        return SUPPORTED_ANIMATIONS.clone();
    }

    @Override
    public String getOoxmlPattern() {
        return "p:animMotion";
    }

    @Override
    public boolean supportsAnimationType(AnimationType animationType) {
        return switch (animationType) {
            case MOTION_LINEAR, MOTION_ARC, MOTION_CUSTOM -> true;
            default -> false;
        };
    }

}
