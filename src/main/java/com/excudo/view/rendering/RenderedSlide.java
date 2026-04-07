package com.excudo.view.rendering;

import javafx.scene.shape.Shape;
import javafx.geometry.Rectangle2D;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import java.util.*;

/**
 * Container for a completely rendered PowerPoint slide.
 * Holds all rendered shapes, metadata, and performance information.
 */
public class RenderedSlide {
    
    private final Document sourceDocument;
    private final String slideId;
    private final long creationTime;
    private final Map<Element, Shape> renderedShapes;
    private final Map<String, Object> metadata;
    private final List<Shape> cachedShapes;
    private final RenderingStatistics statistics;
    
    // Slide properties
    private Rectangle2D slideBounds;
    private String slideTitle;
    private int slideNumber;
    
    // Validity tracking
    private boolean valid;
    private long lastAccessTime;
    
    public RenderedSlide(Document sourceDocument) {
        this.sourceDocument = sourceDocument;
        this.slideId = generateSlideId(sourceDocument);
        this.creationTime = System.currentTimeMillis();
        this.renderedShapes = new LinkedHashMap<>(); // Preserve rendering order
        this.metadata = new HashMap<>();
        this.cachedShapes = new ArrayList<>();
        this.statistics = new RenderingStatistics();
        this.valid = true;
        this.lastAccessTime = System.currentTimeMillis();
        
        // Initialize slide bounds (PowerPoint standard size)
        this.slideBounds = new Rectangle2D(0, 0, 720, 540); // 10" x 7.5" at 72 DPI
    }
    
    // ========== SHAPE MANAGEMENT ==========
    
    /**
     * Add a rendered shape to the slide
     */
    public void addRenderedShape(Element sourceElement, Shape renderedShape) {
        if (sourceElement != null && renderedShape != null) {
            renderedShapes.put(sourceElement, renderedShape);
            cachedShapes.add(renderedShape);
            statistics.incrementShapeCount();
        }
    }
    
    /**
     * Get rendered shape for a source element
     */
    public Shape getRenderedShape(Element sourceElement) {
        updateAccessTime();
        return renderedShapes.get(sourceElement);
    }
    
    /**
     * Get all rendered shapes
     */
    public Map<Element, Shape> getRenderedShapes() {
        updateAccessTime();
        return Collections.unmodifiableMap(renderedShapes);
    }
    
    /**
     * Get cached shapes in rendering order
     */
    public List<Shape> getCachedShapes() {
        updateAccessTime();
        return Collections.unmodifiableList(cachedShapes);
    }
    
    /**
     * Remove a rendered shape
     */
    public boolean removeRenderedShape(Element sourceElement) {
        Shape removed = renderedShapes.remove(sourceElement);
        if (removed != null) {
            cachedShapes.remove(removed);
            statistics.decrementShapeCount();
            return true;
        }
        return false;
    }
    
    /**
     * Clear all rendered shapes
     */
    public void clearRenderedShapes() {
        renderedShapes.clear();
        cachedShapes.clear();
        statistics.resetShapeCount();
    }
    
    // ========== METADATA MANAGEMENT ==========
    
    /**
     * Set slide metadata
     */
    public void setMetadata(String key, Object value) {
        metadata.put(key, value);
    }
    
    /**
     * Get slide metadata
     */
    public Object getMetadata(String key) {
        updateAccessTime();
        return metadata.get(key);
    }
    
    /**
     * Get all metadata
     */
    public Map<String, Object> getAllMetadata() {
        updateAccessTime();
        return Collections.unmodifiableMap(metadata);
    }
    
    // ========== VALIDITY AND CACHING ==========
    
    /**
     * Check if the rendered slide is still valid
     */
    public boolean isValid() {
        updateAccessTime();
        return valid && (System.currentTimeMillis() - creationTime) < getCacheValidityTime();
    }
    
    /**
     * Invalidate the rendered slide
     */
    public void invalidate() {
        this.valid = false;
    }
    
    /**
     * Get cache validity time in milliseconds
     */
    private long getCacheValidityTime() {
        // Default to 5 minutes
        return 5 * 60 * 1000;
    }
    
    /**
     * Update last access time
     */
    private void updateAccessTime() {
        this.lastAccessTime = System.currentTimeMillis();
    }
    
    // ========== SEARCH AND QUERY ==========
    
