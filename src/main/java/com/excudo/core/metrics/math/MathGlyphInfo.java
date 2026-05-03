package com.excudo.core.metrics.math;

import java.util.Map;
import java.util.Set;

/**
 * Per-glyph math typography metadata from the OpenType MATH table.
 *
 * <p>The OpenType {@code MathGlyphInfo} subtable carries four kinds of
 * information that depend on which specific glyph is being rendered,
 * not on the font as a whole:
 *
 * <ul>
 *   <li><b>Italic correction</b>: extra horizontal advance to insert
 *       after an italic glyph before placing a superscript or another
 *       glyph. Without this, the {@code ²} in {@code f²} clips into
 *       the italic {@code f}'s top-right ink.</li>
 *
 *   <li><b>Top-accent attachment</b>: x-coordinate (in design units,
 *       relative to the glyph's origin) where a centred accent should
 *       attach. Lets {@code â} place the circumflex over the visual
 *       centre of the {@code a} rather than its bbox centre.</li>
 *
 *   <li><b>Extended shape coverage</b>: the set of glyph IDs the font
 *       designer has flagged as "extended" (large operators, integrals,
 *       summation signs, etc.). Layout treats the bounding box of
 *       these glyphs as the reference for positioning sub/superscripts
 *       (script baseline drop max), instead of the default "main"
 *       baseline rule.</li>
 *
 *   <li><b>Math kern records</b>: per-glyph kern points around each
 *       corner (top-right, top-left, bottom-right, bottom-left) used
 *       to tuck super/subscripts closer to italic letters and large
 *       operators. {@link MathKernRecord} carries the four kern
 *       records for one glyph.</li>
 * </ul>
 *
 * <p>All maps are unmodifiable views; {@link #italicCorrection(int)}
 * etc. are the canonical lookup helpers and return 0 / null for glyphs
 * the font didn't author values for.
 */
public final class MathGlyphInfo {

    private final Map<Integer, Integer> italicCorrection;
    private final Map<Integer, Integer> topAccentAttachment;
    private final Set<Integer> extendedShapes;
    private final Map<Integer, MathKernRecord> kernRecords;

    public MathGlyphInfo(Map<Integer, Integer> italicCorrection,
                         Map<Integer, Integer> topAccentAttachment,
                         Set<Integer> extendedShapes,
                         Map<Integer, MathKernRecord> kernRecords) {
        this.italicCorrection    = italicCorrection    != null ? Map.copyOf(italicCorrection)    : Map.of();
        this.topAccentAttachment = topAccentAttachment != null ? Map.copyOf(topAccentAttachment) : Map.of();
        this.extendedShapes      = extendedShapes      != null ? Set.copyOf(extendedShapes)      : Set.of();
        this.kernRecords         = kernRecords         != null ? Map.copyOf(kernRecords)         : Map.of();
    }

    /** Italic correction in design units for the supplied glyph, or 0
     *  when the font didn't author one (typically true for upright glyphs). */
    public int italicCorrection(int glyphId) {
        Integer v = italicCorrection.get(glyphId);
        return v != null ? v : 0;
    }

    /** Top-accent attachment x-coordinate in design units, or -1 when
     *  the font didn't author one (callers should fall back to the
     *  glyph's bbox centre). */
    public int topAccentAttachment(int glyphId) {
        Integer v = topAccentAttachment.get(glyphId);
        return v != null ? v : -1;
    }

    /** True when the glyph is flagged as an extended shape (large
     *  operators, integrals, etc.). */
    public boolean isExtendedShape(int glyphId) {
        return extendedShapes.contains(glyphId);
    }

    /** Kern record for the supplied glyph, or null when the font
     *  didn't author one. */
    public MathKernRecord kernRecord(int glyphId) {
        return kernRecords.get(glyphId);
    }

    public Map<Integer, Integer> italicCorrectionMap()    { return italicCorrection; }
    public Map<Integer, Integer> topAccentAttachmentMap() { return topAccentAttachment; }
    public Set<Integer>          extendedShapeSet()       { return extendedShapes; }
    public Map<Integer, MathKernRecord> kernRecordMap()   { return kernRecords; }
}
