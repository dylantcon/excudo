package com.excudo.core.services;

import com.excudo.core.model.*;
import com.excudo.xml.writers.SPIDManager;
import com.excudo.xml.writers.RelationshipManager;
import com.excudo.xml.parsers.SlideXMLParser;
import com.excudo.exceptions.XMLParsingException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.w3c.dom.Document;

/**
 * Central service for aggregating and providing Model context for LLM operations.
 * This service acts as the single source of truth for all Model data needed by the LLM pipeline.
 * 
 * Key responsibilities:
 * - Aggregate data from SPIDManager, RelationshipManager, and slide parsers
 * - Provide pre-calculated SPIDs for operations that will create new shapes
 * - Expose timing tree hierarchies and animation relationships
 * - Track media resources and theme/layout relationships
 * - Enable Model-aware validation before operations
 */
public class ContextService {
    
    private final SPIDManager spidManager;
    private final RelationshipManager relationshipManager;
    private final SlideXMLParser slideParser;
    private final LayoutManager layoutManager;
    private PPTXDocument pptxDocument;

    // Cache parsed slide data for performance
    private final Map<Integer, ParsedSlideData> slideDataCache;
    
    /**
     * Construct from PPTXDocument and pre-built managers (no disk access needed).
     * Used by the fully in-memory initialization path.
     */
    public ContextService(PPTXDocument pptxDocument, SPIDManager spidManager,
                          RelationshipManager relationshipManager, LayoutManager layoutManager)
                          throws XMLParsingException {
        this.pptxDocument = pptxDocument;
        this.spidManager = spidManager;
        this.relationshipManager = relationshipManager;
        this.slideParser = new SlideXMLParser();
        this.layoutManager = layoutManager;
        this.slideDataCache = new ConcurrentHashMap<>();
    }
    
    /**
     * Get comprehensive context for a specific slide
     * @param slideNumber The slide number (1-based)
     * @return SlideContext containing all Model data for the slide
     */
    public SlideContext getSlideContext(int slideNumber) throws XMLParsingException {
        ParsedSlideData slideData = getOrParseSlidData(slideNumber);
        
        return new SlideContext(
            slideNumber,
            slideData,
            spidManager.getSpidsForSlide(slideNumber),
            getSlideRelationships(slideNumber),
            getNextAvailableSpid()
        );
    }
    
    /**
     * Get context for multiple slides
     * @param slideNumbers List of slide numbers to get context for
     * @return Map of slide number to SlideContext
     */
    public Map<Integer, SlideContext> getMultiSlideContext(List<Integer> slideNumbers) throws XMLParsingException {
        Map<Integer, SlideContext> contexts = new HashMap<>();
        for (Integer slideNum : slideNumbers) {
            contexts.put(slideNum, getSlideContext(slideNum));
        }
        return contexts;
    }
    
    /**
     * Get presentation-wide context
     * @return PresentationContext with global Model data
     */
    public PresentationContext getPresentationContext() throws XMLParsingException {
        return new PresentationContext(
            getAllSlideNumbers(),
            spidManager.getGlobalSpidRegistry(),
            relationshipManager.getAllMediaRelationships(),
            relationshipManager.getAvailableThemes(),
            relationshipManager.getAvailableLayouts(),
            getNextAvailableSpid()
        );
    }
    
    /**
     * Get layout context for LLM operations.
     * Provides dynamic information about available layouts and their capabilities.
     * 
     * @return Formatted layout context string for LLM prompts
     */
    public String getLayoutContext() throws XMLParsingException {
        StringBuilder context = new StringBuilder();
        context.append(layoutManager.generateLayoutContext());
        context.append("\n");
        context.append(layoutManager.generateSlideTypeMappingContext());
        return context.toString();
    }
    
    /**
     * Get specific layout information by ID.
     * Used for layout-aware SPID prediction.
     * 
     * @param layoutId The layout ID (e.g., "slideLayout1")
     * @return LayoutInfo or null if not found
     */
    public LayoutInfo getLayoutInfo(String layoutId) throws XMLParsingException {
        return layoutManager.getLayoutInfo(layoutId);
    }
    
