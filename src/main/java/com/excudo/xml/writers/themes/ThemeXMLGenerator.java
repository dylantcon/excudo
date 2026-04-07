package com.excudo.xml.writers.themes;

import com.excudo.core.themes.LayoutDefinition;
import com.excudo.core.themes.PlaceholderDefinition;
import com.excudo.core.themes.TextLevelStyle;
import com.excudo.core.themes.ThemeDefinition;
import com.excudo.core.utils.XMLConstants;
import com.excudo.core.utils.XMLFactoryProvider;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.Map;

/**
 * Generates all XML files for a ThemeDefinition.
 * Produces theme XML, slide master XML, slide layout XML, and relationship documents.
 */
public final class ThemeXMLGenerator {

    private static final String P = XMLConstants.PRESENTATION_NS;
    private static final String A = XMLConstants.DRAWING_NS;
    private static final String R = XMLConstants.RELATIONSHIPS_NS;
    private static final String PKG_REL = XMLConstants.PACKAGE_RELATIONSHIPS_NS;
    private static final String XMLNS = "http://www.w3.org/2000/xmlns/";

    public ThemeXMLGenerator() {}

    /**
     * Generate theme XML (a:theme with clrScheme, fontScheme, fmtScheme).
     */
    public Document generateThemeXML(ThemeDefinition theme) {
        Document doc = XMLFactoryProvider.createDocument();

        Element themeEl = doc.createElementNS(A, "a:theme");
        themeEl.setAttributeNS(XMLNS, "xmlns:a", A);
        themeEl.setAttribute("name", theme.getDisplayName());
        doc.appendChild(themeEl);

        Element themeElements = doc.createElementNS(A, "a:themeElements");
        themeEl.appendChild(themeElements);

        themeElements.appendChild(createClrScheme(doc, theme));
        themeElements.appendChild(createFontScheme(doc, theme));
        themeElements.appendChild(FmtSchemeTemplates.createFmtScheme(doc, "Office"));

        Element objDefaults = doc.createElementNS(A, "a:objectDefaults");
        populateObjectDefaults(doc, objDefaults);
        themeEl.appendChild(objDefaults);

        Element extraClrLst = doc.createElementNS(A, "a:extraClrSchemeLst");
        themeEl.appendChild(extraClrLst);

        return doc;
    }

    /**
     * Generate slide master XML with clrMap, txStyles, placeholder shapes, and background.
     */
    public Document generateSlideMasterXML(ThemeDefinition theme) {
        Document doc = XMLFactoryProvider.createDocument();

        Element sldMaster = doc.createElementNS(P, "p:sldMaster");
        sldMaster.setAttributeNS(XMLNS, "xmlns:a", A);
        sldMaster.setAttributeNS(XMLNS, "xmlns:r", R);
        sldMaster.setAttributeNS(XMLNS, "xmlns:p", P);
        doc.appendChild(sldMaster);

        // p:cSld with shape tree containing title + body placeholders
        Element cSld = doc.createElementNS(P, "p:cSld");
        sldMaster.appendChild(cSld);

        Element bg = createBackground(doc, theme);
        cSld.appendChild(bg);

        Element spTree = doc.createElementNS(P, "p:spTree");
        cSld.appendChild(spTree);

        // Group shape properties (required)
        appendGroupShapeProperties(doc, spTree);

        // Title placeholder shape (SPID 2)
        spTree.appendChild(createPlaceholderShape(doc, 2, "Title Placeholder", "title", null,
                838200L, 365125L, 10515600L, 1325563L));

        // Body placeholder shape (SPID 3)
        spTree.appendChild(createPlaceholderShape(doc, 3, "Text Placeholder", "body", 1,
                838200L, 1825625L, 10515600L, 4351338L));

        // p:clrMap -- dark themes flip bg/tx so dk1 becomes background, lt1 becomes text
        Element clrMap = doc.createElementNS(P, "p:clrMap");
        if (theme.isDarkBackground()) {
            clrMap.setAttribute("bg1", "dk1");
            clrMap.setAttribute("tx1", "lt1");
            clrMap.setAttribute("bg2", "dk2");
            clrMap.setAttribute("tx2", "lt2");
        } else {
            clrMap.setAttribute("bg1", "lt1");
            clrMap.setAttribute("tx1", "dk1");
            clrMap.setAttribute("bg2", "lt2");
            clrMap.setAttribute("tx2", "dk2");
        }
        clrMap.setAttribute("accent1", "accent1");
        clrMap.setAttribute("accent2", "accent2");
        clrMap.setAttribute("accent3", "accent3");
        clrMap.setAttribute("accent4", "accent4");
        clrMap.setAttribute("accent5", "accent5");
        clrMap.setAttribute("accent6", "accent6");
        clrMap.setAttribute("hlink", "hlink");
        clrMap.setAttribute("folHlink", "folHlink");
        sldMaster.appendChild(clrMap);

        // p:sldLayoutIdLst -- must contain an entry per layout matching the rels rIds
        Element sldLayoutIdLst = doc.createElementNS(P, "p:sldLayoutIdLst");
        long layoutIdBase = 2147483649L;
        for (int i = 0; i < theme.getLayouts().size(); i++) {
            Element sldLayoutId = doc.createElementNS(P, "p:sldLayoutId");
            sldLayoutId.setAttribute("id", String.valueOf(layoutIdBase + i));
            sldLayoutId.setAttributeNS(R, "r:id", "rId" + (i + 1));
            sldLayoutIdLst.appendChild(sldLayoutId);
        }
        sldMaster.appendChild(sldLayoutIdLst);

        // p:txStyles
        Element txStyles = doc.createElementNS(P, "p:txStyles");
        txStyles.appendChild(createTextStyle(doc, "p:titleStyle", theme.getTitleStyle(), theme));
        txStyles.appendChild(createTextStyle(doc, "p:bodyStyle", theme.getBodyStyle(), theme));
        txStyles.appendChild(createTextStyle(doc, "p:otherStyle", theme.getOtherStyle(), theme));
        sldMaster.appendChild(txStyles);

        return doc;
    }

