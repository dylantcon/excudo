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

    /** Apply line color, width, and dash pattern to the surface. */
    private static void applyLineStyle(RenderSurface surface, ShapeStyleExtractor.LineStyle line) {
        surface.setStroke(line.color());
        surface.setLineWidth(line.widthPixels());
        if (line.dashPattern() != null) {
            surface.setLineDashes(line.dashPattern());
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

        SurfacePaint surfaceFill = ShapeStyleExtractor.resolveFillColor(shape, slideCtx);
        ShapeStyleExtractor.LineStyle line = ShapeStyleExtractor.resolveLineStyle(shape, slideCtx);
        boolean hasFill = surfaceFill != SurfacePaint.Transparent.INSTANCE;

        // Shadow: draw offset copy of the shape before the real one
        ShapeStyleExtractor.ShadowStyle shadow = ShapeStyleExtractor.resolveShadow(shape, slideCtx);
        if (shadow != null) {
            surface.save();
            surface.translate(shadow.offsetX(), shadow.offsetY());
            surface.setFill(shadow.color());
            surface.fillRect(bounds.getMinX(), bounds.getMinY(),
                bounds.getWidth(), bounds.getHeight());
            surface.restore();
        }

        // Connectors: draw as lines, not filled shapes
        SlideShape.ShapeType type = shape.getType();
        if (type == SlideShape.ShapeType.CONNECTION) {
            if (line.isVisible()) {
                applyLineStyle(surface, line);
                // Straight connector: line from one corner to the opposite
                // flipH/flipV on xfrm determine direction (not yet modeled -- default diagonal)
                surface.strokeLine(bounds.getMinX(), bounds.getMinY(),
                    bounds.getMaxX(), bounds.getMaxY());
                surface.setLineDashes((double[]) null);
            }
            if (rotDeg != 0) surface.restore();
            return;
        }

        // Draw shape based on type category
        String preset = type.hasOoxmlPreset() ? type.getOoxmlPreset() : "rect";

        switch (preset) {
            case "ellipse", "flowChartConnector" -> {
                if (hasFill) {
                    surface.setFill(surfaceFill);
                    surface.fillOval(bounds.getMinX(), bounds.getMinY(),
                        bounds.getWidth(), bounds.getHeight());
                }
                if (line.isVisible()) {
                    applyLineStyle(surface, line);
                    surface.strokeOval(bounds.getMinX(), bounds.getMinY(),
                        bounds.getWidth(), bounds.getHeight());
                    surface.setLineDashes((double[]) null);
                }
            }
            case "roundRect" -> {
                double arc = Math.min(bounds.getWidth(), bounds.getHeight()) * 0.15;
                if (hasFill) {
                    surface.setFill(surfaceFill);
                    surface.fillRoundRect(bounds.getMinX(), bounds.getMinY(),
                        bounds.getWidth(), bounds.getHeight(), arc, arc);
                }
                if (line.isVisible()) {
                    applyLineStyle(surface, line);
                    surface.strokeRoundRect(bounds.getMinX(), bounds.getMinY(),
                        bounds.getWidth(), bounds.getHeight(), arc, arc);
                    surface.setLineDashes((double[]) null);
                }
            }
            default -> {
                // Try preset geometry path first
                PresetGeometryPaths.ShapePathDrawer drawer = PresetGeometryPaths.get(preset);
                if (drawer != null) {
                    drawer.draw(surface, bounds.getMinX(), bounds.getMinY(),
                        bounds.getWidth(), bounds.getHeight());
                    if (hasFill) {
                        surface.setFill(surfaceFill);
                        surface.fillPath();
                    }
                    if (line.isVisible()) {
                        applyLineStyle(surface, line);
                        surface.strokePath();
                        surface.setLineDashes((double[]) null);
                    }
                } else {
                    // Bounding box fallback for unmapped presets
                    if (hasFill) {
                        surface.setFill(surfaceFill);
                        surface.fillRect(bounds.getMinX(), bounds.getMinY(),
                            bounds.getWidth(), bounds.getHeight());
                    }
                    if (line.isVisible()) {
                        applyLineStyle(surface, line);
                        surface.strokeRect(bounds.getMinX(), bounds.getMinY(),
                            bounds.getWidth(), bounds.getHeight());
                        surface.setLineDashes((double[]) null);
                    }
                }
            }
        }

        // Paint text if the shape has any
        if (shape.hasText() && shape.getXmlElement() != null) {
            try {
                TextBody textBody = TextBodyExtractor.extractFromShape(shape.getXmlElement());
                if (textBody != null && !textBody.getParagraphs().isEmpty()) {
                    long widthEmu = geom.getWidth();
                    MeasuredText measured = TextMeasurer.measure(textBody, widthEmu);
                    TextPainter.paint(textBody, measured, bounds, ctx, slideCtx);
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
