package com.excudo.view.precision;

import com.excudo.view.rendering.CoordinateMapper;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;

/**
 * Precision grid overlay and snapping system for surgical precision editing.
 * Provides visual grid, rulers, guides, and coordinate snapping functionality.
 */
public class CoordinateGridView {
    
    // ========== GRID CONFIGURATION ==========
    
    public enum GridType {
        DOTS,
        LINES,
        CROSSES
    }
    
    public enum GridUnit {
        PIXELS(1.0, "px"),
        POINTS(1.333, "pt"),    // 1 point = 4/3 pixels at 96 DPI
        INCHES(96.0, "in"),     // 1 inch = 96 pixels at 96 DPI
        CM(37.795, "cm"),       // 1 cm = 37.795 pixels at 96 DPI
        MM(3.7795, "mm");       // 1 mm = 3.7795 pixels at 96 DPI
        
        private final double pixelsPerUnit;
        private final String symbol;
        
        GridUnit(double pixelsPerUnit, String symbol) {
            this.pixelsPerUnit = pixelsPerUnit;
            this.symbol = symbol;
        }
        
        public double getPixelsPerUnit() { return pixelsPerUnit; }
        public String getSymbol() { return symbol; }
    }
    
    // ========== STATE ==========
    
    private Canvas canvas;
    private CoordinateMapper coordinateMapper;
    
    // Grid settings
    private boolean gridEnabled = false;
    private boolean rulersEnabled = false;
    private boolean snapToGridEnabled = false;
    private GridType gridType = GridType.LINES;
    private GridUnit gridUnit = GridUnit.PIXELS;
    private double gridSpacing = 10.0; // in grid units
    private double zoomLevel = 1.0;
    
    // Visual settings
    private Color gridColor = Color.rgb(200, 200, 200, 0.5);
    private Color majorGridColor = Color.rgb(150, 150, 150, 0.7);
    private Color rulerColor = Color.rgb(100, 100, 100, 0.8);
    private Color snapIndicatorColor = Color.rgb(0, 120, 255, 0.8);
    private double gridLineWidth = 0.5;
    private double majorGridLineWidth = 1.0;
    private int majorGridInterval = 5; // Every 5th line is major
    
    // Ruler settings
    private double rulerHeight = 20.0;
    private double rulerWidth = 20.0;
    private Color rulerBackgroundColor = Color.rgb(240, 240, 240, 0.9);
    private Color rulerTextColor = Color.BLACK;
    
    // Snap tolerance
    private double snapTolerance = 5.0; // pixels
    
    public CoordinateGridView(Canvas canvas, CoordinateMapper coordinateMapper) {
        this.canvas = canvas;
        this.coordinateMapper = coordinateMapper;
    }
    
    // ========== GRID RENDERING ==========
    
    /**
     * Render the complete grid system (grid, rulers, guides)
     */
    public void render() {
        if (canvas == null) return;
        
        GraphicsContext gc = canvas.getGraphicsContext2D();
        
        // Save current graphics state
        gc.save();
        
        try {
            // Render rulers first (background)
            if (rulersEnabled) {
                renderRulers(gc);
            }
            
            // Render grid
            if (gridEnabled) {
                renderGrid(gc);
            }
            
        } finally {
            gc.restore();
        }
    }
    
    /**
     * Render the coordinate grid
     */
    private void renderGrid(GraphicsContext gc) {
        Rectangle2D viewport = getViewport();
        double effectiveSpacing = gridSpacing * gridUnit.getPixelsPerUnit() * zoomLevel;
        
        // Skip rendering if grid would be too dense or too sparse
        if (effectiveSpacing < 2.0 || effectiveSpacing > 1000.0) {
            return;
        }
        
        switch (gridType) {
            case LINES:
                renderGridLines(gc, viewport, effectiveSpacing);
                break;
            case DOTS:
                renderGridDots(gc, viewport, effectiveSpacing);
                break;
            case CROSSES:
                renderGridCrosses(gc, viewport, effectiveSpacing);
                break;
        }
    }
    
