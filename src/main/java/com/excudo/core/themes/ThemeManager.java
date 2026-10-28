package com.excudo.core.themes;

import com.excudo.core.model.PPTXDocument;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.excudo.core.utils.ComponentLogger;
import com.excudo.core.utils.Logger;
import java.util.List;
import java.util.Map;

/**
 * Theme color / font / format-scheme resolution for one presentation.
 *
 * <p>Each open deck has its own instance ({@link #forDocument}), attached
 * to the document itself, so two open decks never see each other's theme.
 * The parsed theme refreshes automatically when the deck revision changes
 * (any theme, master, or layout edit), so callers never initialize or
 * invalidate it. Colors are returned as {@link HexColor} to keep JavaFX out
 * of core.
 */
public final class ThemeManager {

    private static final ComponentLogger logger = Logger.console();
    private static final ThemeManager NO_DOCUMENT = new ThemeManager(null);

    private final PPTXDocument document;
    private ThemeParser theme;
    private long parsedAtDeckRevision = Long.MIN_VALUE;

    private ThemeManager(PPTXDocument document) {
        this.document = document;
    }

    /**
     * The theme resolver for {@code document} (never null; for a null
     * document it reports no theme loaded).
     */
    public static ThemeManager forDocument(PPTXDocument document) {
        if (document == null) return NO_DOCUMENT;
        return document.derived(ThemeManager.class, () -> new ThemeManager(document));
    }

    /** The parsed theme, re-parsed when the deck has changed since last time. */
    private synchronized ThemeParser theme() {
        if (document == null) return null;
        long revision = document.getDeckRevision();
        if (revision != parsedAtDeckRevision) {
            theme = parseTheme(document);
            parsedAtDeckRevision = revision;
        }
        return theme;
    }

    /** Parse the theme slideMaster1 points at (falls back to theme1.xml). */
    private static ThemeParser parseTheme(PPTXDocument pptxDocument) {
        try {
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

            String themePartName = "ppt/theme/theme1.xml";
            if (themeTarget != null) {
                themePartName = themeTarget.startsWith("../")
                    ? "ppt/" + themeTarget.substring(3)
                    : "ppt/slideMasters/" + themeTarget;
            }

            Document themeDom = pptxDocument.getXmlPart(themePartName);
            if (themeDom != null) {
                ThemeParser parser = new ThemeParser();
                if (parser.parseTheme(themeDom)) return parser;
            }
        } catch (Exception e) {
            logger.warn("Failed to parse theme from PPTXDocument: " + e.getMessage());
        }
        return null;
    }

    private ThemeParser requireTheme() {
        ThemeParser t = theme();
        if (t == null) {
            throw new IllegalStateException("No theme loaded for this presentation.");
        }
        return t;
    }

    /** Theme color by scheme name (accent1, dk1, ...). */
    public HexColor getThemeColor(String colorName) {
        return requireTheme().getThemeColor(colorName);
    }

    /** Theme font by name or alias (major / minor / +mj-lt ...). */
    public String getThemeFont(String fontReference) {
        return requireTheme().getThemeFont(fontReference);
    }

    /**
     * Resolve a fill style from the theme's fmtScheme.
     * @param idx fill reference index (1-3 for fills, 1001-1003 for bg fills)
     * @param phColorHex resolved scheme color to substitute for phClr
     */
    public ResolvedFill resolveFillStyle(int idx, String phColorHex) {
        return parsedFmtScheme().resolveFillByIndex(idx, phColorHex);
    }

    /**
     * Resolve a line style from the theme's fmtScheme.
     * @param idx line reference index (1-3)
     * @param phColorHex resolved scheme color to substitute for phClr
     */
    public FmtSchemeResolver.ResolvedLineStyle resolveLineStyle(int idx, String phColorHex) {
        return parsedFmtScheme().resolveLineByIndex(idx, phColorHex);
    }

    /**
     * Resolve an effect style's outer shadow from the theme's fmtScheme.
     * @return the shadow, or null when the style has no outer shadow
     */
    public FmtSchemeResolver.ResolvedShadow resolveEffectStyle(int idx, String phColorHex) {
        return parsedFmtScheme().resolveEffectByIndex(idx, phColorHex);
    }

    private FmtSchemeResolver parsedFmtScheme() {
        FmtSchemeResolver resolver = requireTheme().getFmtSchemeResolver();
        if (!resolver.isParsed()) {
            throw new IllegalStateException("fmtScheme not parsed from theme.");
        }
        return resolver;
    }

    /** The theme's name, or "Default (No Theme Loaded)". */
    public String getCurrentThemeName() {
        ThemeParser t = theme();
        return t != null ? t.getThemeName() : "Default (No Theme Loaded)";
    }

    public boolean isThemeLoaded() {
        ThemeParser t = theme();
        return t != null && t.isThemeLoaded();
    }

    /** Theme summary for debugging. */
    public String getThemeInfo() {
        ThemeParser t = theme();
        if (t == null) return "No theme loaded";

        StringBuilder info = new StringBuilder();
        info.append("Theme: ").append(t.getThemeName()).append("\n");
        info.append("Colors: ").append(t.getColorScheme().size()).append("\n");
        info.append("Fonts: ").append(t.getFontScheme().size()).append("\n");
        Map<String, String> colors = t.getColorScheme();
        for (String colorName : new String[]{"accent1", "accent2", "dk1", "lt1"}) {
            if (colors.containsKey(colorName)) {
                info.append(colorName).append(": ").append(colors.get(colorName)).append("\n");
            }
        }
        return info.toString();
    }

    /** All bundled themes (the theme library, not tied to any deck). */
    public static List<ThemeDefinition> getAvailableThemes() {
        return ThemeLoader.getAll();
    }
}
