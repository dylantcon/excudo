package com.excudo.xml.writers.animations;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import com.excudo.core.model.AnimationType;
import com.excudo.core.model.AnimationBinding;
import com.excudo.core.model.ShapeGeometry;

import java.util.ArrayList;
import java.util.List;

/**
 * Concrete AnimationFactory for color-based emphasis animations.
 * Handles HSL color shift (DARKEN, DESATURATE, LIGHTEN) and RGB color target (COLOR_PULSE)
 * using p:animClr pattern from oracle OOXML analysis.
 *
 * Pattern A - HSL Color Shift (DARKEN, DESATURATE, LIGHTEN):
 *   3x p:animClr[clrSpc=hsl, dir=cw] targeting style.color, fillcolor, stroke.color
 *   + 1x p:set for fill.type=solid
 *   First p:animClr has override="childStyle" on cBhvr
 *
 * Pattern B - RGB Color Target (COLOR_PULSE):
 *   2x p:animClr[clrSpc=rgb, dir=cw, autoRev=1] targeting style.color, fillcolor
 *   + 2x p:set for fill.type=solid and fill.on=true
 *   All inner cTn have autoRev="1" and fill="remove"
 *
 * OOXML Pattern: p:animClr x 2-3 + p:set x 1-2
 */
public class ColorAnimationFactory extends AbstractAnimationFactory {

    private static final String DRAWING_ML_NS = "http://schemas.openxmlformats.org/drawingml/2006/main";

    private static final AnimationType[] SUPPORTED_ANIMATIONS = {
        AnimationType.DARKEN,
        AnimationType.DESATURATE,
        AnimationType.LIGHTEN,
        AnimationType.COLOR_PULSE,
        AnimationType.FONT_COLOR_CHANGE
    };

    @Override
    public List<Element> createAnimationElements(Document document, AnimationBinding binding, ShapeGeometry geometry) {
        List<Element> elements = new ArrayList<>();

        AnimationType type = binding.getAnimationType();
        if (type == AnimationType.COLOR_PULSE) {
            createColorPulseElements(document, binding, elements);
        } else if (type == AnimationType.FONT_COLOR_CHANGE) {
            createFontColorChangeElements(document, binding, elements);
        } else {
            createHslEmphasisElements(document, binding, elements);
        }

        return elements;
    }

    /**
     * Create HSL color shift elements for DARKEN, DESATURATE, LIGHTEN.
     * Oracle pattern: 3x p:animClr (style.color, fillcolor, stroke.color) + 1x p:set (fill.type=solid)
     */
    private void createHslEmphasisElements(Document document, AnimationBinding binding, List<Element> elements) {
        HslDeltas deltas = getHslDeltas(binding.getAnimationType(), binding);

        String[] attrNames = {"style.color", "fillcolor", "stroke.color"};
        for (int i = 0; i < attrNames.length; i++) {
            Element animClr = document.createElementNS(PRESENTATION_NS, "p:animClr");
            animClr.setAttribute("clrSpc", "hsl");
            animClr.setAttribute("dir", "cw");

            Element cBhvr = createCommonBehavior(document, binding, "0", binding.getDuration());
            if (i == 0) {
                cBhvr.setAttribute("override", "childStyle");
            }
            // Set fill="hold" on inner cTn
            Element innerCTn = (Element) cBhvr.getFirstChild();
            innerCTn.setAttribute("fill", "hold");

            animClr.appendChild(cBhvr);

            Element attrNameLst = document.createElementNS(PRESENTATION_NS, "p:attrNameLst");
            cBhvr.appendChild(attrNameLst);
            Element attrName = document.createElementNS(PRESENTATION_NS, "p:attrName");
            attrName.setTextContent(attrNames[i]);
            attrNameLst.appendChild(attrName);

            Element by = document.createElementNS(PRESENTATION_NS, "p:by");
            animClr.appendChild(by);
            Element hsl = document.createElementNS(PRESENTATION_NS, "p:hsl");
            hsl.setAttribute("h", deltas.h);
            hsl.setAttribute("s", deltas.s);
            hsl.setAttribute("l", deltas.l);
            by.appendChild(hsl);

            elements.add(animClr);
        }

        // p:set for fill.type = solid
        elements.add(createPropertySet(document, binding, "fill.type", "solid", binding.getDuration(), false));
    }