    /**
     * Render grid as lines
     */
    private void renderGridLines(GraphicsContext gc, Rectangle2D viewport, double spacing) {
        gc.setLineWidth(gridLineWidth);
        
        // Calculate grid bounds
        double startX = Math.floor(viewport.getMinX() / spacing) * spacing;
        double startY = Math.floor(viewport.getMinY() / spacing) * spacing;
        double endX = viewport.getMaxX();
        double endY = viewport.getMaxY();
        
        // Render vertical lines
        for (double x = startX; x <= endX; x += spacing) {
            int lineNumber = (int) Math.round(x / spacing);
            boolean isMajor = (lineNumber % majorGridInterval) == 0;
            
            gc.setStroke(isMajor ? majorGridColor : gridColor);
            gc.setLineWidth(isMajor ? majorGridLineWidth : gridLineWidth);
            
            gc.strokeLine(x, viewport.getMinY(), x, viewport.getMaxY());
        }
        
        // Render horizontal lines
        for (double y = startY; y <= endY; y += spacing) {
            int lineNumber = (int) Math.round(y / spacing);
            boolean isMajor = (lineNumber % majorGridInterval) == 0;
            
            gc.setStroke(isMajor ? majorGridColor : gridColor);
            gc.setLineWidth(isMajor ? majorGridLineWidth : gridLineWidth);
            
            gc.strokeLine(viewport.getMinX(), y, viewport.getMaxX(), y);
        }
    }
    
    /**
     * Render grid as dots
     */
    private void renderGridDots(GraphicsContext gc, Rectangle2D viewport, double spacing) {
        gc.setFill(gridColor);
        
        double startX = Math.floor(viewport.getMinX() / spacing) * spacing;
        double startY = Math.floor(viewport.getMinY() / spacing) * spacing;
        double endX = viewport.getMaxX();
        double endY = viewport.getMaxY();
        
        double dotSize = Math.max(1.0, gridLineWidth * 2);
        
        for (double x = startX; x <= endX; x += spacing) {
            for (double y = startY; y <= endY; y += spacing) {
                int lineX = (int) Math.round(x / spacing);
                int lineY = (int) Math.round(y / spacing);
                boolean isMajor = (lineX % majorGridInterval) == 0 && (lineY % majorGridInterval) == 0;
                
                gc.setFill(isMajor ? majorGridColor : gridColor);
                double currentDotSize = isMajor ? dotSize * 1.5 : dotSize;
                
                gc.fillOval(x - currentDotSize / 2, y - currentDotSize / 2, currentDotSize, currentDotSize);
            }
        }
    }
    
    /**
     * Render grid as crosses
     */
    private void renderGridCrosses(GraphicsContext gc, Rectangle2D viewport, double spacing) {
        gc.setStroke(gridColor);
        gc.setLineWidth(gridLineWidth);
        
        double startX = Math.floor(viewport.getMinX() / spacing) * spacing;
        double startY = Math.floor(viewport.getMinY() / spacing) * spacing;
        double endX = viewport.getMaxX();
        double endY = viewport.getMaxY();
        
        double crossSize = Math.max(2.0, spacing / 10.0);
        
        for (double x = startX; x <= endX; x += spacing) {
            for (double y = startY; y <= endY; y += spacing) {
                int lineX = (int) Math.round(x / spacing);
                int lineY = (int) Math.round(y / spacing);
                boolean isMajor = (lineX % majorGridInterval) == 0 && (lineY % majorGridInterval) == 0;
                
                gc.setStroke(isMajor ? majorGridColor : gridColor);
                gc.setLineWidth(isMajor ? majorGridLineWidth : gridLineWidth);
                
                double currentCrossSize = isMajor ? crossSize * 1.5 : crossSize;
                
                // Draw cross
                gc.strokeLine(x - currentCrossSize, y, x + currentCrossSize, y);
                gc.strokeLine(x, y - currentCrossSize, x, y + currentCrossSize);
            }
        }
    }
    
    /**
     * Render rulers along the edges
     */
    private void renderRulers(GraphicsContext gc) {
        Rectangle2D viewport = getViewport();
        
        // Render horizontal ruler
        renderHorizontalRuler(gc, viewport);
        
        // Render vertical ruler
        renderVerticalRuler(gc, viewport);
        
        // Render corner
        renderRulerCorner(gc);
    }
    
