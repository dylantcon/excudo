package com.excudo.core.geometry;

import com.excudo.core.geometry.SATCollisionDetector.Polygon;
import com.excudo.core.geometry.SATCollisionDetector.Vector2D;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.SlideShape;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Rigorous tests for the polygon foundation primitives.
 *
 * <p>The agent overlap warning depends on these primitives reading
 * within tight tolerance of analytic ground truth -- a 25%-overlap
 * threshold is meaningless if the underlying area function is off by
 * 5% on a rotated rectangle.
 *
 * <p>Each test case uses a closed-form ground truth where one exists
 * (rectangle = w*h, regular n-gon = (1/2)nR^2 sin(2pi/n), ellipse =
 * pi*a*b) and asserts the polygon-derived value matches within a
 * sampling-density-bound tolerance.
 */
public class PolygonAreaAndRotationTest {

    private final ShapeToPolygonConverter converter = new ShapeToPolygonConverter();
    private static final double TIGHT_TOLERANCE = 1e-6;
    /**
     * Inherent inscribed-polygon-vs-circle area error at 32 samples is
     * 1 - sin(2*pi/n)/(2*pi/n) ~= 0.64%. Bump the tolerance to 0.7% to
     * sit a hair above that ceiling without masking real regressions.
     * If the converter ever increases sample count to 64 (error ~0.16%),
     * tighten this proportionally.
     */
    private static final double ELLIPSE_TOLERANCE = 0.007;

    // ========== Rectangle ==========

    @Test
    public void rectangleAreaMatchesWidthTimesHeight() {
        SlideShape shape = newShape(SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(0, 0, 1000, 500));
        Polygon p = converter.convertToPolygon(shape);
        assertEquals(1000.0 * 500.0, p.area(), TIGHT_TOLERANCE);
    }

    @Test
    public void rotatedRectangleAreaIsInvariant() {
        // 30 degrees in OOXML units (60000 per degree).
        ShapeGeometry geo = new ShapeGeometry(0, 0, 1000, 500, 30 * 60000);
        SlideShape shape = newShape(SlideShape.ShapeType.RECTANGLE, geo);
        Polygon p = converter.convertToPolygon(shape);
        // Area is invariant under rotation; tolerate floating-point drift only.
        assertEquals("Rotated rectangle area must equal w*h",
            1000.0 * 500.0, p.area(), 1e-3);
    }

    @Test
    public void rotationActuallyMovesVertices() {
        // 90 degrees: every (dx, dy) becomes (-dy, dx) around the center.
        ShapeGeometry geo = new ShapeGeometry(0, 0, 1000, 500, 90 * 60000);
        SlideShape shape = newShape(SlideShape.ShapeType.RECTANGLE, geo);
        Polygon p = converter.convertToPolygon(shape);
        // Original rectangle vertices: (0,0), (1000,0), (1000,500), (0,500).
        // Center: (500, 250). After 90deg rotation around center:
        //   (0,0)    -> (250 + 250, 250 - 500) = (500, -250)... no wait.
        // Standard math rotation: (dx, dy) -> (dx*cos - dy*sin, dx*sin + dy*cos).
        // 90deg: (dx, dy) -> (-dy, dx).
        //   (0,0):     dx=-500, dy=-250 -> (250, -500) + (500,250) = (750, -250)
        //   (1000,0):  dx=500,  dy=-250 -> (250, 500)  + (500,250) = (750, 750)
        //   (1000,500):dx=500,  dy=250  -> (-250, 500) + (500,250) = (250, 750)
        //   (0,500):   dx=-500, dy=250  -> (-250,-500) + (500,250) = (250, -250)
        List<Vector2D> v = p.getVertices();
        assertEquals(4, v.size());
        assertEquals(750.0, v.get(0).getX(), 1e-6);
        assertEquals(-250.0, v.get(0).getY(), 1e-6);
        assertEquals(750.0, v.get(1).getX(), 1e-6);
        assertEquals(750.0, v.get(1).getY(), 1e-6);
    }

    // ========== Regular polygons ==========

    @Test
    public void hexagonAreaMatchesAnalytic() {
        // Regular n-gon inscribed in radius R: area = (1/2) n R^2 sin(2pi/n).
        // With width=height=1000, the polygon is inscribed in a 500-radius
        // circle (the converter uses width/2 and height/2 as radii).
        ShapeGeometry geo = new ShapeGeometry(0, 0, 1000, 1000);
        SlideShape shape = newShape(SlideShape.ShapeType.HEXAGON, geo);
        Polygon p = converter.convertToPolygon(shape);
        double R = 500.0;
        int n = 6;
        double expected = 0.5 * n * R * R * Math.sin(2 * Math.PI / n);
        assertEquals(expected, p.area(), 1e-6);
    }

