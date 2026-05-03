package com.excudo.core.metrics.math;

import java.util.List;
import java.util.Map;

/**
 * Stretchy glyph variants from the OpenType MATH table.
 *
 * <p>Some glyphs (parens, brackets, braces, integrals, summation,
 * radicals, vinculum / overbrace / underbrace) need to grow with their
 * content. The font ships pre-designed variants at increasing sizes,
 * plus optional "assembly recipes" for content larger than the largest
 * pre-designed variant -- the layout engine picks the smallest variant
 * whose extent meets or exceeds the content extent.
 *
 * <p>Two axes are tracked separately:
 * <ul>
 *   <li><b>Vertical stretch</b> -- parens, brackets, integrals,
 *       summation, etc. Variant heights grow along the y axis.</li>
 *   <li><b>Horizontal stretch</b> -- overbrace, underbrace, vinculum,
 *       horizontal arrows. Variant widths grow along the x axis.</li>
 * </ul>
 *
 * <p>For each base glyph, the font supplies an ordered list of
 * {@link MathGlyphVariantRecord}s (smallest to largest) and an
 * optional {@link GlyphAssembly} for content past the last variant.
 * The {@link #pickVariant} helpers do the lookup.
 */
public final class MathVariants {

    private final int minConnectorOverlap;
    private final Map<Integer, List<MathGlyphVariantRecord>> verticalVariants;
    private final Map<Integer, List<MathGlyphVariantRecord>> horizontalVariants;
    private final Map<Integer, GlyphAssembly> verticalAssemblies;
    private final Map<Integer, GlyphAssembly> horizontalAssemblies;

    public MathVariants(int minConnectorOverlap,
                        Map<Integer, List<MathGlyphVariantRecord>> verticalVariants,
                        Map<Integer, List<MathGlyphVariantRecord>> horizontalVariants,
                        Map<Integer, GlyphAssembly> verticalAssemblies,
                        Map<Integer, GlyphAssembly> horizontalAssemblies) {
        this.minConnectorOverlap  = minConnectorOverlap;
        this.verticalVariants     = verticalVariants     != null ? Map.copyOf(verticalVariants)     : Map.of();
        this.horizontalVariants   = horizontalVariants   != null ? Map.copyOf(horizontalVariants)   : Map.of();
        this.verticalAssemblies   = verticalAssemblies   != null ? Map.copyOf(verticalAssemblies)   : Map.of();
        this.horizontalAssemblies = horizontalAssemblies != null ? Map.copyOf(horizontalAssemblies) : Map.of();
    }

    /** Minimum overlap (in design units) between successive parts when
     *  assembling a glyph from {@link GlyphAssembly} parts. */
    public int minConnectorOverlap() { return minConnectorOverlap; }

    /** Variants for vertical stretch (parens, integrals, etc.), indexed
     *  by the base glyph ID. Entries are ordered smallest to largest. */
    public List<MathGlyphVariantRecord> verticalVariantsFor(int glyphId) {
        return verticalVariants.getOrDefault(glyphId, List.of());
    }

    /** Variants for horizontal stretch (overbrace, vinculum, etc.). */
    public List<MathGlyphVariantRecord> horizontalVariantsFor(int glyphId) {
        return horizontalVariants.getOrDefault(glyphId, List.of());
    }

    /** Assembly recipe for content too large for any pre-designed
     *  vertical variant; null when none was authored. */
    public GlyphAssembly verticalAssemblyFor(int glyphId) {
        return verticalAssemblies.get(glyphId);
    }

    /** Horizontal counterpart to {@link #verticalAssemblyFor}. */
    public GlyphAssembly horizontalAssemblyFor(int glyphId) {
        return horizontalAssemblies.get(glyphId);
    }

    /**
     * Pick the smallest pre-designed variant whose advance meets or
     * exceeds {@code minSizeFunits} (design units). Returns null when
     * no variant is large enough -- the caller should then fall back
     * to the {@link GlyphAssembly}, or to vertical scaling if no
     * assembly is authored.
     */
    public static MathGlyphVariantRecord pickVariant(
            List<MathGlyphVariantRecord> variants, int minSizeFunits) {
        for (MathGlyphVariantRecord v : variants) {
            if (v.advanceMeasurement() >= minSizeFunits) return v;
        }
        return null;
    }

    /** One pre-designed variant: the glyph ID + its measured extent
     *  (height for vertical, width for horizontal) in design units. */
    public record MathGlyphVariantRecord(int variantGlyphId, int advanceMeasurement) {}

    /**
     * Recipe for assembling a glyph from parts when no pre-designed
     * variant is large enough. Each part is a glyph ID that connects
     * to its neighbours with at least {@link #minConnectorOverlap}
     * units of overlap; flags mark whether the part is the start, the
     * end, or one of the repeatable middle pieces.
     *
     * @param italicsCorrection italic-correction value to apply when
     *                          the assembled glyph is followed by
     *                          superscripts / other glyphs
     * @param parts             ordered parts: the first marked as
     *                          start, last as end, middles repeated
     *                          to fill the content extent
     */
    public record GlyphAssembly(int italicsCorrection, List<GlyphPart> parts) {}

    /** One part of an assembled stretchy glyph. */
    public record GlyphPart(
            int glyphId,
            int startConnectorLength,
            int endConnectorLength,
            int fullAdvance,
            boolean isExtender
    ) {}
}
