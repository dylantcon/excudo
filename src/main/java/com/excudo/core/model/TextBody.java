package com.excudo.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Complete text body model for OOXML p:txBody generation.
 * Immutable after construction. Use builder or factory methods.
 */
public final class TextBody {

    private static final Pattern MARKDOWN_BULLET_PATTERN = Pattern.compile("^(\\s*)- (.+)$");
    private static final Pattern NUMBERED_PATTERN = Pattern.compile("^(\\s*)(\\d+)\\. (.+)$");

    /**
     * Inline markdown pattern: matches **bold**, *italic*, ***bold italic***, and
     * ~~strikethrough~~. Handles nesting by matching the most specific first.
     * Non-greedy so adjacent spans like **a** **b** don't merge.
     */
    private static final Pattern INLINE_MD_PATTERN = Pattern.compile(
        "\\*\\*\\*(.+?)\\*\\*\\*"          // ***bold italic***
        + "|\\*\\*(.+?)\\*\\*"             // **bold**
        + "|\\*(.+?)\\*"                   // *italic*
    );

    private static final int BULLET_MARGIN_EMU = 342900;
    private static final int BULLET_INDENT_EMU = -342900;

    private final List<TextParagraph> paragraphs;
    private final BodyProperties bodyProperties;
    private final boolean placeholder;

    private TextBody(Builder builder) {
        this.paragraphs = Collections.unmodifiableList(new ArrayList<>(builder.paragraphs));
        this.bodyProperties = builder.bodyProperties;
        this.placeholder = builder.placeholder;
    }

    public List<TextParagraph> getParagraphs() { return paragraphs; }
    public BodyProperties getBodyProperties() { return bodyProperties; }
    public boolean isPlaceholder() { return placeholder; }

    /**
     * Concatenate two TextBodies paragraph-by-paragraph. Used by
     * prepend/append content-edit modes to compose user-supplied
     * paragraphs onto a shape's existing paragraphs without touching
     * bullet markers, numbering, or run-level formatting.
     *
     * BodyProperties and placeholder flag are inherited from {@code base}
     * (the shape's existing text body) so the shape's own styling wins
     * over anything the caller's ad-hoc text might have implied.
     * Concatenates {@code base}'s paragraphs followed by {@code extra}'s
     * -- swap argument order for prepend vs append semantics.
     *
     * Null-safe: if either side is null, returns the other (or an empty
     * TextBody if both are null).
     */
    public static TextBody concat(TextBody base, TextBody extra) {
        if (base == null && extra == null) return builder().build();
        if (base == null) return extra;
        if (extra == null) return base;

        Builder b = builder()
            .bodyProperties(base.getBodyProperties())
            .placeholder(base.isPlaceholder());
        for (TextParagraph p : base.getParagraphs()) b.addParagraph(p);
        for (TextParagraph p : extra.getParagraphs()) b.addParagraph(p);
        return b.build();
    }

    /**
     * Factory method that splits text by newlines, detects markdown bullets,
     * numbered lists, and inline formatting (**bold**, *italic*, ***both***).
     */
    public static TextBody fromPlainText(String text, boolean isPlaceholder) {
        Builder builder = builder().placeholder(isPlaceholder);

        if (!isPlaceholder) {
            builder.bodyProperties(BodyProperties.builder()
                    .verticalAlignment("ctr")
                    .build());
        }

        if (text == null || text.trim().isEmpty()) {
            builder.addParagraph(createEmptyParagraph());
            return builder.build();
        }

        String[] lines = text.split("\\n");
        boolean hasBullets = false;
        for (String line : lines) {
            if (MARKDOWN_BULLET_PATTERN.matcher(line).matches() || NUMBERED_PATTERN.matcher(line).matches()) {
                hasBullets = true;
                break;
            }
        }

        for (String line : lines) {
            if (line.trim().isEmpty()) {
                builder.addParagraph(createEmptyParagraph());
                continue;
            }

            Matcher bulletMatcher = MARKDOWN_BULLET_PATTERN.matcher(line);
            if (bulletMatcher.matches()) {
                String leadingSpaces = bulletMatcher.group(1);
                int indentLevel = leadingSpaces.length() / 2;
                String content = bulletMatcher.group(2).trim();
                builder.addParagraph(createBulletParagraph(content, indentLevel, isPlaceholder));
                continue;
            }

            Matcher numberedMatcher = NUMBERED_PATTERN.matcher(line);
            if (numberedMatcher.matches()) {
                String leadingSpaces = numberedMatcher.group(1);
                int indentLevel = leadingSpaces.length() / 2;
                String content = numberedMatcher.group(3).trim();
                builder.addParagraph(createNumberedParagraph(content, indentLevel, isPlaceholder));
                continue;
            }

            builder.addParagraph(createPlainParagraph(line.trim(), isPlaceholder, !hasBullets));
        }

        return builder.build();
    }

