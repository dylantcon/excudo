package com.excudo.core.metrics.math;

/**
 * One scalar value from the OpenType MATH table, in font design units.
 *
 * <p>The OpenType MATH spec uses {@code MathValueRecord} for every
 * tunable layout constant: the value plus an optional offset to a
 * Device Table that adjusts the value at small ppem sizes. Most fonts
 * (including DejaVu Math TeX Gyre, Cambria Math, STIX Two Math) leave
 * the device table null -- the bare {@link #value()} suffices for
 * layout. We preserve the offset for round-trip fidelity but layout
 * code reads {@link #value()} directly.
 *
 * <p>Units are font design units (FUnits): the same coordinate system
 * the existing {@link com.excudo.core.metrics.FontData} uses for
 * {@code unitsPerEm}, glyph advance widths, and OS/2 metrics.
 *
 * @param value             the raw int16 design-unit value
 * @param deviceTableOffset offset to a Device Table from the start of
 *                          the parent table, or 0 when no device
 *                          adjustment is supplied
 */
public record MathValueRecord(int value, int deviceTableOffset) {

    /** Sentinel for fields that aren't authored in a font. The MATH spec
     *  doesn't reserve a specific value, but value=0 with offset=0 means
     *  "no override authored" in practice; layout code can fall back to
     *  a sensible suggested value from the spec. */
    public static final MathValueRecord ZERO = new MathValueRecord(0, 0);
}
