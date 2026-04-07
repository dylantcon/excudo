package com.excudo.xml.writers.animations;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import com.excudo.core.model.AnimationType;
import com.excudo.core.model.AnimationBinding;
import com.excudo.core.model.ShapeGeometry;

import java.util.ArrayList;
import java.util.List;

/**
 * Factory for composite entrance/exit animations that combine multiple OOXML element types.
 * These animations use p:set + p:anim + p:animEffect patterns that don't fit any
 * single-element abstract factory.
 *
 * GROW_TURN (oracle slide 40, presetID=31, entrance):
 *   p:set (visibility=visible)
 *   p:anim[ppt_w]  0 -> #ppt_w          (scale width from 0 to full)
 *   p:anim[ppt_h]  0 -> #ppt_h          (scale height from 0 to full)
 *   p:anim[style.rotation]  90 -> 0      (rotate from 90deg to 0)
 *   p:animEffect[transition=in, filter=fade]
 *
 * SWIVEL (oracle slide 43, presetID=45, entrance):
 *   p:set (visibility=visible)
 *   p:animEffect[transition=in, filter=fade]
 *   p:anim[ppt_w]  fmla="#ppt_w*sin(2.5*pi*$)" 0->1
 *   p:anim[ppt_h]  #ppt_h -> #ppt_h     (constant height)
 *
 * OOXML Pattern: p:set + p:anim(s) + p:animEffect (composite)
 */
public class CompositeEntranceFactory extends AbstractAnimationFactory {

    private static final AnimationType[] SUPPORTED_ANIMATIONS = {
        AnimationType.GROW_TURN,
        AnimationType.SWIVEL
    };

    @Override
    public List<Element> createAnimationElements(Document document, AnimationBinding binding, ShapeGeometry geometry) {
        List<Element> elements = new ArrayList<>();

        AnimationType type = binding.getAnimationType();
        boolean isEntrance = binding.isEntranceAnimation();
        String transition = isEntrance ? "in" : "out";

        if (type == AnimationType.GROW_TURN) {
            createGrowTurnElements(document, binding, elements, isEntrance, transition);
        } else if (type == AnimationType.SWIVEL) {
            createSwivelElements(document, binding, elements, isEntrance, transition);
        }

        return elements;
    }

    /**
     * GROW_TURN: visibility + 3 coordinate anims (width, height, rotation) + fade effect.
     */
    private void createGrowTurnElements(Document document, AnimationBinding binding,
                                         List<Element> elements, boolean isEntrance, String transition) {
        String duration = binding.getDuration();

        // 1. Visibility set
        if (isEntrance) {
            elements.add(createVisibilitySet(document, binding, "visible"));
        }

        // 2. p:anim for ppt_w (scale width: 0 -> full for entrance, reverse for exit)
        String fromW = isEntrance ? "0" : "#ppt_w";
        String toW   = isEntrance ? "#ppt_w" : "0";
        elements.add(createCoordinateAnim(document, binding, "ppt_w", fromW, toW, duration));

        // 3. p:anim for ppt_h (scale height: 0 -> full for entrance, reverse for exit)
        String fromH = isEntrance ? "0" : "#ppt_h";
        String toH   = isEntrance ? "#ppt_h" : "0";
        elements.add(createCoordinateAnim(document, binding, "ppt_h", fromH, toH, duration));

        // 4. p:anim for style.rotation -- read rotationDegrees from effectParams, default 90
        int rotDeg = 90;
        String rotParam = binding.getEffectParam(AnimationBinding.PARAM_ROTATION_DEGREES);
        if (rotParam != null) {
            try { rotDeg = Integer.parseInt(rotParam); } catch (NumberFormatException ignored) {}
        }
        String fromR = isEntrance ? String.valueOf(rotDeg) : "0";
        String toR   = isEntrance ? "0" : String.valueOf(rotDeg);
        elements.add(createCoordinateAnim(document, binding, "style.rotation", fromR, toR, duration));

        // 5. p:animEffect with fade
        elements.add(createFadeEffect(document, binding, transition, duration));

        // Exit: visibility hidden after effect
        if (!isEntrance) {
            elements.add(createVisibilitySet(document, binding, "hidden"));
        }
    }

