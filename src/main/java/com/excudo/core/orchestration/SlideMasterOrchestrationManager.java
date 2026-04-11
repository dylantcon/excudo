package com.excudo.core.orchestration;

import com.excudo.core.results.ExecutionResult;
import com.excudo.core.themes.TextLevelStyle;
import com.excudo.core.utils.ComponentLogger;
import com.excudo.core.utils.Logger;
import com.excudo.core.utils.XMLConstants;
import org.w3c.dom.*;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manages slide master CRUD operations on slideMaster1.xml and theme1.xml.
 *
 * Operations:
 *   - Text style editing (titleStyle, bodyStyle, otherStyle) per level 1-9
 *   - Color map mutations (dark/light switching)
 *   - Background changes (bgRef idx and scheme color)
 *   - Object defaults (spDef/lnDef in theme XML)
 *   - Read-only queries (getMasterInfo, getTxStyles, getClrMap)
 */
public class SlideMasterOrchestrationManager {

    private static final ComponentLogger logger = Logger.getLogger(SlideMasterOrchestrationManager.class);

    private static final String PNS = XMLConstants.PRESENTATION_NS;
    private static final String ANS = XMLConstants.DRAWING_NS;
    private static final String MASTER_PART = "ppt/slideMasters/slideMaster1.xml";
    private static final String THEME_PART = "ppt/theme/theme1.xml";

    private final OrchestrationContext context;

    public SlideMasterOrchestrationManager(OrchestrationContext context) {
        if (context == null) {
            throw new IllegalArgumentException("OrchestrationContext cannot be null");
        }
        this.context = context;
    }

    // ========== TEXT STYLE OPERATIONS ==========

    /**
     * Edit a text style level on the slide master.
     *
     * @param target "title", "body", or "other"
     * @param level 1-9 (1-indexed, maps to a:lvl1pPr through a:lvl9pPr)
     * @param updates Map of property names to values: fontSize (int, hundredths of pt),
     *                bold (boolean), color (scheme ref string), bullet (char), bulletFont (string),
     *                margin (int EMU), indent (int EMU)
     */
    public ExecutionResult<Void> editMasterStyle(String target, int level, Map<String, Object> updates) {
        try {
            if (level < 1 || level > 9) {
                return ExecutionResult.failure("EditMasterStyle", "Level must be 1-9, got: " + level);
            }
            String styleName = resolveStyleName(target);
            if (styleName == null) {
                return ExecutionResult.failure("EditMasterStyle",
                    "Invalid target: " + target + ". Must be title, body, or other.");
            }

            Document doc = context.getXmlPart(MASTER_PART);
            if (doc == null) {
                return ExecutionResult.failure("EditMasterStyle", "slideMaster1.xml not found");
            }

            // Find p:txStyles > p:{target}Style > a:lvl{N}pPr > a:defRPr
            Element txStyles = findFirstElement(doc.getDocumentElement(), PNS, "txStyles");
            if (txStyles == null) {
                return ExecutionResult.failure("EditMasterStyle", "p:txStyles not found in slide master");
            }

            Element styleEl = findFirstElement(txStyles, PNS, styleName.replace("p:", ""));
            if (styleEl == null) {
                return ExecutionResult.failure("EditMasterStyle", styleName + " not found in p:txStyles");
            }

            String lvlTag = "lvl" + level + "pPr";
            Element lvlPPr = findFirstElement(styleEl, ANS, lvlTag);
            if (lvlPPr == null) {
                return ExecutionResult.failure("EditMasterStyle", "a:" + lvlTag + " not found in " + styleName);
            }

            // Apply property updates to lvlPPr and its defRPr child
            applyStyleUpdates(doc, lvlPPr, updates);

            context.putXmlPart(MASTER_PART, doc);
            logger.info("Updated {} level {} with {}", target, level, updates.keySet());
            return ExecutionResult.success("EditMasterStyle", null);

        } catch (Exception e) {
            logger.error("Failed to edit master style: {}", e.getMessage());
            return ExecutionResult.failure("EditMasterStyle",
                "Failed to edit master style: " + e.getMessage(), e);
        }
    }

