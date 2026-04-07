package com.excudo.core.geometry;

import com.excudo.core.geometry.SATCollisionDetector.BoundingBox;
import com.excudo.core.geometry.SATCollisionDetector.Polygon;
import com.excudo.core.geometry.SATCollisionDetector.Vector2D;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.SlideShape;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Arrays;
import java.util.List;

/**
 * Unit tests for SATCollisionDetector and its public inner geometry types.
 *
 * Tests cover Vector2D arithmetic, Polygon construction and accessors,
 * BoundingBox accessors, and the collision detection methods on SlideShape pairs.
 */
public class SATCollisionDetectorTest {

    private static final double DELTA = 0.001;

    private SATCollisionDetector detector;

    @Before
    public void setUp() {
        detector = new SATCollisionDetector();
    }

    // ========== Vector2D ==========

    @Test
    public void vector2D_constructor_storesXAndY() {
        Vector2D v = new Vector2D(3.0, 7.5);
        assertEquals(3.0, v.getX(), DELTA);
        assertEquals(7.5, v.getY(), DELTA);
    }

    @Test
    public void vector2D_add_returnsSumVector() {
        Vector2D a = new Vector2D(1.0, 2.0);
        Vector2D b = new Vector2D(3.0, 4.0);
        Vector2D result = a.add(b);
        assertEquals(4.0, result.getX(), DELTA);
        assertEquals(6.0, result.getY(), DELTA);
    }

    @Test
    public void vector2D_subtract_returnsDifferenceVector() {
        Vector2D a = new Vector2D(5.0, 7.0);
        Vector2D b = new Vector2D(2.0, 3.0);
        Vector2D result = a.subtract(b);
        assertEquals(3.0, result.getX(), DELTA);
        assertEquals(4.0, result.getY(), DELTA);
    }

    @Test
    public void vector2D_multiply_scalesComponents() {
        Vector2D v = new Vector2D(2.0, 3.0);
        Vector2D result = v.multiply(4.0);
        assertEquals(8.0, result.getX(), DELTA);
        assertEquals(12.0, result.getY(), DELTA);
    }

    @Test
    public void vector2D_dot_perpendicularVectors_returnsZero() {
        Vector2D a = new Vector2D(1.0, 0.0);
        Vector2D b = new Vector2D(0.0, 1.0);
        assertEquals(0.0, a.dot(b), DELTA);
    }

    @Test
    public void vector2D_dot_parallelVectors_returnsCorrectValue() {
        Vector2D a = new Vector2D(1.0, 1.0);
        Vector2D b = new Vector2D(1.0, 1.0);
        assertEquals(2.0, a.dot(b), DELTA);
    }

    @Test
    public void vector2D_magnitude_returnsCorrectLength() {
        Vector2D v = new Vector2D(3.0, 4.0);
        assertEquals(5.0, v.magnitude(), DELTA);
    }

    @Test
    public void vector2D_normalize_producesUnitVector() {
        Vector2D v = new Vector2D(3.0, 4.0);
        Vector2D unit = v.normalize();
        assertEquals(1.0, unit.magnitude(), DELTA);
    }

    @Test
    public void vector2D_normalize_zeroVector_handledGracefully() {
        Vector2D zero = new Vector2D(0.0, 0.0);
        Vector2D result = zero.normalize();
        // Must not throw; result components should be finite
        assertNotNull(result);
        assertFalse(Double.isNaN(result.getX()));
        assertFalse(Double.isNaN(result.getY()));
    }

    // ========== Polygon ==========

    @Test
    public void polygon_constructor_storesCorrectVertexCount() {
        List<Vector2D> vertices = Arrays.asList(
                new Vector2D(0, 0),
                new Vector2D(10, 0),
                new Vector2D(10, 10),
                new Vector2D(0, 10)
        );
        Polygon polygon = new Polygon(vertices);
        assertEquals(4, polygon.getVertexCount());
    }

