package com.excudo.core.rendering.surface;

import com.excudo.core.metrics.FontIndex;
import com.excudo.core.utils.ComponentLogger;
import com.excudo.core.utils.Logger;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.MultipleGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/**
 * {@link RenderSurface} backed by an AWT {@link Graphics2D} drawing to
 * a {@link BufferedImage}. The headless-PNG-export sibling of
 * {@link CanvasRenderSurface}.
 *
 * <h2>Why this backend exists</h2>
 * The Canvas-based headless path paints to a JavaFX {@link javafx.scene.canvas.Canvas},
 * snapshots to a {@code WritableImage}, converts via {@code SwingFXUtils.fromFXImage}
 * to a {@code BufferedImage}, then encodes PNG. 30-50 ms of pure
 * conversion overhead on every cold render, plus a mandatory FX-thread
 * hop ({@code Platform.runLater} + {@code CountDownLatch}). This backend
 * writes directly to a {@link BufferedImage} -- {@link #toBufferedImage()}
 * just returns the backing image, no conversion, no FX thread.
 *
 * <h2>Typography</h2>
 * Every text measurement + draw goes through {@link TextLayout} with
 * {@link FontRenderContext#FontRenderContext(AffineTransform, boolean, boolean)}
 * configured for fractional metrics + antialiasing. That's AWT's
 * sub-pixel-aware text pipeline, equivalent in capability to DirectWrite
 * on Windows. Per-glyph positioning is the engine's job; we hand it a
 * baseline origin and it places glyphs at sub-pixel offsets.
 *
 * <h2>Gradients</h2>
 * {@link LinearGradientPaint} requires absolute coordinates, unlike
 * JavaFX {@link javafx.scene.paint.LinearGradient} with {@code proportional=true}.
 * {@link #setFill(SurfacePaint)} buffers the gradient descriptor;
 * {@link #fillRect(double, double, double, double)} / {@link #fillOval}
 * / {@link #fillPath} materialises the AWT paint using that op's
 * bounding box. Solid paints skip the buffer entirely.
 *
 * <h2>Threading</h2>
 * Not thread-safe. Safe to call from any thread (no FX-toolkit
 * dependency) but not concurrently on one instance.
 */
public final class Graphics2DRenderSurface implements RenderSurface {

    private static final ComponentLogger logger = Logger.getLogger(Graphics2DRenderSurface.class);

    private final BufferedImage image;
    private final Graphics2D g;
    private final int widthPx;
    private final int heightPx;
    private final FontRenderContext fontRenderContext;

    // Current path accumulator. Reset on beginPath; mutated by moveTo,
    // lineTo, quadTo, bezierTo, arc, arcTo, closePath. Consumed on
    // fillPath / strokePath.
    private Path2D currentPath;

    // Deferred bbox-relative paint. When setFill receives a paint whose
    // AWT materialisation needs the target bounds (linear/radial
    // gradients, picture fills), we stash the descriptor here and
    // materialise the java.awt.Paint on the next fill op using that
    // op's bbox. Solid + pattern paints apply directly to g.
    private SurfacePaint pendingPaint;

    // Cache resolved java.awt.Font per (family, weight, posture, size-
    // quantised-to-0.1pt, fallback). Mirrors the CanvasRenderSurface
    // font cache policy so the two backends behave comparably on
    // repeat-font-heavy slides (code blocks, theme body text).
    private static final ConcurrentHashMap<String, Font> FONT_CACHE = new ConcurrentHashMap<>();

    // State stack for save/restore. AWT Graphics2D has no native
    // save/restore -- we manually snapshot the paint / stroke / font /
    // transform / clip and restore on pop. Path is intentionally NOT
    // part of the state: OOXML preset drawers begin a fresh path each
    // invocation, matching JavaFX Canvas semantics.
    private final Deque<State> stateStack = new ArrayDeque<>();

    public Graphics2DRenderSurface(int widthPx, int heightPx) {
        if (widthPx <= 0 || heightPx <= 0) {
            throw new IllegalArgumentException("invalid dimensions: " + widthPx + "x" + heightPx);
        }
        this.widthPx = widthPx;
        this.heightPx = heightPx;
        this.image = new BufferedImage(widthPx, heightPx, BufferedImage.TYPE_INT_ARGB);
        this.g = image.createGraphics();
        applyDefaultHints();
        // FontRenderContext drives TextLayout's measurement + rendering.
        // Identity transform + AA on + fractional metrics on -> the
        // glyph advances TextLayout computes match what drawString
        // actually paints, at sub-pixel precision. This is the quality
        // upgrade the AWT backend buys us.
        this.fontRenderContext = new FontRenderContext(new AffineTransform(),
            /*isAntiAliased*/ true,
            /*usesFractionalMetrics*/ true);
    }