    /**
     * Get current text styles from the slide master.
     *
     * @return Map with keys "titleStyle", "bodyStyle", "otherStyle", each mapping
     *         to a TextLevelStyle array (9 levels).
     */
    public Map<String, TextLevelStyle[]> getMasterStyles() {
        Map<String, TextLevelStyle[]> result = new LinkedHashMap<>();
        try {
            Document doc = context.getXmlPart(MASTER_PART);
            if (doc == null) return result;
            Element txStyles = findFirstElement(doc.getDocumentElement(), PNS, "txStyles");
            if (txStyles == null) return result;

            result.put("titleStyle", parseTextStyle(txStyles, "titleStyle"));
            result.put("bodyStyle", parseTextStyle(txStyles, "bodyStyle"));
            result.put("otherStyle", parseTextStyle(txStyles, "otherStyle"));
        } catch (Exception e) {
            logger.error("Failed to read master styles: {}", e.getMessage());
        }
        return result;
    }

    // ========== COLOR MAP OPERATIONS ==========

    /**
     * Set a color mapping attribute on the slide master's p:clrMap.
     *
     * @param logicalColor e.g., "bg1", "tx1", "bg2", "tx2"
     * @param themeColor e.g., "dk1", "lt1", "dk2", "lt2"
     */
    public ExecutionResult<Void> setClrMap(String logicalColor, String themeColor) {
        try {
            Document doc = context.getXmlPart(MASTER_PART);
            if (doc == null) {
                return ExecutionResult.failure("SetClrMap", "slideMaster1.xml not found");
            }
            Element clrMap = findFirstElement(doc.getDocumentElement(), PNS, "clrMap");
            if (clrMap == null) {
                return ExecutionResult.failure("SetClrMap", "p:clrMap not found in slide master");
            }

            clrMap.setAttribute(logicalColor, themeColor);
            context.putXmlPart(MASTER_PART, doc);

            // Invalidate cached isDarkTheme in PresentationOrchestrationManager
            invalidateDarkThemeCache();

            logger.info("Set clrMap {}={}", logicalColor, themeColor);
            return ExecutionResult.success("SetClrMap", null);

        } catch (Exception e) {
            logger.error("Failed to set clrMap: {}", e.getMessage());
            return ExecutionResult.failure("SetClrMap",
                "Failed to set clrMap: " + e.getMessage(), e);
        }
    }

    /**
     * Get the current color map from the slide master.
     */
    public Map<String, String> getClrMap() {
        Map<String, String> result = new LinkedHashMap<>();
        try {
            Document doc = context.getXmlPart(MASTER_PART);
            if (doc == null) return result;
            Element clrMap = findFirstElement(doc.getDocumentElement(), PNS, "clrMap");
            if (clrMap == null) return result;

            NamedNodeMap attrs = clrMap.getAttributes();
            for (int i = 0; i < attrs.getLength(); i++) {
                Node attr = attrs.item(i);
                // Skip xmlns attributes
                if (!attr.getNodeName().startsWith("xmlns")) {
                    result.put(attr.getNodeName(), attr.getNodeValue());
                }
            }
        } catch (Exception e) {
            logger.error("Failed to read clrMap: {}", e.getMessage());
        }
        return result;
    }

    // ========== BACKGROUND OPERATIONS ==========