    @Test
    public void pentagonAreaMatchesAnalytic() {
        ShapeGeometry geo = new ShapeGeometry(0, 0, 1000, 1000);
        SlideShape shape = newShape(SlideShape.ShapeType.PENTAGON, geo);
        Polygon p = converter.convertToPolygon(shape);
        double R = 500.0;
        double expected = 0.5 * 5 * R * R * Math.sin(2 * Math.PI / 5);
        assertEquals(expected, p.area(), 1e-6);
    }

    // ========== Ellipse ==========

    @Test
    public void circleAreaApproximatesPiRSquared() {
        // Ellipse with equal axes is a circle. Area = pi * r^2.
        // 32-point inscribed polygon underestimates by sin(2pi/n)/(2pi/n) factor;
        // for n=32 this is roughly 0.5%.
        ShapeGeometry geo = new ShapeGeometry(0, 0, 1000, 1000);
        SlideShape shape = newShape(SlideShape.ShapeType.ELLIPSE, geo);
        Polygon p = converter.convertToPolygon(shape);
        double r = 500.0;
        double expected = Math.PI * r * r;
        double actual = p.area();
        double relError = Math.abs(actual - expected) / expected;
        assertTrue("Circle area within " + (ELLIPSE_TOLERANCE * 100)
            + "% of pi*r^2; got " + relError * 100 + "% error",
            relError < ELLIPSE_TOLERANCE);
    }

    @Test
    public void ellipseAreaApproximatesPiAB() {
        // Non-circular ellipse: pi * a * b.
        ShapeGeometry geo = new ShapeGeometry(0, 0, 2000, 1000); // a=1000, b=500
        SlideShape shape = newShape(SlideShape.ShapeType.ELLIPSE, geo);
        Polygon p = converter.convertToPolygon(shape);
        double expected = Math.PI * 1000.0 * 500.0;
        double relError = Math.abs(p.area() - expected) / expected;
        assertTrue("Ellipse area within tolerance; got " + relError * 100 + "% error",
            relError < ELLIPSE_TOLERANCE);
    }

    @Test
    public void ellipseSamplingProducesClosedPolygon() {
        ShapeGeometry geo = new ShapeGeometry(0, 0, 1000, 600);
        SlideShape shape = newShape(SlideShape.ShapeType.ELLIPSE, geo);
        Polygon p = converter.convertToPolygon(shape);
        assertTrue("Ellipse polygon should have at least 16 vertices for low-error sampling",
            p.getVertexCount() >= 16);
    }

    @Test
    public void rotatedEllipseAreaIsInvariant() {
        ShapeGeometry geo = new ShapeGeometry(0, 0, 2000, 1000, 45 * 60000);
        SlideShape shape = newShape(SlideShape.ShapeType.ELLIPSE, geo);
        Polygon p = converter.convertToPolygon(shape);
        double expected = Math.PI * 1000.0 * 500.0;
        double relError = Math.abs(p.area() - expected) / expected;
        assertTrue("Rotated ellipse area invariance; got " + relError * 100 + "% error",
            relError < ELLIPSE_TOLERANCE);
    }

    // ========== Star (concave) ==========

    @Test
    public void fivePointStarAreaIsPositiveAndSubsetOfBoundingCircle() {
        // Star area math is rich (depends on inner/outer radius ratio).
        // We assert the floor case: area > 0, area < area of circumscribed
        // ellipse (pi*a*b). For width=height=1000 and inner ratio 0.4,
        // the analytic 5-point star area is roughly 0.4 * pi * R^2.
        ShapeGeometry geo = new ShapeGeometry(0, 0, 1000, 1000);
        SlideShape shape = newShape(SlideShape.ShapeType.STAR_5_POINTS, geo);
        Polygon p = converter.convertToPolygon(shape);
        double R = 500.0;
        double circleArea = Math.PI * R * R;
        double area = p.area();
        assertTrue("Star area positive", area > 0);
        assertTrue("Star area below circumscribed circle", area < circleArea);
        // Sanity: the star's inner-radius=0.4*outer construction puts area
        // somewhere between 30% and 70% of the bounding circle.
        assertTrue("Star area in expected range, got " + (area / circleArea),
            area / circleArea > 0.30 && area / circleArea < 0.70);
    }

    // ========== Helper ==========

    private SlideShape newShape(SlideShape.ShapeType type, ShapeGeometry geo) {
        return new SlideShape(1, "Test", type, "", geo, null);
    }
}
