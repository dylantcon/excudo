package com.excudo.core.geometry;

import com.excudo.core.model.SlideShape;
import com.excudo.core.model.ShapeGeometry;
import java.util.*;

/**
 * Spatial indexing system for efficient collision detection and spatial queries.
 * Uses a simple grid-based approach for fast lookup of shapes in spatial regions.
 */
public class SpatialIndex {
    
    // Grid cell size in EMUs (914400 EMUs = 1 inch)
    private static final long CELL_SIZE = 914400; // 1 inch cells
    
    // Grid structure: Map<CellKey, Set<SlideShape>>
    private final Map<String, Set<SlideShape>> spatialGrid;
    private final Set<SlideShape> allShapes;
    private final SATCollisionDetector satDetector;
    
    public SpatialIndex() {
        this.spatialGrid = new HashMap<>();
        this.allShapes = new HashSet<>();
        this.satDetector = new SATCollisionDetector();
    }
    
    /**
     * Add a shape to the spatial index
     */
    public void addShape(SlideShape shape) {
        if (shape == null || shape.getGeometry() == null) {
            return;
        }
        
        allShapes.add(shape);
        
        // Get the bounding box of the shape
        ShapeGeometry geometry = shape.getGeometry();
        long left = geometry.getX();
        long top = geometry.getY();
        long right = left + geometry.getWidth();
        long bottom = top + geometry.getHeight();
        
        // Find all grid cells that this shape intersects
        Set<String> cellKeys = getCellsInRegion(left, top, right, bottom);
        
        for (String cellKey : cellKeys) {
            spatialGrid.computeIfAbsent(cellKey, k -> new HashSet<>()).add(shape);
        }
    }
    
    /**
     * Remove a shape from the spatial index
     */
    public void removeShape(SlideShape shape) {
        if (shape == null) {
            return;
        }
        
        allShapes.remove(shape);
        
        // Remove from all grid cells
        spatialGrid.values().forEach(shapeSet -> shapeSet.remove(shape));
        
        // Clean up empty cells
        spatialGrid.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }
    
    /**
     * Find all shapes that potentially intersect with the given bounding box
     */
    public Set<SlideShape> getShapesInRegion(long left, long top, long right, long bottom) {
        Set<SlideShape> result = new HashSet<>();
        Set<String> cellKeys = getCellsInRegion(left, top, right, bottom);
        
        for (String cellKey : cellKeys) {
            Set<SlideShape> shapesInCell = spatialGrid.get(cellKey);
            if (shapesInCell != null) {
                result.addAll(shapesInCell);
            }
        }
        
        return result;
    }
    
    /**
     * Find all shapes that potentially intersect with the given shape
     */
    public Set<SlideShape> getShapesIntersecting(SlideShape shape) {
        if (shape == null || shape.getGeometry() == null) {
            return new HashSet<>();
        }
        
        ShapeGeometry geometry = shape.getGeometry();
        return getShapesInRegion(
            geometry.getX(),
            geometry.getY(),
            geometry.getX() + geometry.getWidth(),
            geometry.getY() + geometry.getHeight()
        );
    }
    
    /**
     * Check if a region is free of existing shapes
     */
    public boolean isRegionFree(long left, long top, long right, long bottom) {
        Set<SlideShape> shapesInRegion = getShapesInRegion(left, top, right, bottom);
        
        // Check actual intersection with each shape in the region
        for (SlideShape shape : shapesInRegion) {
            if (shape.getType().requiresSATCollision()) {
                // Use SAT collision detection for complex shapes
                if (satDetector.isShapeCollidingWithRegion(shape, left, top, 
                    right - left, bottom - top)) {
                    return false;
                }
            } else {
                // Use bounding box collision for simple shapes
                if (intersects(left, top, right, bottom, shape.getGeometry())) {
                    return false;
                }
            }
        }
        
        return true;
    }
    
