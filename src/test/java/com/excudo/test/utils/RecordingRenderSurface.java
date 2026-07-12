package com.excudo.test.utils;

import com.excudo.core.rendering.surface.RenderSurface;
import com.excudo.core.rendering.surface.StrokeCap;
import com.excudo.core.rendering.surface.StrokeJoin;
import com.excudo.core.rendering.surface.SurfaceFont;
import com.excudo.core.rendering.surface.SurfaceImage;
import com.excudo.core.rendering.surface.SurfacePaint;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Test double for {@link RenderSurface} that records every
 * {@link #fillText} call (text, position, active font, active fill)
 * while drawing nothing. {@link #measureAdvance} delegates to real AWT
 * TextLayout metrics — the same engine {@code Graphics2DRenderSurface}
 * uses — so any painter code that still measures against the surface
 * behaves exactly as it does in production headless rendering.
 */
public final class RecordingRenderSurface implements RenderSurface {

    /** One recorded fillText invocation. */
    public record TextCall(String text, double x, double baselineY,
                           SurfaceFont font, SurfacePaint fill) {}

    public final List<TextCall> textCalls = new ArrayList<>();
    public int clipRectCalls = 0;

    private final int widthPx;
    private final int heightPx;
    private SurfaceFont currentFont;
    private SurfacePaint currentFill;
    private final FontRenderContext frc =
        new FontRenderContext(new AffineTransform(), true, true);

    public RecordingRenderSurface(int widthPx, int heightPx) {
        this.widthPx = widthPx;
        this.heightPx = heightPx;
    }

    // ========== RECORDING QUERIES ==========

    /** Distinct baseline Y values of all recorded text, ascending. */
    public List<Double> distinctBaselines() {
        return textCalls.stream()
            .map(c -> Math.round(c.baselineY() * 8) / 8.0)
            .distinct()
            .sorted()
            .toList();
    }

    /** Advance width of {@code text} in the given font per AWT TextLayout. */
    public double advanceOf(SurfaceFont font, String text) {
        if (text == null || text.isEmpty()) return 0;
        return new TextLayout(text, awtFont(font), frc).getAdvance();
    }

    // ========== FONT + TEXT ==========

    @Override public void setFont(SurfaceFont font) { this.currentFont = font; }

    @Override
    public double measureAdvance(String text) {
        if (text == null || text.isEmpty() || currentFont == null) return 0;
        return new TextLayout(text, awtFont(currentFont), frc).getAdvance();
    }

    @Override
    public void fillText(String text, double x, double baselineY) {
        if (text == null || text.isEmpty()) return;
        textCalls.add(new TextCall(text, x, baselineY, currentFont, currentFill));
    }

    private Font awtFont(SurfaceFont f) {
        int style = Font.PLAIN;
        if (f.weight() == SurfaceFont.Weight.BOLD) style |= Font.BOLD;
        if (f.posture() == SurfaceFont.Posture.ITALIC) style |= Font.ITALIC;
        return new Font(f.family(), style, 1).deriveFont((float) f.sizePx());
    }

    // ========== STATE (recorded minimally) ==========

    @Override public void setFill(SurfacePaint paint) { this.currentFill = paint; }
    @Override public void setStroke(SurfacePaint paint) {}
    @Override public void setLineWidth(double px) {}
    @Override public void setLineDashes(double... dashes) {}
    @Override public void setLineCap(StrokeCap cap) {}
    @Override public void setLineJoin(StrokeJoin join) {}
    @Override public void setMiterLimit(double limit) {}
    @Override public void setGlobalAlpha(double alpha) {}

    // ========== GEOMETRY (no-op) ==========

    @Override public double widthPx() { return widthPx; }
    @Override public double heightPx() { return heightPx; }
    @Override public void fillRect(double x, double y, double w, double h) {}
    @Override public void strokeRect(double x, double y, double w, double h) {}
    @Override public void fillOval(double x, double y, double w, double h) {}
    @Override public void strokeOval(double x, double y, double w, double h) {}
    @Override public void fillRoundRect(double x, double y, double w, double h,
                                        double arcW, double arcH) {}
    @Override public void strokeRoundRect(double x, double y, double w, double h,
                                          double arcW, double arcH) {}
    @Override public void strokeLine(double x1, double y1, double x2, double y2) {}
    @Override public void beginPath() {}
    @Override public void moveTo(double x, double y) {}
    @Override public void lineTo(double x, double y) {}
    @Override public void quadTo(double cx, double cy, double x, double y) {}
    @Override public void bezierTo(double cx1, double cy1, double cx2, double cy2,
                                   double x, double y) {}
    @Override public void arc(double centerX, double centerY, double radiusX, double radiusY,
                              double startAngleDeg, double arcExtentDeg) {}
    @Override public void arcTo(double x1, double y1, double x2, double y2, double radius) {}
    @Override public void closePath() {}
    @Override public void fillPath() {}
    @Override public void strokePath() {}
    @Override public void drawImage(SurfaceImage image, double x, double y, double w, double h) {}
    @Override public SurfaceImage decodeImage(byte[] bytes, String mimeType) { return null; }
    @Override public void save() {}
    @Override public void restore() {}
    @Override public void translate(double tx, double ty) {}
    @Override public void rotate(double degrees) {}
    @Override public void scale(double sx, double sy) {}
    @Override public void clipRect(double x, double y, double w, double h) { clipRectCalls++; }
    @Override public void clear(SurfacePaint background) {}

    @Override
    public BufferedImage toBufferedImage() {
        return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
    }
}
