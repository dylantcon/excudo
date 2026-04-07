package com.excudo.core.utils;

import org.w3c.dom.*;

import java.io.*;
import java.util.*;

/**
 * Custom OOXML serializer that outputs DOM element attributes in the canonical
 * order defined by the ECMA-376 XSD schemas. Java's built-in Transformer always
 * alphabetizes attributes, which breaks PowerPoint's expectation of XSD schema
 * declaration order.
 *
 * The attribute order map was generated from pml.xsd (PresentationML) and
 * dml-main.xsd (DrawingML) of the ECMA-376 5th edition specification,
 * with attributeGroup references expanded in-place.
 *
 * Usage: call {@link #serialize(Document, File)} instead of Transformer.transform().
 */
public final class OOXMLAttributeOrder {

    private OOXMLAttributeOrder() {}

    private static final Map<String, List<String>> ATTR_ORDER = new HashMap<>();

    static {
        // OPC relationship element -- PowerPoint requires Id, Type, Target order
        ATTR_ORDER.put("Relationship", List.of("Id", "Type", "Target", "TargetMode"));

        // PresentationML (pml.xsd) -- timing, animation, slide structure
        ATTR_ORDER.put("cTn", List.of("id", "presetID", "presetClass", "presetSubtype", "dur", "repeatCount", "repeatDur", "spd", "accel", "decel", "autoRev", "restart", "fill", "syncBehavior", "tmFilter", "evtFilter", "display", "masterRel", "bldLvl", "grpId", "afterEffect", "nodeType", "nodePh"));
        ATTR_ORDER.put("seq", List.of("concurrent", "prevAc", "nextAc"));
        ATTR_ORDER.put("cond", List.of("evt", "delay"));
        ATTR_ORDER.put("endSync", List.of("evt", "delay"));
        ATTR_ORDER.put("iterate", List.of("type", "backwards"));
        ATTR_ORDER.put("anim", List.of("by", "from", "to", "calcmode", "valueType"));
        ATTR_ORDER.put("animClr", List.of("clrSpc", "dir"));
        ATTR_ORDER.put("animEffect", List.of("transition", "filter", "prLst"));
        ATTR_ORDER.put("animMotion", List.of("origin", "path", "pathEditMode", "rAng", "ptsTypes"));
        ATTR_ORDER.put("animRot", List.of("by", "from", "to"));
        ATTR_ORDER.put("cBhvr", List.of("additive", "accumulate", "xfrmType", "from", "to", "by", "rctx", "override"));
        ATTR_ORDER.put("cMediaNode", List.of("vol", "mute", "numSld", "showWhenStopped"));
        ATTR_ORDER.put("tav", List.of("tm", "fmla"));
        ATTR_ORDER.put("bldP", List.of("spid", "grpId", "uiExpand", "build", "bldLvl", "animBg", "autoUpdateAnimBg", "rev", "advAuto"));
        ATTR_ORDER.put("bldDgm", List.of("spid", "grpId", "uiExpand", "bld"));
        ATTR_ORDER.put("bldGraphic", List.of("spid", "grpId", "uiExpand"));
        ATTR_ORDER.put("bldOleChart", List.of("spid", "grpId", "uiExpand", "bld", "animBg"));
        ATTR_ORDER.put("bldChart", List.of("bld", "animBg"));
        ATTR_ORDER.put("pRg", List.of("st", "end"));
        ATTR_ORDER.put("charRg", List.of("st", "end"));
        ATTR_ORDER.put("oleChartEl", List.of("type", "lvl"));
        ATTR_ORDER.put("sld", List.of("showMasterSp", "showMasterPhAnim", "show"));
        ATTR_ORDER.put("sldLayout", List.of("showMasterSp", "showMasterPhAnim", "matchingName", "type", "preserve", "userDrawn"));
        ATTR_ORDER.put("notes", List.of("showMasterSp", "showMasterPhAnim"));
        ATTR_ORDER.put("transition", List.of("spd", "advClick", "advTm"));
        ATTR_ORDER.put("split", List.of("orient", "dir"));
        ATTR_ORDER.put("ph", List.of("type", "idx", "sz", "orient", "hasCustomPrompt"));
        ATTR_ORDER.put("cm", List.of("authorId", "dt", "idx"));
        ATTR_ORDER.put("cmAuthor", List.of("id", "name", "initials", "lastIdx", "clrIdx"));
        ATTR_ORDER.put("tag", List.of("name", "val"));
        ATTR_ORDER.put("custShow", List.of("name", "id"));
        ATTR_ORDER.put("sldSz", List.of("cx", "cy", "type"));
        ATTR_ORDER.put("sldRg", List.of("st", "end"));
        ATTR_ORDER.put("guide", List.of("orient", "pos"));
        ATTR_ORDER.put("hf", List.of("sldNum", "hdr", "ftr", "dt"));
        ATTR_ORDER.put("normalViewPr", List.of("showOutlineIcons", "snapVertSplitter", "vertBarState", "horzBarState", "preferSingleView"));
        ATTR_ORDER.put("restoredLeft", List.of("sz", "autoAdjust"));
        ATTR_ORDER.put("restoredTop", List.of("sz", "autoAdjust"));
        ATTR_ORDER.put("cSldViewPr", List.of("snapToGrid", "snapToObjects", "showGuides"));
        ATTR_ORDER.put("showPr", List.of("loop", "showNarration", "showAnimation", "useTimings"));
        ATTR_ORDER.put("viewPr", List.of("lastView", "showComments"));
        ATTR_ORDER.put("nvPr", List.of("isPhoto", "userDrawn"));
        ATTR_ORDER.put("control", List.of("spid", "name", "showAsIcon", "imgW", "imgH"));
        ATTR_ORDER.put("oleObj", List.of("spid", "name", "showAsIcon", "imgW", "imgH", "progId"));
        ATTR_ORDER.put("htmlPubPr", List.of("showSpeakerNotes", "target", "title"));
        ATTR_ORDER.put("prnPr", List.of("prnWhat", "clrMode", "hiddenSlides", "scaleToFitPaper", "frameSlides"));
        ATTR_ORDER.put("photoAlbum", List.of("bw", "showCaptions", "layout", "frame"));
        ATTR_ORDER.put("presentation", List.of("serverZoom", "firstSlideNum", "showSpecialPlsOnTitleSld", "rtl", "removePersonalInfoOnSave", "compatMode", "strictFirstAndLastChars", "embedTrueTypeFonts", "saveSubsetFonts", "autoCompressPictures", "bookmarkIdSeed", "conformance"));
        ATTR_ORDER.put("sldSyncPr", List.of("serverSldId", "serverSldModifiedTime", "clientInsertedTime"));
        ATTR_ORDER.put("webPr", List.of("showAnimation", "resizeGraphics", "allowPng", "relyOnVml", "organizeInFolders", "useLongFilenames", "imgSz", "encoding", "clr"));

        // DrawingML (dml-main.xsd) -- shapes, text, geometry, effects
        ATTR_ORDER.put("cNvPr", List.of("id", "name", "descr", "hidden", "title"));
        ATTR_ORDER.put("bodyPr", List.of("rot", "spcFirstLastPara", "vertOverflow", "horzOverflow", "vert", "wrap", "lIns", "tIns", "rIns", "bIns", "numCol", "spcCol", "rtlCol", "fromWordArt", "anchor", "anchorCtr", "forceAA", "upright", "compatLnSpc"));
        ATTR_ORDER.put("rPr", List.of("kumimoji", "lang", "altLang", "sz", "b", "i", "u", "strike", "kern", "cap", "spc", "normalizeH", "baseline", "noProof", "dirty", "err", "smtClean", "smtId", "bmk"));
        ATTR_ORDER.put("endParaRPr", List.of("kumimoji", "lang", "altLang", "sz", "b", "i", "u", "strike", "kern", "cap", "spc", "normalizeH", "baseline", "noProof", "dirty", "err", "smtClean", "smtId", "bmk"));
        ATTR_ORDER.put("defRPr", List.of("kumimoji", "lang", "altLang", "sz", "b", "i", "u", "strike", "kern", "cap", "spc", "normalizeH", "baseline", "noProof", "dirty", "err", "smtClean", "smtId", "bmk"));
        ATTR_ORDER.put("pPr", List.of("marL", "marR", "lvl", "indent", "algn", "defTabSz", "rtl", "eaLnBrk", "fontAlgn", "latinLnBrk", "hangingPunct"));
        ATTR_ORDER.put("defPPr", List.of("marL", "marR", "lvl", "indent", "algn", "defTabSz", "rtl", "eaLnBrk", "fontAlgn", "latinLnBrk", "hangingPunct"));
        ATTR_ORDER.put("gd", List.of("name", "fmla"));
        ATTR_ORDER.put("off", List.of("x", "y"));
        ATTR_ORDER.put("ext", List.of("cx", "cy"));
        ATTR_ORDER.put("chOff", List.of("x", "y"));
        ATTR_ORDER.put("chExt", List.of("cx", "cy"));
        ATTR_ORDER.put("pos", List.of("x", "y"));
        ATTR_ORDER.put("pt", List.of("x", "y"));
        ATTR_ORDER.put("by", List.of("x", "y"));
        ATTR_ORDER.put("from", List.of("x", "y"));
        ATTR_ORDER.put("to", List.of("x", "y"));
        ATTR_ORDER.put("rCtr", List.of("x", "y"));
        ATTR_ORDER.put("sx", List.of("n", "d"));
        ATTR_ORDER.put("sy", List.of("n", "d"));
        ATTR_ORDER.put("ln", List.of("w", "cap", "cmpd", "algn"));
        ATTR_ORDER.put("headEnd", List.of("type", "w", "len"));
        ATTR_ORDER.put("tailEnd", List.of("type", "w", "len"));
        ATTR_ORDER.put("stCxn", List.of("id", "idx"));
        ATTR_ORDER.put("endCxn", List.of("id", "idx"));
        ATTR_ORDER.put("fld", List.of("id", "type"));
        ATTR_ORDER.put("latin", List.of("typeface", "panose", "pitchFamily", "charset"));
        ATTR_ORDER.put("ea", List.of("typeface", "panose", "pitchFamily", "charset"));
        ATTR_ORDER.put("cs", List.of("typeface", "panose", "pitchFamily", "charset"));
        ATTR_ORDER.put("sym", List.of("typeface", "panose", "pitchFamily", "charset"));
        ATTR_ORDER.put("buFont", List.of("typeface", "panose", "pitchFamily", "charset"));
        ATTR_ORDER.put("font", List.of("script", "typeface"));
        ATTR_ORDER.put("sysClr", List.of("val", "lastClr"));
        ATTR_ORDER.put("hslClr", List.of("hue", "sat", "lum"));
        ATTR_ORDER.put("scrgbClr", List.of("r", "g", "b"));
        ATTR_ORDER.put("rgb", List.of("r", "g", "b"));
        ATTR_ORDER.put("hsl", List.of("h", "s", "l"));
        ATTR_ORDER.put("clrMap", List.of("bg1", "tx1", "bg2", "tx2", "accent1", "accent2", "accent3", "accent4", "accent5", "accent6", "hlink", "folHlink"));
        ATTR_ORDER.put("overrideClrMapping", List.of("bg1", "tx1", "bg2", "tx2", "accent1", "accent2", "accent3", "accent4", "accent5", "accent6", "hlink", "folHlink"));
        ATTR_ORDER.put("outerShdw", List.of("blurRad", "dist", "dir", "sx", "sy", "kx", "ky", "algn", "rotWithShape"));
        ATTR_ORDER.put("innerShdw", List.of("blurRad", "dist", "dir"));
        ATTR_ORDER.put("prstShdw", List.of("prst", "dist", "dir"));
        ATTR_ORDER.put("reflection", List.of("blurRad", "stA", "stPos", "endA", "endPos", "dist", "dir", "fadeDir", "sx", "sy", "kx", "ky", "algn", "rotWithShape"));
        ATTR_ORDER.put("blur", List.of("rad", "grow"));
        ATTR_ORDER.put("sp3d", List.of("z", "extrusionH", "contourW", "prstMaterial"));
        ATTR_ORDER.put("bevelT", List.of("w", "h", "prst"));
        ATTR_ORDER.put("bevelB", List.of("w", "h", "prst"));
        ATTR_ORDER.put("bevel", List.of("w", "h", "prst"));
        ATTR_ORDER.put("camera", List.of("prst", "fov", "zoom"));
        ATTR_ORDER.put("lightRig", List.of("rig", "dir"));
        ATTR_ORDER.put("rot", List.of("lat", "lon", "rev"));
        ATTR_ORDER.put("norm", List.of("dx", "dy", "dz"));
        ATTR_ORDER.put("up", List.of("dx", "dy", "dz"));
        ATTR_ORDER.put("anchor", List.of("x", "y", "z"));
        ATTR_ORDER.put("blipFill", List.of("dpi", "rotWithShape"));
        ATTR_ORDER.put("gradFill", List.of("flip", "rotWithShape"));
        ATTR_ORDER.put("lin", List.of("ang", "scaled"));
        ATTR_ORDER.put("path", List.of("w", "h", "fill", "stroke", "extrusionOk"));
        ATTR_ORDER.put("srcRect", List.of("l", "t", "r", "b"));
        ATTR_ORDER.put("fillRect", List.of("l", "t", "r", "b"));
        ATTR_ORDER.put("fillToRect", List.of("l", "t", "r", "b"));
        ATTR_ORDER.put("tileRect", List.of("l", "t", "r", "b"));
        ATTR_ORDER.put("rect", List.of("l", "t", "r", "b"));
        ATTR_ORDER.put("tile", List.of("tx", "ty", "sx", "sy", "flip", "algn"));
        ATTR_ORDER.put("normAutofit", List.of("fontScale", "lnSpcReduction"));
        ATTR_ORDER.put("buAutoNum", List.of("type", "startAt"));
        ATTR_ORDER.put("tab", List.of("pos", "algn"));
        ATTR_ORDER.put("tblPr", List.of("rtl", "firstRow", "firstCol", "lastRow", "lastCol", "bandRow", "bandCol"));
        ATTR_ORDER.put("tc", List.of("rowSpan", "gridSpan", "hMerge", "vMerge", "id"));
        ATTR_ORDER.put("tcPr", List.of("marL", "marR", "marT", "marB", "vert", "anchor", "anchorCtr", "horzOverflow"));
        ATTR_ORDER.put("tcTxStyle", List.of("b", "i"));
        ATTR_ORDER.put("tblStyle", List.of("styleId", "styleName"));
        ATTR_ORDER.put("tableStyle", List.of("styleId", "styleName"));
        ATTR_ORDER.put("arcTo", List.of("wR", "hR", "stAng", "swAng"));
        ATTR_ORDER.put("ahXY", List.of("gdRefX", "minX", "maxX", "gdRefY", "minY", "maxY"));
        ATTR_ORDER.put("ahPolar", List.of("gdRefR", "minR", "maxR", "gdRefAng", "minAng", "maxAng"));
        ATTR_ORDER.put("ds", List.of("d", "sp"));
        ATTR_ORDER.put("lum", List.of("bright", "contrast"));
        ATTR_ORDER.put("tint", List.of("hue", "amt"));
        ATTR_ORDER.put("cont", List.of("type", "name"));
        ATTR_ORDER.put("effectDag", List.of("type", "name"));
        ATTR_ORDER.put("hlinkClick", List.of("r:id", "invalidUrl", "action", "tgtFrame", "tooltip", "history", "highlightClick", "endSnd"));
        ATTR_ORDER.put("hlinkHover", List.of("r:id", "invalidUrl", "action", "tgtFrame", "tooltip", "history", "highlightClick", "endSnd"));
        ATTR_ORDER.put("hlinkMouseOver", List.of("r:id", "invalidUrl", "action", "tgtFrame", "tooltip", "history", "highlightClick", "endSnd"));
        ATTR_ORDER.put("spLocks", List.of("noGrp", "noSelect", "noRot", "noChangeAspect", "noMove", "noResize", "noEditPoints", "noAdjustHandles", "noChangeArrowheads", "noChangeShapeType", "noTextEdit"));
        ATTR_ORDER.put("picLocks", List.of("noGrp", "noSelect", "noRot", "noChangeAspect", "noMove", "noResize", "noEditPoints", "noAdjustHandles", "noChangeArrowheads", "noChangeShapeType", "noCrop"));
        ATTR_ORDER.put("cxnSpLocks", List.of("noGrp", "noSelect", "noRot", "noChangeAspect", "noMove", "noResize", "noEditPoints", "noAdjustHandles", "noChangeArrowheads", "noChangeShapeType"));
        ATTR_ORDER.put("cpLocks", List.of("noGrp", "noSelect", "noRot", "noChangeAspect", "noMove", "noResize", "noEditPoints", "noAdjustHandles", "noChangeArrowheads", "noChangeShapeType"));
        ATTR_ORDER.put("grpSpLocks", List.of("noGrp", "noUngrp", "noSelect", "noRot", "noChangeAspect", "noMove", "noResize"));
        ATTR_ORDER.put("graphicFrameLocks", List.of("noGrp", "noDrilldown", "noSelect", "noChangeAspect", "noMove", "noResize"));

        // Paragraph level properties (lvl1pPr through lvl9pPr share same order)
        List<String> lvlPPrOrder = List.of("marL", "marR", "lvl", "indent", "algn", "defTabSz", "rtl", "eaLnBrk", "fontAlgn", "latinLnBrk", "hangingPunct");
        for (int i = 1; i <= 9; i++) {
            ATTR_ORDER.put("lvl" + i + "pPr", lvlPPrOrder);
        }

        // Line variants (lnB, lnT, lnL, lnR, lnBlToTr, lnTlToBr, uLn)
        List<String> lnOrder = List.of("w", "cap", "cmpd", "algn");
        ATTR_ORDER.put("lnB", lnOrder);
        ATTR_ORDER.put("lnT", lnOrder);
        ATTR_ORDER.put("lnL", lnOrder);
        ATTR_ORDER.put("lnR", lnOrder);
        ATTR_ORDER.put("lnBlToTr", lnOrder);
        ATTR_ORDER.put("lnTlToBr", lnOrder);
        ATTR_ORDER.put("uLn", lnOrder);

        // Additional PresentationML elements found in real PPTX files
        ATTR_ORDER.put("gridSpacing", List.of("cx", "cy"));
        ATTR_ORDER.put("notesSz", List.of("cx", "cy"));
        ATTR_ORDER.put("origin", List.of("x", "y"));
        ATTR_ORDER.put("sldId", List.of("id", "r:id"));
        ATTR_ORDER.put("sldLayoutId", List.of("id", "r:id"));
        ATTR_ORDER.put("sldMasterId", List.of("id", "r:id"));

        // Microsoft extension namespaces (observed in PowerPoint output)
        ATTR_ORDER.put("author", List.of("id", "name", "initials", "userId", "providerId"));
        ATTR_ORDER.put("themeFamily", List.of("name", "id", "vid"));
        ATTR_ORDER.put("vector", List.of("size", "baseType"));
        ATTR_ORDER.put("creationId", List.of("id", "val"));
        ATTR_ORDER.put("useLocalDpi", List.of("val"));
        ATTR_ORDER.put("xfrm", List.of("rot", "flipH", "flipV"));
    }

