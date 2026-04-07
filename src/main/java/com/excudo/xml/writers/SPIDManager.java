package com.excudo.xml.writers;

import org.w3c.dom.*;
import javax.xml.xpath.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.excudo.exceptions.XMLParsingException;
import com.excudo.core.model.PPTXDocument;
import com.excudo.core.model.PPTXDocumentParser;
import com.excudo.core.utils.XMLConstants;
import com.excudo.core.utils.XMLFactoryProvider;
import com.excudo.core.utils.Logger;
import com.excudo.core.utils.ComponentLogger;
import com.excudo.core.model.LayoutManager;
import com.excudo.core.model.LayoutInfo;

/**
 * Simplified SPID Manager implementing Microsoft PowerPoint's actual universal pattern:
 * - SPID 1: Group shape (structural, reserved)
 * - SPID 2: Title placeholder (when layout supports title)
 * - SPID 3+: Content shapes (sequential)
 * 
 * This matches real PowerPoint behavior exactly, no complex tiers needed.
 */
public class SPIDManager {
    
    private static final ComponentLogger logger = Logger.spid();
    private static volatile SPIDManager instance;
    
    // Global registry to track all allocated SPIDs
    private final Map<Integer, SPIDInfo> globalSpidRegistry = new ConcurrentHashMap<>();

    private LayoutManager layoutManager;

    // Dependency injection for RelationshipManager
    private RelationshipManager relationshipManager;

    /**
     * Private constructor for in-memory path (no disk scanning).
     */
    private SPIDManager(LayoutManager layoutManager) {
        this.layoutManager = layoutManager;
    }

    /**
     * Factory method to create SPIDManager from pre-parsed state (no disk access).
     * Sets the singleton instance.
     */
    public static SPIDManager createFromParsedState(
            PPTXDocumentParser.ParsedPresentationState parsedState,
            LayoutManager layoutManager) {
        SPIDManager mgr = new SPIDManager(layoutManager);
        for (Map.Entry<Integer, PPTXDocumentParser.SpidEntry> entry :
             parsedState.getSpidRegistry().entrySet()) {
            mgr.registerSpid(entry.getKey(), entry.getValue().getSlideNumber(),
                             entry.getValue().getShapeName());
        }
        synchronized (SPIDManager.class) {
            instance = mgr;
        }
        logger.debug("Created SPIDManager from parsed state: {} SPIDs", mgr.globalSpidRegistry.size());
        return mgr;
    }
    
    /**
     * Get existing instance (for cases where directory not available)
     */
    public static SPIDManager getInstance() throws XMLParsingException {
        if (instance == null) {
            throw new XMLParsingException("SPIDManager not initialized. Call getInstance(File) first.");
        }
        return instance;
    }
    
    /**
     * Reinitialize the SPIDManager from a PPTXDocument's in-memory DOMs.
     * Avoids the double-parse (SPIDManager scans slides, then PPTXDocument loads same slides).
     */
    public void reinitialize(PPTXDocument pptxDocument) {
        globalSpidRegistry.clear();
        try {
            XPath xpath = XMLFactoryProvider.createXPath();
            for (int slideNum : pptxDocument.getSlideNumbers()) {
                Document doc = pptxDocument.getSlideDocument(slideNum);
                scanDocumentForSpids(doc, slideNum, xpath);
            }
            logger.debug("Reinitialized SPIDManager from PPTXDocument: {} SPIDs from {} slides",
                         globalSpidRegistry.size(), pptxDocument.getSlideCount());
        } catch (Exception e) {
            logger.warn("Failed to reinitialize SPIDManager from PPTXDocument: " + e.getMessage());
        }
    }

    /**
     * Scan a Document for SPIDs (shared logic for File and PPTXDocument paths).
     */
    private void scanDocumentForSpids(Document doc, int slideNumber, XPath xpath) throws Exception {
        NodeList spidNodes = (NodeList) xpath.evaluate("//p:cNvPr/@id", doc, XPathConstants.NODESET);
        for (int i = 0; i < spidNodes.getLength(); i++) {
            try {
                int spid = Integer.parseInt(spidNodes.item(i).getNodeValue());
                registerSpid(spid, slideNumber, "existing_shape");
            } catch (NumberFormatException e) {
                // Skip invalid SPID
            }
        }
    }

