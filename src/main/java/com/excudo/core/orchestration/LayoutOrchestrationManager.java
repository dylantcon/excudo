package com.excudo.core.orchestration;

import com.excudo.core.results.ExecutionResult;
import com.excudo.core.model.PPTXDocument;
import com.excudo.core.model.PresentationXmlHelper;
import com.excudo.core.model.LayoutManager;
import com.excudo.core.model.LayoutInfo;
import com.excudo.core.themes.PlaceholderDefinition;
import com.excudo.core.themes.LayoutDefinition;
import com.excudo.core.utils.ComponentLogger;
import com.excudo.core.utils.Logger;
import com.excudo.core.utils.XMLConstants;
import com.excudo.core.utils.XMLFactoryProvider;
import com.excudo.xml.writers.themes.ThemeXMLGenerator;
import org.w3c.dom.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Slide layout CRUD on the in-memory {@link PPTXDocument}.
 *
 * Layout-level: addLayout, duplicateLayout, deleteLayout, renameLayout
 * Placeholder-level: addPlaceholder, removePlaceholder, editPlaceholder
 * Query: getLayoutsInUse, getNextLayoutNumber
 *
 * <p>All reads and writes go through document parts (never the filesystem),
 * so these work for scaffolded and loaded decks alike, and every change
 * reaches inheriting slides via the document's style-part notifications.
 * Multi-master aware: a layout belongs to the master its rels point at.
 */
public class LayoutOrchestrationManager {

    private static final ComponentLogger logger = Logger.getLogger(LayoutOrchestrationManager.class);

    private static final String PNS = XMLConstants.PRESENTATION_NS;
    private static final String ANS = XMLConstants.DRAWING_NS;
    private static final String PKG_REL = XMLConstants.PACKAGE_RELATIONSHIPS_NS;
    /** sldMasterId / sldLayoutId values must be at least 2^31 (ECMA-376 19.2.1.33). */
    private static final long BASE_LAYOUT_ID = 2147483649L;

    private static final Pattern LAYOUT_PART = Pattern.compile("^ppt/slideLayouts/slideLayout(\\d+)\\.xml$");
    private static final Pattern MASTER_PART = Pattern.compile("^ppt/slideMasters/slideMaster(\\d+)\\.xml$");
    private static final Pattern LAYOUT_TARGET = Pattern.compile("slideLayout(\\d+)\\.xml$");
    private static final Pattern MASTER_TARGET = Pattern.compile("slideMaster(\\d+)\\.xml$");

    private final OrchestrationContext context;

    public LayoutOrchestrationManager(OrchestrationContext context) {
        if (context == null) {
            throw new IllegalArgumentException("OrchestrationContext cannot be null");
        }
        this.context = context;
    }

    private PPTXDocument doc() {
        return context.getDocument();
    }

    // ========== LAYOUT-LEVEL OPERATIONS ==========

    /**
     * Add a new layout from a LayoutDefinition, attached to slide master 1.
     *
     * @return The layoutId of the created layout (e.g., "slideLayout11")
     */
    public ExecutionResult<String> addLayout(LayoutDefinition layout) {
        return addLayout(layout, firstMasterNumber());
    }

    /**
     * Add a new layout from a LayoutDefinition, attached to the given master.
     */
    public ExecutionResult<String> addLayout(LayoutDefinition layout, int masterNumber) {
        try {
            if (doc().getXmlPart(masterPart(masterNumber)) == null) {
                return ExecutionResult.failure("AddLayout", "Slide master not found: slideMaster" + masterNumber);
            }
            int nextNum = getNextLayoutNumber();
            String layoutId = "slideLayout" + nextNum;

            ThemeXMLGenerator generator = new ThemeXMLGenerator();
            doc().putXmlPart(layoutPart(nextNum), generator.generateSlideLayoutXML(null, layout));
            doc().putXmlPart(layoutRelsPart(nextNum), generator.generateSlideLayoutRels(masterNumber));

            addLayoutToMaster(masterNumber, nextNum);
            addLayoutContentType(nextNum);

            logger.info("Created layout {} with {} placeholders", layoutId, layout.getPlaceholderCount());
            return ExecutionResult.success("AddLayout", layoutId);
        } catch (Exception e) {
            logger.error("Failed to add layout: {}", e.getMessage());
            return ExecutionResult.failure("AddLayout", "Failed to add layout: " + e.getMessage(), e);
        }
    }