    /**
     * Create RGB color target elements for COLOR_PULSE.
     * Oracle pattern: 2x p:animClr (style.color, fillcolor) with autoRev + p:to srgbClr
     * + 2x p:set (fill.type=solid, fill.on=true), all with autoRev and fill=remove
     */
    private void createColorPulseElements(Document document, AnimationBinding binding, List<Element> elements) {
        String targetColor = binding.getEffectParam(AnimationBinding.PARAM_COLOR, "181818");
        // Use half the total duration since autoRev doubles effective time
        String halfDuration = calculateHalfDuration(binding.getDuration());

        String[] attrNames = {"style.color", "fillcolor"};
        for (int i = 0; i < attrNames.length; i++) {
            Element animClr = document.createElementNS(PRESENTATION_NS, "p:animClr");
            animClr.setAttribute("clrSpc", "rgb");
            animClr.setAttribute("dir", "cw");

            Element cBhvr = createCommonBehavior(document, binding, "0", halfDuration);
            if (i == 0) {
                cBhvr.setAttribute("override", "childStyle");
            }
            // Set autoRev and fill=remove on inner cTn
            Element innerCTn = (Element) cBhvr.getFirstChild();
            innerCTn.setAttribute("autoRev", "1");
            innerCTn.setAttribute("fill", "remove");

            animClr.appendChild(cBhvr);

            Element attrNameLst = document.createElementNS(PRESENTATION_NS, "p:attrNameLst");
            cBhvr.appendChild(attrNameLst);
            Element attrName = document.createElementNS(PRESENTATION_NS, "p:attrName");
            attrName.setTextContent(attrNames[i]);
            attrNameLst.appendChild(attrName);

            Element to = document.createElementNS(PRESENTATION_NS, "p:to");
            animClr.appendChild(to);
            to.appendChild(createColorElement(document, targetColor));

            elements.add(animClr);
        }

        // p:set for fill.type = solid (with autoRev + fill=remove)
        elements.add(createPropertySet(document, binding, "fill.type", "solid", halfDuration, true));
        // p:set for fill.on = true (with autoRev + fill=remove)
        elements.add(createPropertySet(document, binding, "fill.on", "true", halfDuration, true));
    }

    /**
     * Create RGB color target elements for FONT_COLOR_CHANGE.
     * Same structure as COLOR_PULSE but permanent: fill="hold" on inner cTn, no autoRev,
     * and uses full duration (no halving since no autoRev doubling).
     */
    private void createFontColorChangeElements(Document document, AnimationBinding binding, List<Element> elements) {
        String targetColor = binding.getEffectParam(AnimationBinding.PARAM_COLOR, "181818");
        String duration = binding.getDuration();

        String[] attrNames = {"style.color", "fillcolor"};
        for (int i = 0; i < attrNames.length; i++) {
            Element animClr = document.createElementNS(PRESENTATION_NS, "p:animClr");
            animClr.setAttribute("clrSpc", "rgb");
            animClr.setAttribute("dir", "cw");

            Element cBhvr = createCommonBehavior(document, binding, "0", duration);
            if (i == 0) {
                cBhvr.setAttribute("override", "childStyle");
            }
            // Permanent change: fill="hold", no autoRev
            Element innerCTn = (Element) cBhvr.getFirstChild();
            innerCTn.setAttribute("fill", "hold");

            animClr.appendChild(cBhvr);

            Element attrNameLst = document.createElementNS(PRESENTATION_NS, "p:attrNameLst");
            cBhvr.appendChild(attrNameLst);
            Element attrName = document.createElementNS(PRESENTATION_NS, "p:attrName");
            attrName.setTextContent(attrNames[i]);
            attrNameLst.appendChild(attrName);

            Element to = document.createElementNS(PRESENTATION_NS, "p:to");
            animClr.appendChild(to);
            to.appendChild(createColorElement(document, targetColor));

            elements.add(animClr);
        }

        // p:set for fill.type = solid (permanent: fill="hold", no autoRev)
        elements.add(createPropertySet(document, binding, "fill.type", "solid", duration, false));
        // p:set for fill.on = true (permanent: fill="hold", no autoRev)
        elements.add(createPropertySet(document, binding, "fill.on", "true", duration, false));
    }

