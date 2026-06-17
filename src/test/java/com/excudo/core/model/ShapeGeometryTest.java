package com.excudo.core.model;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for ShapeGeometry focusing on mathematical accuracy:
 * - EMU to points conversion precision
 * - Edge cases with extreme values
 * - Coordinate calculation accuracy
 * - String formatting consistency
 */
public class ShapeGeometryTest {
    
    // PowerPoint conversion constant: 1 point = 12700 EMUs
    private static final double EMU_PER_POINT = 12700.0;
    private static final double DELTA = 0.0001; // Precision tolerance for double comparisons
    
    // ===== EMU to Points Conversion Accuracy Tests =====
    
    @Test
    public void testEmuToPointsConversionAccuracy() {
        // Test known conversions
        ShapeGeometry geometry = new ShapeGeometry(127000L, 254000L, 381000L, 508000L);
        
        // 127000 EMUs = 10 points exactly
        assertEquals("X conversion should be accurate", 10.0, geometry.getXInPoints(), DELTA);
        // 254000 EMUs = 20 points exactly  
        assertEquals("Y conversion should be accurate", 20.0, geometry.getYInPoints(), DELTA);
        // 381000 EMUs = 30 points exactly
        assertEquals("Width conversion should be accurate", 30.0, geometry.getWidthInPoints(), DELTA);
        // 508000 EMUs = 40 points exactly
        assertEquals("Height conversion should be accurate", 40.0, geometry.getHeightInPoints(), DELTA);
    }
    
    @Test
    public void testEmuToPointsConversionPrecision() {
        // Test fractional conversions
        ShapeGeometry geometry = new ShapeGeometry(6350L, 19050L, 31750L, 44450L);
        
        // 6350 EMUs = 0.5 points
        assertEquals("Half-point X conversion", 0.5, geometry.getXInPoints(), DELTA);
        // 19050 EMUs = 1.5 points
        assertEquals("One-and-half point Y conversion", 1.5, geometry.getYInPoints(), DELTA);
        // 31750 EMUs = 2.5 points
        assertEquals("Two-and-half point width conversion", 2.5, geometry.getWidthInPoints(), DELTA);
        // 44450 EMUs = 3.5 points
        assertEquals("Three-and-half point height conversion", 3.5, geometry.getHeightInPoints(), DELTA);
    }
    
    @Test
    public void testSingleEmuConversion() {
        // Test minimum precision
        ShapeGeometry geometry = new ShapeGeometry(1L, 1L, 1L, 1L);
        
        double expectedPoints = 1.0 / EMU_PER_POINT;
        assertEquals("Single EMU X conversion", expectedPoints, geometry.getXInPoints(), DELTA);
        assertEquals("Single EMU Y conversion", expectedPoints, geometry.getYInPoints(), DELTA);
        assertEquals("Single EMU width conversion", expectedPoints, geometry.getWidthInPoints(), DELTA);
        assertEquals("Single EMU height conversion", expectedPoints, geometry.getHeightInPoints(), DELTA);
    }
    
    @Test
    public void testLargeValueConversion() {
        // Test with large EMU values (representing large presentation dimensions)
        ShapeGeometry geometry = new ShapeGeometry(12700000L, 25400000L, 38100000L, 50800000L);
        
        // 12700000 EMUs = 1000 points
        assertEquals("Large X value conversion", 1000.0, geometry.getXInPoints(), DELTA);
        // 25400000 EMUs = 2000 points
        assertEquals("Large Y value conversion", 2000.0, geometry.getYInPoints(), DELTA);
        // 38100000 EMUs = 3000 points
        assertEquals("Large width value conversion", 3000.0, geometry.getWidthInPoints(), DELTA);
        // 50800000 EMUs = 4000 points
        assertEquals("Large height value conversion", 4000.0, geometry.getHeightInPoints(), DELTA);
    }
    
    // ===== Edge Cases and Boundary Conditions =====
    
