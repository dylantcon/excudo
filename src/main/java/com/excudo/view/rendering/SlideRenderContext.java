package com.excudo.view.rendering;

import com.excudo.core.model.LayoutInfo;
import com.excudo.core.model.PlaceholderGeometry;
import com.excudo.core.model.PPTXDocument;
import com.excudo.core.model.SlideBackground;
import com.excudo.core.themes.ThemeDefinition;
import com.excudo.core.themes.ThemeManager;
import com.excudo.core.themes.TextLevelStyle;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.HashMap;

/**
 * Carries presentation-level context needed by the rendering pipeline.
 * Provides theme colors, font styles, layout geometry, and background info.
 *
 * The renderer reads from this instead of hardcoding values.
 */
public class SlideRenderContext {

    private final ThemeDefinition theme;
    private final LayoutInfo layoutInfo;
    private final PPTXDocument document;
    private final int slideNumber;

    private final java.util.Map<String, String> clrMap;
    private final String backgroundColorHex;
    private final TextLevelStyle[] masterTitleStyles;
    private final TextLevelStyle[] masterBodyStyles;

    // Lazy-resolved typed background. Sentinel NOT_RESOLVED separates
    // "not computed yet" from "computed to null" (no bg). Computed once
    // per context; SlideRenderContext is per-render so the cost is paid
    // at most once per slide render.
    private static final SlideBackground NOT_RESOLVED =
        new SlideBackground.Solid("#__NOT_RESOLVED__");
    private volatile SlideBackground slideBackgroundCache = NOT_RESOLVED;

    public SlideRenderContext(ThemeDefinition theme, LayoutInfo layoutInfo,
                              PPTXDocument document, int slideNumber,
                              java.util.Map<String, String> clrMap,
                              String backgroundColorHex,
                              java.util.Map<String, TextLevelStyle[]> masterStyles) {
        this.theme = theme;
        this.layoutInfo = layoutInfo;
        this.document = document;
        this.slideNumber = slideNumber;
        this.clrMap = clrMap != null ? clrMap : java.util.Map.of();
        this.backgroundColorHex = backgroundColorHex;
        this.masterTitleStyles = masterStyles != null ? masterStyles.get("titleStyle") : null;
        this.masterBodyStyles = masterStyles != null ? masterStyles.get("bodyStyle") : null;
    }

    /** Convenience constructor when master styles aren't available. */
    public SlideRenderContext(ThemeDefinition theme, LayoutInfo layoutInfo,
                              PPTXDocument document, int slideNumber,
                              java.util.Map<String, String> clrMap,
                              String backgroundColorHex) {
        this(theme, layoutInfo, document, slideNumber, clrMap, backgroundColorHex, null);
    }

    // ========== BACKGROUND ==========

    /**
     * Get the pre-resolved background color hex string for this slide.
     */
    public String getBackgroundColorHex() {
        if (backgroundColorHex == null) {
            throw new IllegalStateException("No background color provided for slide " + slideNumber);
        }
        return backgroundColorHex;
    }

    /**
     * Typed slide background descriptor, or null when no bg is defined.
     * Resolved lazily via {@link com.excudo.core.model.SlideBackgroundResolver}
     * — the walk is idempotent and cheap (small DOM, cached parsed theme),
     * so doing it once per render context is fine. Renderers that can
     * render image fills should prefer this over the hex-only path.
     */
    public SlideBackground getSlideBackground() {
        SlideBackground cached = slideBackgroundCache;
        if (cached == NOT_RESOLVED) {
            cached = com.excudo.core.rendering.SlideBackgroundResolver.resolve(
                document, slideNumber, clrMap);
            slideBackgroundCache = cached;
        }
        return cached;
    }

    /**
     * Get the default text color hex string for titles.
     * Resolves tx1 through the clrMap (dark theme: lt1, light theme: dk1).
     */
    public String getTitleTextColorHex() {
        return resolveSchemeColor("tx1");
    }

    /**
     * Get the default text color hex for body text.
     * Resolves from body style level 0's colorRef, falling back to tx1.
     */
    public String getBodyTextColorHex() {
        TextLevelStyle bodyStyle = getBodyStyle(0);
        if (bodyStyle != null && bodyStyle.getColorRef() != null) {
            return resolveSchemeColor(bodyStyle.getColorRef());
        }
        return getTitleTextColorHex();
    }

    // ========== THEME STYLES ==========

    /**
     * Get the TextLevelStyle for a title at the given indentation level.
     * Prefers PPTX master styles over bundled ThemeDefinition styles.
     */
    public TextLevelStyle getTitleStyle(int level) {
        if (masterTitleStyles != null && level >= 0 && level < masterTitleStyles.length
                && masterTitleStyles[level] != null) {
            return masterTitleStyles[level];
        }
        if (theme == null) return null;
        TextLevelStyle[] styles = theme.getTitleStyle();
        if (styles != null && level >= 0 && level < styles.length) {
            return styles[level];
        }
        return null;
    }