    /**
     * Find the best position for a new shape of given dimensions using intelligent placement strategies.
     * Tries multiple placement preferences in order of desirability, with collision detection.
     * Returns null if no suitable position is found after trying all strategies.
     */
    public ShapeGeometry findBestPosition(long width, long height, long slideWidth, long slideHeight) {
        // Strategy 1: Try strategic positions first (corners, center, etc.)
        ShapeGeometry position = tryStrategicPlacements(width, height, slideWidth, slideHeight);
        if (position != null) {
            return position;
        }
        
        // Strategy 2: Try positions relative to existing content (below, right of content)
        position = tryRelativePlacements(width, height, slideWidth, slideHeight);
        if (position != null) {
            return position;
        }
        
        // Strategy 3: Find largest free rectangular area and place there
        position = tryLargestFreeArea(width, height, slideWidth, slideHeight);
        if (position != null) {
            return position;
        }
        
        // Strategy 4: Fallback to systematic grid search with collision detection
        return tryGridSearch(width, height, slideWidth, slideHeight);
    }
    
    /**
     * Try strategic placements (corners, center) that are typically aesthetically pleasing
     */
    private ShapeGeometry tryStrategicPlacements(long width, long height, long slideWidth, long slideHeight) {
        long margin = CELL_SIZE / 2; // 0.5 inch margin
        
        // Strategic positions to try in order of preference
        long[][] positions = {
            // Top-right corner (often good for icons)
            {slideWidth - width - margin, margin},
            // Bottom-right corner
            {slideWidth - width - margin, slideHeight - height - margin},
            // Top-left corner
            {margin, margin},
            // Bottom-left corner
            {margin, slideHeight - height - margin},
            // Center-right
            {slideWidth - width - margin, (slideHeight - height) / 2},
            // Center-left
            {margin, (slideHeight - height) / 2},
            // Top-center
            {(slideWidth - width) / 2, margin},
            // Bottom-center
            {(slideWidth - width) / 2, slideHeight - height - margin},
            // True center
            {(slideWidth - width) / 2, (slideHeight - height) / 2}
        };
        
        for (long[] pos : positions) {
            long x = pos[0], y = pos[1];
            if (x >= 0 && y >= 0 && x + width <= slideWidth && y + height <= slideHeight) {
                if (isRegionFree(x, y, x + width, y + height)) {
                    return new ShapeGeometry(x, y, width, height);
                }
            }
        }
        
        return null;
    }
    
    /**
     * Try positions relative to existing content (below existing shapes, to the right, etc.)
     */
    private ShapeGeometry tryRelativePlacements(long width, long height, long slideWidth, long slideHeight) {
        if (allShapes.isEmpty()) {
            return null;
        }
        
        long spacing = CELL_SIZE / 4; // 0.25 inch spacing
        
        // Try placing below and to the right of existing shapes
        for (SlideShape existingShape : allShapes) {
            ShapeGeometry geo = existingShape.getGeometry();
            if (geo == null) continue;
            
            // Try below the shape
            long x = geo.getX();
            long y = geo.getY() + geo.getHeight() + spacing;
            if (x + width <= slideWidth && y + height <= slideHeight) {
                if (isRegionFree(x, y, x + width, y + height)) {
                    return new ShapeGeometry(x, y, width, height);
                }
            }
            
            // Try to the right of the shape
            x = geo.getX() + geo.getWidth() + spacing;
            y = geo.getY();
            if (x + width <= slideWidth && y + height <= slideHeight) {
                if (isRegionFree(x, y, x + width, y + height)) {
                    return new ShapeGeometry(x, y, width, height);
                }
            }
        }
        
        return null;
    }
    
    /**
     * Find the largest free rectangular area and try to place the shape there
     */
    private ShapeGeometry tryLargestFreeArea(long width, long height, long slideWidth, long slideHeight) {
        // This is a simplified approach - a full implementation would use more sophisticated
        // algorithms like finding the largest empty rectangle in a 2D array
        
        long bestArea = 0;
        ShapeGeometry bestPosition = null;
        long stepSize = CELL_SIZE / 2; // Larger steps for this strategy
        
        for (long y = 0; y + height <= slideHeight; y += stepSize) {
            for (long x = 0; x + width <= slideWidth; x += stepSize) {
                if (isRegionFree(x, y, x + width, y + height)) {
                    // Calculate available area around this position
                    long availableArea = calculateAvailableArea(x, y, width, height, slideWidth, slideHeight);
                    if (availableArea > bestArea) {
                        bestArea = availableArea;
                        bestPosition = new ShapeGeometry(x, y, width, height);
                    }
                }
            }
        }
        
        return bestPosition;
    }
    
