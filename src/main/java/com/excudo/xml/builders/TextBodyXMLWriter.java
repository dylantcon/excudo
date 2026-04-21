package com.excudo.xml.builders;

import com.excudo.core.model.*;
import com.excudo.core.utils.Logger;
import com.excudo.core.utils.ComponentLogger;
import com.excudo.core.utils.XMLConstants;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Converts TextBody model objects into OOXML DOM elements (p:txBody).
 * Produces identical output to the legacy TextFormatUtils for backward compatibility.
 */
public final class TextBodyXMLWriter {

    private static final ComponentLogger logger = Logger.xml();
    private static final String NS = XMLConstants.DRAWING_NS;

    private TextBodyXMLWriter() {}

    /**
     * Writes a TextBody model to a DOM element containing the paragraph children.
     * Does NOT create the p:txBody wrapper, a:bodyPr, or a:lstStyle -- those are
     * managed by the caller. This method appends a:p elements to the given parent.
     *
     * @param doc The XML document for element creation
     * @param parent The element to append paragraphs to (typically p:txBody)
     * @param body The text body model
     */
    public static void writeParagraphs(Document doc, Element parent, TextBody body) {
        for (TextParagraph para : body.getParagraphs()) {
            Element p = writeParagraph(doc, para);
            parent.appendChild(p);
        }
    }

    /**
     * Creates a complete p:txBody element from a TextBody model.
     * Includes a:bodyPr, a:lstStyle, and all a:p elements.
     *
     * @param doc The XML document for element creation
     * @param body The text body model
     * @return The complete p:txBody element
     */
    public static Element write(Document doc, TextBody body) {
        Element txBody = doc.createElementNS(XMLConstants.PRESENTATION_NS, "p:txBody");

        Element bodyPr = doc.createElementNS(NS, "a:bodyPr");
        writeBodyProperties(bodyPr, body.getBodyProperties());
        txBody.appendChild(bodyPr);

        Element lstStyle = doc.createElementNS(NS, "a:lstStyle");
        txBody.appendChild(lstStyle);

        writeParagraphs(doc, txBody, body);

        return txBody;
    }

    private static Element writeParagraph(Document doc, TextParagraph para) {
        Element p = doc.createElementNS(NS, "a:p");

        boolean needsPPr = hasParagraphProperties(para);
        if (needsPPr) {
            Element pPr = doc.createElementNS(NS, "a:pPr");
            writeParagraphProperties(doc, pPr, para);
            p.appendChild(pPr);
        }

        if (para.isEmpty()) {
            Element endParaRPr = doc.createElementNS(NS, "a:endParaRPr");
            endParaRPr.setAttribute("lang", "en-US");
            endParaRPr.setAttribute("dirty", "0");
            p.appendChild(endParaRPr);
        } else {
            for (TextRun run : para.getRuns()) {
                if (!run.getText().isEmpty()) {
                    Element r = writeRun(doc, run);
                    p.appendChild(r);
                }
            }
        }

        return p;
    }

    private static boolean hasParagraphProperties(TextParagraph para) {
        return para.getLevel() > 0
                || para.getMarginLeft() != null
                || para.getMarginRight() != null
                || para.getIndent() != null
                || para.getAlignment() != null
                || para.getBulletType() == BulletType.CHARACTER
                || para.getBulletType() == BulletType.AUTONUMBER
                || para.getLineSpacing() != null
                || para.getSpaceBefore() != null
                || para.getSpaceAfter() != null;
    }

    private static void writeParagraphProperties(Document doc, Element pPr, TextParagraph para) {
        if (para.getLevel() > 0) {
            pPr.setAttribute("lvl", String.valueOf(para.getLevel()));
        }
        if (para.getMarginLeft() != null) {
            pPr.setAttribute("marL", String.valueOf(para.getMarginLeft()));
        }
        if (para.getMarginRight() != null) {
            pPr.setAttribute("marR", String.valueOf(para.getMarginRight()));
        }
        if (para.getIndent() != null) {
            pPr.setAttribute("indent", String.valueOf(para.getIndent()));
        }
        if (para.getAlignment() != null) {
            pPr.setAttribute("algn", para.getAlignment());
        }

        if (para.getLineSpacing() != null) {
            Element lnSpc = doc.createElementNS(NS, "a:lnSpc");
            Element spcPct = doc.createElementNS(NS, "a:spcPct");
            spcPct.setAttribute("val", String.valueOf(para.getLineSpacing()));
            lnSpc.appendChild(spcPct);
            pPr.appendChild(lnSpc);
        }
        if (para.getSpaceBefore() != null) {
            Element spcBef = doc.createElementNS(NS, "a:spcBef");
            Element spcPts = doc.createElementNS(NS, "a:spcPts");
            spcPts.setAttribute("val", String.valueOf(para.getSpaceBefore()));
            spcBef.appendChild(spcPts);
            pPr.appendChild(spcBef);
        }

        if (para.getSpaceAfter() != null) {
            Element spcAft = doc.createElementNS(NS, "a:spcAft");
            Element spcPts = doc.createElementNS(NS, "a:spcPts");
            spcPts.setAttribute("val", String.valueOf(para.getSpaceAfter()));
            spcAft.appendChild(spcPts);
            pPr.appendChild(spcAft);
        }

        // Bullet properties -- must come after spacing, before run content
        if (para.getBulletType() == BulletType.CHARACTER) {
            if (para.getBulletFont() == null) {
                // Null font means "inherit from text run" -- emit a:buFontTx (inherit marker)
                pPr.appendChild(doc.createElementNS(NS, "a:buFontTx"));
            } else {
                Element buFont = doc.createElementNS(NS, "a:buFont");
                buFont.setAttribute("typeface", para.getBulletFont());
                if (para.getBulletFontPanose() != null) {
                    buFont.setAttribute("panose", para.getBulletFontPanose());
                }
                if (para.getBulletFontPitchFamily() != null) {
                    buFont.setAttribute("pitchFamily", para.getBulletFontPitchFamily());
                }
                if (para.getBulletFontCharset() != null) {
                    buFont.setAttribute("charset", para.getBulletFontCharset());
                }
                pPr.appendChild(buFont);
            }

            Element buChar = doc.createElementNS(NS, "a:buChar");
            buChar.setAttribute("char", para.getBulletChar());
            pPr.appendChild(buChar);
        } else if (para.getBulletType() == BulletType.AUTONUMBER) {
            Element buAutoNum = doc.createElementNS(NS, "a:buAutoNum");
            buAutoNum.setAttribute("type", para.getAutonumType());
            pPr.appendChild(buAutoNum);
        }
    }

