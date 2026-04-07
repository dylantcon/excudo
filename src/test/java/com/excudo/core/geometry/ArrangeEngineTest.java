package com.excudo.core.geometry;

import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.SlideShape;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Arrays;
import java.util.List;

public class ArrangeEngineTest {

    private SlideShape shape(int spid, long x, long y, long w, long h) {
        return new SlideShape(spid, "Shape" + spid, SlideShape.ShapeType.RECTANGLE, null,
            new ShapeGeometry(x, y, w, h), null);
    }

    // ===== ALIGN LEFT =====
    @Test
    public void testAlignLeft() {
        List<SlideShape> shapes = Arrays.asList(
            shape(2, 200, 100, 100, 100),
            shape(3, 500, 200, 100, 100),
            shape(4, 300, 300, 100, 100)
        );
        List<ArrangeEngine.ArrangeResult> results = ArrangeEngine.alignLeft(shapes, null);
        // min x = 200, shapes 3 and 4 should move
        for (ArrangeEngine.ArrangeResult r : results) {
            assertEquals(200, r.updated().getX());
        }
    }

    @Test
    public void testAlignLeftWithAnchor() {
        List<SlideShape> shapes = Arrays.asList(
            shape(2, 200, 100, 100, 100),
            shape(3, 500, 200, 100, 100),
            shape(4, 300, 300, 100, 100)
        );
        List<ArrangeEngine.ArrangeResult> results = ArrangeEngine.alignLeft(shapes, 3);
        // anchor spid 3 has x=500
        for (ArrangeEngine.ArrangeResult r : results) {
            assertEquals(500, r.updated().getX());
        }
    }

    // ===== ALIGN RIGHT =====
    @Test
    public void testAlignRight() {
        List<SlideShape> shapes = Arrays.asList(
            shape(2, 200, 100, 100, 100),  // right edge = 300
            shape(3, 500, 200, 150, 100),  // right edge = 650
            shape(4, 300, 300, 200, 100)   // right edge = 500
        );
        List<ArrangeEngine.ArrangeResult> results = ArrangeEngine.alignRight(shapes, null);
        // max right edge = 650
        for (ArrangeEngine.ArrangeResult r : results) {
            assertEquals(650, r.updated().getX() + r.updated().getWidth());
        }
    }

    // ===== ALIGN TOP =====
    @Test
    public void testAlignTop() {
        List<SlideShape> shapes = Arrays.asList(
            shape(2, 100, 300, 100, 100),
            shape(3, 200, 100, 100, 100),
            shape(4, 300, 500, 100, 100)
        );
        List<ArrangeEngine.ArrangeResult> results = ArrangeEngine.alignTop(shapes, null);
        for (ArrangeEngine.ArrangeResult r : results) {
            assertEquals(100, r.updated().getY());
        }
    }

    // ===== ALIGN BOTTOM =====
    @Test
    public void testAlignBottom() {
        List<SlideShape> shapes = Arrays.asList(
            shape(2, 100, 100, 100, 200),  // bottom = 300
            shape(3, 200, 200, 100, 400),  // bottom = 600
            shape(4, 300, 300, 100, 100)   // bottom = 400
        );
        List<ArrangeEngine.ArrangeResult> results = ArrangeEngine.alignBottom(shapes, null);
        for (ArrangeEngine.ArrangeResult r : results) {
            assertEquals(600, r.updated().getY() + r.updated().getHeight());
        }
    }

    // ===== CENTER =====
    @Test
    public void testAlignCenterH() {
        List<SlideShape> shapes = Arrays.asList(
            shape(2, 100, 100, 200, 100),  // center = 200
            shape(3, 400, 200, 100, 100),  // center = 450
            shape(4, 250, 300, 300, 100)   // center = 400
        );
        List<ArrangeEngine.ArrangeResult> results = ArrangeEngine.alignCenterH(shapes, null);
        // avg center = (200 + 450 + 400) / 3 = 350
        for (ArrangeEngine.ArrangeResult r : results) {
            long center = r.updated().getX() + r.updated().getWidth() / 2;
            assertEquals(350, center);
        }
    }