    @Test
    public void testZeroValues() {
        ShapeGeometry geometry = new ShapeGeometry(0L, 0L, 0L, 0L);
        
        assertEquals("Zero X should convert to zero points", 0.0, geometry.getXInPoints(), DELTA);
        assertEquals("Zero Y should convert to zero points", 0.0, geometry.getYInPoints(), DELTA);
        assertEquals("Zero width should convert to zero points", 0.0, geometry.getWidthInPoints(), DELTA);
        assertEquals("Zero height should convert to zero points", 0.0, geometry.getHeightInPoints(), DELTA);
    }
    
    @Test
    public void testNegativeValues() {
        // Negative coordinates can occur in PowerPoint (shapes positioned off-slide)
        ShapeGeometry geometry = new ShapeGeometry(-127000L, -254000L, 127000L, 254000L);
        
        assertEquals("Negative X should convert correctly", -10.0, geometry.getXInPoints(), DELTA);
        assertEquals("Negative Y should convert correctly", -20.0, geometry.getYInPoints(), DELTA);
        assertEquals("Positive width should convert correctly", 10.0, geometry.getWidthInPoints(), DELTA);
        assertEquals("Positive height should convert correctly", 20.0, geometry.getHeightInPoints(), DELTA);
    }
    
    @Test
    public void testMaximumLongValues() {
        // Test behavior with very large values (near Long.MAX_VALUE)
        long largeValue = Long.MAX_VALUE / 2; // Use half to avoid overflow in calculations
        ShapeGeometry geometry = new ShapeGeometry(largeValue, largeValue, largeValue, largeValue);
        
        double expectedPoints = largeValue / EMU_PER_POINT;
        assertEquals("Large X value conversion", expectedPoints, geometry.getXInPoints(), DELTA);
        assertEquals("Large Y value conversion", expectedPoints, geometry.getYInPoints(), DELTA);
        assertEquals("Large width value conversion", expectedPoints, geometry.getWidthInPoints(), DELTA);
        assertEquals("Large height value conversion", expectedPoints, geometry.getHeightInPoints(), DELTA);
    }
    
    // ===== Raw EMU Value Access Tests =====
    
    @Test
    public void testRawEmuValueAccess() {
        // Verify that raw EMU values are stored and retrieved exactly
        long testX = 1234567L;
        long testY = 2345678L;
        long testWidth = 3456789L;
        long testHeight = 4567890L;
        
        ShapeGeometry geometry = new ShapeGeometry(testX, testY, testWidth, testHeight);
        
        assertEquals("Raw X value should be exact", testX, geometry.getX());
        assertEquals("Raw Y value should be exact", testY, geometry.getY());
        assertEquals("Raw width value should be exact", testWidth, geometry.getWidth());
        assertEquals("Raw height value should be exact", testHeight, geometry.getHeight());
    }
    
    // ===== toString() Format and Precision Tests =====
    
    @Test
    public void testToStringFormat() {
        ShapeGeometry geometry = new ShapeGeometry(127000L, 254000L, 381000L, 508000L);
        String result = geometry.toString();
        
        // Should format as "Geometry{x=10.0pt, y=20.0pt, w=30.0pt, h=40.0pt}"
        assertTrue("toString should include 'Geometry{'", result.startsWith("Geometry{"));
        assertTrue("toString should include x in points", result.contains("x=10.0pt"));
        assertTrue("toString should include y in points", result.contains("y=20.0pt"));
        assertTrue("toString should include width in points", result.contains("w=30.0pt"));
        assertTrue("toString should include height in points", result.contains("h=40.0pt"));
        assertTrue("toString should end with '}'", result.endsWith("}"));
    }
    
