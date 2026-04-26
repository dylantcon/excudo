package com.excudo.core.model;

/**
 * Fill specification for a shape -- solid color, gradient, or none.
 * Nullable fields mean "inherit from theme".
 */
public final class ShapeFill {

    private final FillType type;
    private final TextColor color; // for SOLID
    /** Optional opacity 0-100 (percent). Null = fully opaque (no a:alpha emitted). */
    private final Integer alphaPercent;

    private ShapeFill(FillType type, TextColor color, Integer alphaPercent) {
        this.type = type;
        this.color = color;
        this.alphaPercent = alphaPercent;
    }

    public static ShapeFill solid(TextColor color) {
        return new ShapeFill(FillType.SOLID, color, null);
    }

    public static ShapeFill solid(String hexColor) {
        return new ShapeFill(FillType.SOLID, TextColor.hex(hexColor), null);
    }

    public static ShapeFill scheme(String schemeColor) {
        return new ShapeFill(FillType.SOLID, TextColor.scheme(schemeColor), null);
    }

    public static ShapeFill noFill() {
        return new ShapeFill(FillType.NO_FILL, null, null);
    }

    /** Return a copy of this fill with the supplied alpha percent (0-100).
     *  Null clears the alpha channel (fully opaque, no a:alpha emitted). */
    public ShapeFill withAlphaPercent(Integer alphaPercent) {
        return new ShapeFill(type, color, alphaPercent);
    }

    public FillType getType() { return type; }
    public TextColor getColor() { return color; }
    public Integer getAlphaPercent() { return alphaPercent; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ShapeFill that)) return false;
        return type == that.type
            && java.util.Objects.equals(color, that.color)
            && java.util.Objects.equals(alphaPercent, that.alphaPercent);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(type, color, alphaPercent);
    }
}
