package com.excudo.core.geometry;

import java.util.List;

/**
 * One DrawingML shape geometry definition: the adjust-value defaults
 * ({@code avLst}), the ordered guide formulas ({@code gdLst}), the path
 * list ({@code pathLst}), and the text rectangle ({@code rect}).
 *
 * <p>Instances come from two sources with identical grammar
 * (ECMA-376 Part 1 &sect;20.1.9):
 * <ul>
 *   <li>the vendored spec preset definitions, parsed once by
 *       {@link PresetGeometryRegistry}, and</li>
 *   <li>inline {@code a:custGeom} elements, parsed per shape by
 *       {@link CustomGeometryParser}.</li>
 * </ul>
 *
 * <p>Adjust handles ({@code ahLst}) are parsed-but-ignored: they only
 * matter for interactive dragging, which Excudo does not implement.
 * Connection sites ({@code cxnLst}) are skipped for the same reason.
 *
 * <p>Immutable. The guide/adjust lists preserve document order because
 * guide formulas may reference any previously defined guide
 * (&sect;20.1.9.11: "guides are evaluated in the order given").
 */
public final class GeometryDefinition {

    /** One {@code <a:gd name=".." fmla="op a b c"/>} entry. */
    public record Guide(String name, String fmla) {
        public Guide {
            if (name == null || name.isEmpty()) {
                throw new IllegalArgumentException("guide name must be non-empty");
            }
            if (fmla == null || fmla.isBlank()) {
                throw new IllegalArgumentException("guide '" + name + "' has empty formula");
            }
        }
    }

    /**
     * The {@code <a:rect l=".." t=".." r=".." b=".."/>} text rectangle.
     * Each field is a guide-name-or-literal token. Parsed and kept for
     * the text-layout integration; not consumed by the renderer yet.
     */
    public record TextRect(String left, String top, String right, String bottom) {}

    private final String name;
    private final List<Guide> adjustDefaults; // avLst, document order
    private final List<Guide> guides;         // gdLst, document order
    private final List<GeometryPath> paths;   // pathLst, document order
    private final TextRect textRect;          // nullable

    public GeometryDefinition(String name, List<Guide> adjustDefaults,
                              List<Guide> guides, List<GeometryPath> paths,
                              TextRect textRect) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("geometry name must be non-empty");
        }
        this.name = name;
        this.adjustDefaults = List.copyOf(adjustDefaults);
        this.guides = List.copyOf(guides);
        this.paths = List.copyOf(paths);
        this.textRect = textRect;
    }

    public String getName() { return name; }

    /** avLst entries in document order (formulas are {@code val N}). */
    public List<Guide> getAdjustDefaults() { return adjustDefaults; }

    /** gdLst entries in document order. */
    public List<Guide> getGuides() { return guides; }

    public List<GeometryPath> getPaths() { return paths; }

    /** Text rectangle, or null when the definition declares none. */
    public TextRect getTextRect() { return textRect; }

    @Override
    public String toString() {
        return "GeometryDefinition{" + name + ", " + adjustDefaults.size() + " adj, "
            + guides.size() + " guides, " + paths.size() + " path(s)}";
    }
}