    /**
     * Generate slide layout XML for a specific layout definition.
     */
    public Document generateSlideLayoutXML(ThemeDefinition theme, LayoutDefinition layout) {
        Document doc = XMLFactoryProvider.createDocument();

        Element sldLayout = doc.createElementNS(P, "p:sldLayout");
        sldLayout.setAttributeNS(XMLNS, "xmlns:a", A);
        sldLayout.setAttributeNS(XMLNS, "xmlns:r", R);
        sldLayout.setAttributeNS(XMLNS, "xmlns:p", P);
        sldLayout.setAttribute("matchingName", layout.getMatchingName());
        if (layout.isPreserve()) {
            sldLayout.setAttribute("preserve", "1");
        }
        // Type attribute for well-known layout types
        String typeAttr = getLayoutTypeAttribute(layout.getLayoutType());
        if (typeAttr != null) {
            sldLayout.setAttribute("type", typeAttr);
        }
        doc.appendChild(sldLayout);

        Element cSld = doc.createElementNS(P, "p:cSld");
        cSld.setAttribute("name", layout.getMatchingName());
        sldLayout.appendChild(cSld);

        Element spTree = doc.createElementNS(P, "p:spTree");
        cSld.appendChild(spTree);

        appendGroupShapeProperties(doc, spTree);

        // Add layout placeholder shapes with geometry from the layout definition
        int spidCounter = 2;
        for (PlaceholderDefinition ph : layout.getPlaceholders()) {
            String shapeName = getShapeName(ph.getType(), spidCounter);
            spTree.appendChild(createLayoutPlaceholderShape(doc, spidCounter, shapeName,
                    ph.getType(), ph.getIdx(), ph.getX(), ph.getY(), ph.getCx(), ph.getCy()));
            spidCounter++;
        }

        // p:clrMapOvr with masterClrMapping (inherit from master)
        Element clrMapOvr = doc.createElementNS(P, "p:clrMapOvr");
        Element masterMapping = doc.createElementNS(A, "a:masterClrMapping");
        clrMapOvr.appendChild(masterMapping);
        sldLayout.appendChild(clrMapOvr);

        return doc;
    }