    /**
     * Namespace declaration ordering for root elements.
     * PowerPoint outputs: xmlns:a, xmlns:r, xmlns:p (alphabetical by prefix).
     */
    private static final List<String> NAMESPACE_ORDER = List.of(
        "xmlns:a", "xmlns:r", "xmlns:p"
    );

    /**
     * Serializes a DOM Document to a file with ECMA-376 canonical attribute ordering.
     * This replaces javax.xml.transform.Transformer for PPTX XML parts, since Java's
     * Transformer always alphabetizes attributes regardless of DOM order.
     *
     * @param document the DOM document to serialize
     * @param outputFile the file to write to
     * @throws IOException if writing fails
     */
    public static void serialize(Document document, File outputFile) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(outputFile), "UTF-8"))) {
            writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
            serializeNode(document.getDocumentElement(), writer, true);
        }
    }

    /**
     * Serialize a DOM Document to an OutputStream with OOXML-correct attribute ordering.
     * Does NOT close the output stream.
     */
    public static void serialize(Document document, OutputStream outputStream) throws IOException {
        BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(outputStream, "UTF-8"));
        writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
        serializeNode(document.getDocumentElement(), writer, true);
        writer.flush();
    }

    private static void serializeNode(Node node, Writer writer, boolean isRoot) throws IOException {
        switch (node.getNodeType()) {
            case Node.ELEMENT_NODE:
                serializeElement((Element) node, writer, isRoot);
                break;
            case Node.TEXT_NODE:
                writeEscapedText(node.getNodeValue(), writer);
                break;
            case Node.CDATA_SECTION_NODE:
                writer.write("<![CDATA[");
                writer.write(node.getNodeValue());
                writer.write("]]>");
                break;
            case Node.COMMENT_NODE:
                writer.write("<!--");
                writer.write(node.getNodeValue());
                writer.write("-->");
                break;
            case Node.PROCESSING_INSTRUCTION_NODE:
                writer.write("<?");
                writer.write(node.getNodeName());
                String piData = node.getNodeValue();
                if (piData != null && !piData.isEmpty()) {
                    writer.write(' ');
                    writer.write(piData);
                }
                writer.write("?>");
                break;
            default:
                break;
        }
    }

    private static void serializeElement(Element element, Writer writer, boolean isRoot) throws IOException {
        String tagName = element.getTagName();
        writer.write('<');
        writer.write(tagName);

        // Emit default namespace declaration if element has a namespace URI but no prefix
        // Java DOM's createElementNS() stores the namespace internally but may not expose
        // it as an xmlns attribute in getAttributes(). Standard Transformer handles this
        // automatically, but our custom serializer must do it explicitly.
        String nsUri = element.getNamespaceURI();
        boolean needsDefaultNs = isRoot && nsUri != null && !nsUri.isEmpty()
                && (element.getPrefix() == null || element.getPrefix().isEmpty());

        // Collect and sort attributes
        NamedNodeMap attrs = element.getAttributes();
        boolean hasExplicitDefaultNs = false;
        if (attrs.getLength() > 0 || needsDefaultNs) {
            List<String[]> nsAttrs = new ArrayList<>();
            List<String[]> regularAttrs = new ArrayList<>();

            for (int i = 0; i < attrs.getLength(); i++) {
                Node attr = attrs.item(i);
                String name = attr.getNodeName();
                String value = attr.getNodeValue();
                if (name.startsWith("xmlns")) {
                    if (name.equals("xmlns")) hasExplicitDefaultNs = true;
                    nsAttrs.add(new String[]{name, value});
                } else {
                    regularAttrs.add(new String[]{name, value});
                }
            }

            // Inject default namespace if element has one but DOM didn't expose it as attribute
            if (needsDefaultNs && !hasExplicitDefaultNs) {
                nsAttrs.add(0, new String[]{"xmlns", nsUri});
            }

            // Sort namespace declarations on root element
            if (isRoot && nsAttrs.size() >= 2) {
                nsAttrs.sort((a, b) -> {
                    int ai = NAMESPACE_ORDER.indexOf(a[0]);
                    int bi = NAMESPACE_ORDER.indexOf(b[0]);
                    if (ai < 0) ai = Integer.MAX_VALUE;
                    if (bi < 0) bi = Integer.MAX_VALUE;
                    if (ai != bi) return Integer.compare(ai, bi);
                    return a[0].compareTo(b[0]);
                });
            }

            // Sort regular attributes by schema order
            String localName = element.getLocalName();
            if (localName == null) {
                localName = tagName;
                int colon = localName.indexOf(':');
                if (colon >= 0) {
                    localName = localName.substring(colon + 1);
                }
            }

            List<String> schemaOrder = ATTR_ORDER.get(localName);
            if (schemaOrder != null && regularAttrs.size() >= 2) {
                regularAttrs.sort((a, b) -> {
                    int ai = schemaOrder.indexOf(a[0]);
                    int bi = schemaOrder.indexOf(b[0]);
                    if (ai < 0) ai = Integer.MAX_VALUE;
                    if (bi < 0) bi = Integer.MAX_VALUE;
                    if (ai != bi) return Integer.compare(ai, bi);
                    return a[0].compareTo(b[0]);
                });
            }

            // Write namespace declarations first, then regular attributes
            for (String[] ns : nsAttrs) {
                writer.write(' ');
                writer.write(ns[0]);
                writer.write("=\"");
                writeEscapedAttr(ns[1], writer);
                writer.write('"');
            }
            for (String[] attr : regularAttrs) {
                writer.write(' ');
                writer.write(attr[0]);
                writer.write("=\"");
                writeEscapedAttr(attr[1], writer);
                writer.write('"');
            }
        }

        // Check for children
        NodeList children = element.getChildNodes();
        if (children.getLength() == 0) {
            writer.write("/>");
        } else {
            writer.write('>');
            for (int i = 0; i < children.getLength(); i++) {
                serializeNode(children.item(i), writer, false);
            }
            writer.write("</");
            writer.write(tagName);
            writer.write('>');
        }
    }

    private static void writeEscapedText(String text, Writer writer) throws IOException {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&':  writer.write("&amp;"); break;
                case '<':  writer.write("&lt;"); break;
                case '>':  writer.write("&gt;"); break;
                default:   writer.write(c); break;
            }
        }
    }

    private static void writeEscapedAttr(String value, Writer writer) throws IOException {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&':  writer.write("&amp;"); break;
                case '<':  writer.write("&lt;"); break;
                case '"':  writer.write("&quot;"); break;
                case '\n': writer.write("&#xA;"); break;
                case '\r': writer.write("&#xD;"); break;
                case '\t': writer.write("&#x9;"); break;
                default:   writer.write(c); break;
            }
        }
    }
}
