package com.excudo.core.model;

import org.w3c.dom.*;
import javax.xml.xpath.*;
import java.util.*;
import com.excudo.core.utils.XMLConstants;
import com.excudo.core.utils.XMLFactoryProvider;
import com.excudo.core.utils.Logger;
import com.excudo.core.utils.ComponentLogger;

/**
 * DOM-based operations on presentation.xml and [Content_Types].xml.
 * Replaces the fragile regex/string manipulation in SlideInsertionPipeline
 * and SlideOrchestrationManager.
 */
public class PresentationXmlHelper {

    private static final ComponentLogger logger = Logger.getLogger(PresentationXmlHelper.class);
    private static final String PNS = XMLConstants.PRESENTATION_NS;
    private static final String RNS = XMLConstants.RELATIONSHIPS_NS;

    // ========== PRESENTATION.XML: sldIdLst OPERATIONS ==========

    /**
     * Get the maximum slide ID currently in presentation.xml's sldIdLst.
     *
     * @param presDoc The presentation.xml DOM
     * @return The highest sldId value, or DEFAULT_SLIDE_ID_START - 1 if empty
     */
    public static int getMaxSlideId(Document presDoc) {
        int maxId = XMLConstants.DEFAULT_SLIDE_ID_START - 1;
        NodeList sldIds = presDoc.getElementsByTagNameNS(PNS, "sldId");
        for (int i = 0; i < sldIds.getLength(); i++) {
            Element el = (Element) sldIds.item(i);
            String idStr = el.getAttribute("id");
            if (!idStr.isEmpty()) {
                maxId = Math.max(maxId, Integer.parseInt(idStr));
            }
        }
        return maxId;
    }

    /**
     * Insert a new sldId element into the sldIdLst.
     * Creates sldIdLst if it doesn't exist.
     *
     * @param presDoc The presentation.xml DOM
     * @param slideId The numeric slide ID (e.g., 256, 257, ...)
     * @param rId     The relationship ID (e.g., "rId2")
     * @param insertBeforeRId If non-null, insert before the sldId with this rId.
     *                        If null, append to end.
     */
    public static void insertSlideId(Document presDoc, int slideId, String rId, String insertBeforeRId) {
        Element sldIdLst = findOrCreateSldIdLst(presDoc);

        Element newSldId = presDoc.createElementNS(PNS, "p:sldId");
        newSldId.setAttribute("id", String.valueOf(slideId));
        newSldId.setAttributeNS(RNS, "r:id", rId);

        if (insertBeforeRId != null) {
            // Find the element to insert before
            NodeList children = sldIdLst.getElementsByTagNameNS(PNS, "sldId");
            Element insertBefore = null;
            for (int i = 0; i < children.getLength(); i++) {
                Element child = (Element) children.item(i);
                if (insertBeforeRId.equals(child.getAttributeNS(RNS, "id"))) {
                    insertBefore = child;
                    break;
                }
            }
            if (insertBefore != null) {
                sldIdLst.insertBefore(newSldId, insertBefore);
            } else {
                sldIdLst.appendChild(newSldId);
            }
        } else {
            sldIdLst.appendChild(newSldId);
        }

        logger.debug("Inserted sldId: id={}, r:id={}", slideId, rId);
    }

    /**
     * Insert a new sldId and cascade existing rId references for slide insertion.
     * This handles the rId cascade that occurs when a slide is inserted in the middle:
     * existing slides at or after the insert position have their rIds incremented.
     *
     * @param presDoc         The presentation.xml DOM
     * @param newSlideNumber  The position being inserted (1-based)
     * @param newRId          The rId for the new slide (e.g., "rId3")
     * @return The slideId allocated for the new slide
     */
    public static int insertSlideIdWithCascade(Document presDoc, int newSlideNumber, String newRId) {
        int newSlideId = getMaxSlideId(presDoc) + 1;

        // Extract rId number from the new rId
        int newRIdNum = extractRIdNumber(newRId);

        Element sldIdLst = findOrCreateSldIdLst(presDoc);
        NodeList sldIds = sldIdLst.getElementsByTagNameNS(PNS, "sldId");

        // Cascade existing rIds that are >= newRIdNum
        if (newRIdNum > 0) {
            for (int i = 0; i < sldIds.getLength(); i++) {
                Element el = (Element) sldIds.item(i);
                String existingRId = el.getAttributeNS(RNS, "id");
                int existingRIdNum = extractRIdNumber(existingRId);
                if (existingRIdNum >= newRIdNum) {
                    el.setAttributeNS(RNS, "r:id", "rId" + (existingRIdNum + 1));
                }
            }
        }

        // Determine insert position: after the (newSlideNumber-1)th sldId
        // Refresh the node list after cascade mutations
        sldIds = sldIdLst.getElementsByTagNameNS(PNS, "sldId");

        Element newSldId = presDoc.createElementNS(PNS, "p:sldId");
        newSldId.setAttribute("id", String.valueOf(newSlideId));
        newSldId.setAttributeNS(RNS, "r:id", newRId);

        if (newSlideNumber - 1 < sldIds.getLength()) {
            // Insert before the element at position (newSlideNumber-1), since we
            // want the new slide at that 0-based index
            Element insertBefore = (Element) sldIds.item(newSlideNumber - 1);
            sldIdLst.insertBefore(newSldId, insertBefore);
        } else {
            sldIdLst.appendChild(newSldId);
        }

        logger.debug("Inserted sldId with cascade: id={}, r:id={}, position={}",
                     newSlideId, newRId, newSlideNumber);
        return newSlideId;
    }

