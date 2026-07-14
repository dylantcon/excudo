package com.excudo.core.rendering.lines;

import com.excudo.core.geometry.GeometryResolver.Close;
import com.excudo.core.geometry.GeometryResolver.Cubic;
import com.excudo.core.geometry.GeometryResolver.Line;
import com.excudo.core.geometry.GeometryResolver.Move;
import com.excudo.core.geometry.GeometryResolver.Segment;

import java.util.ArrayList;
import java.util.List;

/**
 * Attaches {@link LineEnd} decorations to a resolved open path: computes
 * the end tangents, builds the arrowhead geometry via
 * {@link LineEndGeometry}, and trims the stroke back so it does not poke
 * through open heads (PowerPoint trims to the triangle base minus w/2,
 * the stealth notch, and the open-arrow vertex -- see the calibration
 * notes on {@link LineEndGeometry}).
 *
 * <p>The head attaches at the start of the FIRST subpath, the tail at
 * the end of the LAST subpath (ECMA-376 attaches ends to the line's
 * begin/end). Closed subpaths take no decoration.
 */
public final class PathEnds {

    private PathEnds() {}

    /** Cubic flattening steps for arc-length estimates. */
    private static final int FLATTEN_STEPS = 32;

    /** Trimmed stroke plus the decorations to draw with the line color. */
    public record Applied(List<Segment> segments, LineEndGeometry.Decoration head,
                          LineEndGeometry.Decoration tail) {}

    /**
     * Apply head/tail ends to a stroked path's segment list. Returns the
     * input unchanged (no decorations) when both ends are none or the
     * relevant subpath is closed/degenerate.
     */
    public static Applied apply(List<Segment> segments, LineEnd head, LineEnd tail, double w) {
        if ((head == null || head.isNone()) && (tail == null || tail.isNone())) {
            return new Applied(segments, null, null);
        }
        List<List<Segment>> subpaths = split(segments);
        if (subpaths.isEmpty()) return new Applied(segments, null, null);

        LineEndGeometry.Decoration headDecor = null;
        LineEndGeometry.Decoration tailDecor = null;

        List<Segment> first = subpaths.get(0);
        if (head != null && !head.isNone() && isOpen(first)) {
            double[] tangent = startTangent(first);
            if (tangent != null) {
                Move m = (Move) first.get(0);
                headDecor = LineEndGeometry.build(head, m.x(), m.y(),
                    tangent[0], tangent[1], w);
                double trim = LineEndGeometry.trimLength(head, w);
                if (trim > 0) {
                    subpaths.set(0, trimStart(first, trim));
                }
            }
        }
        List<Segment> last = subpaths.get(subpaths.size() - 1);
        if (tail != null && !tail.isNone() && isOpen(last)) {
            List<Segment> reversed = reverse(last);
            double[] tangent = startTangent(reversed);
            if (tangent != null) {
                Move m = (Move) reversed.get(0);
                tailDecor = LineEndGeometry.build(tail, m.x(), m.y(),
                    tangent[0], tangent[1], w);
                double trim = LineEndGeometry.trimLength(tail, w);
                if (trim > 0) {
                    subpaths.set(subpaths.size() - 1, reverse(trimStart(reversed, trim)));
                }
            }
        }

        List<Segment> out = new ArrayList<>(segments.size());
        for (List<Segment> sub : subpaths) {
            // A fully-consumed subpath is just its Move -- nothing strokes.
            if (sub.size() > 1) out.addAll(sub);
        }
        return new Applied(List.copyOf(out), headDecor, tailDecor);
    }

    // ========== subpath plumbing ==========

    /** Split a segment list at its Moves. Every subpath starts with one. */
    private static List<List<Segment>> split(List<Segment> segments) {
        List<List<Segment>> out = new ArrayList<>();
        List<Segment> cur = null;
        for (Segment seg : segments) {
            if (seg instanceof Move) {
                cur = new ArrayList<>();
                out.add(cur);
            }
            if (cur == null) {
                throw new IllegalArgumentException("path does not start with a moveTo");
            }
            cur.add(seg);
        }
        return out;
    }

    private static boolean isOpen(List<Segment> subpath) {
        return subpath.stream().noneMatch(s -> s instanceof Close);
    }

    /**
     * Unit direction of travel at the subpath start, or null when every
     * candidate point coincides with the start.
     */
    static double[] startTangent(List<Segment> subpath) {
        Move m = (Move) subpath.get(0);
        for (int i = 1; i < subpath.size(); i++) {
            double[] candidates = switch (subpath.get(i)) {
                case Line l -> new double[]{l.x(), l.y()};
                case Cubic c -> new double[]{c.x1(), c.y1(), c.x2(), c.y2(), c.x3(), c.y3()};
                case Move m2 -> null;
                case Close ignored -> null;
            };
            if (candidates == null) break;
            for (int j = 0; j < candidates.length; j += 2) {
                double dx = candidates[j] - m.x(), dy = candidates[j + 1] - m.y();
                double n = Math.hypot(dx, dy);
                if (n > 1e-9) return new double[]{dx / n, dy / n};
            }
        }
        return null;
    }