    /**
     * Get all available layouts with detailed information.
     * 
     * @return List of LayoutInfo objects
     */
    public List<LayoutInfo> getAvailableLayoutsDetailed() throws XMLParsingException {
        return layoutManager.getAvailableLayouts();
    }
    
    /**
     * Get the LayoutManager instance for direct access to layout operations.
     * 
     * @return The LayoutManager instance
     */
    public LayoutManager getLayoutManager() {
        return layoutManager;
    }
    
    /**
     * Predict SPIDs for shapes that will be created
     * @param shapeCount Number of shapes that will be created
     * @return List of SPIDs that will be allocated
     */
    public List<Integer> predictNextSpids(int shapeCount) {
        // DEPRECATED: This method uses global sequential prediction which is inaccurate
        // Use predictSpidsForSlide() instead for Microsoft-compatible prediction
        
        // Get the current next SPID without actually allocating
        int nextSpid = getNextAvailableSpid();
        List<Integer> predictedSpids = new ArrayList<>();
        for (int i = 0; i < shapeCount; i++) {
            predictedSpids.add(nextSpid + i);
        }
        return predictedSpids;
    }

    /**
     * Predict SPIDs for shapes that will be created on a specific slide.
     * This uses Microsoft-compatible allocation logic that exactly matches SlideCreator behavior.
     * 
     * @param slideNumber The slide number where shapes will be added
     * @param shapeCount Number of shapes that will be created
     * @return List of SPIDs that will be allocated
     */
    public List<Integer> predictSpidsForSlide(int slideNumber, int shapeCount) {
        List<Integer> predictedSpids = new ArrayList<>();
        
        // CRITICAL: Match the exact sequence that SlideCreator.createBlankShapeTree() uses:
        // 1. Group shape: allocateSpidForShape("group", slideNumber, false, true, null) 
        // 2. Title shape: allocateSpidForShape("title", slideNumber, true, true, "slideLayout1")
        
        // Predict group shape SPID (created first by SlideCreator)
        int groupSpid = spidManager.predictSpidForShape("group", slideNumber, false, true, null);
        predictedSpids.add(groupSpid);
        
        // Predict title shape SPID (created second by SlideCreator) 
        int titleSpid = spidManager.predictSpidForShape("title", slideNumber, true, true, "slideLayout1");
        predictedSpids.add(titleSpid);
        
        // For user-created content shapes beyond the layout placeholders
        if (shapeCount > 2) {
            // Get the next available SPID after the layout placeholders
            int nextUserSpid = spidManager.predictSpidForShape("custom", slideNumber, false, false, null);
            
            // Add sequential SPIDs for additional user content
            for (int i = 2; i < shapeCount; i++) {
                predictedSpids.add(nextUserSpid + (i - 2));
            }
        }
        
        return predictedSpids;
    }
    
    /**
     * Predict user-addressable SPIDs using Microsoft's universal allocation pattern.
     * Based on consistent observed behavior: SPID 1=group, SPID 2=title (if supported), SPID 3+=content
     * 
     * IMPORTANT: This only returns SPIDs that the LLM can target - excludes structural
     * group shapes (SPID 1) which are automatically created and filtered by the parser.
     * 
     * @param slideType The generic slide type (e.g., "CONTENT", "TITLE")
     * @param slideNumber The slide number where shapes will be added
     * @param shapeCount Number of user-addressable shapes that will be created
     * @return List of user-addressable SPIDs using Microsoft's universal pattern
     */
    public List<Integer> predictUserSpidsForSlideType(String slideType, int slideNumber, int shapeCount) throws XMLParsingException {
        // Find which layout will actually be chosen for this slideType
        LayoutInfo chosenLayout = layoutManager.findLayoutForSlideType(slideType);
        
        List<Integer> userSpids = new ArrayList<>();
        
        // Microsoft's Universal Pattern: Title always gets SPID 2 when layout supports it
        if (chosenLayout != null && chosenLayout.hasTitlePlaceholder()) {
            userSpids.add(2); // Title placeholder
            shapeCount--; // One slot used for title
        }
        
        // Microsoft's Universal Pattern: Content shapes always start at SPID 3
        if (shapeCount > 0) {
            for (int i = 0; i < shapeCount; i++) {
                userSpids.add(3 + i); // Content shapes: 3, 4, 5, 6...
            }
        }
        
        return userSpids;
    }
    
