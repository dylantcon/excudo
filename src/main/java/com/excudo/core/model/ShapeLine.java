package com.excudo.core.model;

/**
 * Line/border specification for a shape.
 * All fields nullable -- null means inherit from theme.
 */
public final class ShapeLine {

    private final Integer widthEMU;
    private final TextColor color;
    private final String dashStyle; // "solid", "dot", "dash", "lgDash", "dashDot", "lgDashDot", "lgDashDotDot", "sysDot", "sysDash", "sysDashDot", "sysDashDotDot"

    private ShapeLine(Integer widthEMU, TextColor color, String dashStyle) {
        this.widthEMU = widthEMU;
        this.color = color;
        this.dashStyle = dashStyle;
    }

    public static ShapeLine solid(int widthEMU, TextColor color) {
        return new ShapeLine(widthEMU, color, "solid");
    }

    public static ShapeLine of(int widthEMU, TextColor color, String dashStyle) {
        return new ShapeLine(widthEMU, color, dashStyle);
    }

    /** Convenience: 1pt solid line with hex color */
    public static ShapeLine thin(String hexColor) {
        return new ShapeLine(12700, TextColor.hex(hexColor), "solid");
    }

    public Integer getWidthEMU() { return widthEMU; }
    public TextColor getColor() { return color; }
    public String getDashStyle() { return dashStyle; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ShapeLine that)) return false;
        return java.util.Objects.equals(widthEMU, that.widthEMU)
            && java.util.Objects.equals(color, that.color)
            && java.util.Objects.equals(dashStyle, that.dashStyle);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(widthEMU, color, dashStyle);
    }
}
