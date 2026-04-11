package com.excudo.view.rendering;

import com.excudo.core.model.LayoutInfo;
import com.excudo.core.model.PlaceholderGeometry;
import com.excudo.core.model.PPTXDocument;
import com.excudo.core.themes.ThemeDefinition;
import com.excudo.core.themes.ThemeManager;
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

    private final java.util.Map<String, String> clrMap;
    private final String backgroundColorHex;

    public SlideRenderContext(ThemeDefinition theme, LayoutInfo layoutInfo,
                              PPTXDocument document, int slideNumber,
                              java.util.Map<String, String> clrMap,
                              String backgroundColorHex) {
        this.theme = theme;
        this.layoutInfo = layoutInfo;
        this.document = document;
        this.slideNumber = slideNumber;
        this.clrMap = clrMap != null ? clrMap : java.util.Map.of();
        this.backgroundColorHex = backgroundColorHex;
    }

    // ========== BACKGROUND ==========

    /**
     * Get the pre-resolved background color hex string for this slide.
     */
    public String getBackgroundColorHex() {
        if (backgroundColorHex == null) {
            throw new IllegalStateException("No background color provided for slide " + slideNumber);
        }
        return backgroundColorHex;
    }

    /**
     * Get the default text color hex string for titles.
     * Resolves tx1 through the clrMap (dark theme: lt1, light theme: dk1).
     */
    public String getTitleTextColorHex() {
        return resolveSchemeColor("tx1");
    }

    /**
     * Get the default text color hex for body text.
     * Resolves from body style level 0's colorRef, falling back to tx1.
     */
    public String getBodyTextColorHex() {
        TextLevelStyle bodyStyle = getBodyStyle(0);
        if (bodyStyle != null && bodyStyle.getColorRef() != null) {
            return resolveSchemeColor(bodyStyle.getColorRef());
        }
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
        // Resolve through the slide master's clrMap (e.g. tx1->lt1, bg1->dk1)
        String resolved = clrMap.getOrDefault(colorName, colorName);

        if (ThemeManager.isThemeLoaded()) {
            String hex = ThemeManager.getThemeColor(resolved);
            if (hex != null && !hex.isEmpty()) {
                return hex.startsWith("#") ? hex : "#" + hex;
            }
        }

        throw new IllegalStateException("No theme color for '" + colorName
            + "' (resolved='" + resolved + "'). ThemeManager.isThemeLoaded()="
            + ThemeManager.isThemeLoaded());
    }


    // ========== RAW ACCESSORS ==========

    public ThemeDefinition getTheme() { return theme; }
    public LayoutInfo getLayoutInfo() { return layoutInfo; }
    public PPTXDocument getDocument() { return document; }
    public int getSlideNumber() { return slideNumber; }
}
