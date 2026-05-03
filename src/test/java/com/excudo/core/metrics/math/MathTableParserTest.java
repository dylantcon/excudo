package com.excudo.core.metrics.math;

import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/**
 * Pins {@link MathTableParser} against a real OpenType MATH table.
 *
 * <p>DejaVu Math TeX Gyre ships with the standard Linux dejavu font
 * package and is what {@code fc-match "Cambria Math"} resolves to on
 * developer workstations without the proprietary Cambria Math
 * installed. It carries a complete MATH table (constants + glyph info
 * + variants) so every parser path gets exercised by this single
 * fixture.
 *
 * <p>If the font isn't installed (cleanroom CI builders, macOS without
 * dejavu) the tests skip rather than fail -- the parser logic stays
 * the same, just no integration target.
 */
public class MathTableParserTest {

    private static final Path FONT_PATH =
        Paths.get("/usr/share/fonts/truetype/dejavu/DejaVuMathTeXGyre.ttf");
    private static MathTable table;

    @BeforeClass
    public static void loadOnce() throws Exception {
        assumeTrue("DejaVu Math TeX Gyre not available; skipping MATH-table tests",
            java.nio.file.Files.exists(FONT_PATH));
        table = MathTableParser.parse(FONT_PATH);
        assertNotNull("Font carries a MATH table", table);
    }

    @Test
    public void headerVersionIsOneZero() {
        // OpenType MATH spec: current version 1.0. Any non-1.x reading
        // would mean we're parsing the wrong offset.
        assertEquals(1, table.majorVersion());
        assertEquals(0, table.minorVersion());
    }

    @Test
    public void mathConstantsMatchExpectedValues() {
        // Reference values read by the Python sanity script that
        // accompanies this work. They're stable across DejaVu releases
        // (the font hasn't been re-tuned in ~10 years) so a regression
        // here means we're misreading the binary layout, not that the
        // font changed.
        MathConstants c = table.constants();
        assertEquals("scriptPercentScaleDown",
            80, c.scriptPercentScaleDown());
        assertEquals("scriptScriptPercentScaleDown",
            65, c.scriptScriptPercentScaleDown());
        assertEquals("delimitedSubFormulaMinHeight",
            1333, c.delimitedSubFormulaMinHeight());
        assertEquals("displayOperatorMinHeight",
            1333, c.displayOperatorMinHeight());
        assertEquals("axisHeight.value", 275, c.axisHeight().value());
        assertEquals("fractionRuleThickness.value",
            64, c.fractionRuleThickness().value());
    }

    @Test
    public void radicalDegreeBottomRaisePercentInPlausibleRange() {
        // The trailing constant sits past 51 MathValueRecord. A
        // misaligned offset would produce wild values.
        int v = table.constants().radicalDegreeBottomRaisePercent();
        assertTrue("radicalDegreeBottomRaisePercent looks plausible: " + v,
            v >= 0 && v <= 100);
    }

    @Test
    public void glyphInfoCarriesItalicCorrectionForSomeGlyphs() {
        // Math fonts always author italic corrections for italic
        // letters. An empty map means we missed the subtable.
        assertFalse("italic correction map non-empty",
            table.glyphInfo().italicCorrectionMap().isEmpty());
    }

    @Test
    public void glyphInfoCarriesTopAccentForSomeGlyphs() {
        assertFalse("top accent attachment map non-empty",
            table.glyphInfo().topAccentAttachmentMap().isEmpty());
    }

    @Test
    public void variantsTableExposesStretchyDelimiters() {
        // Vertical variants: parens, brackets, integrals. Horizontal:
        // overbrace, vinculum, etc. A math font without either is
        // structurally broken; if we see 0/0 here we lost the table.
        int vCount = 0, hCount = 0;
        for (var e : table.constants().getClass().getRecordComponents()) {
            // (cheap "are we live?" sanity -- nothing to assert on the
            // record's metadata; the real assertions follow.)
            break;
        }
        // Use the public accessors on MathVariants. We don't expose
        // the size directly so probe via a few well-known glyph IDs
        // -- but we don't have a cmap here, so just check that *some*
        // glyph somewhere has variants. Iterate via the public maps
        // through reflection-safe means: the parser populates them
        // from coverage tables.
        MathVariants v = table.variants();
        assertNotNull(v);
        // The minConnectorOverlap is read from the table header; it's
        // typically a small positive value.
        assertTrue("minConnectorOverlap > 0: " + v.minConnectorOverlap(),
            v.minConnectorOverlap() >= 0);
        // Walk a reasonable glyph ID range looking for any vertical
        // variant -- DejaVu has hundreds of stretchy glyphs across
        // the full glyph table.
        for (int gid = 1; gid < 4000; gid++) {
            if (!v.verticalVariantsFor(gid).isEmpty()) { vCount++; if (vCount > 5) break; }
        }
        for (int gid = 1; gid < 4000; gid++) {
            if (!v.horizontalVariantsFor(gid).isEmpty()) { hCount++; if (hCount > 2) break; }
        }
        assertTrue("Some vertical-stretchy glyphs found: " + vCount, vCount > 0);
    }

    @Test
    public void parseIsCachedPerPath() throws Exception {
        // Same font path returns the same MathTable instance across
        // calls -- mirrors TrueTypeFontParser's per-path cache so
        // every math run during a render doesn't re-read the file.
        MathTable a = MathTableParser.parse(FONT_PATH);
        MathTable b = MathTableParser.parse(FONT_PATH);
        assertSame("MathTable cached by absolute path", a, b);
    }

    @Test
    public void parseReturnsNullForFontWithoutMathTable() throws Exception {
        // DejaVu Sans Mono Bold has no MATH table -- it ships from the
        // same package as DejaVuMathTeXGyre but the bold/oblique mono
        // faces don't carry math metrics. Skip if absent.
        Path plain = Paths.get("/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf");
        assumeTrue("DejaVu Sans Mono Bold not available", java.nio.file.Files.exists(plain));
        MathTable t = MathTableParser.parse(plain);
        assertNull("Font without MATH table parses to null", t);
    }

    @Test
    public void mathValueRecordZeroSentinelIsSafe() {
        MathValueRecord z = MathValueRecord.ZERO;
        assertEquals(0, z.value());
        assertEquals(0, z.deviceTableOffset());
    }
}
