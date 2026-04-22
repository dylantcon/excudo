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
