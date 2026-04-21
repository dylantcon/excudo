package com.excudo.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A paragraph containing one or more text runs with shared paragraph-level properties.
 * Nullable fields indicate "inherit from theme/layout".
 */
public final class TextParagraph {

    private final List<TextRun> runs;
    private final String alignment;
    private final int level;
    private final BulletType bulletType;
    private final String bulletChar;
    private final String bulletFont;
    private final String bulletFontPanose;
    private final String bulletFontPitchFamily;
    private final String bulletFontCharset;
    private final String autonumType;
    private final Integer marginLeft;
    private final Integer marginRight;
    private final Integer indent;
    private final Integer lineSpacing;
    private final Integer spaceBefore;
    private final Integer spaceAfter;

    private TextParagraph(Builder builder) {
        this.runs = Collections.unmodifiableList(new ArrayList<>(builder.runs));
        this.alignment = builder.alignment;
        this.level = builder.level;
        this.bulletType = builder.bulletType;
        this.bulletChar = builder.bulletChar;
        this.bulletFont = builder.bulletFont;
        this.bulletFontPanose = builder.bulletFontPanose;
        this.bulletFontPitchFamily = builder.bulletFontPitchFamily;
        this.bulletFontCharset = builder.bulletFontCharset;
        this.autonumType = builder.autonumType;
        this.marginLeft = builder.marginLeft;
        this.marginRight = builder.marginRight;
        this.indent = builder.indent;
        this.lineSpacing = builder.lineSpacing;
        this.spaceBefore = builder.spaceBefore;
        this.spaceAfter = builder.spaceAfter;
    }

    public List<TextRun> getRuns() { return runs; }
    public String getAlignment() { return alignment; }
    public int getLevel() { return level; }
    public BulletType getBulletType() { return bulletType; }
    public String getBulletChar() { return bulletChar; }
    public String getBulletFont() { return bulletFont; }
    public String getBulletFontPanose() { return bulletFontPanose; }
    public String getBulletFontPitchFamily() { return bulletFontPitchFamily; }
    public String getBulletFontCharset() { return bulletFontCharset; }
    public String getAutonumType() { return autonumType; }
    public Integer getMarginLeft() { return marginLeft; }
    public Integer getMarginRight() { return marginRight; }
    public Integer getIndent() { return indent; }
    public Integer getLineSpacing() { return lineSpacing; }
    public Integer getSpaceBefore() { return spaceBefore; }
    public Integer getSpaceAfter() { return spaceAfter; }

    public boolean isEmpty() {
        return runs.isEmpty() || (runs.size() == 1 && runs.get(0).getText().isEmpty());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<TextRun> runs = new ArrayList<>();
        private String alignment;
        private int level = 0;
        private BulletType bulletType = BulletType.NONE;
        private String bulletChar;
        private String bulletFont;
        private String bulletFontPanose;
        private String bulletFontPitchFamily;
        private String bulletFontCharset;
        private String autonumType;
        private Integer marginLeft;
        private Integer marginRight;
        private Integer indent;
        private Integer lineSpacing;
        private Integer spaceBefore;
        private Integer spaceAfter;

        public Builder addRun(TextRun run) { this.runs.add(run); return this; }
        public Builder addText(String text) { this.runs.add(TextRun.builder(text).build()); return this; }
        public Builder alignment(String align) { this.alignment = align; return this; }
        public Builder level(int lvl) { this.level = lvl; return this; }
        public Builder bulletType(BulletType type) { this.bulletType = type; return this; }
        public Builder bulletChar(String ch) { this.bulletChar = ch; return this; }
        public Builder bulletFont(String font) { this.bulletFont = font; return this; }
        public Builder bulletFontPanose(String panose) { this.bulletFontPanose = panose; return this; }
        public Builder bulletFontPitchFamily(String pf) { this.bulletFontPitchFamily = pf; return this; }
        public Builder bulletFontCharset(String cs) { this.bulletFontCharset = cs; return this; }
        public Builder autonumType(String type) { this.autonumType = type; return this; }
        public Builder marginLeft(int emu) { this.marginLeft = emu; return this; }
        public Builder marginRight(int emu) { this.marginRight = emu; return this; }
        public Builder indent(int emu) { this.indent = emu; return this; }
        public Builder lineSpacing(int percentTimes1000) { this.lineSpacing = percentTimes1000; return this; }
        public Builder spaceBefore(int pointsTimes100) { this.spaceBefore = pointsTimes100; return this; }
        public Builder spaceAfter(int pointsTimes100) { this.spaceAfter = pointsTimes100; return this; }

        /** Convenience: set up character bullet with full font metadata */
        public Builder characterBullet(String ch, String font, String panose, String pitchFamily, String charset) {
            this.bulletType = BulletType.CHARACTER;
            this.bulletChar = ch;
            this.bulletFont = font;
            this.bulletFontPanose = panose;
            this.bulletFontPitchFamily = pitchFamily;
            this.bulletFontCharset = charset;
            return this;
        }

        /** Convenience: set up autonumber bullet */
        public Builder autonumber(String type) {
            this.bulletType = BulletType.AUTONUMBER;
            this.autonumType = type;
            return this;
        }

        public TextParagraph build() {
            return new TextParagraph(this);
        }
    }
}
