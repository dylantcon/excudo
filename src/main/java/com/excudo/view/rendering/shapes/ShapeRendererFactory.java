package com.excudo.view.rendering.shapes;

import java.util.ArrayList;
import java.util.List;

/**
 * Factory for shape renderers.
 * Phase 1: Empty -- no renderers registered.
 * Phase 2: Will register model-based renderers (PlaceholderRenderer, GeometricShapeRenderer, PictureRenderer).
 */
public class ShapeRendererFactory {

    private final List<ShapeRenderer> registeredRenderers = new ArrayList<>();

    public ShapeRendererFactory() {
        // Phase 2 will register model-based renderers here
    }

    public void registerRenderer(ShapeRenderer renderer) {
        registeredRenderers.add(renderer);
    }

    public ShapeRenderer getRenderer(String shapeType) {
        return null; // No renderers in Phase 1
    }

    public List<ShapeRenderer> getAllRenderers() {
        return new ArrayList<>(registeredRenderers);
    }
}