    /** Reverse an open subpath (Move + Line/Cubic segments only). */
    static List<Segment> reverse(List<Segment> subpath) {
        List<double[]> starts = new ArrayList<>(); // start point of each segment
        double curX = 0, curY = 0;
        List<Segment> body = new ArrayList<>();
        for (Segment seg : subpath) {
            switch (seg) {
                case Move m -> { curX = m.x(); curY = m.y(); }
                case Line l -> {
                    starts.add(new double[]{curX, curY});
                    body.add(l);
                    curX = l.x(); curY = l.y();
                }
                case Cubic c -> {
                    starts.add(new double[]{curX, curY});
                    body.add(c);
                    curX = c.x3(); curY = c.y3();
                }
                case Close ignored -> throw new IllegalArgumentException(
                    "cannot reverse a closed subpath");
            }
        }
        List<Segment> out = new ArrayList<>(subpath.size());
        out.add(new Move(curX, curY));
        for (int i = body.size() - 1; i >= 0; i--) {
            double[] start = starts.get(i);
            switch (body.get(i)) {
                case Line ignored -> out.add(new Line(start[0], start[1]));
                case Cubic c -> out.add(new Cubic(c.x2(), c.y2(), c.x1(), c.y1(),
                    start[0], start[1]));
                default -> throw new IllegalStateException();
            }
        }
        return out;
    }

    /**
     * Drop the first {@code d} units of arc length from an open subpath.
     * Cubic lengths are estimated by {@value FLATTEN_STEPS}-step
     * flattening and split with de Casteljau. Over-trimming collapses
     * the subpath to its Move (strokes nothing).
     */
    static List<Segment> trimStart(List<Segment> subpath, double d) {
        Move m = (Move) subpath.get(0);
        double curX = m.x(), curY = m.y();
        for (int i = 1; i < subpath.size(); i++) {
            switch (subpath.get(i)) {
                case Line l -> {
                    double len = Math.hypot(l.x() - curX, l.y() - curY);
                    if (len <= d + 1e-9) {
                        d -= len;
                        curX = l.x(); curY = l.y();
                    } else {
                        double t = d / len;
                        List<Segment> out = new ArrayList<>(subpath.size() - i + 1);
                        out.add(new Move(curX + t * (l.x() - curX), curY + t * (l.y() - curY)));
                        out.addAll(subpath.subList(i, subpath.size()));
                        return out;
                    }
                }
                case Cubic c -> {
                    double[] lengths = flattenLengths(curX, curY, c);
                    double total = lengths[FLATTEN_STEPS];
                    if (total <= d + 1e-9) {
                        d -= total;
                        curX = c.x3(); curY = c.y3();
                    } else {
                        double t = paramAtLength(lengths, d);
                        Cubic secondHalf = splitAfter(c, t);
                        List<Segment> out = new ArrayList<>(subpath.size() - i + 1);
                        // The split point is the second half's implicit start.
                        double[] p = pointAt(curX, curY, c, t);
                        out.add(new Move(p[0], p[1]));
                        out.add(secondHalf);
                        out.addAll(subpath.subList(i + 1, subpath.size()));
                        return out;
                    }
                }
                default -> {
                    return subpath; // Move/Close mid-subpath: leave untrimmed
                }
            }
        }
        return List.of(new Move(curX, curY)); // fully consumed
    }

    private static double[] flattenLengths(double x0, double y0, Cubic c) {
        double[] cum = new double[FLATTEN_STEPS + 1];
        double px = x0, py = y0;
        for (int i = 1; i <= FLATTEN_STEPS; i++) {
            double[] p = pointAt(x0, y0, c, (double) i / FLATTEN_STEPS);
            cum[i] = cum[i - 1] + Math.hypot(p[0] - px, p[1] - py);
            px = p[0]; py = p[1];
        }
        return cum;
    }

    private static double paramAtLength(double[] cum, double d) {
        for (int i = 1; i <= FLATTEN_STEPS; i++) {
            if (cum[i] >= d) {
                double span = cum[i] - cum[i - 1];
                double f = span > 1e-12 ? (d - cum[i - 1]) / span : 0;
                return (i - 1 + f) / FLATTEN_STEPS;
            }
        }
        return 1;
    }

    private static double[] pointAt(double x0, double y0, Cubic c, double t) {
        double s = 1 - t;
        double x = s * s * s * x0 + 3 * s * s * t * c.x1() + 3 * s * t * t * c.x2()
            + t * t * t * c.x3();
        double y = s * s * s * y0 + 3 * s * s * t * c.y1() + 3 * s * t * t * c.y2()
            + t * t * t * c.y3();
        return new double[]{x, y};
    }

    /** De Casteljau: the [t, 1] portion of the cubic as a new cubic. */
    private static Cubic splitAfter(Cubic c, double t) {
        double bx = lerp(c.x1(), c.x2(), t),  by = lerp(c.y1(), c.y2(), t);
        double cx = lerp(c.x2(), c.x3(), t),  cy = lerp(c.y2(), c.y3(), t);
        double bcx = lerp(bx, cx, t),         bcy = lerp(by, cy, t);
        // start point P(t) is implicit (the caller emits it as a Move);
        // the [t,1] half's controls are BC, C, end.
        return new Cubic(bcx, bcy, cx, cy, c.x3(), c.y3());
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
