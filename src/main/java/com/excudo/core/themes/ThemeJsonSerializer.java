package com.excudo.core.themes;

import com.excudo.core.themes.LayoutDefinition.LayoutType;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.*;
import com.excudo.core.utils.Logger;
import com.excudo.core.utils.ComponentLogger;

/**
 * Serializes and deserializes ThemeDefinition to/from JSON.
 * Uses Gson for parsing. Helper getters handle Gson's default
 * Double deserialization for numbers via Number.intValue()/longValue().
 */
public final class ThemeJsonSerializer {

    private ThemeJsonSerializer() {}

    private static final ComponentLogger logger = Logger.getLogger(ThemeJsonSerializer.class);
    private static final Gson GSON = new Gson();
    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>(){}.getType();

    /**
     * Deserialize a JSON string into a ThemeDefinition.
     * Parses layouts from JSON if present; falls back to default layouts with warning.
     */
    public static ThemeDefinition deserialize(String json) {
        Map<String, Object> root = parseJsonObject(json);

        String id = getString(root, "id");
        String displayName = getStringOr(root, "displayName", id);

        // Color scheme
        @SuppressWarnings("unchecked")
        Map<String, Object> colorsRaw = (Map<String, Object>) root.get("colorScheme");
        Map<String, String> colors = new LinkedHashMap<>();
        if (colorsRaw != null) {
            for (Map.Entry<String, Object> e : colorsRaw.entrySet()) {
                colors.put(e.getKey(), e.getValue().toString());
            }
        }

        // Parse layouts from JSON or fall back to defaults
        List<LayoutDefinition> layouts = parseLayouts(root, id);

        ThemeDefinition.Builder builder = ThemeDefinition.builder(id)
                .displayName(displayName)
                .colorScheme(colors)
                .majorFont(getStringOr(root, "majorFont", "Calibri"))
                .minorFont(getStringOr(root, "minorFont", "Calibri"))
                .backgroundFillIndex(getIntOr(root, "backgroundFillIndex", 1))
                .darkBackground(getBoolOr(root, "darkBackground", false))
                .layouts(layouts);

        String majorFallback = getStringOr(root, "majorFontFallback", null);
        String minorFallback = getStringOr(root, "minorFontFallback", null);
        if (majorFallback != null) builder.majorFontFallback(majorFallback);
        if (minorFallback != null) builder.minorFontFallback(minorFallback);

        // Text styles
        builder.titleStyle(parseTextStyles(root, "titleStyle"));
        builder.bodyStyle(parseTextStyles(root, "bodyStyle"));
        builder.otherStyle(parseTextStyles(root, "otherStyle"));

        return builder.build();
    }