    /**
     * Remove a sldId element by its r:id attribute value.
     *
     * @param presDoc The presentation.xml DOM
     * @param rId     The relationship ID to remove (e.g., "rId3")
     * @return true if found and removed
     */
    public static boolean removeSlideId(Document presDoc, String rId) {
        NodeList sldIds = presDoc.getElementsByTagNameNS(PNS, "sldId");
        for (int i = 0; i < sldIds.getLength(); i++) {
            Element el = (Element) sldIds.item(i);
            String elRId = el.getAttributeNS(RNS, "id");
            if (rId.equals(elRId)) {
                el.getParentNode().removeChild(el);
                logger.debug("Removed sldId with r:id={}", rId);
                return true;
            }
        }
        logger.warn("No sldId found with r:id={}", rId);
        return false;
    }

    // ========== CONTENT_TYPES.XML DOM OPERATIONS ==========

    private static final String CT_NS = "http://schemas.openxmlformats.org/package/2006/content-types";
    private static final String SLIDE_CONTENT_TYPE =
        "application/vnd.openxmlformats-officedocument.presentationml.slide+xml";

    /**
     * Add a slide Override entry to [Content_Types].xml DOM.
     *
     * @param ctDoc      The [Content_Types].xml DOM
     * @param slideNumber The slide number
     */
    public static void addSlideContentType(Document ctDoc, int slideNumber) {
        String partName = "/ppt/slides/slide" + slideNumber + ".xml";

        // Check if already exists
        Element existing = findOverrideByPartName(ctDoc, partName);
        if (existing != null) {
            return; // Already present
        }

        Element root = ctDoc.getDocumentElement();
        Element override = ctDoc.createElementNS(CT_NS, "Override");
        override.setAttribute("PartName", partName);
        override.setAttribute("ContentType", SLIDE_CONTENT_TYPE);
        root.appendChild(override);

        logger.debug("Added content type for {}", partName);
    }

    /**
     * Remove a slide Override entry from [Content_Types].xml DOM.
     *
     * @param ctDoc      The [Content_Types].xml DOM
     * @param slideNumber The slide number
     */
    public static void removeSlideContentType(Document ctDoc, int slideNumber) {
        String partName = "/ppt/slides/slide" + slideNumber + ".xml";
        Element existing = findOverrideByPartName(ctDoc, partName);
        if (existing != null) {
            existing.getParentNode().removeChild(existing);
            logger.debug("Removed content type for {}", partName);
        }
    }

    /**
     * Rebuild all slide Override entries to match a given set of slide numbers.
     * Removes all existing slide overrides and adds fresh ones.
     *
     * @param ctDoc        The [Content_Types].xml DOM
     * @param slideNumbers The slide numbers that should be present
     */
    public static void rebuildSlideContentTypes(Document ctDoc, List<Integer> slideNumbers) {
        Element root = ctDoc.getDocumentElement();

        // Remove all existing slide overrides
        List<Element> toRemove = new ArrayList<>();
        NodeList overrides = root.getElementsByTagNameNS(CT_NS, "Override");
        // Must collect first since NodeList is live
        for (int i = 0; i < overrides.getLength(); i++) {
            Element el = (Element) overrides.item(i);
            String partName = el.getAttribute("PartName");
            if (partName.matches("/ppt/slides/slide\\d+\\.xml")) {
                toRemove.add(el);
            }
        }
        for (Element el : toRemove) {
            el.getParentNode().removeChild(el);
        }

        // Add fresh entries
        List<Integer> sorted = new ArrayList<>(slideNumbers);
        Collections.sort(sorted);
        for (int slideNum : sorted) {
            addSlideContentType(ctDoc, slideNum);
        }

        logger.debug("Rebuilt slide content types for {} slides", slideNumbers.size());
    }