    /**
     * Predict context for a slide that will be created
     * @param slideType The type of slide that will be created
     * @param slideTitle The title for the new slide
     * @return PredictedSlideContext with expected SPIDs and structure
     */
    public PredictedSlideContext predictSlideCreationContext(String slideType, String slideTitle) throws XMLParsingException {
        // Use layout-aware prediction
        LayoutInfo chosenLayout = layoutManager.findLayoutForSlideType(slideType);
        List<PredictedShape> predictedShapes = new ArrayList<>();
        
        if (chosenLayout != null && chosenLayout.hasTitlePlaceholder() && slideTitle != null && !slideTitle.isEmpty()) {
            // Microsoft Pattern: Title placeholder gets SPID 2 when layout supports it
            int titleSpid = 2;
            
            predictedShapes.add(new PredictedShape(
                titleSpid,
                "Title Placeholder",
                SlideShape.ShapeType.PLACEHOLDER,
                slideTitle
            ));
        }
        
        // Starting SPID is always 1 (group shape) according to Microsoft pattern
        int startingSpid = 1;
        return new PredictedSlideContext(predictedShapes, startingSpid);
    }
    
    /**
     * Validate if a SPID exists and is available for animation
     * @param spid The SPID to validate
     * @param slideNumber The slide number where the shape should exist
     * @return true if the SPID exists on the specified slide
     */
    public boolean validateSpidExists(int spid, int slideNumber) throws XMLParsingException {
        ParsedSlideData slideData = getOrParseSlidData(slideNumber);
        return slideData.getShapeRegistry().getShape(spid) != null;
    }
    
    /**
     * Get animation context for a slide including timing tree details
     * @param slideNumber The slide number
     * @return AnimationContext with full timing hierarchy
     */
    public AnimationContext getAnimationContext(int slideNumber) throws XMLParsingException {
        ParsedSlideData slideData = getOrParseSlidData(slideNumber);
        
        return new AnimationContext(
            slideData.getTimingTree(),
            slideData.getAnimationBindings(),
            Collections.emptyList(),
            Collections.emptyList()
        );
    }
    
    /**
     * Set the PPTXDocument reference so that slide data can be read from in-memory DOMs
     * instead of re-parsing from disk.
     */
    public void setPPTXDocument(PPTXDocument pptxDocument) {
        this.pptxDocument = pptxDocument;
    }

    // ========== CACHE MANAGEMENT ==========

    /**
     * Invalidate the cached slide data for a specific slide.
     * Must be called after any mutation (add-shape, edit-content, create_code_box, etc.)
     * so that subsequent reads (e.g. validate_layout) see the current DOM state.
     */
    public void invalidateSlide(int slideNumber) {
        slideDataCache.remove(slideNumber);
    }

    /**
     * Invalidate all cached slide data.
     * Used after bulk operations (execute_commands) that may touch multiple slides.
     */
    public void invalidateAllSlides() {
        slideDataCache.clear();
    }

    // ========== PRIVATE HELPER METHODS ==========
    
    private ParsedSlideData getOrParseSlidData(int slideNumber) throws XMLParsingException {
        if (!slideDataCache.containsKey(slideNumber)) {
            if (pptxDocument != null && pptxDocument.hasSlide(slideNumber)) {
                Document slideDoc = pptxDocument.getSlideDocument(slideNumber);
                ParsedSlideData data = slideParser.parseSlide(slideDoc, slideNumber);
                slideDataCache.put(slideNumber, data);
            } else {
                throw new XMLParsingException("Slide " + slideNumber + " does not exist in PPTXDocument");
            }
        }
        return slideDataCache.get(slideNumber);
    }