    private void applyDefaultHints() {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // Conservative default: greyscale AA. LCD subpixel is sharper
        // on LCD monitors but produces colour-fringed PNG output that
        // looks wrong when re-decoded as greyscale. An env-var opt-in
        // for LCD can land later if anyone asks.
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
            RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
            RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    }

    // ========== DIMENSIONS ==========

    @Override public double widthPx()  { return widthPx; }
    @Override public double heightPx() { return heightPx; }

    // ========== PAINT STATE ==========

    @Override
    public void setFill(SurfacePaint paint) {
        if (paint instanceof SurfacePaint.LinearGradient
                || paint instanceof SurfacePaint.RadialGradient
                || paint instanceof SurfacePaint.ImageFill) {
            pendingPaint = paint;
            // Set a sentinel colour on g so solid ops after a deferred
            // fill don't accidentally reuse the prior solid paint.
            g.setPaint(Color.BLACK);
        } else if (paint instanceof SurfacePaint.PatternFill pattern) {
            // Pattern tiles anchor at the canvas origin (page grid), so
            // no bbox is needed -- apply immediately.
            pendingPaint = null;
            g.setPaint(patternTexture(pattern));
        } else {
            pendingPaint = null;
            g.setPaint(toAwtColor(paint));
        }
    }

    @Override
    public void setStroke(SurfacePaint paint) {
        // Gradient/image strokes are rare enough in PPTX to not bother
        // with; render a representative solid until demand arrives.
        g.setPaint(toAwtColor(paint));
    }

    private double currentLineWidth = 1.0;
    private double[] currentDashes = null;
    private int currentCap = BasicStroke.CAP_BUTT;
    private int currentJoin = BasicStroke.JOIN_MITER;
    private double currentMiterLimit = 10.0;

    @Override public void setLineWidth(double px)  { currentLineWidth = px; applyStroke(); }
    @Override public void setLineCap(StrokeCap cap) {
        currentCap = switch (cap) {
            case BUTT   -> BasicStroke.CAP_BUTT;
            case ROUND  -> BasicStroke.CAP_ROUND;
            case SQUARE -> BasicStroke.CAP_SQUARE;
        };
        applyStroke();
    }
    @Override public void setLineJoin(StrokeJoin join) {
        currentJoin = switch (join) {
            case MITER -> BasicStroke.JOIN_MITER;
            case ROUND -> BasicStroke.JOIN_ROUND;
            case BEVEL -> BasicStroke.JOIN_BEVEL;
        };
        applyStroke();
    }
    @Override public void setMiterLimit(double limit) { currentMiterLimit = limit; applyStroke(); }

    @Override
    public void setLineDashes(double... dashes) {
        if (dashes == null || dashes.length == 0) {
            currentDashes = null;
        } else {
            currentDashes = dashes;
        }
        applyStroke();
    }

    private void applyStroke() {
        float[] dashArr = null;
        if (currentDashes != null && currentDashes.length > 0) {
            dashArr = new float[currentDashes.length];
            for (int i = 0; i < currentDashes.length; i++) dashArr[i] = (float) currentDashes[i];
        }
        g.setStroke(new BasicStroke(
            (float) Math.max(0.01, currentLineWidth),
            currentCap,
            currentJoin,
            (float) Math.max(1, currentMiterLimit),
            dashArr,
            0f));
    }

    @Override
    public void setGlobalAlpha(double alpha) {
        g.setComposite(java.awt.AlphaComposite.getInstance(
            java.awt.AlphaComposite.SRC_OVER, (float) Math.max(0, Math.min(1, alpha))));
    }

    // ========== FONT + TEXT ==========

    @Override
    public void setFont(SurfaceFont font) {
        if (font == null) return;
        Font resolved = FONT_CACHE.computeIfAbsent(fontKey(font), k -> resolveAwtFont(font));
        g.setFont(resolved);
    }

