package com.excudo.core.model;

import com.excudo.core.model.math.MathBody;

/**
 * Single run of uniformly formatted text within a paragraph.
 * Nullable fields indicate "inherit from theme/layout" -- only non-null fields emit XML attributes.
 *
 * <p>A run can carry math content instead of plain text: when
 * {@link #getMathBody()} is non-null the run renders via the math
 * pipeline ({@code MathPainter} consuming the {@link MathBody} AST +
 * MATH-table constants) rather than the plain-text painter. The
 * {@code text} field on a math run holds the flat fallback string
 * the Tier-A path produced -- still readable on environments that
 * lack the math layout pipeline. Mirrors PowerPoint's run-level
 * embedding of math via {@code <a14:m>/<m:oMath>}.
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
    private final Integer kerningThreshold;
    private final MathBody mathBody;
    private final boolean lineBreak;

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
        this.kerningThreshold = builder.kerningThreshold;
        this.mathBody = builder.mathBody;
        this.lineBreak = builder.lineBreak;
    }

    /**
     * Create a forced-line-break run, the model form of OOXML
     * {@code <a:br/>}. The break carries no glyphs; its {@code text} is
     * "\n" so paragraph-emptiness checks and flat-text extraction keep
     * working, but layout treats it purely as "start a new line here"
     * and the XML writer round-trips it back to {@code <a:br/>}.
     */
    public static TextRun lineBreakRun() {
        Builder b = new Builder("\n");
        b.lineBreak = true;
        return b.build();
    }

    /** True iff this run is a forced line break ({@code <a:br/>}). */
    public boolean isLineBreak() { return lineBreak; }

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
     * OOXML rPr/@kern: minimum font size at which kerning is applied,
     * in hundredths of a point. Zero disables kerning entirely; null
     * means inherit. Render-time application is AWT-backend only and
     * lives behind the per-glyph accumulator backlog item -- this
     * field round-trips today so author intent isn't stripped on edit.
     */
    public Integer getKerningThreshold() { return kerningThreshold; }

    /** Typed math AST when this run carries OMML content; null for
     *  plain-text runs. Non-null implies math rendering. */
    public MathBody getMathBody() { return mathBody; }

    /** Convenience: true iff this run has a non-null math body. */
    public boolean isMathRun() { return mathBody != null; }

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TextRun that)) return false;
        return java.util.Objects.equals(text, that.text)
            && java.util.Objects.equals(fontSize, that.fontSize)
            && java.util.Objects.equals(bold, that.bold)
            && java.util.Objects.equals(italic, that.italic)
            && java.util.Objects.equals(underline, that.underline)
            && java.util.Objects.equals(strikethrough, that.strikethrough)
            && java.util.Objects.equals(fontFamily, that.fontFamily)
            && java.util.Objects.equals(color, that.color)
            && java.util.Objects.equals(highlight, that.highlight)
            && java.util.Objects.equals(language, that.language)
            && java.util.Objects.equals(capitalization, that.capitalization)
            && java.util.Objects.equals(baseline, that.baseline)
            && java.util.Objects.equals(characterSpacing, that.characterSpacing)
            && java.util.Objects.equals(kerningThreshold, that.kerningThreshold)
            && java.util.Objects.equals(mathBody, that.mathBody)
            && lineBreak == that.lineBreak;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(text, fontSize, bold, italic, underline, strikethrough,
            fontFamily, color, highlight, language, capitalization, baseline,
            characterSpacing, kerningThreshold, mathBody, lineBreak);
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
        private Integer kerningThreshold;
        private MathBody mathBody;
        private boolean lineBreak;

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
        /** Kerning threshold in hundredths of a point. Zero disables kerning. */
        public Builder kerningThreshold(int kern) { this.kerningThreshold = kern; return this; }
        /** Math AST for this run. Non-null marks the run as a math
         *  run and routes rendering through the math pipeline. The
         *  surrounding {@link TextRun#getText() text} stays as a flat
         *  fallback string. */
        public Builder mathBody(MathBody body) { this.mathBody = body; return this; }

        public TextRun build() {
            return new TextRun(this);
        }
    }
}
