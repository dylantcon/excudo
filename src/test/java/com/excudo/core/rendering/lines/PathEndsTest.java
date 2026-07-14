package com.excudo.core.rendering.lines;

import com.excudo.core.geometry.GeometryResolver.Close;
import com.excudo.core.geometry.GeometryResolver.Cubic;
import com.excudo.core.geometry.GeometryResolver.Line;
import com.excudo.core.geometry.GeometryResolver.Move;
import com.excudo.core.geometry.GeometryResolver.Segment;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Pins end-attachment mechanics: tangent extraction, stroke trimming
 * (PowerPoint trims at the head base / notch / vertex so the stroke
 * never pokes through an open head), and the closed-subpath guard.
 */
public class PathEndsTest {

    private static final double W = 4.0;

    private static final LineEnd TRIANGLE_LG =
        new LineEnd(LineEnd.Type.TRIANGLE, LineEnd.Size.LG, LineEnd.Size.LG);

    @Test
    public void tailTrimShortensTheLastSegment() {
        List<Segment> path = List.of(new Move(100, 300), new Line(400, 300));
        PathEnds.Applied applied = PathEnds.apply(path, null, TRIANGLE_LG, W);

        assertNotNull(applied.tail());
        assertNull(applied.head());
        // triangle lg trim = 5w - w/2 = 18: stroke now ends at x=382.
        assertEquals(2, applied.segments().size());
        Line lastLine = (Line) applied.segments().get(1);
        assertEquals(382, lastLine.x(), 1e-9);
        assertEquals(300, lastLine.y(), 1e-9);
        // decoration tip anchored at the original endpoint
        Move tip = (Move) applied.tail().outline().get(0);
        assertEquals(400, tip.x(), 1e-9);
        assertEquals(300, tip.y(), 1e-9);
    }

    @Test
    public void headTrimMovesTheStart() {
        List<Segment> path = List.of(new Move(100, 300), new Line(400, 300));
        PathEnds.Applied applied = PathEnds.apply(path, TRIANGLE_LG, null, W);

        Move start = (Move) applied.segments().get(0);
        assertEquals(118, start.x(), 1e-9);
        assertEquals(300, start.y(), 1e-9);
        Move tip = (Move) applied.head().outline().get(0);
        assertEquals(100, tip.x(), 1e-9);
    }

    @Test
    public void trimConsumesWholeSegmentsAcrossAPolyline() {
        // 10 + 10 px legs; trim 18 leaves 2 px of the second leg.
        List<Segment> path = List.of(
            new Move(0, 0), new Line(10, 0), new Line(10, 10));
        List<Segment> trimmed = PathEnds.trimStart(path, 18);
        assertEquals(2, trimmed.size());
        Move start = (Move) trimmed.get(0);
        assertEquals(10, start.x(), 1e-9);
        assertEquals(8, start.y(), 1e-9);
        assertEquals(10, ((Line) trimmed.get(1)).y(), 1e-9);
    }

    @Test
    public void cubicTrimSplitsAtArcLength() {
        // Flat cubic along y=0 from x=0..90 (nonuniform parameter speed).
        Cubic c = new Cubic(30, 0, 60, 0, 90, 0);
        List<Segment> trimmed = PathEnds.trimStart(List.of(new Move(0, 0), c), 45);
        Move start = (Move) trimmed.get(0);
        assertEquals(45, start.x(), 0.5); // flattening tolerance
        assertEquals(0, start.y(), 1e-9);
        assertEquals(90, ((Cubic) trimmed.get(1)).x3(), 1e-9);
    }

    @Test
    public void tangentsFollowTheCurveControlPoints() {
        // curvedConnector-style start: first control point is horizontal.
        List<Segment> path = List.of(new Move(100, 100),
            new Cubic(200, 100, 300, 150, 300, 200));
        double[] t = PathEnds.startTangent(path);
        assertEquals(1, t[0], 1e-9);
        assertEquals(0, t[1], 1e-9);
    }

    @Test
    public void closedSubpathsTakeNoEnds() {
        List<Segment> rect = List.of(new Move(0, 0), new Line(10, 0),
            new Line(10, 10), new Line(0, 10), new Close());
        PathEnds.Applied applied = PathEnds.apply(rect, TRIANGLE_LG, TRIANGLE_LG, W);
        assertNull(applied.head());
        assertNull(applied.tail());
        assertEquals(rect, applied.segments());
    }

    @Test
    public void overTrimCollapsesToNothing() {
        List<Segment> path = List.of(new Move(0, 0), new Line(10, 0));
        PathEnds.Applied applied = PathEnds.apply(path, null, TRIANGLE_LG, 10);
        // trim 45 > length 10: nothing left to stroke, decoration remains
        assertTrue(applied.segments().isEmpty()
            || applied.segments().stream().allMatch(s -> s instanceof Move));
        assertNotNull(applied.tail());
    }
}