    /**
     * Duplicate an existing layout with a new name. The copy belongs to the
     * same master as the source and keeps its relationships (images, etc.).
     *
     * @return The layoutId of the new layout
     */
    public ExecutionResult<String> duplicateLayout(String sourceLayoutId, String newName) {
        try {
            int sourceNum = extractLayoutNumber(sourceLayoutId);
            Document sourceDoc = sourceNum > 0 ? doc().getXmlPart(layoutPart(sourceNum)) : null;
            if (sourceDoc == null) {
                return ExecutionResult.failure("DuplicateLayout", "Source layout not found: " + sourceLayoutId);
            }

            int nextNum = getNextLayoutNumber();
            String newLayoutId = "slideLayout" + nextNum;
            int masterNumber = ownerMaster(sourceNum);

            Document clonedDoc = cloneDocument(sourceDoc);
            setLayoutName(clonedDoc, newName);
            doc().putXmlPart(layoutPart(nextNum), clonedDoc);

            Document sourceRels = doc().getXmlPart(layoutRelsPart(sourceNum));
            doc().putXmlPart(layoutRelsPart(nextNum), sourceRels != null
                ? cloneDocument(sourceRels)
                : new ThemeXMLGenerator().generateSlideLayoutRels(masterNumber));

            addLayoutToMaster(masterNumber, nextNum);
            addLayoutContentType(nextNum);

            logger.info("Duplicated {} as {} (\"{}\")", sourceLayoutId, newLayoutId, newName);
            return ExecutionResult.success("DuplicateLayout", newLayoutId);
        } catch (Exception e) {
            logger.error("Failed to duplicate layout: {}", e.getMessage());
            return ExecutionResult.failure("DuplicateLayout", "Failed to duplicate layout: " + e.getMessage(), e);
        }
    }

    /**
     * Delete a layout. Refused while any slide uses it, or if it is its
     * master's last layout (both as in PowerPoint). Later layouts are
     * renumbered to keep numbering contiguous.
     */
    public ExecutionResult<Void> deleteLayout(String layoutId) {
        try {
            int layoutNum = extractLayoutNumber(layoutId);
            if (layoutNum <= 0 || doc().getXmlPart(layoutPart(layoutNum)) == null) {
                return ExecutionResult.failure("DeleteLayout", "Layout not found: " + layoutId);
            }

            List<Integer> referencingSlides = getLayoutsInUse().get("slideLayout" + layoutNum);
            if (referencingSlides != null && !referencingSlides.isEmpty()) {
                return ExecutionResult.failure("DeleteLayout",
                    "Cannot delete " + layoutId + " -- referenced by slides: " + referencingSlides);
            }
            int master = ownerMaster(layoutNum);
            if (layoutsOfMaster(master).size() <= 1) {
                return ExecutionResult.failure("DeleteLayout",
                    "Cannot delete " + layoutId + " -- it is the last layout of slideMaster" + master);
            }

            int maxLayout = getNextLayoutNumber() - 1;

            removeLayoutFromMasters(layoutNum);
            doc().removeXmlPart(layoutPart(layoutNum));
            doc().removeXmlPart(layoutRelsPart(layoutNum));

            if (layoutNum < maxLayout) {
                renumberLayoutsAfterDelete(layoutNum, maxLayout);
            } else {
                removeLayoutContentType(layoutNum);
            }

            logger.info("Deleted layout {} and renumbered {} subsequent layouts",
                        layoutId, Math.max(0, maxLayout - layoutNum));
            return ExecutionResult.success("DeleteLayout", null);
        } catch (Exception e) {
            logger.error("Failed to delete layout: {}", e.getMessage());
            return ExecutionResult.failure("DeleteLayout", "Failed to delete layout: " + e.getMessage(), e);
        }
    }