    /**
     * Find shapes by SPID
     */
    public List<Shape> findShapesBySpid(String spid) {
        List<Shape> matching = new ArrayList<>();
        
        for (Map.Entry<Element, Shape> entry : renderedShapes.entrySet()) {
            Element element = entry.getKey();
            if (spid.equals(extractSpid(element))) {
                matching.add(entry.getValue());
            }
        }
        
        return matching;
    }
    
    /**
     * Find shapes by type
     */
    public List<Shape> findShapesByType(String shapeType) {
        List<Shape> matching = new ArrayList<>();
        
        for (Map.Entry<Element, Shape> entry : renderedShapes.entrySet()) {
            Element element = entry.getKey();
            if (shapeType.equals(element.getTagName())) {
                matching.add(entry.getValue());
            }
        }
        
        return matching;
    }
    
    /**
     * Find shapes by name
     */
    public List<Shape> findShapesByName(String shapeName) {
        List<Shape> matching = new ArrayList<>();
        
        for (Map.Entry<Element, Shape> entry : renderedShapes.entrySet()) {
            Element element = entry.getKey();
            if (shapeName.equals(extractShapeName(element))) {
                matching.add(entry.getValue());
            }
        }
        
        return matching;
    }
    
    /**
     * Find source element for a rendered shape
     */
    public Element findSourceElement(Shape renderedShape) {
        for (Map.Entry<Element, Shape> entry : renderedShapes.entrySet()) {
            if (entry.getValue() == renderedShape) {
                return entry.getKey();
            }
        }
        return null;
    }
    
    // ========== STATISTICS AND ANALYSIS ==========
    
    /**
     * Get rendering statistics
     */
    public RenderingStatistics getStatistics() {
        updateAccessTime();
        return statistics;
    }
    
    /**
     * Calculate slide complexity score
     */
    public double calculateComplexityScore() {
        double score = 0.0;
        
        // Base score from shape count
        score += renderedShapes.size() * 1.0;
        
        // Additional score from shape complexity
        for (Map.Entry<Element, Shape> entry : renderedShapes.entrySet()) {
            Element element = entry.getKey();
            score += calculateShapeComplexity(element);
        }
        
        return score;
    }
    
    /**
     * Calculate estimated memory usage
     */
    public long calculateEstimatedMemoryUsage() {
        long usage = 0;
        
        // Base slide overhead
        usage += 1024; // 1KB base
        
        // Shape overhead
        usage += renderedShapes.size() * 512; // 512 bytes per shape
        
        // Metadata overhead
        usage += metadata.size() * 128; // 128 bytes per metadata entry
        
        return usage;
    }
    
    /**
     * Get slide performance metrics
     */
    public SlidePerformanceMetrics getPerformanceMetrics() {
        return new SlidePerformanceMetrics(
            creationTime,
            lastAccessTime,
            renderedShapes.size(),
            calculateComplexityScore(),
            calculateEstimatedMemoryUsage(),
            statistics.getTotalRenderingTime()
        );
    }
    
    // ========== UTILITY METHODS ==========
    
    private String generateSlideId(Document document) {
        return "slide_" + System.currentTimeMillis() + "_" + document.hashCode();
    }
    
    private String extractSpid(Element element) {
        try {
            // Look for cNvPr element with id attribute
            return element.getAttribute("id");
        } catch (Exception e) {
            return null;
        }
    }
    
    private String extractShapeName(Element element) {
        try {
            // Look for cNvPr element with name attribute
            return element.getAttribute("name");
        } catch (Exception e) {
            return "Unknown";
        }
    }
    
    private double calculateShapeComplexity(Element element) {
        double complexity = 1.0; // Base complexity
        
        // Add complexity for custom geometry
        if (hasCustomGeometry(element)) {
            complexity += 2.0;
        }
        
        // Add complexity for effects
        if (hasVisualEffects(element)) {
            complexity += 1.0;
        }
        
        // Add complexity for text content
        if (hasTextContent(element)) {
            complexity += 0.5;
        }
        
        return complexity;
    }
    
    private boolean hasCustomGeometry(Element element) {
        // Check for custom geometry (simplified)
        return element.getTagName().contains("custGeom");
    }
    
    private boolean hasVisualEffects(Element element) {
        // Check for visual effects (simplified)
        return element.toString().contains("effectLst") || element.toString().contains("outerShdw");
    }
    
