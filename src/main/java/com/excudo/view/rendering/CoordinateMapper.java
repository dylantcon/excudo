package com.excudo.view.rendering;

import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;

/**
 * Handles coordinate system mapping between PowerPoint's EMU (English Metric Units) 
 * and JavaFX pixel coordinates. PowerPoint uses EMUs where 914,400 EMUs = 1 inch,
 * while JavaFX uses pixels at 96 DPI (96 pixels = 1 inch).
 */
public class CoordinateMapper {
    
    // PowerPoint coordinate constants
    public static final double EMUS_PER_INCH = 914400.0;
    public static final double PIXELS_PER_INCH = 96.0;
    public static final double EMUS_PER_PIXEL = EMUS_PER_INCH / PIXELS_PER_INCH; // 9525.0
    
    // Standard PowerPoint slide dimensions in EMUs
    public static final long SLIDE_WIDTH_EMUS = 12192000L;  // 13.33 inches
    public static final long SLIDE_HEIGHT_EMUS = 6858000L;  // 7.5 inches
    
    // Default JavaFX canvas dimensions
    public static final double DEFAULT_CANVAS_WIDTH = 1280.0;  // pixels
    public static final double DEFAULT_CANVAS_HEIGHT = 720.0;  // pixels
    
    private final double scaleX;
    private final double scaleY;
    private final double offsetX;
    private final double offsetY;
    private final double canvasWidth;
    private final double canvasHeight;
    
    // Zoom and pan support
    private double zoomLevel = 1.0;
    private double panOffsetX = 0.0;
    private double panOffsetY = 0.0;
    private Rectangle2D viewport;
    
    /**
     * Create coordinate mapper with default canvas size
     */
    public CoordinateMapper() {
        this(DEFAULT_CANVAS_WIDTH, DEFAULT_CANVAS_HEIGHT);
    }
    
    /**
     * Create coordinate mapper for specific canvas dimensions
     * @param canvasWidth JavaFX canvas width in pixels
     * @param canvasHeight JavaFX canvas height in pixels
     */
    public CoordinateMapper(double canvasWidth, double canvasHeight) {
        this.canvasWidth = canvasWidth;
        this.canvasHeight = canvasHeight;
        
        // Calculate scale factors to fit PowerPoint slide in canvas
        double slideWidthPixels = emuToPixels(SLIDE_WIDTH_EMUS);
        double slideHeightPixels = emuToPixels(SLIDE_HEIGHT_EMUS);
        
        double tempScaleX = canvasWidth / slideWidthPixels;
        double tempScaleY = canvasHeight / slideHeightPixels;
        
        // Use uniform scaling to maintain aspect ratio
        double uniformScale = Math.min(tempScaleX, tempScaleY);
        this.scaleX = uniformScale;
        this.scaleY = uniformScale;
        
        // Center the slide in the canvas
        double scaledSlideWidth = slideWidthPixels * this.scaleX;
        double scaledSlideHeight = slideHeightPixels * this.scaleY;
        this.offsetX = (canvasWidth - scaledSlideWidth) / 2.0;
        this.offsetY = (canvasHeight - scaledSlideHeight) / 2.0;
    }
    
    // ========== EMU ↔ PIXEL CONVERSION ==========
    
    /**
     * Convert EMU to pixel (base conversion without scaling)
     */
    public static double emuToPixels(long emu) {
        return emu / EMUS_PER_PIXEL;
    }
    
    /**
     * Convert pixel to EMU (base conversion without scaling)
     */
    public static long pixelToEmu(double pixel) {
        return Math.round(pixel * EMUS_PER_PIXEL);
    }
    
    /**
     * Convert pixels to EMU (instance method for compatibility)
     */
    public long pixelsToEmu(double pixels) {
        return pixelToEmu(pixels);
    }
    
    // ========== POWERPOINT → JAVAFX MAPPING ==========
    
    /**
     * Convert PowerPoint EMU coordinates to JavaFX canvas coordinates
     */
    public Point2D mapToCanvas(long emuX, long emuY) {
        double pixelX = emuToPixels(emuX) * scaleX * zoomLevel + offsetX + panOffsetX;
        double pixelY = emuToPixels(emuY) * scaleY * zoomLevel + offsetY + panOffsetY;
        return new Point2D(pixelX, pixelY);
    }
    
    /**
     * Convert PowerPoint EMU coordinates to JavaFX canvas coordinates
     */
    public Point2D mapToCanvas(Point2D emuPoint) {
        return mapToCanvas((long) emuPoint.getX(), (long) emuPoint.getY());
    }
    
    /**
     * Convert PowerPoint EMU rectangle to JavaFX canvas rectangle
     */
    public Rectangle2D mapToCanvas(long emuX, long emuY, long emuWidth, long emuHeight) {
        Point2D topLeft = mapToCanvas(emuX, emuY);
        double canvasWidth = emuToPixels(emuWidth) * scaleX * zoomLevel;
        double canvasHeight = emuToPixels(emuHeight) * scaleY * zoomLevel;
        return new Rectangle2D(topLeft.getX(), topLeft.getY(), canvasWidth, canvasHeight);
    }
    
