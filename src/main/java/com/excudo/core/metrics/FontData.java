package com.excudo.core.metrics;

import java.util.Map;

/**
 * Immutable font metric data extracted from a TrueType/OpenType font file.
 * Contains only the tables PowerPoint uses for text layout: head, OS/2, hhea, hmtx, cmap.
 */
public final class FontData {

    private final String fontFamily;
    private final int unitsPerEm;
    private final int ascent;
    private final int descent;
    private final int lineGap;
    private final boolean useTypoMetrics;
    private final int typoAscender;
    private final int typoDescender;
    private final int typoLineGap;
    private final int[] advanceWidths;
    private final Map<Integer, Integer> cmapLookup;

    public FontData(String fontFamily, int unitsPerEm,
                    int ascent, int descent, int lineGap,
                    boolean useTypoMetrics,
                    int typoAscender, int typoDescender, int typoLineGap,
                    int[] advanceWidths, Map<Integer, Integer> cmapLookup) {
        this.fontFamily = fontFamily;
        this.unitsPerEm = unitsPerEm;
        this.ascent = ascent;
        this.descent = descent;
        this.lineGap = lineGap;
        this.useTypoMetrics = useTypoMetrics;
        this.typoAscender = typoAscender;
        this.typoDescender = typoDescender;
        this.typoLineGap = typoLineGap;
        this.advanceWidths = advanceWidths;
        this.cmapLookup = cmapLookup;
    }

    public String getFontFamily() { return fontFamily; }
    public int getUnitsPerEm() { return unitsPerEm; }
    public int getWinAscent() { return ascent; }
    public int getWinDescent() { return descent; }
    public boolean isUseTypoMetrics() { return useTypoMetrics; }
    public int getTypoAscender() { return typoAscender; }
    public int getTypoDescender() { return typoDescender; }
    public int getTypoLineGap() { return typoLineGap; }

    /**
     * Resolve a Unicode codepoint to a glyph ID via the cmap table.
     * Returns 0 (.notdef) if the character is not mapped.
     */
    public int getGlyphId(int codepoint) {
        return cmapLookup.getOrDefault(codepoint, 0);
    }

    /**
     * Get the advance width for a glyph ID in font design units.
     * Falls back to the last entry (monospace fallback per spec) if glyphId exceeds array.
     */
    public int getAdvanceWidth(int glyphId) {
        if (glyphId < 0) return 0;
        if (glyphId < advanceWidths.length) return advanceWidths[glyphId];
        return advanceWidths.length > 0 ? advanceWidths[advanceWidths.length - 1] : 0;
    }

    /**
     * Get advance width for a character in font design units.
     */
    public int getCharAdvanceWidth(char ch) {
        return getAdvanceWidth(getGlyphId(ch));
    }

    /**
     * Calculate line height in EMUs for a given font size in hundredths of a point.
     * PowerPoint uses OS/2 metrics: either Typo or Win depending on USE_TYPO_METRICS flag.
     *
     * @param fontSizeHundredths font size in hundredths of a point (e.g. 1800 = 18pt)
     * @return line height in EMUs
     */
    public long calculateLineHeightEmu(int fontSizeHundredths) {
        double fontSizePt = fontSizeHundredths / 100.0;
        double lineHeightPt;
        if (useTypoMetrics) {
            lineHeightPt = (typoAscender + Math.abs(typoDescender) + typoLineGap)
                    * fontSizePt / unitsPerEm;
        } else {
            lineHeightPt = (ascent + descent) * fontSizePt / unitsPerEm;
        }
        return Math.round(lineHeightPt * 12700.0);
    }

    /**
     * Calculate the width of a string in EMUs at the given font size.
     *
     * @param text the string to measure
     * @param fontSizeHundredths font size in hundredths of a point
     * @return total advance width in EMUs
     */
    public long measureStringWidthEmu(String text, int fontSizeHundredths) {
        if (text == null || text.isEmpty()) return 0;
        double fontSizePt = fontSizeHundredths / 100.0;
        long totalUnits = 0;
        for (int i = 0; i < text.length(); i++) {
            totalUnits += getCharAdvanceWidth(text.charAt(i));
        }
        double widthPt = (double) totalUnits * fontSizePt / unitsPerEm;
        return Math.round(widthPt * 12700.0);
    }
}