    private static TextParagraph createEmptyParagraph() {
        return TextParagraph.builder().build();
    }

    private static TextParagraph createPlainParagraph(String text, boolean isPlaceholder, boolean centerAlign) {
        TextParagraph.Builder paraBuilder = TextParagraph.builder();
        addInlineFormattedRuns(paraBuilder, text, isPlaceholder);
        if (!isPlaceholder && centerAlign) {
            paraBuilder.alignment("ctr");
        }
        return paraBuilder.build();
    }

    private static TextParagraph createBulletParagraph(String text, int indentLevel, boolean isPlaceholder) {
        TextParagraph.Builder paraBuilder = TextParagraph.builder()
                .level(indentLevel)
                .marginLeft((indentLevel + 1) * BULLET_MARGIN_EMU)
                .indent(BULLET_INDENT_EMU);

        addInlineFormattedRuns(paraBuilder, text, isPlaceholder);

        if (!isPlaceholder) {
            paraBuilder.characterBullet("\u2022", "Arial",
                    "020B0604020202020204", "34", "0");
        } else {
            paraBuilder.bulletType(BulletType.INHERITED);
        }

        return paraBuilder.build();
    }

    private static TextParagraph createNumberedParagraph(String text, int indentLevel, boolean isPlaceholder) {
        TextParagraph.Builder paraBuilder = TextParagraph.builder()
                .level(indentLevel)
                .marginLeft((indentLevel + 1) * BULLET_MARGIN_EMU)
                .indent(BULLET_INDENT_EMU)
                .autonumber("arabicPeriod");

        addInlineFormattedRuns(paraBuilder, text, isPlaceholder);

        return paraBuilder.build();
    }

    /**
     * Parse inline markdown formatting and add runs to the paragraph builder.
     * Handles **bold**, *italic*, ***bold italic***, and plain text spans.
     * If no markdown is detected, a single run is emitted (same as before).
     */
    private static void addInlineFormattedRuns(TextParagraph.Builder paraBuilder,
                                                String text, boolean isPlaceholder) {
        Matcher m = INLINE_MD_PATTERN.matcher(text);
        int lastEnd = 0;
        boolean foundMarkdown = false;

        while (m.find()) {
            foundMarkdown = true;

            // Plain text before this match
            if (m.start() > lastEnd) {
                paraBuilder.addRun(buildRun(text.substring(lastEnd, m.start()), isPlaceholder, false, false));
            }

            // Determine which group matched
            if (m.group(1) != null) {
                // ***bold italic***
                paraBuilder.addRun(buildRun(m.group(1), isPlaceholder, true, true));
            } else if (m.group(2) != null) {
                // **bold**
                paraBuilder.addRun(buildRun(m.group(2), isPlaceholder, true, false));
            } else if (m.group(3) != null) {
                // *italic*
                paraBuilder.addRun(buildRun(m.group(3), isPlaceholder, false, true));
            }

            lastEnd = m.end();
        }

        if (!foundMarkdown) {
            // No inline markdown -- single plain run (preserves original behavior exactly)
            paraBuilder.addRun(buildRun(text, isPlaceholder, false, false));
        } else {
            // Trailing plain text after last match
            if (lastEnd < text.length()) {
                paraBuilder.addRun(buildRun(text.substring(lastEnd), isPlaceholder, false, false));
            }
        }
    }

    private static TextRun buildRun(String text, boolean isPlaceholder, boolean bold, boolean italic) {
        TextRun.Builder runBuilder = TextRun.builder(text);
        if (!isPlaceholder) {
            runBuilder.fontSize(1800).schemeColor("lt1");
        }
        if (bold) runBuilder.bold(true);
        if (italic) runBuilder.italic(true);
        return runBuilder.build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<TextParagraph> paragraphs = new ArrayList<>();
        private BodyProperties bodyProperties = BodyProperties.DEFAULT;
        private boolean placeholder = false;

        public Builder addParagraph(TextParagraph para) { this.paragraphs.add(para); return this; }
        public Builder bodyProperties(BodyProperties props) { this.bodyProperties = props; return this; }
        public Builder placeholder(boolean isPlaceholder) { this.placeholder = isPlaceholder; return this; }

        public TextBody build() {
            return new TextBody(this);
        }
    }
}
