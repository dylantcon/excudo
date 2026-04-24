package com.excudo.core.model;

/**
 * Single entry point for printing a {@link SlideShape}'s text content to
 * any {@link Appendable} (stdout, a StringBuilder, a log sink, etc.).
 *
 * <p>Before this existed, three separate surfaces duplicated the
 * paragraph-iteration loop — {@code ShowShapeCommand},
 * {@code ShowSlideCommand#displayShapeText}, and the
 * {@code get_slide_shapes} / {@code get_shape_detail} agent tools —
 * and the two agent tools were silently truncated because they went
 * through a parser XPath that returned only the first {@code <a:t>}
 * run. Different call sites diverged on what "the text of a shape"
 * meant; bulleted lists showed only bullet 1 in the agent view, while
 * the REPL's show-shape path rendered the full list.
 *
 * <p>Every caller funnels through {@link #writeTo}. Paragraph metadata,
 * when present, drives the output: each paragraph appears on its own
 * line, bulleted paragraphs get their source bullet marker, and empty
 * paragraphs are skipped. When metadata isn't available, the caller's
 * stored {@code textContent} is written as a single line after a
 * {@code Text:} label so the output stays unambiguous.
 */
public final class ShapeTextWriter {

    private ShapeTextWriter() {}

    /**
     * Write the shape's text to {@code out}, one paragraph per line,
     * each prefixed with {@code linePrefix}. Bulleted paragraphs include
     * their source marker character (e.g. {@code •}) between the prefix
     * and the content.
     *
     * @param shape       the shape to render; must not be null
     * @param out         destination stream
     * @param linePrefix  prefix for every line (pass {@code ""} for no
     *                    indentation, {@code "  "} for two-space indent, etc.)
     * @return true if any text was written, false when the shape has no
     *         text at all (caller can emit a blank-state message if desired)
     */
    public static boolean writeTo(SlideShape shape, Appendable out, String linePrefix) {
        if (shape == null) throw new IllegalArgumentException("shape is null");
        if (out == null) throw new IllegalArgumentException("out is null");
        String prefix = linePrefix == null ? "" : linePrefix;
        try {
            ParagraphMetadata meta = shape.getParagraphMetadata();
            if (meta != null && meta.getParagraphCount() > 0) {
                boolean wrote = false;
                for (int i = 0; i < meta.getParagraphCount(); i++) {
                    String content = meta.getParagraphContent(i);
                    if (content == null || content.trim().isEmpty()) continue;
                    out.append(prefix);
                    if (meta.isParagraphBullet(i)) {
                        String marker = meta.getBulletMarker(i);
                        out.append(marker != null && !marker.isEmpty() ? marker : "-");
                        out.append(' ');
                    }
                    out.append(content);
                    out.append('\n');
                    wrote = true;
                }
                return wrote;
            }
            String text = shape.getTextContent();
            if (text == null || text.trim().isEmpty()) return false;
            // Multi-paragraph fallback when metadata isn't available --
            // the new SlideShape.getTextContent synthesizes newlines
            // between paragraphs, so honor them.
            String[] lines = text.split("\\n", -1);
            for (String line : lines) {
                if (line.isEmpty()) continue;
                out.append(prefix).append(line).append('\n');
            }
            return true;
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to write shape text", e);
        }
    }

    /** Convenience: render to a String. */
    public static String render(SlideShape shape, String linePrefix) {
        StringBuilder sb = new StringBuilder();
        writeTo(shape, sb, linePrefix);
        return sb.toString();
    }
}
