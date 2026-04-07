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
import com.excudo.core.utils.OOXMLAttributeOrder;
import com.excudo.xml.writers.themes.ThemeXMLGenerator;
import org.w3c.dom.*;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * Manages slide layout CRUD operations on the extracted PPTX directory.
 *
 * Layout-level: addLayout, duplicateLayout, deleteLayout, renameLayout
 * Placeholder-level: addPlaceholder, removePlaceholder, editPlaceholder
 * Query: getLayoutsInUse, getNextLayoutNumber
 */
public class LayoutOrchestrationManager {

    private static final ComponentLogger logger = Logger.getLogger(LayoutOrchestrationManager.class);

    private static final String PNS = XMLConstants.PRESENTATION_NS;
    private static final String ANS = XMLConstants.DRAWING_NS;
    private static final String RNS = XMLConstants.RELATIONSHIPS_NS;
    private static final String PKG_REL = XMLConstants.PACKAGE_RELATIONSHIPS_NS;
    private static final long BASE_LAYOUT_ID = 2147483649L;

    private final OrchestrationContext context;

    public LayoutOrchestrationManager(OrchestrationContext context) {
        if (context == null) {
            throw new IllegalArgumentException("OrchestrationContext cannot be null");
        }
        this.context = context;
    }

    // ========== LAYOUT-LEVEL OPERATIONS ==========

    /**
     * Add a new layout from a LayoutDefinition.
     *
     * @param layout The layout definition to create
     * @return The layoutId of the created layout (e.g., "slideLayout11")
     */
    public ExecutionResult<String> addLayout(LayoutDefinition layout) {
        try {
            int nextNum = getNextLayoutNumber();
            String layoutId = "slideLayout" + nextNum;

            // Generate layout XML via ThemeXMLGenerator
            ThemeXMLGenerator generator = new ThemeXMLGenerator();
            Document layoutDoc = generator.generateSlideLayoutXML(null, layout);

            // Write layout file
            File layoutFile = getLayoutFile(nextNum);
            layoutFile.getParentFile().mkdirs();
            OOXMLAttributeOrder.serialize(layoutDoc, layoutFile);

            // Write layout .rels file
            Document relsDoc = generator.generateSlideLayoutRels(1);
            File relsFile = getLayoutRelsFile(nextNum);
            relsFile.getParentFile().mkdirs();
            OOXMLAttributeOrder.serialize(relsDoc, relsFile);

            // Update master rels, master sldLayoutIdLst, and content types
            addLayoutToMaster(nextNum);
            addLayoutContentType(nextNum);

            logger.info("Created layout {} with {} placeholders", layoutId, layout.getPlaceholderCount());
            return ExecutionResult.success("AddLayout", layoutId);
        } catch (Exception e) {
            logger.error("Failed to add layout: {}", e.getMessage());
            return ExecutionResult.failure("AddLayout", "Failed to add layout: " + e.getMessage(), e);
        }
    }

