package com.excudo.core.geometry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Evaluates a {@link GeometryDefinition} against concrete shape extents
 * into device-space outlines: runs {@link GuideEvaluator} over the
 * adjust values and guides, resolves every path command's tokens, scales
 * path-local coordinate spaces, and converts {@code arcTo} commands to
 * cubic B&eacute;ziers. The output is backend-neutral -- a flat segment
 * list per path -- so both render surfaces replay it identically.
 *
 * <h2>Units</h2>
 * {@code w}/{@code h} are whatever unit the renderer draws in (pixels);
 * all emitted coordinates are in that unit, relative to the shape's
 * top-left corner.
 *
 * <h2>Path-local coordinate spaces</h2>
 * A path with {@code @w}/{@code @h} declares its literal coordinates in
 * a local space of that size; every resolved coordinate in the path is
 * scaled by {@code shapeW/pathW, shapeH/pathH}. Absent (0) axes scale
 * 1:1 -- those paths use guide-space values, which are already device
 * values because the guides were evaluated at device extents. Angle
 * tokens are never scaled. (The vendored spec definitions reference only
 * angle constants from inside {@code w/h}-scoped paths, so the two
 * readings of ECMA-376 &sect;20.1.9.15 -- scale-everything vs
 * scale-literals-only -- agree on the entire catalogue.)
 *
 * <h2>arcTo semantics (&sect;20.1.9.4, PowerPoint-verified)</h2>
 * {@code stAng}/{@code swAng} are 60000ths of a degree measured y-down
 * (0 at 3 o'clock, positive clockwise on screen). OOXML arc angles are
 * <em>ray</em> angles, not ellipse parameter angles: the point named by
 * angle &theta; is where the ray from the ellipse center at slope
 * &theta; crosses the ellipse. The parameter angle is
 * {@code t = atan2(wR sin θ, hR cos θ)} -- the same "atan then scale"
 * skew the spec's own pie/chord/arc guide formulas spell out via
 * {@code cat2}/{@code sat2}. The arc continues from the current point,
 * which by construction sits at {@code stAng}: the center is
 * {@code current - (wR cos t0, hR sin t0)}. Sweeps convert per-quadrant
 * (quarter-turn boundaries map exactly) and are emitted as cubic
 * B&eacute;ziers in &le;90&deg; parameter steps with the standard
 * {@code k = 4/3 tan(Δt/4)} tangent lengths (max radial error
 * ~2.7e-4 of the radius).
 */
public final class GeometryResolver {

    private GeometryResolver() {}

    /** 60000ths-of-a-degree per radian (same constant as GuideEvaluator). */
    private static final double ANGLE_UNITS_PER_RADIAN = 60000.0 * 180.0 / Math.PI;

    // ========== output model ==========

    /** One device-space path segment. Coordinates are absolute. */
    public sealed interface Segment permits Move, Line, Cubic, Close {}

    public record Move(double x, double y) implements Segment {}

    public record Line(double x, double y) implements Segment {}

    public record Cubic(double x1, double y1, double x2, double y2,
                        double x3, double y3) implements Segment {}

    public record Close() implements Segment {}

    /**
     * One resolved path: fill mode and stroke flag carried through from
     * the definition so the renderer can apply lighten/darken fills and
     * skip stroking unstroked paths.
     */
    public record ResolvedPath(GeometryPath.FillMode fill, boolean stroked,
                               List<Segment> segments) {}

    /** All of a shape's resolved paths, in definition order. */
    public record ResolvedGeometry(List<ResolvedPath> paths) {}

    // ========== resolution ==========

    /**
     * Resolve {@code def} for a shape box of {@code w} x {@code h},
     * with the shape's own {@code avLst} overrides (may be null/empty).
     */
    public static ResolvedGeometry resolve(GeometryDefinition def,
                                           Map<String, Integer> adjustValues,
                                           double w, double h) {
        Map<String, Double> env = GuideEvaluator.evaluate(def, adjustValues, w, h);
        List<ResolvedPath> paths = new ArrayList<>(def.getPaths().size());
        for (GeometryPath path : def.getPaths()) {
            paths.add(resolvePath(path, env, w, h));
        }
        return new ResolvedGeometry(paths);
    }

    private static ResolvedPath resolvePath(GeometryPath path, Map<String, Double> env,
                                            double w, double h) {
        double sx = path.getWidth() > 0 ? w / path.getWidth() : 1.0;
        double sy = path.getHeight() > 0 ? h / path.getHeight() : 1.0;

        List<Segment> segments = new ArrayList<>(path.getCommands().size());
        // Current point and subpath start, in the path's LOCAL space.
        // Arc math runs in local space (the θ ray lives there); emitted
        // coordinates are scaled on the way out, which is exact for
        // Bézier control points under an affine map.
        double curX = 0, curY = 0, startX = 0, startY = 0;
        boolean hasCurrent = false;

        for (GeometryPath.Command cmd : path.getCommands()) {
            switch (cmd) {
                case GeometryPath.MoveTo m -> {
                    curX = GuideEvaluator.resolve(m.x(), env);
                    curY = GuideEvaluator.resolve(m.y(), env);
                    startX = curX;
                    startY = curY;
                    hasCurrent = true;
                    segments.add(new Move(curX * sx, curY * sy));
                }
                case GeometryPath.LnTo l -> {
                    requireCurrent(hasCurrent, "lnTo");
                    curX = GuideEvaluator.resolve(l.x(), env);
                    curY = GuideEvaluator.resolve(l.y(), env);
                    segments.add(new Line(curX * sx, curY * sy));
                }
                case GeometryPath.CubicBezTo c -> {
                    requireCurrent(hasCurrent, "cubicBezTo");
                    double x1 = GuideEvaluator.resolve(c.x1(), env);
                    double y1 = GuideEvaluator.resolve(c.y1(), env);
                    double x2 = GuideEvaluator.resolve(c.x2(), env);
                    double y2 = GuideEvaluator.resolve(c.y2(), env);
                    curX = GuideEvaluator.resolve(c.x3(), env);
                    curY = GuideEvaluator.resolve(c.y3(), env);
                    segments.add(new Cubic(x1 * sx, y1 * sy, x2 * sx, y2 * sy,
                        curX * sx, curY * sy));
                }
                case GeometryPath.QuadBezTo q -> {
                    requireCurrent(hasCurrent, "quadBezTo");
                    double qx = GuideEvaluator.resolve(q.x1(), env);
                    double qy = GuideEvaluator.resolve(q.y1(), env);
                    double ex = GuideEvaluator.resolve(q.x2(), env);
                    double ey = GuideEvaluator.resolve(q.y2(), env);
                    // Exact degree elevation: c1 = p0 + 2/3(q-p0),
                    // c2 = p3 + 2/3(q-p3).
                    double c1x = curX + 2.0 / 3.0 * (qx - curX);
                    double c1y = curY + 2.0 / 3.0 * (qy - curY);
                    double c2x = ex + 2.0 / 3.0 * (qx - ex);
                    double c2y = ey + 2.0 / 3.0 * (qy - ey);
                    curX = ex;
                    curY = ey;
                    segments.add(new Cubic(c1x * sx, c1y * sy, c2x * sx, c2y * sy,
                        curX * sx, curY * sy));
                }
                case GeometryPath.ArcTo a -> {
                    requireCurrent(hasCurrent, "arcTo");
                    double rx = GuideEvaluator.resolve(a.wR(), env);
                    double ry = GuideEvaluator.resolve(a.hR(), env);
                    double stRad = GuideEvaluator.resolve(a.stAng(), env) / ANGLE_UNITS_PER_RADIAN;
                    double swRad = GuideEvaluator.resolve(a.swAng(), env) / ANGLE_UNITS_PER_RADIAN;
                    if (rx <= 0 || ry <= 0 || swRad == 0) {
                        // Degenerate ellipse or empty sweep: PowerPoint
                        // draws nothing and the pen stays put (roundRect
                        // with adj=0 renders as a plain rectangle).
                        break;
                    }
                    double t0 = paramAngle(stRad, rx, ry);
                    double t1 = paramAngle(stRad + swRad, rx, ry);
                    double cx = curX - rx * Math.cos(t0);
                    double cy = curY - ry * Math.sin(t0);
                    emitArc(segments, cx, cy, rx, ry, t0, t1, sx, sy);
                    curX = cx + rx * Math.cos(t1);
                    curY = cy + ry * Math.sin(t1);
                }
                case GeometryPath.Close ignored -> {
                    segments.add(new Close());
                    // The pen returns to the subpath start (Java2D and
                    // PowerPoint agree); a following arcTo continues
                    // from there.
                    curX = startX;
                    curY = startY;
                }
            }
        }
        return new ResolvedPath(path.getFill(), path.isStroked(), List.copyOf(segments));
    }

    private static void requireCurrent(boolean hasCurrent, String command) {
        if (!hasCurrent) {
            throw new IllegalArgumentException(
                "<" + command + "> before any <moveTo>: path has no current point");
        }
    }

    /**
     * OOXML ray angle (radians, y-down) to ellipse parameter angle for
     * radii {@code rx, ry}: {@code t = atan2(rx sin θ, ry cos θ)},
     * continued across turns so sweeps keep their winding count. The
     * conversion is monotonic and fixes every quarter-turn boundary
     * ({@code θ = k·90°} maps to {@code t = k·90°} exactly).
     */
    static double paramAngle(double theta, double rx, double ry) {
        if (rx == ry) return theta; // circle: identity
        double twoPi = 2 * Math.PI;
        double turns = Math.floor(theta / twoPi);
        double rem = theta - turns * twoPi; // [0, 2pi)
        double t = Math.atan2(rx * Math.sin(rem), ry * Math.cos(rem));
        if (t < 0) t += twoPi; // same quadrant as rem, in [0, 2pi)
        return t + turns * twoPi;
    }

    /**
     * Append the elliptical arc {@code t0 -> t1} (parameter angles) on
     * the ellipse centered {@code (cx, cy)} with radii {@code (rx, ry)}
     * as cubic segments of at most a quarter turn each, scaled by
     * {@code (sx, sy)} on emission.
     */
    private static void emitArc(List<Segment> out, double cx, double cy,
                                double rx, double ry, double t0, double t1,
                                double sx, double sy) {
        double sweep = t1 - t0;
        int n = Math.max(1, (int) Math.ceil(Math.abs(sweep) / (Math.PI / 2) - 1e-9));
        double dt = sweep / n;
        double k = 4.0 / 3.0 * Math.tan(dt / 4);
        for (int i = 0; i < n; i++) {
            double a0 = t0 + i * dt;
            double a1 = a0 + dt;
            double x0 = cx + rx * Math.cos(a0), y0 = cy + ry * Math.sin(a0);
            double x3 = cx + rx * Math.cos(a1), y3 = cy + ry * Math.sin(a1);
            // Control points along the parametric tangents E'(t) =
            // (-rx sin t, ry cos t); k's sign follows dt, so negative
            // sweeps emit the counter-clockwise curve automatically.
            double x1 = x0 - k * rx * Math.sin(a0), y1 = y0 + k * ry * Math.cos(a0);
            double x2 = x3 + k * rx * Math.sin(a1), y2 = y3 - k * ry * Math.cos(a1);
            out.add(new Cubic(x1 * sx, y1 * sy, x2 * sx, y2 * sy, x3 * sx, y3 * sy));
        }
    }
}
