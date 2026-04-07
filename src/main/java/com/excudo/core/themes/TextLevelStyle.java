package com.excudo.core.themes;

/**
 * Per-indentation-level text properties for slide master txStyles.
 * Represents one level (0-8) of a:lvl1pPr through a:lvl9pPr.
 *
 * Style inheritance hierarchy (most specific wins):
 *   Theme -> Slide Master -> Slide Layout -> Slide
 * Shapes inherit from their layout placeholder unless they override.
 */
public final class TextLevelStyle {

    private final int level;
    private final int fontSize;
    private final boolean bold;
    private final String colorRef;
    private final String bulletChar;
    private final String bulletFont;
    private final int marginLeft;
    private final int indent;
    private final int lineSpacing;
    private final int spaceBefore;
    private final String alignment;

    private TextLevelStyle(Builder builder) {
        this.level = builder.level;
        this.fontSize = builder.fontSize;
        this.bold = builder.bold;
        this.colorRef = builder.colorRef;
        this.bulletChar = builder.bulletChar;
        this.bulletFont = builder.bulletFont;
        this.marginLeft = builder.marginLeft;
        this.indent = builder.indent;
        this.lineSpacing = builder.lineSpacing;
        this.spaceBefore = builder.spaceBefore;
        this.alignment = builder.alignment;
    }

    public int getLevel() { return level; }
    public int getFontSize() { return fontSize; }
    public boolean isBold() { return bold; }
    public String getColorRef() { return colorRef; }
    public String getBulletChar() { return bulletChar; }
    public String getBulletFont() { return bulletFont; }
    public int getMarginLeft() { return marginLeft; }
    public int getIndent() { return indent; }
    public int getLineSpacing() { return lineSpacing; }
    public int getSpaceBefore() { return spaceBefore; }
    public String getAlignment() { return alignment; }

    public boolean hasBullet() {
        return bulletChar != null && !bulletChar.isEmpty();
    }

    public boolean isSchemeColor() {
        return colorRef != null && !colorRef.startsWith("#");
    }

    public String getHexColor() {
        if (colorRef == null) return null;
        return colorRef.startsWith("#") ? colorRef.substring(1) : null;
    }

    public static Builder builder(int level) {
        return new Builder(level);
    }

    public static final class Builder {
        private final int level;
        private int fontSize = 1800;
        private boolean bold = false;
        private String colorRef = "tx1";
        private String bulletChar = null;
        private String bulletFont = null;
        private int marginLeft = 0;
        private int indent = 0;
        private int lineSpacing = 100000;
        private int spaceBefore = 0;
        private String alignment = "l";

        private Builder(int level) {
            if (level < 0 || level > 8) {
                throw new IllegalArgumentException("Level must be 0-8, got: " + level);
            }
            this.level = level;
        }

        public Builder fontSize(int hundredthsOfPoint) { this.fontSize = hundredthsOfPoint; return this; }
        public Builder fontSizePt(int points) { this.fontSize = points * 100; return this; }
        public Builder bold(boolean bold) { this.bold = bold; return this; }
        public Builder colorRef(String ref) { this.colorRef = ref; return this; }
        public Builder hexColor(String hex) { this.colorRef = "#" + hex.replace("#", ""); return this; }
        public Builder bullet(String character, String fontName) { this.bulletChar = character; this.bulletFont = fontName; return this; }
        public Builder noBullet() { this.bulletChar = null; this.bulletFont = null; return this; }
        public Builder marginLeft(int emu) { this.marginLeft = emu; return this; }
        public Builder indent(int emu) { this.indent = emu; return this; }
        public Builder lineSpacing(int percentTimes1000) { this.lineSpacing = percentTimes1000; return this; }
        public Builder spaceBefore(int pointsTimes100) { this.spaceBefore = pointsTimes100; return this; }
        public Builder alignment(String align) { this.alignment = align; return this; }

        public TextLevelStyle build() {
            return new TextLevelStyle(this);
        }
    }
}
