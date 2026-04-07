package com.excudo.core.themes;

import com.excudo.core.themes.LayoutDefinition.LayoutType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Static factory providing 3 bundled theme definitions:
 * - Minimal: Ultra-clean black/white, sans-serif, high contrast
 * - Corporate: Blue/grey, professional Calibri, standard corporate look
 * - Academic: Serif-based, muted earth tones, research-appropriate
 *
 * Each theme includes a complete color scheme (12 colors), font scheme,
 * text styles (title/body/other x 9 levels), and 10 standard layouts.
 */
public final class BundledThemes {

    private static final Map<String, ThemeDefinition> THEMES;

    static {
        Map<String, ThemeDefinition> themes = new LinkedHashMap<>();
        themes.put("minimal", createMinimal());
        themes.put("corporate", createCorporate());
        themes.put("academic", createAcademic());
        THEMES = Collections.unmodifiableMap(themes);
    }

    private BundledThemes() {}

    public static ThemeDefinition get(String id) {
        ThemeDefinition theme = THEMES.get(id.toLowerCase());
        if (theme == null) {
            throw new IllegalArgumentException("Unknown theme: '" + id + "'. Available: " + getAvailableIds());
        }
        return theme;
    }

    public static List<ThemeDefinition> getAll() {
        return new ArrayList<>(THEMES.values());
    }

    public static List<String> getAvailableIds() {
        return new ArrayList<>(THEMES.keySet());
    }

    public static boolean exists(String id) {
        return THEMES.containsKey(id.toLowerCase());
    }

    // ==================== MINIMAL THEME ====================

    private static ThemeDefinition createMinimal() {
        Map<String, String> colors = new LinkedHashMap<>();
        colors.put("dk1", "1A1A1A");
        colors.put("lt1", "FFFFFF");
        colors.put("dk2", "333333");
        colors.put("lt2", "F5F5F5");
        colors.put("accent1", "2D2D2D");
        colors.put("accent2", "767676");
        colors.put("accent3", "808080");
        colors.put("accent4", "4A90D9");
        colors.put("accent5", "D94A4A");
        colors.put("accent6", "2EA85B");
        colors.put("hlink", "4A90D9");
        colors.put("folHlink", "767676");

        return ThemeDefinition.builder("minimal")
                .displayName("Minimal")
                .colorScheme(colors)
                .majorFont("Inter")
                .minorFont("Inter")
                .majorFontFallback("Helvetica Neue")
                .minorFontFallback("Helvetica Neue")
                .titleStyle(minimalTitleStyle())
                .bodyStyle(minimalBodyStyle())
                .otherStyle(minimalOtherStyle())
                .layouts(createMinimalLayouts())
                .backgroundFillIndex(1)
                .build();
    }

    private static TextLevelStyle[] minimalTitleStyle() {
        TextLevelStyle[] styles = new TextLevelStyle[9];
        styles[0] = TextLevelStyle.builder(0).fontSizePt(44).bold(false).colorRef("tx1").noBullet().alignment("l").build();
        for (int i = 1; i < 9; i++) {
            styles[i] = TextLevelStyle.builder(i).fontSizePt(18).colorRef("tx1").noBullet().build();
        }
        return styles;
    }

    private static TextLevelStyle[] minimalBodyStyle() {
        TextLevelStyle[] styles = new TextLevelStyle[9];
        int[] sizes = {24, 22, 20, 18, 18, 16, 16, 14, 14};
        for (int i = 0; i < 9; i++) {
            styles[i] = TextLevelStyle.builder(i)
                    .fontSizePt(sizes[i])
                    .colorRef("tx2")
                    .bullet("\u2013", "Arial")  // en dash
                    .marginLeft(457200 * (i + 1))
                    .indent(-228600)
                    .lineSpacing(110000)
                    .spaceBefore(400)
                    .build();
        }
        return styles;
    }

    private static TextLevelStyle[] minimalOtherStyle() {
        TextLevelStyle[] styles = new TextLevelStyle[9];
        for (int i = 0; i < 9; i++) {
            styles[i] = TextLevelStyle.builder(i)
                    .fontSizePt(18)
                    .colorRef("tx1")
                    .noBullet()
                    .marginLeft(457200 * i)
                    .build();
        }
        return styles;
    }

    // ==================== CORPORATE THEME ====================

    private static ThemeDefinition createCorporate() {
        Map<String, String> colors = new LinkedHashMap<>();
        colors.put("dk1", "1F3864");
        colors.put("lt1", "FFFFFF");
        colors.put("dk2", "2E5090");
        colors.put("lt2", "D6DCE4");
        colors.put("accent1", "4472C4");
        colors.put("accent2", "D06A1F");
        colors.put("accent3", "808080");
        colors.put("accent4", "B88A00");
        colors.put("accent5", "4A8AC4");
        colors.put("accent6", "5D9638");
        colors.put("hlink", "0563C1");
        colors.put("folHlink", "954F72");

        return ThemeDefinition.builder("corporate")
                .displayName("Corporate")
                .colorScheme(colors)
                .majorFont("Calibri")
                .minorFont("Calibri")
                .titleStyle(corporateTitleStyle())
                .bodyStyle(corporateBodyStyle())
                .otherStyle(corporateOtherStyle())
                .layouts(createCorporateLayouts())
                .backgroundFillIndex(1)
                .build();
    }

