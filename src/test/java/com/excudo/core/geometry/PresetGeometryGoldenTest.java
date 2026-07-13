package com.excudo.core.geometry;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Golden vertex expectations for spec presets, HAND-DERIVED from the
 * ECMA-376 guide formulas (each derivation is shown inline). The default
 * shape box is 200x100 (w=200 h=100 ss=100 ls=200 hc=100 vc=50 r=200
 * b=100), chosen non-square so w/h/ss mix-ups cannot cancel out.
 *
 * <p>These values were computed from the formulas by hand/calculator --
 * NOT by running the engine and copying its output.
 */
public class PresetGeometryGoldenTest {

    private static final double EPS = 1e-9;
    /** For irrational expectations quoted to 6 decimals. */
    private static final double TRIG_EPS = 5e-6;

    /** Resolve a preset's first path at 200x100 with default adjusts. */
    private static GeometryResolver.ResolvedPath path0(String preset) {
        return GeometryResolver.resolve(
            PresetGeometryRegistry.get(preset), Map.of(), 200, 100).paths().get(0);
    }

    /**
     * On-curve anchor points of a path: Move/Line targets and Cubic
     * endpoints, in order. For pure polygons this is exactly the vertex
     * ring (Close contributes nothing).
     */
    private static List<double[]> anchors(GeometryResolver.ResolvedPath p) {
        List<double[]> pts = new ArrayList<>();
        for (GeometryResolver.Segment s : p.segments()) {
            if (s instanceof GeometryResolver.Move m) pts.add(new double[]{m.x(), m.y()});
            else if (s instanceof GeometryResolver.Line l) pts.add(new double[]{l.x(), l.y()});
            else if (s instanceof GeometryResolver.Cubic c) pts.add(new double[]{c.x3(), c.y3()});
        }
        return pts;
    }

    private static void assertVertices(double[][] expected, List<double[]> actual, double eps) {
        assertEquals("vertex count", expected.length, actual.size());
        for (int i = 0; i < expected.length; i++) {
            assertEquals("vertex " + i + " x", expected[i][0], actual.get(i)[0], eps);
            assertEquals("vertex " + i + " y", expected[i][1], actual.get(i)[1], eps);
        }
    }

    // ========== straight-edge presets ==========

    @Test
    public void triangle() {
        // adj=50000: x2 = w*a/100000 = 200*50000/100000 = 100.
        // Path: (l,b) (x2,t) (r,b).
        assertVertices(new double[][]{{0, 100}, {100, 0}, {200, 100}},
            anchors(path0("triangle")), EPS);
    }

    @Test
    public void triangleWithAdjustOverrideMovesTheApex() {
        // adj=0 -> a=pin(0,0,100000)=0 -> x2=0: apex at the LEFT edge.
        var p = GeometryResolver.resolve(
            PresetGeometryRegistry.get("triangle"), Map.of("adj", 0), 200, 100)
            .paths().get(0);
        assertVertices(new double[][]{{0, 100}, {0, 0}, {200, 100}}, anchors(p), EPS);
    }

    @Test
    public void rtTriangle() {
        // Path: (l,b) (l,t) (r,b) -- right angle at bottom-left.
        assertVertices(new double[][]{{0, 100}, {0, 0}, {200, 100}},
            anchors(path0("rtTriangle")), EPS);
    }

    @Test
    public void diamond() {
        // Path: (l,vc) (hc,t) (r,vc) (hc,b).
        assertVertices(new double[][]{{0, 50}, {100, 0}, {200, 50}, {100, 100}},
            anchors(path0("diamond")), EPS);
    }

    @Test
    public void parallelogram() {
        // adj=25000; maxAdj = 100000*w/ss = 200000; a = 25000;
        // x2 = ss*a/100000 = 25; x5 = r - x2 = 175.
        // Path: (l,b) (x2,t) (r,t) (x5,b).
        assertVertices(new double[][]{{0, 100}, {25, 0}, {200, 0}, {175, 100}},
            anchors(path0("parallelogram")), EPS);
    }

    @Test
    public void trapezoid() {
        // adj=25000; maxAdj = 50000*w/ss = 100000; a = 25000;
        // x2 = ss*a/100000 = 25; x3 = r - x2 = 175.
        // Path: (l,b) (x2,t) (x3,t) (r,b) -- the SHORT side is on top.
        assertVertices(new double[][]{{0, 100}, {25, 0}, {175, 0}, {200, 100}},
            anchors(path0("trapezoid")), EPS);
    }

