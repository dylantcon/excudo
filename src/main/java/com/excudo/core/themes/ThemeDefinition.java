package com.excudo.core.themes;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Immutable model representing a complete PowerPoint theme bundle.
 * Encompasses all 4 structural layers: theme XML, slide master, slide layouts, and relationships.
 *
 * Style inheritance hierarchy (most specific wins):
 *   Theme (clrScheme, fontScheme, fmtScheme)
 *   -> Slide Master (clrMap, txStyles)
 *   -> Slide Layout (placeholder geometry, lstStyle overrides)
 *   -> Slide (per-shape overrides only when deviating from inherited defaults)
 */
public final class ThemeDefinition {

    private final String id;
    private final String displayName;
    private final Map<String, String> colorScheme;
    private final String majorFont;
    private final String minorFont;
    private final String majorFontFallback;
    private final String minorFontFallback;
    private final TextLevelStyle[] titleStyle;
    private final TextLevelStyle[] bodyStyle;
    private final TextLevelStyle[] otherStyle;
    private final List<LayoutDefinition> layouts;
    private final int backgroundFillIndex;
    private final boolean darkBackground;

    private ThemeDefinition(Builder builder) {
        this.id = builder.id;
        this.displayName = builder.displayName;
        this.colorScheme = Collections.unmodifiableMap(builder.colorScheme);
        this.majorFont = builder.majorFont;
        this.minorFont = builder.minorFont;
        this.majorFontFallback = builder.majorFontFallback;
        this.minorFontFallback = builder.minorFontFallback;
        this.titleStyle = builder.titleStyle;
        this.bodyStyle = builder.bodyStyle;
        this.otherStyle = builder.otherStyle;
        this.layouts = Collections.unmodifiableList(builder.layouts);
        this.backgroundFillIndex = builder.backgroundFillIndex;
        this.darkBackground = builder.darkBackground;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public Map<String, String> getColorScheme() { return colorScheme; }
    public String getMajorFont() { return majorFont; }
    public String getMinorFont() { return minorFont; }
    public String getMajorFontFallback() { return majorFontFallback; }
    public String getMinorFontFallback() { return minorFontFallback; }
    public TextLevelStyle[] getTitleStyle() { return titleStyle; }
    public TextLevelStyle[] getBodyStyle() { return bodyStyle; }
    public TextLevelStyle[] getOtherStyle() { return otherStyle; }
    public List<LayoutDefinition> getLayouts() { return layouts; }
    public int getBackgroundFillIndex() { return backgroundFillIndex; }
    public boolean isDarkBackground() { return darkBackground; }

    public String getColor(String name) {
        return colorScheme.getOrDefault(name, "000000");
    }

    public boolean hasValidColorScheme() {
        String[] required = {"dk1", "lt1", "dk2", "lt2",
                "accent1", "accent2", "accent3", "accent4", "accent5", "accent6",
                "hlink", "folHlink"};
        for (String key : required) {
            if (!colorScheme.containsKey(key)) return false;
        }
        return true;
    }

    public Builder toBuilder() {
        Builder b = new Builder(this.id);
        b.displayName = this.displayName;
        b.colorScheme = new java.util.LinkedHashMap<>(this.colorScheme);
        b.majorFont = this.majorFont;
        b.minorFont = this.minorFont;
        b.majorFontFallback = this.majorFontFallback;
        b.minorFontFallback = this.minorFontFallback;
        b.titleStyle = this.titleStyle;
        b.bodyStyle = this.bodyStyle;
        b.otherStyle = this.otherStyle;
        b.layouts = new java.util.ArrayList<>(this.layouts);
        b.backgroundFillIndex = this.backgroundFillIndex;
        b.darkBackground = this.darkBackground;
        return b;
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static final class Builder {
        private String id;
        private String displayName;
        private Map<String, String> colorScheme = Collections.emptyMap();
        private String majorFont = "Calibri";
        private String minorFont = "Calibri";
        private String majorFontFallback = null;
        private String minorFontFallback = null;
        private TextLevelStyle[] titleStyle = new TextLevelStyle[0];
        private TextLevelStyle[] bodyStyle = new TextLevelStyle[0];
        private TextLevelStyle[] otherStyle = new TextLevelStyle[0];
        private List<LayoutDefinition> layouts = Collections.emptyList();
        private int backgroundFillIndex = 1;
        private boolean darkBackground = false;

        private Builder(String id) {
            this.id = id;
            this.displayName = id;
        }

        public Builder id(String id) { this.id = id; return this; }
        public Builder displayName(String name) { this.displayName = name; return this; }
        public Builder colorScheme(Map<String, String> colors) { this.colorScheme = colors; return this; }
        public Builder majorFont(String font) { this.majorFont = font; return this; }
        public Builder minorFont(String font) { this.minorFont = font; return this; }
        public Builder majorFontFallback(String font) { this.majorFontFallback = font; return this; }
        public Builder minorFontFallback(String font) { this.minorFontFallback = font; return this; }
        public Builder titleStyle(TextLevelStyle[] styles) { this.titleStyle = styles; return this; }
        public Builder bodyStyle(TextLevelStyle[] styles) { this.bodyStyle = styles; return this; }
        public Builder otherStyle(TextLevelStyle[] styles) { this.otherStyle = styles; return this; }
        public Builder layouts(List<LayoutDefinition> layouts) { this.layouts = layouts; return this; }
        public Builder backgroundFillIndex(int index) { this.backgroundFillIndex = index; return this; }
        public Builder darkBackground(boolean dark) { this.darkBackground = dark; return this; }

        public ThemeDefinition build() {
            if (id == null || id.isEmpty()) {
                throw new IllegalArgumentException("Theme ID must not be empty");
            }
            return new ThemeDefinition(this);
        }
    }
}
