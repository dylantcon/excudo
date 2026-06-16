package com.excudo.core.themes;

import com.excudo.core.model.PPTXDocument;
import com.excudo.core.utils.XMLFactoryProvider;
import org.junit.Test;
import org.w3c.dom.Document;

import java.io.File;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Tests ThemeParser color/font extraction from OOXML theme XML.
 */
public class ThemeParserTest {

    private static final File TEST_FILE = resolveTestFile();

    private static File resolveTestFile() {
        File f = new File("test-pptx-samples/generalist_test_file.pptx");
        if (!f.exists()) f = new File("../../../test-pptx-samples/generalist_test_file.pptx");
        return f;
    }

    @Test
    public void parseThemeFromPPTXExtractsColors() throws Exception {
        if (!TEST_FILE.exists()) fail("Required fixture not found: " + TEST_FILE.getAbsolutePath());
        PPTXDocument doc = PPTXDocument.loadFromZip(TEST_FILE);
        Document themeDom = doc.getXmlPart("ppt/theme/theme1.xml");
        assertNotNull("Theme DOM should exist", themeDom);

        ThemeParser parser = new ThemeParser();
        assertTrue("parseTheme should succeed", parser.parseTheme(themeDom));
        assertTrue("Theme should be loaded", parser.isThemeLoaded());

        // Verify known colors from the generalist test file. ThemeParser
        // now returns typed HexColor; both adapter forms work.
        assertEquals("#569CD6", parser.getThemeColor("accent1").withHash());
        assertEquals("569CD6", parser.getThemeColor("accent1").bare());
        assertNotNull(parser.getThemeColor("dk1"));
        assertNotNull(parser.getThemeColor("lt1"));
        assertNotNull(parser.getThemeColor("dk2"));
        assertNotNull(parser.getThemeColor("lt2"));
    }

    @Test
    public void parseThemeExtractsFontScheme() throws Exception {
        if (!TEST_FILE.exists()) fail("Required fixture not found: " + TEST_FILE.getAbsolutePath());
        PPTXDocument doc = PPTXDocument.loadFromZip(TEST_FILE);
        Document themeDom = doc.getXmlPart("ppt/theme/theme1.xml");

        ThemeParser parser = new ThemeParser();
        parser.parseTheme(themeDom);

        String majorFont = parser.getThemeFont("major");
        String minorFont = parser.getThemeFont("minor");
        assertNotNull("Major font should be resolved", majorFont);
        assertNotNull("Minor font should be resolved", minorFont);
    }

    @Test
    public void getColorSchemeReturnsDefensiveCopy() throws Exception {
        if (!TEST_FILE.exists()) fail("Required fixture not found: " + TEST_FILE.getAbsolutePath());
        PPTXDocument doc = PPTXDocument.loadFromZip(TEST_FILE);
        Document themeDom = doc.getXmlPart("ppt/theme/theme1.xml");

        ThemeParser parser = new ThemeParser();
        parser.parseTheme(themeDom);

        Map<String, String> scheme1 = parser.getColorScheme();
        Map<String, String> scheme2 = parser.getColorScheme();
        assertNotSame("getColorScheme should return a new map each time", scheme1, scheme2);
        assertEquals(scheme1, scheme2);
    }

    @Test
    public void emptyParserIsNotLoaded() {
        ThemeParser parser = new ThemeParser();
        assertFalse(parser.isThemeLoaded());
    }

    @Test(expected = IllegalStateException.class)
    public void getThemeColorThrowsWhenNotLoaded() {
        ThemeParser parser = new ThemeParser();
        parser.getThemeColor("accent1");
    }

    @Test(expected = IllegalStateException.class)
    public void getThemeFontThrowsWhenNotLoaded() {
        ThemeParser parser = new ThemeParser();
        parser.getThemeFont("major");
    }

    @Test
    public void getThemeNameDefaultsWhenNotSet() {
        ThemeParser parser = new ThemeParser();
        assertEquals("Default", parser.getThemeName());
    }

    @Test
    public void parseThemeFromFileReturnsFalseOnMissingFile() {
        ThemeParser parser = new ThemeParser();
        assertFalse(parser.parseTheme(new File("/nonexistent/theme.xml")));
        assertFalse(parser.isThemeLoaded());
    }

    @Test
    public void colorSchemeDoesNotAliasTx1Bg1() throws Exception {
        if (!TEST_FILE.exists()) fail("Required fixture not found: " + TEST_FILE.getAbsolutePath());
        PPTXDocument doc = PPTXDocument.loadFromZip(TEST_FILE);
        Document themeDom = doc.getXmlPart("ppt/theme/theme1.xml");

        ThemeParser parser = new ThemeParser();
        parser.parseTheme(themeDom);

        Map<String, String> scheme = parser.getColorScheme();
        // tx1/bg1 should NOT be in the color scheme — they depend on clrMap
        assertFalse("tx1 should not be aliased in theme", scheme.containsKey("tx1"));
        assertFalse("bg1 should not be aliased in theme", scheme.containsKey("bg1"));
        assertFalse("tx2 should not be aliased in theme", scheme.containsKey("tx2"));
        assertFalse("bg2 should not be aliased in theme", scheme.containsKey("bg2"));
    }

    @Test
    public void allTwelveSchemeColorsPresent() throws Exception {
        if (!TEST_FILE.exists()) fail("Required fixture not found: " + TEST_FILE.getAbsolutePath());
        PPTXDocument doc = PPTXDocument.loadFromZip(TEST_FILE);
        Document themeDom = doc.getXmlPart("ppt/theme/theme1.xml");

        ThemeParser parser = new ThemeParser();
        parser.parseTheme(themeDom);

        String[] required = {"dk1", "lt1", "dk2", "lt2",
            "accent1", "accent2", "accent3", "accent4", "accent5", "accent6",
            "hlink", "folHlink"};
        for (String name : required) {
            assertNotNull("Color " + name + " should be present", parser.getThemeColor(name));
        }
    }
}