    /**
     * Duplicate an existing layout with a new name.
     *
     * @param sourceLayoutId The source layout ID (e.g., "slideLayout2")
     * @param newName The display name for the duplicated layout
     * @return The layoutId of the new layout
     */
    public ExecutionResult<String> duplicateLayout(String sourceLayoutId, String newName) {
        try {
            int sourceNum = extractLayoutNumber(sourceLayoutId);
            File sourceFile = getLayoutFile(sourceNum);
            if (!sourceFile.exists()) {
                return ExecutionResult.failure("DuplicateLayout", "Source layout not found: " + sourceLayoutId);
            }

            int nextNum = getNextLayoutNumber();
            String newLayoutId = "slideLayout" + nextNum;

            // Parse source layout and deep-clone
            Document sourceDoc = XMLFactoryProvider.parseDocument(sourceFile);
            Document clonedDoc = XMLFactoryProvider.createDocument();
            Node imported = clonedDoc.importNode(sourceDoc.getDocumentElement(), true);
            clonedDoc.appendChild(imported);

            // Update matchingName attribute on root element
            Element root = clonedDoc.getDocumentElement();
            root.setAttribute("matchingName", newName);

            // Update cSld@name
            NodeList cSldList = root.getElementsByTagNameNS(PNS, "cSld");
            if (cSldList.getLength() > 0) {
                ((Element) cSldList.item(0)).setAttribute("name", newName);
            }

            // Write new layout file
            File newLayoutFile = getLayoutFile(nextNum);
            newLayoutFile.getParentFile().mkdirs();
            OOXMLAttributeOrder.serialize(clonedDoc, newLayoutFile);

            // Copy rels from source (back-reference to master)
            File sourceRels = getLayoutRelsFile(sourceNum);
            File newRels = getLayoutRelsFile(nextNum);
            newRels.getParentFile().mkdirs();
            if (sourceRels.exists()) {
                Files.copy(sourceRels.toPath(), newRels.toPath());
            } else {
                // Generate standard rels
                ThemeXMLGenerator generator = new ThemeXMLGenerator();
                Document relsDoc = generator.generateSlideLayoutRels(1);
                OOXMLAttributeOrder.serialize(relsDoc, newRels);
            }

            // Update master rels, master sldLayoutIdLst, and content types
            addLayoutToMaster(nextNum);
            addLayoutContentType(nextNum);

            logger.info("Duplicated {} as {} (\"{}\")", sourceLayoutId, newLayoutId, newName);
            return ExecutionResult.success("DuplicateLayout", newLayoutId);
        } catch (Exception e) {
            logger.error("Failed to duplicate layout: {}", e.getMessage());
            return ExecutionResult.failure("DuplicateLayout", "Failed to duplicate layout: " + e.getMessage(), e);
        }
    }

