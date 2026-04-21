package com.excudo.core.geometry;

import com.excudo.core.geometry.SATCollisionDetector.Polygon;
import com.excudo.core.geometry.SATCollisionDetector.Vector2D;

import java.util.ArrayList;
import java.util.List;

/**
 * Polygon clipping and intersection-area computation for the Tier 3
 * shape-overlap warning. Handles convex-convex via Sutherland-Hodgman
 * (1974) and concave-concave via ear-clipping triangulation (Meisters
 * 1975 / O'Rourke 1991) followed by triangle-triangle clipping.
 *
 * <p>Internal convention: polygons are normalised to counter-clockwise
 * (CCW) winding via the signed shoelace formula before clipping or
 * triangulation. CCW is the standard math convention; OOXML EMU
 * coordinates have Y growing downward (screen coords), so a
 * "visually clockwise" polygon there is mathematically CCW. The
 * signed-area sign tells us which without the caller having to think
 * about coordinate orientation.
 *
 * <p>Numerical: all computation in double precision. EMU coordinates
 * span ~12M for a typical slide; their products land well within
 * double range, including the shoelace sums.
 *
 * <p>Degenerate cases (touching polygons, coincident edges, collinear
 * vertices) yield zero-area intersection polygons rather than special
 * exceptions -- "two shapes brush at a corner" should report 0%
 * overlap, not throw.
 */
public final class PolygonClipping {

    /** Minimum vertex count for a polygon to have non-zero area. */
    private static final int MIN_VERTICES = 3;

    private PolygonClipping() {}

    // ========== Public API ==========

    /**
     * Compute the area of the intersection between two arbitrary simple
     * polygons. Handles concave polygons (stars, hearts, lightning,
     * custom geometry) via ear-clipping triangulation; convex pairs
     * dispatch to Sutherland-Hodgman directly for speed.
     *
     * <p>Returns 0 for degenerate inputs (fewer than 3 vertices), for
     * disjoint polygons, and for polygons that touch but do not
     * overlap (e.g. share an edge or corner).
     */
    public static double intersectionArea(Polygon a, Polygon b) {
        if (a == null || b == null) return 0.0;
        if (a.getVertexCount() < MIN_VERTICES || b.getVertexCount() < MIN_VERTICES) return 0.0;

        // Cheap AABB pre-filter: if bounding boxes don't overlap, the
        // polygons can't either. Skips the expensive triangulation +
        // clipping path for obviously-disjoint pairs.
        SATCollisionDetector.BoundingBox bbA = a.getBoundingBox();
        SATCollisionDetector.BoundingBox bbB = b.getBoundingBox();
        if (bbA.getRight() < bbB.getX() || bbB.getRight() < bbA.getX()
                || bbA.getBottom() < bbB.getY() || bbB.getBottom() < bbA.getY()) {
            return 0.0;
        }

        // Normalise both polygons to CCW so SH and ear-clipping share
        // the same convexity / inside-the-edge convention.
        List<Vector2D> aCCW = ensureCCW(a.getVertices());
        List<Vector2D> bCCW = ensureCCW(b.getVertices());

        boolean aConvex = isConvex(aCCW);
        boolean bConvex = isConvex(bCCW);

        if (aConvex && bConvex) {
            // Fast path: SH directly on the two convex polygons.
            Polygon clipped = sutherlandHodgman(new Polygon(aCCW), new Polygon(bCCW));
            return clipped.area();
        }

        // General path: triangulate the concave one (or both) via
        // ear-clipping, clip each triangle of A against each triangle of B,
        // sum areas. SH requires the CLIP polygon to be convex; triangles
        // always are, so the order doesn't matter.
        List<List<Vector2D>> triA = aConvex ? List.of(aCCW) : earClip(aCCW);
        List<List<Vector2D>> triB = bConvex ? List.of(bCCW) : earClip(bCCW);

        double total = 0.0;
        for (List<Vector2D> tA : triA) {
            for (List<Vector2D> tB : triB) {
                Polygon clipped = sutherlandHodgman(new Polygon(tA), new Polygon(tB));
                total += clipped.area();
            }
        }
        return total;
    }

    /**
     * Convenience: intersection area as a fraction of the smaller
     * polygon's area. Returns 0 when either input is degenerate.
     * Used by the Tier 3 overlap warning -- "X% of the smaller shape
     * is overlapped" reads better than absolute EMU values for
     * threshold comparisons.
     */
    public static double intersectionRatio(Polygon a, Polygon b) {
        double inter = intersectionArea(a, b);
        if (inter <= 0) return 0.0;
        double areaA = a.area();
        double areaB = b.area();
        double smaller = Math.min(areaA, areaB);
        if (smaller <= 0) return 0.0;
        return inter / smaller;
    }