    @Test
    public void pentagon() {
        // hf=105146, vf=110557:
        //   swd2 = wd2*hf/100000 = 105.146,  shd2 = hd2*vf/100000 = 55.2785
        //   svc  = vc*vf/100000  = 55.2785
        //   dx1 = swd2*cos(18deg)  = 99.999788   (1080000/60000 = 18)
        //   dx2 = swd2*cos(306deg) = 61.803268   (18360000/60000 = 306)
        //   dy1 = shd2*sin(18deg)  = 17.082346
        //   dy2 = shd2*sin(306deg) = -44.721246
        //   x1 = hc-dx1 = 0.000212   x2 = hc-dx2 = 38.196732
        //   x3 = hc+dx2 = 161.803268 x4 = hc+dx1 = 199.999788
        //   y1 = svc-dy1 = 38.196504 y2 = svc-dy2 = 99.999746
        // Path: (x1,y1) (hc,t) (x4,y1) (x3,y2) (x2,y2).
        assertVertices(new double[][]{
                {0.000212, 38.196504}, {100, 0}, {199.999788, 38.196504},
                {161.803268, 99.999746}, {38.196732, 99.999746}},
            anchors(path0("pentagon")), TRIG_EPS);
    }

    @Test
    public void hexagon() {
        // adj=25000, vf=115470; maxAdj = 50000*w/ss = 100000; a=25000;
        // shd2 = hd2*vf/100000 = 57.735; x1 = ss*a/100000 = 25; x2 = 175;
        // dy1 = shd2*sin(60deg) = 57.735*0.8660254 = 50.000037 ~= 50
        // (vf=115470 is calibrated so the flat-top hexagon spans the full
        // height); y1 = vc-dy1 ~= 0; y2 = vc+dy1 ~= 100.
        // Path: (l,vc) (x1,y1) (x2,y1) (r,vc) (x2,y2) (x1,y2).
        double dy1 = 57.735 * Math.sin(Math.toRadians(60)); // 50.000037...
        assertVertices(new double[][]{
                {0, 50}, {25, 50 - dy1}, {175, 50 - dy1},
                {200, 50}, {175, 50 + dy1}, {25, 50 + dy1}},
            anchors(path0("hexagon")), TRIG_EPS);
    }

    @Test
    public void octagon() {
        // adj=29289; a=29289; x1 = ss*a/100000 = 29.289; x2 = r-x1 =
        // 170.711; y2 = b-x1 = 70.711.
        // Path: (l,x1) (x1,t) (x2,t) (r,x1) (r,y2) (x2,b) (x1,b) (l,y2).
        assertVertices(new double[][]{
                {0, 29.289}, {29.289, 0}, {170.711, 0}, {200, 29.289},
                {200, 70.711}, {170.711, 100}, {29.289, 100}, {0, 70.711}},
            anchors(path0("octagon")), EPS);
    }

    @Test
    public void star4() {
        // adj=12500; a=12500; iwd2 = wd2*a/50000 = 25; ihd2 = 12.5;
        // sdx = iwd2*cos(45deg) = 17.677670; sdy = ihd2*sin(45deg) = 8.838835;
        // sx1 = 82.322330; sx2 = 117.677670; sy1 = 41.161165; sy2 = 58.838835.
        // Path: (l,vc) (sx1,sy1) (hc,t) (sx2,sy1) (r,vc) (sx2,sy2) (hc,b)
        // (sx1,sy2).
        assertVertices(new double[][]{
                {0, 50}, {82.322330, 41.161165}, {100, 0}, {117.677670, 41.161165},
                {200, 50}, {117.677670, 58.838835}, {100, 100}, {82.322330, 58.838835}},
            anchors(path0("star4")), TRIG_EPS);
    }

    @Test
    public void plus() {
        // adj=25000; a=25000; x1 = ss*a/100000 = 25; x2 = r-x1 = 175;
        // y2 = b-x1 = 75. The arm thickness tracks ss (=h here), so the
        // cross is NOT centered vertically symmetric with x-arms: the
        // horizontal bar spans y in [25,75], vertical bar x in [25,175].
        assertVertices(new double[][]{
                {0, 25}, {25, 25}, {25, 0}, {175, 0}, {175, 25}, {200, 25},
                {200, 75}, {175, 75}, {175, 100}, {25, 100}, {25, 75}, {0, 75}},
            anchors(path0("plus")), EPS);
    }

    @Test
    public void chevron() {
        // adj=50000; maxAdj = 100000*w/ss = 200000; a = 50000;
        // x1 = ss*a/100000 = 50; x2 = r-x1 = 150.
        // Path: (l,t) (x2,t) (r,vc) (x2,b) (l,b) (x1,vc).
        assertVertices(new double[][]{
                {0, 0}, {150, 0}, {200, 50}, {150, 100}, {0, 100}, {50, 50}},
            anchors(path0("chevron")), EPS);
    }

    @Test
    public void homePlate() {
        // adj=50000; maxAdj = 200000; a = 50000; dx1 = ss*a/100000 = 50;
        // x1 = r-dx1 = 150. Path: (l,t) (x1,t) (r,vc) (x1,b) (l,b).
        assertVertices(new double[][]{
                {0, 0}, {150, 0}, {200, 50}, {150, 100}, {0, 100}},
            anchors(path0("homePlate")), EPS);
    }

