package com.excudo.core.synthesis;

import com.excudo.core.introspection.LayoutBaseline;
import com.excudo.core.model.LayoutInfo;
import com.excudo.core.model.PlaceholderGeometry;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.SlideShape;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Projects the set of {@link ShapeSnapshot}s a slide <em>would</em>
 * have if it were freshly instantiated from its {@link LayoutBaseline}.
 *
 * <p>This is a mirror of
 * {@code SlideDocumentBuilder.createLayoutShapeTree}'s materialization
 * plan -- same placeholder set, same SPIDs, same names. The builder is
 * the reference implementation; if the two drift, the fixed-point
 * contract breaks: a freshly created slide must diff EMPTY against its
 * baseline (modulo title text, which is per-slide user content).
 * {@code SynthesisFixedPointTest} pins that contract, so any divergence
 * introduced on either side fails CI rather than silently polluting
 * every synthesized script with phantom placeholder mutations.
 *
 * <p>The builder's plan (and therefore this projection):
 * <ul>
 *   <li>Title, when {@code layout.hasTitlePlaceholder()}: SPID 2,
 *       name {@code "Title Placeholder"}, geometry by title type.</li>
 *   <li>Content, when {@code layout.hasContentPlaceholder()}:
 *       {@code max(1, contentPlaceholderCount)} shapes at SPID 3+i,
 *       name {@code "Content Placeholder "+(i+1)}, geometry by
 *       1-based index with body/obj type fallback.</li>
 *   <li>Subtitle, when {@code layout.hasSubtitlePlaceholder()}: next
 *       sequential SPID, name {@code "Subtitle Shape"}, geometry by
 *       subTitle type.</li>
 * </ul>
 *
 * <p>{@code style} and {@code textBody} are projected as null --
 * {@link StateEquality} treats null as equal to a hollow style /
 * zero-authored-text body, so untouched placeholders diff clean while
 * authored title text still emits its SetTextSpec.
 *
 * <p>Known limitation: decks authored OUTSIDE Excudo use different
 * placeholder names ("Title 1", etc.), so loaded foreign decks may emit
 * cosmetic RenameShapeSpecs against this projection. Harmless on apply;
 * revisit if a foreign-deck fixed-point contract becomes a requirement.
 */
public final class PlaceholderProjector {

    private PlaceholderProjector() {}

    public static Map<Integer, ShapeSnapshot> project(LayoutBaseline baseline) {
        if (baseline == null) return Map.of();
        LayoutInfo layout = baseline.layout();
        if (layout == null) return Map.of();

        Map<Integer, ShapeSnapshot> out = new LinkedHashMap<>();

        if (layout.hasTitlePlaceholder()) {
            String titleType = layout.getTitlePlaceholderType();
            PlaceholderGeometry g = layout.getPlaceholderGeometryByType(
                titleType != null && !titleType.isBlank() ? titleType : "title");
            if (g == null) g = layout.getPlaceholderGeometryByType("ctrTitle");
            out.put(2, placeholder(2, "Title Placeholder", g));
        }

        int contentCount = 0;
        if (layout.hasContentPlaceholder()) {
            contentCount = Math.max(1, layout.getContentPlaceholderCount());
            for (int i = 0; i < contentCount; i++) {
                PlaceholderGeometry g = layout.getPlaceholderGeometryByIndex(
                    String.valueOf(i + 1));
                if (g == null) g = layout.getPlaceholderGeometryByType("body");
                if (g == null) g = layout.getPlaceholderGeometryByType("obj");
                int spid = 3 + i;
                out.put(spid, placeholder(spid, "Content Placeholder " + (i + 1), g));
            }
        }

        if (layout.hasSubtitlePlaceholder()) {
            // Builder allocates the subtitle SPID sequentially after the
            // content placeholders (or right after the title when the
            // layout has no content).
            int spid = 3 + contentCount;
            PlaceholderGeometry g = layout.getPlaceholderGeometryByType("subTitle");
            out.put(spid, placeholder(spid, "Subtitle Shape", g));
        }

        return out;
    }

    private static ShapeSnapshot placeholder(int spid, String name, PlaceholderGeometry g) {
        ShapeGeometry geom = g == null ? null
            : new ShapeGeometry(g.getX(), g.getY(), g.getWidth(), g.getHeight());
        return new ShapeSnapshot(spid, name, SlideShape.ShapeType.PLACEHOLDER,
            geom, null, null, false, null);
    }
}