    /**
     * Render horizontal ruler
     */
    private void renderHorizontalRuler(GraphicsContext gc, Rectangle2D viewport) {
        gc.setFill(rulerBackgroundColor);
        gc.fillRect(rulerWidth, 0, viewport.getWidth() - rulerWidth, rulerHeight);
        
        gc.setStroke(rulerColor);
        gc.setLineWidth(1.0);
        gc.strokeRect(rulerWidth, 0, viewport.getWidth() - rulerWidth, rulerHeight);
        
        // Draw tick marks and labels
        double spacing = gridSpacing * gridUnit.getPixelsPerUnit() * zoomLevel;
        if (spacing >= 10.0) { // Only draw if readable
            double startX = Math.floor(viewport.getMinX() / spacing) * spacing;
            double endX = viewport.getMaxX();
            
            gc.setFill(rulerTextColor);
            gc.setStroke(rulerTextColor);
            
            for (double x = startX; x <= endX; x += spacing) {
                if (x >= rulerWidth) {
                    int tickNumber = (int) Math.round(x / spacing);
                    boolean isMajor = (tickNumber % majorGridInterval) == 0;
                    
                    double tickHeight = isMajor ? rulerHeight * 0.7 : rulerHeight * 0.4;
                    gc.strokeLine(x, rulerHeight - tickHeight, x, rulerHeight);
                    
                    if (isMajor && spacing > 20) {
                        String label = formatCoordinate(x, gridUnit);
                        gc.fillText(label, x + 2, rulerHeight - 5);
                    }
                }
            }
        }
    }
    
    /**
     * Render vertical ruler
     */
    private void renderVerticalRuler(GraphicsContext gc, Rectangle2D viewport) {
        gc.setFill(rulerBackgroundColor);
        gc.fillRect(0, rulerHeight, rulerWidth, viewport.getHeight() - rulerHeight);
        
        gc.setStroke(rulerColor);
        gc.setLineWidth(1.0);
        gc.strokeRect(0, rulerHeight, rulerWidth, viewport.getHeight() - rulerHeight);
        
        // Draw tick marks and labels
        double spacing = gridSpacing * gridUnit.getPixelsPerUnit() * zoomLevel;
        if (spacing >= 10.0) { // Only draw if readable
            double startY = Math.floor(viewport.getMinY() / spacing) * spacing;
            double endY = viewport.getMaxY();
            
            gc.setFill(rulerTextColor);
            gc.setStroke(rulerTextColor);
            
            for (double y = startY; y <= endY; y += spacing) {
                if (y >= rulerHeight) {
                    int tickNumber = (int) Math.round(y / spacing);
                    boolean isMajor = (tickNumber % majorGridInterval) == 0;
                    
                    double tickWidth = isMajor ? rulerWidth * 0.7 : rulerWidth * 0.4;
                    gc.strokeLine(rulerWidth - tickWidth, y, rulerWidth, y);
                    
                    if (isMajor && spacing > 20) {
                        String label = formatCoordinate(y, gridUnit);
                        gc.save();
                        gc.translate(5, y - 2);
                        gc.rotate(-90);
                        gc.fillText(label, 0, 0);
                        gc.restore();
                    }
                }
            }
        }
    }
    
    /**
     * Render ruler corner
     */
    private void renderRulerCorner(GraphicsContext gc) {
        gc.setFill(rulerBackgroundColor);
        gc.fillRect(0, 0, rulerWidth, rulerHeight);
        
        gc.setStroke(rulerColor);
        gc.setLineWidth(1.0);
        gc.strokeRect(0, 0, rulerWidth, rulerHeight);
        
        // Draw unit symbol
        gc.setFill(rulerTextColor);
        gc.fillText(gridUnit.getSymbol(), 3, rulerHeight - 3);
    }
    
    // ========== SNAP TO GRID ==========
    
    /**
     * Snap point to nearest grid intersection
     */
    public Point2D snapToGrid(Point2D point) {
        if (!snapToGridEnabled) {
            return point;
        }
        
        double spacing = gridSpacing * gridUnit.getPixelsPerUnit() * zoomLevel;
        
        double snappedX = Math.round(point.getX() / spacing) * spacing;
        double snappedY = Math.round(point.getY() / spacing) * spacing;
        
        // Only snap if within tolerance
        double deltaX = Math.abs(point.getX() - snappedX);
        double deltaY = Math.abs(point.getY() - snappedY);
        
        double finalX = (deltaX <= snapTolerance) ? snappedX : point.getX();
        double finalY = (deltaY <= snapTolerance) ? snappedY : point.getY();
        
        return new Point2D(finalX, finalY);
    }
    
    /**
     * Check if point would snap to grid
     */
    public boolean wouldSnapToGrid(Point2D point) {
        if (!snapToGridEnabled) {
            return false;
        }
        
        Point2D snapped = snapToGrid(point);
        return !snapped.equals(point);
    }
    
