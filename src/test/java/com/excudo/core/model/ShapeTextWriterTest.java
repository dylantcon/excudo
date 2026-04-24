package com.excudo.core.model;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Pins the single entry point for rendering shape text. The regression
 * this test class exists to prevent: bulleted lists on multi-paragraph
 * shapes were rendering only the first paragraph via
 * {@code get_slide_shapes}, because the agent path used
 * {@code shape.getTextContent()} populated by a first-run-only XPath.
 * {@link ShapeTextWriter} is now the sole code path.
 */
public class ShapeTextWriterTest {

    @Test
    public void writesEveryParagraphFromMetadata() {
        SlideShape shape = shapeWithParagraphs(
            List.of("First bullet", "Second bullet", "Third bullet"),
            List.of(true, true, true),
            Arrays.asList("•", "•", "•"));

        String out = ShapeTextWriter.render(shape, "  ");
        assertEquals(
            "  • First bullet\n" +
            "  • Second bullet\n" +
            "  • Third bullet\n",
            out);
    }

    @Test
    public void nonBulletedParagraphsOmitMarker() {
        SlideShape shape = shapeWithParagraphs(
            List.of("Plain line one", "Plain line two"),
            List.of(false, false),
            Arrays.asList(null, null));

        String out = ShapeTextWriter.render(shape, "");
        assertEquals("Plain line one\nPlain line two\n", out);
    }

    @Test
    public void fallbackMarkerUsedWhenBulletMarkerMissing() {
        // A bullet paragraph with no explicit marker string still renders
        // visibly so callers can tell it's a bullet — prevents silent
        // drops when PML source omits buChar.
        SlideShape shape = shapeWithParagraphs(
            List.of("Bullet content"),
            List.of(true),
            Arrays.asList((String) null));

        String out = ShapeTextWriter.render(shape, "");
        assertEquals("- Bullet content\n", out);
    }

    @Test
    public void emptyParagraphsAreSkipped() {
        SlideShape shape = shapeWithParagraphs(
            List.of("Kept", "", "   ", "Also kept"),
            List.of(false, false, false, false),
            Arrays.asList(null, null, null, null));

        String out = ShapeTextWriter.render(shape, "");
        assertEquals("Kept\nAlso kept\n", out);
    }

    @Test
    public void fallsBackToTextContentWhenNoMetadata() {
        // The no-metadata path used to truncate at the first '\n'; the
        // fallback in ShapeTextWriter now honors multi-line text content
        // (which SlideShape.getTextContent synthesizes from metadata).
        SlideShape shape = new SlideShape(1, "name", SlideShape.ShapeType.RECTANGLE,
            "line one\nline two\nline three", null, null);

        String out = ShapeTextWriter.render(shape, ">");
        assertEquals(">line one\n>line two\n>line three\n", out);
    }

    @Test
    public void returnsFalseWhenShapeHasNoText() {
        SlideShape shape = new SlideShape(1, "empty", SlideShape.ShapeType.RECTANGLE,
            null, null, null);

        StringBuilder sb = new StringBuilder();
        assertFalse(ShapeTextWriter.writeTo(shape, sb, ""));
        assertEquals("", sb.toString());
    }

    @Test
    public void slideShapeGetTextContentJoinsParagraphsWithNewlines() {
        // The behavior the agent-tool fix depends on: getTextContent now
        // concatenates ALL paragraph content, not just the first run.
        SlideShape shape = shapeWithParagraphs(
            List.of("First", "Second", "Third"),
            List.of(true, true, true),
            Arrays.asList("•", "•", "•"));

        assertEquals("First\nSecond\nThird", shape.getTextContent());
    }

    // ========== HELPERS ==========

    private static SlideShape shapeWithParagraphs(List<String> contents,
                                                   List<Boolean> isBullet,
                                                   List<String> markers) {
        ParagraphMetadata meta = new ParagraphMetadata(
            new ArrayList<>(contents),
            new ArrayList<>(isBullet),
            new ArrayList<>(markers));
        // SlideShape requires textContent; supply the first paragraph so
        // hasText() reports true — mirrors what the parser does today.
        String first = contents.isEmpty() ? "" : contents.get(0);
        return new SlideShape(1, "test", SlideShape.ShapeType.RECTANGLE,
            first, null, null, meta);
    }
}
