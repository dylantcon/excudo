package com.excudo.core.themes;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;

/**
 * Tests for ThemeDefinition data model and BundledThemes registry.
 */
public class ThemeDefinitionTest {

    private static final long SLIDE_WIDTH = 12192000L;
    private static final long SLIDE_HEIGHT = 6858000L;
    private static final String[] REQUIRED_COLORS = {
            "dk1", "lt1", "dk2", "lt2",
            "accent1", "accent2", "accent3", "accent4", "accent5", "accent6",
            "hlink", "folHlink"
    };

    @Test
    public void allBundledThemesHaveValidColorSchemes() {
        for (ThemeDefinition theme : BundledThemes.getAll()) {
            assertTrue("Theme '" + theme.getId() + "' should have valid color scheme",
                    theme.hasValidColorScheme());

            Map<String, String> colors = theme.getColorScheme();
            assertEquals("Theme '" + theme.getId() + "' should have 12 colors",
                    12, colors.size());

            for (String key : REQUIRED_COLORS) {
                String hex = colors.get(key);
                assertNotNull("Theme '" + theme.getId() + "' missing color: " + key, hex);
                assertTrue("Color '" + key + "' in theme '" + theme.getId() + "' should be valid hex: " + hex,
                        hex.matches("[0-9A-Fa-f]{6}"));
            }
        }
    }

    @Test
    public void allBundledThemesHaveNonNullFonts() {
        for (ThemeDefinition theme : BundledThemes.getAll()) {
            assertNotNull("Theme '" + theme.getId() + "' missing majorFont", theme.getMajorFont());
            assertNotNull("Theme '" + theme.getId() + "' missing minorFont", theme.getMinorFont());
            assertFalse("Theme '" + theme.getId() + "' majorFont should not be empty",
                    theme.getMajorFont().isEmpty());
            assertFalse("Theme '" + theme.getId() + "' minorFont should not be empty",
                    theme.getMinorFont().isEmpty());
        }
    }

    @Test
    public void allBundledThemesHave10Layouts() {
        for (ThemeDefinition theme : BundledThemes.getAll()) {
            assertEquals("Theme '" + theme.getId() + "' should have 10 layouts",
                    10, theme.getLayouts().size());
        }
    }

    @Test
    public void allLayoutPlaceholdersWithinSlideBounds() {
        for (ThemeDefinition theme : BundledThemes.getAll()) {
            for (LayoutDefinition layout : theme.getLayouts()) {
                for (PlaceholderDefinition ph : layout.getPlaceholders()) {
                    assertTrue("Placeholder '" + ph.getType() + "' in layout '"
                            + layout.getMatchingName() + "' of theme '" + theme.getId()
                            + "' should be within slide bounds (x=" + ph.getX() + ", y=" + ph.getY()
                            + ", cx=" + ph.getCx() + ", cy=" + ph.getCy() + ")",
                            ph.isWithinSlideBounds());
                }
            }
        }
    }

    @Test
    public void allBundledThemesHaveTitleAndBodyStyles() {
        for (ThemeDefinition theme : BundledThemes.getAll()) {
            assertNotNull("Theme '" + theme.getId() + "' missing titleStyle", theme.getTitleStyle());
            assertNotNull("Theme '" + theme.getId() + "' missing bodyStyle", theme.getBodyStyle());
            assertNotNull("Theme '" + theme.getId() + "' missing otherStyle", theme.getOtherStyle());

            assertTrue("Theme '" + theme.getId() + "' titleStyle should have 9 levels",
                    theme.getTitleStyle().length == 9);
            assertTrue("Theme '" + theme.getId() + "' bodyStyle should have 9 levels",
                    theme.getBodyStyle().length == 9);
            assertTrue("Theme '" + theme.getId() + "' otherStyle should have 9 levels",
                    theme.getOtherStyle().length == 9);
        }
    }