    // ========== Sutherland-Hodgman ==========

    /**
     * Sutherland-Hodgman convex polygon clipping. The {@code clip}
     * polygon must be convex; {@code subject} can be any simple
     * polygon. Returns the intersection polygon (may be empty if the
     * subject lies entirely outside the clip region).
     */
    static Polygon sutherlandHodgman(Polygon subject, Polygon clip) {
        List<Vector2D> output = new ArrayList<>(subject.getVertices());
        List<Vector2D> clipVerts = clip.getVertices();
        int clipN = clipVerts.size();

        for (int i = 0; i < clipN; i++) {
            if (output.isEmpty()) return new Polygon(output);
            List<Vector2D> input = output;
            output = new ArrayList<>();
            Vector2D edgeStart = clipVerts.get(i);
            Vector2D edgeEnd = clipVerts.get((i + 1) % clipN);
            Vector2D s = input.get(input.size() - 1);

            for (Vector2D e : input) {
                if (isInsideEdge(e, edgeStart, edgeEnd)) {
                    if (!isInsideEdge(s, edgeStart, edgeEnd)) {
                        output.add(lineSegmentIntersection(s, e, edgeStart, edgeEnd));
                    }
                    output.add(e);
                } else if (isInsideEdge(s, edgeStart, edgeEnd)) {
                    output.add(lineSegmentIntersection(s, e, edgeStart, edgeEnd));
                }
                s = e;
            }
        }
        return new Polygon(output);
    }

    /**
     * Is point {@code p} on the inside (left side) of the directed
     * edge from {@code a} to {@code b}? For a CCW-wound clip polygon,
     * "inside" means "interior side of the edge."
     *
     * <p>Uses the 2D cross product. Points exactly on the edge count
     * as inside so SH doesn't lose vertices that lie on the clip
     * boundary.
     */
    private static boolean isInsideEdge(Vector2D p, Vector2D a, Vector2D b) {
        return (b.getX() - a.getX()) * (p.getY() - a.getY())
             - (b.getY() - a.getY()) * (p.getX() - a.getX()) >= 0;
    }

