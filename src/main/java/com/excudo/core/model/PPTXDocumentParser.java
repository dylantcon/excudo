package com.excudo.core.model;

import com.excudo.core.utils.XMLConstants;
import com.excudo.core.utils.Logger;
import com.excudo.core.utils.ComponentLogger;
import com.excudo.exceptions.XMLParsingException;
import org.w3c.dom.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Single-pass parser that extracts all derived state from a PPTXDocument's in-memory DOMs.
 * Called once at load time. The result (ParsedPresentationState) provides everything
 * that RelationshipManager, SPIDManager, LayoutManager, SlideLayoutParser, and
 * NotesSlideRegistry would otherwise scan from disk.
 *
 * Usage:
 *   PPTXDocument doc = PPTXDocument.loadFromZip(file);
 *   ParsedPresentationState state = PPTXDocumentParser.parse(doc);
 *   // pass state to manager constructors
 */
public final class PPTXDocumentParser {

    private static final ComponentLogger logger = Logger.getLogger(PPTXDocumentParser.class);

    private PPTXDocumentParser() {}

    // ========== RESULT TYPE ==========

    /**
     * All derived state extracted from a PPTXDocument in one pass.
     * Immutable snapshot -- managers copy what they need from this at construction time.
     */
    public static class ParsedPresentationState {

        // --- Relationships ---
        private final Map<String, RelationshipEntry> globalRelationships;
        private final Map<String, Integer> perFileMaxRId;
        private int globalMaxRId;

        // --- SPIDs ---
        private final Map<Integer, SpidEntry> spidRegistry;

        // --- Layouts ---
        private final Map<String, LayoutInfo> layouts;
        private final Map<Integer, String> slideToLayoutId;
        private final Map<String, Boolean> layoutHasTitle;

        // --- Master / theme chain ---
        // layoutId ("slideLayout3") -> masterId ("slideMaster2"), via each
        // layout's rels. masterId -> theme part name ("ppt/theme/theme2.xml"),
        // via each master's rels. Together these complete the
        // slide -> layout -> master -> theme walk for multi-master decks.
        private final Map<String, String> layoutToMasterId;
        private final Map<String, String> masterToThemePart;

        // --- Notes ---
        private final Map<Integer, Integer> notesToSlideMap;

        // --- Slide count ---
        private final int slideCount;

        private ParsedPresentationState() {
            this(0);
        }

        private ParsedPresentationState(int slideCount) {
            this.globalRelationships = new LinkedHashMap<>();
            this.perFileMaxRId = new HashMap<>();
            this.spidRegistry = new ConcurrentHashMap<>();
            this.layouts = new LinkedHashMap<>();
            this.slideToLayoutId = new HashMap<>();
            this.layoutHasTitle = new HashMap<>();
            this.layoutToMasterId = new HashMap<>();
            this.masterToThemePart = new HashMap<>();
            this.notesToSlideMap = new TreeMap<>();
            this.slideCount = slideCount;
        }

        public Map<String, RelationshipEntry> getGlobalRelationships() { return globalRelationships; }
        public Map<String, Integer> getPerFileMaxRId() { return perFileMaxRId; }
        public int getGlobalMaxRId() { return globalMaxRId; }
        public Map<Integer, SpidEntry> getSpidRegistry() { return spidRegistry; }
        public Map<String, LayoutInfo> getLayouts() { return layouts; }
        public Map<Integer, String> getSlideToLayoutId() { return slideToLayoutId; }
        public Map<String, Boolean> getLayoutHasTitle() { return layoutHasTitle; }
        public Map<String, String> getLayoutToMasterId() { return layoutToMasterId; }
        public Map<String, String> getMasterToThemePart() { return masterToThemePart; }
        public Map<Integer, Integer> getNotesToSlideMap() { return notesToSlideMap; }
        public int getSlideCount() { return slideCount; }