    private boolean hasTextContent(Element element) {
        // Check for text content (simplified)
        return element.toString().contains("txBody");
    }
    
    // ========== GETTERS AND SETTERS ==========
    
    public Document getSourceDocument() {
        updateAccessTime();
        return sourceDocument;
    }
    
    public String getSlideId() {
        return slideId;
    }
    
    public long getCreationTime() {
        return creationTime;
    }
    
    public long getLastAccessTime() {
        return lastAccessTime;
    }
    
    public Rectangle2D getSlideBounds() {
        return slideBounds;
    }
    
    public void setSlideBounds(Rectangle2D bounds) {
        this.slideBounds = bounds;
    }
    
    public String getSlideTitle() {
        updateAccessTime();
        return slideTitle;
    }
    
    public void setSlideTitle(String title) {
        this.slideTitle = title;
    }
    
    public int getSlideNumber() {
        return slideNumber;
    }
    
    public void setSlideNumber(int number) {
        this.slideNumber = number;
    }
    
    public int getShapeCount() {
        return renderedShapes.size();
    }
    
    // ========== NESTED CLASSES ==========
    
    /**
     * Rendering statistics for the slide
     */
    public static class RenderingStatistics {
        private int shapeCount = 0;
        private long totalRenderingTime = 0;
        private long geometryProcessingTime = 0;
        private long effectProcessingTime = 0;
        private long textRenderingTime = 0;
        private int cacheHits = 0;
        private int cacheMisses = 0;
        
        public void incrementShapeCount() { shapeCount++; }
        public void decrementShapeCount() { shapeCount = Math.max(0, shapeCount - 1); }
        public void resetShapeCount() { shapeCount = 0; }
        
        public void addRenderingTime(long time) { totalRenderingTime += time; }
        public void addGeometryTime(long time) { geometryProcessingTime += time; }
        public void addEffectTime(long time) { effectProcessingTime += time; }
        public void addTextTime(long time) { textRenderingTime += time; }
        
        public void incrementCacheHits() { cacheHits++; }
        public void incrementCacheMisses() { cacheMisses++; }
        
        // Getters
        public int getShapeCount() { return shapeCount; }
        public long getTotalRenderingTime() { return totalRenderingTime; }
        public long getGeometryProcessingTime() { return geometryProcessingTime; }
        public long getEffectProcessingTime() { return effectProcessingTime; }
        public long getTextRenderingTime() { return textRenderingTime; }
        public int getCacheHits() { return cacheHits; }
        public int getCacheMisses() { return cacheMisses; }
        
        public double getCacheHitRatio() {
            int total = cacheHits + cacheMisses;
            return total > 0 ? (double) cacheHits / total : 0.0;
        }
    }
    
    /**
     * Performance metrics for the slide
     */
    public static class SlidePerformanceMetrics {
        private final long creationTime;
        private final long lastAccessTime;
        private final int shapeCount;
        private final double complexityScore;
        private final long estimatedMemoryUsage;
        private final long totalRenderingTime;
        
        public SlidePerformanceMetrics(long creationTime, long lastAccessTime, int shapeCount,
                                     double complexityScore, long estimatedMemoryUsage, long totalRenderingTime) {
            this.creationTime = creationTime;
            this.lastAccessTime = lastAccessTime;
            this.shapeCount = shapeCount;
            this.complexityScore = complexityScore;
            this.estimatedMemoryUsage = estimatedMemoryUsage;
            this.totalRenderingTime = totalRenderingTime;
        }
        
        public long getCreationTime() { return creationTime; }
        public long getLastAccessTime() { return lastAccessTime; }
        public int getShapeCount() { return shapeCount; }
        public double getComplexityScore() { return complexityScore; }
        public long getEstimatedMemoryUsage() { return estimatedMemoryUsage; }
        public long getTotalRenderingTime() { return totalRenderingTime; }
        
        public long getAgeInMillis() {
            return System.currentTimeMillis() - creationTime;
        }
        
        public long getTimeSinceLastAccess() {
            return System.currentTimeMillis() - lastAccessTime;
        }
        
        public double getAverageRenderingTimePerShape() {
            return shapeCount > 0 ? (double) totalRenderingTime / shapeCount : 0.0;
        }
    }
}