    /**
     * Create a p:set element for setting a property value.
     * Used for fill.type=solid, fill.on=true patterns.
     */
    private Element createPropertySet(Document document, AnimationBinding binding,
                                      String propertyName, String value, String duration, boolean autoRev) {
        Element set = document.createElementNS(PRESENTATION_NS, "p:set");

        Element cBhvr = createCommonBehavior(document, binding, "0", duration);
        set.appendChild(cBhvr);

        Element innerCTn = (Element) cBhvr.getFirstChild();
        if (autoRev) {
            innerCTn.setAttribute("autoRev", "1");
            innerCTn.setAttribute("fill", "remove");
        } else {
            innerCTn.setAttribute("fill", "hold");
        }

        Element attrNameLst = document.createElementNS(PRESENTATION_NS, "p:attrNameLst");
        cBhvr.appendChild(attrNameLst);
        Element attrName = document.createElementNS(PRESENTATION_NS, "p:attrName");
        attrName.setTextContent(propertyName);
        attrNameLst.appendChild(attrName);

        Element to = document.createElementNS(PRESENTATION_NS, "p:to");
        set.appendChild(to);
        Element strVal = document.createElementNS(PRESENTATION_NS, "p:strVal");
        strVal.setAttribute("val", value);
        to.appendChild(strVal);

        return set;
    }

    /**
     * Create the appropriate color element for a p:to target.
     * Supports "scheme:accent2" for scheme colors and "FF0000" for sRGB hex colors.
     */
    private Element createColorElement(Document document, String colorSpec) {
        if (colorSpec.startsWith("scheme:")) {
            Element schemeClr = document.createElementNS(DRAWING_ML_NS, "a:schemeClr");
            schemeClr.setAttribute("val", colorSpec.substring(7));
            return schemeClr;
        } else {
            Element srgbClr = document.createElementNS(DRAWING_ML_NS, "a:srgbClr");
            srgbClr.setAttribute("val", colorSpec);
            return srgbClr;
        }
    }

    private HslDeltas getHslDeltas(AnimationType type, AnimationBinding binding) {
        // Base oracle values at 100% intensity
        // DARKEN:     s=-12549, l=-25098
        // DESATURATE: s=-70588, l=0
        // LIGHTEN:    s=12549, l=25098
        // effectParams "intensity" (0-100) scales these proportionally; default=100 (oracle values)
        double scale = 1.0;
        String intensityParam = binding.getEffectParam(AnimationBinding.PARAM_INTENSITY);
        if (intensityParam != null) {
            try { scale = Integer.parseInt(intensityParam) / 100.0; } catch (NumberFormatException ignored) {}
        }

        return switch (type) {
            case DARKEN     -> new HslDeltas("0", String.valueOf((int)(-12549 * scale)), String.valueOf((int)(-25098 * scale)));
            case DESATURATE -> new HslDeltas("0", String.valueOf((int)(-70588 * scale)), "0");
            case LIGHTEN    -> new HslDeltas("0", String.valueOf((int)(12549 * scale)), String.valueOf((int)(25098 * scale)));
            default         -> new HslDeltas("0", "0", "0");
        };
    }

    private record HslDeltas(String h, String s, String l) {}

    @Override
    protected String getOuterFill(AnimationBinding binding) {
        // COLOR_PULSE uses autoRev on inner elements, so outer cTn is fill="remove" (oracle pattern).
        // HSL emphasis (DARKEN/DESATURATE/LIGHTEN) and FONT_COLOR_CHANGE use fill="hold" on inner
        // elements (permanent effect), so outer is "hold".
        if (binding.getAnimationType() == AnimationType.COLOR_PULSE) {
            return "remove";
        }
        return "hold";
    }

    @Override
    public AnimationType[] getSupportedAnimationTypes() {
        return SUPPORTED_ANIMATIONS.clone();
    }

    @Override
    public String getOoxmlPattern() {
        return "p:animClr + p:set";
    }

    @Override
    public boolean supportsAnimationType(AnimationType animationType) {
        return switch (animationType) {
            case DARKEN, DESATURATE, LIGHTEN, COLOR_PULSE, FONT_COLOR_CHANGE -> true;
            default -> false;
        };
    }
}
