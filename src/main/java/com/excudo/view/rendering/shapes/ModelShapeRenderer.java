package com.excudo.view.rendering.shapes;

import com.excudo.core.model.SlideShape;
import com.excudo.view.rendering.RenderingContext;
import com.excudo.view.rendering.SlideRenderContext;

/**
 * Interface for model-based shape renderers.
 * Accepts parsed model objects -- never raw XML.
 */
public interface ModelShapeRenderer {

    /**
     * Render a shape from its model representation.
     *
     * @param shape the parsed shape (geometry, type, xmlElement for text extraction)
     * @param ctx the rendering context (GraphicsContext, CoordinateMapper)
     * @param slideCtx slide-level context (theme, layout, document) -- nullable for tests
     */
    void render(SlideShape shape, RenderingContext ctx, SlideRenderContext slideCtx);

    /**
     * Can this renderer handle the given shape type?
     */
    boolean canRender(SlideShape.ShapeType type);
}
