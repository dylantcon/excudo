package com.excudo.core.model;

/**
 * Theme style reference for a shape -- maps to the OOXML {@code <p:style>} element
 * with lnRef, fillRef, effectRef, fontRef sub-elements.
 *
 * Each ref has an index (into the theme's format scheme) and a scheme color override.
 */
public final class ThemeStyleRef {

    /** Sentinel: when set on a ShapeStyle, no {@code <p:style>} element is emitted at all. */
    public static final ThemeStyleRef NONE = new ThemeStyleRef(
        -1, "", null, -1, "", -1, "", "", "");

    private final int lineRefIdx;
    private final String lineColor;        // scheme color val e.g. "accent1"
    private final String lineShadeVal;     // optional shade child e.g. "15000", null = none

    private final int fillRefIdx;
    private final String fillColor;

    private final int effectRefIdx;
    private final String effectColor;

    private final String fontRefIdx;       // "minor" or "major"
    private final String fontColor;

    private ThemeStyleRef(int lineRefIdx, String lineColor, String lineShadeVal,
                          int fillRefIdx, String fillColor,
                          int effectRefIdx, String effectColor,
                          String fontRefIdx, String fontColor) {
        this.lineRefIdx = lineRefIdx;
        this.lineColor = lineColor;
        this.lineShadeVal = lineShadeVal;
        this.fillRefIdx = fillRefIdx;
        this.fillColor = fillColor;
        this.effectRefIdx = effectRefIdx;
        this.effectColor = effectColor;
        this.fontRefIdx = fontRefIdx;
        this.fontColor = fontColor;
    }

    /**
     * Default shape style matching PowerPoint's insert-shape behavior.
     *
     * fontRef uses "tx1" (the logical text color), which resolves through the
     * slide master's clrMap at render time:
     *   - Dark theme: clrMap tx1="lt1" -> light text on dark background
     *   - Light theme: clrMap tx1="dk1" -> dark text on light background
     *
     * This eliminates the need to know the theme's dark/light mode at shape creation time.
     *
     * @param hasText if true, fill uses "lt1" (light/transparent bg for text shape);
     *                if false, "accent1" (colored fill for non-text shapes)
     */
    public static ThemeStyleRef defaultStyle(boolean hasText) {
        return new ThemeStyleRef(
            2, "accent1", "15000",      // lnRef idx=2, accent1 with shade
            1, hasText ? "lt1" : "accent1",  // fillRef idx=1
            0, "accent1",               // effectRef idx=0
            "minor", "tx1"              // fontRef minor, logical text color via clrMap
        );
    }

    /**
     * Custom style with explicit indices and colors.
     */
    public static ThemeStyleRef of(int lineRefIdx, String lineColor,
                                    int fillRefIdx, String fillColor,
                                    int effectRefIdx, String effectColor,
                                    String fontRefIdx, String fontColor) {
        return new ThemeStyleRef(lineRefIdx, lineColor, null,
                                fillRefIdx, fillColor,
                                effectRefIdx, effectColor,
                                fontRefIdx, fontColor);
    }

    public int getLineRefIdx() { return lineRefIdx; }
    public String getLineColor() { return lineColor; }
    public String getLineShadeVal() { return lineShadeVal; }
    public int getFillRefIdx() { return fillRefIdx; }
    public String getFillColor() { return fillColor; }
    public int getEffectRefIdx() { return effectRefIdx; }
    public String getEffectColor() { return effectColor; }
    public String getFontRefIdx() { return fontRefIdx; }
    public String getFontColor() { return fontColor; }
}
