package com.excudo.core.geometry;

import com.excudo.core.geometry.SATCollisionDetector.Polygon;
import com.excudo.core.geometry.SATCollisionDetector.Vector2D;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.SlideShape;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Rigorous correctness tests for PolygonClipping. Two ground-truth
 * sources are used in tandem:
 *
 * <ol>
 *   <li><b>Analytic</b> for cases with closed-form area: rect-rect,
 *       fully-contained, fully-disjoint, identical polygons.</li>
 *   <li><b>Rasterized</b> for cases without analytic area (star-star,
 *       ellipse-rect): bake both polygons into a 1024x1024 binary mask
 *       at fine pixel resolution, AND the masks, count pixels. The
 *       polygon clipper's reported area must match the rasterized
 *       count within rasterization quantisation error.</li>
 * </ol>
 *
 * Tight tolerance on analytic; ~2% on rasterized (rasterization at
 * this resolution naturally rounds boundaries).
 */
public class PolygonClippingTest {

    private final ShapeToPolygonConverter converter = new ShapeToPolygonConverter();
    private static final double TIGHT_TOLERANCE = 1e-6;

    // ========== Analytic ground truth ==========

    @Test
    public void disjointRectanglesHaveZeroIntersection() {
        Polygon a = rectPolygon(0, 0, 100, 100);
        Polygon b = rectPolygon(200, 200, 100, 100);
        assertEquals(0.0, PolygonClipping.intersectionArea(a, b), TIGHT_TOLERANCE);
    }

    @Test
    public void identicalRectanglesIntersectionEqualsTheirArea() {
        Polygon a = rectPolygon(50, 50, 200, 100);
        Polygon b = rectPolygon(50, 50, 200, 100);
        assertEquals(200.0 * 100.0, PolygonClipping.intersectionArea(a, b), TIGHT_TOLERANCE);
    }

    @Test
    public void fullyContainedRectangleIntersectionEqualsContainedArea() {
        Polygon outer = rectPolygon(0, 0, 1000, 1000);
        Polygon inner = rectPolygon(100, 100, 200, 200);
        assertEquals(200.0 * 200.0,
            PolygonClipping.intersectionArea(outer, inner), TIGHT_TOLERANCE);
    }

    @Test
    public void halfOverlappingRectanglesAnalytic() {
        // Two 100x100 rects offset by (50, 0). Overlap = 50x100 = 5000.
        Polygon a = rectPolygon(0, 0, 100, 100);
        Polygon b = rectPolygon(50, 0, 100, 100);
        assertEquals(50.0 * 100.0,
            PolygonClipping.intersectionArea(a, b), TIGHT_TOLERANCE);
    }

    @Test
    public void touchingRectanglesShareNoArea() {
        // Two rects sharing exactly an edge.
        Polygon a = rectPolygon(0, 0, 100, 100);
        Polygon b = rectPolygon(100, 0, 100, 100);
        assertEquals("Edge-touching rectangles share no area",
            0.0, PolygonClipping.intersectionArea(a, b), TIGHT_TOLERANCE);
    }

    @Test
    public void cornerTouchingRectanglesShareNoArea() {
        Polygon a = rectPolygon(0, 0, 100, 100);
        Polygon b = rectPolygon(100, 100, 100, 100);
        assertEquals(0.0, PolygonClipping.intersectionArea(a, b), TIGHT_TOLERANCE);
    }

    @Test
    public void intersectionRatioOfHalfOverlapIsHalfOfSmaller() {
        Polygon a = rectPolygon(0, 0, 100, 100);
        Polygon b = rectPolygon(50, 0, 100, 100);
        // Overlap is 50% of either; intersectionRatio uses smaller.
        assertEquals(0.5, PolygonClipping.intersectionRatio(a, b), TIGHT_TOLERANCE);
    }

    @Test
    public void intersectionRatioOfFullyContainedSmallShapeIsOne() {
        Polygon outer = rectPolygon(0, 0, 1000, 1000);
        Polygon inner = rectPolygon(100, 100, 200, 200);
        // Inner is fully inside outer; ratio relative to inner = 1.0.
        assertEquals(1.0, PolygonClipping.intersectionRatio(outer, inner), TIGHT_TOLERANCE);
    }

    // ========== Concave shapes (stars) ==========

    @Test
    public void identicalStarsIntersectionEqualsTheirArea() {
        Polygon a = starPolygon(0, 0, 1000, 1000);
        Polygon b = starPolygon(0, 0, 1000, 1000);
        double aArea = a.area();
        // Identical concave polygons should produce intersection ==
        // their area, within the triangulation+clipping numerical
        // tolerance (multiple SH intersections accumulate floating
        // error). 0.1% is generous but well below the 25% warning
        // threshold.
        assertEquals(aArea, PolygonClipping.intersectionArea(a, b), aArea * 0.001);
    }

    @Test
    public void disjointStarsHaveZeroIntersection() {
        Polygon a = starPolygon(0, 0, 1000, 1000);
        Polygon b = starPolygon(2000, 2000, 1000, 1000); // far away
        assertEquals(0.0, PolygonClipping.intersectionArea(a, b), TIGHT_TOLERANCE);
    }

    @Test
    public void starOverRectMatchesRasterizedGroundTruth() {
        // No analytic closed form for "5-point star clipped by rectangle."
        // Rasterize both at 1024x1024 within a known world bounds, AND
        // the masks, count overlapping pixels, scale back to world units.
        Polygon star = starPolygon(0, 0, 1000, 1000);
        Polygon rect = rectPolygon(300, 300, 800, 400);

        double polygonAnswer = PolygonClipping.intersectionArea(star, rect);
        double rasterAnswer = rasterizedIntersectionArea(star, rect, 0, 0, 2000, 2000, 1024);

        // Rasterization at 1024x1024 over a 2000x2000 world produces
        // ~2-pixel boundary error. For a star-rect overlap of order
        // 200K square units, that's < 1% relative error.
        double relError = Math.abs(polygonAnswer - rasterAnswer) / rasterAnswer;
        assertTrue("Star-over-rect intersection: polygon=" + polygonAnswer
            + " raster=" + rasterAnswer + " relError=" + (relError * 100) + "%",
            relError < 0.02);
    }