    private static Element writeRun(Document doc, TextRun run) {
        Element r = doc.createElementNS(NS, "a:r");

        Element rPr = doc.createElementNS(NS, "a:rPr");
        rPr.setAttribute("lang", run.getLanguage() != null ? run.getLanguage() : "en-US");

        if (run.getFontSize() != null) {
            rPr.setAttribute("sz", String.valueOf(run.getFontSize()));
        }
        if (run.getBold() != null && run.getBold()) {
            rPr.setAttribute("b", "1");
        }
        if (run.getItalic() != null && run.getItalic()) {
            rPr.setAttribute("i", "1");
        }
        if (run.getUnderline() != null) {
            rPr.setAttribute("u", run.getUnderline());
        }
        if (run.getCapitalization() != null) {
            rPr.setAttribute("cap", run.getCapitalization());
        }
        if (run.getBaseline() != null) {
            rPr.setAttribute("baseline", String.valueOf(run.getBaseline()));
        }
        if (run.getCharacterSpacing() != null) {
            rPr.setAttribute("spc", String.valueOf(run.getCharacterSpacing()));
        }

        // Color fill
        if (run.getColor() != null) {
            Element solidFill = doc.createElementNS(NS, "a:solidFill");
            if (run.getColor().isScheme()) {
                Element schemeClr = doc.createElementNS(NS, "a:schemeClr");
                schemeClr.setAttribute("val", run.getColor().getSchemeVal());
                solidFill.appendChild(schemeClr);
            } else {
                Element srgbClr = doc.createElementNS(NS, "a:srgbClr");
                srgbClr.setAttribute("val", run.getColor().getHexVal());
                solidFill.appendChild(srgbClr);
            }
            rPr.appendChild(solidFill);
        }

        // Highlight
        if (run.getHighlight() != null) {
            Element highlight = doc.createElementNS(NS, "a:highlight");
            if (run.getHighlight().isScheme()) {
                Element schemeClr = doc.createElementNS(NS, "a:schemeClr");
                schemeClr.setAttribute("val", run.getHighlight().getSchemeVal());
                highlight.appendChild(schemeClr);
            } else {
                Element srgbClr = doc.createElementNS(NS, "a:srgbClr");
                srgbClr.setAttribute("val", run.getHighlight().getHexVal());
                highlight.appendChild(srgbClr);
            }
            rPr.appendChild(highlight);
        }

        // Font family
        if (run.getFontFamily() != null) {
            Element latin = doc.createElementNS(NS, "a:latin");
            latin.setAttribute("typeface", run.getFontFamily());
            rPr.appendChild(latin);
        }

        r.appendChild(rPr);

        Element t = doc.createElementNS(NS, "a:t");
        t.setTextContent(run.getText());
        r.appendChild(t);

        return r;
    }

    private static void writeBodyProperties(Element bodyPr, BodyProperties props) {
        if (props == null) return;

        if (props.getVerticalAlignment() != null) {
            bodyPr.setAttribute("anchor", props.getVerticalAlignment());
        }
        if (props.getWrap() != null) {
            bodyPr.setAttribute("wrap", props.getWrap());
        }
        if (props.getVerticalText() != null) {
            bodyPr.setAttribute("vert", props.getVerticalText());
        }
        if (props.getLeftInset() != null) {
            bodyPr.setAttribute("lIns", String.valueOf(props.getLeftInset()));
        }
        if (props.getTopInset() != null) {
            bodyPr.setAttribute("tIns", String.valueOf(props.getTopInset()));
        }
        if (props.getRightInset() != null) {
            bodyPr.setAttribute("rIns", String.valueOf(props.getRightInset()));
        }
        if (props.getBottomInset() != null) {
            bodyPr.setAttribute("bIns", String.valueOf(props.getBottomInset()));
        }
        if (props.getNumColumns() != null) {
            bodyPr.setAttribute("numCol", String.valueOf(props.getNumColumns()));
        }
        if (props.isRtlCol()) {
            bodyPr.setAttribute("rtlCol", "1");
        }

        // Autofit child elements
        if (props.getAutofit() != null) {
            Document doc = bodyPr.getOwnerDocument();
            switch (props.getAutofit()) {
                case NORMAL:
                    Element normAutofit = doc.createElementNS(NS, "a:normAutofit");
                    if (props.getFontScale() != null) {
                        normAutofit.setAttribute("fontScale", String.valueOf(props.getFontScale()));
                    }
                    bodyPr.appendChild(normAutofit);
                    break;
                case SHAPE:
                    bodyPr.appendChild(doc.createElementNS(NS, "a:spAutoFit"));
                    break;
                case NONE:
                    bodyPr.appendChild(doc.createElementNS(NS, "a:noAutofit"));
                    break;
            }
        }
    }
}