    @Override
    public double measureAdvance(String text) {
        if (text == null || text.isEmpty()) return 0;
        Font font = g.getFont();
        if (font == null) return 0;
        return new TextLayout(text, font, fontRenderContext).getAdvance();
    }

    @Override
    public void fillText(String text, double x, double baselineY) {
        if (text == null || text.isEmpty()) return;
        Font font = g.getFont();
        if (font == null) return;
        // TextLayout.draw respects fractional baseline positioning and
        // internal per-glyph sub-pixel advance -- the quality upgrade
        // the AWT backend exists to deliver.
        new TextLayout(text, font, fontRenderContext).draw(g, (float) x, (float) baselineY);
    }

    // ========== SHAPE PRIMITIVES ==========

    @Override
    public void fillRect(double x, double y, double w, double h) {
        applyPendingPaint(x, y, w, h);
        g.fill(new Rectangle2D.Double(x, y, w, h));
    }

    @Override
    public void strokeRect(double x, double y, double w, double h) {
        g.draw(new Rectangle2D.Double(x, y, w, h));
    }

    @Override
    public void fillOval(double x, double y, double w, double h) {
        applyPendingPaint(x, y, w, h);
        g.fill(new Ellipse2D.Double(x, y, w, h));
    }

    @Override
    public void strokeOval(double x, double y, double w, double h) {
        g.draw(new Ellipse2D.Double(x, y, w, h));
    }

    @Override
    public void fillRoundRect(double x, double y, double w, double h, double arcW, double arcH) {
        applyPendingPaint(x, y, w, h);
        g.fill(new RoundRectangle2D.Double(x, y, w, h, arcW, arcH));
    }

    @Override
    public void strokeRoundRect(double x, double y, double w, double h, double arcW, double arcH) {
        g.draw(new RoundRectangle2D.Double(x, y, w, h, arcW, arcH));
    }

    @Override
    public void strokeLine(double x1, double y1, double x2, double y2) {
        g.draw(new java.awt.geom.Line2D.Double(x1, y1, x2, y2));
    }

    // ========== PATH OPS ==========

    @Override public void beginPath() { currentPath = new Path2D.Double(); }

    @Override
    public void moveTo(double x, double y) {
        if (currentPath == null) currentPath = new Path2D.Double();
        currentPath.moveTo(x, y);
    }

    @Override
    public void lineTo(double x, double y) {
        if (currentPath == null) currentPath = new Path2D.Double();
        currentPath.lineTo(x, y);
    }

    @Override
    public void quadTo(double cx, double cy, double x, double y) {
        if (currentPath == null) currentPath = new Path2D.Double();
        currentPath.quadTo(cx, cy, x, y);
    }

    @Override
    public void bezierTo(double cx1, double cy1, double cx2, double cy2, double x, double y) {
        if (currentPath == null) currentPath = new Path2D.Double();
        currentPath.curveTo(cx1, cy1, cx2, cy2, x, y);
    }

    @Override
    public void arc(double centerX, double centerY, double radiusX, double radiusY,
                    double startAngleDeg, double arcExtentDeg) {
        if (currentPath == null) currentPath = new Path2D.Double();
        Arc2D.Double arc = new Arc2D.Double(
            centerX - radiusX, centerY - radiusY,
            radiusX * 2, radiusY * 2,
            startAngleDeg, arcExtentDeg,
            Arc2D.OPEN);
        // append(arc, true) continues the path from the current point
        // -- JavaFX's arc has the same "connect to last point via
        // implicit lineTo" semantic.
        currentPath.append(arc, true);
    }

