package com.excudo.core.introspection;

import com.excudo.core.model.FillType;
import com.excudo.core.model.PPTXDocument;
import com.excudo.core.model.ParsedSlideData;
import com.excudo.core.model.ShapeFill;
import com.excudo.core.model.ShapeLine;
import com.excudo.core.model.ShapeRegistry;
import com.excudo.core.model.ShapeStyle;
import com.excudo.core.model.SlideShape;
import com.excudo.core.model.TextColor;
import com.excudo.core.model.ThemeStyleRef;
import com.excudo.core.orchestration.PPTXOrchestrator;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Reads the typed {@link ShapeStyle} of a shape by inverse-parsing the
 * DOM produced by {@code ShapeStyleXMLWriter}. One-place-to-update
 * symmetry with the writer: every field the writer can emit is a field
 * the reader can recognize.
 *
 * <p>Scope is intentionally narrow -- this is <em>the shape's own
 * declared style</em>, not the theme-resolved effective appearance.
 * The synthesizer uses {@link #read(int, int)} to diff the current
 * shape's style against its baseline (layout default or theme default);
 * what the pixels actually end up looking like after theme resolution
 * is the renderer's concern, not the synthesizer's.
 *
 * <p>Returns {@code null} when the shape cannot be found on the slide,
 * or when parsing the shape DOM fails -- the synthesizer treats null
 * as "baseline unresolvable" rather than fabricating defaults.
 */
public final class ShapeStyleReader {

    private static final String PML_NS = com.excudo.core.utils.XMLConstants.Namespaces.PML;
    private static final String DML_NS = com.excudo.core.utils.XMLConstants.Namespaces.DML;

    private final PPTXOrchestrator orchestrator;

    public ShapeStyleReader(PPTXOrchestrator orchestrator) {
        if (orchestrator == null) {
            throw new IllegalArgumentException("orchestrator must not be null");
        }
        this.orchestrator = orchestrator;
    }

    /**
     * Read the style of a shape identified by (slide, SPID). Returns
     * null if the shape cannot be located.
     */
    public ShapeStyle read(int slideNumber, int spid) {
        var ctxOpt = orchestrator.getContext();
        if (ctxOpt.isEmpty()) return null;
        PPTXDocument doc = ctxOpt.get().getDocument();
        if (doc == null) return null;

        ParsedSlideData parsed = doc.getParsedSlideData(slideNumber,
            (dom, n) -> new com.excudo.xml.parsers.SlideXMLParser().parseSlide(dom, n));
        if (parsed == null) return null;

        ShapeRegistry registry = parsed.getShapeRegistry();
        if (registry == null) return null;
        SlideShape shape = registry.getShape(spid);
        if (shape == null) return null;
        Element spEl = shape.getXmlElement();
        if (spEl == null) return null;

        ShapeFill fill = readFill(spEl);
        ShapeLine line = readLine(spEl);
        ThemeStyleRef themeRef = readThemeStyleRef(spEl, shape.isTextBox());

        return ShapeStyle.of(fill, line, themeRef);
    }

    // ========== Fill ==========

    /**
     * Return the shape's direct-fill override, or null if none is
     * declared on {@code spPr}. Null means "inherit from theme" in the
     * ShapeStyle model, matching how {@link com.excudo.xml.builders.ShapeStyleXMLWriter}
     * emits nothing for a null fill.
     */
    private ShapeFill readFill(Element spEl) {
        Element spPr = firstDmlChild(spEl, "spPr", PML_NS);
        if (spPr == null) return null;

        Element solidFill = firstDmlChild(spPr, "solidFill", DML_NS);
        if (solidFill != null) {
            TextColor c = readColor(solidFill);
            if (c == null) return null;
            Integer alpha = readAlphaPercent(solidFill);
            return alpha != null ? ShapeFill.solid(c).withAlphaPercent(alpha) : ShapeFill.solid(c);
        }
        if (firstDmlChild(spPr, "noFill", DML_NS) != null) {
            return ShapeFill.noFill();
        }
        // gradFill / blipFill / pattFill: present but not modeled in
        // ShapeFill v1. Return null here; the synthesizer skips the
        // fill spec rather than fabricating a SOLID from a gradient.
        return null;
    }

    // ========== Line ==========

    private ShapeLine readLine(Element spEl) {
        Element spPr = firstDmlChild(spEl, "spPr", PML_NS);
        if (spPr == null) return null;
        Element ln = firstDmlChild(spPr, "ln", DML_NS);
        if (ln == null) return null;

        Integer widthEMU = parseIntOrNull(ln.getAttribute("w"));
        Element solidFill = firstDmlChild(ln, "solidFill", DML_NS);
        TextColor color = solidFill != null ? readColor(solidFill) : null;

        Element prstDash = firstDmlChild(ln, "prstDash", DML_NS);
        String dash = prstDash != null && prstDash.hasAttribute("val")
            ? prstDash.getAttribute("val")
            : "solid"; // absence of prstDash == solid per OOXML

        // The writer requires width + color to emit a "solid"-style
        // line; when either is missing we still return the partial
        // information we can extract. ShapeLine.of accepts the same
        // shape of input regardless of completeness.
        int w = widthEMU != null ? widthEMU : 0;
        return ShapeLine.of(w, color, dash);
    }

    // ========== ThemeStyleRef ==========

    private ThemeStyleRef readThemeStyleRef(Element spEl, boolean isTextBox) {
        Element styleEl = firstDmlChild(spEl, "style", PML_NS);
        if (styleEl == null) {
            // A shape with NO p:style element. Two cases:
            //  - Text box: writer deliberately skips p:style (NONE sentinel).
            //  - Anything else: inherit default on re-write (null).
            return isTextBox ? ThemeStyleRef.NONE : null;
        }

        Element lnRef = firstDmlChild(styleEl, "lnRef", DML_NS);
        Element fillRef = firstDmlChild(styleEl, "fillRef", DML_NS);
        Element effectRef = firstDmlChild(styleEl, "effectRef", DML_NS);
        Element fontRef = firstDmlChild(styleEl, "fontRef", DML_NS);

        int lnIdx = refIdx(lnRef);
        int fillIdx = refIdx(fillRef);
        int effectIdx = refIdx(effectRef);
        String fontIdxStr = fontRef != null && fontRef.hasAttribute("idx")
            ? fontRef.getAttribute("idx") : "minor";

        String lnColor = refSchemeVal(lnRef);
        String fillColor = refSchemeVal(fillRef);
        String effectColor = refSchemeVal(effectRef);
        String fontColor = refSchemeVal(fontRef);

        return ThemeStyleRef.of(lnIdx, lnColor,
            fillIdx, fillColor,
            effectIdx, effectColor,
            fontIdxStr, fontColor);
    }

    private int refIdx(Element ref) {
        if (ref == null) return 0;
        Integer i = parseIntOrNull(ref.getAttribute("idx"));
        return i != null ? i : 0;
    }

    private String refSchemeVal(Element ref) {
        if (ref == null) return "";
        Element clr = firstDmlChild(ref, "schemeClr", DML_NS);
        return clr != null ? clr.getAttribute("val") : "";
    }

    // ========== Color ==========

    /**
     * Read the first color child of a container (solidFill, etc.).
     * Supports schemeClr and srgbClr. Returns null if neither is
     * present (e.g., an empty solidFill -- malformed, treat as absent).
     */
    private TextColor readColor(Element container) {
        Element schemeClr = firstDmlChild(container, "schemeClr", DML_NS);
        if (schemeClr != null && schemeClr.hasAttribute("val")) {
            return TextColor.scheme(schemeClr.getAttribute("val"));
        }
        Element srgbClr = firstDmlChild(container, "srgbClr", DML_NS);
        if (srgbClr != null && srgbClr.hasAttribute("val")) {
            return TextColor.hex(srgbClr.getAttribute("val"));
        }
        return null;
    }

    /**
     * Read an {@code a:alpha} opacity off the fill's color element, decoding
     * OOXML's positive-fixed-percent ({@code val = percent * 1000}) back to a
     * 0-100 percent. Returns null when no alpha child is present (fully
     * opaque) -- mirrors {@code ShapeStyleXMLWriter.appendColorElement}, which
     * emits {@code a:alpha} only when alphaPercent is non-null. Without this,
     * fill opacity is silently dropped at introspection time so it never
     * reaches the diff, the synthesized spec, or JSON.
     */
    private Integer readAlphaPercent(Element solidFill) {
        Element clr = firstDmlChild(solidFill, "schemeClr", DML_NS);
        if (clr == null) clr = firstDmlChild(solidFill, "srgbClr", DML_NS);
        if (clr == null) return null;
        Element alpha = firstDmlChild(clr, "alpha", DML_NS);
        if (alpha == null || !alpha.hasAttribute("val")) return null;
        Integer raw = parseIntOrNull(alpha.getAttribute("val"));
        return raw != null ? raw / 1000 : null;
    }

    // ========== Helpers ==========

    private static Element firstDmlChild(Element parent, String localName, String ns) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() != Node.ELEMENT_NODE) continue;
            Element e = (Element) n;
            if (!localName.equals(e.getLocalName())) continue;
            if (ns != null && !ns.equals(e.getNamespaceURI())) continue;
            return e;
        }
        return null;
    }

    private static Integer parseIntOrNull(String s) {
        if (s == null || s.isEmpty()) return null;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return null; }
    }

    // Compile-time sanity check that FillType references don't fall out from under us.
    @SuppressWarnings("unused")
    private static final FillType FILL_TYPE_REFERENCE = FillType.SOLID;
}