        /**
         * Master id (e.g. {@code "slideMaster2"}) backing the given slide,
         * resolved through its layout. Falls back to {@code "slideMaster1"}
         * when the chain can't be resolved -- single-master decks and
         * malformed rels both land on the conventional master rather than
         * null, matching the legacy hardcoded behavior.
         */
        public String getMasterIdForSlide(int slideNumber) {
            String layoutId = slideToLayoutId.get(slideNumber);
            if (layoutId != null) {
                String masterId = layoutToMasterId.get(layoutId);
                if (masterId != null) return masterId;
            }
            return "slideMaster1";
        }

        /** Full part name of the slide's master, e.g. {@code "ppt/slideMasters/slideMaster2.xml"}. */
        public String getMasterPartForSlide(int slideNumber) {
            return "ppt/slideMasters/" + getMasterIdForSlide(slideNumber) + ".xml";
        }

        /**
         * Theme part name (e.g. {@code "ppt/theme/theme2.xml"}) for the given
         * slide's master. Falls back to {@code "ppt/theme/theme1.xml"} when
         * unresolved, matching legacy behavior.
         */
        public String getThemePartForSlide(int slideNumber) {
            String masterId = getMasterIdForSlide(slideNumber);
            String themePart = masterToThemePart.get(masterId);
            return themePart != null ? themePart : "ppt/theme/theme1.xml";
        }
    }

    public static class RelationshipEntry {
        private final String type;
        private final String target;
        private final String sourcePartName;

        public RelationshipEntry(String type, String target, String sourcePartName) {
            this.type = type;
            this.target = target;
            this.sourcePartName = sourcePartName;
        }

        public String getType() { return type; }
        public String getTarget() { return target; }
        public String getSourcePartName() { return sourcePartName; }
    }

    public static class SpidEntry {
        private final int slideNumber;
        private final String shapeName;

        public SpidEntry(int slideNumber, String shapeName) {
            this.slideNumber = slideNumber;
            this.shapeName = shapeName;
        }

        public int getSlideNumber() { return slideNumber; }
        public String getShapeName() { return shapeName; }
    }

    // ========== MAIN PARSE METHOD ==========

    /**
     * Extract all derived state from a PPTXDocument in a single pass.
     */
    public static ParsedPresentationState parse(PPTXDocument doc) throws XMLParsingException {
        try {
            List<Integer> slideNumbers = doc.getSlideNumbers();
            ParsedPresentationState state = new ParsedPresentationState(slideNumbers.size());

            boolean timing = PPTXDocument.LOAD_TIMING;
            long[] t = new long[8];
            int ti = 0;
            t[ti++] = timing ? System.nanoTime() : 0;

            // 1. Scan all .rels parts for relationships
            scanRelationships(doc, state);
            t[ti++] = timing ? System.nanoTime() : 0;

            // 2. Scan all slide DOMs for SPIDs
            scanSpids(doc, slideNumbers, state);
            t[ti++] = timing ? System.nanoTime() : 0;

            // 3. Resolve slide-to-layout mappings from slide .rels
            resolveSlideLayouts(doc, slideNumbers, state);
            t[ti++] = timing ? System.nanoTime() : 0;

            // 4. Parse layout DOMs for capabilities (title, content, geometry)
            parseLayouts(doc, state);
            t[ti++] = timing ? System.nanoTime() : 0;

            // 4b. Resolve layout -> master via each layout's .rels
            resolveLayoutMasters(doc, state);
            t[ti++] = timing ? System.nanoTime() : 0;

            // 4c. Resolve master -> theme via each master's .rels
            resolveMasterThemes(doc, state);
            t[ti++] = timing ? System.nanoTime() : 0;

            // 5. Scan notes .rels for notes-to-slide mappings
            scanNotes(doc, state);
            t[ti++] = timing ? System.nanoTime() : 0;

            if (timing) {
                logger.info("parse-timing: relationships={}ms spids={}ms slideLayouts={}ms "
                    + "parseLayouts={}ms layoutMasters={}ms masterThemes={}ms notes={}ms",
                    (t[1]-t[0])/1_000_000, (t[2]-t[1])/1_000_000, (t[3]-t[2])/1_000_000,
                    (t[4]-t[3])/1_000_000, (t[5]-t[4])/1_000_000, (t[6]-t[5])/1_000_000,
                    (t[7]-t[6])/1_000_000);
            }

            logger.info("Parsed PPTXDocument: {} relationships, {} SPIDs, {} layouts, {} slides, {} notes, {} masters",
                        state.globalRelationships.size(), state.spidRegistry.size(),
                        state.layouts.size(), state.slideCount, state.notesToSlideMap.size(),
                        new java.util.HashSet<>(state.layoutToMasterId.values()).size());

            return state;

        } catch (Exception e) {
            throw new XMLParsingException("Failed to parse PPTXDocument: " + e.getMessage(), e);
        }
    }

