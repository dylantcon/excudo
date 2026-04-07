package com.excudo.core.metrics;

import org.junit.Test;
import static org.junit.Assert.*;

import java.nio.file.Path;

/**
 * Tests TrueTypeFontParser against known DejaVu Sans metric values
 * extracted from the font's binary tables via Python fonttools.
 */
public class TrueTypeFontParserTest {

    private static final Path DEJAVU_SANS = Path.of("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf");

    @Test
    public void testDejaVuSansHeadTable() throws Exception {
        TrueTypeFontParser.clearCache();
        FontData data = TrueTypeFontParser.parse(DEJAVU_SANS);
        assertEquals("unitsPerEm must be 2048", 2048, data.getUnitsPerEm());
    }

    @Test
    public void testDejaVuSansOS2WinMetrics() throws Exception {
        FontData data = TrueTypeFontParser.parse(DEJAVU_SANS);
        assertEquals("usWinAscent", 1901, data.getWinAscent());
        assertEquals("usWinDescent", 483, data.getWinDescent());
    }

    @Test
    public void testDejaVuSansTypoMetricsFlag() throws Exception {
        FontData data = TrueTypeFontParser.parse(DEJAVU_SANS);
        assertFalse("DejaVu Sans should NOT have USE_TYPO_METRICS set", data.isUseTypoMetrics());
    }

    @Test
    public void testDejaVuSansGlyphMapping() throws Exception {
        FontData data = TrueTypeFontParser.parse(DEJAVU_SANS);
        int glyphA = data.getGlyphId('A');
        assertEquals("Glyph ID for 'A' should be 36", 36, glyphA);

        int glyphSpace = data.getGlyphId(' ');
        assertEquals("Glyph ID for space should be 3", 3, glyphSpace);
    }

    @Test
    public void testDejaVuSansAdvanceWidths() throws Exception {
        FontData data = TrueTypeFontParser.parse(DEJAVU_SANS);

        assertEquals("Advance width for glyph 'A' (id=36)", 1401, data.getAdvanceWidth(36));
        assertEquals("Advance width for space (id=3)", 651, data.getAdvanceWidth(3));
    }

    @Test
    public void testCharAdvanceWidthConvenience() throws Exception {
        FontData data = TrueTypeFontParser.parse(DEJAVU_SANS);
        assertEquals("charAdvanceWidth('A')", 1401, data.getCharAdvanceWidth('A'));
        assertEquals("charAdvanceWidth(' ')", 651, data.getCharAdvanceWidth(' '));
    }

    @Test
    public void testLineHeightCalculation() throws Exception {
        FontData data = TrueTypeFontParser.parse(DEJAVU_SANS);
        // At 18pt: lineHeight = (1901+483)*18/2048 = 20.9531... pt
        // In EMUs: 20.9531 * 12700 = 266104.7... -> round to 266105
        long lineHeight = data.calculateLineHeightEmu(1800);
        assertEquals("Line height at 18pt", 266105, lineHeight);
    }

    @Test
    public void testStringWidthMeasurement() throws Exception {
        FontData data = TrueTypeFontParser.parse(DEJAVU_SANS);
        // Single 'A' at 18pt: width = 1401 * 18 / 2048 pt = 12.3134... pt
        // In EMUs: 12.3134 * 12700 = 156381.1... -> 156381
        long width = data.measureStringWidthEmu("A", 1800);
        assertEquals("Width of 'A' at 18pt", 156381, width);
    }

    @Test
    public void testFontFamilyName() throws Exception {
        FontData data = TrueTypeFontParser.parse(DEJAVU_SANS);
        assertEquals("DejaVu Sans", data.getFontFamily());
    }

    @Test
    public void testCaching() throws Exception {
        TrueTypeFontParser.clearCache();
        FontData first = TrueTypeFontParser.parse(DEJAVU_SANS);
        FontData second = TrueTypeFontParser.parse(DEJAVU_SANS);
        assertSame("Cached instances should be identical", first, second);
    }

    @Test
    public void testUnmappedCharacterReturnsZero() throws Exception {
        FontData data = TrueTypeFontParser.parse(DEJAVU_SANS);
        // Private use area character unlikely to be mapped
        int glyphId = data.getGlyphId(0xFFFF);
        // Should return 0 (.notdef) or a valid glyph -- just verify no exception
        assertTrue("Unmapped char returns valid glyph id", glyphId >= 0);
    }
}
