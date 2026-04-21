package com.excudo.view.rendering.surface;

import java.awt.image.BufferedImage;

/**
 * Backend-neutral rendering surface. The abstraction half of a Bridge
 * pattern split: renderers ({@link com.excudo.view.rendering.shapes.ModelShapeRenderer}
 * implementations, {@link com.excudo.view.rendering.text.TextPainter}) talk
 * to this interface; concrete implementations wrap either JavaFX Canvas
 * ({@link CanvasRenderSurface}) or AWT Graphics2D (future
 * {@code Graphics2DRenderSurface}).
 *
 * <h2>Why this exists</h2>
 * Every renderer previously held a direct reference to
 * {@link javafx.scene.canvas.GraphicsContext} and to JavaFX
 * Paint/Font/Image types. That made headless PNG export go through a
 * Canvas -> WritableImage -> BufferedImage roundtrip (30-50 ms of pure
 * conversion per cold render) and made per-glyph typography control
 * impossible without leaving Canvas entirely. The Bridge split decouples
 * "what to draw" (renderers) from "where to draw it" (surface),
 * unblocking (a) AWT direct-to-BufferedImage for headless, (b) AWT
 * TextLayout for sub-pixel typography on the export path, and (c)
 * future backends (SVG, PDF) without touching a single renderer.
 *
 * <h2>Coordinate system</h2>
 * All coordinates are surface-local pixels (double-precision, not
 * snapped to integer grid). The surface does not apply any scale beyond
 * what it was constructed with -- renderers pre-scale by
 * {@link com.excudo.view.rendering.CoordinateMapper#getEffectiveScale()}
 * exactly as they did against the legacy GraphicsContext.
 *
 * <h2>Threading</h2>
 * Implementations are not required to be thread-safe. The Canvas backend
 * inherits JavaFX's FX-thread requirement; the AWT backend runs on the
 * calling thread without constraint.
 */
public interface RenderSurface {

    // ========== DIMENSIONS ==========

    double widthPx();
    double heightPx();

    // ========== PAINT STATE ==========

    void setFill(SurfacePaint paint);
    void setStroke(SurfacePaint paint);
    void setLineWidth(double px);

    /**
     * Dash pattern in pixels (solid, gap, solid, gap, ...). Pass a
     * zero-length array or null to clear (solid line).
     */
    void setLineDashes(double... dashes);

    void setLineCap(StrokeCap cap);
    void setLineJoin(StrokeJoin join);
    void setMiterLimit(double limit);

    /** 0.0 = fully transparent, 1.0 = fully opaque. Applied multiplicatively. */
    void setGlobalAlpha(double alpha);

    // ========== FONT + TEXT ==========

    void setFont(SurfaceFont font);

    /** Advance width in pixels at the currently-set font. */
    double measureAdvance(String text);

    /**
     * Draw text at the given baseline position. {@code y} is the baseline,
     * not the top of the glyph box -- matches {@code GraphicsContext.fillText}
     * and {@code Graphics2D.drawString} semantics.
     */
    void fillText(String text, double x, double baselineY);

    // ========== SHAPE PRIMITIVES ==========

    void fillRect(double x, double y, double w, double h);
    void strokeRect(double x, double y, double w, double h);
    void fillOval(double x, double y, double w, double h);
    void strokeOval(double x, double y, double w, double h);
    void fillRoundRect(double x, double y, double w, double h, double arcW, double arcH);
    void strokeRoundRect(double x, double y, double w, double h, double arcW, double arcH);
    void strokeLine(double x1, double y1, double x2, double y2);

    // ========== PATH OPS ==========
    // Used by preset-geometry drawers (polygons, arrows, flowchart shapes).

    void beginPath();
    void moveTo(double x, double y);
    void lineTo(double x, double y);
    void quadTo(double cx, double cy, double x, double y);
    void bezierTo(double cx1, double cy1, double cx2, double cy2, double x, double y);

    /**
     * Append an arc to the current path. {@code startAngleDeg} is
     * measured in degrees with 0° at the 3-o'clock position, positive
     * angles sweeping counter-clockwise (matches
     * {@link javafx.scene.canvas.GraphicsContext#arc} semantics).
     */
    void arc(double centerX, double centerY, double radiusX, double radiusY,
             double startAngleDeg, double arcExtentDeg);

    /**
     * Append a rounded-corner arc between three points (current point,
     * corner {@code (x1,y1)}, end {@code (x2,y2)}) with the given radius.
     * Matches {@link javafx.scene.canvas.GraphicsContext#arcTo} semantics.
     */
    void arcTo(double x1, double y1, double x2, double y2, double radius);

    void closePath();
    void fillPath();
    void strokePath();

    // ========== IMAGE ==========

    void drawImage(SurfaceImage image, double x, double y, double w, double h);

    /**
     * Decode raw image bytes into a backend-native raster. The returned
     * {@link SurfaceImage} is cached by callers keyed on some stable id
     * (relationship id, part name, etc.); the surface itself does not
     * cache.
     */
    SurfaceImage decodeImage(byte[] bytes, String mimeType);

    // ========== STATE + TRANSFORMS ==========

    /** Push the current paint/stroke/font/transform/clip onto a stack. */
    void save();

    /** Pop the most recently-saved state. */
    void restore();

    void translate(double tx, double ty);
    void rotate(double degrees);
    void scale(double sx, double sy);
    void clipRect(double x, double y, double w, double h);

    // ========== CLEAR ==========

    /**
     * Fill the entire surface with {@code background}, discarding any
     * prior content. Used at the start of each render.
     */
    void clear(SurfacePaint background);

    // ========== OUTPUT ==========

    /**
     * Snapshot the current surface to an AWT {@link BufferedImage}. The
     * Canvas backend performs a Canvas -> WritableImage -> BufferedImage
     * snapshot+convert internally; the AWT backend returns its backing
     * image directly (zero-copy). Callers never touch
     * {@code SwingFXUtils} or {@code Canvas.snapshot} directly.
     */
    BufferedImage toBufferedImage();
}