    private List<Integer> getAllSlideNumbers() {
        if (pptxDocument != null) {
            return pptxDocument.getSlideNumbers();
        }
        return new ArrayList<>();
    }
    
    private Map<String, String> getSlideRelationships(int slideNumber) {
        return relationshipManager.getSlideRelationships(slideNumber);
    }
    
    private int getNextAvailableSpid() {
        // Use SPIDManager's Microsoft-compatible prediction method for consistency
        return spidManager.peekNextAvailableSpid();
    }
    
    
    /**
     * Analyze shape hierarchy and grouping relationships
     */
    private static ShapeHierarchy analyzeShapeHierarchy(ShapeRegistry registry) {
        Map<SlideShape.ShapeType, List<SlideShape>> byType = new HashMap<>();
        Map<String, List<SlideShape>> byPosition = new HashMap<>();
        List<ShapeGroup> logicalGroups = new ArrayList<>();
        
        // Group by type
        for (SlideShape.ShapeType type : SlideShape.ShapeType.values()) {
            List<SlideShape> shapesOfType = registry.getShapesByType(type);
            if (!shapesOfType.isEmpty()) {
                byType.put(type, shapesOfType);
            }
        }
        
        // Group by logical position (simplified heuristic)
        List<SlideShape> allShapes = registry.getAllShapes();
        for (SlideShape shape : allShapes) {
            if (shape.getGeometry() != null) {
                String position = categorizePosition(shape.getGeometry());
                byPosition.computeIfAbsent(position, k -> new ArrayList<>()).add(shape);
            }
        }
        
        // Identify logical groupings (shapes that might work together)
        identifyLogicalGroups(allShapes, logicalGroups);
        
        return new ShapeHierarchy(byType, byPosition, logicalGroups);
    }
    
    /**
     * Categorize a shape's position on the slide
     */
    private static String categorizePosition(ShapeGeometry geometry) {
        // Convert EMUs to approximate percentages for positioning
        // Assuming standard slide dimensions
        long slideWidth = 9144000;  // 10 inches in EMUs
        long slideHeight = 6858000; // 7.5 inches in EMUs
        
        double xPercent = (double) geometry.getX() / slideWidth;
        double yPercent = (double) geometry.getY() / slideHeight;
        
        String horizontal = xPercent < 0.33 ? "left" : (xPercent < 0.67 ? "center" : "right");
        String vertical = yPercent < 0.33 ? "top" : (yPercent < 0.67 ? "middle" : "bottom");
        
        return vertical + "-" + horizontal;
    }
    
    /**
     * Identify logical groupings of shapes
     */
    private static void identifyLogicalGroups(List<SlideShape> shapes, List<ShapeGroup> groups) {
        // Group 1: Title and subtitle patterns
        SlideShape titleShape = findTitleShape(shapes);
        SlideShape subtitleShape = findSubtitleShape(shapes, titleShape);
        if (titleShape != null) {
            List<SlideShape> titleGroup = new ArrayList<>();
            titleGroup.add(titleShape);
            if (subtitleShape != null) {
                titleGroup.add(subtitleShape);
            }
            groups.add(new ShapeGroup("title-group", titleGroup, "Title and subtitle elements"));
        }
        
        // Group 2: Content shapes (non-title text boxes)
        List<SlideShape> contentShapes = shapes.stream()
            .filter(s -> s.hasText() && !isLikelyTitle(s))
            .toList();
        if (!contentShapes.isEmpty()) {
            groups.add(new ShapeGroup("content-group", contentShapes, "Main content shapes"));
        }
        
        // Group 3: Visual elements (pictures, non-text shapes)
        List<SlideShape> visualShapes = shapes.stream()
            .filter(s -> s.getType() == SlideShape.ShapeType.PICTURE || 
                        (s.getType() == SlideShape.ShapeType.RECTANGLE && !s.hasText()))
            .toList();
        if (!visualShapes.isEmpty()) {
            groups.add(new ShapeGroup("visual-group", visualShapes, "Visual and decorative elements"));
        }
    }
    