    /**
     * Delete a layout. Fails if any slides reference it.
     * Renumbers subsequent layouts to maintain contiguous numbering.
     *
     * @param layoutId The layout ID to delete (e.g., "slideLayout10")
     * @return Success or failure with details
     */
    public ExecutionResult<Void> deleteLayout(String layoutId) {
        try {
            int layoutNum = extractLayoutNumber(layoutId);
            File layoutFile = getLayoutFile(layoutNum);
            if (!layoutFile.exists()) {
                return ExecutionResult.failure("DeleteLayout", "Layout not found: " + layoutId);
            }

            // Check if any slides reference this layout
            Map<String, List<Integer>> usage = getLayoutsInUse();
            List<Integer> referencingSlides = usage.get(layoutId);
            if (referencingSlides != null && !referencingSlides.isEmpty()) {
                return ExecutionResult.failure("DeleteLayout",
                    "Cannot delete " + layoutId + " -- referenced by slides: " + referencingSlides);
            }

            int maxLayout = getNextLayoutNumber() - 1;

            // Remove the layout file and rels
            layoutFile.delete();
            File relsFile = getLayoutRelsFile(layoutNum);
            if (relsFile.exists()) {
                relsFile.delete();
            }

            // Remove from master rels and sldLayoutIdLst
            removeLayoutFromMaster(layoutNum);
            removeLayoutContentType(layoutNum);

            // Renumber subsequent layouts (cascade)
            if (layoutNum < maxLayout) {
                renumberLayoutsAfterDelete(layoutNum, maxLayout);
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
            File layoutFile = getLayoutFile(layoutNum);
            if (!layoutFile.exists()) {
                return ExecutionResult.failure("RenameLayout", "Layout not found: " + layoutId);
            }

            Document doc = readLayoutDocument(layoutNum);
            Element root = doc.getDocumentElement();
            root.setAttribute("matchingName", newName);

            NodeList cSldList = root.getElementsByTagNameNS(PNS, "cSld");
            if (cSldList.getLength() > 0) {
                ((Element) cSldList.item(0)).setAttribute("name", newName);
            }

            writeLayoutDocument(layoutNum, doc);

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
            File layoutFile = getLayoutFile(layoutNum);
            if (!layoutFile.exists()) {
                return ExecutionResult.failure("AddPlaceholder", "Layout not found: " + layoutId);
            }

            Document doc = readLayoutDocument(layoutNum);
            Element spTree = findSpTree(doc);
            if (spTree == null) {
                return ExecutionResult.failure("AddPlaceholder", "No spTree found in layout " + layoutId);
            }

            // Determine next SPID (scan existing shapes)
            int nextSpid = getMaxSpidInLayout(spTree) + 1;

            // Create placeholder shape element
            Element sp = createPlaceholderShape(doc, nextSpid, ph);
            spTree.appendChild(sp);

            writeLayoutDocument(layoutNum, doc);

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
            File layoutFile = getLayoutFile(layoutNum);
            if (!layoutFile.exists()) {
                return ExecutionResult.failure("RemovePlaceholder", "Layout not found: " + layoutId);
            }

            Document doc = readLayoutDocument(layoutNum);
            Element spTree = findSpTree(doc);
            if (spTree == null) {
                return ExecutionResult.failure("RemovePlaceholder", "No spTree found in layout " + layoutId);
            }

            // Find the shape with matching p:ph@idx
            Element targetShape = findShapeByPlaceholderIdx(spTree, idx);
            if (targetShape == null) {
                return ExecutionResult.failure("RemovePlaceholder",
                    "No placeholder with idx=" + idx + " found in " + layoutId);
            }

            spTree.removeChild(targetShape);
            writeLayoutDocument(layoutNum, doc);

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
            File layoutFile = getLayoutFile(layoutNum);
            if (!layoutFile.exists()) {
                return ExecutionResult.failure("EditPlaceholder", "Layout not found: " + layoutId);
            }

            Document doc = readLayoutDocument(layoutNum);
            Element spTree = findSpTree(doc);
            if (spTree == null) {
                return ExecutionResult.failure("EditPlaceholder", "No spTree found in layout " + layoutId);
            }

            Element targetShape = findShapeByPlaceholderIdx(spTree, idx);
            if (targetShape == null) {
                return ExecutionResult.failure("EditPlaceholder",
                    "No placeholder with idx=" + idx + " found in " + layoutId);
            }

            // Update p:ph@type if changed
            Element ph = findDescendant(targetShape, PNS, "ph");
            if (ph != null && updated.getType() != null) {
                ph.setAttribute("type", updated.getType());
            }

            // Update a:xfrm geometry
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

            writeLayoutDocument(layoutNum, doc);

            logger.info("Edited placeholder idx={} in {}", idx, layoutId);
            return ExecutionResult.success("EditPlaceholder", null);
        } catch (Exception e) {
            logger.error("Failed to edit placeholder: {}", e.getMessage());
            return ExecutionResult.failure("EditPlaceholder", "Failed to edit placeholder: " + e.getMessage(), e);
        }
    }

    // ========== QUERY OPERATIONS ==========

    /**
     * Scan all slide rels to find which slides use which layouts.
     * @return Map of layoutId -> list of slide numbers
     */
    public Map<String, List<Integer>> getLayoutsInUse() {
        Map<String, List<Integer>> result = new LinkedHashMap<>();

        try {
            File slidesRelsDir = new File(context.getDocument().getExtractedDir(), "ppt/slides/_rels");
            if (!slidesRelsDir.exists()) return result;

            File[] relsFiles = slidesRelsDir.listFiles((dir, name) ->
                name.matches("slide\\d+\\.xml\\.rels"));
            if (relsFiles == null) return result;

            for (File relsFile : relsFiles) {
                // Extract slide number from filename
                String fileName = relsFile.getName();
                int slideNum = Integer.parseInt(
                    fileName.replaceAll("slide(\\d+)\\.xml\\.rels", "$1"));

                Document doc = XMLFactoryProvider.parseDocument(relsFile);
                NodeList rels = doc.getElementsByTagNameNS(PKG_REL, "Relationship");
                for (int i = 0; i < rels.getLength(); i++) {
                    Element rel = (Element) rels.item(i);
                    String type = rel.getAttribute("Type");
                    if (XMLConstants.RELATIONSHIP_TYPE_SLIDE_LAYOUT.equals(type)) {
                        String target = rel.getAttribute("Target");
                        // ../slideLayouts/slideLayout2.xml -> slideLayout2
                        String layoutId = target.replaceAll(".*/(slideLayout\\d+)\\.xml", "$1");
                        result.computeIfAbsent(layoutId, k -> new ArrayList<>()).add(slideNum);
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to scan layout usage: {}", e.getMessage());
        }

        return result;
    }

    /**
     * Get the next available layout number by scanning existing layout files.
     */
    public int getNextLayoutNumber() {
        File layoutsDir = new File(context.getDocument().getExtractedDir(), "ppt/slideLayouts");
        if (!layoutsDir.exists()) return 1;

        int max = 0;
        File[] files = layoutsDir.listFiles((dir, name) -> name.matches("slideLayout\\d+\\.xml"));
        if (files != null) {
            for (File f : files) {
                int num = Integer.parseInt(
                    f.getName().replaceAll("slideLayout(\\d+)\\.xml", "$1"));
                max = Math.max(max, num);
            }
        }
        return max + 1;
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
        try {
            LayoutManager layoutManager = context.getLayoutManager();
            if (layoutManager == null) return "slideLayout1";
            for (int i = 1; i <= 20; i++) {
                String layoutId = "slideLayout" + i;
                try {
                    LayoutInfo layoutInfo = layoutManager.getLayoutInfo(layoutId);
                    if (layoutInfo != null) {
                        return layoutId;
                    }
                } catch (Exception e) {
                    // Layout doesn't exist, continue
                }
            }

            logger.warn("No layout found, falling back to null (will use insertBlankSlide)");
            return null;
        } catch (Exception e) {
            logger.error("Failed to find default layout: {}", e.getMessage());
            return null;
        }
    }

    // ========== INTERNAL: MASTER RELS MANIPULATION ==========

    /**
     * Add a layout relationship to slideMaster1.xml.rels and sldLayoutIdLst.
     */
    private void addLayoutToMaster(int layoutNum) throws Exception {
        // 1. Update slideMaster1.xml.rels -- insert before theme rel
        File masterRelsFile = new File(context.getDocument().getExtractedDir(),
            "ppt/slideMasters/_rels/slideMaster1.xml.rels");
        Document masterRelsDoc = XMLFactoryProvider.parseDocument(masterRelsFile);
        Element relsRoot = masterRelsDoc.getDocumentElement();

        // Find max rId and theme rel
        NodeList existingRels = relsRoot.getElementsByTagNameNS(PKG_REL, "Relationship");
        int maxRId = 0;
        Element themeRel = null;
        for (int i = 0; i < existingRels.getLength(); i++) {
            Element rel = (Element) existingRels.item(i);
            String id = rel.getAttribute("Id");
            int rIdNum = Integer.parseInt(id.replaceAll("rId(\\d+)", "$1"));
            maxRId = Math.max(maxRId, rIdNum);
            if (XMLConstants.RELATIONSHIP_TYPE_THEME.equals(rel.getAttribute("Type"))) {
                themeRel = rel;
            }
        }

        // Create new layout relationship
        int newRId = maxRId + 1;
        Element newRel = masterRelsDoc.createElementNS(PKG_REL, "Relationship");
        newRel.setAttribute("Id", "rId" + newRId);
        newRel.setAttribute("Type", XMLConstants.RELATIONSHIP_TYPE_SLIDE_LAYOUT);
        newRel.setAttribute("Target", "../slideLayouts/slideLayout" + layoutNum + ".xml");

        // If theme rel exists, move it to use the new highest rId and insert layout before
        if (themeRel != null) {
            // Insert new layout rel before theme
            relsRoot.insertBefore(newRel, themeRel);
            // Update theme rel to use newRId+1
            themeRel.setAttribute("Id", "rId" + (newRId + 1));
        } else {
            relsRoot.appendChild(newRel);
        }

        context.putXmlPart("ppt/slideMasters/_rels/slideMaster1.xml.rels", masterRelsDoc);

        // 2. Update slideMaster1.xml sldLayoutIdLst
        Document masterDoc = context.getXmlPart("ppt/slideMasters/slideMaster1.xml");

        long newId = getMaxSldLayoutId(masterDoc) + 1;
        String layoutRId = "rId" + newRId;

        PresentationXmlHelper.addLayoutIdToMaster(masterDoc, layoutRId, newId);
        context.putXmlPart("ppt/slideMasters/slideMaster1.xml", masterDoc);
    }

    /**
     * Remove a layout relationship from slideMaster1.xml.rels and sldLayoutIdLst.
     */
    private void removeLayoutFromMaster(int layoutNum) throws Exception {
        // 1. Remove from slideMaster1.xml.rels
        File masterRelsFile = new File(context.getDocument().getExtractedDir(),
            "ppt/slideMasters/_rels/slideMaster1.xml.rels");
        Document masterRelsDoc = XMLFactoryProvider.parseDocument(masterRelsFile);
        Element relsRoot = masterRelsDoc.getDocumentElement();

        String targetPath = "../slideLayouts/slideLayout" + layoutNum + ".xml";
        NodeList rels = relsRoot.getElementsByTagNameNS(PKG_REL, "Relationship");
        String removedRId = null;
        for (int i = 0; i < rels.getLength(); i++) {
            Element rel = (Element) rels.item(i);
            if (targetPath.equals(rel.getAttribute("Target"))) {
                removedRId = rel.getAttribute("Id");
                relsRoot.removeChild(rel);
                break;
            }
        }

        OOXMLAttributeOrder.serialize(masterRelsDoc, masterRelsFile);

        // 2. Remove from slideMaster1.xml sldLayoutIdLst
        if (removedRId != null) {
            Document masterDoc = context.getXmlPart("ppt/slideMasters/slideMaster1.xml");
            if (masterDoc != null) {
                PresentationXmlHelper.removeLayoutIdFromMaster(masterDoc, removedRId);
                context.putXmlPart("ppt/slideMasters/slideMaster1.xml", masterDoc);
            }
        }
    }

    /**
     * Add layout content type to [Content_Types].xml.
     */
    private void addLayoutContentType(int layoutNum) throws Exception {
        Document ctDoc = context.getXmlPart("[Content_Types].xml");
        if (ctDoc != null) {
            PresentationXmlHelper.addLayoutContentType(ctDoc, layoutNum);
            context.putXmlPart("[Content_Types].xml", ctDoc);
        }
    }

    /**
     * Remove layout content type from [Content_Types].xml.
     */
    private void removeLayoutContentType(int layoutNum) throws Exception {
        File ctFile = new File(context.getDocument().getExtractedDir(), "[Content_Types].xml");
        Document ctDoc = XMLFactoryProvider.parseDocument(ctFile);
        PresentationXmlHelper.removeLayoutContentType(ctDoc, layoutNum);
        OOXMLAttributeOrder.serialize(ctDoc, ctFile);
    }

    // ========== INTERNAL: DELETE RENUMBER CASCADE ==========

    /**
     * Renumber all layouts after a deletion to maintain contiguous numbering.
     * Pattern mirrors SlideOrchestrationManager's slide renumbering.
     */
    private void renumberLayoutsAfterDelete(int deletedNum, int maxNum) throws Exception {
        File layoutsDir = new File(context.getDocument().getExtractedDir(), "ppt/slideLayouts");
        File layoutsRelsDir = new File(layoutsDir, "_rels");

        // 1. Rename layout files and rels: slideLayout{N+1} -> slideLayout{N}
        for (int i = deletedNum + 1; i <= maxNum; i++) {
            File oldLayout = new File(layoutsDir, "slideLayout" + i + ".xml");
            File newLayout = new File(layoutsDir, "slideLayout" + (i - 1) + ".xml");
            if (oldLayout.exists()) {
                oldLayout.renameTo(newLayout);
            }

            File oldRels = new File(layoutsRelsDir, "slideLayout" + i + ".xml.rels");
            File newRels = new File(layoutsRelsDir, "slideLayout" + (i - 1) + ".xml.rels");
            if (oldRels.exists()) {
                oldRels.renameTo(newRels);
            }
        }

        // 2. Update all slide rels pointing to affected layouts
        File slidesRelsDir = new File(context.getDocument().getExtractedDir(), "ppt/slides/_rels");
        if (slidesRelsDir.exists()) {
            File[] slideRelsFiles = slidesRelsDir.listFiles((dir, name) ->
                name.matches("slide\\d+\\.xml\\.rels"));
            if (slideRelsFiles != null) {
                for (File relsFile : slideRelsFiles) {
                    boolean modified = false;
                    Document doc = XMLFactoryProvider.parseDocument(relsFile);
                    NodeList rels = doc.getElementsByTagNameNS(PKG_REL, "Relationship");
                    for (int i = 0; i < rels.getLength(); i++) {
                        Element rel = (Element) rels.item(i);
                        if (XMLConstants.RELATIONSHIP_TYPE_SLIDE_LAYOUT.equals(rel.getAttribute("Type"))) {
                            String target = rel.getAttribute("Target");
                            // Extract layout number from target path
                            java.util.regex.Matcher m = java.util.regex.Pattern
                                .compile("slideLayout(\\d+)\\.xml").matcher(target);
                            if (m.find()) {
                                int refNum = Integer.parseInt(m.group(1));
                                if (refNum > deletedNum) {
                                    String newTarget = target.replace(
                                        "slideLayout" + refNum, "slideLayout" + (refNum - 1));
                                    rel.setAttribute("Target", newTarget);
                                    modified = true;
                                }
                            }
                        }
                    }
                    if (modified) {
                        OOXMLAttributeOrder.serialize(doc, relsFile);
                    }
                }
            }
        }

        // 3. Rebuild master rels and sldLayoutIdLst for the new layout set
        rebuildMasterRels(maxNum - 1);

        // 4. Rebuild content types for layouts
        rebuildLayoutContentTypes(maxNum - 1);
    }

    /**
     * Rebuild slideMaster1.xml.rels and its sldLayoutIdLst to reflect current layout count.
     */
    private void rebuildMasterRels(int layoutCount) throws Exception {
        // Rebuild slideMaster1.xml.rels
        File masterRelsFile = new File(context.getDocument().getExtractedDir(),
            "ppt/slideMasters/_rels/slideMaster1.xml.rels");
        Document masterRelsDoc = XMLFactoryProvider.parseDocument(masterRelsFile);
        Element relsRoot = masterRelsDoc.getDocumentElement();

        // Preserve the theme target (find existing theme rel)
        String themeTarget = null;
        NodeList existingRels = relsRoot.getElementsByTagNameNS(PKG_REL, "Relationship");
        for (int i = 0; i < existingRels.getLength(); i++) {
            Element rel = (Element) existingRels.item(i);
            if (XMLConstants.RELATIONSHIP_TYPE_THEME.equals(rel.getAttribute("Type"))) {
                themeTarget = rel.getAttribute("Target");
                break;
            }
        }
        if (themeTarget == null) {
            themeTarget = "../theme/theme1.xml";
        }

        // Clear all children
        while (relsRoot.hasChildNodes()) {
            relsRoot.removeChild(relsRoot.getFirstChild());
        }

        // Add layout rels
        for (int i = 1; i <= layoutCount; i++) {
            Element rel = masterRelsDoc.createElementNS(PKG_REL, "Relationship");
            rel.setAttribute("Id", "rId" + i);
            rel.setAttribute("Type", XMLConstants.RELATIONSHIP_TYPE_SLIDE_LAYOUT);
            rel.setAttribute("Target", "../slideLayouts/slideLayout" + i + ".xml");
            relsRoot.appendChild(rel);
        }

        // Theme relationship (last)
        Element themeRel = masterRelsDoc.createElementNS(PKG_REL, "Relationship");
        themeRel.setAttribute("Id", "rId" + (layoutCount + 1));
        themeRel.setAttribute("Type", XMLConstants.RELATIONSHIP_TYPE_THEME);
        themeRel.setAttribute("Target", themeTarget);
        relsRoot.appendChild(themeRel);

        OOXMLAttributeOrder.serialize(masterRelsDoc, masterRelsFile);

        // Rebuild sldLayoutIdLst in slideMaster1.xml
        Document masterDoc = context.getXmlPart("ppt/slideMasters/slideMaster1.xml");
        PresentationXmlHelper.rebuildSldLayoutIdLst(masterDoc, layoutCount);
        context.putXmlPart("ppt/slideMasters/slideMaster1.xml", masterDoc);
    }

    /**
     * Rebuild layout content types in [Content_Types].xml.
     */
    private void rebuildLayoutContentTypes(int layoutCount) throws Exception {
        Document ctDoc = context.getXmlPart("[Content_Types].xml");
        if (ctDoc != null) {
            List<Integer> layoutNumbers = new ArrayList<>();
            for (int i = 1; i <= layoutCount; i++) {
                layoutNumbers.add(i);
            }
            PresentationXmlHelper.rebuildLayoutContentTypes(ctDoc, layoutNumbers);
            context.putXmlPart("[Content_Types].xml", ctDoc);
        }
    }

    // ========== INTERNAL: DOM HELPERS ==========

    private long getMaxSldLayoutId(Document masterDoc) {
        long maxId = BASE_LAYOUT_ID - 1;
        NodeList sldLayoutIds = masterDoc.getElementsByTagNameNS(PNS, "sldLayoutId");
        for (int i = 0; i < sldLayoutIds.getLength(); i++) {
            Element el = (Element) sldLayoutIds.item(i);
            String idStr = el.getAttribute("id");
            if (!idStr.isEmpty()) {
                maxId = Math.max(maxId, Long.parseLong(idStr));
            }
        }
        return maxId;
    }

    private Element findSpTree(Document doc) {
        NodeList spTrees = doc.getElementsByTagNameNS(PNS, "spTree");
        return spTrees.getLength() > 0 ? (Element) spTrees.item(0) : null;
    }

    private int getMaxSpidInLayout(Element spTree) {
        int max = 1; // group shape is always 1
        NodeList shapes = spTree.getElementsByTagNameNS(PNS, "sp");
        for (int i = 0; i < shapes.getLength(); i++) {
            Element sp = (Element) shapes.item(i);
            Element cNvPr = findDescendant(sp, PNS, "cNvPr");
            if (cNvPr != null) {
                String idStr = cNvPr.getAttribute("id");
                if (!idStr.isEmpty()) {
                    max = Math.max(max, Integer.parseInt(idStr));
                }
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
    private Element createPlaceholderShape(Document doc, int spid, PlaceholderDefinition ph) {
        Element sp = doc.createElementNS(PNS, "p:sp");

        // nvSpPr
        Element nvSpPr = doc.createElementNS(PNS, "p:nvSpPr");
        sp.appendChild(nvSpPr);

        Element cNvPr = doc.createElementNS(PNS, "p:cNvPr");
        cNvPr.setAttribute("id", String.valueOf(spid));
        String shapeName = getShapeName(ph.getType(), spid);
        cNvPr.setAttribute("name", shapeName);
        nvSpPr.appendChild(cNvPr);

        Element cNvSpPr = doc.createElementNS(PNS, "p:cNvSpPr");
        Element spLocks = doc.createElementNS(ANS, "a:spLocks");
        spLocks.setAttribute("noGrp", "1");
        cNvSpPr.appendChild(spLocks);
        nvSpPr.appendChild(cNvSpPr);

        Element nvPr = doc.createElementNS(PNS, "p:nvPr");
        Element phEl = doc.createElementNS(PNS, "p:ph");
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

        // spPr with geometry
        Element spPr = doc.createElementNS(PNS, "p:spPr");
        sp.appendChild(spPr);

        Element xfrm = doc.createElementNS(ANS, "a:xfrm");
        spPr.appendChild(xfrm);

        Element off = doc.createElementNS(ANS, "a:off");
        off.setAttribute("x", String.valueOf(ph.getX()));
        off.setAttribute("y", String.valueOf(ph.getY()));
        xfrm.appendChild(off);

        Element ext = doc.createElementNS(ANS, "a:ext");
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

    // ========== INTERNAL: FILE PATH HELPERS ==========

    private File getLayoutFile(int layoutNum) {
        return new File(context.getDocument().getExtractedDir(), "ppt/slideLayouts/slideLayout" + layoutNum + ".xml");
    }

    private String getLayoutPartName(int layoutNum) {
        return "ppt/slideLayouts/slideLayout" + layoutNum + ".xml";
    }

    private String getLayoutRelsPartName(int layoutNum) {
        return "ppt/slideLayouts/_rels/slideLayout" + layoutNum + ".xml.rels";
    }

    private File getLayoutRelsFile(int layoutNum) {
        return new File(context.getDocument().getExtractedDir(),
            "ppt/slideLayouts/_rels/slideLayout" + layoutNum + ".xml.rels");
    }

    /**
     * Read a layout DOM from PPTXDocument or disk.
     */
    private Document readLayoutDocument(int layoutNum) {
        return context.getXmlPart(getLayoutPartName(layoutNum));
    }

    /**
     * Write a layout DOM to PPTXDocument or disk.
     */
    private void writeLayoutDocument(int layoutNum, Document doc) {
        context.putXmlPart(getLayoutPartName(layoutNum), doc);
    }

    private int extractLayoutNumber(String layoutId) {
        return Integer.parseInt(layoutId.replaceAll("slideLayout(\\d+)", "$1"));
    }
}