    /**
     * Generate slide master .rels document with relationships to theme and all layouts.
     */
    public Document generateSlideMasterRels(ThemeDefinition theme, int masterNumber) {
        Document doc = XMLFactoryProvider.createDocument();

        Element rels = doc.createElementNS(PKG_REL, "Relationships");
        doc.appendChild(rels);

        int rIdCounter = 1;
        for (int i = 0; i < theme.getLayouts().size(); i++) {
            Element rel = doc.createElementNS(PKG_REL, "Relationship");
            rel.setAttribute("Id", "rId" + rIdCounter);
            rel.setAttribute("Type", XMLConstants.RELATIONSHIP_TYPE_SLIDE_LAYOUT);
            rel.setAttribute("Target", "../slideLayouts/slideLayout" + (i + 1) + ".xml");
            rels.appendChild(rel);
            rIdCounter++;
        }

        // Theme relationship (last rId)
        Element themeRel = doc.createElementNS(PKG_REL, "Relationship");
        themeRel.setAttribute("Id", "rId" + rIdCounter);
        themeRel.setAttribute("Type", XMLConstants.RELATIONSHIP_TYPE_THEME);
        themeRel.setAttribute("Target", "../theme/theme" + masterNumber + ".xml");
        rels.appendChild(themeRel);

        return doc;
    }

    /**
     * Generate slide layout .rels document with relationship back to slide master.
     */
    public Document generateSlideLayoutRels(int masterNumber) {
        Document doc = XMLFactoryProvider.createDocument();

        Element rels = doc.createElementNS(PKG_REL, "Relationships");
        doc.appendChild(rels);

        Element rel = doc.createElementNS(PKG_REL, "Relationship");
        rel.setAttribute("Id", "rId1");
        rel.setAttribute("Type", XMLConstants.RELATIONSHIP_TYPE_SLIDE_MASTER);
        rel.setAttribute("Target", "../slideMasters/slideMaster" + masterNumber + ".xml");
        rels.appendChild(rel);

        return doc;
    }

    // --- Private helpers ---

    private Element createClrScheme(Document doc, ThemeDefinition theme) {
        Element clrScheme = doc.createElementNS(A, "a:clrScheme");
        clrScheme.setAttribute("name", theme.getDisplayName());

        String[] colorNames = {"dk1", "lt1", "dk2", "lt2",
                "accent1", "accent2", "accent3", "accent4", "accent5", "accent6",
                "hlink", "folHlink"};

        for (String name : colorNames) {
            Element colorEl = doc.createElementNS(A, "a:" + name);
            Element srgbClr = doc.createElementNS(A, "a:srgbClr");
            srgbClr.setAttribute("val", theme.getColor(name));
            colorEl.appendChild(srgbClr);
            clrScheme.appendChild(colorEl);
        }

        return clrScheme;
    }

    private Element createFontScheme(Document doc, ThemeDefinition theme) {
        Element fontScheme = doc.createElementNS(A, "a:fontScheme");
        fontScheme.setAttribute("name", theme.getDisplayName());

        fontScheme.appendChild(createFontEntry(doc, "a:majorFont", theme.getMajorFont(), theme.getMajorFontFallback()));
        fontScheme.appendChild(createFontEntry(doc, "a:minorFont", theme.getMinorFont(), theme.getMinorFontFallback()));

        return fontScheme;
    }

    private Element createFontEntry(Document doc, String elementName, String typeface, String fallbackFont) {
        Element entry = doc.createElementNS(A, elementName);

        Element latin = doc.createElementNS(A, "a:latin");
        latin.setAttribute("typeface", typeface);
        entry.appendChild(latin);

        Element ea = doc.createElementNS(A, "a:ea");
        ea.setAttribute("typeface", fallbackFont != null ? fallbackFont : "");
        entry.appendChild(ea);

        Element cs = doc.createElementNS(A, "a:cs");
        cs.setAttribute("typeface", fallbackFont != null ? fallbackFont : "");
        entry.appendChild(cs);

        return entry;
    }

