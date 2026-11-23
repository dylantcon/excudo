package com.excudo.core.model;

/**
 * Text color reference -- either a scheme color name (e.g. "lt1", "dk1")
 * or an sRGB hex value (e.g. "FF0000").
 */
public final class TextColor {

    private final String schemeVal;
    private final String hexVal;

    private TextColor(String schemeVal, String hexVal) {
        this.schemeVal = schemeVal;
        this.hexVal = hexVal;
    }

    public static TextColor scheme(String val) {
        return new TextColor(val, null);
    }

    /** Theme colour names, lower case to their OOXML spelling. */
    private static final java.util.Map<String, String> SCHEME_NAMES = new java.util.HashMap<>();
    static {
        for (String name : new String[] {"accent1", "accent2", "accent3", "accent4", "accent5", "accent6",
                "dk1", "lt1", "dk2", "lt2", "tx1", "bg1", "tx2", "bg2", "hlink", "folHlink"}) {
            SCHEME_NAMES.put(name.toLowerCase(), name);
        }
    }

    /** Whether this is a theme colour name (accent1, dk1, tx1, bg1, hlink, ...), in any case. */
    public static boolean isSchemeName(String val) {
        return val != null && SCHEME_NAMES.containsKey(val.trim().toLowerCase());
    }

    /** The OOXML spelling of a theme colour name, or null if it isn't one. */
    public static String schemeName(String val) {
        return val == null ? null : SCHEME_NAMES.get(val.trim().toLowerCase());
    }

    public static TextColor hex(String hex) {
        String cleaned = hex.startsWith("#") ? hex.substring(1) : hex;
        return new TextColor(null, cleaned);
    }

    public boolean isScheme() { return schemeVal != null; }
    public String getSchemeVal() { return schemeVal; }
    public String getHexVal() { return hexVal; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TextColor that)) return false;
        return java.util.Objects.equals(schemeVal, that.schemeVal)
            && java.util.Objects.equals(hexVal, that.hexVal);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(schemeVal, hexVal);
    }
}
