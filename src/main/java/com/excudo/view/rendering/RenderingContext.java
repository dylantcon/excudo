package com.excudo.view.rendering;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.text.Font;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

/**
 * Maintains rendering state and context for PowerPoint slide rendering.
 * Provides coordinate mapping, graphics state management, and rendering utilities.
 */
public class RenderingContext {
    
    private final GraphicsContext graphicsContext;
    private final CoordinateMapper coordinateMapper;
    private final Stack<RenderingState> stateStack;
    private final Map<String, Object> renderingHints;
    
    // Current rendering state
    private double zoomFactor;
    private boolean debugMode;
    private boolean showBounds;
    private boolean showSpids;
    private boolean showGrid;
    
    public RenderingContext(GraphicsContext graphicsContext, CoordinateMapper coordinateMapper) {
        this.graphicsContext = graphicsContext;
        this.coordinateMapper = coordinateMapper;
        this.stateStack = new Stack<>();
        this.renderingHints = new HashMap<>();
        this.zoomFactor = 1.0;
        this.debugMode = false;
        this.showBounds = false;
        this.showSpids = false;
        this.showGrid = false;
    }
    
    // ========== STATE MANAGEMENT ==========
    
    /**
     * Save current graphics state to stack
     */
    public void saveState() {
        RenderingState state = new RenderingState(
                graphicsContext.getFill(),
                graphicsContext.getStroke(),
                graphicsContext.getLineWidth(),
                graphicsContext.getFont(),
                graphicsContext.getGlobalAlpha()
        );
        stateStack.push(state);
        graphicsContext.save();
    }
    
    /**
     * Restore graphics state from stack
     */
    public void restoreState() {
        if (!stateStack.isEmpty()) {
            RenderingState state = stateStack.pop();
            graphicsContext.restore();
        }
    }
    
    // ========== COORDINATE MAPPING ==========
    
    /**
     * Get coordinate mapper for EMU ↔ Canvas conversion
     */
    public CoordinateMapper getCoordinateMapper() {
        return coordinateMapper;
    }
    
    /**
     * Get coordinate mapper with current zoom applied
     */
    public CoordinateMapper getZoomedCoordinateMapper() {
        return coordinateMapper.withZoom(zoomFactor);
    }
    
    // ========== GRAPHICS CONTEXT ACCESS ==========
    
    /**
     * Get underlying JavaFX graphics context
     */
    public GraphicsContext getGraphicsContext() {
        return graphicsContext;
    }
    
    // ========== RENDERING SETTINGS ==========
    
    public double getZoomFactor() { return zoomFactor; }
    public void setZoomFactor(double zoomFactor) { this.zoomFactor = zoomFactor; }
    
    public boolean isDebugMode() { return debugMode; }
    public void setDebugMode(boolean debugMode) { this.debugMode = debugMode; }
    
    public boolean isShowBounds() { return showBounds; }
    public void setShowBounds(boolean showBounds) { this.showBounds = showBounds; }
    
    public boolean isShowSpids() { return showSpids; }
    public void setShowSpids(boolean showSpids) { this.showSpids = showSpids; }
    
    public boolean isShowGrid() { return showGrid; }
    public void setShowGrid(boolean showGrid) { this.showGrid = showGrid; }
    
    // ========== RENDERING HINTS ==========
    
    /**
     * Set rendering hint for optimization or behavior control
     */
    public void setRenderingHint(String key, Object value) {
        renderingHints.put(key, value);
    }
    
    /**
     * Get rendering hint value
     */
    @SuppressWarnings("unchecked")
    public <T> T getRenderingHint(String key, Class<T> type) {
        Object value = renderingHints.get(key);
        return type.isInstance(value) ? (T) value : null;
    }
    
    /**
     * Check if rendering hint is enabled
     */
    public boolean isRenderingHintEnabled(String key) {
        Object value = renderingHints.get(key);
        return value instanceof Boolean && (Boolean) value;
    }
    
    // ========== UTILITY METHODS ==========
    
    /**
     * Clear the entire canvas
     */
    public void clearCanvas() {
        graphicsContext.clearRect(0, 0, 
                graphicsContext.getCanvas().getWidth(), 
                graphicsContext.getCanvas().getHeight());
    }
    
    /**
     * Draw debug grid if enabled
     */
    public void drawGridIfEnabled() {
        if (showGrid) {
            drawCoordinateGrid();
        }
    }
    
