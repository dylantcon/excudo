package com.excudo.core.metrics.math;

/**
 * The 56 typesetting constants from the OpenType MATH table's
 * {@code MathConstants} subtable, in font design units (FUnits).
 *
 * <p>These constants are the load-bearing reference for math layout:
 * super/sub shifts, fraction bar thickness, n-ary operator gaps,
 * radical extra ascender, accent base height -- everything a layout
 * engine needs that varies per-font. PowerPoint's math composer reads
 * these directly from Cambria Math; rendering math without them
 * produces visibly wrong results (the difference between {@code f²}
 * with the {@code ²} hugging the {@code f} versus floating off in
 * space).
 *
 * <p>The four leading fields are bare integers; the rest are
 * {@link MathValueRecord}s so device-table adjustments can be
 * preserved. Layout code typically reads
 * {@code constant.value()} -- e.g.
 * {@code constants.fractionRuleThickness().value()} for the fraction
 * bar thickness.
 *
 * <p>Field naming and semantics follow the OpenType spec verbatim.
 * See {@code docs/ms-specs/OT-MATH-table.txt} (lines 188-256) for
 * suggested values and detailed descriptions of each.
 */
public record MathConstants(
        // Bare integers (not MathValueRecord -- no device adjustment in the spec)
        int scriptPercentScaleDown,
        int scriptScriptPercentScaleDown,
        int delimitedSubFormulaMinHeight,
        int displayOperatorMinHeight,

        // General constants
        MathValueRecord mathLeading,
        MathValueRecord axisHeight,
        MathValueRecord accentBaseHeight,
        MathValueRecord flattenedAccentBaseHeight,

        // Subscript / superscript
        MathValueRecord subscriptShiftDown,
        MathValueRecord subscriptTopMax,
        MathValueRecord subscriptBaselineDropMin,
        MathValueRecord superscriptShiftUp,
        MathValueRecord superscriptShiftUpCramped,
        MathValueRecord superscriptBottomMin,
        MathValueRecord superscriptBaselineDropMax,
        MathValueRecord subSuperscriptGapMin,
        MathValueRecord superscriptBottomMaxWithSubscript,
        MathValueRecord spaceAfterScript,

        // n-ary limits (over/under operator)
        MathValueRecord upperLimitGapMin,
        MathValueRecord upperLimitBaselineRiseMin,
        MathValueRecord lowerLimitGapMin,
        MathValueRecord lowerLimitBaselineDropMin,

        // Stack (top element over bottom element with no rule between)
        MathValueRecord stackTopShiftUp,
        MathValueRecord stackTopDisplayStyleShiftUp,
        MathValueRecord stackBottomShiftDown,
        MathValueRecord stackBottomDisplayStyleShiftDown,
        MathValueRecord stackGapMin,
        MathValueRecord stackDisplayStyleGapMin,

        // Stretch stack (stack with stretchable middle element)
        MathValueRecord stretchStackTopShiftUp,
        MathValueRecord stretchStackBottomShiftDown,
        MathValueRecord stretchStackGapAboveMin,
        MathValueRecord stretchStackGapBelowMin,

        // Fractions
        MathValueRecord fractionNumeratorShiftUp,
        MathValueRecord fractionNumeratorDisplayStyleShiftUp,
        MathValueRecord fractionDenominatorShiftDown,
        MathValueRecord fractionDenominatorDisplayStyleShiftDown,
        MathValueRecord fractionNumeratorGapMin,
        MathValueRecord fractionNumDisplayStyleGapMin,
        MathValueRecord fractionRuleThickness,
        MathValueRecord fractionDenominatorGapMin,
        MathValueRecord fractionDenomDisplayStyleGapMin,
        MathValueRecord skewedFractionHorizontalGap,
        MathValueRecord skewedFractionVerticalGap,

        // Bars (over / under)
        MathValueRecord overbarVerticalGap,
        MathValueRecord overbarRuleThickness,
        MathValueRecord overbarExtraAscender,
        MathValueRecord underbarVerticalGap,
        MathValueRecord underbarRuleThickness,
        MathValueRecord underbarExtraDescender,

        // Radicals (sqrt and nth-root)
        MathValueRecord radicalVerticalGap,
        MathValueRecord radicalDisplayStyleVerticalGap,
        MathValueRecord radicalRuleThickness,
        MathValueRecord radicalExtraAscender,
        MathValueRecord radicalKernBeforeDegree,
        MathValueRecord radicalKernAfterDegree,
        int radicalDegreeBottomRaisePercent
) {
    /** Total bytes the MathConstants subtable occupies in the MATH
     *  table on disk. Useful for offset arithmetic when other subtables
     *  follow it. 4 simple int16/uint16 + 51 MathValueRecord (4 bytes
     *  each) + 1 int16 = 8 + 204 + 2 = 214 bytes. */
    public static final int SIZE_BYTES = 214;
}