    private Element createTextStyle(Document doc, String styleName, TextLevelStyle[] levels, ThemeDefinition theme) {
        Element style = doc.createElementNS(P, styleName);
        boolean isTitleStyle = "p:titleStyle".equals(styleName);

        // lvl1pPr through lvl9pPr (no defPPr -- PowerPoint removes it)
        for (int i = 0; i < 9; i++) {
            TextLevelStyle level = (levels != null && i < levels.length) ? levels[i] : getDefaultLevel(i);
            Element lvlPPr = doc.createElementNS(A, "a:lvl" + (i + 1) + "pPr");
            lvlPPr.setAttribute("marL", String.valueOf(level.getMarginLeft()));
            lvlPPr.setAttribute("indent", String.valueOf(level.getIndent()));
            lvlPPr.setAttribute("algn", level.getAlignment());
            lvlPPr.setAttribute("defTabSz", "914400");
            lvlPPr.setAttribute("rtl", "0");
            lvlPPr.setAttribute("eaLnBrk", "1");
            lvlPPr.setAttribute("latinLnBrk", "0");
            lvlPPr.setAttribute("hangingPunct", "1");

            appendSpacing(doc, lvlPPr, level.getLineSpacing(), level.getSpaceBefore(), 0);

            // Bullet properties
            if (level.hasBullet()) {
                if (level.getBulletFont() != null) {
                    Element buFont = doc.createElementNS(A, "a:buFont");
                    buFont.setAttribute("typeface", level.getBulletFont());
                    buFont.setAttribute("panose", "020B0604020202020204");
                    buFont.setAttribute("pitchFamily", "34");
                    buFont.setAttribute("charset", "0");
                    lvlPPr.appendChild(buFont);
                }

                Element buChar = doc.createElementNS(A, "a:buChar");
                buChar.setAttribute("char", level.getBulletChar());
                lvlPPr.appendChild(buChar);
            } else {
                Element buNone = doc.createElementNS(A, "a:buNone");
                lvlPPr.appendChild(buNone);
            }

            // Default run properties
            Element defRPr = doc.createElementNS(A, "a:defRPr");
            defRPr.setAttribute("sz", String.valueOf(level.getFontSize()));
            defRPr.setAttribute("kern", "1200");
            if (level.isBold()) {
                defRPr.setAttribute("b", "1");
            }

            Element fill = doc.createElementNS(A, "a:solidFill");
            fill.appendChild(createColorElement(doc, level));
            defRPr.appendChild(fill);

            // Title uses major font (+mj-lt), body/other use minor font (+mn-lt)
            Element latin = doc.createElementNS(A, "a:latin");
            latin.setAttribute("typeface", isTitleStyle ? "+mj-lt" : "+mn-lt");
            defRPr.appendChild(latin);

            Element ea = doc.createElementNS(A, "a:ea");
            ea.setAttribute("typeface", isTitleStyle ? "+mj-ea" : "+mn-ea");
            defRPr.appendChild(ea);

            Element csPr = doc.createElementNS(A, "a:cs");
            csPr.setAttribute("typeface", isTitleStyle ? "+mj-cs" : "+mn-cs");
            defRPr.appendChild(csPr);

            lvlPPr.appendChild(defRPr);
            style.appendChild(lvlPPr);
        }

        return style;
    }

    private Element createColorElement(Document doc, TextLevelStyle level) {
        if (level.isSchemeColor()) {
            Element schemeClr = doc.createElementNS(A, "a:schemeClr");
            schemeClr.setAttribute("val", level.getColorRef());
            return schemeClr;
        } else {
            Element srgbClr = doc.createElementNS(A, "a:srgbClr");
            srgbClr.setAttribute("val", level.getHexColor());
            return srgbClr;
        }
    }

    private void appendSpacing(Document doc, Element parent, int lineSpacing, int spaceBefore, int spaceAfter) {
        Element lnSpc = doc.createElementNS(A, "a:lnSpc");
        Element lnSpcPct = doc.createElementNS(A, "a:spcPct");
        lnSpcPct.setAttribute("val", String.valueOf(lineSpacing));
        lnSpc.appendChild(lnSpcPct);
        parent.appendChild(lnSpc);

        Element spcBef = doc.createElementNS(A, "a:spcBef");
        Element spcBefPts = doc.createElementNS(A, "a:spcPts");
        spcBefPts.setAttribute("val", String.valueOf(spaceBefore));
        spcBef.appendChild(spcBefPts);
        parent.appendChild(spcBef);

        Element spcAft = doc.createElementNS(A, "a:spcAft");
        Element spcAftPts = doc.createElementNS(A, "a:spcPts");
        spcAftPts.setAttribute("val", String.valueOf(spaceAfter));
        spcAft.appendChild(spcAftPts);
        parent.appendChild(spcAft);
    }

