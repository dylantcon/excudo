package com.excudo.core.themes;

import com.excudo.core.model.PPTXDocument;
import com.excudo.core.utils.XMLFactoryProvider;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import com.excudo.core.utils.ComponentLogger;
import com.excudo.core.utils.Logger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages PowerPoint theme files and provides theme-based color/font resolution.
 * Handles theme discovery, parsing, and caching for efficient rendering.
 * Returns colors as hex strings to avoid JavaFX dependencies in core.
 */
public class ThemeManager {

    private static final ComponentLogger logger = Logger.console();
    private static final Map<String, ThemeParser> themeCache = new HashMap<>();
    private static ThemeParser defaultTheme = null;

    private final XPath xpath;
    
    public ThemeManager() {
        this.xpath = XMLFactoryProvider.createXPath();
    }
    
    /**
     * Initialize theme manager from PPTXDocument's in-memory parts.
     * Parses theme directly from in-memory DOM when available.
     * Falls back to directory-based initialization if needed.
     */
    public static void initialize(PPTXDocument pptxDocument) {
        themeCache.clear();
        defaultTheme = null;

        try {
            // Try to find theme from slide master rels
            Document masterRels = pptxDocument.getXmlPart("ppt/slideMasters/_rels/slideMaster1.xml.rels");
            String themeTarget = null;
            if (masterRels != null) {
                NodeList rels = masterRels.getElementsByTagName("Relationship");
                for (int i = 0; i < rels.getLength(); i++) {
                    Element rel = (Element) rels.item(i);
                    if (rel.getAttribute("Type").contains("/theme")) {
                        themeTarget = rel.getAttribute("Target");
                        break;
                    }
                }
            }

            // Resolve theme part name
            String themePartName = "ppt/theme/theme1.xml"; // default
            if (themeTarget != null) {
                if (themeTarget.startsWith("../")) {
                    themePartName = "ppt/" + themeTarget.substring(3);
                } else {
                    themePartName = "ppt/slideMasters/" + themeTarget;
                }
            }

            // Parse theme from in-memory DOM
            Document themeDom = pptxDocument.getXmlPart(themePartName);
            if (themeDom != null) {
                ThemeParser parser = new ThemeParser();
                if (parser.parseTheme(themeDom)) {
                    defaultTheme = parser;
                    themeCache.put("default", parser);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to initialize ThemeManager from PPTXDocument: " + e.getMessage());
        }
    }
    
    /**
     * Get theme color by name (returns hex string)
     */
    public static String getThemeColor(String colorName) {
        if (defaultTheme == null) {
            throw new IllegalStateException("No theme loaded. Call ThemeManager.initialize() first.");
        }
        return defaultTheme.getThemeColor(colorName);
    }

    /**
     * Get theme font by name or alias
     */
    public static String getThemeFont(String fontReference) {
        if (defaultTheme == null) {
            throw new IllegalStateException("No theme loaded. Call ThemeManager.initialize() first.");
        }
        return defaultTheme.getThemeFont(fontReference);
    }

    /**
     * Resolve a fill style from the theme's fmtScheme.
     * @param idx fill reference index (1-3 for fills, 1001-1003 for bg fills)
     * @param phColorHex resolved scheme color to substitute for phClr
     */
    public static ResolvedFill resolveFillStyle(int idx, String phColorHex) {
        if (defaultTheme == null) {
            throw new IllegalStateException("No theme loaded. Call ThemeManager.initialize() first.");
        }
        FmtSchemeResolver resolver = defaultTheme.getFmtSchemeResolver();
        if (!resolver.isParsed()) {
            throw new IllegalStateException("fmtScheme not parsed from theme.");
        }
        return resolver.resolveFillByIndex(idx, phColorHex);
    }

    /**
     * Resolve a line style from the theme's fmtScheme.
     * @param idx line reference index (1-3)
     * @param phColorHex resolved scheme color to substitute for phClr
     */
    public static FmtSchemeResolver.ResolvedLineStyle resolveLineStyle(int idx, String phColorHex) {
        if (defaultTheme == null) {
            throw new IllegalStateException("No theme loaded. Call ThemeManager.initialize() first.");
        }
        FmtSchemeResolver resolver = defaultTheme.getFmtSchemeResolver();
        if (!resolver.isParsed()) {
            throw new IllegalStateException("fmtScheme not parsed from theme.");
        }
        return resolver.resolveLineByIndex(idx, phColorHex);
    }
    
    /**
     * Get current theme name
     */
    public static String getCurrentThemeName() {
        if (defaultTheme != null) {
            return defaultTheme.getThemeName();
        }
        return "Default (No Theme Loaded)";
    }
    
    /**
     * Check if a theme is loaded
     */
    public static boolean isThemeLoaded() {
        return defaultTheme != null && defaultTheme.isThemeLoaded();
    }
    
    /**
     * Get theme information for debugging
     */
    public static String getThemeInfo() {
        if (defaultTheme == null) {
            return "No theme loaded";
        }
        
        StringBuilder info = new StringBuilder();
        info.append("Theme: ").append(defaultTheme.getThemeName()).append("\\n");
        info.append("Colors: ").append(defaultTheme.getColorScheme().size()).append("\\n");
        info.append("Fonts: ").append(defaultTheme.getFontScheme().size()).append("\\n");
        
        // List some key colors
        Map<String, String> colors = defaultTheme.getColorScheme();
        for (String colorName : new String[]{"accent1", "accent2", "dk1", "lt1"}) {
            if (colors.containsKey(colorName)) {
                String color = colors.get(colorName);
                info.append(colorName).append(": ").append(color).append("\\n");
            }
        }
        
        return info.toString();
    }
    
    /**
     * Get all available bundled themes.
     */
    public static List<ThemeDefinition> getAvailableThemes() {
        return ThemeLoader.getAll();
    }
}