    // ========== RELATIONSHIP SCANNING ==========

    private static void scanRelationships(PPTXDocument doc, ParsedPresentationState state) {
        int globalMax = 0;

        for (String partName : doc.getPartNames()) {
            if (!partName.endsWith(".rels")) continue;

            Document relsDom = doc.getXmlPart(partName);
            if (relsDom == null) continue;

            NodeList rels = relsDom.getElementsByTagName("Relationship");
            int maxForFile = 0;

            for (int i = 0; i < rels.getLength(); i++) {
                Element rel = (Element) rels.item(i);
                String id = rel.getAttribute("Id");
                String type = rel.getAttribute("Type");
                String target = rel.getAttribute("Target");

                state.globalRelationships.put(id, new RelationshipEntry(type, target, partName));

                if (id.startsWith("rId")) {
                    try {
                        int num = Integer.parseInt(id.substring(3));
                        maxForFile = Math.max(maxForFile, num);
                        globalMax = Math.max(globalMax, num);
                    } catch (NumberFormatException ignored) {}
                }
            }

            if (maxForFile > 0) {
                state.perFileMaxRId.put(partName, maxForFile);
            }
        }

        state.globalMaxRId = globalMax;
    }

    // ========== SPID SCANNING ==========

    private static void scanSpids(PPTXDocument doc, List<Integer> slideNumbers,
                                   ParsedPresentationState state) {
        for (int slideNum : slideNumbers) {
            Document slideDom = doc.getSlideDocument(slideNum);
            if (slideDom == null) continue;
            // Direct DOM traversal -- getElementsByTagNameNS is a native, indexed
            // descendant scan; far cheaper than an XPath "//p:cNvPr" evaluated
            // over a wrapped w3c DOM (which builds a Xalan DTM per query).
            NodeList cNvPrs = slideDom.getElementsByTagNameNS(XMLConstants.PRESENTATION_NS, "cNvPr");
            for (int i = 0; i < cNvPrs.getLength(); i++) {
                String id = ((Element) cNvPrs.item(i)).getAttribute("id");
                if (id.isEmpty()) continue;
                try {
                    state.spidRegistry.put(Integer.parseInt(id), new SpidEntry(slideNum, "existing_shape"));
                } catch (NumberFormatException ignored) {}
            }
        }
    }

    // ========== SLIDE-TO-LAYOUT RESOLUTION ==========

    private static void resolveSlideLayouts(PPTXDocument doc, List<Integer> slideNumbers,
                                             ParsedPresentationState state) {
        for (int slideNum : slideNumbers) {
            String relsPartName = "ppt/slides/_rels/slide" + slideNum + ".xml.rels";
            Document relsDom = doc.getXmlPart(relsPartName);
            if (relsDom == null) continue;
            String target = firstRelTarget(relsDom, XMLConstants.RELATIONSHIP_TYPE_SLIDE_LAYOUT);
            String layoutId = idFromTarget(target); // "../slideLayouts/slideLayout1.xml" -> "slideLayout1"
            if (layoutId != null) state.slideToLayoutId.put(slideNum, layoutId);
        }
    }

    /** Target of the first {@code <Relationship>} with the given Type in a
     *  .rels DOM, or null. Mirrors {@link #scanRelationships}'s direct
     *  getElementsByTagName walk rather than paying XPath over the DOM. */
    private static String firstRelTarget(Document relsDom, String type) {
        NodeList rels = relsDom.getElementsByTagName("Relationship");
        for (int i = 0; i < rels.getLength(); i++) {
            Element rel = (Element) rels.item(i);
            if (type.equals(rel.getAttribute("Type"))) {
                String t = rel.getAttribute("Target");
                return (t == null || t.isEmpty()) ? null : t;
            }
        }
        return null;
    }