    private Element createPlaceholderShape(Document doc, int spid, String name,
                                            String phType, Integer phIdx,
                                            long x, long y, long cx, long cy) {
        Element sp = doc.createElementNS(P, "p:sp");

        // nvSpPr
        Element nvSpPr = doc.createElementNS(P, "p:nvSpPr");
        Element cNvPr = doc.createElementNS(P, "p:cNvPr");
        cNvPr.setAttribute("id", String.valueOf(spid));
        cNvPr.setAttribute("name", name);
        nvSpPr.appendChild(cNvPr);

        Element cNvSpPr = doc.createElementNS(P, "p:cNvSpPr");
        Element spLocks = doc.createElementNS(A, "a:spLocks");
        spLocks.setAttribute("noGrp", "1");
        cNvSpPr.appendChild(spLocks);
        nvSpPr.appendChild(cNvSpPr);

        Element nvPr = doc.createElementNS(P, "p:nvPr");
        Element ph = doc.createElementNS(P, "p:ph");
        if (phType != null) {
            ph.setAttribute("type", phType);
        }
        if (phIdx != null) {
            ph.setAttribute("idx", String.valueOf(phIdx));
        }
        nvPr.appendChild(ph);
        nvSpPr.appendChild(nvPr);
        sp.appendChild(nvSpPr);

        // spPr with geometry
        Element spPr = doc.createElementNS(P, "p:spPr");
        Element xfrm = doc.createElementNS(A, "a:xfrm");
        Element off = doc.createElementNS(A, "a:off");
        off.setAttribute("x", String.valueOf(x));
        off.setAttribute("y", String.valueOf(y));
        xfrm.appendChild(off);
        Element ext = doc.createElementNS(A, "a:ext");
        ext.setAttribute("cx", String.valueOf(cx));
        ext.setAttribute("cy", String.valueOf(cy));
        xfrm.appendChild(ext);
        spPr.appendChild(xfrm);

        // PowerPoint requires a:prstGeom on slide master placeholder shapes
        Element prstGeom = doc.createElementNS(A, "a:prstGeom");
        prstGeom.setAttribute("prst", "rect");
        Element avLst = doc.createElementNS(A, "a:avLst");
        prstGeom.appendChild(avLst);
        spPr.appendChild(prstGeom);
        sp.appendChild(spPr);

        // txBody with complete bodyPr
        Element txBody = doc.createElementNS(P, "p:txBody");
        Element bodyPr = doc.createElementNS(A, "a:bodyPr");
        boolean isTitle = "title".equals(phType) || "ctrTitle".equals(phType);
        bodyPr.setAttribute("vert", "horz");
        bodyPr.setAttribute("lIns", "91440");
        bodyPr.setAttribute("tIns", "45720");
        bodyPr.setAttribute("rIns", "91440");
        bodyPr.setAttribute("bIns", "45720");
        bodyPr.setAttribute("rtlCol", "0");
        if (isTitle) {
            bodyPr.setAttribute("anchor", "ctr");
        } else {
            bodyPr.setAttribute("anchor", "t");
        }
        Element autofit = doc.createElementNS(A, "a:normAutofit");
        bodyPr.appendChild(autofit);
        txBody.appendChild(bodyPr);

        Element lstStyle = doc.createElementNS(A, "a:lstStyle");
        txBody.appendChild(lstStyle);
        Element p = doc.createElementNS(A, "a:p");
        Element endParaRPr = doc.createElementNS(A, "a:endParaRPr");
        endParaRPr.setAttribute("lang", "en-US");
        p.appendChild(endParaRPr);
        txBody.appendChild(p);
        sp.appendChild(txBody);

        return sp;
    }