    /**
     * Convert EMU dimension to canvas dimension (width/height only)
     */
    public double mapDimensionToCanvas(long emuDimension) {
        return emuToPixels(emuDimension) * scaleX * zoomLevel;
    }
    
    // ========== JAVAFX → POWERPOINT MAPPING ==========
    
    /**
     * Convert JavaFX canvas coordinates to PowerPoint EMU coordinates
     */
    public Point2D mapToEmu(double canvasX, double canvasY) {
        double pixelX = (canvasX - offsetX - panOffsetX) / (scaleX * zoomLevel);
        double pixelY = (canvasY - offsetY - panOffsetY) / (scaleY * zoomLevel);
        return new Point2D(pixelToEmu(pixelX), pixelToEmu(pixelY));
    }
    
    /**
     * Convert JavaFX canvas coordinates to PowerPoint EMU coordinates
     */
    public Point2D mapToEmu(Point2D canvasPoint) {
        return mapToEmu(canvasPoint.getX(), canvasPoint.getY());
    }
    
    /**
     * Convert canvas dimension to EMU dimension (width/height only)
     */
    public long mapDimensionToEmu(double canvasDimension) {
        return pixelToEmu(canvasDimension / (scaleX * zoomLevel));
    }
    
    // ========== UTILITY METHODS ==========
    
    /**
     * Get the effective slide bounds in canvas coordinates
     */
    public Rectangle2D getSlideBounds() {
        return mapToCanvas(0, 0, SLIDE_WIDTH_EMUS, SLIDE_HEIGHT_EMUS);
    }
    
    /**
     * Check if canvas coordinates are within the slide area
     */
    public boolean isWithinSlide(double canvasX, double canvasY) {
        Rectangle2D slideBounds = getSlideBounds();
        return slideBounds.contains(canvasX, canvasY);
    }
    
    /**
     * Get current scale factor
     */
    public double getScale() {
        return scaleX; // Uniform scaling, so scaleX == scaleY
    }
    
    /**
     * Get canvas offset (for centering)
     */
    public Point2D getOffset() {
        return new Point2D(offsetX, offsetY);
    }
    
    /**
     * Create a new mapper with different canvas dimensions
     */
    public CoordinateMapper withCanvasSize(double newWidth, double newHeight) {
        return new CoordinateMapper(newWidth, newHeight);
    }
    
    /**
     * Create a new mapper with zoom factor applied
     */
    public CoordinateMapper withZoom(double zoomFactor) {
        // Create a mapper that applies additional zoom on top of fit-to-canvas scaling
        CoordinateMapper baseMapper = new CoordinateMapper(canvasWidth, canvasHeight);
        baseMapper.setZoomLevel(zoomFactor);
        return baseMapper;
    }
    
    // ========== ZOOM AND PAN SUPPORT ==========
    
    /**
     * Set zoom level
     */
    public void setZoomLevel(double zoomLevel) {
        this.zoomLevel = zoomLevel;
    }
    
    /**
     * Get zoom level
     */
    public double getZoomLevel() {
        return zoomLevel;
    }

    /**
     * Get the composite scale that {@link #mapToCanvas} applies to shape
     * geometry: {@code scaleX * zoomLevel}. This is the factor callers
     * should multiply into font-point sizes, insets, indents, and any
     * other metric expressed in EMU/pt that must match the on-canvas
     * visual scale. {@link #getZoomLevel()} alone omits the canvas-fit
     * factor and produces text that doesn't shrink when the viewport is
     * resized smaller.
     */
    public double getEffectiveScale() {
        return scaleX * zoomLevel;
    }
    
    /**
     * Set pan offset
     */
    public void setPanOffset(double x, double y) {
        this.panOffsetX = x;
        this.panOffsetY = y;
    }
    
    /**
     * Get zoomed coordinate mapper
     */
    public CoordinateMapper getZoomedCoordinateMapper() {
        return this; // For simplicity, return self with zoom applied
    }
    
    /**
     * Set viewport
     */
    public void setViewport(Rectangle2D viewport) {
        this.viewport = viewport;
    }
    
    /**
     * Get viewport
     */
    public Rectangle2D getViewport() {
        return viewport != null ? viewport : new Rectangle2D(0, 0, canvasWidth, canvasHeight);
    }
    
    /**
     * Check if point is in viewport
     */
    public boolean isPointInViewport(Point2D point) {
        Rectangle2D vp = getViewport();
        return vp.contains(point);
    }
    
    /**
     * Canvas to EMU conversion
     */
    public Point2D canvasToEmu(Point2D canvasPoint) {
        return mapToEmu(canvasPoint);
    }
    
    @Override
    public String toString() {
        return String.format("CoordinateMapper[canvas=%.0fx%.0f, scale=%.3f, offset=(%.1f,%.1f)]",
                canvasWidth, canvasHeight, scaleX, offsetX, offsetY);
    }
}