    @Test
    public void leftArrow() {
        // adj1=adj2=50000; maxAdj2 = 100000*w/ss = 200000; a1=a2=50000;
        // dx2 = ss*a2/100000 = 50; x2 = l+dx2 = 50;
        // dy1 = h*a1/200000 = 25; y1 = vc-dy1 = 25; y2 = vc+dy1 = 75.
        // Path: (l,vc) (x2,t) (x2,y1) (r,y1) (r,y2) (x2,y2) (x2,b).
        assertVertices(new double[][]{
                {0, 50}, {50, 0}, {50, 25}, {200, 25}, {200, 75}, {50, 75}, {50, 100}},
            anchors(path0("leftArrow")), EPS);
    }

    // ========== arc presets ==========

    @Test
    public void roundRectTangentPointsAtDefaultAdjust() {
        // adj=16667; a=16667; x1 = ss*a/100000 = 16.667; x2 = r-x1 =
        // 183.333; y2 = b-x1 = 83.333. Corner radii are x1 in BOTH axes
        // (circular corners even in a 2:1 box). Going clockwise from the
        // left edge, the anchor ring alternates arc-end tangent points
        // and straight-edge targets:
        //   Move (0, 16.667)
        //   arc cd2+cd4  -> (16.667, 0)     [top-left corner]
        //   line         -> (183.333, 0)
        //   arc 3cd4+cd4 -> (200, 16.667)   [top-right corner]
        //   line         -> (200, 83.333)
        //   arc 0+cd4    -> (183.333, 100)  [bottom-right corner]
        //   line         -> (16.667, 100)
        //   arc cd4+cd4  -> (0, 83.333)     [bottom-left corner]
        assertVertices(new double[][]{
                {0, 16.667}, {16.667, 0}, {183.333, 0}, {200, 16.667},
                {200, 83.333}, {183.333, 100}, {16.667, 100}, {0, 83.333}},
            anchors(path0("roundRect")), 1e-6);
    }

    @Test
    public void roundRectWithZeroAdjustIsAPlainRectangle() {
        // adj=0 -> x1=0 -> all four corner arcs are zero-radius no-ops:
        // exactly the rectangle outline.
        var p = GeometryResolver.resolve(
            PresetGeometryRegistry.get("roundRect"), Map.of("adj", 0), 200, 100)
            .paths().get(0);
        assertVertices(new double[][]{
                {0, 0}, {200, 0}, {200, 100}, {0, 100}}, anchors(p), EPS);
    }

    @Test
    public void pieDefaultIsThreeQuartersStartingAtThreeOClock() {
        // Defaults adj1=0, adj2=16200000 (270deg): start point guides
        // give (hc + wd2, vc) = (200, 50); the arc's center resolves to
        // (hc, vc) and sweeps 270deg clockwise-on-screen through
        // (100,100) and (0,50) to (100,0), then a line to the center
        // closes the wedge. (Full anchor walk asserted in ArcToTest;
        // here: first, last-arc, and wedge anchors.)
        var pts = anchors(path0("pie"));
        assertEquals(5, pts.size());
        assertArrayEquals(new double[]{200, 50}, pts.get(0), EPS);
        assertArrayEquals(new double[]{100, 0}, pts.get(3), 1e-6);
        assertArrayEquals(new double[]{100, 50}, pts.get(4), EPS);
    }

    @Test
    public void flowChartDecisionScalesItsTwoByTwoLocalSpace() {
        // Path is defined in a 2x2 local space: (0,1) (1,0) (2,1) (1,2);
        // at 200x100 that scales by (100, 50) to (0,50) (100,0) (200,50)
        // (100,100) -- same diamond the guide-space definition would give.
        assertVertices(new double[][]{{0, 50}, {100, 0}, {200, 50}, {100, 100}},
            anchors(path0("flowChartDecision")), EPS);
    }

    // ========== whole-catalogue resolvability ==========

    @Test
    public void everyVendoredPresetResolvesToFinitePaths() {
        // Every definition must resolve every path command end-to-end
        // (guides, token resolution, local scaling, arc conversion) with
        // finite output in both aspects. An unknown token, a NaN from
        // arc math, or a command-order bug fails here by name.
        for (String name : PresetGeometryRegistry.names()) {
            for (double[] box : new double[][]{{254, 190}, {190, 254}}) {
                GeometryResolver.ResolvedGeometry rg = GeometryResolver.resolve(
                    PresetGeometryRegistry.get(name), Map.of(), box[0], box[1]);
                assertFalse(name + " resolved to zero paths", rg.paths().isEmpty());
                for (GeometryResolver.ResolvedPath p : rg.paths()) {
                    for (GeometryResolver.Segment s : p.segments()) {
                        for (double v : coords(s)) {
                            assertTrue(name + " produced non-finite coordinate",
                                Double.isFinite(v));
                        }
                    }
                }
            }
        }
    }

    private static double[] coords(GeometryResolver.Segment s) {
        if (s instanceof GeometryResolver.Move m) return new double[]{m.x(), m.y()};
        if (s instanceof GeometryResolver.Line l) return new double[]{l.x(), l.y()};
        if (s instanceof GeometryResolver.Cubic c) {
            return new double[]{c.x1(), c.y1(), c.x2(), c.y2(), c.x3(), c.y3()};
        }
        return new double[0];
    }
}
