package com.excudo.view.rendering.shapes;

import com.excudo.core.metrics.MeasuredText;
import com.excudo.core.metrics.TextBodyExtractor;
import com.excudo.core.metrics.TextMeasurer;
import com.excudo.core.model.*;
import com.excudo.view.rendering.*;
import com.excudo.core.rendering.surface.RenderSurface;
import com.excudo.core.rendering.surface.SurfacePaint;
import com.excudo.view.rendering.text.TextPainter;
import javafx.geometry.Rectangle2D;

/**
 * Renders geometric shapes (rectangles, ellipses, arrows, flowcharts, etc.).
 * Handles all 145+ OOXML preset geometry types.
 *
 * For common types (rect, ellipse), draws the correct shape.
 * For everything else, draws the bounding box with the preset type label.
 */
public class GeometricShapeRenderer implements ModelShapeRenderer {

    /**
     * Endpoints of a straight connector within its bounding box, honoring
     * flipH/flipV. A connector runs along one diagonal of the box; the flip
     * flags pick which. Since no arrowhead is drawn, only the diagonal matters:
     * {@code flipH == flipV} -> the main diagonal {@code (minX,minY)-(maxX,maxY)};
     * exactly one flag set -> the anti-diagonal {@code (minX,maxY)-(maxX,minY)}.
     * Returns {@code {x1, y1, x2, y2}}.
     */
    static double[] connectorEndpoints(double minX, double minY, double maxX, double maxY,
                                       boolean flipH, boolean flipV) {
        if (flipH ^ flipV) {
            return new double[]{minX, maxY, maxX, minY}; // anti-diagonal (BL->TR)
        }
        return new double[]{minX, minY, maxX, maxY};     // main diagonal (TL->BR)
    }

    /** Apply line color, width, and dash pattern to the surface. */
    private static void applyLineStyle(RenderSurface surface, ShapeStyleExtractor.LineStyle line) {
        surface.setStroke(line.color());
        surface.setLineWidth(line.widthPixels());
        if (line.dashPattern() != null) {
            surface.setLineDashes(line.dashPattern());
        }
    }

    /**
     * Fill the shape silhouette for {@code preset} at {@code bounds} with
     * the surface's current fill paint. Used by both the body fill and the
     * shadow pass -- they share path geometry, only the paint and the
     * translation differ.
     */
    private static void fillShape(RenderSurface surface, String preset, Rectangle2D bounds) {
        double x = bounds.getMinX(), y = bounds.getMinY();
        double w = bounds.getWidth(), h = bounds.getHeight();
        switch (preset) {
            case "ellipse", "flowChartConnector" -> surface.fillOval(x, y, w, h);
            case "roundRect" -> {
                double arc = Math.min(w, h) * 0.15;
                surface.fillRoundRect(x, y, w, h, arc, arc);
            }
            default -> {
                PresetGeometryPaths.ShapePathDrawer drawer = PresetGeometryPaths.get(preset);
                if (drawer != null) {
                    drawer.draw(surface, x, y, w, h);
                    surface.fillPath();
                } else {
                    surface.fillRect(x, y, w, h);
                }
            }
        }
    }

    /**
     * Stroke the shape silhouette for {@code preset} at {@code bounds}
     * with the surface's current stroke style. Sister to {@link #fillShape}.
     */
    private static void strokeShape(RenderSurface surface, String preset, Rectangle2D bounds) {
        double x = bounds.getMinX(), y = bounds.getMinY();
        double w = bounds.getWidth(), h = bounds.getHeight();
        switch (preset) {
            case "ellipse", "flowChartConnector" -> surface.strokeOval(x, y, w, h);
            case "roundRect" -> {
                double arc = Math.min(w, h) * 0.15;
                surface.strokeRoundRect(x, y, w, h, arc, arc);
            }
            default -> {
                PresetGeometryPaths.ShapePathDrawer drawer = PresetGeometryPaths.get(preset);
                if (drawer != null) {
                    drawer.draw(surface, x, y, w, h);
                    surface.strokePath();
                } else {
                    surface.strokeRect(x, y, w, h);
                }
            }
        }
    }