    @Test
    public void testToStringPrecision() {
        // Test formatting with fractional values
        ShapeGeometry geometry = new ShapeGeometry(6350L, 19050L, 31750L, 44450L);
        String result = geometry.toString();
        
        // Should show one decimal place: 0.5, 1.5, 2.5, 3.5
        assertTrue("toString should format fractional x correctly", result.contains("x=0.5pt"));
        assertTrue("toString should format fractional y correctly", result.contains("y=1.5pt"));
        assertTrue("toString should format fractional width correctly", result.contains("w=2.5pt"));
        assertTrue("toString should format fractional height correctly", result.contains("h=3.5pt"));
    }
    
    @Test
    public void testToStringWithNegativeValues() {
        ShapeGeometry geometry = new ShapeGeometry(-127000L, -254000L, 381000L, 508000L);
        String result = geometry.toString();
        
        assertTrue("toString should handle negative x", result.contains("x=-10.0pt"));
        assertTrue("toString should handle negative y", result.contains("y=-20.0pt"));
        assertTrue("toString should handle positive width", result.contains("w=30.0pt"));
        assertTrue("toString should handle positive height", result.contains("h=40.0pt"));
    }
    
    @Test
    public void testToStringWithZeroValues() {
        ShapeGeometry geometry = new ShapeGeometry(0L, 0L, 0L, 0L);
        String result = geometry.toString();
        
        assertTrue("toString should handle zero x", result.contains("x=0.0pt"));
        assertTrue("toString should handle zero y", result.contains("y=0.0pt"));
        assertTrue("toString should handle zero width", result.contains("w=0.0pt"));
        assertTrue("toString should handle zero height", result.contains("h=0.0pt"));
    }
    
    // ===== Mathematical Consistency Tests =====
    
    @Test
    public void testConversionRoundTrip() {
        // Test that multiple conversions maintain precision
        ShapeGeometry geometry = new ShapeGeometry(127000L, 254000L, 381000L, 508000L);
        
        // Convert to points and back (conceptually)
        double xPoints = geometry.getXInPoints();
        long xEmuBack = Math.round(xPoints * EMU_PER_POINT);
        assertEquals("Round-trip conversion for X should be accurate", 127000L, xEmuBack);
        
        double yPoints = geometry.getYInPoints();
        long yEmuBack = Math.round(yPoints * EMU_PER_POINT);
        assertEquals("Round-trip conversion for Y should be accurate", 254000L, yEmuBack);
    }
    
    @Test
    public void testConversionConsistency() {
        // Test that the same EMU value always produces the same point value
        long testValue = 123456L;
        
        ShapeGeometry geo1 = new ShapeGeometry(testValue, 0L, 0L, 0L);
        ShapeGeometry geo2 = new ShapeGeometry(0L, testValue, 0L, 0L);
        ShapeGeometry geo3 = new ShapeGeometry(0L, 0L, testValue, 0L);
        ShapeGeometry geo4 = new ShapeGeometry(0L, 0L, 0L, testValue);
        
        double expectedPoints = testValue / EMU_PER_POINT;
        assertEquals("X conversion should be consistent", expectedPoints, geo1.getXInPoints(), DELTA);
        assertEquals("Y conversion should be consistent", expectedPoints, geo2.getYInPoints(), DELTA);
        assertEquals("Width conversion should be consistent", expectedPoints, geo3.getWidthInPoints(), DELTA);
        assertEquals("Height conversion should be consistent", expectedPoints, geo4.getHeightInPoints(), DELTA);
    }
    
    // ===== Real-world PowerPoint Scenario Tests =====
    
