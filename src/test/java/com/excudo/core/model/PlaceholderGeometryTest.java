package com.excudo.core.model;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Test class for PlaceholderGeometry functionality.
 * Verifies geometry data handling and conversion methods.
 */
public class PlaceholderGeometryTest {
    
    @Test
    public void testBasicGeometry() {
        PlaceholderGeometry geometry = new PlaceholderGeometry(997233, 1826684, 5568772, 4349749, "1");
        
        assertEquals(997233L, geometry.getX());
        assertEquals(1826684L, geometry.getY());
        assertEquals(5568772L, geometry.getWidth());
        assertEquals(4349749L, geometry.getHeight());
        assertEquals("1", geometry.getPlaceholderIndex());
    }
    
    @Test
    public void testPointsConversion() {
        // Test EMU to points conversion (1 point = 12700 EMUs)
        PlaceholderGeometry geometry = new PlaceholderGeometry(127000, 254000, 381000, 508000, "1");
        
        assertEquals(10.0, geometry.getXInPoints(), 0.01);
        assertEquals(20.0, geometry.getYInPoints(), 0.01);
        assertEquals(30.0, geometry.getWidthInPoints(), 0.01);
        assertEquals(40.0, geometry.getHeightInPoints(), 0.01);
    }
    
    @Test
    public void testToShapeGeometry() {
        PlaceholderGeometry placeholder = new PlaceholderGeometry(100, 200, 300, 400, "1");
        ShapeGeometry shape = placeholder.toShapeGeometry();
        
        assertEquals(100L, shape.getX());
        assertEquals(200L, shape.getY());
        assertEquals(300L, shape.getWidth());
        assertEquals(400L, shape.getHeight());
    }
    
    @Test
    public void testEquals() {
        PlaceholderGeometry geometry1 = new PlaceholderGeometry(100, 200, 300, 400, "1");
        PlaceholderGeometry geometry2 = new PlaceholderGeometry(100, 200, 300, 400, "1");
        PlaceholderGeometry geometry3 = new PlaceholderGeometry(100, 200, 300, 400, "2");
        
        assertEquals(geometry1, geometry2);
        assertNotEquals(geometry1, geometry3);
    }
    
    @Test
    public void testToString() {
        PlaceholderGeometry geometry = new PlaceholderGeometry(127000, 254000, 381000, 508000, "1");
        String result = geometry.toString();
        
        assertTrue(result.contains("idx=1"));
        assertTrue(result.contains("x=10.0pt"));
        assertTrue(result.contains("y=20.0pt"));
        assertTrue(result.contains("w=30.0pt"));
        assertTrue(result.contains("h=40.0pt"));
    }
}