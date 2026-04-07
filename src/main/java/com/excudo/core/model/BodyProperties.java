package com.excudo.core.model;

/**
 * Text body configuration properties (a:bodyPr attributes).
 * Nullable fields indicate "use default" -- only non-null fields emit XML attributes.
 */
public final class BodyProperties {

    private final String verticalAlignment;
    private final String wrap;
    private final String verticalText;
    private final AutofitType autofit;
    private final Integer fontScale;
    private final Integer leftInset;
    private final Integer topInset;
    private final Integer rightInset;
    private final Integer bottomInset;
    private final Integer numColumns;
    private final boolean rtlCol;

    private BodyProperties(Builder builder) {
        this.verticalAlignment = builder.verticalAlignment;
        this.wrap = builder.wrap;
        this.verticalText = builder.verticalText;
        this.autofit = builder.autofit;
        this.fontScale = builder.fontScale;
        this.leftInset = builder.leftInset;
        this.topInset = builder.topInset;
        this.rightInset = builder.rightInset;
        this.bottomInset = builder.bottomInset;
        this.numColumns = builder.numColumns;
        this.rtlCol = builder.rtlCol;
    }

    public String getVerticalAlignment() { return verticalAlignment; }
    public String getWrap() { return wrap; }
    public String getVerticalText() { return verticalText; }
    public AutofitType getAutofit() { return autofit; }
    public Integer getFontScale() { return fontScale; }
    public Integer getLeftInset() { return leftInset; }
    public Integer getTopInset() { return topInset; }
    public Integer getRightInset() { return rightInset; }
    public Integer getBottomInset() { return bottomInset; }
    public Integer getNumColumns() { return numColumns; }
    public boolean isRtlCol() { return rtlCol; }

    /** Default empty body properties -- produces bare &lt;a:bodyPr/&gt; */
    public static final BodyProperties DEFAULT = new Builder().build();

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String verticalAlignment;
        private String wrap;
        private String verticalText;
        private AutofitType autofit;
        private Integer fontScale;
        private Integer leftInset;
        private Integer topInset;
        private Integer rightInset;
        private Integer bottomInset;
        private Integer numColumns;
        private boolean rtlCol = false;

        public Builder verticalAlignment(String anchor) { this.verticalAlignment = anchor; return this; }
        public Builder wrap(String wrap) { this.wrap = wrap; return this; }
        public Builder verticalText(String vert) { this.verticalText = vert; return this; }
        public Builder autofit(AutofitType type) { this.autofit = type; return this; }
        public Builder fontScale(int scale) { this.fontScale = scale; return this; }
        public Builder leftInset(int emu) { this.leftInset = emu; return this; }
        public Builder topInset(int emu) { this.topInset = emu; return this; }
        public Builder rightInset(int emu) { this.rightInset = emu; return this; }
        public Builder bottomInset(int emu) { this.bottomInset = emu; return this; }
        public Builder numColumns(int cols) { this.numColumns = cols; return this; }
        public Builder rtlCol(boolean rtl) { this.rtlCol = rtl; return this; }

        public BodyProperties build() {
            return new BodyProperties(this);
        }
    }
}
