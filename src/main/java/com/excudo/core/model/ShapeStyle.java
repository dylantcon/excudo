package com.excudo.core.model;

/**
 * Complete styling specification for a shape.
 * Nullable fields = inherit from theme / use defaults.
 *
 * When all fields are null, the shape gets a default {@code <p:style>} matching
 * PowerPoint's insert-shape behavior (accent1 fill, accent1 line with shade).
 */
public final class ShapeStyle {

    private final ShapeFill fill;        // null = no direct fill (theme decides)
    private final ShapeLine line;        // null = no direct line (theme decides)
    private final ThemeStyleRef themeStyle; // null = generate default

    private ShapeStyle(ShapeFill fill, ShapeLine line, ThemeStyleRef themeStyle) {
        this.fill = fill;
        this.line = line;
        this.themeStyle = themeStyle;
    }

    /** Default shape style -- theme-based accent1 fill/line, no direct overrides. */
    public static ShapeStyle defaultStyle() {
        return new ShapeStyle(null, null, null);
    }

    /**
     * Text box style: noFill, no line, and NO p:style element emitted.
     * Matches oracle pattern where text boxes inherit default text color (dk1/black)
     * from the slide master rather than getting a themed style with accent1 line + lt1 font.
     */
    public static ShapeStyle textBox() {
        return new ShapeStyle(ShapeFill.noFill(), null, ThemeStyleRef.NONE);
    }

    /** Style with only a direct fill, theme defaults for everything else. */
    public static ShapeStyle withFill(ShapeFill fill) {
        return new ShapeStyle(fill, null, null);
    }

    /** Style with direct fill and line, theme defaults for style ref. */
    public static ShapeStyle withFillAndLine(ShapeFill fill, ShapeLine line) {
        return new ShapeStyle(fill, line, null);
    }

    /** Fully specified style. */
    public static ShapeStyle of(ShapeFill fill, ShapeLine line, ThemeStyleRef themeStyle) {
        return new ShapeStyle(fill, line, themeStyle);
    }

    public ShapeFill getFill() { return fill; }
    public ShapeLine getLine() { return line; }
    public ThemeStyleRef getThemeStyle() { return themeStyle; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ShapeStyle that)) return false;
        return java.util.Objects.equals(fill, that.fill)
            && java.util.Objects.equals(line, that.line)
            && java.util.Objects.equals(themeStyle, that.themeStyle);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(fill, line, themeStyle);
    }
}