    /**
     * Set the slide master background.
     *
     * @param fillIndex bgRef idx value (e.g., 1001)
     * @param schemeColor scheme color ref for bgRef (e.g., "bg1")
     */
    public ExecutionResult<Void> setBackground(int fillIndex, String schemeColor) {
        try {
            Document doc = context.getXmlPart(MASTER_PART);
            if (doc == null) {
                return ExecutionResult.failure("SetMasterBg", "slideMaster1.xml not found");
            }
            Element cSld = findFirstElement(doc.getDocumentElement(), PNS, "cSld");
            if (cSld == null) {
                return ExecutionResult.failure("SetMasterBg", "p:cSld not found in slide master");
            }

            // Find or create p:bg
            Element bg = findFirstElement(cSld, PNS, "bg");
            if (bg == null) {
                bg = doc.createElementNS(PNS, "p:bg");
                cSld.insertBefore(bg, cSld.getFirstChild());
            }

            // Find or create p:bgRef
            Element bgRef = findFirstElement(bg, PNS, "bgRef");
            if (bgRef == null) {
                // Remove any existing bg children (like p:bgPr) and add bgRef
                while (bg.hasChildNodes()) {
                    bg.removeChild(bg.getFirstChild());
                }
                bgRef = doc.createElementNS(PNS, "p:bgRef");
                bg.appendChild(bgRef);
            }

            bgRef.setAttribute("idx", String.valueOf(fillIndex));

            // Find or create a:schemeClr inside bgRef
            Element schemeClrEl = findFirstElement(bgRef, ANS, "schemeClr");
            if (schemeClrEl == null) {
                // Remove existing children
                while (bgRef.hasChildNodes()) {
                    bgRef.removeChild(bgRef.getFirstChild());
                }
                schemeClrEl = doc.createElementNS(ANS, "a:schemeClr");
                bgRef.appendChild(schemeClrEl);
            }
            schemeClrEl.setAttribute("val", schemeColor);

            context.putXmlPart(MASTER_PART, doc);
            logger.info("Set master background idx={} color={}", fillIndex, schemeColor);
            return ExecutionResult.success("SetMasterBg", null);

        } catch (Exception e) {
            logger.error("Failed to set master background: {}", e.getMessage());
            return ExecutionResult.failure("SetMasterBg",
                "Failed to set master background: " + e.getMessage(), e);
        }
    }

    /**
     * Resolve the background color hex for a slide, walking the OOXML hierarchy:
     * slide p:bg > master p:bg. Returns null if no background is defined.
     */
    public String getBackgroundColorHex(int slideNumber) {
        // Check slide-level background
        Document slideDom = context.getSlideDocument(slideNumber);
        if (slideDom != null) {
            String bg = extractBgSolidFillHex(slideDom);
            if (bg != null) return bg;
        }

        // Check slide master background
        Document masterDom = context.getXmlPart(MASTER_PART);
        if (masterDom != null) {
            String bg = extractBgSolidFillHex(masterDom);
            if (bg != null) return bg;
        }

        return null;
    }

    /**
     * Extract a solid fill hex color from a p:bg element in the given DOM.
     * Handles both bgPr/solidFill/srgbClr and bgPr/solidFill/schemeClr patterns.
     */
    private String extractBgSolidFillHex(Document dom) {
        Element bg = findFirstElement(dom.getDocumentElement(), PNS, "bg");
        if (bg == null) return null;

        // Check bgPr > solidFill > srgbClr
        Element bgPr = findFirstElement(bg, PNS, "bgPr");
        if (bgPr != null) {
            Element solidFill = findFirstElement(bgPr, ANS, "solidFill");
            if (solidFill != null) {
                Element srgb = findFirstElement(solidFill, ANS, "srgbClr");
                if (srgb != null && srgb.hasAttribute("val")) {
                    return "#" + srgb.getAttribute("val");
                }
                Element scheme = findFirstElement(solidFill, ANS, "schemeClr");
                if (scheme != null && scheme.hasAttribute("val")) {
                    String colorName = scheme.getAttribute("val");
                    return "#" + com.excudo.core.themes.ThemeManager.getThemeColor(colorName);
                }
            }
        }

        // Check bgRef > schemeClr (theme fill reference)
        Element bgRef = findFirstElement(bg, PNS, "bgRef");
        if (bgRef != null) {
            Element scheme = findFirstElement(bgRef, ANS, "schemeClr");
            if (scheme != null && scheme.hasAttribute("val")) {
                String colorName = scheme.getAttribute("val");
                return "#" + com.excudo.core.themes.ThemeManager.getThemeColor(colorName);
            }
        }

        return null;
    }

