package com.excudo.core.themes;

import com.excudo.core.model.PPTXDocument;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;

/**
 * Tests ThemeManager static initialization and color/font resolution.
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
        if (!TEST_FILE.exists()) return;
        PPTXDocument doc = PPTXDocument.loadFromZip(TEST_FILE);
        ThemeManager.initialize(doc);

        assertTrue("Theme should be loaded after initialize", ThemeManager.isThemeLoaded());
        assertNotNull(ThemeManager.getCurrentThemeName());
        assertFalse("Theme name should not be default placeholder",
            ThemeManager.getCurrentThemeName().contains("No Theme"));
    }

    @Test
    public void getThemeColorReturnsAccentColors() throws Exception {
        if (!TEST_FILE.exists()) return;
        PPTXDocument doc = PPTXDocument.loadFromZip(TEST_FILE);
        ThemeManager.initialize(doc);

        String accent1 = ThemeManager.getThemeColor("accent1");
        assertNotNull(accent1);
        assertEquals("#569CD6", accent1);
    }

    @Test
    public void getThemeColorReturnsDkLtColors() throws Exception {
        if (!TEST_FILE.exists()) return;
        PPTXDocument doc = PPTXDocument.loadFromZip(TEST_FILE);
        ThemeManager.initialize(doc);

        assertNotNull(ThemeManager.getThemeColor("dk1"));
        assertNotNull(ThemeManager.getThemeColor("lt1"));
        assertNotNull(ThemeManager.getThemeColor("dk2"));
        assertNotNull(ThemeManager.getThemeColor("lt2"));
    }

    @Test
    public void getThemeFontReturnsFonts() throws Exception {
        if (!TEST_FILE.exists()) return;
        PPTXDocument doc = PPTXDocument.loadFromZip(TEST_FILE);
        ThemeManager.initialize(doc);

        assertNotNull(ThemeManager.getThemeFont("major"));
        assertNotNull(ThemeManager.getThemeFont("minor"));
    }

    @Test
    public void getThemeInfoContainsColorData() throws Exception {
        if (!TEST_FILE.exists()) return;
        PPTXDocument doc = PPTXDocument.loadFromZip(TEST_FILE);
        ThemeManager.initialize(doc);

        String info = ThemeManager.getThemeInfo();
        assertNotNull(info);
        assertTrue("Info should mention accent1", info.contains("accent1"));
    }

    @Test
    public void getAvailableThemesReturnsNonEmpty() {
        java.util.List<ThemeDefinition> themes = ThemeManager.getAvailableThemes();
        assertNotNull(themes);
        assertFalse("Should have at least one bundled theme", themes.isEmpty());
    }

    @Test
    public void getCurrentThemeNameWhenNotLoaded() {
        // Force unloaded state
        ThemeManager.initialize(null);
        String name = ThemeManager.getCurrentThemeName();
        assertTrue("Should indicate no theme loaded",
            name.contains("No Theme") || name.equals("Default"));
    }
}
