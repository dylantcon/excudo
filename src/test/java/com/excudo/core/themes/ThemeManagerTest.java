package com.excudo.core.themes;

import com.excudo.core.model.PPTXDocument;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;

/**
 * Tests per-document ThemeManager color/font resolution.
 */
public class ThemeManagerTest {

    private static final File TEST_FILE = resolveTestFile();

    private static File resolveTestFile() {
        File f = new File("test-pptx-samples/generalist_test_file.pptx");
        if (!f.exists()) f = new File("../../../test-pptx-samples/generalist_test_file.pptx");
        return f;
    }

    @Test
    public void initializeFromPPTXLoadsTheme() throws Exception {
        if (!TEST_FILE.exists()) fail("Required fixture not found: " + TEST_FILE.getAbsolutePath());
        PPTXDocument doc = PPTXDocument.loadFromZip(TEST_FILE);
        ThemeManager tm = ThemeManager.forDocument(doc);

        assertTrue("Theme should be loaded", tm.isThemeLoaded());
        assertNotNull(tm.getCurrentThemeName());
        assertFalse("Theme name should not be default placeholder",
            tm.getCurrentThemeName().contains("No Theme"));
    }

    @Test
    public void getThemeColorReturnsAccentColors() throws Exception {
        if (!TEST_FILE.exists()) fail("Required fixture not found: " + TEST_FILE.getAbsolutePath());
        PPTXDocument doc = PPTXDocument.loadFromZip(TEST_FILE);
        ThemeManager tm = ThemeManager.forDocument(doc);

        com.excudo.core.themes.HexColor accent1 = tm.getThemeColor("accent1");
        assertNotNull(accent1);
        assertEquals("#569CD6", accent1.withHash());
        assertEquals("569CD6", accent1.bare());
    }

    @Test
    public void getThemeColorReturnsDkLtColors() throws Exception {
        if (!TEST_FILE.exists()) fail("Required fixture not found: " + TEST_FILE.getAbsolutePath());
        PPTXDocument doc = PPTXDocument.loadFromZip(TEST_FILE);
        ThemeManager tm = ThemeManager.forDocument(doc);

        assertNotNull(tm.getThemeColor("dk1"));
        assertNotNull(tm.getThemeColor("lt1"));
        assertNotNull(tm.getThemeColor("dk2"));
        assertNotNull(tm.getThemeColor("lt2"));
    }

    @Test
    public void getThemeFontReturnsFonts() throws Exception {
        if (!TEST_FILE.exists()) fail("Required fixture not found: " + TEST_FILE.getAbsolutePath());
        PPTXDocument doc = PPTXDocument.loadFromZip(TEST_FILE);
        ThemeManager tm = ThemeManager.forDocument(doc);

        assertNotNull(tm.getThemeFont("major"));
        assertNotNull(tm.getThemeFont("minor"));
    }

    @Test
    public void getThemeInfoContainsColorData() throws Exception {
        if (!TEST_FILE.exists()) fail("Required fixture not found: " + TEST_FILE.getAbsolutePath());
        PPTXDocument doc = PPTXDocument.loadFromZip(TEST_FILE);
        ThemeManager tm = ThemeManager.forDocument(doc);

        String info = tm.getThemeInfo();
        assertNotNull(info);
        assertTrue("Info should mention accent1", info.contains("accent1"));
    }

    @Test
    public void openDecksKeepTheirOwnThemes() throws Exception {
        PPTXDocument minimal = com.excudo.core.orchestration.PresentationScaffolder.scaffoldDocument("minimal");
        PPTXDocument corporate = com.excudo.core.orchestration.PresentationScaffolder.scaffoldDocument("corporate");

        assertEquals("Minimal", ThemeManager.forDocument(minimal).getCurrentThemeName());
        assertEquals("Corporate", ThemeManager.forDocument(corporate).getCurrentThemeName());
        assertEquals("Inter", ThemeManager.forDocument(minimal).getThemeFont("major"));
        assertEquals("Calibri", ThemeManager.forDocument(corporate).getThemeFont("major"));
        assertSame(ThemeManager.forDocument(minimal), ThemeManager.forDocument(minimal));
    }

    @Test
    public void getAvailableThemesReturnsNonEmpty() {
        java.util.List<ThemeDefinition> themes = ThemeManager.getAvailableThemes();
        assertNotNull(themes);
        assertFalse("Should have at least one bundled theme", themes.isEmpty());
    }

    @Test
    public void getCurrentThemeNameWhenNotLoaded() {
        String name = ThemeManager.forDocument(null).getCurrentThemeName();
        assertTrue("Should indicate no theme loaded",
            name.contains("No Theme") || name.equals("Default"));
    }
}