    /**
     * Create a layout placeholder shape with explicit geometry.
     * Each layout defines unique placeholder positions so slides are visually distinct.
     */
    private Element createLayoutPlaceholderShape(Document doc, int spid, String name,
                                                  String phType, Integer phIdx,
                                                  long x, long y, long cx, long cy) {
        Element sp = doc.createElementNS(P, "p:sp");

        // nvSpPr
        Element nvSpPr = doc.createElementNS(P, "p:nvSpPr");
        Element cNvPr = doc.createElementNS(P, "p:cNvPr");
        cNvPr.setAttribute("id", String.valueOf(spid));
        cNvPr.setAttribute("name", name);
        nvSpPr.appendChild(cNvPr);

        Element cNvSpPr = doc.createElementNS(P, "p:cNvSpPr");
        Element spLocks = doc.createElementNS(A, "a:spLocks");
        spLocks.setAttribute("noGrp", "1");
        cNvSpPr.appendChild(spLocks);
        nvSpPr.appendChild(cNvSpPr);

        Element nvPr = doc.createElementNS(P, "p:nvPr");
        Element ph = doc.createElementNS(P, "p:ph");
        if (phType != null) {
            ph.setAttribute("type", phType);
        }
        if (phIdx != null) {
            ph.setAttribute("idx", String.valueOf(phIdx));
        }
        nvPr.appendChild(ph);
        nvSpPr.appendChild(nvPr);
        sp.appendChild(nvSpPr);

        // spPr with geometry
        Element spPr = doc.createElementNS(P, "p:spPr");
        Element xfrm = doc.createElementNS(A, "a:xfrm");
        Element off = doc.createElementNS(A, "a:off");
        off.setAttribute("x", String.valueOf(x));
        off.setAttribute("y", String.valueOf(y));
        xfrm.appendChild(off);
        Element ext = doc.createElementNS(A, "a:ext");
        ext.setAttribute("cx", String.valueOf(cx));
        ext.setAttribute("cy", String.valueOf(cy));
        xfrm.appendChild(ext);
        spPr.appendChild(xfrm);

        Element prstGeom = doc.createElementNS(A, "a:prstGeom");
        prstGeom.setAttribute("prst", "rect");
        Element avLst = doc.createElementNS(A, "a:avLst");
        prstGeom.appendChild(avLst);
        spPr.appendChild(prstGeom);
        sp.appendChild(spPr);

        // Minimal txBody
        Element txBody = doc.createElementNS(P, "p:txBody");
        Element bodyPr = doc.createElementNS(A, "a:bodyPr");
        txBody.appendChild(bodyPr);
        Element lstStyle = doc.createElementNS(A, "a:lstStyle");
        txBody.appendChild(lstStyle);
        Element p = doc.createElementNS(A, "a:p");
        Element endParaRPr = doc.createElementNS(A, "a:endParaRPr");
        endParaRPr.setAttribute("lang", "en-US");
        p.appendChild(endParaRPr);
        txBody.appendChild(p);
        sp.appendChild(txBody);

        return sp;
    }

    private Element createBackground(Document doc, ThemeDefinition theme) {
        Element bg = doc.createElementNS(P, "p:bg");
        Element bgRef = doc.createElementNS(P, "p:bgRef");
        bgRef.setAttribute("idx", String.valueOf(1000 + theme.getBackgroundFillIndex()));
        Element schemeClr = doc.createElementNS(A, "a:schemeClr");
        schemeClr.setAttribute("val", "bg1");
        bgRef.appendChild(schemeClr);
        bg.appendChild(bgRef);
        return bg;
    }

    private void appendGroupShapeProperties(Document doc, Element spTree) {
        Element nvGrpSpPr = doc.createElementNS(P, "p:nvGrpSpPr");
        Element cNvPr = doc.createElementNS(P, "p:cNvPr");
        cNvPr.setAttribute("id", "1");
        cNvPr.setAttribute("name", "");
        nvGrpSpPr.appendChild(cNvPr);
        Element cNvGrpSpPr = doc.createElementNS(P, "p:cNvGrpSpPr");
        nvGrpSpPr.appendChild(cNvGrpSpPr);
        Element nvPr = doc.createElementNS(P, "p:nvPr");
        nvGrpSpPr.appendChild(nvPr);
        spTree.appendChild(nvGrpSpPr);

        Element grpSpPr = doc.createElementNS(P, "p:grpSpPr");
        Element xfrm = doc.createElementNS(A, "a:xfrm");
        Element off = doc.createElementNS(A, "a:off");
        off.setAttribute("x", "0");
        off.setAttribute("y", "0");
        xfrm.appendChild(off);
        Element ext = doc.createElementNS(A, "a:ext");
        ext.setAttribute("cx", "0");
        ext.setAttribute("cy", "0");
        xfrm.appendChild(ext);
        Element chOff = doc.createElementNS(A, "a:chOff");
        chOff.setAttribute("x", "0");
        chOff.setAttribute("y", "0");
        xfrm.appendChild(chOff);
        Element chExt = doc.createElementNS(A, "a:chExt");
        chExt.setAttribute("cx", "0");
        chExt.setAttribute("cy", "0");
        xfrm.appendChild(chExt);
        grpSpPr.appendChild(xfrm);
        spTree.appendChild(grpSpPr);
    }

    private TextLevelStyle getDefaultLevel(int level) {
        int fontSize = Math.max(1200, 1800 - (level * 100));
        return TextLevelStyle.builder(level)
                .fontSize(fontSize)
                .colorRef("tx1")
                .noBullet()
                .build();
    }

