package com.excudo.view.rendering;

import com.excudo.core.model.ParsedSlideData;
import com.excudo.core.model.SlideShape;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.view.rendering.shapes.ModelShapeRenderer;
import com.excudo.view.rendering.shapes.PlaceholderRenderer;
import com.excudo.view.rendering.shapes.GeometricShapeRenderer;
import com.excudo.view.rendering.shapes.PictureRenderer;
import com.excudo.view.rendering.surface.CanvasRenderSurface;
import com.excudo.core.rendering.surface.RenderSurface;
import com.excudo.core.rendering.surface.SurfacePaint;
import com.excudo.core.rendering.surface.SurfaceFont;
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

    // Editor-canvas decoration: the gray outline marking the slide edge.
    // GUI-only — exported/headless renders must match PowerPoint's own
    // raster, which has no frame (the outline previously leaked into
    // every headless PNG and skewed the parity harness's
    // background-color estimate).
    private final boolean drawSlideBoundsOutline;

    /**
     * GUI live-preview constructor. Wraps the provided Canvas in a
     * {@link CanvasRenderSurface} and delegates to the surface-based
     * constructor.
     */
    public SlideRenderer(Canvas canvas) {
        this(new CanvasRenderSurface(canvas), canvas.getWidth(), canvas.getHeight(), true);
    }

    /**
     * Backend-agnostic constructor. Used by
     * {@link HeadlessSlideRenderer} when running on the AWT backend --
     * no Canvas, no FX thread, direct-to-BufferedImage output.
     */
    public SlideRenderer(RenderSurface surface) {
        this(surface, surface.widthPx(), surface.heightPx(), false);
    }

    private SlideRenderer(RenderSurface surface, double widthPx, double heightPx,
                          boolean drawSlideBoundsOutline) {
        this.drawSlideBoundsOutline = drawSlideBoundsOutline;
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
            renderInheritedDecorations();
            renderingContext.drawGridIfEnabled();
            if (drawSlideBoundsOutline) {
                renderingContext.drawSlideBounds();
            }

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
            // Post-shape debug overlays: bounds + SPID label. Both
            // honor the RenderingContext flags internally (no-op when
            // the corresponding toggle is off), so calling them
            // unconditionally is correct + lets the View-menu toggles
            // actually produce visible output.
            if (shape.getGeometry() != null) {
                ShapeGeometry g = shape.getGeometry();
                Rectangle2D debugBounds = renderingContext.getZoomedCoordinateMapper()
                    .mapToCanvas(g.getX(), g.getY(), g.getWidth(), g.getHeight());
                renderingContext.drawDebugBounds(debugBounds,
                    SurfacePaint.Solid.rgb(0, 200, 255));
                renderingContext.drawSpidLabel(shape.getSpid(),
                    new javafx.geometry.Point2D(debugBounds.getMinX(), debugBounds.getMinY()));
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

    // ========== INHERITED DECORATIONS ==========

    /**
     * Render the non-placeholder shapes of the slide master and layout
     * spTrees under the slide's own shapes, the way PowerPoint composes
     * a slide (master decorations, then layout decorations, then slide
     * content). Placeholders are skipped: the slide's own placeholder
     * instances render the content. A layout with showMasterSp="0"
     * suppresses the master's decorations.
     *
     * <p>Decoration shapes are overwhelmingly custom geometry (theme
     * artwork such as doodles/grids exported by authoring tools), which
     * the ECMA-376 geometry engine renders exactly.
     */
    private void renderInheritedDecorations() {
        if (slideContext == null) return;
        Document layout = null;
        try {
            layout = slideContext.getLayoutDom();
            boolean showMasterSp = true;
            if (layout != null) {
                String attr = layout.getDocumentElement().getAttribute("showMasterSp");
                showMasterSp = !("0".equals(attr) || "false".equalsIgnoreCase(attr));
            }
            if (showMasterSp) {
                renderDecorationShapes(slideContext.getMasterDom());
            }
        } catch (Exception e) {
            // Decoration is best-effort: the slide's own content must
            // still render.
        }
        renderDecorationShapes(layout);
    }

    private void renderDecorationShapes(Document dom) {
        if (dom == null) return;
        try {
            ParsedSlideData data = new SlideXMLParser().parseSlide(dom);
            if (data == null || data.getShapeRegistry() == null) return;
            for (SlideShape shape : data.getShapeRegistry().getAllShapes()) {
                if (shape.getType() == SlideShape.ShapeType.PLACEHOLDER) continue;
                renderModelShape(shape);
            }
        } catch (Exception e) {
            // Skip broken decoration trees; slide content still renders.
        }
    }

    // ========== BACKGROUND ==========

    private void renderBackground() {
        renderingContext.saveState();
        try {
            RenderSurface surface = renderingContext.getSurface();
            Rectangle2D slideBounds = renderingContext.getZoomedCoordinateMapper().getSlideBounds();

            // Prefer typed background: image fills (theme wallpaper via
            // bgFillStyleLst blipFill) need to decode + draw the embedded
            // picture, not paint a solid approximation of its phClr.
            com.excudo.core.model.SlideBackground typed = slideContext != null
                ? slideContext.getSlideBackground() : null;
            if (typed instanceof com.excudo.core.model.SlideBackground.BlipImage img
                    && drawBlipBackground(surface, img, slideBounds)) {
                return;
            }

            // Gradient backgrounds paint the full ramp (previously
            // collapsed to the first stop's color).
            if (typed instanceof com.excudo.core.model.SlideBackground.Gradient grad) {
                surface.setFill(ShapeStyleExtractor.backgroundGradientPaint(
                    grad, slideBounds.getWidth(), slideBounds.getHeight()));
                surface.fillRect(slideBounds.getMinX(), slideBounds.getMinY(),
                                 slideBounds.getWidth(), slideBounds.getHeight());
                return;
            }

            String bgHex = (slideContext != null) ? slideContext.getBackgroundColorHex() : "#FFFFFF";
            surface.setFill(SurfacePaint.Solid.fromHex(bgHex));
            surface.fillRect(slideBounds.getMinX(), slideBounds.getMinY(),
                             slideBounds.getWidth(), slideBounds.getHeight());
        } finally {
            renderingContext.restoreState();
        }
    }

    /**
     * Decode the theme's blipFill image, apply duotone recolor when the
     * theme specifies one, and stretch the result over the slide bounds.
     * Returns false on decode / recolor failure so the caller falls back
     * to the solid-hex path rather than leaving the slide background
     * blank.
     */
    private boolean drawBlipBackground(RenderSurface surface,
                                        com.excudo.core.model.SlideBackground.BlipImage img,
                                        Rectangle2D slideBounds) {
        try {
            byte[] bytes = img.imageBytes();
            if (img.hasDuotone()) {
                bytes = com.excudo.core.rendering.DuotoneRecolor.apply(
                    bytes, img.duotoneShadowHex(), img.duotoneHighlightHex());
            }
            com.excudo.core.rendering.surface.SurfaceImage decoded =
                surface.decodeImage(bytes, img.mimeType());
            if (decoded == null) return false;
            surface.drawImage(decoded,
                slideBounds.getMinX(), slideBounds.getMinY(),
                slideBounds.getWidth(), slideBounds.getHeight());
            return true;
        } catch (Exception e) {
            return false;
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