    // ========== LAYOUT -> MASTER RESOLUTION ==========

    /**
     * Resolve each layout to its backing master via the layout's .rels file.
     * Each {@code ppt/slideLayouts/_rels/slideLayoutN.xml.rels} carries exactly
     * one slideMaster relationship. Multi-master decks point different layouts
     * at different masters; single-master decks all point at slideMaster1.
     */
    private static void resolveLayoutMasters(PPTXDocument doc, ParsedPresentationState state) {
        for (String layoutId : state.layouts.keySet()) {
            String relsPartName = "ppt/slideLayouts/_rels/" + layoutId + ".xml.rels";
            Document relsDom = doc.getXmlPart(relsPartName);
            if (relsDom == null) continue;
            String masterId = idFromTarget(
                firstRelTarget(relsDom, XMLConstants.RELATIONSHIP_TYPE_SLIDE_MASTER));
            if (masterId != null) state.layoutToMasterId.put(layoutId, masterId);
        }
    }

    // ========== MASTER -> THEME RESOLUTION ==========

    /**
     * Resolve each distinct master to its theme part via the master's .rels
     * file. Multi-master decks typically pair each master with its own theme,
     * so color/fmtScheme resolution must follow this per-master mapping rather
     * than assuming theme1.
     */
    private static void resolveMasterThemes(PPTXDocument doc, ParsedPresentationState state) {
        Set<String> masters = new HashSet<>(state.layoutToMasterId.values());
        for (String masterId : masters) {
            String relsPartName = "ppt/slideMasters/_rels/" + masterId + ".xml.rels";
            Document relsDom = doc.getXmlPart(relsPartName);
            if (relsDom == null) continue;
            // Target is relative to ppt/slideMasters/, e.g. "../theme/theme2.xml"
            String target = firstRelTarget(relsDom, XMLConstants.RELATIONSHIP_TYPE_THEME);
            String themePart = resolveRelativePart("ppt/slideMasters", target);
            if (themePart != null) state.masterToThemePart.put(masterId, themePart);
        }
    }

    /** Extract a part id from a rels Target, e.g. "../slideMasters/slideMaster2.xml" -> "slideMaster2". */
    private static String idFromTarget(String target) {
        if (target == null || target.isEmpty()) return null;
        String filename = target.substring(target.lastIndexOf('/') + 1);
        return filename.endsWith(".xml") ? filename.substring(0, filename.length() - 4) : filename;
    }

    /** Resolve a relative rels Target against a base directory into an OPC part name. */
    private static String resolveRelativePart(String baseDir, String target) {
        if (target == null || target.isEmpty()) return null;
        if (target.startsWith("/")) return target.substring(1);
        Deque<String> stack = new ArrayDeque<>();
        for (String s : baseDir.split("/")) if (!s.isEmpty()) stack.addLast(s);
        for (String seg : target.split("/")) {
            if (seg.isEmpty() || ".".equals(seg)) continue;
            if ("..".equals(seg)) { if (!stack.isEmpty()) stack.removeLast(); }
            else stack.addLast(seg);
        }
        return String.join("/", stack);
    }

    // ========== LAYOUT PARSING ==========

