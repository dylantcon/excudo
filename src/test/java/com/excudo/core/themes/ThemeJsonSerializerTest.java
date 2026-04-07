package com.excudo.core.themes;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.List;

/**
 * Tests for ThemeJsonSerializer layout round-trip and backward compatibility.
 */
public class ThemeJsonSerializerTest {

    @Test
    public void roundTrip_preservesLayouts_allThemes() {
        for (ThemeDefinition original : BundledThemes.getAll()) {
            String json = ThemeJsonSerializer.serialize(original);
            ThemeDefinition restored = ThemeJsonSerializer.deserialize(json);

            assertEquals("Theme " + original.getId() + " layout count",
                    original.getLayouts().size(), restored.getLayouts().size());

            for (int i = 0; i < original.getLayouts().size(); i++) {
                LayoutDefinition origLayout = original.getLayouts().get(i);
                LayoutDefinition restLayout = restored.getLayouts().get(i);

                assertEquals("Layout ID mismatch at index " + i,
                        origLayout.getLayoutId(), restLayout.getLayoutId());
                assertEquals("Matching name mismatch at index " + i,
                        origLayout.getMatchingName(), restLayout.getMatchingName());
                assertEquals("Layout type mismatch at index " + i,
                        origLayout.getLayoutType(), restLayout.getLayoutType());
                assertEquals("Preserve mismatch at index " + i,
                        origLayout.isPreserve(), restLayout.isPreserve());
                assertEquals("Placeholder count mismatch at index " + i,
                        origLayout.getPlaceholderCount(), restLayout.getPlaceholderCount());
            }
        }
    }

    @Test
    public void roundTrip_preservesPlaceholderGeometry() {
        ThemeDefinition original = BundledThemes.get("minimal");
        String json = ThemeJsonSerializer.serialize(original);
        ThemeDefinition restored = ThemeJsonSerializer.deserialize(json);

        // Check Title Slide placeholders (slideLayout1)
        LayoutDefinition origLayout = original.getLayouts().get(0);
        LayoutDefinition restLayout = restored.getLayouts().get(0);

        assertEquals(origLayout.getPlaceholderCount(), restLayout.getPlaceholderCount());

        for (int i = 0; i < origLayout.getPlaceholders().size(); i++) {
            PlaceholderDefinition origPh = origLayout.getPlaceholders().get(i);
            PlaceholderDefinition restPh = restLayout.getPlaceholders().get(i);

            assertEquals("Type mismatch", origPh.getType(), restPh.getType());
            assertEquals("Idx mismatch", origPh.getIdx(), restPh.getIdx());
            assertEquals("X mismatch", origPh.getX(), restPh.getX());
            assertEquals("Y mismatch", origPh.getY(), restPh.getY());
            assertEquals("CX mismatch", origPh.getCx(), restPh.getCx());
            assertEquals("CY mismatch", origPh.getCy(), restPh.getCy());
        }
    }

    @Test
    public void backwardCompat_oldJsonWithoutLayouts_usesDefaults() {
        // Simulate old JSON format without layouts key
        String oldJson = "{\n"
                + "  \"id\": \"test-old\",\n"
                + "  \"displayName\": \"Test Old\",\n"
                + "  \"colorScheme\": {\n"
                + "    \"dk1\": \"000000\", \"lt1\": \"FFFFFF\",\n"
                + "    \"dk2\": \"333333\", \"lt2\": \"F5F5F5\",\n"
                + "    \"accent1\": \"4472C4\", \"accent2\": \"ED7D31\",\n"
                + "    \"accent3\": \"A5A5A5\", \"accent4\": \"FFC000\",\n"
                + "    \"accent5\": \"5B9BD5\", \"accent6\": \"70AD47\",\n"
                + "    \"hlink\": \"0563C1\", \"folHlink\": \"954F72\"\n"
                + "  },\n"
                + "  \"majorFont\": \"Calibri\",\n"
                + "  \"minorFont\": \"Calibri\",\n"
                + "  \"backgroundFillIndex\": 1,\n"
                + "  \"titleStyle\": [],\n"
                + "  \"bodyStyle\": [],\n"
                + "  \"otherStyle\": []\n"
                + "}\n";

        ThemeDefinition theme = ThemeJsonSerializer.deserialize(oldJson);
        assertEquals("test-old", theme.getId());
        // Should fall back to default layouts
        assertEquals(10, theme.getLayouts().size());
        assertEquals("slideLayout1", theme.getLayouts().get(0).getLayoutId());
    }

    @Test
    public void emuPrecision_preserved() {
        ThemeDefinition original = BundledThemes.get("corporate");
        String json = ThemeJsonSerializer.serialize(original);
        ThemeDefinition restored = ThemeJsonSerializer.deserialize(json);

        // Check specific EMU values are preserved exactly
        LayoutDefinition titleSlide = restored.getLayouts().get(0);
        PlaceholderDefinition ctrTitle = titleSlide.getPlaceholders().get(0);

        assertEquals(1524000L, ctrTitle.getX());
        assertEquals(1122363L, ctrTitle.getY());
        assertEquals(9144000L, ctrTitle.getCx());
        assertEquals(2387600L, ctrTitle.getCy());
    }
}
