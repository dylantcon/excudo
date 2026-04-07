package com.excudo.core.layout;

import com.excudo.core.model.*;
import com.excudo.xml.parsers.SlideXMLParser;
import java.util.*;

/**
 * Analyzes slide layout and provides intelligent suggestions for content placement
 */
public class SlideLayoutAnalyzer {
    
    // Standard slide dimensions in EMUs
    private static final long SLIDE_WIDTH = 9144000L;  // 10 inches
    private static final long SLIDE_HEIGHT = 6858000L; // 7.5 inches
    
    // Common margin in EMUs (0.5 inch)
    private static final long MARGIN = 457200L;
    
    // Grid system for layout suggestions (12 columns, 8 rows)
    private static final int GRID_COLS = 12;
    private static final int GRID_ROWS = 8;
    
    /**
     * Analyze current slide layout and find available space
     */
    public LayoutAnalysis analyzeSlide(ParsedSlideData slideData) {
        LayoutAnalysis analysis = new LayoutAnalysis();
        
        // Create occupancy grid
        boolean[][] occupancyGrid = new boolean[GRID_ROWS][GRID_COLS];
        
        // Mark occupied areas
        for (ShapeData shape : slideData.getShapeRegistry().getAllShapes()) {
            if (shape.getGeometry() != null) {
                markOccupiedCells(occupancyGrid, shape.getGeometry());
            }
        }
        
        // Find available regions
        List<AvailableRegion> availableRegions = findAvailableRegions(occupancyGrid);
        analysis.setAvailableRegions(availableRegions);
        
        // Calculate layout metrics
        analysis.setOccupancyPercentage(calculateOccupancy(occupancyGrid));
        analysis.setLayoutBalance(calculateBalance(slideData.getShapeRegistry()));
        
        // Suggest optimal positions for new content
        analysis.setSuggestedPositions(suggestPositions(availableRegions));
        
        return analysis;
    }
    
    /**
     * Suggest optimal shape placement based on existing layout
     */
    public ShapeGeometry suggestShapePlacement(ParsedSlideData slideData, 
                                               long desiredWidth, 
                                               long desiredHeight) {
        LayoutAnalysis analysis = analyzeSlide(slideData);
        
        // Find best fit region
        for (AvailableRegion region : analysis.getAvailableRegions()) {
            if (region.canFit(desiredWidth, desiredHeight)) {
                // Center the shape in the region
                long x = region.getX() + (region.getWidth() - desiredWidth) / 2;
                long y = region.getY() + (region.getHeight() - desiredHeight) / 2;
                return new ShapeGeometry(x, y, desiredWidth, desiredHeight);
            }
        }
        
        // If no perfect fit, suggest scaling or alternative placement
        return suggestAlternativePlacement(analysis, desiredWidth, desiredHeight);
    }
    
    /**
     * Analyze layout balance (visual weight distribution)
     */
    private double calculateBalance(ShapeRegistry registry) {
        double leftWeight = 0;
        double rightWeight = 0;
        double centerLine = SLIDE_WIDTH / 2;
        
        for (ShapeData shape : registry.getAllShapes()) {
            if (shape.getGeometry() != null) {
                double shapeCenterX = shape.getGeometry().getX() + 
                                     shape.getGeometry().getWidth() / 2;
                double area = shape.getGeometry().getWidth() * 
                             shape.getGeometry().getHeight();
                
                if (shapeCenterX < centerLine) {
                    leftWeight += area;
                } else {
                    rightWeight += area;
                }
            }
        }
        
        // Return balance score (0 = perfectly balanced, 1 = completely unbalanced)
        double totalWeight = leftWeight + rightWeight;
        if (totalWeight == 0) return 0;
        
        return Math.abs(leftWeight - rightWeight) / totalWeight;
    }
    
    private void markOccupiedCells(boolean[][] grid, ShapeGeometry geometry) {
        int startCol = (int) ((geometry.getX() - MARGIN) * GRID_COLS / SLIDE_WIDTH);
        int endCol = (int) ((geometry.getX() + geometry.getWidth() + MARGIN) * GRID_COLS / SLIDE_WIDTH);
        int startRow = (int) ((geometry.getY() - MARGIN) * GRID_ROWS / SLIDE_HEIGHT);
        int endRow = (int) ((geometry.getY() + geometry.getHeight() + MARGIN) * GRID_ROWS / SLIDE_HEIGHT);
        
        // Clamp to grid bounds
        startCol = Math.max(0, Math.min(startCol, GRID_COLS - 1));
        endCol = Math.max(0, Math.min(endCol, GRID_COLS - 1));
        startRow = Math.max(0, Math.min(startRow, GRID_ROWS - 1));
        endRow = Math.max(0, Math.min(endRow, GRID_ROWS - 1));
        
        for (int row = startRow; row <= endRow; row++) {
            for (int col = startCol; col <= endCol; col++) {
                grid[row][col] = true;
            }
        }
    }
    
    private List<AvailableRegion> findAvailableRegions(boolean[][] grid) {
        List<AvailableRegion> regions = new ArrayList<>();
        
        // Find rectangular regions of available space
        for (int row = 0; row < GRID_ROWS; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                if (!grid[row][col]) {
                    // Find the largest rectangle starting at this cell
                    AvailableRegion region = findLargestRectangle(grid, row, col);
                    if (region.getArea() > 0) {
                        regions.add(region);
                        // Mark these cells as processed
                        markRegionAsProcessed(grid, region);
                    }
                }
            }
        }
        