    private static void parseLayouts(PPTXDocument doc, ParsedPresentationState state) {
        Set<String> layoutParts = doc.getPartNamesByPrefix("ppt/slideLayouts/");

        for (String partName : layoutParts) {
            if (!partName.endsWith(".xml") || partName.contains("_rels")) continue;

            Document layoutDom = doc.getXmlPart(partName);
            if (layoutDom == null) continue;

            try {
                // Extract layout ID from part name (e.g., "ppt/slideLayouts/slideLayout1.xml" -> "slideLayout1")
                String filename = partName.substring(partName.lastIndexOf('/') + 1);
                String layoutId = filename.substring(0, filename.length() - 4);

                // Parse layout name
                String name = parseLayoutName(layoutDom);
                if (name == null || name.isEmpty()) name = layoutId;

                // Analyze placeholders
                boolean hasTitle = hasPlaceholderType(layoutDom, "title", "ctrTitle");
                boolean hasContent = hasPlaceholderType(layoutDom, "body", "obj")
                    || hasIndexedContent(layoutDom);
                boolean hasSubtitle = hasPlaceholderType(layoutDom, "subTitle");
                String titleType = detectTitleType(layoutDom);
                int contentCount = countPlaceholderType(layoutDom, "body", "obj")
                    + countIndexedContent(layoutDom);

                // Parse placeholder geometries
                List<PlaceholderGeometry> geometries = parsePlaceholderGeometries(layoutDom, layoutId);

                String description = generateDescription(hasTitle, hasContent, hasSubtitle);
                String filePath = "slideLayouts/" + filename;

                LayoutInfo info = new LayoutInfo(layoutId, name, filePath, hasTitle,
                    hasContent, hasSubtitle, contentCount, description, geometries, titleType);
                state.layouts.put(layoutId, info);
                state.layoutHasTitle.put(layoutId, hasTitle);

            } catch (Exception e) {
                logger.warn("Failed to parse layout {}: {}", partName, e.getMessage());
            }
        }
    }

    // ========== NOTES SCANNING ==========

    private static void scanNotes(PPTXDocument doc, ParsedPresentationState state) {
        Set<String> notesParts = doc.getPartNamesByPrefix("ppt/notesSlides/");

        for (String partName : notesParts) {
            if (!partName.endsWith(".xml") || partName.contains("_rels")) continue;

            try {
                String filename = partName.substring(partName.lastIndexOf('/') + 1);
                if (!filename.startsWith("notesSlide")) continue;

                String numStr = filename.substring(10, filename.length() - 4);
                int seqNum = Integer.parseInt(numStr);

                // Find target slide from rels
                String relsPartName = "ppt/notesSlides/_rels/notesSlide" + numStr + ".xml.rels";
                Document relsDom = doc.getXmlPart(relsPartName);
                if (relsDom == null) continue;

                NodeList rels = relsDom.getElementsByTagName("Relationship");
                for (int i = 0; i < rels.getLength(); i++) {
                    Element rel = (Element) rels.item(i);
                    String type = rel.getAttribute("Type");
                    String target = rel.getAttribute("Target");
                    if (type.contains("/slide") && !type.contains("slideLayout") && !type.contains("slideMaster")) {
                        String slideFilename = target.substring(target.lastIndexOf('/') + 1);
                        String slideNumStr = slideFilename.replace("slide", "").replace(".xml", "");
                        state.notesToSlideMap.put(seqNum, Integer.parseInt(slideNumStr));
                        break;
                    }
                }
            } catch (NumberFormatException e) {
                logger.warn("Ignoring malformed notes part: {}", partName);
            }
        }
    }

    // ========== LAYOUT PARSING HELPERS ==========

    private static String parseLayoutName(Document doc) {
        try {
            NodeList cSldList = doc.getElementsByTagName("p:cSld");
            if (cSldList.getLength() > 0) {
                String name = ((Element) cSldList.item(0)).getAttribute("name");
                if (!name.isEmpty()) return name;
            }
        } catch (Exception ignored) {}
        return null;
    }

    // All placeholder analysis below walks p:ph / p:sp via getElementsByTagNameNS
    // rather than XPath. A p:ph only ever lives inside a p:sp's nvSpPr, so
    // "an sp containing a ph of type X" is equivalent to "a ph of type X
    // exists" -- the DOM scan is a fraction of the cost of evaluating
    // "//p:sp[.//p:ph[...]]" against a Xalan-wrapped w3c DOM per layout.

    private static NodeList phElements(Document doc) {
        return doc.getElementsByTagNameNS(XMLConstants.PRESENTATION_NS, "ph");
    }

    private static boolean hasPlaceholderType(Document doc, String... types) {
        NodeList phs = phElements(doc);
        for (int i = 0; i < phs.getLength(); i++) {
            String t = ((Element) phs.item(i)).getAttribute("type");
            for (String type : types) if (type.equals(t)) return true;
        }
        return false;
    }