    /**
     * Calculate the available area around a potential position (for aesthetically pleasing placement)
     */
    private long calculateAvailableArea(long x, long y, long width, long height, long slideWidth, long slideHeight) {
        // Simple calculation of surrounding free space
        long leftSpace = x;
        long rightSpace = slideWidth - (x + width);
        long topSpace = y;
        long bottomSpace = slideHeight - (y + height);
        
        return leftSpace + rightSpace + topSpace + bottomSpace;
    }
    
    /**
     * Fallback grid search with smaller steps and collision detection
     */
    private ShapeGeometry tryGridSearch(long width, long height, long slideWidth, long slideHeight) {
        long stepSize = CELL_SIZE / 8; // Finer steps (1/8 inch) for thorough search
        
        for (long y = 0; y + height <= slideHeight; y += stepSize) {
            for (long x = 0; x + width <= slideWidth; x += stepSize) {
                if (isRegionFree(x, y, x + width, y + height)) {
                    return new ShapeGeometry(x, y, width, height);
                }
            }
        }
        
        return null; // Truly no free position found
    }
    
    /**
     * Check if a specific shape would collide with existing shapes
     * Uses SAT for complex shapes, bounding box for simple ones
     */
    public boolean wouldShapeCollide(SlideShape newShape) {
        if (newShape == null || newShape.getGeometry() == null) {
            return true;
        }
        
        Set<SlideShape> candidateShapes = getShapesIntersecting(newShape);
        
        for (SlideShape existingShape : candidateShapes) {
            if (newShape.getType().requiresSATCollision() || 
                existingShape.getType().requiresSATCollision()) {
                // Use SAT for complex shapes
                if (satDetector.areShapesColliding(newShape, existingShape)) {
                    return true;
                }
            } else {
                // Use bounding box for simple shapes
                if (intersectsGeometry(newShape.getGeometry(), existingShape.getGeometry())) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Clear all shapes from the index
     */
    public void clear() {
        spatialGrid.clear();
        allShapes.clear();
    }
    
    /**
     * Get all shapes in the index
     */
    public Set<SlideShape> getAllShapes() {
        return new HashSet<>(allShapes);
    }
    
    /**
     * Get the number of shapes in the index
     */
    public int getShapeCount() {
        return allShapes.size();
    }
    
    // ========== PRIVATE HELPER METHODS ==========
    
    private Set<String> getCellsInRegion(long left, long top, long right, long bottom) {
        Set<String> cellKeys = new HashSet<>();
        
        long minCellX = left / CELL_SIZE;
        long maxCellX = right / CELL_SIZE;
        long minCellY = top / CELL_SIZE;
        long maxCellY = bottom / CELL_SIZE;
        
        for (long cellY = minCellY; cellY <= maxCellY; cellY++) {
            for (long cellX = minCellX; cellX <= maxCellX; cellX++) {
                cellKeys.add(cellX + "," + cellY);
            }
        }
        
        return cellKeys;
    }
    
    private boolean intersects(long left1, long top1, long right1, long bottom1, ShapeGeometry geometry) {
        if (geometry == null) {
            return false;
        }
        
        long left2 = geometry.getX();
        long top2 = geometry.getY();
        long right2 = left2 + geometry.getWidth();
        long bottom2 = top2 + geometry.getHeight();
        
        // Check if rectangles intersect
        return !(left1 >= right2 || right1 <= left2 || top1 >= bottom2 || bottom1 <= top2);
    }
    
    private boolean intersectsGeometry(ShapeGeometry geo1, ShapeGeometry geo2) {
        if (geo1 == null || geo2 == null) {
            return false;
        }
        
        return !(geo1.getX() + geo1.getWidth() <= geo2.getX() ||
                geo2.getX() + geo2.getWidth() <= geo1.getX() ||
                geo1.getY() + geo1.getHeight() <= geo2.getY() ||
                geo2.getY() + geo2.getHeight() <= geo1.getY());
    }
}