    /**
     * Find the most likely title shape
     */
    private static SlideShape findTitleShape(List<SlideShape> shapes) {
        return shapes.stream()
            .filter(ContextService::isLikelyTitle)
            .min((a, b) -> {
                // Prefer shapes higher on the slide
                if (a.getGeometry() != null && b.getGeometry() != null) {
                    return Long.compare(a.getGeometry().getY(), b.getGeometry().getY());
                }
                return 0;
            })
            .orElse(null);
    }
    
    /**
     * Find the most likely subtitle shape
     */
    private static SlideShape findSubtitleShape(List<SlideShape> shapes, SlideShape titleShape) {
        if (titleShape == null || titleShape.getGeometry() == null) return null;
        
        return shapes.stream()
            .filter(s -> s != titleShape && s.hasText() && s.getGeometry() != null)
            .filter(s -> s.getGeometry().getY() > titleShape.getGeometry().getY()) // Below title
            .filter(s -> Math.abs(s.getGeometry().getX() - titleShape.getGeometry().getX()) < 1000000) // Similar X position
            .min((a, b) -> Long.compare(a.getGeometry().getY(), b.getGeometry().getY())) // Closest to title
            .orElse(null);
    }
    
    /**
     * Heuristic to identify title-like shapes
     */
    private static boolean isLikelyTitle(SlideShape shape) {
        if (!shape.hasText()) return false;
        
        String name = shape.getName();
        if (name != null) {
            String lowerName = name.toLowerCase();
            if (lowerName.contains("title") || lowerName.contains("header")) {
                return true;
            }
        }
        
        String text = shape.getTextContent();
        if (text != null) {
            String lowerText = text.toLowerCase();
            if (lowerText.contains("click to add title") || 
                lowerText.contains("title") && text.length() < 50) {
                return true;
            }
        }
        
        // Position-based heuristic: shapes in the top third are more likely to be titles
        if (shape.getGeometry() != null) {
            return shape.getGeometry().getY() < 2000000; // Top ~2.2 inches
        }
        
        return false;
    }
    
    // ========== INNER CLASSES FOR CONTEXT DATA ==========
    
    /**
     * Complete context for a single slide
     */
    public static class SlideContext {
        private final int slideNumber;
        private final ParsedSlideData slideData;
        private final Set<Integer> existingSpids;
        private final Map<String, String> relationships;
        private final int nextAvailableSpid;
        private final ShapeHierarchy shapeHierarchy;
        private final BulletPointContext bulletPointContext;
        
        public SlideContext(int slideNumber, ParsedSlideData slideData, 
                          Set<Integer> existingSpids, Map<String, String> relationships,
                          int nextAvailableSpid) {
            this.slideNumber = slideNumber;
            this.slideData = slideData;
            this.existingSpids = existingSpids;
            this.relationships = relationships;
            this.nextAvailableSpid = nextAvailableSpid;
            this.shapeHierarchy = analyzeShapeHierarchy(slideData.getShapeRegistry());
            this.bulletPointContext = analyzeBulletPointContext(slideData.getShapeRegistry());
        }
        
        // Getters
        public int getSlideNumber() { return slideNumber; }
        public ParsedSlideData getSlideData() { return slideData; }
        public Set<Integer> getExistingSpids() { return existingSpids; }
        public Map<String, String> getRelationships() { return relationships; }
        public int getNextAvailableSpid() { return nextAvailableSpid; }
        public ShapeHierarchy getShapeHierarchy() { return shapeHierarchy; }
        public BulletPointContext getBulletPointContext() { return bulletPointContext; }
        