    private static boolean hasIndexedContent(Document doc) {
        // Indexed placeholders without an explicit type (not covered by hasPlaceholderType).
        NodeList phs = phElements(doc);
        for (int i = 0; i < phs.getLength(); i++) {
            Element ph = (Element) phs.item(i);
            if (ph.hasAttribute("idx") && !ph.hasAttribute("type")) return true;
        }
        return false;
    }

    private static int countPlaceholderType(Document doc, String... types) {
        int count = 0;
        NodeList phs = phElements(doc);
        for (int i = 0; i < phs.getLength(); i++) {
            String t = ((Element) phs.item(i)).getAttribute("type");
            for (String type : types) if (type.equals(t)) { count++; break; }
        }
        return count;
    }

    private static int countIndexedContent(Document doc) {
        int count = 0;
        NodeList phs = phElements(doc);
        for (int i = 0; i < phs.getLength(); i++) {
            Element ph = (Element) phs.item(i);
            if (ph.hasAttribute("idx") && !ph.hasAttribute("type")) count++;
        }
        return count;
    }

    private static String detectTitleType(Document doc) {
        // ctrTitle takes priority over title when both are present.
        NodeList phs = phElements(doc);
        boolean hasTitle = false;
        for (int i = 0; i < phs.getLength(); i++) {
            String t = ((Element) phs.item(i)).getAttribute("type");
            if ("ctrTitle".equals(t)) return "ctrTitle";
            if ("title".equals(t)) hasTitle = true;
        }
        return hasTitle ? "title" : null;
    }

    private static List<PlaceholderGeometry> parsePlaceholderGeometries(Document doc, String layoutId) {
        List<PlaceholderGeometry> geometries = new ArrayList<>();
        try {
            NodeList sps = doc.getElementsByTagNameNS(XMLConstants.PRESENTATION_NS, "sp");
            for (int i = 0; i < sps.getLength(); i++) {
                Element sp = (Element) sps.item(i);
                NodeList phNodes = sp.getElementsByTagNameNS(XMLConstants.PRESENTATION_NS, "ph");
                if (phNodes.getLength() == 0) continue; // not a placeholder shape
                Element ph = (Element) phNodes.item(0);
                String type = ph.getAttribute("type");
                String idx = ph.getAttribute("idx");

                NodeList xfrmNodes = sp.getElementsByTagNameNS(XMLConstants.DRAWING_NS, "xfrm");
                if (xfrmNodes.getLength() == 0) continue;
                Element xfrm = (Element) xfrmNodes.item(0);
                NodeList offNodes = xfrm.getElementsByTagNameNS(XMLConstants.DRAWING_NS, "off");
                NodeList extNodes = xfrm.getElementsByTagNameNS(XMLConstants.DRAWING_NS, "ext");
                if (offNodes.getLength() == 0 || extNodes.getLength() == 0) continue;

                Element off = (Element) offNodes.item(0);
                Element ext = (Element) extNodes.item(0);
                long x = Long.parseLong(off.getAttribute("x"));
                long y = Long.parseLong(off.getAttribute("y"));
                long cx = Long.parseLong(ext.getAttribute("cx"));
                long cy = Long.parseLong(ext.getAttribute("cy"));

                // Store by type key for getPlaceholderGeometryByType() lookups
                if (type != null && !type.isEmpty()) {
                    geometries.add(new PlaceholderGeometry(x, y, cx, cy, "type:" + type, type));
                }
                // Also store by idx for getPlaceholderGeometryByIndex() lookups
                if (idx != null && !idx.isEmpty()) {
                    geometries.add(new PlaceholderGeometry(x, y, cx, cy, idx, type));
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to parse placeholder geometries for {}: {}", layoutId, e.getMessage());
        }
        return geometries;
    }

    private static String generateDescription(boolean hasTitle, boolean hasContent, boolean hasSubtitle) {
        List<String> parts = new ArrayList<>();
        if (hasTitle) parts.add("title");
        if (hasSubtitle) parts.add("subtitle");
        if (hasContent) parts.add("content");
        if (parts.isEmpty()) parts.add("blank");
        return String.join(" + ", parts);
    }
}
