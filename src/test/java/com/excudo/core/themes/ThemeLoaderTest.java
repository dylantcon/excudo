package com.excudo.core.themes;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

/**
 * Tests for ThemeLoader (disk-based theme loading from JSON files)
 * and ThemeJsonSerializer (JSON round-trip serialization).
 */
public class ThemeLoaderTest {

    @Test
    public void loaderFindsProjectRoot_loadsThreeBuiltinThemes() {
        ThemeLoader.initialize();
        List<String> ids = ThemeLoader.getAvailableIds();
        assertTrue("Should have at least 3 themes", ids.size() >= 3);
        assertTrue(ThemeLoader.exists("minimal"));
        assertTrue(ThemeLoader.exists("corporate"));
        assertTrue(ThemeLoader.exists("academic"));
    }

    @Test
    public void loaderGet_returnsValidTheme() {
        ThemeLoader.initialize();
        ThemeDefinition theme = ThemeLoader.get("minimal");
        assertNotNull(theme);
        assertEquals("minimal", theme.getId());
        assertEquals("Minimal", theme.getDisplayName());
        assertTrue(theme.hasValidColorScheme());
        assertEquals(10, theme.getLayouts().size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void loaderGet_unknownTheme_throws() {
        ThemeLoader.initialize();
        ThemeLoader.get("nonexistent_theme_xyz");
    }

    @Test
    public void loaderGetAll_returnsAllThemes() {
        ThemeLoader.initialize();
        List<ThemeDefinition> all = ThemeLoader.getAll();
        assertFalse(all.isEmpty());
        for (ThemeDefinition t : all) {
            assertNotNull(t.getId());
            assertTrue(t.hasValidColorScheme());
        }
    }

    @Test
    public void jsonRoundTrip_preservesThemeData() {
        ThemeDefinition original = BundledThemes.get("corporate");
        String json = ThemeJsonSerializer.serialize(original);
        ThemeDefinition restored = ThemeJsonSerializer.deserialize(json);

        assertEquals(original.getId(), restored.getId());
        assertEquals(original.getDisplayName(), restored.getDisplayName());
        assertEquals(original.getMajorFont(), restored.getMajorFont());
        assertEquals(original.getMinorFont(), restored.getMinorFont());
        assertEquals(original.getBackgroundFillIndex(), restored.getBackgroundFillIndex());
        assertEquals(original.getColorScheme(), restored.getColorScheme());
        assertEquals(10, restored.getLayouts().size());

        // Verify text styles
        assertEquals(original.getTitleStyle().length, restored.getTitleStyle().length);
        assertEquals(original.getBodyStyle().length, restored.getBodyStyle().length);
        assertEquals(original.getOtherStyle().length, restored.getOtherStyle().length);

        // Spot-check title level 0
        assertEquals(original.getTitleStyle()[0].getFontSize(), restored.getTitleStyle()[0].getFontSize());
        assertEquals(original.getTitleStyle()[0].getColorRef(), restored.getTitleStyle()[0].getColorRef());

        // Spot-check body level 0 bullet
        assertEquals(original.getBodyStyle()[0].hasBullet(), restored.getBodyStyle()[0].hasBullet());
        assertEquals(original.getBodyStyle()[0].getBulletChar(), restored.getBodyStyle()[0].getBulletChar());
    }

    @Test
    public void jsonRoundTrip_preservesFallbackFonts() {
        ThemeDefinition original = BundledThemes.get("academic");
        String json = ThemeJsonSerializer.serialize(original);
        ThemeDefinition restored = ThemeJsonSerializer.deserialize(json);

        assertEquals("Georgia", restored.getMajorFont());
        assertEquals("Garamond", restored.getMajorFontFallback());
        assertEquals("Garamond", restored.getMinorFontFallback());
    }

    @Test
    public void jsonDeserialize_fromDiskFile_matchesHardcoded() throws IOException {
        // Find themes/builtin/minimal.json from project root
        File projectRoot = findProjectRoot();
        if (projectRoot == null) {
            System.out.println("SKIP: project root not found for disk file test");
            return;
        }
        File jsonFile = new File(projectRoot, "themes/builtin/minimal.json");
        assertTrue("minimal.json should exist", jsonFile.exists());

        String content = Files.readString(jsonFile.toPath());
        ThemeDefinition fromDisk = ThemeJsonSerializer.deserialize(content);
        ThemeDefinition hardcoded = BundledThemes.get("minimal");

        assertEquals(hardcoded.getId(), fromDisk.getId());
        assertEquals(hardcoded.getDisplayName(), fromDisk.getDisplayName());
        assertEquals(hardcoded.getColorScheme(), fromDisk.getColorScheme());
        assertEquals(hardcoded.getMajorFont(), fromDisk.getMajorFont());
        assertEquals(hardcoded.getMinorFont(), fromDisk.getMinorFont());
        assertEquals(hardcoded.getMajorFontFallback(), fromDisk.getMajorFontFallback());
    }

    @Test
    public void customTheme_saveAndDelete() throws IOException {
        File tempDir = Files.createTempDirectory("theme-test").toFile();
        try {
            // Set up directory structure
            new File(tempDir, "themes/builtin").mkdirs();
            // Copy a builtin theme
            String minimalJson = ThemeJsonSerializer.serialize(BundledThemes.get("minimal"));
            Files.writeString(new File(tempDir, "themes/builtin/minimal.json").toPath(), minimalJson);

            ThemeLoader.initialize(tempDir);
            assertTrue(ThemeLoader.exists("minimal"));

            // Create a custom theme
            ThemeDefinition custom = ThemeDefinition.builder("custom-test")
                    .displayName("Custom Test")
                    .colorScheme(BundledThemes.get("minimal").getColorScheme())
                    .majorFont("Arial")
                    .minorFont("Arial")
                    .titleStyle(BundledThemes.get("minimal").getTitleStyle())
                    .bodyStyle(BundledThemes.get("minimal").getBodyStyle())
                    .otherStyle(BundledThemes.get("minimal").getOtherStyle())
                    .layouts(BundledThemes.createDefaultLayouts())
                    .backgroundFillIndex(1)
                    .build();

            ThemeLoader.saveCustomTheme(custom);
            assertTrue(ThemeLoader.exists("custom-test"));
            assertTrue(ThemeLoader.isCustomTheme("custom-test"));

            // Delete custom theme
            assertTrue(ThemeLoader.deleteCustomTheme("custom-test"));
            assertFalse(ThemeLoader.exists("custom-test"));

        } finally {
            // Re-initialize with real project root
            ThemeLoader.initialize();
            deleteRecursive(tempDir);
        }
    }

    @Test
    public void loaderFallsBackToHardcoded_whenNoDiskThemes() {
        File nonExistentRoot = new File("/tmp/nonexistent-theme-root-" + System.nanoTime());
        ThemeLoader.initialize(nonExistentRoot);
        // Should fall back to hardcoded themes
        assertTrue(ThemeLoader.exists("minimal"));
        assertTrue(ThemeLoader.exists("corporate"));
        assertTrue(ThemeLoader.exists("academic"));
        // Re-initialize properly
        ThemeLoader.initialize();
    }

    private static File findProjectRoot() {
        File cwd = new File(System.getProperty("user.dir"));
        File dir = cwd;
        while (dir != null) {
            if (new File(dir, "themes").isDirectory()) return dir;
            dir = dir.getParentFile();
        }
        return null;
    }

    private static void deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursive(child);
            }
        }
        file.delete();
    }
}
