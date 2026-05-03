package com.excudo.core.metrics.math;

/**
 * The OpenType MATH table -- font-supplied math typography data.
 *
 * <p>Three subtables make up everything math layout needs from the
 * font itself:
 *
 * <ul>
 *   <li>{@link MathConstants} -- 56 typesetting constants (axis height,
 *       fraction bar thickness, super/sub shifts, n-ary gaps, etc).
 *       Per-font, not per-glyph.</li>
 *   <li>{@link MathGlyphInfo} -- italic correction, top-accent
 *       attachment, extended-shape flag, and kern records, all keyed
 *       by glyph ID.</li>
 *   <li>{@link MathVariants} -- stretchy glyph variants for parens,
 *       integrals, summation, and similar, plus assembly recipes for
 *       content larger than any pre-designed variant.</li>
 * </ul>
 *
 * <p>Constructed via {@link MathTableParser#parse} and cached per font
 * path, mirroring {@link com.excudo.core.metrics.TrueTypeFontParser}'s
 * parse + cache style. Layout code reads the subtables directly --
 * see {@code core/metrics/math/} for usage.
 */
public final class MathTable {

    private final int majorVersion;
    private final int minorVersion;
    private final MathConstants constants;
    private final MathGlyphInfo glyphInfo;
    private final MathVariants variants;

    public MathTable(int majorVersion, int minorVersion,
                     MathConstants constants,
                     MathGlyphInfo glyphInfo,
                     MathVariants variants) {
        this.majorVersion = majorVersion;
        this.minorVersion = minorVersion;
        this.constants    = constants;
        this.glyphInfo    = glyphInfo;
        this.variants     = variants;
    }

    public int majorVersion() { return majorVersion; }
    public int minorVersion() { return minorVersion; }

    public MathConstants constants() { return constants; }
    public MathGlyphInfo glyphInfo() { return glyphInfo; }
    public MathVariants variants() { return variants; }
}