    /**
     * Render snap indicator at point
     */
    public void renderSnapIndicator(Point2D point) {
        if (!snapToGridEnabled || canvas == null) {
            return;
        }
        
        Point2D snapped = snapToGrid(point);
        if (!snapped.equals(point)) {
            GraphicsContext gc = canvas.getGraphicsContext2D();
            
            gc.save();
            try {
                gc.setStroke(snapIndicatorColor);
                gc.setLineWidth(2.0);
                
                // Draw crosshair at snap point
                double size = 8.0;
                gc.strokeLine(snapped.getX() - size, snapped.getY(), snapped.getX() + size, snapped.getY());
                gc.strokeLine(snapped.getX(), snapped.getY() - size, snapped.getX(), snapped.getY() + size);
                
                // Draw circle around snap point
                gc.strokeOval(snapped.getX() - size/2, snapped.getY() - size/2, size, size);
                
            } finally {
                gc.restore();
            }
        }
    }
    
    // ========== UTILITY METHODS ==========
    
    /**
     * Get current viewport bounds
     */
    private Rectangle2D getViewport() {
        if (canvas == null) {
            return new Rectangle2D(0, 0, 800, 600);
        }
        return new Rectangle2D(0, 0, canvas.getWidth(), canvas.getHeight());
    }
    
    /**
     * Format coordinate value for display
     */
    private String formatCoordinate(double pixelValue, GridUnit unit) {
        double value = pixelValue / unit.getPixelsPerUnit();
        
        if (unit == GridUnit.PIXELS) {
            return String.format("%.0f", value);
        } else if (unit == GridUnit.POINTS) {
            return String.format("%.1f", value);
        } else {
            return String.format("%.2f", value);
        }
    }
    
    // ========== CONFIGURATION METHODS ==========
    
    public void setGridEnabled(boolean enabled) {
        this.gridEnabled = enabled;
    }
    
    public void setRulersEnabled(boolean enabled) {
        this.rulersEnabled = enabled;
    }
    
    public void setSnapToGridEnabled(boolean enabled) {
        this.snapToGridEnabled = enabled;
    }
    
    public void setGridType(GridType type) {
        this.gridType = type;
    }
    
    public void setGridUnit(GridUnit unit) {
        this.gridUnit = unit;
    }
    
    public void setGridSpacing(double spacing) {
        this.gridSpacing = Math.max(0.1, spacing);
    }
    
    public void setZoomLevel(double zoomLevel) {
        this.zoomLevel = Math.max(0.01, zoomLevel);
    }
    
    public void setGridColor(Color color) {
        this.gridColor = color;
    }
    
    public void setMajorGridColor(Color color) {
        this.majorGridColor = color;
    }
    
    public void setSnapTolerance(double tolerance) {
        this.snapTolerance = Math.max(1.0, tolerance);
    }
    
    // ========== GETTERS ==========
    
    public boolean isGridEnabled() { return gridEnabled; }
    public boolean isRulersEnabled() { return rulersEnabled; }
    public boolean isSnapToGridEnabled() { return snapToGridEnabled; }
    public GridType getGridType() { return gridType; }
    public GridUnit getGridUnit() { return gridUnit; }
    public double getGridSpacing() { return gridSpacing; }
    public double getZoomLevel() { return zoomLevel; }
    public double getSnapTolerance() { return snapTolerance; }
    
    /**
     * Get effective grid spacing in pixels
     */
    public double getEffectiveGridSpacing() {
        return gridSpacing * gridUnit.getPixelsPerUnit() * zoomLevel;
    }
    
    /**
     * Get grid intersection points within a rectangle
     */
    public java.util.List<Point2D> getGridIntersections(Rectangle2D bounds) {
        java.util.List<Point2D> intersections = new java.util.ArrayList<>();
        
        double spacing = getEffectiveGridSpacing();
        double startX = Math.floor(bounds.getMinX() / spacing) * spacing;
        double startY = Math.floor(bounds.getMinY() / spacing) * spacing;
        
        for (double x = startX; x <= bounds.getMaxX(); x += spacing) {
            for (double y = startY; y <= bounds.getMaxY(); y += spacing) {
                if (bounds.contains(x, y)) {
                    intersections.add(new Point2D(x, y));
                }
            }
        }
        
        return intersections;
    }
}