        /**
         * Get shapes filtered by type
         */
        public List<SlideShape> getShapesByType(SlideShape.ShapeType type) {
            return slideData.getShapeRegistry().getShapesByType(type);
        }
        
        /**
         * Get only text-containing shapes
         */
        public List<SlideShape> getTextShapes() {
            return slideData.getShapeRegistry().getTextShapes();
        }
        
        /**
         * Get shapes grouped by their type
         */
        public Map<SlideShape.ShapeType, List<SlideShape>> getShapesGroupedByType() {
            Map<SlideShape.ShapeType, List<SlideShape>> grouped = new HashMap<>();
            for (SlideShape.ShapeType type : SlideShape.ShapeType.values()) {
                List<SlideShape> shapesOfType = getShapesByType(type);
                if (!shapesOfType.isEmpty()) {
                    grouped.put(type, shapesOfType);
                }
            }
            return grouped;
        }
    }
    
    /**
     * Global presentation context
     */
    public static class PresentationContext {
        private final List<Integer> slideNumbers;
        private final Map<Integer, SPIDManager.SPIDInfo> globalSpidRegistry;
        private final Map<String, String> mediaRelationships;
        private final List<String> availableThemes;
        private final List<String> availableLayouts;
        private final int nextAvailableSpid;
        
        public PresentationContext(List<Integer> slideNumbers,
                                 Map<Integer, SPIDManager.SPIDInfo> globalSpidRegistry,
                                 Map<String, String> mediaRelationships,
                                 List<String> availableThemes,
                                 List<String> availableLayouts,
                                 int nextAvailableSpid) {
            this.slideNumbers = slideNumbers;
            this.globalSpidRegistry = globalSpidRegistry;
            this.mediaRelationships = mediaRelationships;
            this.availableThemes = availableThemes;
            this.availableLayouts = availableLayouts;
            this.nextAvailableSpid = nextAvailableSpid;
        }
        
        // Getters
        public List<Integer> getSlideNumbers() { return slideNumbers; }
        public Map<Integer, SPIDManager.SPIDInfo> getGlobalSpidRegistry() { return globalSpidRegistry; }
        public Map<String, String> getMediaRelationships() { return mediaRelationships; }
        public List<String> getAvailableThemes() { return availableThemes; }
        public List<String> getAvailableLayouts() { return availableLayouts; }
        public int getNextAvailableSpid() { return nextAvailableSpid; }
    }
    
    /**
     * Context for a slide that will be created
     */
    public static class PredictedSlideContext {
        private final List<PredictedShape> predictedShapes;
        private final int startingSpid;
        
        public PredictedSlideContext(List<PredictedShape> predictedShapes, int startingSpid) {
            this.predictedShapes = predictedShapes;
            this.startingSpid = startingSpid;
        }
        
        public List<PredictedShape> getPredictedShapes() { return predictedShapes; }
        public int getStartingSpid() { return startingSpid; }
    }
    
    /**
     * Predicted shape information
     */
    public static class PredictedShape {
        private final int spid;
        private final String name;
        private final SlideShape.ShapeType type;
        private final String initialText;
        
        public PredictedShape(int spid, String name, SlideShape.ShapeType type, String initialText) {
            this.spid = spid;
            this.name = name;
            this.type = type;
            this.initialText = initialText;
        }
        
        public int getSpid() { return spid; }
        public String getName() { return name; }
        public SlideShape.ShapeType getType() { return type; }
        public String getInitialText() { return initialText; }
    }
    
    /**
     * Animation context with timing details
     */
    public static class AnimationContext {
        private final TimingTree timingTree;
        private final List<AnimationBinding> animationBindings;
        private final List<ClickSequence> clickSequences;
        private final List<AnimationGroup> animationGroups;
        
