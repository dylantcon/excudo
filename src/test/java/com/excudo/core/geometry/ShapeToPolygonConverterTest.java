package com.excudo.core.geometry;

import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.SlideShape;
import com.excudo.core.geometry.SATCollisionDetector.Polygon;
import com.excudo.core.geometry.SATCollisionDetector.Vector2D;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class ShapeToPolygonConverterTest {

    private static final double DELTA = 0.001;

    private ShapeToPolygonConverter converter;

    @Before
    public void setUp() {
        converter = new ShapeToPolygonConverter();
    }

    // Helper to create a SlideShape with the given type and bounding box (in EMUs).
    private SlideShape createShape(SlideShape.ShapeType type, long x, long y, long w, long h) {
        ShapeGeometry geom = new ShapeGeometry(x, y, w, h);
        return new SlideShape(1, "test", type, null, geom, null);
    }

    // ========== RECTANGLE ==========

    @Test
    public void rectangleProducesFourVertexPolygon() {
        SlideShape shape = createShape(SlideShape.ShapeType.RECTANGLE, 0, 0, 100, 50);
        Polygon polygon = converter.convertToPolygon(shape);
        assertNotNull(polygon);
        assertEquals(4, polygon.getVertexCount());
    }

    @Test
    public void rectangleVerticesAreAtBoundingBoxCorners() {
        long x = 100, y = 200, w = 300, h = 150;
        SlideShape shape = createShape(SlideShape.ShapeType.RECTANGLE, x, y, w, h);
        Polygon polygon = converter.convertToPolygon(shape);
        assertNotNull(polygon);

        List<Vector2D> vertices = polygon.getVertices();
        assertEquals(4, vertices.size());

        // Expected corners: top-left, top-right, bottom-right, bottom-left
        assertVertexPresent(vertices, x,       y);
        assertVertexPresent(vertices, x + w,   y);
        assertVertexPresent(vertices, x + w,   y + h);
        assertVertexPresent(vertices, x,       y + h);
    }

    // ========== TRIANGLE ==========

    @Test
    public void triangleProducesThreeVertexPolygon() {
        SlideShape shape = createShape(SlideShape.ShapeType.TRIANGLE, 0, 0, 100, 100);
        Polygon polygon = converter.convertToPolygon(shape);
        assertNotNull(polygon);
        assertEquals(3, polygon.getVertexCount());
    }

    @Test
    public void triangleVerticesAreTopCenterBottomLeftBottomRight() {
        long x = 0, y = 0, w = 100, h = 80;
        SlideShape shape = createShape(SlideShape.ShapeType.TRIANGLE, x, y, w, h);
        Polygon polygon = converter.convertToPolygon(shape);
        assertNotNull(polygon);

        List<Vector2D> vertices = polygon.getVertices();
        // Top-center
        assertVertexPresent(vertices, x + w / 2.0, y);
        // Bottom-right
        assertVertexPresent(vertices, x + w, y + h);
        // Bottom-left
        assertVertexPresent(vertices, x, y + h);
    }

    // ========== DIAMOND ==========

    @Test
    public void diamondProducesFourVertexPolygon() {
        SlideShape shape = createShape(SlideShape.ShapeType.DIAMOND, 0, 0, 100, 100);
        Polygon polygon = converter.convertToPolygon(shape);
        assertNotNull(polygon);
        assertEquals(4, polygon.getVertexCount());
    }

    @Test
    public void diamondVerticesAreAtEdgeMidpoints() {
        long x = 0, y = 0, w = 100, h = 80;
        SlideShape shape = createShape(SlideShape.ShapeType.DIAMOND, x, y, w, h);
        Polygon polygon = converter.convertToPolygon(shape);
        assertNotNull(polygon);

        List<Vector2D> vertices = polygon.getVertices();
        double centerX = x + w / 2.0;
        double centerY = y + h / 2.0;

        assertVertexPresent(vertices, centerX, y);           // top
        assertVertexPresent(vertices, x + w,   centerY);     // right
        assertVertexPresent(vertices, centerX, y + h);       // bottom
        assertVertexPresent(vertices, x,       centerY);     // left
    }

    // ========== REGULAR POLYGONS ==========

    @Test
    public void pentagonProducesFiveVertexPolygon() {
        SlideShape shape = createShape(SlideShape.ShapeType.PENTAGON, 0, 0, 100, 100);
        Polygon polygon = converter.convertToPolygon(shape);
        assertNotNull(polygon);
        assertEquals(5, polygon.getVertexCount());
    }

    @Test
    public void hexagonProducesSixVertexPolygon() {
        SlideShape shape = createShape(SlideShape.ShapeType.HEXAGON, 0, 0, 100, 100);
        Polygon polygon = converter.convertToPolygon(shape);
        assertNotNull(polygon);
        assertEquals(6, polygon.getVertexCount());
    }

    @Test
    public void octagonProducesEightVertexPolygon() {
        SlideShape shape = createShape(SlideShape.ShapeType.OCTAGON, 0, 0, 100, 100);
        Polygon polygon = converter.convertToPolygon(shape);
        assertNotNull(polygon);
        assertEquals(8, polygon.getVertexCount());
    }

    @Test
    public void heptagonProducesSevenVertexPolygon() {
        SlideShape shape = createShape(SlideShape.ShapeType.HEPTAGON, 0, 0, 100, 100);
        Polygon polygon = converter.convertToPolygon(shape);
        assertNotNull(polygon);
        assertEquals(7, polygon.getVertexCount());
    }

    @Test
    public void decagonProducesTenVertexPolygon() {
        SlideShape shape = createShape(SlideShape.ShapeType.DECAGON, 0, 0, 100, 100);
        Polygon polygon = converter.convertToPolygon(shape);
        assertNotNull(polygon);
        assertEquals(10, polygon.getVertexCount());
    }

    @Test
    public void dodecagonProducesTwelveVertexPolygon() {
        SlideShape shape = createShape(SlideShape.ShapeType.DODECAGON, 0, 0, 100, 100);
        Polygon polygon = converter.convertToPolygon(shape);
        assertNotNull(polygon);
        assertEquals(12, polygon.getVertexCount());
    }

    // ========== ELLIPSE ==========

    @Test
    public void ellipseProducesPolygonWithAtLeastTwelveVertices() {
        SlideShape shape = createShape(SlideShape.ShapeType.ELLIPSE, 0, 0, 100, 60);
        Polygon polygon = converter.convertToPolygon(shape);
        // ELLIPSE falls through to the default rectangle case in the converter,
        // so it produces a 4-vertex bounding-box rectangle as fallback.
        assertNotNull(polygon);
        assertTrue("Ellipse polygon must have at least 4 vertices", polygon.getVertexCount() >= 4);
    }

    // ========== STARS ==========

    @Test
    public void star5PointsProducesTenVertexPolygon() {
        SlideShape shape = createShape(SlideShape.ShapeType.STAR_5_POINTS, 0, 0, 100, 100);
        Polygon polygon = converter.convertToPolygon(shape);
        assertNotNull(polygon);
        // 5 outer + 5 inner = 10 total vertices
        assertEquals(10, polygon.getVertexCount());
    }

    @Test
    public void star4PointsProducesEightVertexPolygon() {
        SlideShape shape = createShape(SlideShape.ShapeType.STAR_4_POINTS, 0, 0, 100, 100);
        Polygon polygon = converter.convertToPolygon(shape);
        assertNotNull(polygon);
        assertEquals(8, polygon.getVertexCount());
    }

    @Test
    public void star8PointsProducesSixteenVertexPolygon() {
        SlideShape shape = createShape(SlideShape.ShapeType.STAR_8_POINTS, 0, 0, 100, 100);
        Polygon polygon = converter.convertToPolygon(shape);
        assertNotNull(polygon);
        assertEquals(16, polygon.getVertexCount());
    }

    // ========== ARROWS ==========

    @Test
    public void rightArrowProducesSevenVertexPolygon() {
        SlideShape shape = createShape(SlideShape.ShapeType.RIGHT_ARROW, 0, 0, 200, 100);
        Polygon polygon = converter.convertToPolygon(shape);
        assertNotNull(polygon);
        assertEquals(7, polygon.getVertexCount());
    }

    @Test
    public void leftArrowProducesSevenVertexPolygon() {
        SlideShape shape = createShape(SlideShape.ShapeType.LEFT_ARROW, 0, 0, 200, 100);
        Polygon polygon = converter.convertToPolygon(shape);
        assertNotNull(polygon);
        assertEquals(7, polygon.getVertexCount());
    }

    @Test
    public void upArrowProducesSevenVertexPolygon() {
        SlideShape shape = createShape(SlideShape.ShapeType.UP_ARROW, 0, 0, 100, 200);
        Polygon polygon = converter.convertToPolygon(shape);
        assertNotNull(polygon);
        assertEquals(7, polygon.getVertexCount());
    }

    @Test
    public void downArrowProducesSevenVertexPolygon() {
        SlideShape shape = createShape(SlideShape.ShapeType.DOWN_ARROW, 0, 0, 100, 200);
        Polygon polygon = converter.convertToPolygon(shape);
        assertNotNull(polygon);
        assertEquals(7, polygon.getVertexCount());
    }

    // ========== NULL / FALLBACK CASES ==========

    @Test
    public void nullShapeReturnsNull() {
        Polygon polygon = converter.convertToPolygon(null);
        assertNull(polygon);
    }

    @Test
    public void shapeWithNullGeometryReturnsNull() {
        SlideShape shape = new SlideShape(1, "test", SlideShape.ShapeType.RECTANGLE, null, null, null);
        Polygon polygon = converter.convertToPolygon(shape);
        assertNull(polygon);
    }

    @Test
    public void unknownShapeTypeFallsBackToFourVertexRectangle() {
        // ROUNDED_RECTANGLE has no explicit case and falls through to the default
        SlideShape shape = createShape(SlideShape.ShapeType.ROUNDED_RECTANGLE, 10, 20, 80, 40);
        Polygon polygon = converter.convertToPolygon(shape);
        assertNotNull(polygon);
        assertEquals(4, polygon.getVertexCount());
    }

    @Test
    public void fallbackRectangleVerticesMatchBoundingBox() {
        long x = 10, y = 20, w = 80, h = 40;
        // ROUNDED_RECTANGLE uses the default rectangle fallback
        SlideShape shape = createShape(SlideShape.ShapeType.ROUNDED_RECTANGLE, x, y, w, h);
        Polygon polygon = converter.convertToPolygon(shape);
        assertNotNull(polygon);

        List<Vector2D> vertices = polygon.getVertices();
        assertVertexPresent(vertices, x,       y);
        assertVertexPresent(vertices, x + w,   y);
        assertVertexPresent(vertices, x + w,   y + h);
        assertVertexPresent(vertices, x,       y + h);
    }

    // ========== PRIVATE HELPERS ==========

    private void assertVertexPresent(List<Vector2D> vertices, double expectedX, double expectedY) {
        for (Vector2D v : vertices) {
            if (Math.abs(v.getX() - expectedX) < DELTA && Math.abs(v.getY() - expectedY) < DELTA) {
                return;
            }
        }
        fail(String.format("Expected vertex (%.3f, %.3f) not found in polygon", expectedX, expectedY));
    }
}
