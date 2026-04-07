package com.excudo.view.rendering;

import com.excudo.core.model.LayoutInfo;
import com.excudo.core.model.PlaceholderGeometry;
import com.excudo.core.model.PPTXDocument;
import com.excudo.core.themes.ThemeDefinition;
import com.excudo.core.themes.TextLevelStyle;

/**
 * Carries presentation-level context needed by the rendering pipeline.
 * Provides theme colors, font styles, layout geometry, and background info.
 *
 * The renderer reads from this instead of hardcoding values.
 */
public class SlideRenderContext {

    private final ThemeDefinition theme;
    private final LayoutInfo layoutInfo;
    private final PPTXDocument document;
    private final int slideNumber;

    public SlideRenderContext(ThemeDefinition theme, LayoutInfo layoutInfo, PPTXDocument document) {
        this(theme, layoutInfo, document, 0);
    }

    public SlideRenderContext(ThemeDefinition theme, LayoutInfo layoutInfo, PPTXDocument document, int slideNumber) {
        this.theme = theme;
        this.layoutInfo = layoutInfo;
        this.document = document;
        this.slideNumber = slideNumber;
    }

    // ========== BACKGROUND ==========

    /**
     * Get the background color hex string for this slide.
     * Respects dark theme inversion.
     */
    public String getBackgroundColorHex() {
        if (theme == null) return "#FFFFFF";
        boolean dark = theme.isDarkBackground();
        // bg1 maps to dk1 for dark themes, lt1 for light themes
        String colorKey = dark ? "dk1" : "lt1";
        String hex = theme.getColor(colorKey);
        return hex.startsWith("#") ? hex : "#" + hex;
    }

    /**
     * Get the default text color hex string for titles.
     * Respects dark theme inversion (light text on dark bg).
     */
    public String getTitleTextColorHex() {
        if (theme == null) return "#000000";
        boolean dark = theme.isDarkBackground();
        String colorKey = dark ? "lt1" : "dk1";
        String hex = theme.getColor(colorKey);
        return hex.startsWith("#") ? hex : "#" + hex;
    }

    /**
     * Get the default text color hex for body text.
     * Resolves from body style level 0's colorRef, falling back to tx1 for readability.
     */
    public String getBodyTextColorHex() {
        if (theme == null) return "#333333";
        // Use the body style's colorRef if available
        TextLevelStyle bodyStyle = getBodyStyle(0);
        if (bodyStyle != null && bodyStyle.getColorRef() != null) {
            return resolveSchemeColor(bodyStyle.getColorRef());
        }
        // Fallback: same as title text (tx1 maps through clrMap)
        return getTitleTextColorHex();
    }

    // ========== THEME STYLES ==========

    /**
     * Get the TextLevelStyle for a title at the given indentation level.
     */
    public TextLevelStyle getTitleStyle(int level) {
        if (theme == null) return null;
        TextLevelStyle[] styles = theme.getTitleStyle();
        if (styles != null && level >= 0 && level < styles.length) {
            return styles[level];
        }
        return null;
    }

    /**
     * Get the TextLevelStyle for body text at the given indentation level.
     */
    public TextLevelStyle getBodyStyle(int level) {
        if (theme == null) return null;
        TextLevelStyle[] styles = theme.getBodyStyle();
        if (styles != null && level >= 0 && level < styles.length) {
            return styles[level];
        }
        return null;
    }

    // ========== FONT INFO ==========

    /**
     * Get the heading font family name.
     */
    public String getMajorFont() {
        return theme != null ? theme.getMajorFont() : "Calibri";
    }

    /**
     * Get the body font family name.
     */
    public String getMinorFont() {
        return theme != null ? theme.getMinorFont() : "Calibri";
    }

    // ========== PLACEHOLDER GEOMETRY ==========

    /**
     * Get placeholder geometry by type (e.g., "title", "ctrTitle", "body").
     */
    public PlaceholderGeometry getPlaceholderGeometry(String type) {
        if (layoutInfo == null) return null;
        return layoutInfo.getPlaceholderGeometryByType(type);
    }

    /**
     * Get placeholder geometry by index (e.g., "1", "2").
     */
    public PlaceholderGeometry getPlaceholderGeometryByIndex(String idx) {
        if (layoutInfo == null) return null;
        return layoutInfo.getPlaceholderGeometryByIndex(idx);
    }

    // ========== THEME COLOR RESOLUTION ==========

    /**
     * Resolve a scheme color name to a hex string.
     * Handles dk1, lt1, accent1-6, hlink, folHlink, tx1, bg1, etc.
     */
    public String resolveSchemeColor(String colorName) {
        if (theme == null) return "#000000";

        // Resolve clrMap aliases: tx1/tx2/bg1/bg2 depend on dark/light theme
        boolean dark = theme.isDarkBackground();
        String resolved = switch (colorName) {
            case "tx1" -> dark ? "lt1" : "dk1";   // text 1 = light on dark, dark on light
            case "tx2" -> dark ? "lt2" : "dk2";   // text 2
            case "bg1" -> dark ? "dk1" : "lt1";   // background 1
            case "bg2" -> dark ? "dk2" : "lt2";   // background 2
            default -> colorName;
        };

        String hex = theme.getColor(resolved);
        return hex.startsWith("#") ? hex : "#" + hex;
    }

    // ========== RAW ACCESSORS ==========

    public ThemeDefinition getTheme() { return theme; }
    public LayoutInfo getLayoutInfo() { return layoutInfo; }
    public PPTXDocument getDocument() { return document; }
    public int getSlideNumber() { return slideNumber; }
}
