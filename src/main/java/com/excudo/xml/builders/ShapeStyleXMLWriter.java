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

        // Find spPr to inject fill/line. Per ECMA-376 §20.1.8.x, the EG_FillProperties
        // choice under spPr is single-valued -- one fill child max. Likewise EG_LineProperties
        // permits one a:ln. Before this clear, applyStyle simply appended new children, so
        // a set-style call layered the new color over whatever the shape was created with;
        // the OLDER fill (first child) won at every read, and set-style returned OK while
        // the visual stayed unchanged. The 2026-04-22 beta logs documented this as silent-accept
        // on color overrides.
        Element spPr = findChild(shapeElem, "p:spPr");
        if (spPr != null) {
            if (style.getFill() != null) {
                removeFillChildren(spPr);
            }
            if (style.getLine() != null) {
                removeChildrenByTagName(spPr, "a:ln");
            }
            writeFill(doc, spPr, style.getFill());
            writeLine(doc, spPr, style.getLine());
        }

        // Replace any pre-existing p:style siblings (idempotent re-style). Without this,
        // every set-style call accumulated another <p:style> with a fillRef/lnRef,
        // bloating the part and producing inconsistent renders downstream when the
        // multiple-children resolver picks a stale ref.
        removeChildrenByTagName(shapeElem, "p:style");

        // Create and insert p:style after spPr, before txBody.
        // Skip entirely when:
        //   - ThemeStyleRef.NONE is set (text box pattern: no p:style)
        //   - explicit fill is set (the spPr fill is the user's intent; competing fillRef
        //     from a default theme ref would be redundant at best, conflicting at worst).
        ThemeStyleRef effectiveTheme = style.getThemeStyle();
        boolean explicitColorOverride = style.getFill() != null || style.getLine() != null;
        if (effectiveTheme != ThemeStyleRef.NONE && !explicitColorOverride) {
            Element styleElem = writeThemeStyleRef(doc, effectiveTheme, hasText);
            Element txBody = findChild(shapeElem, "p:txBody");
            if (txBody != null) {
                shapeElem.insertBefore(styleElem, txBody);
            } else {
                shapeElem.appendChild(styleElem);
            }
        }
    }

    /** Remove any direct-child fill element ({@code a:solidFill / a:gradFill /
     *  a:noFill / a:blipFill / a:pattFill / a:grpFill}). */
    private static void removeFillChildren(Element parent) {
        for (String tag : new String[] {
                "a:solidFill", "a:gradFill", "a:noFill", "a:blipFill", "a:pattFill", "a:grpFill"}) {
            removeChildrenByTagName(parent, tag);
        }
    }

    /** Remove every direct-child element matching the given prefixed tag name. */
    private static void removeChildrenByTagName(Element parent, String tagName) {
        NodeList kids = parent.getChildNodes();
        java.util.List<Element> toRemove = new java.util.ArrayList<>();
        for (int i = 0; i < kids.getLength(); i++) {
            if (kids.item(i) instanceof Element el) {
                if (el.getTagName().equals(tagName)) {
                    toRemove.add(el);
                }
            }
        }
        for (Element el : toRemove) parent.removeChild(el);
    }

    /**
     * Write fill element into spPr.
     */
    static void writeFill(Document doc, Element spPr, ShapeFill fill) {
        if (fill == null) return;

        switch (fill.getType()) {
            case SOLID -> {
                Element solidFill = doc.createElementNS(XMLConstants.DRAWING_NS, "a:solidFill");
                appendColorElement(doc, solidFill, fill.getColor(), fill.getAlphaPercent());
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
            appendColorElement(doc, solidFill, line.getColor(), line.getAlphaPercent());
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
        appendColorElement(doc, parent, color, null);
    }

    /**
     * Append the color element ({@code a:srgbClr} or {@code a:schemeClr}),
     * optionally with an {@code a:alpha} child carrying the supplied opacity.
     *
     * <p>OOXML's {@code a:alpha/@val} uses positive-fixed-percent encoding:
     * an integer 0..100000 representing 0%..100%. Caller passes percent
     * 0..100; this multiplies by 1000 internally. Values outside the range
     * are clamped silently rather than failing -- alpha overflow on render
     * is not load-bearing.
     */
    private static void appendColorElement(Document doc, Element parent, TextColor color,
                                           Integer alphaPercent) {
        if (color == null) return;
        Element clr;
        if (color.isScheme()) {
            clr = doc.createElementNS(XMLConstants.DRAWING_NS, "a:schemeClr");
            clr.setAttribute("val", color.getSchemeVal());
        } else {
            clr = doc.createElementNS(XMLConstants.DRAWING_NS, "a:srgbClr");
            clr.setAttribute("val", color.getHexVal());
        }
        if (alphaPercent != null) {
            int clamped = Math.max(0, Math.min(100, alphaPercent));
            Element alpha = doc.createElementNS(XMLConstants.DRAWING_NS, "a:alpha");
            alpha.setAttribute("val", String.valueOf(clamped * 1000));
            clr.appendChild(alpha);
        }
        parent.appendChild(clr);
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
