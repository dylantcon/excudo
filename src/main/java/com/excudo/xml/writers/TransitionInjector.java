package com.excudo.xml.writers;

import com.excudo.core.model.TransitionType;
import com.excudo.core.utils.XMLConstants;
import com.excudo.exceptions.XMLParsingException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Injects and removes slide transition elements in PPTX slide XML.
 *
 * Transitions are placed as children of p:sld, after p:clrMapOvr and before p:timing.
 * Structure:
 * <pre>
 *   &lt;p:transition spd="med"&gt;
 *     &lt;p:fade/&gt;
 *   &lt;/p:transition&gt;
 * </pre>
 */
public final class TransitionInjector {

    private static final String P = XMLConstants.PRESENTATION_NS;

    private TransitionInjector() {}

    /**
     * Set a transition on a slide document. Replaces any existing transition.
     *
     * @param document    the slide DOM document
     * @param type        the transition type
     * @param speed       transition speed: "slow", "med", or "fast" (null defaults to "med")
     * @param advanceTimeMs auto-advance time in milliseconds (null for manual advance only)
     * @param durationMs  explicit duration in milliseconds (null uses speed default)
     */
    public static void setTransition(Document document, TransitionType type,
                                      String speed, Integer advanceTimeMs, Integer durationMs) throws XMLParsingException {
        if (type == TransitionType.NONE) {
            removeTransition(document);
            return;
        }

        // Remove any existing transition first
        removeTransition(document);

        Element sld = document.getDocumentElement();
        if (sld == null) {
            throw new XMLParsingException("Slide document has no root element");
        }

        // Build p:transition element
        Element transition = document.createElementNS(P, "p:transition");
        transition.setAttribute("spd", speed != null ? speed : "med");
        if (durationMs != null) {
            transition.setAttribute("dur", String.valueOf(durationMs));
        }
        if (advanceTimeMs != null) {
            transition.setAttribute("advTm", String.valueOf(advanceTimeMs));
        }

        // Add child transition type element
        String xmlElementName = type.getXmlElementName();
        if (xmlElementName != null) {
            Element typeEl = document.createElementNS(P, xmlElementName);
            if (type.getAttributeName() != null) {
                typeEl.setAttribute(type.getAttributeName(), type.getAttributeValue());
            }
            transition.appendChild(typeEl);
        }

        // Insert after p:clrMapOvr, before p:timing
        Element timing = findChildElement(sld, "p:timing");
        if (timing != null) {
            sld.insertBefore(transition, timing);
        } else {
            sld.appendChild(transition);
        }
    }

    /**
     * Remove any transition from a slide document.
     */
    public static void removeTransition(Document document) {
        Element sld = document.getDocumentElement();
        if (sld == null) return;

        NodeList transitions = sld.getElementsByTagNameNS(P, "transition");
        for (int i = transitions.getLength() - 1; i >= 0; i--) {
            sld.removeChild(transitions.item(i));
        }
    }

    /**
     * Check if a slide document has a transition.
     */
    public static boolean hasTransition(Document document) {
        Element sld = document.getDocumentElement();
        if (sld == null) return false;
        return sld.getElementsByTagNameNS(P, "transition").getLength() > 0;
    }

    private static Element findChildElement(Element parent, String qualifiedName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element) {
                Element el = (Element) children.item(i);
                if (qualifiedName.equals(el.getTagName())) {
                    return el;
                }
            }
        }
        return null;
    }
}