    @Test
    public void testTypicalPowerPointDimensions() {
        // Test with typical PowerPoint slide dimensions and shape sizes
        
        // Standard slide dimensions: 10" x 7.5" = 127000000 x 95250000 EMUs
        // Typical text box: 6" x 4" at position 2", 1.5" = 762000 x 508000 EMUs at 254000, 190500 EMUs
        ShapeGeometry slideGeometry = new ShapeGeometry(0L, 0L, 12700000L, 9525000L);
        ShapeGeometry textBoxGeometry = new ShapeGeometry(254000L, 190500L, 762000L, 508000L);
        
        // Slide dimensions in points
        assertEquals("Slide width should be 1000pt", 1000.0, slideGeometry.getWidthInPoints(), DELTA);
        assertEquals("Slide height should be 750pt", 750.0, slideGeometry.getHeightInPoints(), DELTA);
        
        // Text box position and size in points
        assertEquals("Text box X should be 20pt", 20.0, textBoxGeometry.getXInPoints(), DELTA);
        assertEquals("Text box Y should be 15pt", 15.0, textBoxGeometry.getYInPoints(), DELTA);
        assertEquals("Text box width should be 60pt", 60.0, textBoxGeometry.getWidthInPoints(), DELTA);
        assertEquals("Text box height should be 40pt", 40.0, textBoxGeometry.getHeightInPoints(), DELTA);
    }
    
    @Test
    public void testMinimalShapeDimensions() {
        // Test with very small shape dimensions (1 point each)
        ShapeGeometry geometry = new ShapeGeometry(127000L, 254000L, 12700L, 12700L);

        assertEquals("Small shape X position", 10.0, geometry.getXInPoints(), DELTA);
        assertEquals("Small shape Y position", 20.0, geometry.getYInPoints(), DELTA);
        assertEquals("Small shape width (1pt)", 1.0, geometry.getWidthInPoints(), DELTA);
        assertEquals("Small shape height (1pt)", 1.0, geometry.getHeightInPoints(), DELTA);
    }

    // ===== Flip (a:xfrm/@flipH, @flipV) Tests =====

    @Test
    public void testFlipDefaultsFalse() {
        // The pre-flip constructors must keep flip false so the 60+ existing
        // call sites are unaffected.
        assertFalse(new ShapeGeometry(1L, 2L, 3L, 4L).isFlipH());
        assertFalse(new ShapeGeometry(1L, 2L, 3L, 4L).isFlipV());
        assertFalse(new ShapeGeometry(1L, 2L, 3L, 4L, 90 * 60000).isFlipH());
        assertFalse(new ShapeGeometry(1L, 2L, 3L, 4L, 90 * 60000).isFlipV());
    }

    @Test
    public void testFlipStoredAndRetrieved() {
        ShapeGeometry hv = new ShapeGeometry(1L, 2L, 3L, 4L, 0, true, true);
        assertTrue("flipH stored", hv.isFlipH());
        assertTrue("flipV stored", hv.isFlipV());

        ShapeGeometry vOnly = new ShapeGeometry(1L, 2L, 3L, 4L, 0, false, true);
        assertFalse("flipH false", vOnly.isFlipH());
        assertTrue("flipV true", vOnly.isFlipV());
    }

    @Test
    public void testEqualsAndHashCodeDistinguishFlip() {
        ShapeGeometry plain = new ShapeGeometry(1L, 2L, 3L, 4L, 0, false, false);
        ShapeGeometry flipV = new ShapeGeometry(1L, 2L, 3L, 4L, 0, false, true);
        ShapeGeometry flipV2 = new ShapeGeometry(1L, 2L, 3L, 4L, 0, false, true);

        assertNotEquals("flip difference must break equality", plain, flipV);
        assertEquals("same flip must be equal", flipV, flipV2);
        assertEquals("equal objects share hashCode", flipV.hashCode(), flipV2.hashCode());

        // The no-flip constructor must equal the explicit all-false form, so
        // existing geometry comparisons don't shift.
        assertEquals(new ShapeGeometry(1L, 2L, 3L, 4L), plain);
    }

    @Test
    public void testToStringShowsFlipOnlyWhenSet() {
        assertFalse("unflipped toString omits flip",
            new ShapeGeometry(0L, 0L, 0L, 0L).toString().contains("flip"));
        String flipped = new ShapeGeometry(0L, 0L, 0L, 0L, 0, true, false).toString();
        assertTrue("flipped toString surfaces flipH", flipped.contains("flipH=true"));
    }
}