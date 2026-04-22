package com.excudo.core.synthesis;

import com.excudo.core.introspection.LayoutBaseline;
import com.excudo.core.model.LayoutInfo;
import com.excudo.core.model.PlaceholderGeometry;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.SlideShape;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Projects the set of {@link ShapeSnapshot}s a slide <em>would</em>
 * have if it were freshly instantiated from its {@link LayoutBaseline}.
 *
 * <p>Excudo's {@code CreateSlideCommand} materializes one shape per
 * placeholder declared on the layout, following the canonical SPID
 * convention: SPID 1 = root group, SPID 2 = title (if present), SPID
 * 3+ = content / subtitle placeholders. This projector reproduces that
 * projection so the diff can distinguish "shape came from the layout"
 * (skip, no CommandSpec needed) from "shape user-added" (emit an
 * {@code AddShapeSpec}).
 *
 * <p>The projection is intentionally conservative: {@code style},
 * {@code textBody}, and {@code parentGroupSpid} are all {@code null}
 * on placeholder snapshots. When the parser reads a slide that still
 * has its untouched placeholders, {@link StateEquality} compares those
 * against the projected snapshots -- null-vs-null on style means equal
 * (inherit from theme), and an empty {@code TextBody} on the current
 * side diffs as "TEXT field changed" only if there's authored content.
 * That's the expected behavior: authored content SHOULD round-trip as
 * a {@code SetTextSpec} on top of the baseline placeholder.
 */
public final class PlaceholderProjector {

    private PlaceholderProjector() {}

    /**
     * Build the shape map for the layout baseline. SPIDs follow the
     * canonical {@code CreateSlideCommand} convention. Returns an
     * insertion-ordered map so downstream diff output is deterministic.
     */
    public static Map<Integer, ShapeSnapshot> project(LayoutBaseline baseline) {
        if (baseline == null) return Map.of();
        LayoutInfo layout = baseline.layout();
        if (layout == null) return Map.of();

        Map<Integer, ShapeSnapshot> out = new LinkedHashMap<>();
        List<PlaceholderGeometry> geometries = layout.getPlaceholderGeometries();
        if (geometries == null) return out;

        // Canonical SPID walk: 2 is title when present; then every other
        // placeholder starts at 3 in the order the layout declared them.
        // Reproduce by iterating the geometry list once, treating title
        // as special.
        int titleSpid = 2;
        int nextNonTitleSpid = 3;

        for (PlaceholderGeometry g : geometries) {
            String key = g.getPlaceholderIndex();
            String type = extractType(key, g);
            boolean isTitle = isTitleType(type);
            int spid = isTitle ? titleSpid : nextNonTitleSpid++;
            String name = deriveName(type);
            ShapeGeometry geom = new ShapeGeometry(g.getX(), g.getY(), g.getWidth(), g.getHeight());
            out.put(spid, new ShapeSnapshot(
                spid, name, SlideShape.ShapeType.PLACEHOLDER,
                geom, null, null, false, null));
        }
        return out;
    }

    /** Extract the OOXML ph type. PlaceholderGeometry stores it in
     *  {@code placeholderType} when type-keyed, else the index is
     *  formatted as {@code "type:XYZ"}. Falls back to the raw index. */
    private static String extractType(String key, PlaceholderGeometry g) {
        if (g.getPlaceholderType() != null && !g.getPlaceholderType().isBlank()) {
            return g.getPlaceholderType();
        }
        if (key != null && key.startsWith("type:")) return key.substring(5);
        return key;
    }

    private static boolean isTitleType(String type) {
        return "title".equals(type) || "ctrTitle".equals(type);
    }

    private static String deriveName(String type) {
        if (type == null) return "Placeholder";
        return switch (type) {
            case "title", "ctrTitle" -> "Title";
            case "subTitle"          -> "Subtitle";
            case "body", "obj"       -> "Content";
            default                  -> "Placeholder_" + type;
        };
    }
}