    // ===== DISTRIBUTE H =====
    @Test
    public void testDistributeH() {
        List<SlideShape> shapes = Arrays.asList(
            shape(2, 0, 0, 100, 100),
            shape(3, 100, 0, 100, 100),
            shape(4, 800, 0, 100, 100)
        );
        List<ArrangeEngine.ArrangeResult> results = ArrangeEngine.distributeH(shapes);
        // First (x=0) and last (x=800) stay. Interior shapes evenly spaced.
        // Total span = 800, total interior width = 100 (shape 3)
        // gap = (800 - 0 - 100 - 100) / 2 = 300
        // shape 3 should be at x = 0 + 100 + 300 = 400
        boolean foundShape3 = false;
        for (ArrangeEngine.ArrangeResult r : results) {
            if (r.spid() == 3) {
                assertEquals(400, r.updated().getX());
                foundShape3 = true;
            }
        }
        assertTrue("Shape 3 should be in results", foundShape3);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDistributeHTooFewShapes() {
        List<SlideShape> shapes = Arrays.asList(
            shape(2, 0, 0, 100, 100),
            shape(3, 500, 0, 100, 100)
        );
        ArrangeEngine.distributeH(shapes);
    }

    // ===== MATCH =====
    @Test
    public void testMatchWidth() {
        List<SlideShape> shapes = Arrays.asList(
            shape(2, 0, 0, 300, 100),
            shape(3, 100, 0, 100, 100),
            shape(4, 200, 0, 200, 100)
        );
        List<ArrangeEngine.ArrangeResult> results = ArrangeEngine.matchWidth(shapes, 2);
        for (ArrangeEngine.ArrangeResult r : results) {
            assertEquals(300, r.updated().getWidth());
        }
    }

    @Test
    public void testMatchSize() {
        List<SlideShape> shapes = Arrays.asList(
            shape(2, 0, 0, 300, 200),
            shape(3, 100, 0, 100, 100)
        );
        List<ArrangeEngine.ArrangeResult> results = ArrangeEngine.matchSize(shapes, 2);
        for (ArrangeEngine.ArrangeResult r : results) {
            assertEquals(300, r.updated().getWidth());
            assertEquals(200, r.updated().getHeight());
        }
    }

    // ===== CENTER ON SLIDE =====
    @Test
    public void testCenterOnSlide() {
        List<SlideShape> shapes = Arrays.asList(
            shape(2, 0, 0, 1000, 500)
        );
        List<ArrangeEngine.ArrangeResult> results = ArrangeEngine.centerOnSlide(shapes);
        assertEquals(1, results.size());
        assertEquals((12192000L - 1000) / 2, results.get(0).updated().getX());
        assertEquals((6858000L - 500) / 2, results.get(0).updated().getY());
    }

    // ===== SNAP TO GRID =====
    @Test
    public void testSnapToGrid() {
        List<SlideShape> shapes = Arrays.asList(
            shape(2, 500000, 1200000, 100, 100)
        );
        List<ArrangeEngine.ArrangeResult> results = ArrangeEngine.snapToGrid(shapes);
        assertEquals(1, results.size());
        assertEquals(914400L, results.get(0).updated().getX());   // rounds to nearest 914400
        assertEquals(914400L, results.get(0).updated().getY());   // rounds to nearest 914400
    }

    // ===== DISPATCHER =====
    @Test
    public void testExecuteDispatcher() {
        List<SlideShape> shapes = Arrays.asList(
            shape(2, 200, 100, 100, 100),
            shape(3, 500, 200, 100, 100)
        );
        List<ArrangeEngine.ArrangeResult> results = ArrangeEngine.execute("align-left", shapes, null);
        assertNotNull(results);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testExecuteUnknownOperation() {
        List<SlideShape> shapes = Arrays.asList(shape(2, 0, 0, 100, 100));
        ArrangeEngine.execute("unknown-op", shapes, null);
    }

    // ===== NO-CHANGE FILTERING =====
    @Test
    public void testNoChangeFiltered() {
        // All shapes already aligned left at x=100
        List<SlideShape> shapes = Arrays.asList(
            shape(2, 100, 0, 100, 100),
            shape(3, 100, 100, 200, 100)
        );
        List<ArrangeEngine.ArrangeResult> results = ArrangeEngine.alignLeft(shapes, null);
        assertEquals(0, results.size()); // no shapes changed
    }
}
