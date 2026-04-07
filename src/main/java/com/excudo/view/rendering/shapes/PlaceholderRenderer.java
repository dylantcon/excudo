package com.excudo.view.rendering.shapes;

import com.excudo.core.metrics.MeasuredText;
import com.excudo.core.metrics.TextBodyExtractor;
import com.excudo.core.metrics.TextMeasurer;
import com.excudo.core.model.*;
import com.excudo.view.rendering.*;
import com.excudo.view.rendering.text.TextPainter;
import javafx.geometry.Rectangle2D;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Renders placeholder shapes (title, body, subtitle, etc.).
 * Geometry comes from SlideRenderContext layout or the shape's own xfrm.
 * Text extracted via TextBodyExtractor, measured via TextMeasurer, painted via TextPainter.
 */
public class PlaceholderRenderer implements ModelShapeRenderer {

    @Override
    public void render(SlideShape shape, RenderingContext ctx, SlideRenderContext slideCtx) {
        if (shape.getXmlElement() == null) return;

        CoordinateMapper mapper = ctx.getZoomedCoordinateMapper();

        // 1. Resolve geometry: layout placeholder bounds or explicit xfrm
        Rectangle2D boundsPixels = resolveBounds(shape, mapper, slideCtx);
        if (boundsPixels == null || boundsPixels.getWidth() <= 0 || boundsPixels.getHeight() <= 0) {
            return; // No geometry -- can't render
        }

        // 2. Draw placeholder fill (usually transparent for placeholders)
        javafx.scene.paint.Paint fill = ShapeStyleExtractor.resolveFillColor(shape, slideCtx);
        if (!Color.TRANSPARENT.equals(fill)) {
            GraphicsContext gc = ctx.getGraphicsContext();
            gc.setFill(fill);
            gc.fillRect(boundsPixels.getMinX(), boundsPixels.getMinY(),
                       boundsPixels.getWidth(), boundsPixels.getHeight());
        }

        // 3. Extract and paint text
        String phType = getPlaceholderType(shape.getXmlElement());
        TextBody textBody = TextBodyExtractor.extractFromShape(shape.getXmlElement());
        if (textBody != null && !textBody.getParagraphs().isEmpty()) {
            long widthEmu = pixelsToEmu(boundsPixels.getWidth() / mapper.getZoomLevel());
            try {
                MeasuredText measured = TextMeasurer.measure(textBody, widthEmu);
                TextPainter.paint(textBody, measured, boundsPixels, ctx, slideCtx, phType);
            } catch (Exception e) {
                // Fallback: render raw text at bounds origin
                GraphicsContext gc = ctx.getGraphicsContext();
                gc.setFill(Color.RED);
                gc.fillText("Text render error: " + e.getMessage(),
                    boundsPixels.getMinX() + 5, boundsPixels.getMinY() + 20);
            }
        }
    }

    @Override
    public boolean canRender(SlideShape.ShapeType type) {
        return type == SlideShape.ShapeType.PLACEHOLDER;
    }

    /**
     * Resolve pixel bounds for a placeholder shape.
     * Priority: shape's own a:xfrm > layout placeholder geometry > null.
     */
    private Rectangle2D resolveBounds(SlideShape shape, CoordinateMapper mapper, SlideRenderContext slideCtx) {
        // First try explicit geometry on the shape
        if (shape.getGeometry() != null) {
            ShapeGeometry g = shape.getGeometry();
            if (g.getWidth() > 0 && g.getHeight() > 0) {
                return mapper.mapToCanvas(g.getX(), g.getY(), g.getWidth(), g.getHeight());
            }
        }

        // Fall back to layout placeholder geometry
        if (slideCtx != null && shape.getXmlElement() != null) {
            String phType = getPlaceholderType(shape.getXmlElement());
            String phIdx = getPlaceholderIndex(shape.getXmlElement());

            PlaceholderGeometry geom = null;
            if (phType != null && !phType.isEmpty()) {
                geom = slideCtx.getPlaceholderGeometry(phType);
            }
            if (geom == null && phIdx != null && !phIdx.isEmpty()) {
                geom = slideCtx.getPlaceholderGeometryByIndex(phIdx);
            }
            if (geom != null) {
                return mapper.mapToCanvas(geom.getX(), geom.getY(),
                    geom.getWidth(), geom.getHeight());
            }
        }

        return null;
    }

    private String getPlaceholderType(Element shapeElement) {
        NodeList phList = shapeElement.getElementsByTagName("p:ph");
        if (phList.getLength() > 0) {
            return ((Element) phList.item(0)).getAttribute("type");
        }
        return null;
    }

    private String getPlaceholderIndex(Element shapeElement) {
        NodeList phList = shapeElement.getElementsByTagName("p:ph");
        if (phList.getLength() > 0) {
            return ((Element) phList.item(0)).getAttribute("idx");
        }
        return null;
    }

    private static long pixelsToEmu(double pixels) {
        return (long) (pixels * 914400.0 / 96.0);
    }
}