    // ========== OBJECT DEFAULTS (THEME-LEVEL) ==========

    /**
     * Populate a:objectDefaults in theme1.xml with spDef and lnDef.
     * This ensures non-placeholder shapes get consistent default styling.
     *
     * @param fontColor scheme color ref for default shape text (e.g., "tx1")
     * @param lineWidth line width in EMUs (e.g., 25400 = 2pt), null to skip
     * @param fillColor scheme color ref for default shape fill, null for no fill
     */
    public ExecutionResult<Void> setObjectDefaults(String fontColor, Integer lineWidth, String fillColor) {
        try {
            Document doc = context.getXmlPart(THEME_PART);
            if (doc == null) {
                return ExecutionResult.failure("SetObjectDefaults", "theme1.xml not found");
            }

            // Find a:objectDefaults under a:theme
            Element themeEl = doc.getDocumentElement();
            Element objDefaults = findFirstElement(themeEl, ANS, "objectDefaults");
            if (objDefaults == null) {
                objDefaults = doc.createElementNS(ANS, "a:objectDefaults");
                // Insert after themeElements
                Element themeElements = findFirstElement(themeEl, ANS, "themeElements");
                if (themeElements != null && themeElements.getNextSibling() != null) {
                    themeEl.insertBefore(objDefaults, themeElements.getNextSibling());
                } else {
                    themeEl.appendChild(objDefaults);
                }
            }

            // Clear existing children and rebuild
            while (objDefaults.hasChildNodes()) {
                objDefaults.removeChild(objDefaults.getFirstChild());
            }

            // a:spDef -- default shape definition
            Element spDef = doc.createElementNS(ANS, "a:spDef");
            objDefaults.appendChild(spDef);

            // spPr (empty -- inherit from theme style matrix)
            Element spPr = doc.createElementNS(ANS, "a:spPr");
            spDef.appendChild(spPr);

            // bodyPr
            Element bodyPr = doc.createElementNS(ANS, "a:bodyPr");
            bodyPr.setAttribute("rtlCol", "0");
            bodyPr.setAttribute("anchor", "ctr");
            spDef.appendChild(bodyPr);

            // lstStyle
            Element lstStyle = doc.createElementNS(ANS, "a:lstStyle");
            spDef.appendChild(lstStyle);

            // p:style with theme refs -- same pattern as shapes use
            Element style = doc.createElementNS(ANS, "a:style");
            spDef.appendChild(style);

            appendStyleRef(doc, style, "lnRef", "2", "accent1");
            appendStyleRef(doc, style, "fillRef", "1", "accent1");
            appendStyleRef(doc, style, "effectRef", "0", "accent1");

            // fontRef with specified color
            Element fontRef = doc.createElementNS(ANS, "a:fontRef");
            fontRef.setAttribute("idx", "minor");
            Element fontClr = doc.createElementNS(ANS, "a:schemeClr");
            fontClr.setAttribute("val", fontColor != null ? fontColor : "tx1");
            fontRef.appendChild(fontClr);
            style.appendChild(fontRef);

            // a:lnDef -- default line definition
            Element lnDef = doc.createElementNS(ANS, "a:lnDef");
            objDefaults.appendChild(lnDef);

            Element lnSpPr = doc.createElementNS(ANS, "a:spPr");
            lnDef.appendChild(lnSpPr);

            if (lineWidth != null) {
                Element ln = doc.createElementNS(ANS, "a:ln");
                ln.setAttribute("w", String.valueOf(lineWidth));
                lnSpPr.appendChild(ln);
            }

            Element lnBodyPr = doc.createElementNS(ANS, "a:bodyPr");
            lnDef.appendChild(lnBodyPr);
            Element lnLstStyle = doc.createElementNS(ANS, "a:lstStyle");
            lnDef.appendChild(lnLstStyle);

            Element lnStyle = doc.createElementNS(ANS, "a:style");
            lnDef.appendChild(lnStyle);
            appendStyleRef(doc, lnStyle, "lnRef", "2", "accent1");
            appendStyleRef(doc, lnStyle, "fillRef", "0", "accent1");
            appendStyleRef(doc, lnStyle, "effectRef", "0", "accent1");

            Element lnFontRef = doc.createElementNS(ANS, "a:fontRef");
            lnFontRef.setAttribute("idx", "minor");
            Element lnFontClr = doc.createElementNS(ANS, "a:schemeClr");
            lnFontClr.setAttribute("val", "tx1");
            lnFontRef.appendChild(lnFontClr);
            lnStyle.appendChild(lnFontRef);

            context.putXmlPart(THEME_PART, doc);
            logger.info("Set object defaults: fontColor={}, lineWidth={}", fontColor, lineWidth);
            return ExecutionResult.success("SetObjectDefaults", null);

        } catch (Exception e) {
            logger.error("Failed to set object defaults: {}", e.getMessage());
            return ExecutionResult.failure("SetObjectDefaults",
                "Failed to set object defaults: " + e.getMessage(), e);
        }
    }