    /**
     * Remove a notes slide Override entry from [Content_Types].xml DOM.
     *
     * @param ctDoc    The [Content_Types].xml DOM
     * @param partName The full part name (e.g., "/ppt/notesSlides/notesSlide1.xml")
     */
    public static void removeNotesSlideContentType(Document ctDoc, String partName) {
        Element existing = findOverrideByPartName(ctDoc, partName);
        if (existing != null) {
            existing.getParentNode().removeChild(existing);
            logger.debug("Removed notes content type for {}", partName);
        }
    }

    // ========== SLIDE MASTER: sldLayoutIdLst OPERATIONS ==========

    private static final String LAYOUT_CONTENT_TYPE =
        "application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml";

    /**
     * Add a sldLayoutId element to the master's sldLayoutIdLst.
     */
    public static void addLayoutIdToMaster(Document masterDoc, String rId, long id) {
        Element sldLayoutIdLst = findOrCreateSldLayoutIdLst(masterDoc);

        Element newEl = masterDoc.createElementNS(PNS, "p:sldLayoutId");
        newEl.setAttribute("id", String.valueOf(id));
        newEl.setAttributeNS(RNS, "r:id", rId);
        sldLayoutIdLst.appendChild(newEl);

        logger.debug("Added sldLayoutId: id={}, r:id={}", id, rId);
    }

    /**
     * Remove a sldLayoutId element by its r:id.
     */
    public static boolean removeLayoutIdFromMaster(Document masterDoc, String rId) {
        NodeList sldLayoutIds = masterDoc.getElementsByTagNameNS(PNS, "sldLayoutId");
        for (int i = 0; i < sldLayoutIds.getLength(); i++) {
            Element el = (Element) sldLayoutIds.item(i);
            String elRId = el.getAttributeNS(RNS, "id");
            if (rId.equals(elRId)) {
                el.getParentNode().removeChild(el);
                logger.debug("Removed sldLayoutId with r:id={}", rId);
                return true;
            }
        }
        logger.warn("No sldLayoutId found with r:id={}", rId);
        return false;
    }