    private static TextLevelStyle[] corporateTitleStyle() {
        TextLevelStyle[] styles = new TextLevelStyle[9];
        styles[0] = TextLevelStyle.builder(0).fontSizePt(44).bold(false).colorRef("tx1").noBullet().alignment("l").build();
        for (int i = 1; i < 9; i++) {
            styles[i] = TextLevelStyle.builder(i).fontSizePt(18).colorRef("tx1").noBullet().build();
        }
        return styles;
    }

    private static TextLevelStyle[] corporateBodyStyle() {
        TextLevelStyle[] styles = new TextLevelStyle[9];
        int[] sizes = {24, 22, 20, 18, 18, 16, 16, 14, 14};
        // Standard Office indentation: 228600 EMU per level
        for (int i = 0; i < 9; i++) {
            styles[i] = TextLevelStyle.builder(i)
                    .fontSizePt(sizes[i])
                    .colorRef("tx2")
                    .bullet("\u2022", "Arial")  // bullet point
                    .marginLeft(228600 * (i + 1))
                    .indent(-228600)
                    .lineSpacing(100000)
                    .spaceBefore(500)
                    .build();
        }
        return styles;
    }

    private static TextLevelStyle[] corporateOtherStyle() {
        TextLevelStyle[] styles = new TextLevelStyle[9];
        for (int i = 0; i < 9; i++) {
            styles[i] = TextLevelStyle.builder(i)
                    .fontSizePt(18)
                    .colorRef("tx1")
                    .noBullet()
                    .marginLeft(228600 * i)
                    .build();
        }
        return styles;
    }

    // ==================== ACADEMIC THEME ====================

    private static ThemeDefinition createAcademic() {
        Map<String, String> colors = new LinkedHashMap<>();
        colors.put("dk1", "2C2C2C");
        colors.put("lt1", "FFFEF5");
        colors.put("dk2", "4A3728");
        colors.put("lt2", "F0E8D8");
        colors.put("accent1", "8B4513");
        colors.put("accent2", "2E5E4E");
        colors.put("accent3", "8B7355");
        colors.put("accent4", "A5804F");
        colors.put("accent5", "4E7A5E");
        colors.put("accent6", "A0522D");
        colors.put("hlink", "1E4D8C");
        colors.put("folHlink", "6B4226");

        return ThemeDefinition.builder("academic")
                .displayName("Academic")
                .colorScheme(colors)
                .majorFont("Georgia")
                .minorFont("Georgia")
                .majorFontFallback("Garamond")
                .minorFontFallback("Garamond")
                .titleStyle(academicTitleStyle())
                .bodyStyle(academicBodyStyle())
                .otherStyle(academicOtherStyle())
                .layouts(createAcademicLayouts())
                .backgroundFillIndex(1)
                .build();
    }

    private static TextLevelStyle[] academicTitleStyle() {
        TextLevelStyle[] styles = new TextLevelStyle[9];
        styles[0] = TextLevelStyle.builder(0).fontSizePt(40).bold(false).colorRef("tx1").noBullet().alignment("l").build();
        for (int i = 1; i < 9; i++) {
            styles[i] = TextLevelStyle.builder(i).fontSizePt(18).colorRef("tx1").noBullet().build();
        }
        return styles;
    }

    private static TextLevelStyle[] academicBodyStyle() {
        TextLevelStyle[] styles = new TextLevelStyle[9];
        int[] sizes = {22, 20, 18, 18, 16, 16, 14, 14, 14};
        for (int i = 0; i < 9; i++) {
            styles[i] = TextLevelStyle.builder(i)
                    .fontSizePt(sizes[i])
                    .colorRef("tx2")
                    .bullet("\u2022", "Arial")  // bullet point
                    .marginLeft(342900 * (i + 1))
                    .indent(-228600)
                    .lineSpacing(120000)
                    .spaceBefore(600)
                    .build();
        }
        return styles;
    }

    private static TextLevelStyle[] academicOtherStyle() {
        TextLevelStyle[] styles = new TextLevelStyle[9];
        for (int i = 0; i < 9; i++) {
            styles[i] = TextLevelStyle.builder(i)
                    .fontSizePt(16)
                    .colorRef("tx1")
                    .noBullet()
                    .marginLeft(342900 * i)
                    .build();
        }
        return styles;
    }

    // ==================== STANDARD LAYOUTS ====================

    /**
     * Returns the hardcoded theme definitions as a map.
     * Used as fallback when theme JSON files are not found on disk.
     */
    public static Map<String, ThemeDefinition> getHardcodedThemes() {
        return new LinkedHashMap<>(THEMES);
    }