    @Test
    public void ellipseOverRectMatchesRasterizedGroundTruth() {
        // Ellipse + rect are both convex (after 32-pt sampling of
        // ellipse). Hits the SH fast path. Compare to rasterized
        // ground truth.
        Polygon ellipse = ellipsePolygon(0, 0, 1000, 600);
        Polygon rect = rectPolygon(400, 100, 800, 400);

        double polygonAnswer = PolygonClipping.intersectionArea(ellipse, rect);
        double rasterAnswer = rasterizedIntersectionArea(ellipse, rect, 0, 0, 2000, 1500, 1024);

        double relError = Math.abs(polygonAnswer - rasterAnswer) / rasterAnswer;
        assertTrue("Ellipse-over-rect: polygon=" + polygonAnswer
            + " raster=" + rasterAnswer + " relError=" + (relError * 100) + "%",
            relError < 0.02);
    }

    @Test
    public void rotatedRectIntersectionMatchesRasterized() {
        // Rotation must propagate end-to-end: rotated rect, axis-aligned
        // rect, polygon clipper handles the non-axis-aligned quad.
        ShapeGeometry geo = new ShapeGeometry(0, 0, 1000, 500, 30 * 60000);
        SlideShape rotated = new SlideShape(1, "Rot", SlideShape.ShapeType.RECTANGLE, "", geo, null);
        Polygon rotatedRect = converter.convertToPolygon(rotated);
        Polygon plainRect = rectPolygon(200, 100, 600, 400);

        double polygonAnswer = PolygonClipping.intersectionArea(rotatedRect, plainRect);
        double rasterAnswer = rasterizedIntersectionArea(
            rotatedRect, plainRect, -200, -200, 1500, 1200, 1024);

        double relError = Math.abs(polygonAnswer - rasterAnswer) / rasterAnswer;
        assertTrue("Rotated-rect intersection: polygon=" + polygonAnswer
            + " raster=" + rasterAnswer + " relError=" + (relError * 100) + "%",
            relError < 0.02);
    }

    // ========== Helpers ==========

    private Polygon rectPolygon(double x, double y, double w, double h) {
        return new Polygon(List.of(
            new Vector2D(x, y),
            new Vector2D(x + w, y),
            new Vector2D(x + w, y + h),
            new Vector2D(x, y + h)));
    }

    private Polygon starPolygon(double x, double y, double w, double h) {
        ShapeGeometry geo = new ShapeGeometry((long) x, (long) y, (long) w, (long) h);
        SlideShape shape = new SlideShape(1, "Star", SlideShape.ShapeType.STAR_5_POINTS, "", geo, null);
        return converter.convertToPolygon(shape);
    }

    private Polygon ellipsePolygon(double x, double y, double w, double h) {
        ShapeGeometry geo = new ShapeGeometry((long) x, (long) y, (long) w, (long) h);
        SlideShape shape = new SlideShape(1, "Ellipse", SlideShape.ShapeType.ELLIPSE, "", geo, null);
        return converter.convertToPolygon(shape);
    }

    /**
     * Rasterize both polygons into a binary mask at the requested
     * resolution within the world bounds, AND the masks, count the
     * overlapping pixels, scale back to world-area units.
     *
     * <p>Independent ground truth source -- doesn't share any code with
     * the polygon clipper, so a regression in the clipper won't also
     * silently corrupt the comparison.
     */
    private double rasterizedIntersectionArea(Polygon a, Polygon b,
            double worldX, double worldY, double worldW, double worldH, int resolution) {
        boolean[][] maskA = rasterize(a, worldX, worldY, worldW, worldH, resolution);
        boolean[][] maskB = rasterize(b, worldX, worldY, worldW, worldH, resolution);
        int overlapPixels = 0;
        for (int y = 0; y < resolution; y++) {
            for (int x = 0; x < resolution; x++) {
                if (maskA[y][x] && maskB[y][x]) overlapPixels++;
            }
        }
        double pixelAreaInWorld = (worldW / resolution) * (worldH / resolution);
        return overlapPixels * pixelAreaInWorld;
    }

    private boolean[][] rasterize(Polygon p,
            double worldX, double worldY, double worldW, double worldH, int resolution) {
        boolean[][] mask = new boolean[resolution][resolution];
        for (int py = 0; py < resolution; py++) {
            // World-y of pixel center.
            double wy = worldY + (py + 0.5) * worldH / resolution;
            for (int px = 0; px < resolution; px++) {
                double wx = worldX + (px + 0.5) * worldW / resolution;
                if (pointInPolygon(p, wx, wy)) mask[py][px] = true;
            }
        }
        return mask;
    }

    /**
     * Crossing-number point-in-polygon test. Independent of any
     * triangulation logic in the clipper.
     */
    private boolean pointInPolygon(Polygon p, double x, double y) {
        List<Vector2D> v = p.getVertices();
        int n = v.size();
        boolean inside = false;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = v.get(i).getX(), yi = v.get(i).getY();
            double xj = v.get(j).getX(), yj = v.get(j).getY();
            boolean intersect = ((yi > y) != (yj > y))
                && (x < (xj - xi) * (y - yi) / (yj - yi) + xi);
            if (intersect) inside = !inside;
        }
        return inside;
    }
}
