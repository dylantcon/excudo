package com.excudo.core.rendering.lines;

import com.excudo.core.geometry.GeometryResolver.Close;
import com.excudo.core.geometry.GeometryResolver.Cubic;
import com.excudo.core.geometry.GeometryResolver.Line;
import com.excudo.core.geometry.GeometryResolver.Move;
import com.excudo.core.geometry.GeometryResolver.Segment;

import java.awt.BasicStroke;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.util.List;

/**
 * OOXML compound lines ({@code a:ln/@cmpd}) as filled ring geometry.
 * PowerPoint's PDF export flattens compound strokes into ring FILLS
 * (parity-corpus lines-dash-cap-join slide 2): an 8pt {@code dbl} rect
 * paints two 8/3pt bands separated by an 8/3pt gap, and
 * {@code thickThin} paints 3:1:1 outer-to-inner (measured 4.8/1.6/1.6pt
 * at 8pt). The outermost boundary carries round-join corners (radius
 * w/2 in the truth streams), which the round-join stroke below produces
 * naturally.
 *
 * <p>Band construction uses signed-offset regions: {@code G(t)} is the
 * area bounded by the offset of the path at signed distance {@code t}
 * (positive outward), computed from the auto-closed fill area plus/minus
 * a symmetric stroke band. A band between offsets {@code hi > lo} is
 * {@code G(hi) - G(lo)}. This is exact for closed subpaths; symmetric
 * layouts (dbl, tri) reduce to stroke-area differences and work for open
 * paths too.
 */
public final class CompoundStroke {

    private CompoundStroke() {}

    /** True for any authored cmpd other than the single-line default. */
    public static boolean isCompound(String cmpd) {
        return cmpd != null && !cmpd.isEmpty() && !"sng".equals(cmpd);
    }

    /**
     * Ring areas for a compound stroke, to FILL with the line color, or
     * null when this cmpd/path combination is unsupported (the caller
     * falls back to a single solid stroke): asymmetric layouts
     * (thickThin/thinThick) require closed subpaths to orient their
     * thick side outward.
     */
    public static Area rings(List<Segment> segments, double w, String cmpd) {
        Path2D.Double path = toPath(segments);
        switch (cmpd) {
            case "dbl":
                // equal thirds: ink [w/6, w/2] both sides of the center
                return subtract(strokeArea(path, w), strokeArea(path, w / 3));
            case "tri": {
                // thin-thick-thin 2:1:2:1:2 (uncalibrated -- no corpus
                // deck exercises tri; symmetric, so open paths work)
                Area outer = subtract(strokeArea(path, w), strokeArea(path, w * 0.75));
                outer.add(strokeArea(path, w * 0.25));
                return outer;
            }
            case "thickThin":
            case "thinThick": {
                if (!allClosed(segments)) return null;
                Area fill = new Area(path);
                boolean thickOutside = "thickThin".equals(cmpd);
                // Bands outer->inner: 0.6/0.2/0.2 of w (PDF-measured 3:1:1).
                double b0 = 0.5 * w;
                double b1 = thickOutside ? -0.1 * w : 0.3 * w;
                double b2 = thickOutside ? -0.3 * w : 0.1 * w;
                double b3 = -0.5 * w;
                Area rings = subtract(offsetRegion(path, fill, b0), offsetRegion(path, fill, b1));
                rings.add(subtract(offsetRegion(path, fill, b2), offsetRegion(path, fill, b3)));
                return rings;
            }
            default:
                return null;
        }
    }

    /** Area bounded by the path offset at signed distance t (+ = outward). */
    private static Area offsetRegion(Path2D path, Area fill, double t) {
        Area out = new Area(fill);
        if (t > 1e-9) {
            out.add(strokeArea(path, 2 * t));
        } else if (t < -1e-9) {
            out.subtract(strokeArea(path, -2 * t));
        }
        return out;
    }

    private static Area strokeArea(Path2D path, double width) {
        BasicStroke stroke = new BasicStroke((float) width,
            BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND);
        return new Area(stroke.createStrokedShape(path));
    }

    private static Area subtract(Area a, Area b) {
        a.subtract(b);
        return a;
    }

    private static boolean allClosed(List<Segment> segments) {
        boolean sawSubpath = false;
        boolean closed = true;
        for (Segment seg : segments) {
            if (seg instanceof Move) {
                if (sawSubpath && !closed) return false;
                sawSubpath = true;
                closed = false;
            } else if (seg instanceof Close) {
                closed = true;
            }
        }
        return sawSubpath && closed;
    }

    private static Path2D.Double toPath(List<Segment> segments) {
        Path2D.Double path = new Path2D.Double();
        for (Segment seg : segments) {
            switch (seg) {
                case Move m -> path.moveTo(m.x(), m.y());
                case Line l -> path.lineTo(l.x(), l.y());
                case Cubic c -> path.curveTo(c.x1(), c.y1(), c.x2(), c.y2(), c.x3(), c.y3());
                case Close ignored -> path.closePath();
            }
        }
        return path;
    }
}
