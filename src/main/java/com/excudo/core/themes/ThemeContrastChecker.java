package com.excudo.core.themes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Evaluates a theme's color scheme for visibility issues using WCAG 2.1 contrast ratios.
 *
 * Checks all default color pairings that PowerPoint uses:
 *   - Title text (tx1) on slide background (bg1)
 *   - Body text (tx2) on slide background (bg1)
 *   - Default shape text (tx1) on each accent fill (accent1-6)
 *   - Each accent fill visibility against slide background (bg1)
 *
 * WCAG thresholds: 4.5:1 for normal text, 3.0:1 for large text (>= 18pt or 14pt bold).
 * PowerPoint titles are large text; body text at default sizes qualifies as large text.
 * We use 3.0:1 as the threshold since most presentation text is large.
 */
public class ThemeContrastChecker {

    private static final double LARGE_TEXT_THRESHOLD = 3.0;
    private static final String[] ACCENT_NAMES = {
        "accent1", "accent2", "accent3", "accent4", "accent5", "accent6"
    };

    /**
     * Check a theme for contrast issues. Returns a list of human-readable warnings.
     * Empty list means the theme passes all checks.
     */
    public static List<String> check(ThemeDefinition theme) {
        List<String> warnings = new ArrayList<>();
        Map<String, String> cs = theme.getColorScheme();
        boolean dark = theme.isDarkBackground();

        // Resolve clrMap: bg1, tx1, tx2 depend on darkBackground
        String bgColor = stripHash(dark ? cs.get("dk1") : cs.get("lt1"));
        String tx1Color = stripHash(dark ? cs.get("lt1") : cs.get("dk1"));
        String tx2Color = stripHash(dark ? cs.get("lt2") : cs.get("dk2"));

        if (bgColor == null || tx1Color == null || tx2Color == null) {
            warnings.add("Incomplete color scheme -- cannot evaluate contrast");
            return warnings;
        }

        // Check title text on background
        double tx1Contrast = contrastRatio(bgColor, tx1Color);
        if (tx1Contrast < LARGE_TEXT_THRESHOLD) {
            warnings.add(String.format("Title text (tx1 #%s) on background (#%s): contrast %.1f:1 (needs %.1f:1)",
                tx1Color, bgColor, tx1Contrast, LARGE_TEXT_THRESHOLD));
        }

        // Check body text on background
        double tx2Contrast = contrastRatio(bgColor, tx2Color);
        if (tx2Contrast < LARGE_TEXT_THRESHOLD) {
            warnings.add(String.format("Body text (tx2 #%s) on background (#%s): contrast %.1f:1 (needs %.1f:1)",
                tx2Color, bgColor, tx2Contrast, LARGE_TEXT_THRESHOLD));
        }

        // Check accent fill visibility against background (are shapes visible?)
        List<String> lowVisAccents = new ArrayList<>();
        for (String accentName : ACCENT_NAMES) {
            String accentColor = stripHash(cs.get(accentName));
            if (accentColor == null) continue;

            double fillOnBg = contrastRatio(bgColor, accentColor);
            if (fillOnBg < LARGE_TEXT_THRESHOLD) {
                lowVisAccents.add(accentName + " (#" + accentColor + ", " +
                    String.format("%.1f", fillOnBg) + ":1 vs background)");
            }
        }

        if (!lowVisAccents.isEmpty()) {
            warnings.add("Shape fills may be hard to see against background: " +
                String.join(", ", lowVisAccents));
        }

        return warnings;
    }

    /**
     * Get a list of theme IDs that pass all contrast checks.
     */
    public static List<String> getPassingThemeIds() {
        List<String> passing = new ArrayList<>();
        for (String id : ThemeLoader.getAvailableIds()) {
            try {
                ThemeDefinition theme = ThemeLoader.get(id);
                if (check(theme).isEmpty()) {
                    passing.add(id);
                }
            } catch (Exception ignored) {}
        }
        return passing;
    }

    // ========== WCAG 2.1 Contrast Ratio ==========

    static double contrastRatio(String hex1, String hex2) {
        double l1 = relativeLuminance(hex1);
        double l2 = relativeLuminance(hex2);
        double lighter = Math.max(l1, l2);
        double darker = Math.min(l1, l2);
        return (lighter + 0.05) / (darker + 0.05);
    }

    private static double relativeLuminance(String hex) {
        int r = Integer.parseInt(hex.substring(0, 2), 16);
        int g = Integer.parseInt(hex.substring(2, 4), 16);
        int b = Integer.parseInt(hex.substring(4, 6), 16);
        return 0.2126 * linearize(r / 255.0)
             + 0.7152 * linearize(g / 255.0)
             + 0.0722 * linearize(b / 255.0);
    }

    private static double linearize(double srgb) {
        return srgb <= 0.04045 ? srgb / 12.92 : Math.pow((srgb + 0.055) / 1.055, 2.4);
    }

    private static String stripHash(String color) {
        if (color == null) return null;
        return color.startsWith("#") ? color.substring(1) : color;
    }
}