    @Override
    public void arcTo(double x1, double y1, double x2, double y2, double radius) {
        // Approximate JavaFX's arcTo: a circular arc tangent to the
        // two segments (currentPoint -> (x1,y1)) and ((x1,y1) ->
        // (x2,y2)) with the given radius. Port of the standard
        // HTML5-canvas arcTo construction. If the three points are
        // collinear or radius is <= 0, degenerate to a lineTo.
        if (currentPath == null) currentPath = new Path2D.Double();
        java.awt.geom.Point2D.Double cp = (java.awt.geom.Point2D.Double) currentPath.getCurrentPoint();
        if (cp == null || radius <= 0) { currentPath.lineTo(x2, y2); return; }

        double x0 = cp.x, y0 = cp.y;
        double dx1 = x0 - x1, dy1 = y0 - y1;
        double dx2 = x2 - x1, dy2 = y2 - y1;
        double len1 = Math.hypot(dx1, dy1);
        double len2 = Math.hypot(dx2, dy2);
        if (len1 < 1e-9 || len2 < 1e-9) { currentPath.lineTo(x2, y2); return; }

        double cosTheta = (dx1 * dx2 + dy1 * dy2) / (len1 * len2);
        cosTheta = Math.max(-1, Math.min(1, cosTheta));
        double theta = Math.acos(cosTheta);
        if (theta < 1e-9 || Math.abs(theta - Math.PI) < 1e-9) {
            currentPath.lineTo(x2, y2);
            return;
        }
        double tanHalf = Math.tan(theta / 2);
        if (tanHalf <= 1e-9) { currentPath.lineTo(x2, y2); return; }
        double distFromCorner = radius / tanHalf;
        // Tangent points
        double t1x = x1 + dx1 / len1 * distFromCorner;
        double t1y = y1 + dy1 / len1 * distFromCorner;
        double t2x = x1 + dx2 / len2 * distFromCorner;
        double t2y = y1 + dy2 / len2 * distFromCorner;
        // Line to first tangent, then a cubic-bezier arc approximation.
        currentPath.lineTo(t1x, t1y);
        // Kappa for 90-degree-like arcs; scale linearly with arc angle
        // for acceptable fidelity over the range we care about
        // (OOXML round-rect corners are always 90°).
        double kappa = 0.5522847498 * (radius / tanHalf) / distFromCorner;
        // Control points along the outward-bisecting direction towards the corner (x1,y1)
        double c1x = t1x + (x1 - t1x) * kappa;
        double c1y = t1y + (y1 - t1y) * kappa;
        double c2x = t2x + (x1 - t2x) * kappa;
        double c2y = t2y + (y1 - t2y) * kappa;
        currentPath.curveTo(c1x, c1y, c2x, c2y, t2x, t2y);
    }

    @Override
    public void closePath() {
        if (currentPath != null) currentPath.closePath();
    }

    @Override
    public void fillPath() {
        if (currentPath == null) return;
        Rectangle2D b = currentPath.getBounds2D();
        applyPendingPaint(b.getX(), b.getY(), b.getWidth(), b.getHeight());
        g.fill(currentPath);
    }

    @Override
    public void strokePath() {
        if (currentPath == null) return;
        g.draw(currentPath);
    }

    // ========== IMAGE ==========

    @Override
    public void drawImage(SurfaceImage image, double x, double y, double w, double h) {
        if (image == null) return;
        Object handle = image.nativeHandle();
        if (handle instanceof BufferedImage img) {
            // drawImage's w/h are integer pixels; round to nearest.
            g.drawImage(img, (int) Math.round(x), (int) Math.round(y),
                (int) Math.round(w), (int) Math.round(h), null);
        } else {
            throw new IllegalArgumentException("Graphics2DRenderSurface cannot draw SurfaceImage "
                + "backed by " + (handle == null ? "null" : handle.getClass().getName()));
        }
    }