        // Sort by area (largest first)
        regions.sort((a, b) -> Long.compare(b.getArea(), a.getArea()));
        return regions;
    }
    
    private AvailableRegion findLargestRectangle(boolean[][] grid, int startRow, int startCol) {
        int maxWidth = 0;
        int maxHeight = 0;
        
        // Find maximum width
        for (int col = startCol; col < GRID_COLS && !grid[startRow][col]; col++) {
            maxWidth++;
        }
        
        // Find maximum height that maintains width
        outerLoop:
        for (int row = startRow; row < GRID_ROWS; row++) {
            for (int col = startCol; col < startCol + maxWidth; col++) {
                if (grid[row][col]) {
                    break outerLoop;
                }
            }
            maxHeight++;
        }
        
        // Convert grid coordinates to EMUs
        long x = (long) (startCol * SLIDE_WIDTH / GRID_COLS);
        long y = (long) (startRow * SLIDE_HEIGHT / GRID_ROWS);
        long width = (long) (maxWidth * SLIDE_WIDTH / GRID_COLS);
        long height = (long) (maxHeight * SLIDE_HEIGHT / GRID_ROWS);
        
        return new AvailableRegion(x, y, width, height);
    }
    
    private void markRegionAsProcessed(boolean[][] grid, AvailableRegion region) {
        int startCol = (int) (region.getX() * GRID_COLS / SLIDE_WIDTH);
        int endCol = (int) ((region.getX() + region.getWidth()) * GRID_COLS / SLIDE_WIDTH);
        int startRow = (int) (region.getY() * GRID_ROWS / SLIDE_HEIGHT);
        int endRow = (int) ((region.getY() + region.getHeight()) * GRID_ROWS / SLIDE_HEIGHT);
        
        for (int row = startRow; row < endRow && row < GRID_ROWS; row++) {
            for (int col = startCol; col < endCol && col < GRID_COLS; col++) {
                grid[row][col] = true;
            }
        }
    }
    
    private double calculateOccupancy(boolean[][] grid) {
        int occupied = 0;
        int total = GRID_ROWS * GRID_COLS;
        
        for (int row = 0; row < GRID_ROWS; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                if (grid[row][col]) occupied++;
            }
        }
        
        return (double) occupied / total;
    }
    
    private List<ShapeGeometry> suggestPositions(List<AvailableRegion> regions) {
        List<ShapeGeometry> suggestions = new ArrayList<>();
        
        // Suggest common content sizes for top regions
        for (int i = 0; i < Math.min(3, regions.size()); i++) {
            AvailableRegion region = regions.get(i);
            
            // Title suggestion
            if (region.canFit(7772400L, 1470025L)) { // Standard title size
                suggestions.add(new ShapeGeometry(
                    region.getX() + MARGIN,
                    region.getY() + MARGIN,
                    7772400L, 1470025L
                ));
            }
            
            // Content block suggestion
            if (region.canFit(7772400L, 3500000L)) { // Standard content size
                suggestions.add(new ShapeGeometry(
                    region.getX() + MARGIN,
                    region.getY() + MARGIN,
                    7772400L, 3500000L
                ));
            }
        }
        
        return suggestions;
    }
    
    private ShapeGeometry suggestAlternativePlacement(LayoutAnalysis analysis,
                                                      long desiredWidth,
                                                      long desiredHeight) {
        // Find the largest available region
        if (!analysis.getAvailableRegions().isEmpty()) {
            AvailableRegion largest = analysis.getAvailableRegions().get(0);
            
            // Scale down if needed
            double scaleX = Math.min(1.0, (largest.getWidth() - 2 * MARGIN) / (double) desiredWidth);
            double scaleY = Math.min(1.0, (largest.getHeight() - 2 * MARGIN) / (double) desiredHeight);
            double scale = Math.min(scaleX, scaleY);
            
            long scaledWidth = (long) (desiredWidth * scale);
            long scaledHeight = (long) (desiredHeight * scale);
            
            // Center in the region
            long x = largest.getX() + (largest.getWidth() - scaledWidth) / 2;
            long y = largest.getY() + (largest.getHeight() - scaledHeight) / 2;
            
            return new ShapeGeometry(x, y, scaledWidth, scaledHeight);
        }
        
        // Default fallback position
        return new ShapeGeometry(MARGIN, MARGIN, desiredWidth, desiredHeight);
    }
}

class LayoutAnalysis {
    private List<AvailableRegion> availableRegions = new ArrayList<>();
    private List<ShapeGeometry> suggestedPositions = new ArrayList<>();
    private double occupancyPercentage;
    private double layoutBalance;
    
    // Getters and setters
    public List<AvailableRegion> getAvailableRegions() { return availableRegions; }
    public void setAvailableRegions(List<AvailableRegion> regions) { this.availableRegions = regions; }
    
    public List<ShapeGeometry> getSuggestedPositions() { return suggestedPositions; }
    public void setSuggestedPositions(List<ShapeGeometry> positions) { this.suggestedPositions = positions; }
    
    public double getOccupancyPercentage() { return occupancyPercentage; }
    public void setOccupancyPercentage(double percentage) { this.occupancyPercentage = percentage; }
    
    public double getLayoutBalance() { return layoutBalance; }
    public void setLayoutBalance(double balance) { this.layoutBalance = balance; }
    
    public boolean isOvercrowded() { return occupancyPercentage > 0.7; }
    public boolean isWellBalanced() { return layoutBalance < 0.2; }
}

class AvailableRegion {
    private final long x, y, width, height;
    
    public AvailableRegion(long x, long y, long width, long height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }
    
    public long getX() { return x; }
    public long getY() { return y; }
    public long getWidth() { return width; }
    public long getHeight() { return height; }
    public long getArea() { return width * height; }
    
    public boolean canFit(long reqWidth, long reqHeight) {
        return width >= reqWidth && height >= reqHeight;
    }
}