    @Test
    public void bundledThemesRegistryCorrect() {
        assertEquals(3, BundledThemes.getAll().size());
        assertTrue(BundledThemes.exists("minimal"));
        assertTrue(BundledThemes.exists("corporate"));
        assertTrue(BundledThemes.exists("academic"));
        assertFalse(BundledThemes.exists("nonexistent"));

        List<String> ids = BundledThemes.getAvailableIds();
        assertEquals(3, ids.size());
        assertTrue(ids.contains("minimal"));
        assertTrue(ids.contains("corporate"));
        assertTrue(ids.contains("academic"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void unknownThemeThrows() {
        BundledThemes.get("nonexistent");
    }

    @Test
    public void textLevelStyleBuilder() {
        TextLevelStyle style = TextLevelStyle.builder(0)
                .fontSizePt(44)
                .bold(true)
                .colorRef("tx1")
                .bullet("*", "Wingdings")
                .marginLeft(228600)
                .indent(-228600)
                .lineSpacing(120000)
                .spaceBefore(500)
                .alignment("ctr")
                .build();

        assertEquals(0, style.getLevel());
        assertEquals(4400, style.getFontSize());
        assertTrue(style.isBold());
        assertEquals("tx1", style.getColorRef());
        assertTrue(style.isSchemeColor());
        assertNull(style.getHexColor());
        assertEquals("*", style.getBulletChar());
        assertEquals("Wingdings", style.getBulletFont());
        assertTrue(style.hasBullet());
        assertEquals(228600, style.getMarginLeft());
        assertEquals(-228600, style.getIndent());
        assertEquals(120000, style.getLineSpacing());
        assertEquals(500, style.getSpaceBefore());
        assertEquals("ctr", style.getAlignment());
    }

    @Test
    public void textLevelStyleHexColor() {
        TextLevelStyle style = TextLevelStyle.builder(1)
                .hexColor("FF0000")
                .build();

        assertFalse(style.isSchemeColor());
        assertEquals("FF0000", style.getHexColor());
    }

    @Test
    public void toBuilder_roundTrip() {
        ThemeDefinition original = BundledThemes.get("corporate");
        ThemeDefinition copy = original.toBuilder().build();

        assertEquals(original.getId(), copy.getId());
        assertEquals(original.getDisplayName(), copy.getDisplayName());
        assertEquals(original.getColorScheme(), copy.getColorScheme());
        assertEquals(original.getMajorFont(), copy.getMajorFont());
        assertEquals(original.getMinorFont(), copy.getMinorFont());
        assertEquals(original.getLayouts().size(), copy.getLayouts().size());
        assertEquals(original.getBackgroundFillIndex(), copy.getBackgroundFillIndex());
    }

    @Test
    public void toBuilder_withModifications() {
        ThemeDefinition original = BundledThemes.get("minimal");
        ThemeDefinition modified = original.toBuilder()
                .id("custom-minimal")
                .displayName("Custom Minimal")
                .majorFont("Georgia")
                .build();

        assertEquals("custom-minimal", modified.getId());
        assertEquals("Custom Minimal", modified.getDisplayName());
        assertEquals("Georgia", modified.getMajorFont());
        // Other fields should be preserved from original
        assertEquals(original.getMinorFont(), modified.getMinorFont());
        assertEquals(original.getColorScheme(), modified.getColorScheme());
        assertEquals(original.getLayouts().size(), modified.getLayouts().size());
    }

    @Test
    public void layoutDefinitionTypes() {
        ThemeDefinition minimal = BundledThemes.get("minimal");
        List<LayoutDefinition> layouts = minimal.getLayouts();

        // First layout should be Title Slide
        assertEquals(LayoutDefinition.LayoutType.TITLE_SLIDE, layouts.get(0).getLayoutType());
        assertEquals("Title Slide", layouts.get(0).getMatchingName());
        assertTrue(layouts.get(0).hasPlaceholderOfType("ctrTitle"));

        // 7th layout should be Blank
        assertEquals(LayoutDefinition.LayoutType.BLANK, layouts.get(6).getLayoutType());
        assertEquals(0, layouts.get(6).getPlaceholderCount());
    }
}
