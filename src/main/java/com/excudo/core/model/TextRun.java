package com.excudo.core.model;

/**
 * Single run of uniformly formatted text within a paragraph.
 * Nullable fields indicate "inherit from theme/layout" -- only non-null fields emit XML attributes.
 */
public final class TextRun {

    private final String text;
    private final Integer fontSize;
    private final Boolean bold;
    private final Boolean italic;
    private final String underline;
    private final String strikethrough;
    private final String fontFamily;
    private final TextColor color;
    private final TextColor highlight;
    private final String language;
    private final String capitalization;
    private final Integer baseline;
    private final Integer characterSpacing;

    private TextRun(Builder builder) {
        this.text = builder.text;
        this.fontSize = builder.fontSize;
        this.bold = builder.bold;
        this.italic = builder.italic;
        this.underline = builder.underline;
        this.strikethrough = builder.strikethrough;
        this.fontFamily = builder.fontFamily;
        this.color = builder.color;
        this.highlight = builder.highlight;
        this.language = builder.language;
        this.capitalization = builder.capitalization;
        this.baseline = builder.baseline;
        this.characterSpacing = builder.characterSpacing;
    }

    public String getText() { return text; }
    public Integer getFontSize() { return fontSize; }
    public Boolean getBold() { return bold; }
    public Boolean getItalic() { return italic; }
    public String getUnderline() { return underline; }
    public String getStrikethrough() { return strikethrough; }
    public String getFontFamily() { return fontFamily; }
    public TextColor getColor() { return color; }
    public TextColor getHighlight() { return highlight; }
    public String getLanguage() { return language; }
    /** OOXML rPr/@cap: "none" (default), "small" (smallcaps), or "all" (uppercase transform). Null means inherit. */
    public String getCapitalization() { return capitalization; }
    /**
     * OOXML rPr/@baseline: super/sub-script offset, expressed as percent
     * of font size times 1000. Positive raises the run above the baseline
     * (superscript), negative drops it below (subscript). Null/zero is
     * normal baseline.
     */
    public Integer getBaseline() { return baseline; }
    /**
     * OOXML rPr/@spc: additional spacing between characters (tracking),
     * in hundredths of a point. Positive = looser, negative = tighter.
     * Null means inherit. True per-character rendering needs the AWT
     * per-glyph accumulator (backlog); measurement currently approximates
     * by adding {@code spc * charCount} to each word's width, which is
     * enough for wrap-width calculations to track the intent.
     */
    public Integer getCharacterSpacing() { return characterSpacing; }

    /**
     * Text as it should be rendered + measured, after applying any
     * capitalization transform. {@code cap="all"} uppercases the raw
     * text; {@code cap="small"} falls back to the raw text for now
     * (true smallcaps needs per-glyph font support, tracked in the
     * paragraph-coverage backlog). {@code cap="none"} or null returns
     * the raw text unchanged.
     */
    public String getDisplayText() {
        if (text == null) return "";
        if ("all".equalsIgnoreCase(capitalization)) {
            return text.toUpperCase(java.util.Locale.ROOT);
        }
        return text;
    }

    public static Builder builder(String text) {
        return new Builder(text);
    }

    public static final class Builder {
        private final String text;
        private Integer fontSize;
        private Boolean bold;
        private Boolean italic;
        private String underline;
        private String strikethrough;
        private String fontFamily;
        private TextColor color;
        private TextColor highlight;
        private String language = "en-US";
        private String capitalization;
        private Integer baseline;
        private Integer characterSpacing;

        private Builder(String text) {
            this.text = text;
        }

        public Builder fontSize(int hundredthsOfPoint) { this.fontSize = hundredthsOfPoint; return this; }
        public Builder bold(boolean bold) { this.bold = bold; return this; }
        public Builder italic(boolean italic) { this.italic = italic; return this; }
        public Builder underline(String type) { this.underline = type; return this; }
        public Builder strikethrough(String type) { this.strikethrough = type; return this; }
        public Builder fontFamily(String family) { this.fontFamily = family; return this; }
        public Builder color(TextColor color) { this.color = color; return this; }
        public Builder schemeColor(String val) { this.color = TextColor.scheme(val); return this; }
        public Builder hexColor(String hex) { this.color = TextColor.hex(hex); return this; }
        public Builder highlight(TextColor hl) { this.highlight = hl; return this; }
        public Builder language(String lang) { this.language = lang; return this; }
        /** OOXML values: "none", "small", "all". Null keeps inheritance. */
        public Builder capitalization(String cap) { this.capitalization = cap; return this; }
        /** Super/sub-script offset in percent*1000 units. Positive = super, negative = sub. */
        public Builder baseline(int baseline) { this.baseline = baseline; return this; }
        /** Character spacing (tracking) in hundredths of a point. Positive = looser, negative = tighter. */
        public Builder characterSpacing(int spc) { this.characterSpacing = spc; return this; }

        public TextRun build() {
            return new TextRun(this);
        }
    }
}
