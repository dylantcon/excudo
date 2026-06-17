package com.excudo.core.model;

/**
 * Represents the position and size of a shape in EMUs (English Metric Units)
 */
public class ShapeGeometry {
  private final long x, y;      // Position (top-left corner)
  private final long width, height;  // Dimensions
  private final int rotation;   // Rotation in 60,000ths of a degree (OOXML a:xfrm/@rot)
  private final boolean flipH;  // OOXML a:xfrm/@flipH -- mirror across the vertical axis
  private final boolean flipV;  // OOXML a:xfrm/@flipV -- mirror across the horizontal axis

  public ShapeGeometry(long x, long y, long width, long height) {
    this(x, y, width, height, 0);
  }

  public ShapeGeometry(long x, long y, long width, long height, int rotation) {
    this(x, y, width, height, rotation, false, false);
  }

  public ShapeGeometry(long x, long y, long width, long height, int rotation,
                       boolean flipH, boolean flipV) {
    this.x = x;
    this.y = y;
    this.width = width;
    this.height = height;
    this.rotation = rotation;
    this.flipH = flipH;
    this.flipV = flipV;
  }

  // Convert EMUs to points (common PowerPoint unit)
  public double getXInPoints() { return emuToPoints(x); }
  public double getYInPoints() { return emuToPoints(y); }
  public double getWidthInPoints() { return emuToPoints(width); }
  public double getHeightInPoints() { return emuToPoints(height); }

  // Raw EMU values
  public long getX() { return x; }
  public long getY() { return y; }
  public long getWidth() { return width; }
  public long getHeight() { return height; }
  public int getRotation() { return rotation; }
  public double getRotationDegrees() { return rotation / 60000.0; }

  /** OOXML a:xfrm/@flipH -- the shape is mirrored across its vertical axis.
   *  For a straight connector, flipH/flipV select which diagonal of the
   *  bounding box the line runs along (see GeometricShapeRenderer). */
  public boolean isFlipH() { return flipH; }

  /** OOXML a:xfrm/@flipV -- the shape is mirrored across its horizontal axis. */
  public boolean isFlipV() { return flipV; }

  // PowerPoint conversion: 1 point = 12700 EMUs
  private static double emuToPoints(long emu) {
    return emu / 12700.0;
  }

  @Override
  public String toString() {
    String flip = (flipH || flipV)
        ? String.format(", flipH=%b, flipV=%b", flipH, flipV) : "";
    return String.format("Geometry{x=%.1fpt, y=%.1fpt, w=%.1fpt, h=%.1fpt%s}",
        getXInPoints(), getYInPoints(), getWidthInPoints(), getHeightInPoints(), flip);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ShapeGeometry that)) return false;
    return x == that.x && y == that.y
        && width == that.width && height == that.height
        && rotation == that.rotation
        && flipH == that.flipH && flipV == that.flipV;
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(x, y, width, height, rotation, flipH, flipV);
  }
}
