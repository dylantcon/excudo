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

        public TextRun build() {
            return new TextRun(this);
        }
    }
}
