package com.excudo.core.rendering.lines;

import com.excudo.core.geometry.GeometryResolver.Close;
import com.excudo.core.geometry.GeometryResolver.Line;
import com.excudo.core.geometry.GeometryResolver.Move;
import com.excudo.core.geometry.GeometryResolver.Segment;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Pins arrowhead geometry to the truth-PDF calibration (arrowheads
 * corpus deck, 2.25pt lines). All expectations below are the PDF
 * measurements re-expressed in line widths -- see LineEndGeometry's
 * javadoc for the raw stream coordinates.
 */
public class LineEndGeometryTest {

    private static final double W = 4.0; // line width in px

    private static LineEnd end(LineEnd.Type type, LineEnd.Size size) {
        return new LineEnd(type, size, size);
    }

    // ========== trim lengths ==========

    @Test
    public void trimLengths() {
        // triangle: stroke stops w/2 short of the base (measured
        // 76.375 = 73 + len 4.5 - 1.125 at 2.25pt).
        assertEquals(6.0, LineEndGeometry.trimLength(
            end(LineEnd.Type.TRIANGLE, LineEnd.Size.SM), W), 1e-9);
        assertEquals(10.0, LineEndGeometry.trimLength(
            end(LineEnd.Type.TRIANGLE, LineEnd.Size.MED), W), 1e-9);
        assertEquals(18.0, LineEndGeometry.trimLength(
            end(LineEnd.Type.TRIANGLE, LineEnd.Size.LG), W), 1e-9);
        // stealth: stroke meets the notch (measured trim == notch depth
        // 1w/2w/3w for sm/med/lg).
        assertEquals(4.0, LineEndGeometry.trimLength(
            end(LineEnd.Type.STEALTH, LineEnd.Size.SM), W), 1e-9);
        assertEquals(8.0, LineEndGeometry.trimLength(
            end(LineEnd.Type.STEALTH, LineEnd.Size.MED), W), 1e-9);
        assertEquals(12.0, LineEndGeometry.trimLength(
            end(LineEnd.Type.STEALTH, LineEnd.Size.LG), W), 1e-9);
        // diamond/oval: centered on the endpoint, no trim.
        assertEquals(0.0, LineEndGeometry.trimLength(
            end(LineEnd.Type.DIAMOND, LineEnd.Size.LG), W), 1e-9);
        assertEquals(0.0, LineEndGeometry.trimLength(
            end(LineEnd.Type.OVAL, LineEnd.Size.MED), W), 1e-9);
        // arrow: stroke meets the arm vertex, inset (w/2)/sin(theta)
        // (measured vertex 371.20 for tip 369 at 2.25pt med:
        // 2.241 = 1.125 * hypot(3, 1.75) / 1.75; at w=4 that is 3.9694).
        assertEquals(2 * Math.hypot(12, 7) / 7, LineEndGeometry.trimLength(
            end(LineEnd.Type.ARROW, LineEnd.Size.MED), W), 1e-9);
        assertEquals(0.0, LineEndGeometry.trimLength(LineEnd.NONE, W), 1e-9);
    }

    // ========== polygon vertices (tail at (400,300), line to the left) ==========

    private static final double TX = 400, TY = 300;
    private static final double UX = -1, UY = 0; // into the line

    @Test
    public void triangleLgVertices() {
        LineEndGeometry.Decoration d = LineEndGeometry.build(
            end(LineEnd.Type.TRIANGLE, LineEnd.Size.LG), TX, TY, UX, UY, W);
        assertFalse(d.strokedArms());
        // 20 x 20: tip (400,300), base x=380 spanning y 290..310.
        List<Segment> s = d.outline();
        assertEquals(4, s.size());
        assertMove(s.get(0), 400, 300);
        assertLine(s.get(1), 380, 290); // +v side; v = perp(u) = (0,-1)
        assertLine(s.get(2), 380, 310);
        assertTrue(s.get(3) instanceof Close);
    }

    @Test
    public void stealthLgVertices() {
        LineEndGeometry.Decoration d = LineEndGeometry.build(
            end(LineEnd.Type.STEALTH, LineEnd.Size.LG), TX, TY, UX, UY, W);
        List<Segment> s = d.outline();
        assertEquals(5, s.size());
        assertMove(s.get(0), 400, 300);
        assertLine(s.get(1), 380, 290);
        assertLine(s.get(2), 388, 300); // notch 3w from the tip
        assertLine(s.get(3), 380, 310);
        assertTrue(s.get(4) instanceof Close);
    }

    @Test
    public void diamondMedIsCenteredOnTip() {
        LineEndGeometry.Decoration d = LineEndGeometry.build(
            end(LineEnd.Type.DIAMOND, LineEnd.Size.MED), TX, TY, UX, UY, W);
        List<Segment> s = d.outline();
        assertEquals(5, s.size());
        // 12 x 12 rhombus centered (400,300).
        assertMove(s.get(0), 406, 300);
        assertLine(s.get(1), 400, 294);
        assertLine(s.get(2), 394, 300);
        assertLine(s.get(3), 400, 306);
    }

    @Test
    public void ovalSmBoundsAreTwoWidthsCentered() {
        LineEndGeometry.Decoration d = LineEndGeometry.build(
            end(LineEnd.Type.OVAL, LineEnd.Size.SM), TX, TY, UX, UY, W);
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (Segment seg : d.outline()) {
            double[] pts = switch (seg) {
                case Move m -> new double[]{m.x(), m.y()};
                case Line l -> new double[]{l.x(), l.y()};
                case com.excudo.core.geometry.GeometryResolver.Cubic c ->
                    new double[]{c.x3(), c.y3()};
                default -> new double[0];
            };
            for (int i = 0; i + 1 < pts.length; i += 2) {
                minX = Math.min(minX, pts[i]); maxX = Math.max(maxX, pts[i]);
                minY = Math.min(minY, pts[i + 1]); maxY = Math.max(maxY, pts[i + 1]);
            }
        }
        // On-curve extremes of the 8 x 8 disc centered (400,300).
        assertEquals(396, minX, 1e-9);
        assertEquals(404, maxX, 1e-9);
        assertEquals(296, minY, 1e-9);
        assertEquals(304, maxY, 1e-9);
    }

    @Test
    public void arrowMedArmsMeetAtTheInsetVertex() {
        LineEndGeometry.Decoration d = LineEndGeometry.build(
            end(LineEnd.Type.ARROW, LineEnd.Size.MED), TX, TY, UX, UY, W);
        assertTrue(d.strokedArms());
        List<Segment> s = d.outline();
        assertEquals(3, s.size());
        double t0 = 2 * Math.hypot(12, 7) / 7;
        double vx = 400 - t0;
        // arms reach 12 px along and 7 px across from the vertex
        assertMove(s.get(0), vx - 12, 300 - 7);
        assertLine(s.get(1), vx, 300);
        assertLine(s.get(2), vx - 12, 300 + 7);
    }

    @Test
    public void noneBuildsNothing() {
        assertNull(LineEndGeometry.build(LineEnd.NONE, TX, TY, UX, UY, W));
    }

    private static void assertMove(Segment s, double x, double y) {
        assertTrue("expected Move, got " + s, s instanceof Move);
        assertEquals(x, ((Move) s).x(), 1e-9);
        assertEquals(y, ((Move) s).y(), 1e-9);
    }

    private static void assertLine(Segment s, double x, double y) {
        assertTrue("expected Line, got " + s, s instanceof Line);
        assertEquals(x, ((Line) s).x(), 1e-9);
        assertEquals(y, ((Line) s).y(), 1e-9);
    }
}
