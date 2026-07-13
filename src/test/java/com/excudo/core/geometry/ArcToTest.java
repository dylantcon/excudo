package com.excudo.core.geometry;

import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Pins the {@code arcTo} math in {@link GeometryResolver}: OOXML ray
 * angles (60000ths of a degree, y-down, clockwise-positive) convert to
 * ellipse parameter angles via the atan-then-scale skew, the ellipse
 * center is derived so the current point sits at {@code stAng}, and the
 * cubic approximation stays within its analytic error bound. All
 * expectations are hand-computed from the spec semantics -- never from
 * engine output.
 */
public class ArcToTest {

    /** Cubic max radial error for a 90-degree arc is ~2.72e-4 * r. */
    private static final double ARC_EPS = 0.05;
    private static final double EPS = 1e-9;

    /** Definition with a single path built from raw command tokens. */
    private static GeometryDefinition def(GeometryPath.Command... cmds) {
        return new GeometryDefinition("test", List.of(), List.of(),
            List.of(new GeometryPath(0, 0, GeometryPath.FillMode.NORM, true,
                List.of(cmds))), null);
    }

    private static List<GeometryResolver.Segment> resolveSegments(
            GeometryDefinition d, double w, double h) {
        return GeometryResolver.resolve(d, Map.of(), w, h).paths().get(0).segments();
    }

    /** End point of a segment (Move/Line/Cubic). */
    private static double[] end(GeometryResolver.Segment s) {
        if (s instanceof GeometryResolver.Move m) return new double[]{m.x(), m.y()};
        if (s instanceof GeometryResolver.Line l) return new double[]{l.x(), l.y()};
        if (s instanceof GeometryResolver.Cubic c) return new double[]{c.x3(), c.y3()};
        throw new AssertionError("no endpoint on " + s);
    }

    /** Evaluate a Cubic at parameter u, given its start point. */
    private static double[] cubicAt(double[] p0, GeometryResolver.Cubic c, double u) {
        double v = 1 - u;
        double b0 = v * v * v, b1 = 3 * v * v * u, b2 = 3 * v * u * u, b3 = u * u * u;
        return new double[]{
            b0 * p0[0] + b1 * c.x1() + b2 * c.x2() + b3 * c.x3(),
            b0 * p0[1] + b1 * c.y1() + b2 * c.y2() + b3 * c.y3()};
    }

    // ========== circle quadrants ==========

    @Test
    public void quarterCircleFromThreeOClockSweepsClockwiseOnScreen() {
        // Current point (100,50) = stAng 0 on a radius-50 circle, so the
        // center is (100,50) - (50*cos0, 50*sin0) = (50,50). swAng +90
        // (cd4) ends at (50+50*cos90, 50+50*sin90) = (50,100): in y-down
        // space a positive sweep moves toward the BOTTOM of the box --
        // clockwise on screen.
        var segs = resolveSegments(def(
            new GeometryPath.MoveTo("100", "50"),
            new GeometryPath.ArcTo("50", "50", "0", "5400000")), 100, 100);

        assertEquals(2, segs.size());
        var arc = (GeometryResolver.Cubic) segs.get(1);
        assertArrayEquals(new double[]{50, 100}, end(arc), EPS);

        // Curve midpoint approximates the 45-degree point
        // (50+50*cos45, 50+50*sin45) = (85.355339, 85.355339).
        double[] mid = cubicAt(new double[]{100, 50}, arc, 0.5);
        assertEquals(85.355339, mid[0], ARC_EPS);
        assertEquals(85.355339, mid[1], ARC_EPS);
    }

    @Test
    public void negativeSweepRunsCounterClockwiseOnScreen() {
        // Same start, swAng -90 (toward the TOP): ends at
        // (50+50*cos(-90), 50+50*sin(-90)) = (50, 0).
        var segs = resolveSegments(def(
            new GeometryPath.MoveTo("100", "50"),
            new GeometryPath.ArcTo("50", "50", "0", "-5400000")), 100, 100);
        assertArrayEquals(new double[]{50, 0}, end(segs.get(1)), EPS);
    }

    @Test
    public void multiQuadrantSweepAnchorsExactlyOnQuarterPoints() {
        // 270 degrees from stAng 0 splits into three 90-degree cubics;
        // every cubic boundary is an exact ellipse point: (50,100) at
        // 90, (0,50) at 180, (50,0) at 270.
        var segs = resolveSegments(def(
            new GeometryPath.MoveTo("100", "50"),
            new GeometryPath.ArcTo("50", "50", "0", "16200000")), 100, 100);
        assertEquals(4, segs.size()); // Move + 3 cubics
        assertArrayEquals(new double[]{50, 100}, end(segs.get(1)), EPS);
        assertArrayEquals(new double[]{0, 50}, end(segs.get(2)), EPS);
        assertArrayEquals(new double[]{50, 0}, end(segs.get(3)), EPS);
    }

