package com.excudo.core.model;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * A slide's title, read live from its title placeholder -- the same source
 * PowerPoint uses for the outline and slide sorter. Derived on demand, never
 * stored, so it always reflects the current text.
 */
public final class SlideTitles {

    private static final String P_NS = "http://schemas.openxmlformats.org/presentationml/2006/main";
    private static final String A_NS = "http://schemas.openxmlformats.org/drawingml/2006/main";

    private SlideTitles() {}

    /** Title text of slide {@code n}, or "Slide n" when it has none. */
    public static String titleOf(PPTXDocument doc, int slideNumber) {
        String title = doc == null ? null : extract(doc.getSlideDocument(slideNumber));
        return title != null ? title : "Slide " + slideNumber;
    }

    /**
     * Text of the slide's title / ctrTitle placeholder (paragraphs joined by
     * a space), or {@code null} if there is no such placeholder or it's empty.
     */
    public static String extract(Document slideDoc) {
        if (slideDoc == null) return null;
        NodeList phs = slideDoc.getElementsByTagNameNS(P_NS, "ph");
        for (int i = 0; i < phs.getLength(); i++) {
            Element ph = (Element) phs.item(i);
            String type = ph.getAttribute("type");
            if (!"title".equals(type) && !"ctrTitle".equals(type)) continue;
            Element shape = enclosingShape(ph);
            if (shape == null) continue;
            String text = paragraphText(shape);
            if (!text.isBlank()) return text.strip();
        }
        return null;
    }

    private static Element enclosingShape(Element ph) {
        for (Node n = ph.getParentNode(); n != null; n = n.getParentNode()) {
            if (n instanceof Element e && P_NS.equals(e.getNamespaceURI()) && "sp".equals(e.getLocalName())) {
                return e;
            }
        }
        return null;
    }

    private static String paragraphText(Element shape) {
        StringBuilder out = new StringBuilder();
        NodeList paragraphs = shape.getElementsByTagNameNS(A_NS, "p");
        for (int i = 0; i < paragraphs.getLength(); i++) {
            Element p = (Element) paragraphs.item(i);
            StringBuilder para = new StringBuilder();
            NodeList runs = p.getElementsByTagNameNS(A_NS, "t");
            for (int j = 0; j < runs.getLength(); j++) para.append(runs.item(j).getTextContent());
            if (para.length() == 0) continue;
            if (out.length() > 0) out.append(' ');
            out.append(para);
        }
        return out.toString();
    }
}