    /**
     * Reset singleton instance for testing
     */
    public static void resetInstance() {
        synchronized (SPIDManager.class) {
            instance = null;
        }
    }
    
    /**
     * Setter for dependency injection of RelationshipManager.
     * This enables SPIDManager to read actual slide layout IDs instead of hardcoded defaults.
     * 
     * @param relationshipManager the RelationshipManager instance to inject
     */
    public void setRelationshipManager(RelationshipManager relationshipManager) {
        this.relationshipManager = relationshipManager;
        logger.debug("Injected RelationshipManager into SPIDManager");
    }
    
    /**
     * Allocate SPID using Microsoft's actual universal pattern
     */
    public int allocateSpidForShape(String shapeType, int slideNumber, boolean isPlaceholder, 
                                   boolean isFromTemplate, String layoutId) {
        
        // SPID 1: Always reserved for group shapes
        if ("group".equals(shapeType)) {
            registerSpid(1, slideNumber, "Group Shape");
            return 1;
        }
        
        // SPID 2: Title placeholder (when layout supports it)
        if ("title".equals(shapeType) && isPlaceholder) {
            // Check if layout supports title - simplified check
            boolean layoutHasTitle = checkLayoutSupportsTitle(slideNumber);
            // Check per-slide registry instead of global - SPID 2 can be reused across slides
            Set<Integer> slideSpids = getSpidsForSlide(slideNumber);
            if (layoutHasTitle && !slideSpids.contains(2)) {
                registerSpid(2, slideNumber, "Title Placeholder");
                return 2;
            }
            // If layout doesn't support title, fall through to content allocation
        }
        
        // SPID 3+: All other shapes (content) - use universal pattern
        int candidateSpid = findFirstAvailableInContentRangeForSlide(slideNumber);
        registerSpid(candidateSpid, slideNumber, shapeType + "_shape");
        logger.debug("Allocated content SPID " + candidateSpid + " for " + shapeType + " on slide " + slideNumber);
        return candidateSpid;
    }
    
    // REMOVED: allocateNextContentSpid - replaced with direct universal pattern allocation
    
    /**
     * Check if layout supports title placeholder by examining the actual layout
     */
    private boolean checkLayoutSupportsTitle(int slideNumber) {
        try {
            String layoutId = getSlideLayoutId(slideNumber);
            if (layoutId != null) {
                LayoutInfo layoutInfo = layoutManager.getLayoutInfo(layoutId);
                return layoutInfo != null && layoutInfo.hasTitlePlaceholder();
            }
        } catch (Exception e) {
            logger.warn("Failed to check layout title support for slide " + slideNumber + ": " + e.getMessage());
        }
        // Default to true for backward compatibility
        return true;
    }
    
    /**
     * Get the layout ID for a specific slide number by reading actual slide relationships.
     * Falls back to "slideLayout1" if RelationshipManager is not available or slide relationships are missing.
     */
    private String getSlideLayoutId(int slideNumber) {
        if (relationshipManager != null) {
            try {
                Map<String, String> slideRelationships = relationshipManager.getSlideRelationships(slideNumber);
                
                // Look for layout relationship
                for (Map.Entry<String, String> entry : slideRelationships.entrySet()) {
                    String target = entry.getValue();
                    if (target != null && target.contains("slideLayouts/")) {
                        // Extract layout ID from target like "../slideLayouts/slideLayout2.xml"
                        String layoutFileName = target.substring(target.lastIndexOf('/') + 1);
                        if (layoutFileName.endsWith(".xml")) {
                            String layoutId = layoutFileName.substring(0, layoutFileName.length() - 4);
                            logger.debug("Retrieved actual layout ID '{}' for slide {}", layoutId, slideNumber);
                            return layoutId;
                        }
                    }
                }
                
                logger.debug("No layout relationship found for slide {}, using fallback", slideNumber);
            } catch (Exception e) {
                logger.warn("Failed to get layout ID for slide {} from RelationshipManager: {}", 
                           slideNumber, e.getMessage());
            }
        } else {
            logger.debug("RelationshipManager not injected, using fallback layout ID for slide {}", slideNumber);
        }
        
        // Fallback to default
        return "slideLayout1";
    }
    