    /**
     * Get the TextLevelStyle for body text at the given indentation level.
     * Prefers PPTX master styles over bundled ThemeDefinition styles.
     */
    public TextLevelStyle getBodyStyle(int level) {
        if (masterBodyStyles != null && level >= 0 && level < masterBodyStyles.length
                && masterBodyStyles[level] != null) {
            return masterBodyStyles[level];
        }
        if (theme == null) return null;
        TextLevelStyle[] styles = theme.getBodyStyle();
        if (styles != null && level >= 0 && level < styles.length) {
            return styles[level];
        }
        return null;
    }

    // ========== FONT INFO ==========

    /**
     * Get the heading font family name.
     */
    public String getMajorFont() {
        return theme != null ? theme.getMajorFont() : "Calibri";
    }

    /**
     * Get the body font family name.
     */
    public String getMinorFont() {
        return theme != null ? theme.getMinorFont() : "Calibri";
    }

    /**
     * Fallback for the heading font when the declared family isn't installed
     * on the render host. May be null if the theme didn't declare one.
     */
    public String getMajorFontFallback() {
        return theme != null ? theme.getMajorFontFallback() : null;
    }

    /**
     * Fallback for the body font. May be null if the theme didn't declare one.
     */
    public String getMinorFontFallback() {
        return theme != null ? theme.getMinorFontFallback() : null;
    }

    // ========== PLACEHOLDER GEOMETRY ==========

    /**
     * Get placeholder geometry by type (e.g., "title", "ctrTitle", "body").
     */
    public PlaceholderGeometry getPlaceholderGeometry(String type) {
        if (layoutInfo == null) return null;
        return layoutInfo.getPlaceholderGeometryByType(type);
    }

    /**
     * Get placeholder geometry by index (e.g., "1", "2").
     */
    public PlaceholderGeometry getPlaceholderGeometryByIndex(String idx) {
        if (layoutInfo == null) return null;
        return layoutInfo.getPlaceholderGeometryByIndex(idx);
    }

    // ========== THEME COLOR RESOLUTION ==========

    /**
     * Resolve a scheme color name to a hex string.
     * Handles dk1, lt1, accent1-6, hlink, folHlink, tx1, bg1, etc.
     */
    public String resolveSchemeColor(String colorName) {
        // Resolve through the slide master's clrMap (e.g. tx1->lt1, bg1->dk1)
        String resolved = clrMap.getOrDefault(colorName, colorName);

        if (ThemeManager.isThemeLoaded()) {
            return ThemeManager.getThemeColor(resolved).withHash();
        }

        throw new IllegalStateException("No theme color for '" + colorName
            + "' (resolved='" + resolved + "'). ThemeManager.isThemeLoaded()="
            + ThemeManager.isThemeLoaded());
    }


    // ========== PLACEHOLDER bodyPr INHERITANCE ==========

    // Cache of (phType|phIdx) -> resolved anchor so multi-placeholder slides
    // don't re-walk the layout+master DOMs per shape. Null entries mean
    // "resolved to absent" -- keep them cached so the miss-path is O(1) too.
    private final java.util.Map<String, String> phAnchorCache = new HashMap<>();
    private static final String REL_NS = "http://schemas.openxmlformats.org/package/2006/relationships";
    private static final String MASTER_REL_TYPE =
        "http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster";

    /**
     * Resolve the vertical-anchor attribute for a placeholder bodyPr by
     * walking layout -> master, as required by ECMA-376 bodyPr attribute
     * inheritance (MS-OI29500 section 2.1.1379). Returns the anchor string
     * ("t", "ctr", "b") from whichever ancestor placeholder first defines
     * it, or null if neither specifies one.
     *
     * <p>The slide's own anchor attribute is NOT consulted here -- callers
     * check that first, falling through to this only for missing values.
     * Supply {@code phType} (e.g. "title") when known; otherwise supply
     * {@code phIdx}. When both are provided, type-match wins over idx-match
     * within each part. The default placeholder type for the master is
     * "title" (it has no explicit ph type on the title sp in most masters),
     * which this method folds in.
     */
    public String resolveInheritedBodyPrAnchor(String phType, String phIdx) {
        String key = (phType == null ? "" : phType) + "|" + (phIdx == null ? "" : phIdx);
        if (phAnchorCache.containsKey(key)) return phAnchorCache.get(key);

        String resolved = null;
        Document layoutDom = layoutInfo != null && document != null
            ? document.getXmlPart(normalizeLayoutPartName(layoutInfo.getFilePath()))
            : null;
        if (layoutDom != null) {
            resolved = findPlaceholderBodyPrAnchor(layoutDom, phType, phIdx);
        }
        if (resolved == null) {
            Document masterDom = resolveMasterDom(layoutDom);
            if (masterDom != null) {
                resolved = findPlaceholderBodyPrAnchor(masterDom, phType, phIdx);
            }
        }
        phAnchorCache.put(key, resolved);
        return resolved;
    }