    /**
     * SWIVEL: visibility + fade effect + 2 coordinate anims (width with sin formula, constant height).
     */
    private void createSwivelElements(Document document, AnimationBinding binding,
                                       List<Element> elements, boolean isEntrance, String transition) {
        String duration = binding.getDuration();

        // 1. Visibility set
        if (isEntrance) {
            elements.add(createVisibilitySet(document, binding, "visible"));
        }

        // 2. p:animEffect with fade
        elements.add(createFadeEffect(document, binding, transition, duration));

        // 3. p:anim for ppt_w with sinusoidal formula
        String fmla = isEntrance ? "#ppt_w*sin(2.5*pi*$)" : "#ppt_w*sin(2.5*pi*(1-$))";
        elements.add(createCoordinateAnimWithFormula(document, binding, "ppt_w", "0", "1", fmla, duration));

        // 4. p:anim for ppt_h (constant)
        elements.add(createCoordinateAnim(document, binding, "ppt_h", "#ppt_h", "#ppt_h", duration));

        // Exit: visibility hidden after effect
        if (!isEntrance) {
            elements.add(createVisibilitySet(document, binding, "hidden"));
        }
    }

    /**
     * Create a p:anim element for coordinate-based animation (width, height, rotation).
     */
    private Element createCoordinateAnim(Document document, AnimationBinding binding,
                                          String attrNameValue, String fromVal, String toVal, String duration) {
        return createCoordinateAnimWithFormula(document, binding, attrNameValue, fromVal, toVal, null, duration);
    }

    /**
     * Create a p:anim element with optional formula for computed animations.
     */
    private Element createCoordinateAnimWithFormula(Document document, AnimationBinding binding,
                                                     String attrNameValue, String fromVal, String toVal,
                                                     String formula, String duration) {
        Element anim = document.createElementNS(PRESENTATION_NS, "p:anim");
        anim.setAttribute("calcmode", "lin");
        anim.setAttribute("valueType", "num");
        if (formula != null) {
            anim.setAttribute("from", fromVal);
            anim.setAttribute("to", toVal);
        }

        Element cBhvr = createCommonBehavior(document, binding, "0", duration);
        anim.appendChild(cBhvr);

        // Add attribute name
        Element attrNameLst = document.createElementNS(PRESENTATION_NS, "p:attrNameLst");
        cBhvr.appendChild(attrNameLst);
        Element attrName = document.createElementNS(PRESENTATION_NS, "p:attrName");
        attrName.setTextContent(attrNameValue);
        attrNameLst.appendChild(attrName);

        if (formula != null) {
            anim.setAttribute("fmla", formula);
        }

        // Add timing value list with from/to
        Element tavLst = document.createElementNS(PRESENTATION_NS, "p:tavLst");
        anim.appendChild(tavLst);
        tavLst.appendChild(createTimingValue(document, "0", fromVal));
        tavLst.appendChild(createTimingValue(document, "100000", toVal));

        return anim;
    }

    /**
     * Create a p:animEffect element for fade transition.
     */
    private Element createFadeEffect(Document document, AnimationBinding binding,
                                      String transition, String duration) {
        Element animEffect = document.createElementNS(PRESENTATION_NS, "p:animEffect");
        animEffect.setAttribute("transition", transition);
        animEffect.setAttribute("filter", "fade");

        Element cBhvr = createCommonBehaviorNoFill(document, binding, "0", duration);
        animEffect.appendChild(cBhvr);

        return animEffect;
    }

    @Override
    public AnimationType[] getSupportedAnimationTypes() {
        return SUPPORTED_ANIMATIONS.clone();
    }

    @Override
    public String getOoxmlPattern() {
        return "p:set + p:anim + p:animEffect (composite)";
    }

    @Override
    public boolean supportsAnimationType(AnimationType animationType) {
        return switch (animationType) {
            case GROW_TURN, SWIVEL -> true;
            default -> false;
        };
    }

}