    @Test
    public void fullTurnClosesExactlyOnItsStart() {
        // stAng 90, swAng 21600000 (a full turn): the donut hole /
        // ellipse case. Must land exactly back on the start point.
        var segs = resolveSegments(def(
            new GeometryPath.MoveTo("50", "100"),
            new GeometryPath.ArcTo("50", "50", "5400000", "21600000")), 100, 100);
        assertEquals(5, segs.size()); // Move + 4 quadrant cubics
        assertArrayEquals(new double[]{50, 100}, end(segs.get(4)), EPS);
    }

    // ========== the ellipse skew (ray angles, not parameter angles) ==========

    @Test
    public void ellipseAnglesUseAtanThenScaleSkew() {
        // rx=100, ry=50, stAng=45deg (2700000). The OOXML 45-degree RAY
        // hits the ellipse at parameter t0 = atan2(rx sin45, ry cos45)
        //    = atan2(70.710678, 35.355339) = atan2(2, 1) = 63.434949 deg
        // -- exactly the value the spec's own chord/pie guides derive
        // via cat2/sat2. Start point for center (100,50):
        //    (100 + 100 cos t0, 50 + 50 sin t0) = (144.721360, 94.721360)
        // (the guides px/py below derive it the same way the spec does,
        // because path tokens are integers-or-guide-names only).
        // swAng=45deg ends the ray at 90deg, and 90 maps to t1=90
        // exactly (quarter boundaries are fixed points), so the arc must
        // END at (100 + 100 cos90, 50 + 50 sin90) = (100, 100).
        //
        // A naive parametric reading (t = theta) would instead put the
        // center at (74.0, 59.4) and the end at (74.0, 109.4) -- pinning
        // the skew, not just the offset.
        GeometryDefinition d = new GeometryDefinition("test", List.of(),
            List.of(
                new GeometryDefinition.Guide("sx", "cat2 100 1 2"),
                new GeometryDefinition.Guide("sy", "sat2 50 1 2"),
                new GeometryDefinition.Guide("px", "+- 100 sx 0"),
                new GeometryDefinition.Guide("py", "+- 50 sy 0")),
            List.of(new GeometryPath(0, 0, GeometryPath.FillMode.NORM, true,
                List.of(
                    new GeometryPath.MoveTo("px", "py"),
                    new GeometryPath.ArcTo("100", "50", "2700000", "2700000")))),
            null);
        var segs = resolveSegments(d, 200, 100);
        assertArrayEquals(new double[]{144.721360, 94.721360}, end(segs.get(0)), 1e-5);
        double[] e = end(segs.get(segs.size() - 1));
        assertEquals(100.0, e[0], 1e-6);
        assertEquals(100.0, e[1], 1e-6);
    }

    @Test
    public void paramAngleFixesQuarterBoundariesAndPreservesTurns() {
        double rx = 100, ry = 50;
        // Quarter-turn boundaries map to themselves for any radii.
        for (int k = -4; k <= 8; k++) {
            double theta = k * Math.PI / 2;
            assertEquals("k=" + k, theta,
                GeometryResolver.paramAngle(theta, rx, ry), 1e-12);
        }
        // Interior angles skew toward the long axis: ray 45deg on a 2:1
        // ellipse is parameter 63.434949 deg.
        assertEquals(Math.toRadians(63.434949),
            GeometryResolver.paramAngle(Math.toRadians(45), rx, ry), 1e-8);
        // ...and a full turn later, exactly 360 more.
        assertEquals(Math.toRadians(63.434949 + 360),
            GeometryResolver.paramAngle(Math.toRadians(45 + 360), rx, ry), 1e-8);
        // Fourth quadrant stays in the fourth quadrant.
        double t = GeometryResolver.paramAngle(Math.toRadians(315), rx, ry);
        assertEquals(Math.toRadians(360 - 63.434949), t, 1e-8);
    }

    // ========== spec-preset cross-checks ==========