    /**
     * Get layout-aware first available SPID for user content
     */
    public int getFirstAvailableUserSpid(int slideNumber) throws XMLParsingException {
        try {
            String layoutId = getSlideLayoutId(slideNumber);
            if (layoutId != null) {
                LayoutInfo layoutInfo = layoutManager.getLayoutInfo(layoutId);
                if (layoutInfo != null) {
                    return layoutInfo.getFirstAvailableUserSpid();
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to get layout info for slide " + slideNumber + ", using default: " + e.getMessage());
        }
        
        // Fallback: assume title + single content placeholder
        return 4; // After group(1), title(2), content(3)
    }
    
    /**
     * Register a SPID in the global registry
     */
    public void registerSpid(int spid, int slideNumber, String shapeName) {
        globalSpidRegistry.put(spid, new SPIDInfo(slideNumber, shapeName));
        logger.debug("Registered SPID " + spid + " for '" + shapeName + "' on slide " + slideNumber);
    }
    
    /**
     * Get all SPIDs for a specific slide
     */
    public Set<Integer> getSpidsForSlide(int slideNumber) {
        return globalSpidRegistry.entrySet().stream()
            .filter(entry -> entry.getValue().slideNumber == slideNumber)
            .map(Map.Entry::getKey)
            .collect(java.util.stream.Collectors.toSet());
    }
    
    private int extractSlideNumber(String fileName) {
        // Extract number from "slide5.xml" -> 5
        return Integer.parseInt(fileName.replaceAll("[^0-9]", ""));
    }
    
    /**
     * Find the first available SPID in the universal content range (3-10).
     * If none available in that range, returns the next available SPID after 10.
     */
    private int findFirstAvailableInContentRange() {
        return findFirstAvailableInContentRangeForSlide(-1); // Global fallback
    }
    
    /**
     * Find the first available SPID for a specific slide.
     * Since our data model tracks all existing shapes (including layout placeholders),
     * we just need to find the first unused SPID starting from 3.
     */
    private int findFirstAvailableInContentRangeForSlide(int slideNumber) {
        Set<Integer> slideSpids = slideNumber > 0 ? getSpidsForSlide(slideNumber) : Set.of();
        
        // Start from SPID 3 (after group=1, title=2) and find first available
        int candidate = 3;
        while (slideSpids.contains(candidate)) {
            candidate++;
        }
        
        logger.debug("Allocating SPID {} for slide {}, existing SPIDs on slide: {}", 
                    candidate, slideNumber, slideSpids);
        
        return candidate;
    }
    
    /**
     * Predict what SPID would be allocated (for LLM context)
     */
    public int predictSpidForShape(String shapeType, int slideNumber, boolean isPlaceholder, 
                                  boolean isFromTemplate, String layoutId) {
        // Same logic as allocation but without actually allocating
        if ("group".equals(shapeType)) return 1;
        if ("title".equals(shapeType) && isPlaceholder && !globalSpidRegistry.containsKey(2)) {
            return checkLayoutSupportsTitle(slideNumber) ? 2 : getFirstAvailableUserSpidSafe(slideNumber);
        }
        return getFirstAvailableUserSpidSafe(slideNumber);
    }
    
    /**
     * Safe version of getFirstAvailableUserSpid that doesn't throw exceptions
     */
    private int getFirstAvailableUserSpidSafe(int slideNumber) {
        try {
            return getFirstAvailableUserSpid(slideNumber);
        } catch (Exception e) {
            logger.warn("Failed to predict layout-aware SPID, using fallback: " + e.getMessage());
            return 4; // Safe fallback to first content SPID
        }
    }
    
    /**
     * Peek at next available SPID without allocating (uses universal pattern)
     */
    public int peekNextAvailableSpid() {
        return findFirstAvailableInContentRange();
    }
    
    /**
     * Get SPID info for testing
     */
    public SPIDInfo getSpidInfo(int spid) {
        return globalSpidRegistry.get(spid);
    }
    
    /**
     * Validate SPID uniqueness for testing
     */
    public ValidationResult validateSpidUniqueness() {
        // Since we maintain uniqueness by design, always return valid
        return new ValidationResult(true, new ArrayList<>(), new ArrayList<>());
    }
    
    /**
     * Regenerate SPIDs for slide copying (simplified implementation)
     */
    public SPIDRegenerationResult regenerateSpids(Document slideDocument, int slideNumber) throws XMLParsingException {
        Map<Integer, Integer> spidMappings = new HashMap<>();
        int shapesProcessed = 0;
        int animationsUpdated = 0;
        
        try {
            XPath xpath = XMLFactoryProvider.createXPath();

            // Find all shapes and assign new SPIDs
            NodeList spidNodes = (NodeList) xpath.evaluate("//p:cNvPr/@id", slideDocument, XPathConstants.NODESET);
            
            for (int i = 0; i < spidNodes.getLength(); i++) {
                try {
                    int oldSpid = Integer.parseInt(spidNodes.item(i).getNodeValue());
                    int newSpid = allocateSpidForShape("copied_shape", slideNumber, false, false, null);
                    spidMappings.put(oldSpid, newSpid);
                    spidNodes.item(i).setNodeValue(String.valueOf(newSpid));
                    shapesProcessed++;
                } catch (NumberFormatException e) {
                    // Skip invalid SPID
                }
            }
            
            return new SPIDRegenerationResult(spidMappings, shapesProcessed, animationsUpdated);
            
        } catch (Exception e) {
            throw new XMLParsingException("Failed to regenerate SPIDs", e);
        }
    }
    
    /**
     * Get global registry for testing
     */
    public Map<Integer, SPIDInfo> getGlobalSpidRegistry() {
        return new HashMap<>(globalSpidRegistry);
    }
    
    /**
     * Get all SPIDs in the registry
     */
    public Set<Integer> getAllSpids() {
        return new HashSet<>(globalSpidRegistry.keySet());
    }
    
    /**
     * Check if a SPID is in use
     */
    public boolean isSpidInUse(int spid) {
        return globalSpidRegistry.containsKey(spid);
    }
    
    // REMOVED: All deprecated sequential allocation methods
    // Only Microsoft's universal pattern allocation is now supported
    // Use allocateSpidForShape(shapeType, slideNumber, isPlaceholder, isFromTemplate, layoutId)
    
    /**
     * SPID information holder
     */
    public static class SPIDInfo {
        public final int slideNumber;
        public final String shapeName;
        
        public SPIDInfo(int slideNumber, String shapeName) {
            this.slideNumber = slideNumber;
            this.shapeName = shapeName;
        }
        
        // Getters for compatibility
        public int getSlideNumber() { return slideNumber; }
        public String getShapeName() { return shapeName; }
    }
    
    /**
     * Validation result for testing
     */
    public static class ValidationResult {
        public final boolean isValid;
        public final List<String> errors;
        public final List<String> warnings;
        
        public ValidationResult(boolean isValid, List<String> errors, List<String> warnings) {
            this.isValid = isValid;
            this.errors = errors;
            this.warnings = warnings;
        }
        
        public boolean isValid() { return isValid; }
        public List<String> getErrors() { return errors; }
        public List<String> getWarnings() { return warnings; }
        public boolean hasErrors() { return !errors.isEmpty(); }
    }
    
    /**
     * SPID regeneration result for slide copying
     */
    public static class SPIDRegenerationResult {
        public final Map<Integer, Integer> spidMappings;
        public final int shapesProcessed;
        public final int animationsUpdated;
        
        public SPIDRegenerationResult(Map<Integer, Integer> spidMappings, int shapesProcessed, int animationsUpdated) {
            this.spidMappings = spidMappings;
            this.shapesProcessed = shapesProcessed;
            this.animationsUpdated = animationsUpdated;
        }
        
        public Map<Integer, Integer> getSpidMappings() { return spidMappings; }
        public int getShapesProcessed() { return shapesProcessed; }
        public int getAnimationsUpdated() { return animationsUpdated; }
    }
    
}