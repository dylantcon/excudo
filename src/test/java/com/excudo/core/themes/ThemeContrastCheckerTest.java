package com.excudo.core.themes;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests ThemeContrastChecker WCAG 2.1 contrast validation.
 */
public class ThemeContrastCheckerTest {

    @Test
    public void highContrastThemeProducesNoWarnings() {
        // Build a theme with maximum contrast: black text on white background
        ThemeDefinition theme = buildTheme(false, "#FFFFFF", "#000000", "#333333");
        List<String> warnings = ThemeContrastChecker.check(theme);
        assertTrue("High contrast theme should have no warnings, got: " + warnings,
            warnings.isEmpty());
    }

    @Test
    public void lowContrastThemeProducesWarnings() {
        // Light gray text on white background — poor contrast
        ThemeDefinition theme = buildTheme(false, "#FFFFFF", "#CCCCCC", "#DDDDDD");
        List<String> warnings = ThemeContrastChecker.check(theme);
        assertFalse("Low contrast theme should produce warnings", warnings.isEmpty());
    }

    @Test
    public void darkThemeChecksCorrectColors() {
        // Dark theme: bg1=dk1 (dark), tx1=lt1 (light)
        ThemeDefinition theme = buildTheme(true, "#1A1A1A", "#F0F0F0", "#E0E0E0");
        List<String> warnings = ThemeContrastChecker.check(theme);
        assertTrue("Well-contrasted dark theme should pass, got: " + warnings,
            warnings.isEmpty());
    }

    @Test
    public void getPassingThemeIdsReturnsNonNull() {
        List<String> passing = ThemeContrastChecker.getPassingThemeIds();
        assertNotNull(passing);
    }

    @Test
    public void nullColorSchemeReturnsIncompleteWarning() {
        // Theme with missing color values
        ThemeDefinition theme = buildThemeWithMissingColors();
        List<String> warnings = ThemeContrastChecker.check(theme);
        assertFalse("Missing colors should produce warnings", warnings.isEmpty());
        assertTrue("Should mention incomplete",
            warnings.stream().anyMatch(w -> w.contains("Incomplete") || w.contains("contrast")));
    }

    // ========== HELPERS ==========

    private ThemeDefinition buildTheme(boolean dark, String bg, String text, String text2) {
        java.util.Map<String, String> colors = new java.util.LinkedHashMap<>();
        if (dark) {
            colors.put("dk1", bg);
            colors.put("lt1", text);
            colors.put("dk2", "#2A2A2A");
            colors.put("lt2", text2);
        } else {
            colors.put("lt1", bg);
            colors.put("dk1", text);
            colors.put("lt2", "#F5F5F5");
            colors.put("dk2", text2);
        }
        if (dark) {
            // Light accents for good contrast against dark bg
            colors.put("accent1", "#7EB8FF");
            colors.put("accent2", "#FF9E9E");
            colors.put("accent3", "#A8E6A3");
            colors.put("accent4", "#FFD080");
            colors.put("accent5", "#8ED1E0");
            colors.put("accent6", "#C0E080");
        } else {
            // Dark accents for good contrast against white bg
            colors.put("accent1", "#2B579A");
            colors.put("accent2", "#9B2D30");
            colors.put("accent3", "#2D572C");
            colors.put("accent4", "#7B4F23");
            colors.put("accent5", "#1B4F72");
            colors.put("accent6", "#3B6B3A");
        }
        colors.put("hlink", "#0563C1");
        colors.put("folHlink", "#954F72");

        return ThemeDefinition.builder("test-theme")
            .displayName("Test Theme")
            .colorScheme(colors)
            .darkBackground(dark)
            .build();
    }

    private ThemeDefinition buildThemeWithMissingColors() {
        java.util.Map<String, String> colors = new java.util.LinkedHashMap<>();
        colors.put("accent1", "#4472C4");
        return ThemeDefinition.builder("broken-theme")
            .displayName("Broken Theme")
            .colorScheme(colors)
            .build();
    }
}
