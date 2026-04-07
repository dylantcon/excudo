package com.excudo.core.model;

/**
 * Fill specification for a shape -- solid color, gradient, or none.
 * Nullable fields mean "inherit from theme".
 */
public final class ShapeFill {

    private final FillType type;
    private final TextColor color; // for SOLID

    private ShapeFill(FillType type, TextColor color) {
        this.type = type;
        this.color = color;
    }

    public static ShapeFill solid(TextColor color) {
        return new ShapeFill(FillType.SOLID, color);
    }

    public static ShapeFill solid(String hexColor) {
        return new ShapeFill(FillType.SOLID, TextColor.hex(hexColor));
    }

    public static ShapeFill scheme(String schemeColor) {
        return new ShapeFill(FillType.SOLID, TextColor.scheme(schemeColor));
    }

    public static ShapeFill noFill() {
        return new ShapeFill(FillType.NO_FILL, null);
    }

    public FillType getType() { return type; }
    public TextColor getColor() { return color; }
}