    /**
     * Per-theme layout factories. Currently identical, decoupled so themes can diverge.
     */
    public static List<LayoutDefinition> createMinimalLayouts() {
        return createDefaultLayouts();
    }

    public static List<LayoutDefinition> createCorporateLayouts() {
        return createDefaultLayouts();
    }

    public static List<LayoutDefinition> createAcademicLayouts() {
        return createDefaultLayouts();
    }

    /**
     * Default 10-layout set. Public fallback for deserializing old JSON without layout data.
     * Geometry is in EMUs (914400 per inch). Slide dimensions: 12192000 x 6858000 EMU.
     */
    public static List<LayoutDefinition> createDefaultLayouts() {
        List<LayoutDefinition> layouts = new ArrayList<>();

        // Standard geometry constants
        long slideW = 12192000L;
        long margin = 838200L;  // ~0.92 inches
        long contentW = slideW - (2 * margin);  // 10515600
        long titleY = 365125L;
        long titleH = 1325563L;
        long bodyY = 1825625L;
        long bodyH = 4351338L;
        long halfW = (contentW - margin) / 2;  // half width for two-column layouts

        // 1. Title Slide
        layouts.add(new LayoutDefinition("slideLayout1", "Title Slide", LayoutType.TITLE_SLIDE,
                List.of(
                        new PlaceholderDefinition("ctrTitle", null, 1524000L, 1122363L, 9144000L, 2387600L),
                        new PlaceholderDefinition("subTitle", null, 1524000L, 3602038L, 9144000L, 1655762L)
                ), true));

        // 2. Title + Content
        layouts.add(new LayoutDefinition("slideLayout2", "Title, Content", LayoutType.TITLE_CONTENT,
                List.of(
                        new PlaceholderDefinition("title", null, margin, titleY, contentW, titleH),
                        new PlaceholderDefinition("body", 1, margin, bodyY, contentW, bodyH)
                ), true));

        // 3. Section Header
        layouts.add(new LayoutDefinition("slideLayout3", "Section Header", LayoutType.SECTION_HEADER,
                List.of(
                        new PlaceholderDefinition("title", null, 831850L, 1709738L, 10515600L, 2852737L),
                        new PlaceholderDefinition("body", 1, 831850L, 4589463L, 10515600L, 1500187L)
                ), true));

        // 4. Two Content
        layouts.add(new LayoutDefinition("slideLayout4", "Two Content", LayoutType.TWO_CONTENT,
                List.of(
                        new PlaceholderDefinition("title", null, margin, titleY, contentW, titleH),
                        new PlaceholderDefinition("body", 1, margin, bodyY, halfW, bodyH),
                        new PlaceholderDefinition("body", 2, margin + halfW + margin, bodyY, halfW, bodyH)
                ), true));

        // 5. Comparison
        layouts.add(new LayoutDefinition("slideLayout5", "Comparison", LayoutType.COMPARISON,
                List.of(
                        new PlaceholderDefinition("title", null, margin, titleY, contentW, titleH),
                        new PlaceholderDefinition("body", 1, margin, 2174875L, halfW, 1143000L),
                        new PlaceholderDefinition("body", 2, margin, 3317875L, halfW, 2858763L),
                        new PlaceholderDefinition("body", 3, margin + halfW + margin, 2174875L, halfW, 1143000L),
                        new PlaceholderDefinition("body", 4, margin + halfW + margin, 3317875L, halfW, 2858763L)
                ), true));

        // 6. Title Only
        layouts.add(new LayoutDefinition("slideLayout6", "Title Only", LayoutType.TITLE_ONLY,
                List.of(
                        new PlaceholderDefinition("title", null, margin, titleY, contentW, titleH)
                ), true));

        // 7. Blank
        layouts.add(new LayoutDefinition("slideLayout7", "Blank", LayoutType.BLANK,
                List.of(), true));

        // 8. Content with Caption
        layouts.add(new LayoutDefinition("slideLayout8", "Content with Caption", LayoutType.CONTENT_CAPTION,
                List.of(
                        new PlaceholderDefinition("title", null, margin, titleY, contentW, titleH),
                        new PlaceholderDefinition("body", 1, margin, bodyY, 3886200L, bodyH),
                        new PlaceholderDefinition("body", 2, 4800600L, bodyY, 6553200L, bodyH)
                ), true));

        // 9. Picture with Caption
        layouts.add(new LayoutDefinition("slideLayout9", "Picture with Caption", LayoutType.PICTURE_CAPTION,
                List.of(
                        new PlaceholderDefinition("title", null, margin, titleY, contentW, titleH),
                        new PlaceholderDefinition("body", 1, margin, bodyY, contentW, bodyH)
                ), true));

        // 10. Title and Vertical Text
        layouts.add(new LayoutDefinition("slideLayout10", "Title and Vertical Text", LayoutType.TITLE_VERTICAL,
                List.of(
                        new PlaceholderDefinition("title", null, margin, titleY, contentW, titleH),
                        new PlaceholderDefinition("body", 1, margin, bodyY, contentW, bodyH)
                ), true));

        return layouts;
    }
}