    private String getLayoutTypeAttribute(LayoutDefinition.LayoutType type) {
        switch (type) {
            case TITLE_SLIDE: return "title";
            case TITLE_CONTENT: return "obj";
            case SECTION_HEADER: return "secHead";
            case TWO_CONTENT: return "twoObj";
            case COMPARISON: return "twoTxTwoObj";
            case TITLE_ONLY: return "titleOnly";
            case BLANK: return "blank";
            case CONTENT_CAPTION: return "objTx";
            case PICTURE_CAPTION: return "picTx";
            case TITLE_VERTICAL: return "vertTitleAndTx";
            default: return null;
        }
    }

    /**
     * Populate a:objectDefaults with spDef and lnDef so non-placeholder shapes
     * inherit theme-consistent styling by default.
     */
    private void populateObjectDefaults(Document doc, Element objDefaults) {
        // a:spDef -- default shape definition
        Element spDef = doc.createElementNS(A, "a:spDef");
        objDefaults.appendChild(spDef);

        Element spPr = doc.createElementNS(A, "a:spPr");
        spDef.appendChild(spPr);

        Element bodyPr = doc.createElementNS(A, "a:bodyPr");
        bodyPr.setAttribute("rtlCol", "0");
        bodyPr.setAttribute("anchor", "ctr");
        spDef.appendChild(bodyPr);

        Element lstStyle = doc.createElementNS(A, "a:lstStyle");
        spDef.appendChild(lstStyle);

        Element style = doc.createElementNS(A, "a:style");
        spDef.appendChild(style);

        appendObjDefaultStyleRef(doc, style, "lnRef", "2", "accent1");
        appendObjDefaultStyleRef(doc, style, "fillRef", "1", "accent1");
        appendObjDefaultStyleRef(doc, style, "effectRef", "0", "accent1");

        Element fontRef = doc.createElementNS(A, "a:fontRef");
        fontRef.setAttribute("idx", "minor");
        Element fontClr = doc.createElementNS(A, "a:schemeClr");
        fontClr.setAttribute("val", "tx1");
        fontRef.appendChild(fontClr);
        style.appendChild(fontRef);

        // a:lnDef -- default line definition
        Element lnDef = doc.createElementNS(A, "a:lnDef");
        objDefaults.appendChild(lnDef);

        Element lnSpPr = doc.createElementNS(A, "a:spPr");
        lnDef.appendChild(lnSpPr);
        Element lnBodyPr = doc.createElementNS(A, "a:bodyPr");
        lnDef.appendChild(lnBodyPr);
        Element lnLstStyle = doc.createElementNS(A, "a:lstStyle");
        lnDef.appendChild(lnLstStyle);

        Element lnStyle = doc.createElementNS(A, "a:style");
        lnDef.appendChild(lnStyle);
        appendObjDefaultStyleRef(doc, lnStyle, "lnRef", "2", "accent1");
        appendObjDefaultStyleRef(doc, lnStyle, "fillRef", "0", "accent1");
        appendObjDefaultStyleRef(doc, lnStyle, "effectRef", "0", "accent1");
        Element lnFontRef = doc.createElementNS(A, "a:fontRef");
        lnFontRef.setAttribute("idx", "minor");
        Element lnFontClr = doc.createElementNS(A, "a:schemeClr");
        lnFontClr.setAttribute("val", "tx1");
        lnFontRef.appendChild(lnFontClr);
        lnStyle.appendChild(lnFontRef);
    }

    private void appendObjDefaultStyleRef(Document doc, Element parent, String refName,
                                          String idx, String schemeColorVal) {
        Element ref = doc.createElementNS(A, "a:" + refName);
        ref.setAttribute("idx", idx);
        Element clr = doc.createElementNS(A, "a:schemeClr");
        clr.setAttribute("val", schemeColorVal);
        ref.appendChild(clr);
        parent.appendChild(ref);
    }

    private String getShapeName(String phType, int spid) {
        switch (phType) {
            case "title": return "Title " + spid;
            case "ctrTitle": return "Title " + spid;
            case "subTitle": return "Subtitle " + spid;
            case "body": return "Content Placeholder " + spid;
            case "dt": return "Date Placeholder " + spid;
            case "ftr": return "Footer Placeholder " + spid;
            case "sldNum": return "Slide Number Placeholder " + spid;
            default: return "Placeholder " + spid;
        }
    }
}