    /**
     * Rename a layout's display name.
     */
    public ExecutionResult<Void> renameLayout(String layoutId, String newName) {
        try {
            int layoutNum = extractLayoutNumber(layoutId);
            Document layoutDoc = layoutNum > 0 ? readLayoutDocument(layoutNum) : null;
            if (layoutDoc == null) {
                return ExecutionResult.failure("RenameLayout", "Layout not found: " + layoutId);
            }

            setLayoutName(layoutDoc, newName);
            writeLayoutDocument(layoutNum, layoutDoc);

            logger.info("Renamed {} to \"{}\"", layoutId, newName);
            return ExecutionResult.success("RenameLayout", null);
        } catch (Exception e) {
            logger.error("Failed to rename layout: {}", e.getMessage());
            return ExecutionResult.failure("RenameLayout", "Failed to rename layout: " + e.getMessage(), e);
        }
    }

    // ========== PLACEHOLDER-LEVEL OPERATIONS ==========

    /**
     * Add a placeholder to an existing layout.
     */
    public ExecutionResult<Void> addPlaceholder(String layoutId, PlaceholderDefinition ph) {
        try {
            int layoutNum = extractLayoutNumber(layoutId);
            Document layoutDoc = layoutNum > 0 ? readLayoutDocument(layoutNum) : null;
            if (layoutDoc == null) {
                return ExecutionResult.failure("AddPlaceholder", "Layout not found: " + layoutId);
            }

            Element spTree = findSpTree(layoutDoc);
            if (spTree == null) {
                return ExecutionResult.failure("AddPlaceholder", "No spTree found in layout " + layoutId);
            }
            int idx = ph.getIdx() != null ? ph.getIdx() : 0;
            if (findShapeByPlaceholderIdx(spTree, idx) != null) {
                return ExecutionResult.failure("AddPlaceholder",
                    layoutId + " already has a placeholder with idx=" + idx);
            }

            int nextSpid = getMaxSpidInLayout(spTree) + 1;
            spTree.appendChild(createPlaceholderShape(layoutDoc, nextSpid, ph));
            writeLayoutDocument(layoutNum, layoutDoc);

            logger.info("Added placeholder type={} idx={} to {}", ph.getType(), ph.getIdx(), layoutId);
            return ExecutionResult.success("AddPlaceholder", null);
        } catch (Exception e) {
            logger.error("Failed to add placeholder: {}", e.getMessage());
            return ExecutionResult.failure("AddPlaceholder", "Failed to add placeholder: " + e.getMessage(), e);
        }
    }

    /**
     * Remove a placeholder from a layout by its idx.
     */
    public ExecutionResult<Void> removePlaceholder(String layoutId, int idx) {
        try {
            int layoutNum = extractLayoutNumber(layoutId);
            Document layoutDoc = layoutNum > 0 ? readLayoutDocument(layoutNum) : null;
            if (layoutDoc == null) {
                return ExecutionResult.failure("RemovePlaceholder", "Layout not found: " + layoutId);
            }

            Element spTree = findSpTree(layoutDoc);
            if (spTree == null) {
                return ExecutionResult.failure("RemovePlaceholder", "No spTree found in layout " + layoutId);
            }
            Element targetShape = findShapeByPlaceholderIdx(spTree, idx);
            if (targetShape == null) {
                return ExecutionResult.failure("RemovePlaceholder",
                    "No placeholder with idx=" + idx + " found in " + layoutId);
            }

            targetShape.getParentNode().removeChild(targetShape);
            writeLayoutDocument(layoutNum, layoutDoc);

            logger.info("Removed placeholder idx={} from {}", idx, layoutId);
            return ExecutionResult.success("RemovePlaceholder", null);
        } catch (Exception e) {
            logger.error("Failed to remove placeholder: {}", e.getMessage());
            return ExecutionResult.failure("RemovePlaceholder", "Failed to remove placeholder: " + e.getMessage(), e);
        }
    }

