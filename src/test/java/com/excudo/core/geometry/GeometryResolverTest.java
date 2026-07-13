package com.excudo.core.geometry;

import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Resolver behaviors not covered by the arc suite or the preset goldens:
 * quad-to-cubic elevation, path-local scaling of curve control points,
 * fill-mode/stroke passthrough, and multi-subpath paths.
 */
public class GeometryResolverTest {

    private static final double EPS = 1e-9;

    private static GeometryDefinition onePath(GeometryPath path) {
        return new GeometryDefinition("test", List.of(), List.of(), List.of(path), null);
    }

    @Test
    public void quadBezToElevatesExactly() {
        // Quad (0,0) -> ctrl (50,100) -> (100,0). Exact degree elevation:
        // c1 = p0 + 2/3(q-p0) = (100/3, 200/3); c2 = p2 + 2/3(q-p2) =
        // (200/3, 200/3).
        GeometryDefinition d = onePath(new GeometryPath(0, 0,
            GeometryPath.FillMode.NORM, true, List.of(
                new GeometryPath.MoveTo("0", "0"),
                new GeometryPath.QuadBezTo("50", "100", "100", "0"))));
        var segs = GeometryResolver.resolve(d, Map.of(), 200, 100).paths().get(0).segments();
        var c = (GeometryResolver.Cubic) segs.get(1);
        assertEquals(100.0 / 3, c.x1(), EPS);
        assertEquals(200.0 / 3, c.y1(), EPS);
        assertEquals(200.0 / 3, c.x2(), EPS);
        assertEquals(200.0 / 3, c.y2(), EPS);
        assertEquals(100.0, c.x3(), EPS);
        assertEquals(0.0, c.y3(), EPS);
    }

    @Test
    public void localSpaceScalesCurveControlPointsAnisotropically() {
        // 10x10 local space rendered at 200x100: sx=20, sy=10. Cubic
        // control points must scale per-axis, not uniformly.
        GeometryDefinition d = onePath(new GeometryPath(10, 10,
            GeometryPath.FillMode.NORM, true, List.of(
                new GeometryPath.MoveTo("0", "0"),
                new GeometryPath.CubicBezTo("1", "2", "3", "4", "5", "6"))));
        var segs = GeometryResolver.resolve(d, Map.of(), 200, 100).paths().get(0).segments();
        var c = (GeometryResolver.Cubic) segs.get(1);
        assertEquals(20.0, c.x1(), EPS);
        assertEquals(20.0, c.y1(), EPS);
        assertEquals(60.0, c.x2(), EPS);
        assertEquals(40.0, c.y2(), EPS);
        assertEquals(100.0, c.x3(), EPS);
        assertEquals(60.0, c.y3(), EPS);
    }

    @Test
    public void fillModeAndStrokeFlagSurviveResolution() {
        // actionButtonHome layers five paths with distinct fill modes and
        // stroke flags (pinned against the spec file in
        // RegistryCompletenessTest); the resolver must carry them through
        // unchanged and in order.
        var rg = GeometryResolver.resolve(
            PresetGeometryRegistry.get("actionButtonHome"), Map.of(), 200, 100);
        assertEquals(5, rg.paths().size());
        assertEquals(GeometryPath.FillMode.NORM, rg.paths().get(0).fill());
        assertFalse(rg.paths().get(0).stroked());
        assertEquals(GeometryPath.FillMode.DARKEN_LESS, rg.paths().get(1).fill());
        assertEquals(GeometryPath.FillMode.DARKEN, rg.paths().get(2).fill());
        assertEquals(GeometryPath.FillMode.NONE, rg.paths().get(3).fill());
        assertTrue(rg.paths().get(3).stroked());
        for (var p : rg.paths()) {
            assertFalse("path resolved empty", p.segments().isEmpty());
        }
    }

    @Test
    public void frameKeepsBothSubpathsInOnePath() {
        // frame = outer rect (clockwise) + inner rect (counter-clockwise)
        // in ONE path, so a non-zero winding fill punches the hole.
        // adj1=12500 at 200x100: x1 = ss*a1/100000 = 12.5; inner ring
        // (12.5,12.5) (12.5,87.5) (187.5,87.5) (187.5,12.5).
        var segs = GeometryResolver.resolve(
            PresetGeometryRegistry.get("frame"), Map.of(), 200, 100)
            .paths().get(0).segments();
        long moves = segs.stream().filter(s -> s instanceof GeometryResolver.Move).count();
        long closes = segs.stream().filter(s -> s instanceof GeometryResolver.Close).count();
        assertEquals(2, moves);
        assertEquals(2, closes);
        var innerStart = (GeometryResolver.Move) segs.get(5);
        assertEquals(12.5, innerStart.x(), EPS);
        assertEquals(12.5, innerStart.y(), EPS);
        // inner ring runs counter-clockwise (down first), opposite the
        // outer ring -- that opposition IS the hole.
        var innerSecond = (GeometryResolver.Line) segs.get(6);
        assertEquals(12.5, innerSecond.x(), EPS);
        assertEquals(87.5, innerSecond.y(), EPS);
    }

    @Test
    public void adjustOverridesFlowIntoPathCoordinates() {
        // homePlate adj=100000 at 200x100: a = pin(0, 100000, 200000) =
        // 100000; dx1 = ss*a/100000 = 100; x1 = r-dx1 = 100: the tip
        // consumes half the width instead of a quarter.
        var segs = GeometryResolver.resolve(
            PresetGeometryRegistry.get("homePlate"), Map.of("adj", 100000), 200, 100)
            .paths().get(0).segments();
        var v1 = (GeometryResolver.Line) segs.get(1);
        assertEquals(100.0, v1.x(), EPS);
        assertEquals(0.0, v1.y(), EPS);
    }
}
