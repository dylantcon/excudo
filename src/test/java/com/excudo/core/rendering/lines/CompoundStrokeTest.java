package com.excudo.core.rendering.lines;

import com.excudo.core.geometry.GeometryResolver.Close;
import com.excudo.core.geometry.GeometryResolver.Line;
import com.excudo.core.geometry.GeometryResolver.Move;
import com.excudo.core.geometry.GeometryResolver.Segment;
import org.junit.Test;

import java.awt.geom.Area;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Pins compound-line ring geometry to the truth PDF's flattened fills
 * (lines-dash-cap-join slide 2): dbl = equal thirds, thickThin = 3:1:1
 * outer-to-inner (measured 4.8/1.6/1.6pt bands on an 8pt line), rounded
 * outer corners of radius w/2.
 *
 * <p>Fixture: rect (100,100)-(400,300) at 16 px width. The left edge's
 * stroke band spans x in [92,108].
 */
public class CompoundStrokeTest {

    private static final List<Segment> RECT = List.of(
        new Move(100, 100), new Line(400, 100), new Line(400, 300),
        new Line(100, 300), new Close());

    @Test
    public void dblIsEqualThirds() {
        Area rings = CompoundStroke.rings(RECT, 16, "dbl");
        // ink [92, 97.33] and [102.67, 108]
        assertTrue(rings.contains(94, 200));
        assertFalse(rings.contains(100, 200));
        assertTrue(rings.contains(106, 200));
        assertFalse(rings.contains(250, 200)); // interior stays empty
        assertFalse(rings.contains(85, 200));
    }

    @Test
    public void dblOuterCornersAreRounded() {
        Area rings = CompoundStroke.rings(RECT, 16, "dbl");
        // outer boundary at the corner is a radius-8 arc centered (100,100)
        assertTrue(rings.contains(95, 95));   // dist 7.07 from the corner
        assertFalse(rings.contains(93.5, 93.5)); // dist 9.19 -- outside
    }

    @Test
    public void thickThinIsThreeToOneToOneOutward() {
        Area rings = CompoundStroke.rings(RECT, 16, "thickThin");
        // thick [92, 101.6], gap (101.6, 104.8), thin [104.8, 108]
        assertTrue(rings.contains(96, 200));
        assertTrue(rings.contains(100, 200)); // centerline inside the thick band
        assertFalse(rings.contains(103, 200));
        assertTrue(rings.contains(107, 200));
    }

    @Test
    public void thinThickMirrorsInward() {
        Area rings = CompoundStroke.rings(RECT, 16, "thinThick");
        // thin [92, 95.2], gap, thick [98.4, 108]
        assertTrue(rings.contains(93.5, 200));
        assertFalse(rings.contains(97, 200));
        assertTrue(rings.contains(100, 200));
        assertTrue(rings.contains(106, 200));
    }

    @Test
    public void asymmetricOpenPathIsUnsupported() {
        List<Segment> open = List.of(new Move(100, 300), new Line(400, 300));
        assertNull(CompoundStroke.rings(open, 16, "thickThin"));
    }

    @Test
    public void dblWorksOnOpenPaths() {
        List<Segment> open = List.of(new Move(100, 300), new Line(400, 300));
        Area rings = CompoundStroke.rings(open, 12, "dbl");
        // bands y in [294,298] and [302,306]
        assertTrue(rings.contains(250, 296));
        assertFalse(rings.contains(250, 300));
        assertTrue(rings.contains(250, 304));
    }

    @Test
    public void isCompoundIgnoresSingleAndEmpty() {
        assertFalse(CompoundStroke.isCompound(null));
        assertFalse(CompoundStroke.isCompound(""));
        assertFalse(CompoundStroke.isCompound("sng"));
        assertTrue(CompoundStroke.isCompound("dbl"));
        assertTrue(CompoundStroke.isCompound("thickThin"));
    }
}