    @Override
    public void render(SlideShape shape, RenderingContext ctx, SlideRenderContext slideCtx) {
        ShapeGeometry geom = shape.getGeometry();
        if (geom == null || geom.getWidth() <= 0 || geom.getHeight() <= 0) return;

        CoordinateMapper mapper = ctx.getZoomedCoordinateMapper();
        Rectangle2D bounds = mapper.mapToCanvas(geom.getX(), geom.getY(),
            geom.getWidth(), geom.getHeight());

        RenderSurface surface = ctx.getSurface();

        // Apply rotation around shape center
        double rotDeg = geom.getRotationDegrees();
        if (rotDeg != 0) {
            double cx = bounds.getMinX() + bounds.getWidth() / 2;
            double cy = bounds.getMinY() + bounds.getHeight() / 2;
            surface.save();
            surface.translate(cx, cy);
            surface.rotate(rotDeg);
            surface.translate(-cx, -cy);
        }

        SurfacePaint surfaceFill = ShapeStyleExtractor.resolveFillColor(shape, slideCtx, surface);
        ShapeStyleExtractor.LineStyle line = ShapeStyleExtractor.resolveLineStyle(shape, slideCtx);
        boolean hasFill = surfaceFill != SurfacePaint.Transparent.INSTANCE;

        // Connectors: draw as lines, not filled shapes
        SlideShape.ShapeType type = shape.getType();
        if (type == SlideShape.ShapeType.CONNECTION) {
            if (line.isVisible()) {
                applyLineStyle(surface, line);
                // A straight connector is stored as a bounding box plus flipH/flipV,
                // which select which diagonal the line runs along. Ignoring the flips
                // rendered every flipped connector mirrored.
                double[] e = connectorEndpoints(bounds.getMinX(), bounds.getMinY(),
                    bounds.getMaxX(), bounds.getMaxY(), geom.isFlipH(), geom.isFlipV());
                surface.strokeLine(e[0], e[1], e[2], e[3]);
                surface.setLineDashes((double[]) null);
            }
            if (rotDeg != 0) surface.restore();
            return;
        }

        String preset = type.hasOoxmlPreset() ? type.getOoxmlPreset() : "rect";

        // Shadow pass: paint the same shape silhouette offset behind the
        // body. Reusing fillShape with the preset means an mathPlus, ellipse,
        // or arrow casts a shadow shaped like itself -- not a bounding-box
        // rect, which is what the previous implementation did and which
        // showed up as ugly dark squares behind the math symbols on the
        // JavaScript-events deck.
        ShapeStyleExtractor.ShadowStyle shadow = ShapeStyleExtractor.resolveShadow(shape, slideCtx);
        if (shadow != null) {
            surface.save();
            surface.translate(shadow.offsetX(), shadow.offsetY());
            surface.setFill(shadow.color());
            fillShape(surface, preset, bounds);
            surface.restore();
        }

        // Body pass: fill then stroke
        if (hasFill) {
            surface.setFill(surfaceFill);
            fillShape(surface, preset, bounds);
        }
        if (line.isVisible()) {
            applyLineStyle(surface, line);
            strokeShape(surface, preset, bounds);
            surface.setLineDashes((double[]) null);
        }

        // Paint text if the shape has any
        if (shape.hasText() && shape.getXmlElement() != null) {
            try {
                TextBody textBody = TextBodyExtractor.extractFromShape(shape.getXmlElement());
                if (textBody != null && !textBody.getParagraphs().isEmpty()) {
                    long widthEmu = geom.getWidth();
                    // Non-placeholder shapes inherit from their own
                    // lstStyle + presentation defaultTextStyle. The same
                    // source feeds measurement and painting so the two
                    // can never disagree.
                    com.excudo.core.metrics.TextStyleSource styles =
                        com.excudo.view.rendering.text.LstStyleResolver.forShape(
                            slideCtx, null, null, shape.getXmlElement());
                    MeasuredText measured = TextMeasurer.measure(textBody, widthEmu, styles);
                    TextPainter.paint(textBody, measured, bounds, ctx, slideCtx, null, styles);
                }
            } catch (Exception e) {
                // Non-critical -- shape renders, text doesn't
            }
        }

        // Restore graphics state after rotation
        if (rotDeg != 0) {
            surface.restore();
        }
    }

    @Override
    public boolean canRender(SlideShape.ShapeType type) {
        // Catch-all for geometric shapes. GROUP shapes are not rendered directly --
        // their children are already in the flat registry with transformed coordinates.
        return type != SlideShape.ShapeType.PLACEHOLDER
            && type != SlideShape.ShapeType.PICTURE
            && type != SlideShape.ShapeType.GROUP;
    }
}
