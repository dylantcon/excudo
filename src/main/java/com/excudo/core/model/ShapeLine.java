package com.excudo.core.model;

/**
 * Line/border specification for a shape.
 * All fields nullable -- null means inherit from theme.
 */
public final class ShapeLine {

    private final Integer widthEMU;
    private final TextColor color;
    private final String dashStyle; // "solid", "dot", "dash", "lgDash", "dashDot", "lgDashDot", "lgDashDotDot", "sysDot", "sysDash", "sysDashDot", "sysDashDotDot"
    /** Optional opacity 0-100 (percent). Null = fully opaque (no a:alpha emitted). */
    private final Integer alphaPercent;

    private ShapeLine(Integer widthEMU, TextColor color, String dashStyle, Integer alphaPercent) {
        this.widthEMU = widthEMU;
        this.color = color;
        this.dashStyle = dashStyle;
        this.alphaPercent = alphaPercent;
    }

    public static ShapeLine solid(int widthEMU, TextColor color) {
        return new ShapeLine(widthEMU, color, "solid", null);
    }

    public static ShapeLine of(int widthEMU, TextColor color, String dashStyle) {
        return new ShapeLine(widthEMU, color, dashStyle, null);
    }

    /** Convenience: 1pt solid line with hex color */
    public static ShapeLine thin(String hexColor) {
        return new ShapeLine(12700, TextColor.hex(hexColor), "solid", null);
    }

    /** Return a copy of this line with the supplied alpha percent (0-100).
     *  Null clears the alpha channel (fully opaque, no a:alpha emitted). */
    public ShapeLine withAlphaPercent(Integer alphaPercent) {
        return new ShapeLine(widthEMU, color, dashStyle, alphaPercent);
    }

    public Integer getWidthEMU() { return widthEMU; }
    public TextColor getColor() { return color; }
    public String getDashStyle() { return dashStyle; }
    public Integer getAlphaPercent() { return alphaPercent; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ShapeLine that)) return false;
        return java.util.Objects.equals(widthEMU, that.widthEMU)
            && java.util.Objects.equals(color, that.color)
            && java.util.Objects.equals(dashStyle, that.dashStyle)
            && java.util.Objects.equals(alphaPercent, that.alphaPercent);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(widthEMU, color, dashStyle, alphaPercent);
    }
}
