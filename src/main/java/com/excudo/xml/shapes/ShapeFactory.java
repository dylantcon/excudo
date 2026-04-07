package com.excudo.xml.shapes;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.SlideShape;

/**
 * Abstract Factory pattern for creating OOXML shape elements.
 * This factory encapsulates the creation logic for different shape types,
 * removing the responsibility from SlideXMLWriter and enabling extensibility.
 * 
 * Factories implementing this interface should extend AbstractShapeFactory
 * for common functionality and consistent XML structure.
 */
public interface ShapeFactory {
    
    /**
     * Create a complete shape element with all required OOXML structure.
     * 
     * @param document The XML document for creating elements
     * @param shapeType The type of shape to create
     * @param spid The shape ID
     * @param name The shape name
     * @param geometry The shape geometry (position and size)
     * @param text Optional text content (may be null)
     * @return The created shape element
     */
    Element createShape(Document document, SlideShape.ShapeType shapeType, 
                       int spid, String name, ShapeGeometry geometry, String text);
    
    /**
     * Create shape properties element with specific shape type.
     * Handles preset geometry, transform, and type-specific properties.
     * 
     * @param document The XML document
     * @param shapeType The type of shape
     * @param geometry The shape geometry
     * @return The shape properties element
     */
    Element createShapeProperties(Document document, SlideShape.ShapeType shapeType, 
                                 ShapeGeometry geometry);
    
    /**
     * Create the preset geometry element for the shape type.
     * This method handles the core difference between shape types.
     * 
     * @param document The XML document
     * @param shapeType The type of shape
     * @return The preset geometry element
     */
    Element createPresetGeometry(Document document, SlideShape.ShapeType shapeType);
    
    /**
     * Create non-visual shape properties (nvSpPr) element.
     * Common structure for all shape types.
     * 
     * @param document The XML document
     * @param spid The shape ID
     * @param name The shape name
     * @return The non-visual properties element
     */
    Element createNonVisualProperties(Document document, int spid, String name);
    
    /**
     * Create text body element with content.
     * Uses TextFormatUtils for bullet point and formatting support.
     * 
     * @param document The XML document
     * @param text The text content (may be null)
     * @return The text body element, or null if no text
     */
    Element createTextBody(Document document, String text);
    
    /**
     * Check if this factory supports the given shape type.
     * 
     * @param shapeType The shape type to check
     * @return true if this factory can create the shape type
     */
    boolean supportsShapeType(SlideShape.ShapeType shapeType);
    
    /**
     * Get the shape types supported by this factory.
     * 
     * @return Array of supported shape types
     */
    SlideShape.ShapeType[] getSupportedShapeTypes();
}