    @Test
    public void cornerArcMeetsItsTangentPoints() {
        // The roundRect corner idiom: pen at (l, r0) = (0, 20), arcTo
        // wR=hR=20, stAng=cd2, swAng=cd4. Center = (0,20) - 20*(cos180,
        // sin180) = (20, 20); ends at t=270: (20, 0) -- the top-edge
        // tangent point. (The full preset is covered in the golden test.)
        var segs = resolveSegments(def(
            new GeometryPath.MoveTo("0", "20"),
            new GeometryPath.ArcTo("20", "20", "cd2", "cd4")), 200, 100);
        assertArrayEquals(new double[]{20, 0}, end(segs.get(1)), EPS);
    }

    @Test
    public void pieArcStartsWhereTheGuidesPutThePen() {
        // The pie preset moves to (x1,y1) computed by cat2/sat2 guides,
        // then arcs with the same stAng. The arc's derived center must
        // therefore be exactly (hc, vc). Defaults: stAng=0, sw=270deg,
        // 200x100 box: pen (200,50), center (100,50); quadrant anchors
        // (100,100), (0,50); end (100,0).
        GeometryDefinition pie = PresetGeometryRegistry.get("pie");
        var segs = GeometryResolver.resolve(pie, Map.of(), 200, 100)
            .paths().get(0).segments();
        assertArrayEquals(new double[]{200, 50}, end(segs.get(0)), EPS);
        assertArrayEquals(new double[]{100, 100}, end(segs.get(1)), 1e-6);
        assertArrayEquals(new double[]{0, 50}, end(segs.get(2)), 1e-6);
        assertArrayEquals(new double[]{100, 0}, end(segs.get(3)), 1e-6);
        // then the wedge: line to center, close
        assertArrayEquals(new double[]{100, 50}, end(segs.get(4)), EPS);
        assertTrue(segs.get(5) instanceof GeometryResolver.Close);
    }

    @Test
    public void chordAnchorsAllLieOnTheEllipse() {
        // chord defaults: stAng=45deg, enAng=270deg -> sweep 225deg on
        // the (100,50) ellipse centered (100,50). Start hand-derived:
        // (144.721360, 94.721360); end (100, 0). Every cubic boundary
        // must satisfy the ellipse equation.
        GeometryDefinition chord = PresetGeometryRegistry.get("chord");
        var segs = GeometryResolver.resolve(chord, Map.of(), 200, 100)
            .paths().get(0).segments();
        assertArrayEquals(new double[]{144.721360, 94.721360}, end(segs.get(0)), 1e-5);
        GeometryResolver.Segment last = segs.get(segs.size() - 2); // before Close
        assertArrayEquals(new double[]{100, 0}, end(last), 1e-5);
        for (int i = 1; i < segs.size() - 1; i++) {
            double[] p = end(segs.get(i));
            double ex = (p[0] - 100) / 100, ey = (p[1] - 50) / 50;
            assertEquals("anchor " + i + " on ellipse", 1.0, ex * ex + ey * ey, 1e-9);
        }
    }

    // ========== degenerate + error contracts ==========

    @Test
    public void zeroRadiusArcIsANoOpNotACrash() {
        // roundRect with adj=0 collapses its corner radii to 0;
        // PowerPoint draws a plain rectangle. The pen must stay put and
        // a following lnTo continues from it.
        var segs = resolveSegments(def(
            new GeometryPath.MoveTo("10", "20"),
            new GeometryPath.ArcTo("0", "50", "0", "5400000"),
            new GeometryPath.LnTo("30", "20")), 100, 100);
        assertEquals(2, segs.size());
        assertArrayEquals(new double[]{30, 20}, end(segs.get(1)), EPS);
    }

    @Test
    public void closeResetsThePenToTheSubpathStart() {
        // After close the pen is back at the subpath's moveTo; an arc
        // that follows continues from THERE. Start (0,0), stAng 0,
        // radius 50 -> center (-50, 0), +90 ends at (-50, 50).
        var segs = resolveSegments(def(
            new GeometryPath.MoveTo("0", "0"),
            new GeometryPath.LnTo("100", "0"),
            new GeometryPath.Close(),
            new GeometryPath.ArcTo("50", "50", "0", "5400000")), 100, 100);
        assertArrayEquals(new double[]{-50, 50}, end(segs.get(3)), EPS);
    }

    @Test(expected = IllegalArgumentException.class)
    public void arcToWithoutCurrentPointThrows() {
        resolveSegments(def(
            new GeometryPath.ArcTo("50", "50", "0", "5400000")), 100, 100);
    }

    @Test(expected = IllegalArgumentException.class)
    public void lnToWithoutCurrentPointThrows() {
        resolveSegments(def(new GeometryPath.LnTo("1", "2")), 100, 100);
    }
}
