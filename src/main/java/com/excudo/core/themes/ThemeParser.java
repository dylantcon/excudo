package com.excudo.core.themes;

import com.excudo.core.utils.XMLFactoryProvider;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import com.excudo.core.utils.Logger;
import com.excudo.core.utils.ComponentLogger;

/**
 * Parses PowerPoint theme files (theme{n}.xml) to extract color schemes, font schemes,
 * and format schemes for accurate visual rendering.
 * Returns colors as hex strings to avoid JavaFX dependencies in core.
 */
public class ThemeParser {

    private static final ComponentLogger logger = Logger.theme();

    private final XPath xpath;
    private Map<String, String> colorScheme;
    private Map<String, String> fontScheme;
    private String themeName;
    
    
    public ThemeParser() {
        this.xpath = XMLFactoryProvider.createXPath();
        this.colorScheme = new HashMap<>();
        this.fontScheme = new HashMap<>();
    }
    
    /**
     * Parse theme file and extract color/font schemes
     */
    public boolean parseTheme(File themeFile) {
        try {
            Document document = XMLFactoryProvider.parseDocument(themeFile);
            return parseTheme(document);
        } catch (Exception e) {
            logger.error("Failed to parse theme file: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Parse theme from an already-parsed DOM document.
     * Used when theme XML is available in-memory (e.g., from PPTXDocument).
     */
    public boolean parseTheme(Document document) {
        try {
            // Get theme name
            Element themeElement = (Element) xpath.evaluate("//a:theme", document, XPathConstants.NODE);
            if (themeElement != null && themeElement.hasAttribute("name")) {
                themeName = themeElement.getAttribute("name");
            }

            // Parse color scheme
            parseColorScheme(document);

            // Parse font scheme
            parseFontScheme(document);

            return true;

        } catch (Exception e) {
            logger.error("Failed to parse theme document: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Parse color scheme from theme document
     */
    private void parseColorScheme(Document document) throws Exception {
        Element colorScheme = (Element) xpath.evaluate("//a:clrScheme", document, XPathConstants.NODE);
        if (colorScheme == null) {
            return;
        }

        // Parse each color definition
        parseColorDefinition(document, "dk1");
        parseColorDefinition(document, "lt1");
        parseColorDefinition(document, "dk2");
        parseColorDefinition(document, "lt2");
        parseColorDefinition(document, "accent1");
        parseColorDefinition(document, "accent2");
        parseColorDefinition(document, "accent3");
        parseColorDefinition(document, "accent4");
        parseColorDefinition(document, "accent5");
        parseColorDefinition(document, "accent6");
        parseColorDefinition(document, "hlink");
        parseColorDefinition(document, "folHlink");
        
        // tx1/tx2/bg1/bg2 are NOT aliased here — their mapping depends on the
        // slide master's p:clrMap (e.g. dark theme: tx1->lt1, light theme: tx1->dk1).
        // Callers must resolve through the clrMap, not through the theme directly.
    }
    
    /**
     * Parse individual color definition
     */
    private void parseColorDefinition(Document document, String colorName) throws Exception {
        Element colorElement = (Element) xpath.evaluate("//a:" + colorName, document, XPathConstants.NODE);
        if (colorElement == null) {
            return;
        }
        
        // Look for sRGB color value
        Element srgbClr = (Element) xpath.evaluate("a:srgbClr", colorElement, XPathConstants.NODE);
        if (srgbClr != null && srgbClr.hasAttribute("val")) {
            String colorVal = srgbClr.getAttribute("val");
            String hexColor = "#" + colorVal;
            this.colorScheme.put(colorName, hexColor);
            return;
        }
        
        // Look for system color (sysClr)
        Element sysClr = (Element) xpath.evaluate("a:sysClr", colorElement, XPathConstants.NODE);
        if (sysClr != null) {
            String sysColorName = sysClr.getAttribute("val");
            String lastClr = sysClr.getAttribute("lastClr");
            if (lastClr != null && !lastClr.isEmpty()) {
                String hexColor = "#" + lastClr;
                this.colorScheme.put(colorName, hexColor);
            }
        }
    }
    
    /**
     * Parse font scheme from theme document
     */
    private void parseFontScheme(Document document) throws Exception {
        Element fontScheme = (Element) xpath.evaluate("//a:fontScheme", document, XPathConstants.NODE);
        if (fontScheme == null) {
            return;
        }

        // Parse major font (headings)
        Element majorFont = (Element) xpath.evaluate("a:majorFont/a:latin", fontScheme, XPathConstants.NODE);
        if (majorFont != null && majorFont.hasAttribute("typeface")) {
            String majorTypeface = majorFont.getAttribute("typeface");
            this.fontScheme.put("major", majorTypeface);
            this.fontScheme.put("+mj-lt", majorTypeface); // Major font Latin alias
        }

        // Parse minor font (body text)
        Element minorFont = (Element) xpath.evaluate("a:minorFont/a:latin", fontScheme, XPathConstants.NODE);
        if (minorFont != null && minorFont.hasAttribute("typeface")) {
            String minorTypeface = minorFont.getAttribute("typeface");
            this.fontScheme.put("minor", minorTypeface);
            this.fontScheme.put("+mn-lt", minorTypeface); // Minor font Latin alias
        }
    }
    
    /**
     * Get theme color by name (returns hex string)
     */
    public String getThemeColor(String colorName) {
        String themeColor = colorScheme.get(colorName);
        if (themeColor != null) {
            return themeColor;
        }
        throw new IllegalStateException("Theme color '" + colorName
            + "' not found in parsed theme. Available: " + colorScheme.keySet());
    }
    
    /**
     * Get theme font by name or alias
     */
    public String getThemeFont(String fontReference) {
        String themeFont = fontScheme.get(fontReference);
        if (themeFont != null) {
            return themeFont;
        }
        throw new IllegalStateException("Theme font '" + fontReference
            + "' not found in parsed theme. Available: " + fontScheme.keySet());
    }
    
    /**
     * Get theme name
     */
    public String getThemeName() {
        return themeName != null ? themeName : "Default";
    }
    
    /**
     * Check if theme was successfully parsed
     */
    public boolean isThemeLoaded() {
        return !colorScheme.isEmpty();
    }
    
    /**
     * Get all available theme colors (for debugging)
     */
    public Map<String, String> getColorScheme() {
        return new HashMap<>(colorScheme);
    }
    
    /**
     * Get all available theme fonts (for debugging)
     */
    public Map<String, String> getFontScheme() {
        return new HashMap<>(fontScheme);
    }
}