    /**
     * Edit a placeholder's properties (type, geometry) by idx.
     */
    public ExecutionResult<Void> editPlaceholder(String layoutId, int idx, PlaceholderDefinition updated) {
        try {
            int layoutNum = extractLayoutNumber(layoutId);
            Document layoutDoc = layoutNum > 0 ? readLayoutDocument(layoutNum) : null;
            if (layoutDoc == null) {
                return ExecutionResult.failure("EditPlaceholder", "Layout not found: " + layoutId);
            }

            Element spTree = findSpTree(layoutDoc);
            if (spTree == null) {
                return ExecutionResult.failure("EditPlaceholder", "No spTree found in layout " + layoutId);
            }
            Element targetShape = findShapeByPlaceholderIdx(spTree, idx);
            if (targetShape == null) {
                return ExecutionResult.failure("EditPlaceholder",
                    "No placeholder with idx=" + idx + " found in " + layoutId);
            }

            Element ph = findDescendant(targetShape, PNS, "ph");
            if (ph != null && updated.getType() != null) {
                ph.setAttribute("type", updated.getType());
            }

            Element xfrm = findDescendant(targetShape, ANS, "xfrm");
            if (xfrm != null) {
                Element off = findDescendant(xfrm, ANS, "off");
                if (off != null) {
                    off.setAttribute("x", String.valueOf(updated.getX()));
                    off.setAttribute("y", String.valueOf(updated.getY()));
                }
                Element ext = findDescendant(xfrm, ANS, "ext");
                if (ext != null) {
                    ext.setAttribute("cx", String.valueOf(updated.getCx()));
                    ext.setAttribute("cy", String.valueOf(updated.getCy()));
                }
            }

            writeLayoutDocument(layoutNum, layoutDoc);

            logger.info("Edited placeholder idx={} in {}", idx, layoutId);
            return ExecutionResult.success("EditPlaceholder", null);
        } catch (Exception e) {
            logger.error("Failed to edit placeholder: {}", e.getMessage());
            return ExecutionResult.failure("EditPlaceholder", "Failed to edit placeholder: " + e.getMessage(), e);
        }
    }

    // ========== QUERY OPERATIONS ==========

