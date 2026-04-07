package com.excudo.core.geometry;

import com.excudo.core.model.SlideShape;
import com.excudo.core.model.ParsedSlideData;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.utils.Logger;
import com.excudo.core.utils.ComponentLogger;
import java.util.*;

/**
 * Intelligent layout engine that analyzes existing slide content and finds
 * optimal positions for new elements while avoiding overlaps.
 */
public class IntelligentLayoutEngine {
    
    private static final ComponentLogger logger = Logger.getLogger(IntelligentLayoutEngine.class);
    
    // Standard PowerPoint slide dimensions in EMUs
    private static final long SLIDE_WIDTH_EMU = 9144000L;  // 10 inches
    private static final long SLIDE_HEIGHT_EMU = 6858000L; // 7.5 inches
    
    // Layout constraints
    private static final long MIN_MARGIN = 457200L;  // 0.5 inch margin
    private static final long MIN_SPACING = 228600L; // 0.25 inch minimum spacing between elements
    
    private final SpatialIndex spatialIndex;
    private final SATCollisionDetector satDetector;
    
    public IntelligentLayoutEngine() {
        this.spatialIndex = new SpatialIndex();
        this.satDetector = new SATCollisionDetector();
    }
    
    /**
     * Initialize the layout engine with existing slide content
     */
    public void initializeWithSlideData(ParsedSlideData slideData) {
        spatialIndex.clear();
        
        if (slideData != null && slideData.getShapeRegistry() != null) {
            for (SlideShape shape : slideData.getShapeRegistry().getAllShapes()) {
                spatialIndex.addShape(shape);
            }
        }
    }
    
    /**
     * Find the best position for a new shape, avoiding existing content
     */
    public ShapeGeometry findOptimalPosition(long width, long height, PlacementPreference preference) {
        // Add spacing buffer around the requested size
        long bufferedWidth = width + MIN_SPACING;
        long bufferedHeight = height + MIN_SPACING;
        
        ShapeGeometry position = null;
        
        switch (preference) {
            case TOP_LEFT:
                position = findPositionFromTopLeft(bufferedWidth, bufferedHeight);
                break;
            case TOP_RIGHT:
                position = findPositionFromTopRight(bufferedWidth, bufferedHeight);
                break;
            case BOTTOM_LEFT:
                position = findPositionFromBottomLeft(bufferedWidth, bufferedHeight);
                break;
            case BOTTOM_RIGHT:
                position = findPositionFromBottomRight(bufferedWidth, bufferedHeight);
                break;
            case CENTER:
                position = findPositionFromCenter(bufferedWidth, bufferedHeight);
                break;
            case BELOW_EXISTING:
                position = findPositionBelowExisting(bufferedWidth, bufferedHeight);
                break;
            case RIGHT_OF_EXISTING:
                position = findPositionRightOfExisting(bufferedWidth, bufferedHeight);
                break;
            default:
                position = spatialIndex.findBestPosition(bufferedWidth, bufferedHeight, 
                    SLIDE_WIDTH_EMU - MIN_MARGIN * 2, SLIDE_HEIGHT_EMU - MIN_MARGIN * 2);
        }
        
        // Remove spacing buffer from the final position
        if (position != null) {
            return new ShapeGeometry(
                position.getX() + MIN_SPACING / 2,
                position.getY() + MIN_SPACING / 2,
                width,
                height
            );
        }
        
        return null;
    }
    
    /**
     * Check if a position would cause overlaps
     */
    public boolean wouldCauseOverlap(ShapeGeometry geometry) {
        if (geometry == null) {
            return true;
        }
        
        return !spatialIndex.isRegionFree(
            geometry.getX(),
            geometry.getY(),
            geometry.getX() + geometry.getWidth(),
            geometry.getY() + geometry.getHeight()
        );
    }
    
