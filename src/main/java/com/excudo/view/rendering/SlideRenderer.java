package com.excudo.view.rendering;

import com.excudo.core.model.ParsedSlideData;
import com.excudo.core.model.SlideShape;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.view.rendering.shapes.ModelShapeRenderer;
import com.excudo.view.rendering.shapes.PlaceholderRenderer;
import com.excudo.view.rendering.shapes.GeometricShapeRenderer;
import com.excudo.view.rendering.shapes.PictureRenderer;
import com.excudo.view.rendering.surface.CanvasRenderSurface;
import com.excudo.view.rendering.surface.RenderSurface;
import com.excudo.view.rendering.surface.SurfacePaint;
import com.excudo.view.rendering.surface.SurfaceFont;
import com.excudo.xml.parsers.SlideXMLParser;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.canvas.Canvas;
import org.w3c.dom.Document;

import java.util.ArrayList;
import java.util.List;

/**
 * Master renderer for PowerPoint slides.
 * Consumes ParsedSlideData from the model layer -- no XML re-parsing.
 *
 * Pipeline:
 *   PPTXDocument -> SlideXMLParser.parseSlide() -> ParsedSlideData
 *   ParsedSlideData.getShapeRegistry() -> List<SlideShape> (already in z-order)
 *   For each shape: dispatch to ModelShapeRenderer (Placeholder, Geometric, Picture)
 */
public class SlideRenderer {

    private final RenderingContext renderingContext;
    private final ViewportCuller viewportCuller;
    private final List<ModelShapeRenderer> renderers;
    private SlideRenderContext slideContext;

    // Rendering statistics
    private int shapesRendered;
    private int textElementsRendered;
    private long lastRenderTime;
    private int totalShapes;
    private int culledShapes;

    /**
     * GUI live-preview constructor. Wraps the provided Canvas in a
     * {@link CanvasRenderSurface} and delegates to the surface-based
     * constructor.
     */
    public SlideRenderer(Canvas canvas) {
        this(new CanvasRenderSurface(canvas), canvas.getWidth(), canvas.getHeight());
    }

    /**
     * Backend-agnostic constructor. Used by
     * {@link HeadlessSlideRenderer} when running on the AWT backend --
     * no Canvas, no FX thread, direct-to-BufferedImage output.
     */
    public SlideRenderer(RenderSurface surface) {
        this(surface, surface.widthPx(), surface.heightPx());
    }

    private SlideRenderer(RenderSurface surface, double widthPx, double heightPx) {
        this.renderingContext = new RenderingContext(
                surface,
                new CoordinateMapper(widthPx, heightPx)
        );
        this.viewportCuller = new ViewportCuller();
        this.renderers = new ArrayList<>();

        // Register renderers in priority order (first match wins)
        renderers.add(new PlaceholderRenderer());
        renderers.add(new PictureRenderer());
        renderers.add(new GeometricShapeRenderer()); // catch-all

        renderingContext.setupForShapeRendering();
    }

    public void setSlideContext(SlideRenderContext context) {
        this.slideContext = context;
    }

    /**
     * Primary entry point: render from parsed model data.
     * No XML re-parsing -- shapes come from ShapeRegistry.
     */
    public void renderSlide(ParsedSlideData slideData) {
        long startTime = System.currentTimeMillis();
        resetRenderingStats();

        try {
            renderingContext.clearCanvas();
            renderBackground();
            renderingContext.drawGridIfEnabled();
            renderingContext.drawSlideBounds();

            if (slideData != null && slideData.getShapeRegistry() != null) {
                List<SlideShape> shapes = slideData.getShapeRegistry().getAllShapes();
                totalShapes = shapes.size();

                for (SlideShape shape : shapes) {
                    // Viewport culling (model-based)
                    if (viewportCuller.isShapeInViewport(shape)) {
                        renderModelShape(shape);
                    } else {
                        culledShapes++;
                    }
                }
            }

            if (renderingContext.isDebugMode()) {
                renderDebugOverlays();
            }

        } catch (Exception e) {
            renderErrorState(e);
        } finally {
            lastRenderTime = System.currentTimeMillis() - startTime;
        }
    }

    /**
     * Legacy entry point: accepts raw XML Document.
     * Parses to ParsedSlideData, then delegates to model path.
     */
    public void renderSlide(Document slideDocument) {
        try {
            SlideXMLParser parser = new SlideXMLParser();
            ParsedSlideData data = parser.parseSlide(slideDocument);
            renderSlide(data);
        } catch (Exception e) {
            System.err.println("[SlideRenderer] ERROR: " + e.getMessage());
            e.printStackTrace();
            renderErrorState(e);
        }
    }

    // ========== SHAPE RENDERING ==========

    private void renderModelShape(SlideShape shape) {
        renderingContext.saveState();
        try {
            for (ModelShapeRenderer renderer : renderers) {
                if (renderer.canRender(shape.getType())) {
                    renderer.render(shape, renderingContext, slideContext);
                    shapesRendered++;
                    break;
                }
            }
        } catch (Exception e) {
            // Draw error indicator at shape position
            if (shape.getGeometry() != null) {
                ShapeGeometry g = shape.getGeometry();
                Rectangle2D bounds = renderingContext.getZoomedCoordinateMapper()
                    .mapToCanvas(g.getX(), g.getY(), g.getWidth(), g.getHeight());
                RenderSurface surface = renderingContext.getSurface();
                surface.setStroke(SurfacePaint.Solid.rgb(255, 0, 0));
                surface.strokeRect(bounds.getMinX(), bounds.getMinY(),
                    bounds.getWidth(), bounds.getHeight());
            }
        } finally {
            renderingContext.restoreState();
        }
    }

    // ========== BACKGROUND ==========

    private void renderBackground() {
        renderingContext.saveState();
        try {
            String bgHex = (slideContext != null) ? slideContext.getBackgroundColorHex() : "#FFFFFF";
            RenderSurface surface = renderingContext.getSurface();
            surface.setFill(SurfacePaint.Solid.fromHex(bgHex));

            Rectangle2D slideBounds = renderingContext.getZoomedCoordinateMapper().getSlideBounds();
            surface.fillRect(slideBounds.getMinX(), slideBounds.getMinY(),
                             slideBounds.getWidth(), slideBounds.getHeight());
        } finally {
            renderingContext.restoreState();
        }
    }

    // ========== DEBUG ==========

    private void renderDebugOverlays() {
        // Debug overlays will be rebuilt in Phase 3 with proper model-based shape data
    }

    private void renderErrorState(Exception e) {
        try {
            RenderSurface surface = renderingContext.getSurface();
            surface.setFill(SurfacePaint.Solid.rgb(255, 0, 0));
            surface.setFont(SurfaceFont.of("System", 12));
            surface.fillText("Render error: " + e.getMessage(), 20, 40);
        } catch (Exception ex) {
            // Can't render the error
        }
    }

    // ========== STATS ==========

    private void resetRenderingStats() {
        shapesRendered = 0;
        textElementsRendered = 0;
        totalShapes = 0;
        culledShapes = 0;
    }

    public int getShapesRendered() { return shapesRendered; }
    public int getTextElementsRendered() { return textElementsRendered; }
    public long getLastRenderTime() { return lastRenderTime; }
    public int getTotalShapes() { return totalShapes; }
    public int getCulledShapes() { return culledShapes; }
    public RenderingContext getRenderingContext() { return renderingContext; }
}
