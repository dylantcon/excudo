package com.excudo.xml.builders;

import com.excudo.core.model.*;
import com.excudo.core.utils.XMLConstants;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Writes shape styling (fill, line, theme style ref) into OOXML DOM elements.
 *
 * Handles two distinct concerns:
 * <ul>
 *   <li>Direct overrides: {@code <a:solidFill>}, {@code <a:ln>} inside {@code <p:spPr>}</li>
 *   <li>Theme style ref: {@code <p:style>} element as sibling of {@code <p:spPr>}</li>
 * </ul>
 */
public final class ShapeStyleXMLWriter {

    private ShapeStyleXMLWriter() {}

    /**
     * Apply styling to an existing shape element.
     * Injects fill/line into spPr and appends p:style after spPr.
     *
     * @param doc       the XML document
     * @param shapeElem the {@code <p:sp>} element
     * @param style     the style to apply (null = apply defaults)
     * @param hasText   whether the shape contains text (affects default fill color)
     */
    public static void applyStyle(Document doc, Element shapeElem, ShapeStyle style, boolean hasText) {
        if (style == null) {
            style = ShapeStyle.defaultStyle();
        }

        // Find spPr to inject fill/line
        Element spPr = findChild(shapeElem, "p:spPr");
        if (spPr != null) {
            writeFill(doc, spPr, style.getFill());
            writeLine(doc, spPr, style.getLine());
        }

        // Create and insert p:style after spPr, before txBody.
        // Skip entirely when ThemeStyleRef.NONE is set (text box pattern: no p:style).
        ThemeStyleRef effectiveTheme = style.getThemeStyle();
        if (effectiveTheme != ThemeStyleRef.NONE) {
            Element styleElem = writeThemeStyleRef(doc, effectiveTheme, hasText);
            Element txBody = findChild(shapeElem, "p:txBody");
            if (txBody != null) {
                shapeElem.insertBefore(styleElem, txBody);
            } else {
                shapeElem.appendChild(styleElem);
            }
        }
    }

    /**
     * Write fill element into spPr.
     */
    static void writeFill(Document doc, Element spPr, ShapeFill fill) {
        if (fill == null) return;

        switch (fill.getType()) {
            case SOLID -> {
                Element solidFill = doc.createElementNS(XMLConstants.DRAWING_NS, "a:solidFill");
                appendColorElement(doc, solidFill, fill.getColor());
                spPr.appendChild(solidFill);
            }
            case NO_FILL -> {
                Element noFill = doc.createElementNS(XMLConstants.DRAWING_NS, "a:noFill");
                spPr.appendChild(noFill);
            }
            case GRADIENT -> {
                // Future: gradient stop list + angle
            }
        }
    }

    /**
     * Write line element into spPr.
     */
    static void writeLine(Document doc, Element spPr, ShapeLine line) {
        if (line == null) return;

        Element ln = doc.createElementNS(XMLConstants.DRAWING_NS, "a:ln");
        if (line.getWidthEMU() != null) {
            ln.setAttribute("w", String.valueOf(line.getWidthEMU()));
        }

        if (line.getColor() != null) {
            Element solidFill = doc.createElementNS(XMLConstants.DRAWING_NS, "a:solidFill");
            appendColorElement(doc, solidFill, line.getColor());
            ln.appendChild(solidFill);
        }

        if (line.getDashStyle() != null && !"solid".equals(line.getDashStyle())) {
            Element prstDash = doc.createElementNS(XMLConstants.DRAWING_NS, "a:prstDash");
            prstDash.setAttribute("val", line.getDashStyle());
            ln.appendChild(prstDash);
        }

        spPr.appendChild(ln);
    }

    /**
     * Write the {@code <p:style>} element from a ThemeStyleRef.
     * If ref is null, generates the default PowerPoint insert-shape style.
     */
    static Element writeThemeStyleRef(Document doc, ThemeStyleRef ref, boolean hasText) {
        if (ref == null) {
            ref = ThemeStyleRef.defaultStyle(hasText);
        }

        Element style = doc.createElementNS(XMLConstants.PRESENTATION_NS, "p:style");

        // lnRef
        Element lnRef = doc.createElementNS(XMLConstants.DRAWING_NS, "a:lnRef");
        lnRef.setAttribute("idx", String.valueOf(ref.getLineRefIdx()));
        Element lnClr = doc.createElementNS(XMLConstants.DRAWING_NS, "a:schemeClr");
        lnClr.setAttribute("val", ref.getLineColor());
        if (ref.getLineShadeVal() != null) {
            Element shade = doc.createElementNS(XMLConstants.DRAWING_NS, "a:shade");
            shade.setAttribute("val", ref.getLineShadeVal());
            lnClr.appendChild(shade);
        }
        lnRef.appendChild(lnClr);
        style.appendChild(lnRef);

        // fillRef
        Element fillRef = doc.createElementNS(XMLConstants.DRAWING_NS, "a:fillRef");
        fillRef.setAttribute("idx", String.valueOf(ref.getFillRefIdx()));
        Element fillClr = doc.createElementNS(XMLConstants.DRAWING_NS, "a:schemeClr");
        fillClr.setAttribute("val", ref.getFillColor());
        fillRef.appendChild(fillClr);
        style.appendChild(fillRef);

        // effectRef
        Element effectRef = doc.createElementNS(XMLConstants.DRAWING_NS, "a:effectRef");
        effectRef.setAttribute("idx", String.valueOf(ref.getEffectRefIdx()));
        Element effectClr = doc.createElementNS(XMLConstants.DRAWING_NS, "a:schemeClr");
        effectClr.setAttribute("val", ref.getEffectColor());
        effectRef.appendChild(effectClr);
        style.appendChild(effectRef);

        // fontRef
        Element fontRef = doc.createElementNS(XMLConstants.DRAWING_NS, "a:fontRef");
        fontRef.setAttribute("idx", ref.getFontRefIdx());
        Element fontClr = doc.createElementNS(XMLConstants.DRAWING_NS, "a:schemeClr");
        fontClr.setAttribute("val", ref.getFontColor());
        fontRef.appendChild(fontClr);
        style.appendChild(fontRef);

        return style;
    }

    private static void appendColorElement(Document doc, Element parent, TextColor color) {
        if (color == null) return;
        if (color.isScheme()) {
            Element clr = doc.createElementNS(XMLConstants.DRAWING_NS, "a:schemeClr");
            clr.setAttribute("val", color.getSchemeVal());
            parent.appendChild(clr);
        } else {
            Element clr = doc.createElementNS(XMLConstants.DRAWING_NS, "a:srgbClr");
            clr.setAttribute("val", color.getHexVal());
            parent.appendChild(clr);
        }
    }

    private static Element findChild(Element parent, String tagName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element elem) {
                if (elem.getTagName().equals(tagName) || elem.getLocalName().equals(tagName.substring(tagName.indexOf(':') + 1))) {
                    return elem;
                }
            }
        }
        return null;
    }
}
