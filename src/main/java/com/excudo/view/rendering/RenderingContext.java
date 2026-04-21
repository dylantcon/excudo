package com.excudo.view.rendering;

import com.excudo.view.rendering.surface.CanvasRenderSurface;
import com.excudo.view.rendering.surface.RenderSurface;
import com.excudo.view.rendering.surface.SurfacePaint;
import javafx.scene.canvas.GraphicsContext;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

/**
 * Maintains rendering state and context for PowerPoint slide rendering.
 * Provides coordinate mapping, graphics state management, and rendering utilities.
 *
 * <p>As of the Bridge-pattern refactor, this type holds a
 * {@link RenderSurface} rather than a raw {@link GraphicsContext}.
 * {@link #getGraphicsContext()} remains as a shim during Phase A so
 * existing renderers that still call it compile unchanged; Phase B
 * rewires renderers to {@link #getSurface()} and Phase E removes the
 * shim entirely.
 */
public class RenderingContext {

    private final RenderSurface surface;
    private final CoordinateMapper coordinateMapper;
    private final Stack<RenderingState> stateStack;
    private final Map<String, Object> renderingHints;

    // Current rendering state
    private double zoomFactor;
    private boolean debugMode;
    private boolean showBounds;
    private boolean showSpids;
    private boolean showGrid;

    public RenderingContext(RenderSurface surface, CoordinateMapper coordinateMapper) {
        this.surface = surface;
        this.coordinateMapper = coordinateMapper;
        this.stateStack = new Stack<>();
        this.renderingHints = new HashMap<>();
        this.zoomFactor = 1.0;
        this.debugMode = false;
        this.showBounds = false;
        this.showSpids = false;
        this.showGrid = false;
    }

    /**
     * Legacy constructor accepting a raw GraphicsContext. Internally
     * wraps the GraphicsContext's Canvas in a {@link CanvasRenderSurface}.
     * Kept for callers that haven't migrated yet; remove once the
     * codebase is fully on the surface API.
     */
    public RenderingContext(GraphicsContext graphicsContext, CoordinateMapper coordinateMapper) {
        this(new CanvasRenderSurface(graphicsContext.getCanvas()), coordinateMapper);
    }
    
    // ========== STATE MANAGEMENT ==========
    //
    // The parallel RenderingState stack that captured fill/stroke/font
    // was redundant with the surface's native save/restore (the fields
    // were populated but never read back). Collapsed onto surface.save/
    // surface.restore; the stateStack and RenderingState holder remain
    // below only to avoid API churn in callers that peek at the depth.

    /**
     * Save current graphics state to stack
     */
    public void saveState() {
        stateStack.push(RenderingState.EMPTY);
        surface.save();
    }

    /**
     * Restore graphics state from stack
     */
    public void restoreState() {
        if (!stateStack.isEmpty()) {
            stateStack.pop();
            surface.restore();
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
     * Primary API as of the Bridge refactor. Renderers should prefer
     * this over {@link #getGraphicsContext()}.
     */
    public RenderSurface getSurface() {
        return surface;
    }

    /**
     * Get underlying JavaFX graphics context.
     *
     * <p><b>Deprecated shim.</b> Phase A of the Bridge refactor keeps
     * this method so renderers that still issue {@code gc.XXX} calls
     * compile. Phase B migrates every call site onto {@link #getSurface()};
     * Phase E removes this method. If the active surface is not a
     * {@link CanvasRenderSurface} (e.g. future AWT backend), this will
     * throw.
     */
    @Deprecated
    public GraphicsContext getGraphicsContext() {
        if (surface instanceof CanvasRenderSurface canvasSurface) {
            return canvasSurface.getGraphicsContext();
        }
        throw new UnsupportedOperationException(
            "getGraphicsContext() only works with CanvasRenderSurface; surface is "
                + surface.getClass().getName() + ". Migrate this caller to getSurface().");
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
        // Clear with transparent; callers that want a specific
        // background paint on top call surface.clear(paint) directly.
        surface.clear(com.excudo.view.rendering.surface.SurfacePaint.Transparent.INSTANCE);
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
            surface.setStroke(com.excudo.view.rendering.surface.SurfacePaint.Solid.rgb(169, 169, 169)); // DARKGRAY
            surface.setLineWidth(2.0);
            surface.strokeRect(bounds.getMinX(), bounds.getMinY(),
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
            surface.setStroke(com.excudo.view.rendering.surface.SurfacePaint.Solid.rgb(211, 211, 211)); // LIGHTGRAY
            surface.setLineWidth(0.5);
            surface.setGlobalAlpha(0.3);

            javafx.geometry.Rectangle2D bounds = getZoomedCoordinateMapper().getSlideBounds();
            double gridSpacing = 20.0 * zoomFactor; // 20 pixel grid

            // Vertical lines
            for (double x = bounds.getMinX(); x <= bounds.getMaxX(); x += gridSpacing) {
                surface.strokeLine(x, bounds.getMinY(), x, bounds.getMaxY());
            }

            // Horizontal lines
            for (double y = bounds.getMinY(); y <= bounds.getMaxY(); y += gridSpacing) {
                surface.strokeLine(bounds.getMinX(), y, bounds.getMaxX(), y);
            }

        } finally {
            restoreState();
        }
    }

    /**
     * Draw debug bounds rectangle for a shape
     */
    public void drawDebugBounds(javafx.geometry.Rectangle2D bounds, SurfacePaint color) {
        if (showBounds || debugMode) {
            saveState();
            try {
                surface.setStroke(color);
                surface.setLineWidth(1.0);
                surface.setGlobalAlpha(0.7);
                surface.strokeRect(bounds.getMinX(), bounds.getMinY(),
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
                surface.setFill(com.excudo.view.rendering.surface.SurfacePaint.Solid.rgb(255, 0, 0)); // RED
                surface.setFont(com.excudo.view.rendering.surface.SurfaceFont.of("Monospaced", 10));
                surface.fillText("SPID:" + spid, position.getX() + 5, position.getY() - 5);
            } finally {
                restoreState();
            }
        }
    }

    /**
     * Set up graphics context for shape rendering
     */
    public void setupForShapeRendering() {
        surface.setLineCap(com.excudo.view.rendering.surface.StrokeCap.ROUND);
        surface.setLineJoin(com.excudo.view.rendering.surface.StrokeJoin.ROUND);
        surface.setMiterLimit(10.0);
    }

    
    // ========== RENDERING STATE HOLDER ==========
    //
    // Empty placeholder class retained only so stateStack depth
    // inspection (if any caller ever does that) keeps working. The
    // surface's native save/restore handles the actual paint/stroke/
    // font/transform/clip stacks. Removed in Phase E along with the
    // rest of the legacy shim.

    private static final class RenderingState {
        static final RenderingState EMPTY = new RenderingState();
        private RenderingState() {}
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