    @Override
    public SurfaceImage decodeImage(byte[] bytes, String mimeType) {
        if (bytes == null || bytes.length == 0) return null;
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (iis == null) return null;
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) return null; // e.g. WMF/EMF -- caller draws a placeholder
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true);
                // Header-only dimension read; the full raster isn't decoded yet.
                int srcW = reader.getWidth(0);
                int srcH = reader.getHeight(0);

                ImageReadParam param = reader.getDefaultReadParam();
                int sub = ImageDecodeScaler.subsampleFactor(srcW, srcH, widthPx, heightPx);
                if (sub > 1) {
                    // The reader skips pixels during decode, so the full-res
                    // raster is never materialized -- this is where the memory
                    // saving actually happens, not a post-decode resize.
                    param.setSourceSubsampling(sub, sub, 0, 0);
                }
                BufferedImage decoded = reader.read(0, param);
                if (decoded == null) return null;
                return new AwtSurfaceImage(decoded, decoded.getWidth(), decoded.getHeight());
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            logger.debug("Failed to decode image (mime={}): {}", mimeType, e.getMessage());
            return null;
        }
    }

    // ========== STATE + TRANSFORMS ==========

    @Override
    public void save() {
        stateStack.push(new State(
            g.getTransform(),
            g.getPaint(),
            g.getStroke(),
            g.getFont(),
            g.getClip(),
            g.getComposite(),
            currentLineWidth, currentDashes == null ? null : currentDashes.clone(),
            currentCap, currentJoin, currentMiterLimit,
            pendingPaint));
    }

    @Override
    public void restore() {
        State s = stateStack.pollFirst();
        if (s == null) return;
        g.setTransform(s.transform);
        g.setPaint(s.paint);
        g.setStroke(s.stroke);
        g.setFont(s.font);
        g.setClip(s.clip);
        g.setComposite(s.composite);
        currentLineWidth = s.lineWidth;
        currentDashes = s.dashes;
        currentCap = s.cap;
        currentJoin = s.join;
        currentMiterLimit = s.miterLimit;
        pendingPaint = s.pendingPaint;
    }

    @Override
    public void translate(double tx, double ty) {
        g.translate(tx, ty);
    }

    @Override
    public void rotate(double degrees) {
        g.rotate(Math.toRadians(degrees));
    }

    @Override
    public void scale(double sx, double sy) {
        g.scale(sx, sy);
    }

    @Override
    public void clipRect(double x, double y, double w, double h) {
        g.clip(new Rectangle2D.Double(x, y, w, h));
    }

    // ========== CLEAR ==========

    @Override
    public void clear(SurfacePaint background) {
        // Wipe to fully transparent first so repeated renders don't
        // composite on top of prior content (BufferedImage persists).
        java.awt.Composite prev = g.getComposite();
        g.setComposite(java.awt.AlphaComposite.Clear);
        g.fillRect(0, 0, widthPx, heightPx);
        g.setComposite(prev);
        if (background != null && background != SurfacePaint.Transparent.INSTANCE) {
            java.awt.Paint prevPaint = g.getPaint();
            SurfacePaint prevPending = pendingPaint;
            setFill(background);
            fillRect(0, 0, widthPx, heightPx);
            g.setPaint(prevPaint);
            pendingPaint = prevPending;
        }
    }

    // ========== OUTPUT ==========

    @Override
    public BufferedImage toBufferedImage() {
        // Zero-copy: return the backing image directly. The caller is
        // expected to treat it as immutable post-call.
        return image;
    }

    // ========== INTERNAL HELPERS ==========

    /**
     * If a bbox-relative fill (linear/radial gradient, picture fill) was
     * set via setFill but not yet applied, materialise it now using the
     * current fill op's bounding box and install it on g. No-op for
     * solid/pattern fills (already applied).
     */
    private void applyPendingPaint(double x, double y, double w, double h) {
        if (pendingPaint == null) return;
        if (pendingPaint instanceof SurfacePaint.LinearGradient grad) {
            float[] fractions = fractionsOf(grad.stops());
            Color[] colors = colorsOf(grad.stops());
            g.setPaint(new LinearGradientPaint(
                (float) (x + grad.startX() * w), (float) (y + grad.startY() * h),
                (float) (x + grad.endX()   * w), (float) (y + grad.endY()   * h),
                fractions, colors,
                MultipleGradientPaint.CycleMethod.NO_CYCLE));
        } else if (pendingPaint instanceof SurfacePaint.RadialGradient grad) {
            g.setPaint(materialiseRadial(grad, x, y, w, h));
        } else if (pendingPaint instanceof SurfacePaint.ImageFill fill) {
            g.setPaint(materialiseImageFill(fill, x, y, w, h));
        }
    }

    private java.awt.Paint materialiseRadial(SurfacePaint.RadialGradient grad,
                                              double x, double y, double w, double h) {
        float[] fractions = fractionsOf(grad.stops());
        Color[] colors = colorsOf(grad.stops());
        if (grad.geometry() == SurfacePaint.RadialGradient.Geometry.CIRCLE) {
            // Circular contours in absolute space. Radius reaches the
            // farthest bbox corner so the final stop lands exactly there
            // (PowerPoint's PDF flattening of a:path path="circle").
            double cx = x + grad.centerX() * w;
            double cy = y + grad.centerY() * h;
            double radius = 0;
            for (double[] corner : new double[][]{{x, y}, {x + w, y}, {x, y + h}, {x + w, y + h}}) {
                radius = Math.max(radius, Math.hypot(corner[0] - cx, corner[1] - cy));
            }
            if (radius <= 0) radius = 1;
            return new java.awt.RadialGradientPaint(
                new java.awt.geom.Point2D.Double(cx, cy), (float) radius,
                new java.awt.geom.Point2D.Double(x + grad.focusX() * w, y + grad.focusY() * h),
                fractions, colors,
                MultipleGradientPaint.CycleMethod.NO_CYCLE);
        }
        // ELLIPSE: build a unit-space circle and stretch it onto the bbox
        // via the gradient transform, so contours scale with the shape
        // and the final stop is reached at the bbox edges through the
        // center. Radius 0.5 assumes a near-centered focus (the OOXML
        // rect/shape path cases we approximate always are).
        AffineTransform toBox = new AffineTransform();
        toBox.translate(x, y);
        toBox.scale(w <= 0 ? 1 : w, h <= 0 ? 1 : h);
        return new java.awt.RadialGradientPaint(
            new java.awt.geom.Point2D.Double(grad.centerX(), grad.centerY()), 0.5f,
            new java.awt.geom.Point2D.Double(grad.focusX(), grad.focusY()),
            fractions, colors,
            MultipleGradientPaint.CycleMethod.NO_CYCLE,
            MultipleGradientPaint.ColorSpaceType.SRGB,
            toBox);
    }

    private java.awt.Paint materialiseImageFill(SurfacePaint.ImageFill fill,
                                                 double x, double y, double w, double h) {
        Object handle = fill.image() != null ? fill.image().nativeHandle() : null;
        if (!(handle instanceof BufferedImage img)) {
            return new Color(0, 0, 0, 0); // undecodable: draw nothing
        }
        // TexturePaint scales the image onto the anchor rect and tiles
        // it; a stretch fill is simply "one tile == the whole bbox".
        // Filling a non-rect geometry (oval, round-rect, path) clips the
        // texture to that geometry for free.
        Rectangle2D anchor;
        if (fill.tile() && fill.tileFracW() > 0 && fill.tileFracH() > 0) {
            anchor = new Rectangle2D.Double(x, y, w * fill.tileFracW(), h * fill.tileFracH());
        } else {
            anchor = new Rectangle2D.Double(x, y, w, h);
        }
        return new java.awt.TexturePaint(img, anchor);
    }

    // Pattern tiles are tiny (8x8) and shared across shapes/slides; cache
    // the TexturePaint per PatternFill value (bits + colors).
    private static final ConcurrentHashMap<SurfacePaint.PatternFill, java.awt.TexturePaint>
        PATTERN_CACHE = new ConcurrentHashMap<>();

    /**
     * Build (or fetch) the 8x8 texture for an OOXML preset pattern.
     * Anchored at the canvas origin: PowerPoint aligns pattern tiles to
     * the page grid (multiples of 6pt = 8px @96dpi), not to the shape.
     */
    private static java.awt.TexturePaint patternTexture(SurfacePaint.PatternFill pattern) {
        return PATTERN_CACHE.computeIfAbsent(pattern, p -> {
            BufferedImage tile = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
            for (int py = 0; py < 8; py++) {
                for (int px = 0; px < 8; px++) {
                    boolean fg = ((p.bits() >>> (py * 8 + px)) & 1L) != 0;
                    tile.setRGB(px, py, fg ? p.foreground().argb() : p.background().argb());
                }
            }
            return new java.awt.TexturePaint(tile, new Rectangle2D.Double(0, 0, 8, 8));
        });
    }

    private static float[] fractionsOf(java.util.List<SurfacePaint.LinearGradient.Stop> stops) {
        float[] fractions = new float[stops.size()];
        for (int i = 0; i < stops.size(); i++) {
            fractions[i] = (float) Math.max(0, Math.min(1, stops.get(i).position()));
        }
        // AWT gradient paints require strictly-increasing fractions.
        for (int i = 1; i < fractions.length; i++) {
            if (fractions[i] <= fractions[i - 1]) {
                fractions[i] = fractions[i - 1] + 1e-5f;
            }
        }
        return fractions;
    }

    private static Color[] colorsOf(java.util.List<SurfacePaint.LinearGradient.Stop> stops) {
        Color[] colors = new Color[stops.size()];
        for (int i = 0; i < stops.size(); i++) {
            colors[i] = toAwtColor(stops.get(i).color());
        }
        return colors;
    }

    private static Color toAwtColor(SurfacePaint paint) {
        if (paint == null || paint == SurfacePaint.Transparent.INSTANCE) {
            return new Color(0, 0, 0, 0);
        }
        if (paint instanceof SurfacePaint.Solid s) {
            return new Color(s.argb(), true);
        }
        if (paint instanceof SurfacePaint.LinearGradient g && !g.stops().isEmpty()) {
            // Caller should have used applyPendingPaint, but fall back
            // to the first stop's colour so we never leave the paint unset.
            return toAwtColor(g.stops().get(0).color());
        }
        if (paint instanceof SurfacePaint.RadialGradient g && !g.stops().isEmpty()) {
            return toAwtColor(g.stops().get(0).color());
        }
        if (paint instanceof SurfacePaint.PatternFill p) {
            return toAwtColor(p.foreground());
        }
        return Color.BLACK;
    }

    private static String fontKey(SurfaceFont f) {
        return f.family() + "|" + f.weight() + "|" + f.posture()
            + "|" + Math.round(f.sizePx() * 10)
            + "|" + (f.fallbackFamily() == null ? "" : f.fallbackFamily());
    }

    /**
     * Resolve a java.awt.Font for the given descriptor. Tries the
     * FontIndex (OS-installed TTF/OTF) for the primary family first;
     * on miss, tries the declared fallback family; final fallback is
     * AWT's family-name lookup (which will substitute its own default
     * if the family is unknown). Derived fonts are cheap; loading the
     * TTF file is done once per (family, bold, italic) triple.
     */
    private static Font resolveAwtFont(SurfaceFont sf) {
        int style = (sf.weight() == SurfaceFont.Weight.BOLD ? Font.BOLD : 0)
                  | (sf.posture() == SurfaceFont.Posture.ITALIC ? Font.ITALIC : 0);
        float size = (float) Math.max(1, sf.sizePx());

        Font resolved = tryIndexedFont(sf.family(), sf.weight(), sf.posture(), size);
        if (resolved != null) return resolved.deriveFont(style, size);

        if (sf.fallbackFamily() != null && !sf.fallbackFamily().isBlank()
                && !sf.fallbackFamily().equalsIgnoreCase(sf.family())) {
            resolved = tryIndexedFont(sf.fallbackFamily(), sf.weight(), sf.posture(), size);
            if (resolved != null) {
                FontSubstitutionTracker.record(sf.family(), sf.fallbackFamily(), "awt");
                return resolved.deriveFont(style, size);
            }
        }

        // Last resort: AWT's own family-name resolution. Substitutes
        // Dialog/SansSerif if the requested family is unknown -- same
        // behaviour as AWT's default drawString path. Record the
        // substitution so HeadlessSlideRenderer can surface it to the
        // MCP response rather than silently shipping the wrong font.
        Font last = new Font(sf.family(), style, (int) size).deriveFont(size);
        if (last.getFamily() != null && !last.getFamily().equalsIgnoreCase(sf.family())) {
            FontSubstitutionTracker.record(sf.family(), last.getFamily(), "awt");
        }
        return last;
    }

    private static Font tryIndexedFont(String family, SurfaceFont.Weight weight,
                                        SurfaceFont.Posture posture, float size) {
        if (family == null || family.isBlank()) return null;
        java.nio.file.Path path = FontIndex.lookup(
            family, weight == SurfaceFont.Weight.BOLD, posture == SurfaceFont.Posture.ITALIC);
        if (path == null) return null;
        try {
            return Font.createFont(Font.TRUETYPE_FONT, path.toFile());
        } catch (Exception e) {
            return null;
        }
    }

    /** Minimal SurfaceImage implementation wrapping an AWT BufferedImage. */
    private record AwtSurfaceImage(BufferedImage image, int widthPx, int heightPx) implements SurfaceImage {
        @Override public Object nativeHandle() { return image; }
    }

    /** Snapshot of state fields for save/restore. */
    private record State(AffineTransform transform, java.awt.Paint paint, java.awt.Stroke stroke,
                          Font font, Shape clip, java.awt.Composite composite,
                          double lineWidth, double[] dashes, int cap, int join, double miterLimit,
                          SurfacePaint pendingPaint) {}
}
