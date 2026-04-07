package com.excudo.core.metrics;

import com.excudo.core.model.*;
import com.excudo.core.utils.XMLConstants;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Extracts a TextBody model from a DOM p:txBody element.
 * Inverse of TextBodyXMLWriter: parse structure matches write structure.
 */
public final class TextBodyExtractor {

    private static final String NS_A = XMLConstants.DRAWING_NS;

    private TextBodyExtractor() {}

    /**
     * Extract a TextBody from a p:txBody DOM element.
     *
     * @param txBody the p:txBody element
     * @return the extracted TextBody model
     */
    public static TextBody extract(Element txBody) {
        if (txBody == null) return null;

        TextBody.Builder builder = TextBody.builder();

        // Parse a:bodyPr
        Element bodyPr = getFirstChild(txBody, "a:bodyPr");
        if (bodyPr != null) {
            builder.bodyProperties(extractBodyProperties(bodyPr));
        }

        // Parse a:p elements
        NodeList children = txBody.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element el && matchesName(el, "a:p")) {
                builder.addParagraph(extractParagraph(el));
            }
        }

        return builder.build();
    }

    /**
     * Extract a TextBody from a SlideShape's XML element.
     * Finds the p:txBody child within the shape element (p:sp, p:cxnSp, etc).
     */
    public static TextBody extractFromShape(Element shapeElement) {
        if (shapeElement == null) return null;
        // Try both prefixed and unprefixed name
        Element txBody = getFirstChild(shapeElement, "p:txBody");
        if (txBody == null) txBody = getFirstChild(shapeElement, "txBody");
        if (txBody == null) return null;
        return extract(txBody);
    }

    private static BodyProperties extractBodyProperties(Element bodyPr) {
        BodyProperties.Builder builder = BodyProperties.builder();

        String anchor = bodyPr.getAttribute("anchor");
        if (!anchor.isEmpty()) builder.verticalAlignment(anchor);

        String wrap = bodyPr.getAttribute("wrap");
        if (!wrap.isEmpty()) builder.wrap(wrap);

        String vert = bodyPr.getAttribute("vert");
        if (!vert.isEmpty()) builder.verticalText(vert);

        String lIns = bodyPr.getAttribute("lIns");
        if (!lIns.isEmpty()) builder.leftInset(Integer.parseInt(lIns));

        String tIns = bodyPr.getAttribute("tIns");
        if (!tIns.isEmpty()) builder.topInset(Integer.parseInt(tIns));

        String rIns = bodyPr.getAttribute("rIns");
        if (!rIns.isEmpty()) builder.rightInset(Integer.parseInt(rIns));

        String bIns = bodyPr.getAttribute("bIns");
        if (!bIns.isEmpty()) builder.bottomInset(Integer.parseInt(bIns));

        String numCol = bodyPr.getAttribute("numCol");
        if (!numCol.isEmpty()) builder.numColumns(Integer.parseInt(numCol));

        String rtlCol = bodyPr.getAttribute("rtlCol");
        if ("1".equals(rtlCol)) builder.rtlCol(true);

        // Autofit child elements
        if (getFirstChild(bodyPr, "a:normAutofit") != null) {
            builder.autofit(AutofitType.NORMAL);
            Element normAutofit = getFirstChild(bodyPr, "a:normAutofit");
            String fontScale = normAutofit.getAttribute("fontScale");
            if (!fontScale.isEmpty()) builder.fontScale(Integer.parseInt(fontScale));
        } else if (getFirstChild(bodyPr, "a:spAutoFit") != null) {
            builder.autofit(AutofitType.SHAPE);
        } else if (getFirstChild(bodyPr, "a:noAutofit") != null) {
            builder.autofit(AutofitType.NONE);
        }

        return builder.build();
    }

    private static TextParagraph extractParagraph(Element p) {
        TextParagraph.Builder builder = TextParagraph.builder();

        // Parse a:pPr
        Element pPr = getFirstChild(p, "a:pPr");
        if (pPr != null) {
            extractParagraphProperties(pPr, builder);
        }

        // Parse a:r elements
        NodeList children = p.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element el && matchesName(el, "a:r")) {
                TextRun run = extractRun(el);
                if (run != null) builder.addRun(run);
            }
        }

        return builder.build();
    }

    private static void extractParagraphProperties(Element pPr, TextParagraph.Builder builder) {
        String lvl = pPr.getAttribute("lvl");
        if (!lvl.isEmpty()) builder.level(Integer.parseInt(lvl));

        String marL = pPr.getAttribute("marL");
        if (!marL.isEmpty()) builder.marginLeft(Integer.parseInt(marL));

        String indent = pPr.getAttribute("indent");
        if (!indent.isEmpty()) builder.indent(Integer.parseInt(indent));

        String algn = pPr.getAttribute("algn");
        if (!algn.isEmpty()) builder.alignment(algn);

        // Line spacing
        Element lnSpc = getFirstChild(pPr, "a:lnSpc");
        if (lnSpc != null) {
            Element spcPct = getFirstChild(lnSpc, "a:spcPct");
            if (spcPct != null) {
                String val = spcPct.getAttribute("val");
                if (!val.isEmpty()) builder.lineSpacing(Integer.parseInt(val));
            }
        }

        // Space before
        Element spcBef = getFirstChild(pPr, "a:spcBef");
        if (spcBef != null) {
            Element spcPts = getFirstChild(spcBef, "a:spcPts");
            if (spcPts != null) {
                String val = spcPts.getAttribute("val");
                if (!val.isEmpty()) builder.spaceBefore(Integer.parseInt(val));
            }
        }

        // Space after
        Element spcAft = getFirstChild(pPr, "a:spcAft");
        if (spcAft != null) {
            Element spcPts = getFirstChild(spcAft, "a:spcPts");
            if (spcPts != null) {
                String val = spcPts.getAttribute("val");
                if (!val.isEmpty()) builder.spaceAfter(Integer.parseInt(val));
            }
        }

        // Bullet: character
        Element buChar = getFirstChild(pPr, "a:buChar");
        if (buChar != null) {
            String ch = buChar.getAttribute("char");
            Element buFont = getFirstChild(pPr, "a:buFont");
            if (buFont != null) {
                String typeface = buFont.getAttribute("typeface");
                String panose = buFont.getAttribute("panose");
                String pitchFamily = buFont.getAttribute("pitchFamily");
                String charset = buFont.getAttribute("charset");
                builder.characterBullet(ch,
                        typeface.isEmpty() ? null : typeface,
                        panose.isEmpty() ? null : panose,
                        pitchFamily.isEmpty() ? null : pitchFamily,
                        charset.isEmpty() ? null : charset);
            } else {
                builder.bulletType(BulletType.CHARACTER);
                builder.bulletChar(ch);
            }
        }

        // Bullet: autonumber
        Element buAutoNum = getFirstChild(pPr, "a:buAutoNum");
        if (buAutoNum != null) {
            builder.autonumber(buAutoNum.getAttribute("type"));
        }

        // Bullet: explicit none
        Element buNone = getFirstChild(pPr, "a:buNone");

        // If no bullet element at all (no buChar, no buAutoNum, no buNone),
        // default to INHERITED -- the paragraph inherits bullets from master/layout lstStyle
        if (buChar == null && buAutoNum == null && buNone == null) {
            builder.bulletType(com.excudo.core.model.BulletType.INHERITED);
        }
    }

    private static TextRun extractRun(Element r) {
        Element t = getFirstChild(r, "a:t");
        if (t == null) return null;
        String text = t.getTextContent();

        TextRun.Builder builder = TextRun.builder(text);

        Element rPr = getFirstChild(r, "a:rPr");
        if (rPr != null) {
            String lang = rPr.getAttribute("lang");
            if (!lang.isEmpty()) builder.language(lang);

            String sz = rPr.getAttribute("sz");
            if (!sz.isEmpty()) builder.fontSize(Integer.parseInt(sz));

            String b = rPr.getAttribute("b");
            if ("1".equals(b)) builder.bold(true);

            String italic = rPr.getAttribute("i");
            if ("1".equals(italic)) builder.italic(true);

            String u = rPr.getAttribute("u");
            if (!u.isEmpty()) builder.underline(u);

            String strike = rPr.getAttribute("strike");
            if (!strike.isEmpty() && !"noStrike".equals(strike)) builder.strikethrough(strike);

            // Color: solidFill
            Element solidFill = getFirstChild(rPr, "a:solidFill");
            if (solidFill != null) {
                Element schemeClr = getFirstChild(solidFill, "a:schemeClr");
                if (schemeClr != null) {
                    builder.schemeColor(schemeClr.getAttribute("val"));
                } else {
                    Element srgbClr = getFirstChild(solidFill, "a:srgbClr");
                    if (srgbClr != null) {
                        builder.hexColor(srgbClr.getAttribute("val"));
                    }
                }
            }

            // Font family: a:latin
            Element latin = getFirstChild(rPr, "a:latin");
            if (latin != null) {
                String typeface = latin.getAttribute("typeface");
                if (!typeface.isEmpty()) builder.fontFamily(typeface);
            }
        }

        return builder.build();
    }

    private static boolean matchesName(Element el, String qualifiedName) {
        String localPart = qualifiedName.contains(":")
            ? qualifiedName.substring(qualifiedName.indexOf(':') + 1)
            : qualifiedName;
        return localPart.equals(el.getLocalName()) || qualifiedName.equals(el.getNodeName());
    }

    private static Element getFirstChild(Element parent, String qualifiedName) {
        // Strip prefix for localName comparison (e.g. "a:bodyPr" -> "bodyPr")
        String localPart = qualifiedName.contains(":")
            ? qualifiedName.substring(qualifiedName.indexOf(':') + 1)
            : qualifiedName;

        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element el) {
                String elLocal = el.getLocalName();
                String elName = el.getNodeName();
                if (localPart.equals(elLocal) || qualifiedName.equals(elName)
                        || qualifiedName.equals(elLocal)) {
                    return el;
                }
            }
        }
        return null;
    }
}
