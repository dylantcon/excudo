package com.excudo.view.rendering.tables;

import com.excudo.view.rendering.SlideRenderContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves an {@code a:tableStyleId} GUID to a {@link TableStyle}:
 *
 * <ol>
 *   <li>The deck's own {@code ppt/tableStyles.xml} part, when it defines
 *       an {@code a:tblStyle} with that styleId (custom styles).</li>
 *   <li>The built-in registry below. PowerPoint resolves built-in GUIDs
 *       from its internal style library — decks reference them without
 *       embedding a definition (the parity corpus decks carry an EMPTY
 *       tblStyleLst whose {@code def} is the GUID below).</li>
 *   <li>null — PowerPoint's "No Style, No Grid": no fills, no borders,
 *       no text overrides; only explicit tcPr formatting paints.</li>
 * </ol>
 *
 * <p>The built-in Medium Style 2 - Accent 1 definition is authored from
 * the tables-basic/tables-merges truth-PDF calibration (2026-07-25, PDF
 * content streams at 960x540pt):
 * <ul>
 *   <li>fills: firstRow/lastRow solid accent1 (PDF 0.31 0.506 0.741 rg =
 *       #4F81BD on the corpus theme), band1H/band1V accent1 tint 40000
 *       (0.816 0.847 0.91 = #D0D8E8), wholeTbl accent1 tint 20000
 *       (0.914 0.929 0.957 = #E9EDF4) — both tints reproduce the PDF
 *       bytes exactly under ColorTransforms' linear-sRGB tint;</li>
 *   <li>borders: every edge lt1 at 12700 EMU (PDF '1 w', white), the
 *       firstRow bottom / lastRow top at 38100 EMU (PDF '3 w');</li>
 *   <li>text: firstRow/lastRow bold lt1 (PDF BCDEEE+Calibri-Bold, '1 g'),
 *       wholeTbl dk1 ('0 g'), minor font ref.</li>
 * </ul>
 */
final class TableStyleResolver {

    private TableStyleResolver() {}

    static final String MEDIUM2_ACCENT1_GUID = "{5C22544A-7EE6-4342-B048-85BDC9FD1C3A}";

    private static final String A_NS =
        "http://schemas.openxmlformats.org/drawingml/2006/main";

    private static final String LT1_1PT_LN =
        "<a:ln w=\"12700\" cmpd=\"sng\"><a:solidFill><a:schemeClr val=\"lt1\"/></a:solidFill></a:ln>";
    private static final String LT1_3PT_LN =
        "<a:ln w=\"38100\" cmpd=\"sng\"><a:solidFill><a:schemeClr val=\"lt1\"/></a:solidFill></a:ln>";

    private static final String MEDIUM2_ACCENT1_XML =
        "<a:tblStyle xmlns:a=\"" + A_NS + "\" styleId=\"" + MEDIUM2_ACCENT1_GUID + "\""
        + " styleName=\"Medium Style 2 - Accent 1\">"
        + "<a:wholeTbl>"
        +   "<a:tcTxStyle><a:fontRef idx=\"minor\"><a:prstClr val=\"black\"/></a:fontRef>"
        +     "<a:schemeClr val=\"dk1\"/></a:tcTxStyle>"
        +   "<a:tcStyle>"
        +     "<a:tcBdr>"
        +       "<a:left>" + LT1_1PT_LN + "</a:left>"
        +       "<a:right>" + LT1_1PT_LN + "</a:right>"
        +       "<a:top>" + LT1_1PT_LN + "</a:top>"
        +       "<a:bottom>" + LT1_1PT_LN + "</a:bottom>"
        +       "<a:insideH>" + LT1_1PT_LN + "</a:insideH>"
        +       "<a:insideV>" + LT1_1PT_LN + "</a:insideV>"
        +     "</a:tcBdr>"
        +     "<a:fill><a:solidFill><a:schemeClr val=\"accent1\"><a:tint val=\"20000\"/>"
        +       "</a:schemeClr></a:solidFill></a:fill>"
        +   "</a:tcStyle>"
        + "</a:wholeTbl>"
        + "<a:band1H><a:tcStyle><a:tcBdr/>"
        +   "<a:fill><a:solidFill><a:schemeClr val=\"accent1\"><a:tint val=\"40000\"/>"
        +     "</a:schemeClr></a:solidFill></a:fill>"
        + "</a:tcStyle></a:band1H>"
        + "<a:band2H><a:tcStyle><a:tcBdr/></a:tcStyle></a:band2H>"
        + "<a:band1V><a:tcStyle><a:tcBdr/>"
        +   "<a:fill><a:solidFill><a:schemeClr val=\"accent1\"><a:tint val=\"40000\"/>"
        +     "</a:schemeClr></a:solidFill></a:fill>"
        + "</a:tcStyle></a:band1V>"
        + "<a:band2V><a:tcStyle><a:tcBdr/></a:tcStyle></a:band2V>"
        + "<a:lastCol><a:tcTxStyle b=\"on\"/><a:tcStyle><a:tcBdr/></a:tcStyle></a:lastCol>"
        + "<a:firstCol><a:tcTxStyle b=\"on\"/><a:tcStyle><a:tcBdr/></a:tcStyle></a:firstCol>"
        + "<a:lastRow>"
        +   "<a:tcTxStyle b=\"on\"><a:fontRef idx=\"minor\"><a:prstClr val=\"black\"/></a:fontRef>"
        +     "<a:schemeClr val=\"lt1\"/></a:tcTxStyle>"
        +   "<a:tcStyle><a:tcBdr><a:top>" + LT1_3PT_LN + "</a:top></a:tcBdr>"
        +     "<a:fill><a:solidFill><a:schemeClr val=\"accent1\"/></a:solidFill></a:fill>"
        +   "</a:tcStyle>"
        + "</a:lastRow>"
        + "<a:firstRow>"
        +   "<a:tcTxStyle b=\"on\"><a:fontRef idx=\"minor\"><a:prstClr val=\"black\"/></a:fontRef>"
        +     "<a:schemeClr val=\"lt1\"/></a:tcTxStyle>"
        +   "<a:tcStyle><a:tcBdr><a:bottom>" + LT1_3PT_LN + "</a:bottom></a:tcBdr>"
        +     "<a:fill><a:solidFill><a:schemeClr val=\"accent1\"/></a:solidFill></a:fill>"
        +   "</a:tcStyle>"
        + "</a:firstRow>"
        + "</a:tblStyle>";

    private static final Map<String, String> BUILT_IN_XML =
        Map.of(MEDIUM2_ACCENT1_GUID, MEDIUM2_ACCENT1_XML);

    private static final Map<String, TableStyle> BUILT_IN_CACHE = new ConcurrentHashMap<>();

    /**
     * Resolve a styleId to a parsed style, or null for the documented
     * No Style, No Grid fallback (unknown GUID with no deck definition).
     */
    static TableStyle resolve(String styleId, SlideRenderContext slideCtx) {
        if (styleId == null || styleId.isEmpty()) return null;

        // Deck-defined custom styles win over the built-in library.
        if (slideCtx != null && slideCtx.getDocument() != null) {
            Document part = slideCtx.getDocument().getXmlPart("ppt/tableStyles.xml");
            if (part != null) {
                NodeList styles = part.getElementsByTagNameNS(A_NS, "tblStyle");
                for (int i = 0; i < styles.getLength(); i++) {
                    Element style = (Element) styles.item(i);
                    if (styleId.equals(style.getAttribute("styleId"))) {
                        return TableStyle.parse(style);
                    }
                }
            }
        }

        String builtIn = BUILT_IN_XML.get(styleId);
        if (builtIn == null) return null;
        return BUILT_IN_CACHE.computeIfAbsent(styleId,
            id -> TableStyle.parse(parseXml(builtIn)));
    }

    private static Element parseXml(String xml) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            return f.newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
                .getDocumentElement();
        } catch (Exception e) {
            throw new IllegalStateException("built-in table style failed to parse", e);
        }
    }
}
