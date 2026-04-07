package com.excudo.core.model;

/**
 * Represents the geometry (position and dimensions) of a content placeholder in a slide layout.
 * This class encapsulates the positioning information parsed from layout XML files to enable
 * proper inheritance of geometry properties when creating slides from layouts.
 * 
 * Follows Single Responsibility Principle by focusing solely on placeholder geometry data.
 */
public class PlaceholderGeometry {
    private final long x;          // X position in EMUs (English Metric Units)
    private final long y;          // Y position in EMUs
    private final long width;      // Width in EMUs
    private final long height;     // Height in EMUs
    private final String placeholderIndex;  // The idx attribute value (e.g., "1", "14")
    private final String placeholderType;   // OOXML ph type (e.g., "body", "obj", "pic", "title")

    /**
     * Creates a placeholder geometry instance with position and size in EMUs.
     *
     * @param x X position in EMUs
     * @param y Y position in EMUs
     * @param width Width in EMUs
     * @param height Height in EMUs
     * @param placeholderIndex The placeholder index from the idx attribute
     */
    public PlaceholderGeometry(long x, long y, long width, long height, String placeholderIndex) {
        this(x, y, width, height, placeholderIndex, null);
    }

    /**
     * Creates a placeholder geometry with position, size, and OOXML placeholder type.
     *
     * @param x X position in EMUs
     * @param y Y position in EMUs
     * @param width Width in EMUs
     * @param height Height in EMUs
     * @param placeholderIndex The placeholder index from the idx attribute
     * @param placeholderType The OOXML placeholder type (body, obj, pic, etc.)
     */
    public PlaceholderGeometry(long x, long y, long width, long height,
                                String placeholderIndex, String placeholderType) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.placeholderIndex = placeholderIndex;
        this.placeholderType = placeholderType;
    }
    
    // Raw EMU values for precise OOXML manipulation
    public long getX() { return x; }
    public long getY() { return y; }
    public long getWidth() { return width; }
    public long getHeight() { return height; }
    public String getPlaceholderIndex() { return placeholderIndex; }
    public String getPlaceholderType() { return placeholderType; }
    
    // Convert EMUs to points for human-readable display
    public double getXInPoints() { return emuToPoints(x); }
    public double getYInPoints() { return emuToPoints(y); }
    public double getWidthInPoints() { return emuToPoints(width); }
    public double getHeightInPoints() { return emuToPoints(height); }
    
    /**
     * Creates a ShapeGeometry instance from this placeholder geometry.
     * This enables reuse of existing geometry infrastructure.
     * 
     * @return ShapeGeometry with the same dimensions
     */
    public ShapeGeometry toShapeGeometry() {
        return new ShapeGeometry(x, y, width, height);
    }
    
    /**
     * Converts EMUs to points using PowerPoint's standard conversion ratio.
     * 1 point = 12700 EMUs
     */
    private static double emuToPoints(long emu) {
        return emu / 12700.0;
    }
    
    @Override
    public String toString() {
        return String.format("PlaceholderGeometry{idx=%s, type=%s, x=%.1fpt, y=%.1fpt, w=%.1fpt, h=%.1fpt}",
                placeholderIndex, placeholderType,
                getXInPoints(), getYInPoints(),
                getWidthInPoints(), getHeightInPoints());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PlaceholderGeometry that = (PlaceholderGeometry) o;
        return x == that.x && y == that.y && width == that.width &&
               height == that.height && placeholderIndex.equals(that.placeholderIndex) &&
               java.util.Objects.equals(placeholderType, that.placeholderType);
    }

    @Override
    public int hashCode() {
        int result = Long.hashCode(x);
        result = 31 * result + Long.hashCode(y);
        result = 31 * result + Long.hashCode(width);
        result = 31 * result + Long.hashCode(height);
        result = 31 * result + placeholderIndex.hashCode();
        result = 31 * result + (placeholderType != null ? placeholderType.hashCode() : 0);
        return result;
    }
}