    /**
     * Check if a shape would cause overlaps using SAT for complex shapes
     */
    public boolean wouldShapeCauseOverlap(SlideShape newShape) {
        if (newShape == null || newShape.getGeometry() == null) {
            return true;
        }
        
        // Check against all existing shapes
        Set<SlideShape> existingShapes = spatialIndex.getAllShapes();
        for (SlideShape existingShape : existingShapes) {
            if (satDetector.areShapesColliding(newShape, existingShape)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Add a new shape to the layout tracking
     */
    public void addShape(SlideShape shape) {
        spatialIndex.addShape(shape);
    }
    
    /**
     * Remove a shape from layout tracking
     */
    public void removeShape(SlideShape shape) {
        spatialIndex.removeShape(shape);
    }
    
    /**
     * Get layout analysis for the current slide
     */
    public LayoutAnalysis analyzeLayout() {
        Set<SlideShape> allShapes = spatialIndex.getAllShapes();
        
        if (allShapes.isEmpty()) {
            return new LayoutAnalysis(0, 0, SLIDE_WIDTH_EMU * SLIDE_HEIGHT_EMU, 
                Collections.emptyList(), Collections.emptyList());
        }
        
        // Calculate coverage
        long totalArea = SLIDE_WIDTH_EMU * SLIDE_HEIGHT_EMU;
        long coveredArea = calculateCoveredArea(allShapes);
        long freeArea = totalArea - coveredArea;
        
        // Find largest free rectangles
        List<ShapeGeometry> freeRegions = findLargestFreeRegions();
        
        // Detect potential overlaps
        List<OverlapInfo> overlaps = detectOverlaps(allShapes);
        
        return new LayoutAnalysis(allShapes.size(), coveredArea, freeArea, freeRegions, overlaps);
    }
    
    // ========== PLACEMENT STRATEGIES ==========
    
    private ShapeGeometry findPositionFromTopLeft(long width, long height) {
        long x = MIN_MARGIN;
        long y = MIN_MARGIN;
        
        while (y + height <= SLIDE_HEIGHT_EMU - MIN_MARGIN) {
            while (x + width <= SLIDE_WIDTH_EMU - MIN_MARGIN) {
                if (spatialIndex.isRegionFree(x, y, x + width, y + height)) {
                    return new ShapeGeometry(x, y, width, height);
                }
                x += MIN_SPACING;
            }
            x = MIN_MARGIN;
            y += MIN_SPACING;
        }
        
        return null;
    }
    
    private ShapeGeometry findPositionFromTopRight(long width, long height) {
        long x = SLIDE_WIDTH_EMU - MIN_MARGIN - width;
        long y = MIN_MARGIN;
        
        while (y + height <= SLIDE_HEIGHT_EMU - MIN_MARGIN) {
            while (x >= MIN_MARGIN) {
                if (spatialIndex.isRegionFree(x, y, x + width, y + height)) {
                    return new ShapeGeometry(x, y, width, height);
                }
                x -= MIN_SPACING;
            }
            x = SLIDE_WIDTH_EMU - MIN_MARGIN - width;
            y += MIN_SPACING;
        }
        
        return null;
    }
    
    private ShapeGeometry findPositionFromBottomLeft(long width, long height) {
        long x = MIN_MARGIN;
        long y = SLIDE_HEIGHT_EMU - MIN_MARGIN - height;
        
        while (y >= MIN_MARGIN) {
            while (x + width <= SLIDE_WIDTH_EMU - MIN_MARGIN) {
                if (spatialIndex.isRegionFree(x, y, x + width, y + height)) {
                    return new ShapeGeometry(x, y, width, height);
                }
                x += MIN_SPACING;
            }
            x = MIN_MARGIN;
            y -= MIN_SPACING;
        }
        
        return null;
    }
    
    private ShapeGeometry findPositionFromBottomRight(long width, long height) {
        long x = SLIDE_WIDTH_EMU - MIN_MARGIN - width;
        long y = SLIDE_HEIGHT_EMU - MIN_MARGIN - height;
        
        while (y >= MIN_MARGIN) {
            while (x >= MIN_MARGIN) {
                if (spatialIndex.isRegionFree(x, y, x + width, y + height)) {
                    return new ShapeGeometry(x, y, width, height);
                }
                x -= MIN_SPACING;
            }
            x = SLIDE_WIDTH_EMU - MIN_MARGIN - width;
            y -= MIN_SPACING;
        }
        
        return null;
    }
    
    private ShapeGeometry findPositionFromCenter(long width, long height) {
        long centerX = SLIDE_WIDTH_EMU / 2;
        long centerY = SLIDE_HEIGHT_EMU / 2;
        
        // Try concentric rectangles around the center
        for (long radius = 0; radius < Math.max(SLIDE_WIDTH_EMU, SLIDE_HEIGHT_EMU) / 2; radius += MIN_SPACING) {
            for (int angle = 0; angle < 360; angle += 45) {
                double rad = Math.toRadians(angle);
                long x = centerX + (long) (radius * Math.cos(rad)) - width / 2;
                long y = centerY + (long) (radius * Math.sin(rad)) - height / 2;
                
                if (x >= MIN_MARGIN && x + width <= SLIDE_WIDTH_EMU - MIN_MARGIN &&
                    y >= MIN_MARGIN && y + height <= SLIDE_HEIGHT_EMU - MIN_MARGIN) {
                    
                    if (spatialIndex.isRegionFree(x, y, x + width, y + height)) {
                        return new ShapeGeometry(x, y, width, height);
                    }
                }
            }
        }
        
        return null;
    }
    
    private ShapeGeometry findPositionBelowExisting(long width, long height) {
        Set<SlideShape> shapes = spatialIndex.getAllShapes();
        if (shapes.isEmpty()) {
            return findPositionFromTopLeft(width, height);
        }
        
        // Find the bottom-most shape
        long maxBottom = 0;
        for (SlideShape shape : shapes) {
            ShapeGeometry geo = shape.getGeometry();
            if (geo != null) {
                maxBottom = Math.max(maxBottom, geo.getY() + geo.getHeight());
            }
        }
        
        long y = maxBottom + MIN_SPACING;
        if (y + height <= SLIDE_HEIGHT_EMU - MIN_MARGIN) {
            long x = MIN_MARGIN;
            while (x + width <= SLIDE_WIDTH_EMU - MIN_MARGIN) {
                if (spatialIndex.isRegionFree(x, y, x + width, y + height)) {
                    return new ShapeGeometry(x, y, width, height);
                }
                x += MIN_SPACING;
            }
        }
        
        return findPositionFromTopLeft(width, height);
    }
    
    private ShapeGeometry findPositionRightOfExisting(long width, long height) {
        Set<SlideShape> shapes = spatialIndex.getAllShapes();
        if (shapes.isEmpty()) {
            return findPositionFromTopLeft(width, height);
        }
        
        // Find the right-most shape
        long maxRight = 0;
        for (SlideShape shape : shapes) {
            ShapeGeometry geo = shape.getGeometry();
            if (geo != null) {
                maxRight = Math.max(maxRight, geo.getX() + geo.getWidth());
            }
        }
        
        long x = maxRight + MIN_SPACING;
        if (x + width <= SLIDE_WIDTH_EMU - MIN_MARGIN) {
            long y = MIN_MARGIN;
            while (y + height <= SLIDE_HEIGHT_EMU - MIN_MARGIN) {
                if (spatialIndex.isRegionFree(x, y, x + width, y + height)) {
                    return new ShapeGeometry(x, y, width, height);
                }
                y += MIN_SPACING;
            }
        }
        
        return findPositionFromTopLeft(width, height);
    }
    
    // ========== ANALYSIS METHODS ==========
    
    private long calculateCoveredArea(Set<SlideShape> shapes) {
        // Simple approach: sum individual areas (doesn't account for overlaps)
        return shapes.stream()
            .mapToLong(shape -> {
                ShapeGeometry geo = shape.getGeometry();
                return geo != null ? geo.getWidth() * geo.getHeight() : 0;
            })
            .sum();
    }
    
    private List<ShapeGeometry> findLargestFreeRegions() {
        List<ShapeGeometry> freeRegions = new ArrayList<>();
        
        // Simple grid-based approach to find free rectangles
        long stepSize = MIN_SPACING * 2;
        
        for (long y = MIN_MARGIN; y < SLIDE_HEIGHT_EMU - MIN_MARGIN; y += stepSize) {
            for (long x = MIN_MARGIN; x < SLIDE_WIDTH_EMU - MIN_MARGIN; x += stepSize) {
                // Try to expand from this point
                ShapeGeometry region = expandFreeRegion(x, y, stepSize, stepSize);
                if (region != null && region.getWidth() >= stepSize && region.getHeight() >= stepSize) {
                    freeRegions.add(region);
                }
            }
        }
        
        // Sort by area, largest first
        freeRegions.sort((a, b) -> Long.compare(
            b.getWidth() * b.getHeight(),
            a.getWidth() * a.getHeight()
        ));
        
        int returnSize = Math.min(10, freeRegions.size());
        logger.debug("IntelligentLayoutEngine subList: freeRegions.size()=" + freeRegions.size() + ", returning subList(0, " + returnSize + ")");
        
        // ZEUS'S LIGHTNING: Strike down the subList bug!
        if (returnSize < 0) {
            logger.error("ZEUS STRIKES! returnSize is negative: " + returnSize + ", freeRegions.size()=" + freeRegions.size());
            throw new IllegalStateException("ZEUS FOUND THE BUG! returnSize=" + returnSize + " for list size=" + freeRegions.size());
        }
        
        try {
            return freeRegions.subList(0, returnSize);
        } catch (IndexOutOfBoundsException e) {
            logger.error("ZEUS'S THUNDER! subList failed: freeRegions.size()=" + freeRegions.size() + ", returnSize=" + returnSize);
            logger.error("ZEUS FOUND IT! subList(0, " + returnSize + ") failed on list of size " + freeRegions.size());
            throw e;
        }
    }
    
    private ShapeGeometry expandFreeRegion(long startX, long startY, long width, long height) {
        if (!spatialIndex.isRegionFree(startX, startY, startX + width, startY + height)) {
            return null;
        }
        
        // Try to expand the region
        long maxWidth = width;
        long maxHeight = height;
        
        // Expand width
        while (startX + maxWidth + MIN_SPACING <= SLIDE_WIDTH_EMU - MIN_MARGIN) {
            if (spatialIndex.isRegionFree(startX, startY, startX + maxWidth + MIN_SPACING, startY + maxHeight)) {
                maxWidth += MIN_SPACING;
            } else {
                break;
            }
        }
        
        // Expand height
        while (startY + maxHeight + MIN_SPACING <= SLIDE_HEIGHT_EMU - MIN_MARGIN) {
            if (spatialIndex.isRegionFree(startX, startY, startX + maxWidth, startY + maxHeight + MIN_SPACING)) {
                maxHeight += MIN_SPACING;
            } else {
                break;
            }
        }
        
        return new ShapeGeometry(startX, startY, maxWidth, maxHeight);
    }
    
    private List<OverlapInfo> detectOverlaps(Set<SlideShape> shapes) {
        List<OverlapInfo> overlaps = new ArrayList<>();
        List<SlideShape> shapeList = new ArrayList<>(shapes);
        
        for (int i = 0; i < shapeList.size(); i++) {
            for (int j = i + 1; j < shapeList.size(); j++) {
                SlideShape shape1 = shapeList.get(i);
                SlideShape shape2 = shapeList.get(j);
                
                if (shapesOverlap(shape1, shape2)) {
                    overlaps.add(new OverlapInfo(shape1, shape2, calculateOverlapArea(shape1, shape2)));
                }
            }
        }
        
        return overlaps;
    }
    
    private boolean shapesOverlap(SlideShape shape1, SlideShape shape2) {
        // Use SAT for complex shapes, bounding box for simple ones
        if (shape1.getType().requiresSATCollision() || shape2.getType().requiresSATCollision()) {
            return satDetector.areShapesColliding(shape1, shape2);
        }
        
        // Fallback to bounding box collision for simple shapes
        ShapeGeometry geo1 = shape1.getGeometry();
        ShapeGeometry geo2 = shape2.getGeometry();
        
        if (geo1 == null || geo2 == null) {
            return false;
        }
        
        return !(geo1.getX() + geo1.getWidth() <= geo2.getX() ||
                geo2.getX() + geo2.getWidth() <= geo1.getX() ||
                geo1.getY() + geo1.getHeight() <= geo2.getY() ||
                geo2.getY() + geo2.getHeight() <= geo1.getY());
    }
    
    private long calculateOverlapArea(SlideShape shape1, SlideShape shape2) {
        ShapeGeometry geo1 = shape1.getGeometry();
        ShapeGeometry geo2 = shape2.getGeometry();
        
        if (geo1 == null || geo2 == null) {
            return 0;
        }
        
        long overlapLeft = Math.max(geo1.getX(), geo2.getX());
        long overlapTop = Math.max(geo1.getY(), geo2.getY());
        long overlapRight = Math.min(geo1.getX() + geo1.getWidth(), geo2.getX() + geo2.getWidth());
        long overlapBottom = Math.min(geo1.getY() + geo1.getHeight(), geo2.getY() + geo2.getHeight());
        
        if (overlapLeft < overlapRight && overlapTop < overlapBottom) {
            return (overlapRight - overlapLeft) * (overlapBottom - overlapTop);
        }
        
        return 0;
    }
    
    // ========== INNER CLASSES ==========
    
    public enum PlacementPreference {
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, CENTER,
        BELOW_EXISTING, RIGHT_OF_EXISTING, AUTO
    }
    
    public static class LayoutAnalysis {
        private final int shapeCount;
        private final long coveredArea;
        private final long freeArea;
        private final List<ShapeGeometry> freeRegions;
        private final List<OverlapInfo> overlaps;
        
        public LayoutAnalysis(int shapeCount, long coveredArea, long freeArea,
                            List<ShapeGeometry> freeRegions, List<OverlapInfo> overlaps) {
            this.shapeCount = shapeCount;
            this.coveredArea = coveredArea;
            this.freeArea = freeArea;
            this.freeRegions = freeRegions;
            this.overlaps = overlaps;
        }
        
        // Getters
        public int getShapeCount() { return shapeCount; }
        public long getCoveredArea() { return coveredArea; }
        public long getFreeArea() { return freeArea; }
        public List<ShapeGeometry> getFreeRegions() { return freeRegions; }
        public List<OverlapInfo> getOverlaps() { return overlaps; }
        public boolean hasOverlaps() { return !overlaps.isEmpty(); }
        public double getCoveragePercentage() { 
            return (double) coveredArea / (SLIDE_WIDTH_EMU * SLIDE_HEIGHT_EMU) * 100; 
        }
    }
    
    public static class OverlapInfo {
        private final SlideShape shape1;
        private final SlideShape shape2;
        private final long overlapArea;
        
        public OverlapInfo(SlideShape shape1, SlideShape shape2, long overlapArea) {
            this.shape1 = shape1;
            this.shape2 = shape2;
            this.overlapArea = overlapArea;
        }
        
        public SlideShape getShape1() { return shape1; }
        public SlideShape getShape2() { return shape2; }
        public long getOverlapArea() { return overlapArea; }
    }
}