    /**
     * Walk the layout's .rels, find the slideMaster relationship, and
     * return the master DOM. Mirrors TransitionReader.resolveMasterDom.
     */
    private Document resolveMasterDom(Document layoutDom) {
        if (document == null || layoutInfo == null) return null;
        String layoutPartName = normalizeLayoutPartName(layoutInfo.getFilePath());
        if (layoutPartName == null) return null;
        int lastSlash = layoutPartName.lastIndexOf('/');
        if (lastSlash < 0) return null;
        String dir = layoutPartName.substring(0, lastSlash);
        String filename = layoutPartName.substring(lastSlash + 1);
        Document relsDom = document.getXmlPart(dir + "/_rels/" + filename + ".rels");
        if (relsDom == null) return null;
        Element relsRoot = relsDom.getDocumentElement();
        if (relsRoot == null) return null;
        NodeList rels = relsRoot.getElementsByTagNameNS(REL_NS, "Relationship");
        for (int i = 0; i < rels.getLength(); i++) {
            Element r = (Element) rels.item(i);
            if (!MASTER_REL_TYPE.equals(r.getAttribute("Type"))) continue;
            String target = r.getAttribute("Target");
            if (target == null || target.isEmpty()) continue;
            String masterPart = resolveRelativePart(dir, target);
            Document masterDom = document.getXmlPart(masterPart);
            if (masterDom != null) return masterDom;
        }
        return null;
    }

    private static String resolveRelativePart(String baseDir, String target) {
        if (target.startsWith("/")) return target.substring(1);
        java.util.Deque<String> stack = new java.util.ArrayDeque<>();
        for (String s : baseDir.split("/")) if (!s.isEmpty()) stack.addLast(s);
        for (String seg : target.split("/")) {
            if (seg.isEmpty() || ".".equals(seg)) continue;
            if ("..".equals(seg)) { if (!stack.isEmpty()) stack.removeLast(); }
            else stack.addLast(seg);
        }
        return String.join("/", stack);
    }

    /**
     * Scan a layout/master DOM for a p:sp whose p:ph matches the requested
     * phType/phIdx, and return its bodyPr/@anchor. Type match trumps idx
     * match. Returns null if no matching placeholder carries an anchor.
     */
    private static String findPlaceholderBodyPrAnchor(Document dom, String phType, String phIdx) {
        if (dom == null) return null;
        NodeList shapes = dom.getElementsByTagName("p:sp");
        String idxMatch = null; // fallback if type doesn't match anything
        for (int i = 0; i < shapes.getLength(); i++) {
            Element sp = (Element) shapes.item(i);
            Element ph = firstDescendant(sp, "p:ph");
            if (ph == null) continue;
            String shapePhType = ph.getAttribute("type");
            String shapePhIdx = ph.getAttribute("idx");
            // Master's title ph sometimes omits type=title (implicit).
            // Treat missing type as "title" ONLY when idx is also missing,
            // which is the canonical master-title pattern.
            if (shapePhType.isEmpty() && shapePhIdx.isEmpty()) {
                shapePhType = "title";
            }
            boolean typeHit = phType != null && !phType.isEmpty()
                && phType.equals(shapePhType);
            boolean idxHit = phIdx != null && !phIdx.isEmpty()
                && phIdx.equals(shapePhIdx);
            if (!typeHit && !idxHit) continue;
            Element txBody = firstDescendant(sp, "p:txBody");
            if (txBody == null) continue;
            Element bodyPr = firstDescendant(txBody, "a:bodyPr");
            if (bodyPr == null) continue;
            String anchor = bodyPr.getAttribute("anchor");
            if (anchor.isEmpty()) continue;
            if (typeHit) return anchor; // prefer type match
            if (idxMatch == null) idxMatch = anchor;
        }
        return idxMatch;
    }

    private static Element firstDescendant(Element parent, String tag) {
        NodeList kids = parent.getElementsByTagName(tag);
        return kids.getLength() > 0 ? (Element) kids.item(0) : null;
    }

    /**
     * PPTXDocumentParser stores LayoutInfo.filePath as "slideLayouts/..."
     * (no {@code ppt/} prefix) while {@link PPTXDocument#getXmlPart} keys
     * on the full OPC part name including that prefix. Prepend it when
     * missing; tolerate a leading slash too. Returns null unchanged.
     */
    private static String normalizeLayoutPartName(String raw) {
        if (raw == null) return null;
        String s = raw.startsWith("/") ? raw.substring(1) : raw;
        if (s.startsWith("ppt/")) return s;
        if (s.startsWith("slideLayouts/") || s.startsWith("slideMasters/")) {
            return "ppt/" + s;
        }
        return s;
    }

    // ========== RAW ACCESSORS ==========

    public ThemeDefinition getTheme() { return theme; }
    public LayoutInfo getLayoutInfo() { return layoutInfo; }
    public PPTXDocument getDocument() { return document; }
    public int getSlideNumber() { return slideNumber; }
}