    /**
     * Draw slide bounds outline
     */
    public void drawSlideBounds() {
        saveState();
        try {
            javafx.geometry.Rectangle2D bounds = getZoomedCoordinateMapper().getSlideBounds();
            graphicsContext.setStroke(Color.DARKGRAY);
            graphicsContext.setLineWidth(2.0);
            graphicsContext.strokeRect(bounds.getMinX(), bounds.getMinY(), 
                                     bounds.getWidth(), bounds.getHeight());
        } finally {
            restoreState();
        }
    }
    
    /**
     * Draw coordinate grid for precision editing
     */
    private void drawCoordinateGrid() {
        saveState();
        try {
            graphicsContext.setStroke(Color.LIGHTGRAY);
            graphicsContext.setLineWidth(0.5);
            graphicsContext.setGlobalAlpha(0.3);
            
            javafx.geometry.Rectangle2D bounds = getZoomedCoordinateMapper().getSlideBounds();
            double gridSpacing = 20.0 * zoomFactor; // 20 pixel grid
            
            // Vertical lines
            for (double x = bounds.getMinX(); x <= bounds.getMaxX(); x += gridSpacing) {
                graphicsContext.strokeLine(x, bounds.getMinY(), x, bounds.getMaxY());
            }
            
            // Horizontal lines
            for (double y = bounds.getMinY(); y <= bounds.getMaxY(); y += gridSpacing) {
                graphicsContext.strokeLine(bounds.getMinX(), y, bounds.getMaxX(), y);
            }
            
        } finally {
            restoreState();
        }
    }
    
    /**
     * Draw debug bounds rectangle for a shape
     */
    public void drawDebugBounds(javafx.geometry.Rectangle2D bounds, Color color) {
        if (showBounds || debugMode) {
            saveState();
            try {
                graphicsContext.setStroke(color);
                graphicsContext.setLineWidth(1.0);
                graphicsContext.setGlobalAlpha(0.7);
                graphicsContext.strokeRect(bounds.getMinX(), bounds.getMinY(),
                                         bounds.getWidth(), bounds.getHeight());
            } finally {
                restoreState();
            }
        }
    }
    
    /**
     * Draw SPID label for debugging
     */
    public void drawSpidLabel(int spid, javafx.geometry.Point2D position) {
        if (showSpids || debugMode) {
            saveState();
            try {
                graphicsContext.setFill(Color.RED);
                graphicsContext.setFont(Font.font("Monospace", 10));
                graphicsContext.fillText("SPID:" + spid, position.getX() + 5, position.getY() - 5);
            } finally {
                restoreState();
            }
        }
    }
    
    /**
     * Set up graphics context for shape rendering
     */
    public void setupForShapeRendering() {
        graphicsContext.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        graphicsContext.setLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);
        graphicsContext.setMiterLimit(10.0);
    }
    
    // ========== RENDERING STATE HOLDER ==========
    
    /**
     * Immutable state holder for graphics context state
     */
    private static class RenderingState {
        private final Paint fill;
        private final Paint stroke;
        private final double lineWidth;
        private final Font font;
        private final double globalAlpha;
        
        public RenderingState(Paint fill, Paint stroke, double lineWidth, Font font, double globalAlpha) {
            this.fill = fill;
            this.stroke = stroke;
            this.lineWidth = lineWidth;
            this.font = font;
            this.globalAlpha = globalAlpha;
        }
        
        // Getters if needed for state inspection
        public Paint getFill() { return fill; }
        public Paint getStroke() { return stroke; }
        public double getLineWidth() { return lineWidth; }
        public Font getFont() { return font; }
        public double getGlobalAlpha() { return globalAlpha; }
    }
    
    // ========== COMMON RENDERING HINT CONSTANTS ==========
    
    public static final String HINT_ANTIALIASING = "antialiasing";
    public static final String HINT_TEXT_SMOOTHING = "textSmoothing";
    public static final String HINT_PERFORMANCE_MODE = "performanceMode";
    public static final String HINT_PRECISION_MODE = "precisionMode";
    public static final String HINT_CACHE_SHAPES = "cacheShapes";
    
    @Override
    public String toString() {
        return String.format("RenderingContext[zoom=%.2f, debug=%b, bounds=%b, spids=%b, grid=%b]",
                zoomFactor, debugMode, showBounds, showSpids, showGrid);
    }
}