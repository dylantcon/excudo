package com.excudo.core.rendering.lines;

import com.excudo.core.geometry.GeometryResolver.Close;
import com.excudo.core.geometry.GeometryResolver.Cubic;
import com.excudo.core.geometry.GeometryResolver.Line;
import com.excudo.core.geometry.GeometryResolver.Move;
import com.excudo.core.geometry.GeometryResolver.Segment;

import java.util.ArrayList;
import java.util.List;

/**
 * Arrowhead geometry, calibrated against PowerPoint's PDF export of the
 * arrowheads corpus deck (2.25pt lines, every type at sm/med/lg). All
 * dimensions are multiples of the line width {@code w}:
 *
 * <ul>
 *   <li>triangle/stealth/diamond/oval span {@code len x width} of
 *       {@code {sm:2, med:3, lg:5}} w each way; triangle and stealth
 *       anchor their tip AT the endpoint pointing outward, diamond and
 *       oval are CENTERED on the endpoint.</li>
 *   <li>stealth's back notch sits {@code {sm:1, med:2, lg:3}} w from the
 *       tip (measured: notch x = 75.25/373.5/671.75 for tips at
 *       73/369/665 on 2.25pt lines).</li>
 *   <li>the open {@code arrow} is two arms stroked at the line width
 *       with round caps; the arm cap centers sit {@code {2, 3, 4.5}} w
 *       along and {@code {1.25, 1.75, 2.5}} w across from the vertex,
 *       and the vertex is inset so the arm INK tip touches the endpoint:
 *       {@code (w/2)/sin(theta)} (measured vertices 75.116/371.20/667.29
 *       coincide with PowerPoint's trimmed stroke starts).</li>
 * </ul>
 *
 * PowerPoint trims the stroke so it does not poke through open heads:
 * triangle to {@code len - w/2} (measured 76.375 = 73 + 4.5 - 1.125),
 * stealth to the notch, arrow to the vertex; diamond and oval overlap
 * the untrimmed stroke.
 */
public final class LineEndGeometry {

    private LineEndGeometry() {}

    /** Cubic circle constant. */
    private static final double KAPPA = 0.5522847498307936;

    /**
     * A drawable decoration at one path end. {@code outline} is a closed
     * polygon/ellipse to FILL with the line color unless
     * {@code strokedArms} -- then it is an open V to STROKE at the line
     * width with round caps and joins.
     */
    public record Decoration(List<Segment> outline, boolean strokedArms) {}

    /** len/width multiplier per size class (PDF-measured). */
    private static double sizeMult(LineEnd.Size size) {
        return switch (size) {
            case SM -> 2;
            case MED -> 3;
            case LG -> 5;
        };
    }

    /** Stealth notch depth in line widths, by length class (PDF-measured). */
    private static double stealthNotch(LineEnd.Size len) {
        return switch (len) {
            case SM -> 1;
            case MED -> 2;
            case LG -> 3;
        };
    }

    /** Open-arrow arm reach along the line axis, by length class. */
    private static double arrowAlong(LineEnd.Size len) {
        return switch (len) {
            case SM -> 2.0;
            case MED -> 3.0;
            case LG -> 4.5;
        };
    }

    /** Open-arrow arm spread across the line axis, by width class. */
    private static double arrowAcross(LineEnd.Size width) {
        return switch (width) {
            case SM -> 1.25;
            case MED -> 1.75;
            case LG -> 2.5;
        };
    }

    /**
     * How far the stroke retreats from the endpoint so it does not poke
     * through the head. Zero for none/diamond/oval.
     */
    public static double trimLength(LineEnd end, double w) {
        return switch (end.type()) {
            case NONE, DIAMOND, OVAL -> 0;
            case TRIANGLE -> sizeMult(end.length()) * w - w / 2;
            case STEALTH -> stealthNotch(end.length()) * w;
            case ARROW -> {
                double along = arrowAlong(end.length());
                double across = arrowAcross(end.width());
                // (w/2) / sin(theta), theta = arm angle off the axis
                yield (w / 2) * Math.hypot(along, across) / across;
            }
        };
    }