    @Test
    public void polygon_getVertices_returnsUnmodifiableList() {
        List<Vector2D> vertices = Arrays.asList(
                new Vector2D(0, 0),
                new Vector2D(5, 0),
                new Vector2D(5, 5)
        );
        Polygon polygon = new Polygon(vertices);
        List<Vector2D> returned = polygon.getVertices();

        try {
            returned.add(new Vector2D(99, 99));
            fail("Expected UnsupportedOperationException from unmodifiable list");
        } catch (UnsupportedOperationException expected) {
            // pass
        }
    }

    @Test
    public void polygon_getCentroid_unitSquare_returnsMidpoint() {
        // Unit square with corners at (0,0), (2,0), (2,2), (0,2)
        List<Vector2D> vertices = Arrays.asList(
                new Vector2D(0, 0),
                new Vector2D(2, 0),
                new Vector2D(2, 2),
                new Vector2D(0, 2)
        );
        Polygon polygon = new Polygon(vertices);
        Vector2D centroid = polygon.getCentroid();
        assertEquals(1.0, centroid.getX(), DELTA);
        assertEquals(1.0, centroid.getY(), DELTA);
    }

    @Test
    public void polygon_getBoundingBox_returnsCorrectBounds() {
        List<Vector2D> vertices = Arrays.asList(
                new Vector2D(1, 2),
                new Vector2D(5, 2),
                new Vector2D(5, 8),
                new Vector2D(1, 8)
        );
        Polygon polygon = new Polygon(vertices);
        BoundingBox box = polygon.getBoundingBox();

        assertEquals(1.0, box.getX(),      DELTA);
        assertEquals(2.0, box.getY(),      DELTA);
        assertEquals(4.0, box.getWidth(),  DELTA);
        assertEquals(6.0, box.getHeight(), DELTA);
        assertEquals(5.0, box.getRight(),  DELTA); // x + width = 1 + 4 = 5
        assertEquals(8.0, box.getBottom(), DELTA); // y + height = 2 + 6 = 8
    }

    // ========== Collision detection (SlideShape with RECTANGLE geometry) ==========
    // RECTANGLE does not requiresSATCollision(), so the bounding box path is exercised.

    private SlideShape makeRect(int spid, long x, long y, long width, long height) {
        ShapeGeometry geometry = new ShapeGeometry(x, y, width, height);
        return new SlideShape(spid, "shape-" + spid,
                SlideShape.ShapeType.RECTANGLE, null, geometry, null);
    }

    @Test
    public void areShapesColliding_overlappingRectangles_returnsTrue() {
        // shape1: (0,0) 100x100, shape2: (50,50) 100x100 -- clearly overlapping
        SlideShape shape1 = makeRect(1, 0L,  0L,  100L, 100L);
        SlideShape shape2 = makeRect(2, 50L, 50L, 100L, 100L);
        assertTrue(detector.areShapesColliding(shape1, shape2));
    }

    @Test
    public void areShapesColliding_separatedRectangles_returnsFalse() {
        // shape1: (0,0) 50x50, shape2: (200,200) 50x50 -- no overlap
        SlideShape shape1 = makeRect(1, 0L,   0L,   50L, 50L);
        SlideShape shape2 = makeRect(2, 200L, 200L, 50L, 50L);
        assertFalse(detector.areShapesColliding(shape1, shape2));
    }

    @Test
    public void isShapeCollidingWithRegion_overlapping_returnsTrue() {
        // shape at (10,10) 100x100, region at (50,50) 200x200 -- overlapping
        SlideShape shape = makeRect(1, 10L, 10L, 100L, 100L);
        assertTrue(detector.isShapeCollidingWithRegion(shape, 50L, 50L, 200L, 200L));
    }

    @Test
    public void isShapeCollidingWithRegion_separated_returnsFalse() {
        // shape at (0,0) 30x30, region at (500,500) 100x100 -- no overlap
        SlideShape shape = makeRect(1, 0L, 0L, 30L, 30L);
        assertFalse(detector.isShapeCollidingWithRegion(shape, 500L, 500L, 100L, 100L));
    }
}