    /**
     * Which slides use which layouts, from the slides' rels.
     * @return Map of layoutId -> list of slide numbers
     */
    public Map<String, List<Integer>> getLayoutsInUse() {
        Map<String, List<Integer>> result = new LinkedHashMap<>();
        try {
            for (int slide : doc().getSlideNumbers()) {
                Document rels = doc().getXmlPart("ppt/slides/_rels/slide" + slide + ".xml.rels");
                if (rels == null) continue;
                for (Element rel : relationships(rels)) {
                    if (!XMLConstants.RELATIONSHIP_TYPE_SLIDE_LAYOUT.equals(rel.getAttribute("Type"))) continue;
                    int n = targetNumber(LAYOUT_TARGET, rel.getAttribute("Target"));
                    if (n > 0) result.computeIfAbsent("slideLayout" + n, k -> new ArrayList<>()).add(slide);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to scan layout usage: {}", e.getMessage());
        }
        return result;
    }

    /**
     * The next unused layout number (one past the highest existing layout part).
     */
    public int getNextLayoutNumber() {
        TreeSet<Integer> numbers = layoutNumbers();
        return numbers.isEmpty() ? 1 : numbers.last() + 1;
    }

    /**
     * Predict what SPIDs will be allocated when creating a slide with the given layout.
     */
    public List<Integer> predictAllocatedSpids(String layoutId, int slideNumber) {
        List<Integer> predictedSpids = new ArrayList<>();

        try {
            predictedSpids.add(1);

            if (layoutId != null && !layoutId.trim().isEmpty()) {
                LayoutManager layoutManager = context.getLayoutManager();
                LayoutInfo layoutInfo = layoutManager != null ? layoutManager.getLayoutInfo(layoutId) : null;

                if (layoutInfo != null) {
                    if (layoutInfo.hasTitlePlaceholder()) {
                        predictedSpids.add(2);
                    }
                    if (layoutInfo.hasContentPlaceholder()) {
                        int contentCount = Math.max(1, layoutInfo.getContentPlaceholderCount());
                        for (int i = 0; i < contentCount; i++) {
                            predictedSpids.add(3 + i);
                        }
                    }
                }
            }

            return predictedSpids;
        } catch (Exception e) {
            logger.warn("Failed to predict SPIDs, falling back to minimal prediction: {}", e.getMessage());
            return List.of(1);
        }
    }

    /**
     * Get the first available layout ID.
     */
    public String getFirstAvailableLayoutId() {
        TreeSet<Integer> numbers = layoutNumbers();
        if (numbers.isEmpty()) {
            logger.warn("No layout found, falling back to null (will use insertBlankSlide)");
            return null;
        }
        return "slideLayout" + numbers.first();
    }

    /**
     * First layout whose p:sldLayout/@type matches the given OOXML type
     * (e.g. "obj", "title"), or null.
     */
    public String findLayoutByType(String ooxmlType) {
        if (ooxmlType == null) return null;
        for (int n : layoutNumbers()) {
            Document layoutDoc = doc().getXmlPart(layoutPart(n));
            if (layoutDoc != null && ooxmlType.equals(layoutDoc.getDocumentElement().getAttribute("type"))) {
                return "slideLayout" + n;
            }
        }
        return null;
    }

    // ========== INTERNAL: MASTER RELS ==========

    /**
     * Attach layout {@code layoutNum} to a master: a new relationship in the
     * master's rels and a matching sldLayoutId entry in its sldLayoutIdLst.
     */
    private void addLayoutToMaster(int masterNum, int layoutNum) throws Exception {
        String relsName = masterRelsPart(masterNum);
        Document relsDoc = doc().getXmlPart(relsName);
        if (relsDoc == null) {
            throw new IllegalStateException("Missing " + relsName);
        }
        String rId = nextRelationshipId(relsDoc);
        Element newRel = relsDoc.createElementNS(PKG_REL, "Relationship");
        newRel.setAttribute("Id", rId);
        newRel.setAttribute("Type", XMLConstants.RELATIONSHIP_TYPE_SLIDE_LAYOUT);
        newRel.setAttribute("Target", "../slideLayouts/slideLayout" + layoutNum + ".xml");
        relsDoc.getDocumentElement().appendChild(newRel);
        doc().putXmlPart(relsName, relsDoc);

        Document masterDoc = doc().getXmlPart(masterPart(masterNum));
        PresentationXmlHelper.addLayoutIdToMaster(masterDoc, rId, nextLayoutIdValue());
        doc().putXmlPart(masterPart(masterNum), masterDoc);
    }

    /**
     * Detach layout {@code layoutNum} from whichever master(s) reference it.
     */
    private void removeLayoutFromMasters(int layoutNum) {
        for (int master : masterNumbers()) {
            Document relsDoc = doc().getXmlPart(masterRelsPart(master));
            if (relsDoc == null) continue;
            for (Element rel : relationships(relsDoc)) {
                if (!XMLConstants.RELATIONSHIP_TYPE_SLIDE_LAYOUT.equals(rel.getAttribute("Type"))) continue;
                if (targetNumber(LAYOUT_TARGET, rel.getAttribute("Target")) != layoutNum) continue;
                String rId = rel.getAttribute("Id");
                rel.getParentNode().removeChild(rel);
                doc().putXmlPart(masterRelsPart(master), relsDoc);

                Document masterDoc = doc().getXmlPart(masterPart(master));
                if (masterDoc != null && PresentationXmlHelper.removeLayoutIdFromMaster(masterDoc, rId)) {
                    doc().putXmlPart(masterPart(master), masterDoc);
                }
            }
        }
    }

    private void addLayoutContentType(int layoutNum) {
        Document ctDoc = doc().getContentTypesDoc();
        if (ctDoc != null) {
            PresentationXmlHelper.addLayoutContentType(ctDoc, layoutNum);
            doc().markContentTypesDirty();
        }
    }

    private void removeLayoutContentType(int layoutNum) {
        Document ctDoc = doc().getContentTypesDoc();
        if (ctDoc != null) {
            PresentationXmlHelper.removeLayoutContentType(ctDoc, layoutNum);
            doc().markContentTypesDirty();
        }
    }

    // ========== INTERNAL: DELETE RENUMBER CASCADE ==========

    /**
     * Shift layouts {@code deletedNum+1..maxNum} down by one: move the parts,
     * then retarget every reference (all masters' rels and all slides' rels).
     * Relationship ids are untouched, so sldLayoutIdLst entries stay valid.
     */
    private void renumberLayoutsAfterDelete(int deletedNum, int maxNum) {
        for (int i = deletedNum + 1; i <= maxNum; i++) {
            Document layoutDoc = doc().getXmlPart(layoutPart(i));
            if (layoutDoc != null) doc().putXmlPart(layoutPart(i - 1), layoutDoc);
            Document relsDoc = doc().getXmlPart(layoutRelsPart(i));
            if (relsDoc != null) {
                doc().putXmlPart(layoutRelsPart(i - 1), relsDoc);
            } else {
                doc().removeXmlPart(layoutRelsPart(i - 1));
            }
        }
        doc().removeXmlPart(layoutPart(maxNum));
        doc().removeXmlPart(layoutRelsPart(maxNum));

        List<String> referencingParts = new ArrayList<>();
        for (int master : masterNumbers()) referencingParts.add(masterRelsPart(master));
        for (int slide : doc().getSlideNumbers()) referencingParts.add("ppt/slides/_rels/slide" + slide + ".xml.rels");

        for (String partName : referencingParts) {
            Document relsDoc = doc().getXmlPart(partName);
            if (relsDoc == null) continue;
            boolean modified = false;
            for (Element rel : relationships(relsDoc)) {
                if (!XMLConstants.RELATIONSHIP_TYPE_SLIDE_LAYOUT.equals(rel.getAttribute("Type"))) continue;
                String target = rel.getAttribute("Target");
                Matcher m = LAYOUT_TARGET.matcher(target);
                if (!m.find()) continue;
                int refNum = Integer.parseInt(m.group(1));
                if (refNum > deletedNum) {
                    rel.setAttribute("Target", target.substring(0, m.start()) + "slideLayout" + (refNum - 1) + ".xml");
                    modified = true;
                }
            }
            if (modified) doc().markDirty(partName);
        }

        // Numbering is contiguous again: the highest number is the one that vanished.
        removeLayoutContentType(maxNum);
    }

    // ========== INTERNAL: PART DISCOVERY ==========

    private TreeSet<Integer> layoutNumbers() {
        return partNumbers("ppt/slideLayouts/slideLayout", LAYOUT_PART);
    }

    private TreeSet<Integer> masterNumbers() {
        return partNumbers("ppt/slideMasters/slideMaster", MASTER_PART);
    }

    private TreeSet<Integer> partNumbers(String prefix, Pattern pattern) {
        TreeSet<Integer> numbers = new TreeSet<>();
        for (String name : doc().getPartNamesByPrefix(prefix)) {
            Matcher m = pattern.matcher(name);
            if (m.matches()) numbers.add(Integer.parseInt(m.group(1)));
        }
        return numbers;
    }

    private int firstMasterNumber() {
        TreeSet<Integer> masters = masterNumbers();
        return masters.isEmpty() ? 1 : masters.first();
    }

    /**
     * The master a layout belongs to: the slideMaster target in the layout's
     * rels, else whichever master's rels reference it, else the first master.
     */
    int ownerMaster(int layoutNum) {
        Document layoutRels = doc().getXmlPart(layoutRelsPart(layoutNum));
        if (layoutRels != null) {
            for (Element rel : relationships(layoutRels)) {
                if (XMLConstants.RELATIONSHIP_TYPE_SLIDE_MASTER.equals(rel.getAttribute("Type"))) {
                    int n = targetNumber(MASTER_TARGET, rel.getAttribute("Target"));
                    if (n > 0) return n;
                }
            }
        }
        for (int master : masterNumbers()) {
            if (layoutsOfMaster(master).contains(layoutNum)) return master;
        }
        return firstMasterNumber();
    }

    private List<Integer> layoutsOfMaster(int masterNum) {
        List<Integer> layouts = new ArrayList<>();
        Document relsDoc = doc().getXmlPart(masterRelsPart(masterNum));
        if (relsDoc == null) return layouts;
        for (Element rel : relationships(relsDoc)) {
            if (XMLConstants.RELATIONSHIP_TYPE_SLIDE_LAYOUT.equals(rel.getAttribute("Type"))) {
                int n = targetNumber(LAYOUT_TARGET, rel.getAttribute("Target"));
                if (n > 0) layouts.add(n);
            }
        }
        return layouts;
    }

    /**
     * Next sldLayoutId value: one past the highest id used by any layout or
     * master entry, since those ids share one space.
     */
    private long nextLayoutIdValue() {
        long max = BASE_LAYOUT_ID - 1;
        for (int master : masterNumbers()) {
            Document masterDoc = doc().getXmlPart(masterPart(master));
            if (masterDoc != null) max = Math.max(max, maxIdAttribute(masterDoc, "sldLayoutId"));
        }
        Document presentation = doc().getPresentationXml();
        if (presentation != null) max = Math.max(max, maxIdAttribute(presentation, "sldMasterId"));
        return max + 1;
    }

    private static long maxIdAttribute(Document d, String localName) {
        long max = 0;
        NodeList nodes = d.getElementsByTagNameNS(PNS, localName);
        for (int i = 0; i < nodes.getLength(); i++) {
            String id = ((Element) nodes.item(i)).getAttribute("id");
            try {
                if (!id.isEmpty()) max = Math.max(max, Long.parseLong(id));
            } catch (NumberFormatException ignored) {
                // Malformed id: not ours to fix here
            }
        }
        return max;
    }

    private static String nextRelationshipId(Document relsDoc) {
        int max = 0;
        for (Element rel : relationships(relsDoc)) {
            Matcher m = Pattern.compile("^rId(\\d+)$").matcher(rel.getAttribute("Id"));
            if (m.matches()) max = Math.max(max, Integer.parseInt(m.group(1)));
        }
        return "rId" + (max + 1);
    }

    private static List<Element> relationships(Document relsDoc) {
        List<Element> out = new ArrayList<>();
        NodeList nodes = relsDoc.getElementsByTagNameNS(PKG_REL, "Relationship");
        for (int i = 0; i < nodes.getLength(); i++) out.add((Element) nodes.item(i));
        return out;
    }

    private static int targetNumber(Pattern pattern, String target) {
        Matcher m = pattern.matcher(target);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    // ========== INTERNAL: DOM HELPERS ==========

    private static Document cloneDocument(Document source) throws Exception {
        Document copy = XMLFactoryProvider.createDocument();
        copy.appendChild(copy.importNode(source.getDocumentElement(), true));
        return copy;
    }

    private static void setLayoutName(Document layoutDoc, String name) {
        Element root = layoutDoc.getDocumentElement();
        root.setAttribute("matchingName", name);
        NodeList cSldList = root.getElementsByTagNameNS(PNS, "cSld");
        if (cSldList.getLength() > 0) {
            ((Element) cSldList.item(0)).setAttribute("name", name);
        }
    }

    private Element findSpTree(Document d) {
        NodeList spTrees = d.getElementsByTagNameNS(PNS, "spTree");
        return spTrees.getLength() > 0 ? (Element) spTrees.item(0) : null;
    }

    private int getMaxSpidInLayout(Element spTree) {
        int max = 1; // group shape is always 1
        NodeList cNvPrs = spTree.getElementsByTagNameNS(PNS, "cNvPr");
        for (int i = 0; i < cNvPrs.getLength(); i++) {
            String idStr = ((Element) cNvPrs.item(i)).getAttribute("id");
            try {
                if (!idStr.isEmpty()) max = Math.max(max, Integer.parseInt(idStr));
            } catch (NumberFormatException ignored) {
                // Non-numeric id: skip
            }
        }
        return max;
    }

    private Element findShapeByPlaceholderIdx(Element spTree, int idx) {
        NodeList shapes = spTree.getElementsByTagNameNS(PNS, "sp");
        for (int i = 0; i < shapes.getLength(); i++) {
            Element sp = (Element) shapes.item(i);
            Element ph = findDescendant(sp, PNS, "ph");
            if (ph != null) {
                String idxStr = ph.getAttribute("idx");
                if (!idxStr.isEmpty() && Integer.parseInt(idxStr) == idx) {
                    return sp;
                }
                // Title placeholder has no idx (implicit 0)
                if (idxStr.isEmpty() && idx == 0) {
                    return sp;
                }
            }
        }
        return null;
    }

    private Element findDescendant(Element parent, String ns, String localName) {
        NodeList list = parent.getElementsByTagNameNS(ns, localName);
        return list.getLength() > 0 ? (Element) list.item(0) : null;
    }

    /**
     * Create a placeholder shape DOM element for a layout.
     */
    private Element createPlaceholderShape(Document d, int spid, PlaceholderDefinition ph) {
        Element sp = d.createElementNS(PNS, "p:sp");

        Element nvSpPr = d.createElementNS(PNS, "p:nvSpPr");
        sp.appendChild(nvSpPr);

        Element cNvPr = d.createElementNS(PNS, "p:cNvPr");
        cNvPr.setAttribute("id", String.valueOf(spid));
        cNvPr.setAttribute("name", getShapeName(ph.getType(), spid));
        nvSpPr.appendChild(cNvPr);

        Element cNvSpPr = d.createElementNS(PNS, "p:cNvSpPr");
        Element spLocks = d.createElementNS(ANS, "a:spLocks");
        spLocks.setAttribute("noGrp", "1");
        cNvSpPr.appendChild(spLocks);
        nvSpPr.appendChild(cNvSpPr);

        Element nvPr = d.createElementNS(PNS, "p:nvPr");
        Element phEl = d.createElementNS(PNS, "p:ph");
        if (ph.getType() != null && !ph.getType().isEmpty()) {
            // Title type has no explicit type attribute in OOXML (it's the default)
            if (!"title".equals(ph.getType()) || ph.getIdx() != null) {
                phEl.setAttribute("type", ph.getType());
            }
        }
        if (ph.getIdx() != null && ph.getIdx() > 0) {
            phEl.setAttribute("idx", String.valueOf(ph.getIdx()));
        }
        nvPr.appendChild(phEl);
        nvSpPr.appendChild(nvPr);

        Element spPr = d.createElementNS(PNS, "p:spPr");
        sp.appendChild(spPr);

        Element xfrm = d.createElementNS(ANS, "a:xfrm");
        spPr.appendChild(xfrm);

        Element off = d.createElementNS(ANS, "a:off");
        off.setAttribute("x", String.valueOf(ph.getX()));
        off.setAttribute("y", String.valueOf(ph.getY()));
        xfrm.appendChild(off);

        Element ext = d.createElementNS(ANS, "a:ext");
        ext.setAttribute("cx", String.valueOf(ph.getCx()));
        ext.setAttribute("cy", String.valueOf(ph.getCy()));
        xfrm.appendChild(ext);

        return sp;
    }

    private String getShapeName(String type, int spid) {
        if (type == null) return "Placeholder " + spid;
        return switch (type) {
            case "title", "ctrTitle" -> "Title " + spid;
            case "subTitle" -> "Subtitle " + spid;
            case "body", "obj" -> "Content Placeholder " + spid;
            case "pic" -> "Picture Placeholder " + spid;
            case "chart" -> "Chart Placeholder " + spid;
            case "tbl" -> "Table Placeholder " + spid;
            case "dt" -> "Date Placeholder " + spid;
            case "ftr" -> "Footer Placeholder " + spid;
            case "sldNum" -> "Slide Number Placeholder " + spid;
            default -> "Placeholder " + spid;
        };
    }

    // ========== INTERNAL: PART NAMES ==========

    private static String layoutPart(int layoutNum) {
        return "ppt/slideLayouts/slideLayout" + layoutNum + ".xml";
    }

    private static String layoutRelsPart(int layoutNum) {
        return "ppt/slideLayouts/_rels/slideLayout" + layoutNum + ".xml.rels";
    }

    private static String masterPart(int masterNum) {
        return "ppt/slideMasters/slideMaster" + masterNum + ".xml";
    }

    private static String masterRelsPart(int masterNum) {
        return "ppt/slideMasters/_rels/slideMaster" + masterNum + ".xml.rels";
    }

    private Document readLayoutDocument(int layoutNum) {
        return doc().getXmlPart(layoutPart(layoutNum));
    }

    private void writeLayoutDocument(int layoutNum, Document d) {
        doc().putXmlPart(layoutPart(layoutNum), d);
    }

    /** N from "slideLayoutN", or -1 if it isn't one. */
    private static int extractLayoutNumber(String layoutId) {
        if (layoutId == null) return -1;
        Matcher m = Pattern.compile("^slideLayout(\\d+)$").matcher(layoutId.trim());
        return m.matches() ? Integer.parseInt(m.group(1)) : -1;
    }
}