        public AnimationContext(TimingTree timingTree, 
                              List<AnimationBinding> animationBindings,
                              List<ClickSequence> clickSequences,
                              List<AnimationGroup> animationGroups) {
            this.timingTree = timingTree;
            this.animationBindings = animationBindings;
            this.clickSequences = clickSequences;
            this.animationGroups = animationGroups;
        }
        
        public TimingTree getTimingTree() { return timingTree; }
        public List<AnimationBinding> getAnimationBindings() { return animationBindings; }
        public List<ClickSequence> getClickSequences() { return clickSequences; }
        public List<AnimationGroup> getAnimationGroups() { return animationGroups; }
    }
    
    /**
     * Represents animations triggered by a single click
     */
    public static class ClickSequence {
        private final int clickNumber;
        private final List<AnimationBinding> animations;
        
        public ClickSequence(int clickNumber) {
            this.clickNumber = clickNumber;
            this.animations = new ArrayList<>();
        }
        
        public void addAnimation(AnimationBinding animation) {
            animations.add(animation);
        }
        
        public int getClickNumber() { return clickNumber; }
        public List<AnimationBinding> getAnimations() { return animations; }
    }
    
    /**
     * Group of animations that play together
     */
    public static class AnimationGroup {
        private final String groupId;
        private final List<AnimationBinding> animations;
        
        public AnimationGroup(String groupId) {
            this.groupId = groupId;
            this.animations = new ArrayList<>();
        }
        
        public void addAnimation(AnimationBinding animation) {
            animations.add(animation);
        }
        
        public String getGroupId() { return groupId; }
        public List<AnimationBinding> getAnimations() { return animations; }
    }
    
    /**
     * Hierarchical organization of shapes on a slide
     */
    public static class ShapeHierarchy {
        private final Map<SlideShape.ShapeType, List<SlideShape>> byType;
        private final Map<String, List<SlideShape>> byPosition;
        private final List<ShapeGroup> logicalGroups;
        
        public ShapeHierarchy(Map<SlideShape.ShapeType, List<SlideShape>> byType,
                            Map<String, List<SlideShape>> byPosition,
                            List<ShapeGroup> logicalGroups) {
            this.byType = byType;
            this.byPosition = byPosition;
            this.logicalGroups = logicalGroups;
        }
        
        public Map<SlideShape.ShapeType, List<SlideShape>> getByType() { return byType; }
        public Map<String, List<SlideShape>> getByPosition() { return byPosition; }
        public List<ShapeGroup> getLogicalGroups() { return logicalGroups; }
        
        /**
         * Get a summary of the shape organization
         */
        public String getSummary() {
            StringBuilder summary = new StringBuilder();
            summary.append("Shape organization: ");
            summary.append(byType.size()).append(" types, ");
            summary.append(byPosition.size()).append(" positions, ");
            summary.append(logicalGroups.size()).append(" logical groups");
            return summary.toString();
        }
    }
    
    /**
     * A logical grouping of related shapes
     */
    public static class ShapeGroup {
        private final String groupId;
        private final List<SlideShape> shapes;
        private final String description;
        
        public ShapeGroup(String groupId, List<SlideShape> shapes, String description) {
            this.groupId = groupId;
            this.shapes = new ArrayList<>(shapes);
            this.description = description;
        }
        
        public String getGroupId() { return groupId; }
        public List<SlideShape> getShapes() { return shapes; }
        public String getDescription() { return description; }
        
        /**
         * Get the primary shape (usually the first or most important)
         */
        public SlideShape getPrimaryShape() {
            return shapes.isEmpty() ? null : shapes.get(0);
        }
        
        /**
         * Check if this group contains a specific SPID
         */
        public boolean containsSpid(int spid) {
            return shapes.stream().anyMatch(s -> s.getSpid() == spid);
        }
    }
    