    /**
     * Build the decoration for a line end. {@code (tipX, tipY)} is the
     * path endpoint; {@code (ux, uy)} the unit tangent pointing from the
     * endpoint INTO the line; {@code w} the line width. Null for NONE.
     */
    public static Decoration build(LineEnd end, double tipX, double tipY,
                                   double ux, double uy, double w) {
        if (end.isNone()) return null;
        double vx = -uy, vy = ux; // perpendicular
        double lenPx = sizeMult(end.length()) * w;
        double halfW = sizeMult(end.width()) * w / 2;

        List<Segment> out = new ArrayList<>();
        switch (end.type()) {
            case TRIANGLE -> {
                out.add(new Move(tipX, tipY));
                out.add(new Line(tipX + lenPx * ux + halfW * vx, tipY + lenPx * uy + halfW * vy));
                out.add(new Line(tipX + lenPx * ux - halfW * vx, tipY + lenPx * uy - halfW * vy));
                out.add(new Close());
            }
            case STEALTH -> {
                double notch = stealthNotch(end.length()) * w;
                out.add(new Move(tipX, tipY));
                out.add(new Line(tipX + lenPx * ux + halfW * vx, tipY + lenPx * uy + halfW * vy));
                out.add(new Line(tipX + notch * ux, tipY + notch * uy));
                out.add(new Line(tipX + lenPx * ux - halfW * vx, tipY + lenPx * uy - halfW * vy));
                out.add(new Close());
            }
            case DIAMOND -> {
                double halfL = lenPx / 2;
                out.add(new Move(tipX - halfL * ux, tipY - halfL * uy));
                out.add(new Line(tipX + halfW * vx, tipY + halfW * vy));
                out.add(new Line(tipX + halfL * ux, tipY + halfL * uy));
                out.add(new Line(tipX - halfW * vx, tipY - halfW * vy));
                out.add(new Close());
            }
            case OVAL -> ellipse(out, tipX, tipY, lenPx / 2, halfW, ux, uy, vx, vy);
            case ARROW -> {
                double along = arrowAlong(end.length()) * w;
                double across = arrowAcross(end.width()) * w;
                double t0 = trimLength(end, w);
                double vX = tipX + t0 * ux, vY = tipY + t0 * uy;
                out.add(new Move(vX + along * ux + across * vx, vY + along * uy + across * vy));
                out.add(new Line(vX, vY));
                out.add(new Line(vX + along * ux - across * vx, vY + along * uy - across * vy));
                return new Decoration(List.copyOf(out), true);
            }
            case NONE -> throw new IllegalStateException(); // handled above
        }
        return new Decoration(List.copyOf(out), false);
    }

    /**
     * Axis-aligned-in-(u,v) ellipse centered on the tip, semi-axes
     * {@code a} along u and {@code b} along v, as four cubics.
     */
    private static void ellipse(List<Segment> out, double cx, double cy,
                                double a, double b,
                                double ux, double uy, double vx, double vy) {
        double ka = KAPPA * a, kb = KAPPA * b;
        out.add(new Move(cx + a * ux, cy + a * uy));
        out.add(new Cubic(cx + a * ux + kb * vx, cy + a * uy + kb * vy,
                          cx + ka * ux + b * vx, cy + ka * uy + b * vy,
                          cx + b * vx, cy + b * vy));
        out.add(new Cubic(cx - ka * ux + b * vx, cy - ka * uy + b * vy,
                          cx - a * ux + kb * vx, cy - a * uy + kb * vy,
                          cx - a * ux, cy - a * uy));
        out.add(new Cubic(cx - a * ux - kb * vx, cy - a * uy - kb * vy,
                          cx - ka * ux - b * vx, cy - ka * uy - b * vy,
                          cx - b * vx, cy - b * vy));
        out.add(new Cubic(cx + ka * ux - b * vx, cy + ka * uy - b * vy,
                          cx + a * ux - kb * vx, cy + a * uy - kb * vy,
                          cx + a * ux, cy + a * uy));
        out.add(new Close());
    }
}