    /**
     * Compute the intersection of two infinite lines defined by
     * segments (s,e) and (a,b). Caller (Sutherland-Hodgman) only
     * invokes this when an actual intersection is known to exist
     * within the segments, so degenerate parallel handling falls
     * back to the segment endpoint -- the resulting polygon has a
     * spurious vertex but correct area.
     */
    private static Vector2D lineSegmentIntersection(Vector2D s, Vector2D e, Vector2D a, Vector2D b) {
        double x1 = s.getX(), y1 = s.getY();
        double x2 = e.getX(), y2 = e.getY();
        double x3 = a.getX(), y3 = a.getY();
        double x4 = b.getX(), y4 = b.getY();
        double denom = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4);
        if (denom == 0) return s; // parallel/degenerate
        double t = ((x1 - x3) * (y3 - y4) - (y1 - y3) * (x3 - x4)) / denom;
        return new Vector2D(x1 + t * (x2 - x1), y1 + t * (y2 - y1));
    }

    // ========== Ear-clipping triangulation ==========

    /**
     * Decompose a simple (possibly concave) CCW polygon into a list of
     * triangles via ear-clipping. Each triangle is a
     * {@code List&lt;Vector2D&gt;} of exactly 3 vertices.
     *
     * <p>Algorithm: repeatedly find an "ear" -- three consecutive
     * vertices (a, b, c) where (i) the angle at b is convex, and (ii)
     * the triangle (a, b, c) contains no other polygon vertices.
     * Remove b, output the triangle, repeat until only 3 vertices
     * remain.
     *
     * <p>Worst-case O(n^2) but n is small for slide shapes (a 5-point
     * star has 10 vertices = 24 ear-checks total). No spatial index
     * needed.
     */
    static List<List<Vector2D>> earClip(List<Vector2D> ccwVertices) {
        List<List<Vector2D>> triangles = new ArrayList<>();
        List<Vector2D> verts = new ArrayList<>(ccwVertices);

        // Defensive: degenerate inputs return empty list.
        if (verts.size() < 3) return triangles;

        // Bound iterations to avoid infinite loops on pathological
        // input (e.g. self-intersecting polygons that slip past our
        // simple-polygon assumption). 3*n is a safe ceiling for
        // well-formed simple polygons.
        int maxIterations = verts.size() * 3;
        int iter = 0;

        while (verts.size() > 3 && iter++ < maxIterations) {
            int n = verts.size();
            boolean earFound = false;
            for (int i = 0; i < n; i++) {
                Vector2D a = verts.get((i - 1 + n) % n);
                Vector2D b = verts.get(i);
                Vector2D c = verts.get((i + 1) % n);

                if (!isConvexAngle(a, b, c)) continue;

                // Check no other vertex falls inside the candidate ear.
                boolean isEar = true;
                for (int j = 0; j < n; j++) {
                    if (j == (i - 1 + n) % n || j == i || j == (i + 1) % n) continue;
                    if (isPointInTriangle(verts.get(j), a, b, c)) {
                        isEar = false;
                        break;
                    }
                }
                if (isEar) {
                    triangles.add(List.of(a, b, c));
                    verts.remove(i);
                    earFound = true;
                    break;
                }
            }
            if (!earFound) {
                // Polygon may not be simple, or numerical issues
                // prevented ear detection. Bail with what we have so
                // far rather than loop forever.
                break;
            }
        }
        if (verts.size() == 3) {
            triangles.add(List.of(verts.get(0), verts.get(1), verts.get(2)));
        }
        return triangles;
    }

    /**
     * Convex angle at vertex b (between edges a->b and b->c) for a
     * CCW polygon. Uses the 2D cross product.
     */
    private static boolean isConvexAngle(Vector2D a, Vector2D b, Vector2D c) {
        double cross = (b.getX() - a.getX()) * (c.getY() - b.getY())
                     - (b.getY() - a.getY()) * (c.getX() - b.getX());
        return cross > 0;
    }

    /**
     * Strict point-in-triangle test via barycentric coordinates.
     * "Strict" means a point exactly on a triangle edge counts as
     * inside; ear-clipping wants to detect ANY vertex that would
     * compromise the ear, including coincident ones.
     */
    private static boolean isPointInTriangle(Vector2D p, Vector2D a, Vector2D b, Vector2D c) {
        double d1 = sign(p, a, b);
        double d2 = sign(p, b, c);
        double d3 = sign(p, c, a);
        boolean hasNeg = (d1 < 0) || (d2 < 0) || (d3 < 0);
        boolean hasPos = (d1 > 0) || (d2 > 0) || (d3 > 0);
        return !(hasNeg && hasPos);
    }

    private static double sign(Vector2D p1, Vector2D p2, Vector2D p3) {
        return (p1.getX() - p3.getX()) * (p2.getY() - p3.getY())
             - (p2.getX() - p3.getX()) * (p1.getY() - p3.getY());
    }

    // ========== Winding + convexity helpers ==========

    /**
     * Return the polygon's vertex list normalised to counter-clockwise
     * winding (positive signed area). If already CCW, returns the
     * input list unchanged; otherwise returns a reversed copy.
     */
    static List<Vector2D> ensureCCW(List<Vector2D> vertices) {
        if (signedArea(vertices) >= 0) return vertices;
        List<Vector2D> reversed = new ArrayList<>(vertices.size());
        for (int i = vertices.size() - 1; i >= 0; i--) reversed.add(vertices.get(i));
        return reversed;
    }

    /**
     * Signed area via the shoelace formula. Positive = CCW (math
     * convention), negative = CW. Useful for both winding detection
     * and (via abs) area computation.
     */
    static double signedArea(List<Vector2D> vertices) {
        int n = vertices.size();
        if (n < 3) return 0.0;
        double sum = 0.0;
        for (int i = 0; i < n; i++) {
            Vector2D a = vertices.get(i);
            Vector2D b = vertices.get((i + 1) % n);
            sum += a.getX() * b.getY() - b.getX() * a.getY();
        }
        return sum / 2.0;
    }

    /**
     * True iff every interior angle in {@code ccw} is convex (i.e.
     * the polygon has no reflex vertices). Assumes input is CCW;
     * call {@link #ensureCCW} first if winding is unknown.
     *
     * <p>Cheap O(n) scan; worth checking before paying for ear-clipping
     * on what might be a convex polygon.
     */
    static boolean isConvex(List<Vector2D> ccw) {
        int n = ccw.size();
        if (n < 3) return false;
        for (int i = 0; i < n; i++) {
            Vector2D a = ccw.get(i);
            Vector2D b = ccw.get((i + 1) % n);
            Vector2D c = ccw.get((i + 2) % n);
            double cross = (b.getX() - a.getX()) * (c.getY() - b.getY())
                         - (b.getY() - a.getY()) * (c.getX() - b.getX());
            if (cross < 0) return false; // reflex vertex
        }
        return true;
    }
}
