package com.excudo.view.rendering.tables;

import com.excudo.core.model.TextColor;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * One parsed {@code a:tblStyle} (ECMA-376 20.1.4.2.26 CT_TableStyle):
 * the nine style parts the A6 renderer consumes, each carrying its
 * text properties, per-edge border {@code a:ln} elements and fill
 * choice element. Parts and their members are null when the style
 * doesn't author them — the renderer's layer overlay treats null as
 * "this layer says nothing about that property".
 *
 * <p>Border slots store the {@code a:ln} element itself (the child of
 * {@code a:left}/{@code a:insideH}/... wrappers), the same shape as a
 * {@code tcPr} {@code a:lnL..lnB} element, so one border resolver
 * serves both style-layer and per-cell borders.
 */
final class TableStyle {

    /** CT_TableStyleTextStyle: bold flag, text color, fontRef idx ("minor"/"major"). */
    record TextProps(Boolean bold, TextColor color, String fontRefIdx) {}

    /** CT_TableCellBorderStyle: the a:ln element per edge, null when unset. */
    record Borders(Element left, Element right, Element top, Element bottom,
                   Element insideH, Element insideV) {
        static final Borders EMPTY = new Borders(null, null, null, null, null, null);
    }

    /** CT_TablePartStyle: tcTxStyle + tcStyle(tcBdr, fill). */
    record Part(TextProps text, Borders borders, Element fill) {}

    private final Part wholeTbl;
    private final Part band1H;
    private final Part band2H;
    private final Part band1V;
    private final Part band2V;
    private final Part firstCol;
    private final Part lastCol;
    private final Part firstRow;
    private final Part lastRow;

    private TableStyle(Part wholeTbl, Part band1H, Part band2H, Part band1V, Part band2V,
                       Part firstCol, Part lastCol, Part firstRow, Part lastRow) {
        this.wholeTbl = wholeTbl;
        this.band1H = band1H;
        this.band2H = band2H;
        this.band1V = band1V;
        this.band2V = band2V;
        this.firstCol = firstCol;
        this.lastCol = lastCol;
        this.firstRow = firstRow;
        this.lastRow = lastRow;
    }

    Part wholeTbl() { return wholeTbl; }
    Part band1H() { return band1H; }
    Part band2H() { return band2H; }
    Part band1V() { return band1V; }
    Part band2V() { return band2V; }
    Part firstCol() { return firstCol; }
    Part lastCol() { return lastCol; }
    Part firstRow() { return firstRow; }
    Part lastRow() { return lastRow; }

    // ====================================================================
    // Parsing
    // ====================================================================

    static TableStyle parse(Element tblStyle) {
        if (tblStyle == null) throw new IllegalArgumentException("null a:tblStyle");
        return new TableStyle(
            parsePart(child(tblStyle, "wholeTbl")),
            parsePart(child(tblStyle, "band1H")),
            parsePart(child(tblStyle, "band2H")),
            parsePart(child(tblStyle, "band1V")),
            parsePart(child(tblStyle, "band2V")),
            parsePart(child(tblStyle, "firstCol")),
            parsePart(child(tblStyle, "lastCol")),
            parsePart(child(tblStyle, "firstRow")),
            parsePart(child(tblStyle, "lastRow")));
    }

    private static Part parsePart(Element partEl) {
        if (partEl == null) return null;

        TextProps text = null;
        Element tcTxStyle = child(partEl, "tcTxStyle");
        if (tcTxStyle != null) {
            // ST_OnOffStyleType: on / off / def (def = inherit = null).
            Boolean bold = switch (tcTxStyle.getAttribute("b")) {
                case "on", "1", "true" -> Boolean.TRUE;
                case "off", "0", "false" -> Boolean.FALSE;
                default -> null;
            };
            String fontRefIdx = null;
            Element fontRef = child(tcTxStyle, "fontRef");
            if (fontRef != null && !fontRef.getAttribute("idx").isEmpty()) {
                fontRefIdx = fontRef.getAttribute("idx");
            }
            // Text color is a DIRECT color-choice child of tcTxStyle --
            // fontRef carries its own nested color that must not leak.
            TextColor color = null;
            Element srgb = child(tcTxStyle, "srgbClr");
            Element scheme = child(tcTxStyle, "schemeClr");
            if (srgb != null && !srgb.getAttribute("val").isEmpty()) {
                color = TextColor.hex(srgb.getAttribute("val"));
            } else if (scheme != null && !scheme.getAttribute("val").isEmpty()) {
                color = TextColor.scheme(scheme.getAttribute("val"));
            }
            text = new TextProps(bold, color, fontRefIdx);
        }

        Borders borders = Borders.EMPTY;
        Element fill = null;
        Element tcStyle = child(partEl, "tcStyle");
        if (tcStyle != null) {
            Element tcBdr = child(tcStyle, "tcBdr");
            if (tcBdr != null) {
                borders = new Borders(
                    lnOf(child(tcBdr, "left")), lnOf(child(tcBdr, "right")),
                    lnOf(child(tcBdr, "top")), lnOf(child(tcBdr, "bottom")),
                    lnOf(child(tcBdr, "insideH")), lnOf(child(tcBdr, "insideV")));
            }
            Element fillEl = child(tcStyle, "fill");
            if (fillEl != null) {
                // CT_FillProperties: the fill choice is fill's only child.
                for (Node n = fillEl.getFirstChild(); n != null; n = n.getNextSibling()) {
                    if (n instanceof Element el) { fill = el; break; }
                }
            }
        }
        return new Part(text, borders, fill);
    }

    private static Element lnOf(Element borderWrapper) {
        return borderWrapper == null ? null : child(borderWrapper, "ln");
    }

    private static Element child(Element parent, String local) {
        if (parent == null) return null;
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element el) {
                String name = el.getLocalName();
                if (name == null) {
                    String tag = el.getTagName();
                    int colon = tag.indexOf(':');
                    name = colon < 0 ? tag : tag.substring(colon + 1);
                }
                if (local.equals(name)) return el;
            }
        }
        return null;
    }
}
