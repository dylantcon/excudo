package com.excudo.view.rendering.shapes;

import com.excudo.view.rendering.RenderingContext;
import org.w3c.dom.Element;

/**
 * Base interface for all PowerPoint shape renderers.
 * Each shape type (rectangle, ellipse, path, etc.) implements this interface
 * to provide shape-specific rendering logic.
 */
public interface ShapeRenderer {
    
    /**
     * Render the shape element to the rendering context
     * @param shapeElement XML element containing shape data
     * @param context Rendering context with graphics and coordinate mapping
     */
    void render(Element shapeElement, RenderingContext context);
    
    /**
     * Get the shape types this renderer can handle
     * @return Array of XML element names this renderer supports
     */
    String[] getSupportedShapeTypes();
    
    /**
     * Check if this renderer can handle the given shape element
     * @param shapeElement XML element to check
     * @return true if this renderer can handle the element
     */
    default boolean canRender(Element shapeElement) {
        String tagName = shapeElement.getTagName();
        for (String supportedType : getSupportedShapeTypes()) {
            if (supportedType.equals(tagName)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Get rendering priority (lower numbers render first)
     * @return Priority value for rendering order
     */
    default int getRenderingPriority() {
        return 100; // Default priority
    }
}