    // ========== QUERY OPERATIONS ==========

    /**
     * Get a summary of the slide master state.
     *
     * @return Map containing clrMap, txStyles summary, background info, placeholder count
     */
    public Map<String, Object> getMasterInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        try {
            Document doc = context.getXmlPart(MASTER_PART);
            if (doc == null) {
                info.put("error", "slideMaster1.xml not found");
                return info;
            }

            // clrMap
            info.put("clrMap", getClrMap());

            // Placeholder count
            NodeList shapes = doc.getElementsByTagNameNS(PNS, "sp");
            int phCount = 0;
            for (int i = 0; i < shapes.getLength(); i++) {
                Element sp = (Element) shapes.item(i);
                if (findFirstElement(sp, PNS, "ph") != null) {
                    phCount++;
                }
            }
            info.put("placeholderCount", phCount);

            // Background
            Element bg = findFirstElement(doc.getDocumentElement(), PNS, "bg");
            if (bg != null) {
                Element bgRef = findFirstElement(bg, PNS, "bgRef");
                if (bgRef != null) {
                    info.put("bgRefIdx", bgRef.getAttribute("idx"));
                    Element schemeClr = findFirstElement(bgRef, ANS, "schemeClr");
                    if (schemeClr != null) {
                        info.put("bgColor", schemeClr.getAttribute("val"));
                    }
                }
            }

            // txStyles summary (font sizes per level for each style)
            Map<String, TextLevelStyle[]> styles = getMasterStyles();
            Map<String, String> styleSummary = new LinkedHashMap<>();
            for (Map.Entry<String, TextLevelStyle[]> entry : styles.entrySet()) {
                StringBuilder sb = new StringBuilder();
                TextLevelStyle[] levels = entry.getValue();
                for (int i = 0; i < Math.min(3, levels.length); i++) {
                    if (levels[i] == null) continue;
                    if (sb.length() > 0) sb.append(", ");
                    TextLevelStyle lvl = levels[i];
                    sb.append("L").append(i + 1).append("=")
                      .append(lvl.getFontSize() / 100).append("pt");
                    if (lvl.isBold()) sb.append("/bold");
                    sb.append("/").append(lvl.getColorRef());
                }
                styleSummary.put(entry.getKey(), sb.toString());
            }
            info.put("txStyles", styleSummary);

            // Object defaults check
            Document themeDoc = context.getXmlPart(THEME_PART);
            if (themeDoc != null) {
                Element objDefaults = findFirstElement(themeDoc.getDocumentElement(), ANS, "objectDefaults");
                boolean hasDefaults = objDefaults != null && objDefaults.hasChildNodes();
                info.put("objectDefaultsPopulated", hasDefaults);
            }

        } catch (Exception e) {
            info.put("error", "Failed to read master info: " + e.getMessage());
        }
        return info;
    }

    // ========== PRIVATE HELPERS ==========

    private String resolveStyleName(String target) {
        switch (target.toLowerCase()) {
            case "title": return "p:titleStyle";
            case "body": return "p:bodyStyle";
            case "other": return "p:otherStyle";
            default: return null;
        }
    }

    private void applyStyleUpdates(Document doc, Element lvlPPr, Map<String, Object> updates) {
        // Find or create a:defRPr
        Element defRPr = findFirstElement(lvlPPr, ANS, "defRPr");
        if (defRPr == null) {
            defRPr = doc.createElementNS(ANS, "a:defRPr");
            lvlPPr.appendChild(defRPr);
        }

        // fontSize -- in hundredths of a point (e.g., 2000 = 20pt)
        if (updates.containsKey("fontSize")) {
            Object val = updates.get("fontSize");
            int sz;
            if (val instanceof Integer) {
                sz = (Integer) val * 100; // interpret as points, convert to hundredths
            } else {
                sz = Integer.parseInt(val.toString()) * 100;
            }
            defRPr.setAttribute("sz", String.valueOf(sz));
        }

        // bold
        if (updates.containsKey("bold")) {
            boolean bold = Boolean.parseBoolean(updates.get("bold").toString());
            if (bold) {
                defRPr.setAttribute("b", "1");
            } else {
                defRPr.removeAttribute("b");
            }
        }

        // color -- scheme ref (replaces solidFill child)
        if (updates.containsKey("color")) {
            String colorRef = updates.get("color").toString();
            // Remove existing solidFill
            Element existingFill = findFirstElement(defRPr, ANS, "solidFill");
            if (existingFill != null) {
                defRPr.removeChild(existingFill);
            }
            // Add new solidFill with schemeClr
            Element fill = doc.createElementNS(ANS, "a:solidFill");
            Element schemeClr = doc.createElementNS(ANS, "a:schemeClr");
            schemeClr.setAttribute("val", colorRef);
            fill.appendChild(schemeClr);
            // Insert solidFill as first child of defRPr
            if (defRPr.hasChildNodes()) {
                defRPr.insertBefore(fill, defRPr.getFirstChild());
            } else {
                defRPr.appendChild(fill);
            }
        }

        // bullet
        if (updates.containsKey("bullet")) {
            String bulletChar = updates.get("bullet").toString();
            String bulletFont = updates.containsKey("bulletFont")
                ? updates.get("bulletFont").toString() : null;

            // Remove existing buNone or buChar
            removeDirectChild(lvlPPr, ANS, "buNone");
            removeDirectChild(lvlPPr, ANS, "buChar");
            removeDirectChild(lvlPPr, ANS, "buFont");

            if (bulletFont != null) {
                Element buFont = doc.createElementNS(ANS, "a:buFont");
                buFont.setAttribute("typeface", bulletFont);
                buFont.setAttribute("panose", "020B0604020202020204");
                buFont.setAttribute("pitchFamily", "34");
                buFont.setAttribute("charset", "0");
                // Insert before defRPr
                lvlPPr.insertBefore(buFont, defRPr);
            }

            Element buChar = doc.createElementNS(ANS, "a:buChar");
            buChar.setAttribute("char", bulletChar);
            lvlPPr.insertBefore(buChar, defRPr);
        }

        // margin (marL attribute on lvlPPr)
        if (updates.containsKey("margin")) {
            int margin = Integer.parseInt(updates.get("margin").toString());
            lvlPPr.setAttribute("marL", String.valueOf(margin));
        }

        // indent attribute on lvlPPr
        if (updates.containsKey("indent")) {
            int indent = Integer.parseInt(updates.get("indent").toString());
            lvlPPr.setAttribute("indent", String.valueOf(indent));
        }
    }

    private TextLevelStyle[] parseTextStyle(Element txStyles, String localName) {
        TextLevelStyle[] levels = new TextLevelStyle[9];
        Element styleEl = findFirstElement(txStyles, PNS, localName);
        if (styleEl == null) return levels;

        for (int i = 0; i < 9; i++) {
            Element lvlPPr = findFirstElement(styleEl, ANS, "lvl" + (i + 1) + "pPr");
            if (lvlPPr == null) continue;

            TextLevelStyle.Builder b = TextLevelStyle.builder(i);

            // Parse margin and indent from lvlPPr attributes
            String marL = lvlPPr.getAttribute("marL");
            if (!marL.isEmpty()) b.marginLeft(Integer.parseInt(marL));
            String indentAttr = lvlPPr.getAttribute("indent");
            if (!indentAttr.isEmpty()) b.indent(Integer.parseInt(indentAttr));
            String algn = lvlPPr.getAttribute("algn");
            if (!algn.isEmpty()) b.alignment(algn);

            // Parse bullet
            Element buChar = findFirstElement(lvlPPr, ANS, "buChar");
            if (buChar != null) {
                String bulletChar = buChar.getAttribute("char");
                Element buFont = findFirstElement(lvlPPr, ANS, "buFont");
                String bulletFont = buFont != null ? buFont.getAttribute("typeface") : null;
                b.bullet(bulletChar, bulletFont);
            } else {
                b.noBullet();
            }

            // Parse defRPr
            Element defRPr = findFirstElement(lvlPPr, ANS, "defRPr");
            if (defRPr != null) {
                String sz = defRPr.getAttribute("sz");
                if (!sz.isEmpty()) b.fontSize(Integer.parseInt(sz));
                String boldAttr = defRPr.getAttribute("b");
                b.bold("1".equals(boldAttr));

                // Parse color from solidFill
                Element solidFill = findFirstElement(defRPr, ANS, "solidFill");
                if (solidFill != null) {
                    Element schemeClr = findFirstElement(solidFill, ANS, "schemeClr");
                    if (schemeClr != null) {
                        b.colorRef(schemeClr.getAttribute("val"));
                    }
                    Element srgbClr = findFirstElement(solidFill, ANS, "srgbClr");
                    if (srgbClr != null) {
                        b.hexColor(srgbClr.getAttribute("val"));
                    }
                }
            }

            levels[i] = b.build();
        }
        return levels;
    }

    private void invalidateDarkThemeCache() {
        // The darkThemeCache field is package-private in PresentationOrchestrationManager.
        // Since we can't access it directly, we set a flag in context data that
        // PresentationOrchestrationManager can check.
        context.getContextData().put("darkThemeCacheInvalid", true);
    }

    private void appendStyleRef(Document doc, Element parent, String refName,
                                String idx, String schemeColorVal) {
        Element ref = doc.createElementNS(ANS, "a:" + refName);
        ref.setAttribute("idx", idx);
        Element clr = doc.createElementNS(ANS, "a:schemeClr");
        clr.setAttribute("val", schemeColorVal);
        ref.appendChild(clr);
        parent.appendChild(ref);
    }

    private Element findFirstElement(Element parent, String ns, String localName) {
        NodeList list = parent.getElementsByTagNameNS(ns, localName);
        if (list.getLength() > 0) {
            return (Element) list.item(0);
        }
        return null;
    }

    private void removeDirectChild(Element parent, String ns, String localName) {
        NodeList children = parent.getChildNodes();
        for (int i = children.getLength() - 1; i >= 0; i--) {
            Node child = children.item(i);
            if (child instanceof Element) {
                Element el = (Element) child;
                if (localName.equals(el.getLocalName()) &&
                    (ns == null || ns.equals(el.getNamespaceURI()))) {
                    parent.removeChild(el);
                }
            }
        }
    }
}