    /**
     * Serialize a ThemeDefinition to a formatted JSON string.
     */
    public static String serialize(ThemeDefinition theme) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"id\": ").append(jsonString(theme.getId())).append(",\n");
        sb.append("  \"displayName\": ").append(jsonString(theme.getDisplayName())).append(",\n");
        sb.append("  \"colorScheme\": {\n");
        Iterator<Map.Entry<String, String>> colorIt = theme.getColorScheme().entrySet().iterator();
        while (colorIt.hasNext()) {
            Map.Entry<String, String> e = colorIt.next();
            sb.append("    ").append(jsonString(e.getKey())).append(": ").append(jsonString(e.getValue()));
            sb.append(colorIt.hasNext() ? ",\n" : "\n");
        }
        sb.append("  },\n");
        sb.append("  \"majorFont\": ").append(jsonString(theme.getMajorFont())).append(",\n");
        sb.append("  \"minorFont\": ").append(jsonString(theme.getMinorFont())).append(",\n");
        if (theme.getMajorFontFallback() != null) {
            sb.append("  \"majorFontFallback\": ").append(jsonString(theme.getMajorFontFallback())).append(",\n");
        }
        if (theme.getMinorFontFallback() != null) {
            sb.append("  \"minorFontFallback\": ").append(jsonString(theme.getMinorFontFallback())).append(",\n");
        }
        sb.append("  \"backgroundFillIndex\": ").append(theme.getBackgroundFillIndex()).append(",\n");
        if (theme.isDarkBackground()) {
            sb.append("  \"darkBackground\": true,\n");
        }
        sb.append("  \"titleStyle\": ").append(serializeStyles(theme.getTitleStyle())).append(",\n");
        sb.append("  \"bodyStyle\": ").append(serializeStyles(theme.getBodyStyle())).append(",\n");
        sb.append("  \"otherStyle\": ").append(serializeStyles(theme.getOtherStyle())).append(",\n");
        sb.append("  \"layouts\": ").append(serializeLayouts(theme.getLayouts())).append("\n");
        sb.append("}\n");
        return sb.toString();
    }

    // ========== Style Parsing ==========

    private static TextLevelStyle[] parseTextStyles(Map<String, Object> root, String key) {
        @SuppressWarnings("unchecked")
        List<Object> levels = (List<Object>) root.get(key);
        if (levels == null || levels.isEmpty()) {
            return defaultStyles();
        }

        TextLevelStyle[] styles = new TextLevelStyle[9];
        for (int i = 0; i < 9; i++) {
            if (i < levels.size()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> lvl = (Map<String, Object>) levels.get(i);
                styles[i] = parseTextLevel(lvl, i);
            } else {
                styles[i] = TextLevelStyle.builder(i).fontSizePt(18).colorRef("tx1").noBullet().build();
            }
        }
        return styles;
    }

    private static TextLevelStyle parseTextLevel(Map<String, Object> obj, int defaultLevel) {
        int level = getIntOr(obj, "level", defaultLevel);
        TextLevelStyle.Builder b = TextLevelStyle.builder(level);

        if (obj.containsKey("fontSizePt")) b.fontSizePt(getIntOr(obj, "fontSizePt", 18));
        if (obj.containsKey("fontSize")) b.fontSize(getIntOr(obj, "fontSize", 1800));
        if (obj.containsKey("bold")) b.bold(getBoolOr(obj, "bold", false));
        if (obj.containsKey("colorRef")) b.colorRef(getStringOr(obj, "colorRef", "tx1"));
        if (obj.containsKey("hexColor")) b.hexColor(getStringOr(obj, "hexColor", "000000"));
        if (obj.containsKey("alignment")) b.alignment(getStringOr(obj, "alignment", "l"));

        String bulletChar = getStringOr(obj, "bulletChar", null);
        String bulletFont = getStringOr(obj, "bulletFont", null);
        if (bulletChar != null) {
            b.bullet(bulletChar, bulletFont);
        } else {
            b.noBullet();
        }

        if (obj.containsKey("marginLeft")) b.marginLeft(getIntOr(obj, "marginLeft", 0));
        if (obj.containsKey("indent")) b.indent(getIntOr(obj, "indent", 0));
        if (obj.containsKey("lineSpacing")) b.lineSpacing(getIntOr(obj, "lineSpacing", 100000));
        if (obj.containsKey("spaceBefore")) b.spaceBefore(getIntOr(obj, "spaceBefore", 0));

        return b.build();
    }

    private static TextLevelStyle[] defaultStyles() {
        TextLevelStyle[] styles = new TextLevelStyle[9];
        for (int i = 0; i < 9; i++) {
            styles[i] = TextLevelStyle.builder(i).fontSizePt(18).colorRef("tx1").noBullet().build();
        }
        return styles;
    }

    // ========== Style Serialization ==========

    private static String serializeStyles(TextLevelStyle[] styles) {
        if (styles == null || styles.length == 0) return "[]";
        StringBuilder sb = new StringBuilder("[\n");
        for (int i = 0; i < styles.length; i++) {
            TextLevelStyle s = styles[i];
            sb.append("    { \"level\": ").append(s.getLevel());
            sb.append(", \"fontSizePt\": ").append(s.getFontSize() / 100);
            if (s.isBold()) sb.append(", \"bold\": true");
            if (s.getColorRef() != null) sb.append(", \"colorRef\": ").append(jsonString(s.getColorRef()));
            if (s.getAlignment() != null && !"l".equals(s.getAlignment())) {
                sb.append(", \"alignment\": ").append(jsonString(s.getAlignment()));
            }
            if (s.hasBullet()) {
                sb.append(", \"bulletChar\": ").append(jsonString(s.getBulletChar()));
                if (s.getBulletFont() != null) sb.append(", \"bulletFont\": ").append(jsonString(s.getBulletFont()));
            }
            if (s.getMarginLeft() != 0) sb.append(", \"marginLeft\": ").append(s.getMarginLeft());
            if (s.getIndent() != 0) sb.append(", \"indent\": ").append(s.getIndent());
            if (s.getLineSpacing() != 100000) sb.append(", \"lineSpacing\": ").append(s.getLineSpacing());
            if (s.getSpaceBefore() != 0) sb.append(", \"spaceBefore\": ").append(s.getSpaceBefore());
            sb.append(" }");
            if (i < styles.length - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ]");
        return sb.toString();
    }

    // ========== Layout Serialization ==========

    private static String serializeLayouts(List<LayoutDefinition> layouts) {
        if (layouts == null || layouts.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[\n");
        for (int i = 0; i < layouts.size(); i++) {
            LayoutDefinition l = layouts.get(i);
            sb.append("    { \"layoutId\": ").append(jsonString(l.getLayoutId()));
            sb.append(", \"matchingName\": ").append(jsonString(l.getMatchingName()));
            sb.append(", \"layoutType\": ").append(jsonString(l.getLayoutType().name()));
            sb.append(", \"preserve\": ").append(l.isPreserve());
            sb.append(", \"placeholders\": [");
            List<PlaceholderDefinition> phs = l.getPlaceholders();
            for (int j = 0; j < phs.size(); j++) {
                PlaceholderDefinition ph = phs.get(j);
                sb.append("{ \"type\": ").append(jsonString(ph.getType()));
                if (ph.getIdx() != null) {
                    sb.append(", \"idx\": ").append(ph.getIdx());
                }
                sb.append(", \"x\": ").append(ph.getX());
                sb.append(", \"y\": ").append(ph.getY());
                sb.append(", \"cx\": ").append(ph.getCx());
                sb.append(", \"cy\": ").append(ph.getCy());
                sb.append(" }");
                if (j < phs.size() - 1) sb.append(", ");
            }
            sb.append("] }");
            if (i < layouts.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ]");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static List<LayoutDefinition> parseLayouts(Map<String, Object> root, String themeId) {
        Object layoutsObj = root.get("layouts");
        if (layoutsObj == null) {
            logger.warn("Theme '{}' has no layout data in JSON -- using default 9-layout set (fallback)", themeId);
            return BundledThemes.createDefaultLayouts();
        }

        List<Object> layoutList = (List<Object>) layoutsObj;
        List<LayoutDefinition> layouts = new ArrayList<>();
        for (Object item : layoutList) {
            Map<String, Object> lMap = (Map<String, Object>) item;
            String layoutId = getString(lMap, "layoutId");
            String matchingName = getStringOr(lMap, "matchingName", layoutId);
            String layoutTypeStr = getStringOr(lMap, "layoutType", "BLANK");
            boolean preserve = getBoolOr(lMap, "preserve", true);

            LayoutType layoutType;
            try {
                layoutType = LayoutType.valueOf(layoutTypeStr);
            } catch (IllegalArgumentException e) {
                layoutType = LayoutType.BLANK;
            }

            List<PlaceholderDefinition> placeholders = new ArrayList<>();
            Object phsObj = lMap.get("placeholders");
            if (phsObj instanceof List) {
                for (Object phItem : (List<Object>) phsObj) {
                    Map<String, Object> phMap = (Map<String, Object>) phItem;
                    String type = getString(phMap, "type");
                    Integer idx = phMap.containsKey("idx") ? getIntOr(phMap, "idx", 0) : null;
                    long x = getLongOr(phMap, "x", 0);
                    long y = getLongOr(phMap, "y", 0);
                    long cx = getLongOr(phMap, "cx", 0);
                    long cy = getLongOr(phMap, "cy", 0);
                    placeholders.add(new PlaceholderDefinition(type, idx, x, y, cx, cy));
                }
            }

            layouts.add(new LayoutDefinition(layoutId, matchingName, layoutType, placeholders, preserve));
        }
        return layouts;
    }

    // ========== JSON Helpers ==========

    static Map<String, Object> parseJsonObject(String json) {
        return GSON.fromJson(json, MAP_TYPE);
    }

    private static long getLongOr(Map<String, Object> map, String key, long fallback) {
        Object val = map.get(key);
        if (val instanceof Number) return ((Number) val).longValue();
        if (val instanceof String) {
            try { return Long.parseLong((String) val); } catch (NumberFormatException e) { return fallback; }
        }
        return fallback;
    }

    private static String jsonString(String val) {
        if (val == null) return "null";
        return "\"" + val.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
    }

    private static String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : null;
    }

    private static String getStringOr(Map<String, Object> map, String key, String fallback) {
        Object val = map.get(key);
        return val != null ? val.toString() : fallback;
    }

    private static int getIntOr(Map<String, Object> map, String key, int fallback) {
        Object val = map.get(key);
        if (val instanceof Number) return ((Number) val).intValue();
        if (val instanceof String) {
            try { return Integer.parseInt((String) val); } catch (NumberFormatException e) { return fallback; }
        }
        return fallback;
    }

    private static boolean getBoolOr(Map<String, Object> map, String key, boolean fallback) {
        Object val = map.get(key);
        if (val instanceof Boolean) return (Boolean) val;
        if (val instanceof String) return Boolean.parseBoolean((String) val);
        return fallback;
    }
}
