package com.excudo.view.rendering.surface;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Paint;
import javafx.scene.paint.Stop;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link RenderSurface} backed by a JavaFX {@link Canvas} + {@link GraphicsContext}.
 *
 * This is the GUI live-preview backend and also (until Phase E flips the
 * default) the headless PNG backend. It is a thin translation layer:
 * every surface call maps to 1-3 GraphicsContext calls. The legacy
 * {@link javafx.scene.text.Font} / paint / image caches that used to
 * live in {@code TextPainter} migrate here, so the surface owns every
 * JavaFX interaction.
 *
 * {@link #toBufferedImage()} performs the
 * {@code Canvas.snapshot -> WritableImage -> SwingFXUtils.fromFXImage}
 * conversion internally so callers don't import
 * {@link javafx.embed.swing.SwingFXUtils}.
 */
public final class CanvasRenderSurface implements RenderSurface {

    private final Canvas canvas;
    private final GraphicsContext gc;

    // Cache resolved JavaFX Font objects per (family, weight, posture,
    // size-to-0.1-pt, fallbackFamily). The quantisation matches the
    // prior TextPainter.FONT_CACHE key policy so we don't explode the
    // cache across tiny zoom increments that render identically.
    private final ConcurrentHashMap<String, Font> fontCache = new ConcurrentHashMap<>();

    // Cache JavaFX text-node width measurements per (font-key, text).
    // First call pays the Text-node allocation + layout pass; repeats
    // are a HashMap hit. See TextPainter.computeTextWidth for history:
    // using raw TTF advance widths (FontData) drifts from JavaFX's
    // actual rendered advance when hinting/sub-pixel-snapping fires,
    // so we measure through the same Font object we'll paint with.
    private final ConcurrentHashMap<String, Double> widthCache = new ConcurrentHashMap<>();

    // Last-set font as a (family-weight-posture-size) composite key --
    // prefixed to WIDTH_CACHE lookups so the cache partitions naturally.
    private String activeFontKey = "null";

    public CanvasRenderSurface(Canvas canvas) {
        if (canvas == null) throw new IllegalArgumentException("canvas is null");
        this.canvas = canvas;
        this.gc = canvas.getGraphicsContext2D();
    }

    /**
     * Escape hatch used by {@link com.excudo.view.rendering.RenderingContext}
     * during Phase A of the Bridge refactor, where renderers still call
     * {@code ctx.getGraphicsContext()}. Removed in Phase E.
     */
    public GraphicsContext getGraphicsContext() {
        return gc;
    }

    /** Access to the underlying Canvas. Used for sizing queries. */
    public Canvas getCanvas() {
        return canvas;
    }

    // ========== DIMENSIONS ==========

    @Override public double widthPx()  { return canvas.getWidth(); }
    @Override public double heightPx() { return canvas.getHeight(); }

    // ========== PAINT STATE ==========

    @Override
    public void setFill(SurfacePaint paint) {
        gc.setFill(toFxPaint(paint));
    }

    @Override
    public void setStroke(SurfacePaint paint) {
        gc.setStroke(toFxPaint(paint));
    }

    @Override public void setLineWidth(double px)  { gc.setLineWidth(px); }
    @Override public void setLineCap(StrokeCap cap) {
        gc.setLineCap(switch (cap) {
            case BUTT   -> StrokeLineCap.BUTT;
            case ROUND  -> StrokeLineCap.ROUND;
            case SQUARE -> StrokeLineCap.SQUARE;
        });
    }
    @Override public void setLineJoin(StrokeJoin join) {
        gc.setLineJoin(switch (join) {
            case MITER -> StrokeLineJoin.MITER;
            case ROUND -> StrokeLineJoin.ROUND;
            case BEVEL -> StrokeLineJoin.BEVEL;
        });
    }
    @Override public void setMiterLimit(double limit) { gc.setMiterLimit(limit); }
    @Override public void setGlobalAlpha(double alpha) { gc.setGlobalAlpha(alpha); }

    @Override
    public void setLineDashes(double... dashes) {
        if (dashes == null || dashes.length == 0) {
            gc.setLineDashes((double[]) null);
        } else {
            gc.setLineDashes(dashes);
        }
    }

    // ========== FONT + TEXT ==========

    @Override
    public void setFont(SurfaceFont font) {
        if (font == null) {
            activeFontKey = "null";
            return;
        }
        activeFontKey = fontKey(font);
        Font resolved = fontCache.computeIfAbsent(activeFontKey, k -> resolveFont(font));
        gc.setFont(resolved);
    }

    @Override
    public double measureAdvance(String text) {
        if (text == null || text.isEmpty()) return 0;
        String key = activeFontKey + "|" + text;
        Double cached = widthCache.get(key);
        if (cached != null) return cached;
        javafx.scene.text.Text helper = new javafx.scene.text.Text(text);
        Font font = gc.getFont();
        if (font != null) helper.setFont(font);
        double width = helper.getLayoutBounds().getWidth();
        widthCache.put(key, width);
        return width;
    }

    @Override
    public void fillText(String text, double x, double baselineY) {
        gc.fillText(text, x, baselineY);
    }

    // ========== SHAPE PRIMITIVES ==========

    @Override public void fillRect(double x, double y, double w, double h)   { gc.fillRect(x, y, w, h); }
    @Override public void strokeRect(double x, double y, double w, double h) { gc.strokeRect(x, y, w, h); }
    @Override public void fillOval(double x, double y, double w, double h)   { gc.fillOval(x, y, w, h); }
    @Override public void strokeOval(double x, double y, double w, double h) { gc.strokeOval(x, y, w, h); }

    @Override
    public void fillRoundRect(double x, double y, double w, double h, double arcW, double arcH) {
        gc.fillRoundRect(x, y, w, h, arcW, arcH);
    }

    @Override
    public void strokeRoundRect(double x, double y, double w, double h, double arcW, double arcH) {
        gc.strokeRoundRect(x, y, w, h, arcW, arcH);
    }

    @Override
    public void strokeLine(double x1, double y1, double x2, double y2) {
        gc.strokeLine(x1, y1, x2, y2);
    }

    // ========== PATH OPS ==========

    @Override public void beginPath() { gc.beginPath(); }
    @Override public void moveTo(double x, double y) { gc.moveTo(x, y); }
    @Override public void lineTo(double x, double y) { gc.lineTo(x, y); }
    @Override public void quadTo(double cx, double cy, double x, double y) { gc.quadraticCurveTo(cx, cy, x, y); }
    @Override public void bezierTo(double cx1, double cy1, double cx2, double cy2, double x, double y) {
        gc.bezierCurveTo(cx1, cy1, cx2, cy2, x, y);
    }
    @Override
    public void arc(double centerX, double centerY, double radiusX, double radiusY,
                    double startAngleDeg, double arcExtentDeg) {
        gc.arc(centerX, centerY, radiusX, radiusY, startAngleDeg, arcExtentDeg);
    }
    @Override public void arcTo(double x1, double y1, double x2, double y2, double radius) {
        gc.arcTo(x1, y1, x2, y2, radius);
    }
    @Override public void closePath() { gc.closePath(); }
    @Override public void fillPath()  { gc.fill(); }
    @Override public void strokePath() { gc.stroke(); }

    // ========== IMAGE ==========

    @Override
    public void drawImage(SurfaceImage image, double x, double y, double w, double h) {
        if (image == null) return;
        Object handle = image.nativeHandle();
        if (handle instanceof Image img) {
            gc.drawImage(img, x, y, w, h);
        } else {
            throw new IllegalArgumentException("CanvasRenderSurface cannot draw SurfaceImage "
                + "backed by " + (handle == null ? "null" : handle.getClass().getName()));
        }
    }

    @Override
    public SurfaceImage decodeImage(byte[] bytes, String mimeType) {
        if (bytes == null || bytes.length == 0) return null;
        Image img = new Image(new ByteArrayInputStream(bytes));
        int w = (int) img.getWidth();
        int h = (int) img.getHeight();
        return new FxSurfaceImage(img, w, h);
    }

    // ========== STATE + TRANSFORMS ==========

    @Override public void save()    { gc.save(); }
    @Override public void restore() { gc.restore(); }
    @Override public void translate(double tx, double ty) { gc.translate(tx, ty); }
    @Override public void rotate(double degrees)          { gc.rotate(degrees); }
    @Override public void scale(double sx, double sy)     { gc.scale(sx, sy); }

    @Override
    public void clipRect(double x, double y, double w, double h) {
        gc.beginPath();
        gc.rect(x, y, w, h);
        gc.clip();
    }

    // ========== CLEAR ==========

    @Override
    public void clear(SurfacePaint background) {
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        gc.clearRect(0, 0, w, h);
        if (background != null && background != SurfacePaint.Transparent.INSTANCE) {
            gc.save();
            gc.setFill(toFxPaint(background));
            gc.fillRect(0, 0, w, h);
            gc.restore();
        }
    }

    // ========== OUTPUT ==========

    @Override
    public BufferedImage toBufferedImage() {
        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.WHITE);
        WritableImage fx = canvas.snapshot(params, null);
        return SwingFXUtils.fromFXImage(fx, null);
    }

    // ========== INTERNAL HELPERS ==========

    private Paint toFxPaint(SurfacePaint paint) {
        if (paint == null || paint == SurfacePaint.Transparent.INSTANCE) return Color.TRANSPARENT;
        if (paint instanceof SurfacePaint.Solid s) {
            return fxColor(s.argb());
        }
        if (paint instanceof SurfacePaint.LinearGradient g) {
            List<Stop> fxStops = new ArrayList<>(g.stops().size());
            for (SurfacePaint.LinearGradient.Stop st : g.stops()) {
                fxStops.add(new Stop(st.position(), fxColor(st.color().argb())));
            }
            return new LinearGradient(
                g.startX(), g.startY(), g.endX(), g.endY(),
                /*proportional*/ true,
                CycleMethod.NO_CYCLE,
                fxStops);
        }
        throw new IllegalArgumentException("unknown SurfacePaint: " + paint.getClass());
    }

    private static Color fxColor(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8)  & 0xFF;
        int b = argb & 0xFF;
        return Color.rgb(r, g, b, a / 255.0);
    }

    private static String fontKey(SurfaceFont f) {
        return f.family() + "|" + f.weight() + "|" + f.posture()
            + "|" + Math.round(f.sizePx() * 10)
            + "|" + (f.fallbackFamily() == null ? "" : f.fallbackFamily());
    }

    /**
     * Resolve a JavaFX Font for the given descriptor. Detects JavaFX's
     * silent substitution when the requested family isn't installed
     * (typically on Linux hosts without Segoe UI / Consolas / Calibri),
     * and falls back to the declared fallback family if it matches.
     * Otherwise accepts JavaFX's substitute. Logic migrated from the
     * prior {@code TextPainter.resolveFont}.
     */
    private static Font resolveFont(SurfaceFont f) {
        FontWeight weight = f.weight() == SurfaceFont.Weight.BOLD ? FontWeight.BOLD : FontWeight.NORMAL;
        FontPosture posture = f.posture() == SurfaceFont.Posture.ITALIC ? FontPosture.ITALIC : FontPosture.REGULAR;
        double size = Math.max(1, f.sizePx());

        Font requested = Font.font(f.family(), weight, posture, size);
        if (familyMatches(requested, f.family()) || f.fallbackFamily() == null
                || f.fallbackFamily().isBlank()
                || f.fallbackFamily().equalsIgnoreCase(f.family())) {
            return requested;
        }
        Font fallback = Font.font(f.fallbackFamily(), weight, posture, size);
        if (familyMatches(fallback, f.fallbackFamily())) {
            return fallback;
        }
        return requested;
    }

    /**
     * JavaFX's returned family may include the style (e.g. "Segoe UI Bold"
     * when we asked for "Segoe UI" with BOLD weight). Check for prefix /
     * equality, case-insensitively.
     */
    private static boolean familyMatches(Font actual, String requestedFamily) {
        if (actual == null) return false;
        String actualFamily = actual.getFamily();
        if (actualFamily == null) return false;
        return actualFamily.equalsIgnoreCase(requestedFamily)
            || actualFamily.toLowerCase().startsWith(requestedFamily.toLowerCase());
    }

    /**
     * Minimal SurfaceImage implementation wrapping a JavaFX Image.
     */
    private record FxSurfaceImage(Image image, int widthPx, int heightPx) implements SurfaceImage {
        @Override public Object nativeHandle() { return image; }
    }
}
