package com.excudo.core.model;

import com.excudo.core.utils.XMLConstants;
import com.excudo.core.utils.XMLFactoryProvider;
import com.excudo.core.utils.Logger;
import com.excudo.core.utils.ComponentLogger;
import com.excudo.exceptions.XMLParsingException;
import org.w3c.dom.*;

import javax.xml.xpath.*;
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

            XPath xpath = XMLFactoryProvider.createXPath();
            XPath xpathNoNs = XMLFactoryProvider.createXPathWithoutNamespace();

            // 1. Scan all .rels parts for relationships
            scanRelationships(doc, state);

            // 2. Scan all slide DOMs for SPIDs
            scanSpids(doc, slideNumbers, state, xpath);

            // 3. Resolve slide-to-layout mappings from slide .rels
            resolveSlideLayouts(doc, slideNumbers, state, xpathNoNs);

            // 4. Parse layout DOMs for capabilities (title, content, geometry)
            parseLayouts(doc, state, xpath);

            // 4b. Resolve layout -> master via each layout's .rels
            resolveLayoutMasters(doc, state, xpathNoNs);

            // 4c. Resolve master -> theme via each master's .rels
            resolveMasterThemes(doc, state, xpathNoNs);

            // 5. Scan notes .rels for notes-to-slide mappings
            scanNotes(doc, state);

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
                                   ParsedPresentationState state, XPath xpath) throws Exception {
        for (int slideNum : slideNumbers) {
            Document slideDom = doc.getSlideDocument(slideNum);
            NodeList spidNodes = (NodeList) xpath.evaluate("//p:cNvPr/@id", slideDom, XPathConstants.NODESET);

            for (int i = 0; i < spidNodes.getLength(); i++) {
                try {
                    int spid = Integer.parseInt(spidNodes.item(i).getNodeValue());
                    state.spidRegistry.put(spid, new SpidEntry(slideNum, "existing_shape"));
                } catch (NumberFormatException ignored) {}
            }
        }
    }

    // ========== SLIDE-TO-LAYOUT RESOLUTION ==========

    private static void resolveSlideLayouts(PPTXDocument doc, List<Integer> slideNumbers,
                                             ParsedPresentationState state, XPath xpath) {
        for (int slideNum : slideNumbers) {
            String relsPartName = "ppt/slides/_rels/slide" + slideNum + ".xml.rels";
            Document relsDom = doc.getXmlPart(relsPartName);
            if (relsDom == null) continue;

            try {
                NodeList rels = (NodeList) xpath.evaluate(
                    "//*[local-name()='Relationship'][@Type='" +
                    XMLConstants.RELATIONSHIP_TYPE_SLIDE_LAYOUT + "']",
                    relsDom, XPathConstants.NODESET);

                if (rels.getLength() > 0) {
                    String target = ((Element) rels.item(0)).getAttribute("Target");
                    // Target is typically "../slideLayouts/slideLayout1.xml"
                    String filename = target.substring(target.lastIndexOf('/') + 1);
                    String layoutId = filename.endsWith(".xml")
                        ? filename.substring(0, filename.length() - 4) : filename;
                    state.slideToLayoutId.put(slideNum, layoutId);
                }
            } catch (Exception e) {
                logger.warn("Failed to resolve layout for slide {}: {}", slideNum, e.getMessage());
            }
        }
    }

    // ========== LAYOUT -> MASTER RESOLUTION ==========

    /**
     * Resolve each layout to its backing master via the layout's .rels file.
     * Each {@code ppt/slideLayouts/_rels/slideLayoutN.xml.rels} carries exactly
     * one slideMaster relationship. Multi-master decks point different layouts
     * at different masters; single-master decks all point at slideMaster1.
     */
    private static void resolveLayoutMasters(PPTXDocument doc, ParsedPresentationState state, XPath xpath) {
        for (String layoutId : state.layouts.keySet()) {
            String relsPartName = "ppt/slideLayouts/_rels/" + layoutId + ".xml.rels";
            Document relsDom = doc.getXmlPart(relsPartName);
            if (relsDom == null) continue;
            try {
                NodeList rels = (NodeList) xpath.evaluate(
                    "//*[local-name()='Relationship'][@Type='" +
                    XMLConstants.RELATIONSHIP_TYPE_SLIDE_MASTER + "']",
                    relsDom, XPathConstants.NODESET);
                if (rels.getLength() > 0) {
                    String target = ((Element) rels.item(0)).getAttribute("Target");
                    String masterId = idFromTarget(target);
                    if (masterId != null) state.layoutToMasterId.put(layoutId, masterId);
                }
            } catch (Exception e) {
                logger.warn("Failed to resolve master for layout {}: {}", layoutId, e.getMessage());
            }
        }
    }

    // ========== MASTER -> THEME RESOLUTION ==========

    /**
     * Resolve each distinct master to its theme part via the master's .rels
     * file. Multi-master decks typically pair each master with its own theme,
     * so color/fmtScheme resolution must follow this per-master mapping rather
     * than assuming theme1.
     */
    private static void resolveMasterThemes(PPTXDocument doc, ParsedPresentationState state, XPath xpath) {
        Set<String> masters = new HashSet<>(state.layoutToMasterId.values());
        for (String masterId : masters) {
            String relsPartName = "ppt/slideMasters/_rels/" + masterId + ".xml.rels";
            Document relsDom = doc.getXmlPart(relsPartName);
            if (relsDom == null) continue;
            try {
                NodeList rels = (NodeList) xpath.evaluate(
                    "//*[local-name()='Relationship'][@Type='" +
                    XMLConstants.RELATIONSHIP_TYPE_THEME + "']",
                    relsDom, XPathConstants.NODESET);
                if (rels.getLength() > 0) {
                    String target = ((Element) rels.item(0)).getAttribute("Target");
                    // Target is relative to ppt/slideMasters/, e.g. "../theme/theme2.xml"
                    String themePart = resolveRelativePart("ppt/slideMasters", target);
                    if (themePart != null) state.masterToThemePart.put(masterId, themePart);
                }
            } catch (Exception e) {
                logger.warn("Failed to resolve theme for master {}: {}", masterId, e.getMessage());
            }
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

    private static void parseLayouts(PPTXDocument doc, ParsedPresentationState state, XPath xpath) {
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
                String name = parseLayoutName(layoutDom, xpath);
                if (name == null || name.isEmpty()) name = layoutId;

                // Analyze placeholders
                boolean hasTitle = hasPlaceholderType(layoutDom, xpath, "title", "ctrTitle");
                boolean hasContent = hasPlaceholderType(layoutDom, xpath, "body", "obj")
                    || hasIndexedContent(layoutDom, xpath);
                boolean hasSubtitle = hasPlaceholderType(layoutDom, xpath, "subTitle");
                String titleType = detectTitleType(layoutDom, xpath);
                int contentCount = countPlaceholderType(layoutDom, xpath, "body", "obj")
                    + countIndexedContent(layoutDom, xpath);

                // Parse placeholder geometries
                List<PlaceholderGeometry> geometries = parsePlaceholderGeometries(layoutDom, xpath, layoutId);

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

    private static String parseLayoutName(Document doc, XPath xpath) {
        try {
            NodeList cSldList = doc.getElementsByTagName("p:cSld");
            if (cSldList.getLength() > 0) {
                String name = ((Element) cSldList.item(0)).getAttribute("name");
                if (!name.isEmpty()) return name;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static boolean hasPlaceholderType(Document doc, XPath xpath, String... types) {
        try {
            for (String type : types) {
                NodeList nodes = (NodeList) xpath.evaluate(
                    "//p:sp[.//p:ph[@type='" + type + "']]", doc, XPathConstants.NODESET);
                if (nodes.getLength() > 0) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static boolean hasIndexedContent(Document doc, XPath xpath) {
        try {
            // Only count indexed placeholders that don't have an explicit type (not already counted by hasPlaceholderType)
            NodeList nodes = (NodeList) xpath.evaluate(
                "//p:sp[.//p:ph[@idx and not(@type)]]",
                doc, XPathConstants.NODESET);
            return nodes.getLength() > 0;
        } catch (Exception ignored) {}
        return false;
    }

    private static int countPlaceholderType(Document doc, XPath xpath, String... types) {
        int count = 0;
        try {
            for (String type : types) {
                NodeList nodes = (NodeList) xpath.evaluate(
                    "//p:sp[.//p:ph[@type='" + type + "']]", doc, XPathConstants.NODESET);
                count += nodes.getLength();
            }
        } catch (Exception ignored) {}
        return count;
    }

    private static int countIndexedContent(Document doc, XPath xpath) {
        try {
            // Count indexed placeholders that don't have an explicit type (not already counted by countPlaceholderType)
            NodeList nodes = (NodeList) xpath.evaluate(
                "//p:sp[.//p:ph[@idx and not(@type)]]",
                doc, XPathConstants.NODESET);
            return nodes.getLength();
        } catch (Exception ignored) {}
        return 0;
    }

    private static String detectTitleType(Document doc, XPath xpath) {
        try {
            NodeList ctrTitle = (NodeList) xpath.evaluate(
                "//p:sp[.//p:ph[@type='ctrTitle']]", doc, XPathConstants.NODESET);
            if (ctrTitle.getLength() > 0) return "ctrTitle";
            NodeList title = (NodeList) xpath.evaluate(
                "//p:sp[.//p:ph[@type='title']]", doc, XPathConstants.NODESET);
            if (title.getLength() > 0) return "title";
        } catch (Exception ignored) {}
        return null;
    }

    private static List<PlaceholderGeometry> parsePlaceholderGeometries(Document doc, XPath xpath, String layoutId) {
        List<PlaceholderGeometry> geometries = new ArrayList<>();
        try {
            NodeList placeholders = (NodeList) xpath.evaluate("//p:sp[.//p:ph]", doc, XPathConstants.NODESET);

            for (int i = 0; i < placeholders.getLength(); i++) {
                Element sp = (Element) placeholders.item(i);
                Element ph = (Element) ((NodeList) xpath.evaluate(".//p:ph", sp, XPathConstants.NODESET)).item(0);
                String type = ph.getAttribute("type");
                String idx = ph.getAttribute("idx");

                // Find xfrm
                NodeList xfrmNodes = (NodeList) xpath.evaluate(".//a:xfrm", sp, XPathConstants.NODESET);
                if (xfrmNodes.getLength() > 0) {
                    Element xfrm = (Element) xfrmNodes.item(0);
                    NodeList offNodes = xfrm.getElementsByTagNameNS(XMLConstants.DRAWING_NS, "off");
                    NodeList extNodes = xfrm.getElementsByTagNameNS(XMLConstants.DRAWING_NS, "ext");

                    if (offNodes.getLength() > 0 && extNodes.getLength() > 0) {
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