    /**
     * Analyze bullet point context for a shape registry
     */
    private static BulletPointContext analyzeBulletPointContext(ShapeRegistry registry) {
        Map<Integer, BulletShape> bulletShapes = new HashMap<>();
        List<BulletShape> allBulletShapes = new ArrayList<>();
        
        for (SlideShape shape : registry.getAllShapes()) {
            if (shape.hasBulletPoints()) {
                BulletShape bulletShape = new BulletShape(
                    shape.getSpid(),
                    shape.getName(),
                    shape.getBulletPointCount(),
                    shape.getParagraphMetadata().getBulletPointsOnly(),
                    shape.getParagraphMetadata().getBulletPointIndices()
                );
                bulletShapes.put(shape.getSpid(), bulletShape);
                allBulletShapes.add(bulletShape);
            }
        }
        
        return new BulletPointContext(bulletShapes, allBulletShapes);
    }
    
    /**
     * Context for bullet point information within a slide
     */
    public static class BulletPointContext {
        private final Map<Integer, BulletShape> bulletShapesBySpid;
        private final List<BulletShape> allBulletShapes;
        
        public BulletPointContext(Map<Integer, BulletShape> bulletShapes, List<BulletShape> allBulletShapes) {
            this.bulletShapesBySpid = bulletShapes;
            this.allBulletShapes = allBulletShapes;
        }
        
        // Getters
        public Map<Integer, BulletShape> getBulletShapesBySpid() { return bulletShapesBySpid; }
        public List<BulletShape> getAllBulletShapes() { return allBulletShapes; }
        
        /**
         * Get bullet shape by SPID
         */
        public BulletShape getBulletShape(int spid) {
            return bulletShapesBySpid.get(spid);
        }
        
        /**
         * Check if a shape contains bullet points
         */
        public boolean hasBulletPoints(int spid) {
            return bulletShapesBySpid.containsKey(spid);
        }
        
        /**
         * Get total number of shapes with bullet points
         */
        public int getBulletShapeCount() {
            return allBulletShapes.size();
        }
        
        /**
         * Get total number of bullet points across all shapes
         */
        public int getTotalBulletPointCount() {
            return allBulletShapes.stream()
                    .mapToInt(BulletShape::getBulletCount)
                    .sum();
        }
        
        /**
         * Get all bullet contents across all shapes
         */
        public List<String> getAllBulletContents() {
            return allBulletShapes.stream()
                    .flatMap(shape -> shape.getBulletContents().stream())
                    .collect(java.util.stream.Collectors.toList());
        }
    }
    
    /**
     * Information about a shape containing bullet points
     */
    public static class BulletShape {
        private final int spid;
        private final String name;
        private final int bulletCount;
        private final List<String> bulletContents;
        private final List<Integer> bulletIndices;
        
        public BulletShape(int spid, String name, int bulletCount, 
                          List<String> bulletContents, List<Integer> bulletIndices) {
            this.spid = spid;
            this.name = name;
            this.bulletCount = bulletCount;
            this.bulletContents = bulletContents;
            this.bulletIndices = bulletIndices;
        }
        
        // Getters
        public int getSpid() { return spid; }
        public String getName() { return name; }
        public int getBulletCount() { return bulletCount; }
        public List<String> getBulletContents() { return bulletContents; }
        public List<Integer> getBulletIndices() { return bulletIndices; }
        
        /**
         * Get specific bullet content by bullet index
         */
        public String getBulletContent(int bulletIndex) {
            if (bulletIndex < 0 || bulletIndex >= bulletContents.size()) {
                throw new IndexOutOfBoundsException("Bullet index out of range: " + bulletIndex);
            }
            return bulletContents.get(bulletIndex);
        }
        
        /**
         * Get paragraph index for a bullet index
         */
        public int getParagraphIndex(int bulletIndex) {
            if (bulletIndex < 0 || bulletIndex >= bulletIndices.size()) {
                throw new IndexOutOfBoundsException("Bullet index out of range: " + bulletIndex);
            }
            return bulletIndices.get(bulletIndex);
        }
        
        @Override
        public String toString() {
            return String.format("BulletShape{spid=%d, name='%s', bullets=%d}", 
                    spid, name, bulletCount);
        }
    }
}