package com.excudo.core.geometry;

import java.util.List;

/**
 * One parsed {@code <a:path>} from a geometry definition
 * (ECMA-376 Part 1 &sect;20.1.9.15).
 *
 * <h2>Coordinate space</h2>
 * When the {@code w}/{@code h} attributes are present, the path's literal
 * coordinates (and arc radii) live in a local coordinate system of that
 * size, and {@link GeometryResolver} scales them by
 * {@code shapeW/w, shapeH/h}. When absent, coordinates are guide-space
 * values (the space the guide formulas were evaluated in). Angles are
 * never scaled &mdash; the vendored spec definitions only ever reference the
 * angle constants ({@code cd2}, {@code 3cd4}, ...) from inside
 * {@code w/h}-scoped paths, verified by scan.
 *
 * <h2>Attributes</h2>
 * {@code fill} selects how the shape fill paint applies to this path
 * ({@code norm}, {@code none}, or a lighten/darken derivation);
 * {@code stroke} (default true) selects whether the outline paints;
 * {@code extrusionOk} is 3D metadata and is ignored.
 *
 * <p>Every command coordinate is a <em>guide-name-or-literal</em> token,
 * resolved against the evaluated guide context at render time.
 */
public final class GeometryPath {

    /** {@code <a:path fill="...">} values (ST_PathFillMode). */
    public enum FillMode {
        NORM, NONE, LIGHTEN, LIGHTEN_LESS, DARKEN, DARKEN_LESS;

        /** Parse the OOXML attribute value; unknown values throw. */
        public static FillMode fromXml(String value) {
            if (value == null || value.isEmpty()) return NORM;
            return switch (value) {
                case "norm" -> NORM;
                case "none" -> NONE;
                case "lighten" -> LIGHTEN;
                case "lightenLess" -> LIGHTEN_LESS;
                case "darken" -> DARKEN;
                case "darkenLess" -> DARKEN_LESS;
                default -> throw new IllegalArgumentException(
                    "Unknown path fill mode '" + value + "'");
            };
        }
    }

    /** A path command; coordinates are guide-name-or-literal tokens. */
    public sealed interface Command
            permits MoveTo, LnTo, ArcTo, CubicBezTo, QuadBezTo, Close {}

    public record MoveTo(String x, String y) implements Command {}

    public record LnTo(String x, String y) implements Command {}

    /**
     * {@code <a:arcTo wR=".." hR=".." stAng=".." swAng=".."/>}: continue
     * from the current point along an ellipse with the given radii.
     * {@code stAng}/{@code swAng} are in 60000ths of a degree, measured
     * y-down (positive sweeps clockwise on screen). See
     * {@link GeometryResolver} for the exact PowerPoint semantics
     * (geometric ray angles, not parametric angles).
     */
    public record ArcTo(String wR, String hR, String stAng, String swAng) implements Command {}

    public record CubicBezTo(String x1, String y1, String x2, String y2,
                             String x3, String y3) implements Command {}

    public record QuadBezTo(String x1, String y1, String x2, String y2) implements Command {}

    public record Close() implements Command {}

    private final double width;   // local coordinate-space width; 0 = unspecified
    private final double height;  // local coordinate-space height; 0 = unspecified
    private final FillMode fill;
    private final boolean stroke;
    private final List<Command> commands;

    public GeometryPath(double width, double height, FillMode fill, boolean stroke,
                        List<Command> commands) {
        this.width = width;
        this.height = height;
        this.fill = fill == null ? FillMode.NORM : fill;
        this.stroke = stroke;
        this.commands = List.copyOf(commands);
    }

    /** Local coordinate-space width ({@code @w}); 0 when unspecified. */
    public double getWidth() { return width; }

    /** Local coordinate-space height ({@code @h}); 0 when unspecified. */
    public double getHeight() { return height; }

    public FillMode getFill() { return fill; }

    /** Whether this path's outline is stroked ({@code @stroke}, default true). */
    public boolean isStroked() { return stroke; }

    public List<Command> getCommands() { return commands; }

    @Override
    public String toString() {
        return "GeometryPath{fill=" + fill + ", stroke=" + stroke
            + (width > 0 || height > 0 ? ", local=" + width + "x" + height : "")
            + ", " + commands.size() + " cmd(s)}";
    }
}