    /**
     * Get the maximum sldLayoutId value from the master.
     */
    public static long getMaxSldLayoutId(Document masterDoc) {
        long maxId = 2147483648L; // base - 1
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

    /**
     * Rebuild sldLayoutIdLst with contiguous entries for the given layout count.
     */
    public static void rebuildSldLayoutIdLst(Document masterDoc, int layoutCount) {
        // Remove existing sldLayoutIdLst
        NodeList existing = masterDoc.getElementsByTagNameNS(PNS, "sldLayoutIdLst");
        for (int i = existing.getLength() - 1; i >= 0; i--) {
            existing.item(i).getParentNode().removeChild(existing.item(i));
        }

        // Create fresh sldLayoutIdLst
        Element root = masterDoc.getDocumentElement();
        Element sldLayoutIdLst = masterDoc.createElementNS(PNS, "p:sldLayoutIdLst");

        for (int i = 1; i <= layoutCount; i++) {
            Element el = masterDoc.createElementNS(PNS, "p:sldLayoutId");
            el.setAttribute("id", String.valueOf(2147483648L + i));
            el.setAttributeNS(RNS, "r:id", "rId" + i);
            sldLayoutIdLst.appendChild(el);
        }

        // Insert as first child of root (before cSld)
        root.insertBefore(sldLayoutIdLst, root.getFirstChild());

        logger.debug("Rebuilt sldLayoutIdLst with {} entries", layoutCount);
    }

    // ========== LAYOUT CONTENT TYPE OPERATIONS ==========

    /**
     * Add a layout Override entry to [Content_Types].xml DOM.
     */
    public static void addLayoutContentType(Document ctDoc, int layoutNumber) {
        String partName = "/ppt/slideLayouts/slideLayout" + layoutNumber + ".xml";

        Element existing = findOverrideByPartName(ctDoc, partName);
        if (existing != null) {
            return;
        }

        Element root = ctDoc.getDocumentElement();
        Element override = ctDoc.createElementNS(CT_NS, "Override");
        override.setAttribute("PartName", partName);
        override.setAttribute("ContentType", LAYOUT_CONTENT_TYPE);
        root.appendChild(override);

        logger.debug("Added layout content type for {}", partName);
    }

    /**
     * Remove a layout Override entry from [Content_Types].xml DOM.
     */
    public static void removeLayoutContentType(Document ctDoc, int layoutNumber) {
        String partName = "/ppt/slideLayouts/slideLayout" + layoutNumber + ".xml";
        Element existing = findOverrideByPartName(ctDoc, partName);
        if (existing != null) {
            existing.getParentNode().removeChild(existing);
            logger.debug("Removed layout content type for {}", partName);
        }
    }

    /**
     * Rebuild all layout Override entries to match a given set of layout numbers.
     */
    public static void rebuildLayoutContentTypes(Document ctDoc, List<Integer> layoutNumbers) {
        Element root = ctDoc.getDocumentElement();

        // Remove all existing layout overrides
        List<Element> toRemove = new ArrayList<>();
        NodeList overrides = root.getElementsByTagNameNS(CT_NS, "Override");
        for (int i = 0; i < overrides.getLength(); i++) {
            Element el = (Element) overrides.item(i);
            String partName = el.getAttribute("PartName");
            if (partName.matches("/ppt/slideLayouts/slideLayout\\d+\\.xml")) {
                toRemove.add(el);
            }
        }
        // Also check without namespace
        NodeList overridesNoNs = root.getElementsByTagName("Override");
        for (int i = 0; i < overridesNoNs.getLength(); i++) {
            Element el = (Element) overridesNoNs.item(i);
            String partName = el.getAttribute("PartName");
            if (partName.matches("/ppt/slideLayouts/slideLayout\\d+\\.xml") && !toRemove.contains(el)) {
                toRemove.add(el);
            }
        }
        for (Element el : toRemove) {
            el.getParentNode().removeChild(el);
        }

        // Add fresh entries
        List<Integer> sorted = new ArrayList<>(layoutNumbers);
        Collections.sort(sorted);
        for (int num : sorted) {
            addLayoutContentType(ctDoc, num);
        }

        logger.debug("Rebuilt layout content types for {} layouts", layoutNumbers.size());
    }

    // ========== SLIDE MASTER HELPERS ==========

    private static Element findOrCreateSldLayoutIdLst(Document masterDoc) {
        NodeList lst = masterDoc.getElementsByTagNameNS(PNS, "sldLayoutIdLst");
        if (lst.getLength() > 0) {
            return (Element) lst.item(0);
        }

        // Create sldLayoutIdLst as first child of root
        Element root = masterDoc.getDocumentElement();
        Element sldLayoutIdLst = masterDoc.createElementNS(PNS, "p:sldLayoutIdLst");
        root.insertBefore(sldLayoutIdLst, root.getFirstChild());
        return sldLayoutIdLst;
    }

    // ========== INTERNAL HELPERS ==========

    private static Element findOrCreateSldIdLst(Document presDoc) {
        NodeList lst = presDoc.getElementsByTagNameNS(PNS, "sldIdLst");
        if (lst.getLength() > 0) {
            return (Element) lst.item(0);
        }

        // Create sldIdLst -- insert after sldMasterIdLst or as first child of presentation
        Element root = presDoc.getDocumentElement();
        Element sldIdLst = presDoc.createElementNS(PNS, "p:sldIdLst");

        // Try to insert after sldMasterIdLst for correct ordering
        NodeList masterLst = presDoc.getElementsByTagNameNS(PNS, "sldMasterIdLst");
        if (masterLst.getLength() > 0) {
            Node masterListNode = masterLst.item(0);
            Node nextSibling = masterListNode.getNextSibling();
            root.insertBefore(sldIdLst, nextSibling);
        } else {
            root.insertBefore(sldIdLst, root.getFirstChild());
        }

        return sldIdLst;
    }

    private static Element findOverrideByPartName(Document ctDoc, String partName) {
        Element root = ctDoc.getDocumentElement();
        NodeList overrides = root.getElementsByTagNameNS(CT_NS, "Override");
        for (int i = 0; i < overrides.getLength(); i++) {
            Element el = (Element) overrides.item(i);
            if (partName.equals(el.getAttribute("PartName"))) {
                return el;
            }
        }
        // Also try without namespace (some content types use default namespace)
        overrides = root.getElementsByTagName("Override");
        for (int i = 0; i < overrides.getLength(); i++) {
            Element el = (Element) overrides.item(i);
            if (partName.equals(el.getAttribute("PartName"))) {
                return el;
            }
        }
        return null;
    }

    private static int extractRIdNumber(String rId) {
        if (rId == null || !rId.startsWith("rId")) return -1;
        try {
            return Integer.parseInt(rId.substring(3));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
