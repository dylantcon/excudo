package com.excudo.core.commands;

import com.excudo.core.commands.mutating.slide.ContentEditCommand;

import com.excudo.core.metrics.TextBodyExtractor;
import com.excudo.core.model.PPTXDocument;
import com.excudo.core.model.SlideShape;
import com.excudo.core.model.TextBody;
import com.excudo.core.model.TextParagraph;
import com.excudo.core.orchestration.PPTXOrchestratorImpl;
import com.excudo.core.orchestration.PresentationScaffolder;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/**
 * Tests for ContentEditCommand and the underlying editShapeText / setTextBody
 * orchestrator paths.
 *
 * <p>Historically this file consisted of smoke tests (assertNotNull / isSuccess)
 * that verified the call didn't throw but never asserted on resulting state.
 * That left both {@code getShapeText}-returning-only-the-first-run AND
 * bullet-clobbering-on-string-concat undetected for the lifetime of the
 * project. The strengthened tests here assert on the actual TextBody after
 * each edit so any state regression in either direction surfaces immediately.
 */
public class ContentEditCommandTest {

    private PPTXOrchestratorImpl orchestrator;

    @Before
    public void setUp() throws Exception {
        PPTXDocument doc = PresentationScaffolder.scaffoldDocument("minimal");
        orchestrator = new PPTXOrchestratorImpl();
        orchestrator.initialize(doc);
        orchestrator.createSlide(1, "Initial Title", "slideLayout2");
    }

    // ========== REPLACE mode (orchestrator.editShapeText) ==========

    @Test
    public void replaceShapeTextChangesContent() {
        var result = orchestrator.editShapeText(1, 2, "Updated Title");
        assertTrue("Edit should succeed", result.isSuccess());
        TextBody body = readTextBody(2);
        assertNotNull("title txBody must exist after edit", body);
        assertEquals("Updated Title", concatRunText(body));
    }

    @Test
    public void replaceWithEmptyClearsContent() {
        // First populate with text, then clear.
        orchestrator.editShapeText(1, 2, "Some text");
        TextBody before = readTextBody(2);
        assertEquals("Some text", concatRunText(before));

        var result = orchestrator.editShapeText(1, 2, "");
        assertTrue("Empty edit should succeed", result.isSuccess());

        TextBody after = readTextBody(2);
        assertNotNull(after);
        assertEquals("After clear, the run text must be empty", "", concatRunText(after));
    }

    @Test
    public void replaceMultiLineCreatesParagraphPerLine() {
        var result = orchestrator.editShapeText(1, 3, "Line 1\nLine 2\nLine 3");
        assertTrue(result.isSuccess());
        TextBody body = readTextBody(3);
        assertNotNull(body);
        assertEquals("Three newline-separated lines should produce three paragraphs",
            3, body.getParagraphs().size());
        List<String> lines = body.getParagraphs().stream()
            .map(this::paragraphText)
            .collect(Collectors.toList());
        assertEquals(List.of("Line 1", "Line 2", "Line 3"), lines);
    }

    @Test
    public void replaceWithBulletMarkdownCreatesBulletParagraphs() {
        var result = orchestrator.editShapeText(1, 3,
            "- Bullet 1\n- Bullet 2\n  - Sub-bullet");
        assertTrue(result.isSuccess());
        TextBody body = readTextBody(3);
        assertNotNull(body);
        assertEquals("Three bullet lines should produce three bullet paragraphs",
            3, body.getParagraphs().size());

        TextParagraph first = body.getParagraphs().get(0);
        TextParagraph second = body.getParagraphs().get(1);
        TextParagraph third = body.getParagraphs().get(2);

        assertEquals("Bullet 1", paragraphText(first));
        assertEquals("Bullet 2", paragraphText(second));
        assertEquals("Sub-bullet", paragraphText(third));

        // Bullet paragraphs carry the canonical hanging-indent margin/indent.
        assertNotNull("first bullet should have marginLeft", first.getMarginLeft());
        assertNotNull("first bullet should have indent", first.getIndent());
        assertEquals("Sub-bullet should be level 1 (two leading spaces in markdown)",
            1, third.getLevel());
    }

    // ========== Failure modes still fail loud ==========

    @Test
    public void editNonexistentShapeFails() {
        var result = orchestrator.editShapeText(1, 999, "Text");
        assertFalse(result.isSuccess());
    }

    @Test
    public void editNonexistentSlideFails() {
        var result = orchestrator.editShapeText(99, 2, "Text");
        assertFalse(result.isSuccess());
    }

    // ========== PREPEND/APPEND via ContentEditCommand at TextBody level ==========

    @Test
    public void appendBulletPreservesExistingBulletParagraphs() throws Exception {
        // Seed a 3-bullet list, then append a 4th bullet via APPEND mode.
        orchestrator.editShapeText(1, 3, "- one\n- two\n- three");
        TextBody before = readTextBody(3);
        assertEquals("seed produces 3 paragraphs", 3, before.getParagraphs().size());

        ShapeCommandFactory factory = new ShapeCommandFactory(orchestrator);
        ContentEditCommand cmd = factory.createContentEdit(
            1, 3, "- four", ContentEditCommand.Mode.APPEND, null);
        cmd.execute();

        TextBody after = readTextBody(3);
        assertEquals("APPEND should produce 4 paragraphs", 4, after.getParagraphs().size());
        assertEquals("one",   paragraphText(after.getParagraphs().get(0)));
        assertEquals("two",   paragraphText(after.getParagraphs().get(1)));
        assertEquals("three", paragraphText(after.getParagraphs().get(2)));
        assertEquals("four",  paragraphText(after.getParagraphs().get(3)));
        // Existing bullet styling preserved -- this is the regression that
        // string-concat prepend/append used to silently break.
        for (int i = 0; i < 3; i++) {
            TextParagraph p = after.getParagraphs().get(i);
            assertNotNull("pre-existing bullet " + i + " should keep marginLeft", p.getMarginLeft());
            assertNotNull("pre-existing bullet " + i + " should keep indent", p.getIndent());
        }
    }

    @Test
    public void prependBulletPreservesExistingBulletParagraphs() throws Exception {
        orchestrator.editShapeText(1, 3, "- one\n- two");
        TextBody before = readTextBody(3);
        assertEquals(2, before.getParagraphs().size());

        ShapeCommandFactory factory = new ShapeCommandFactory(orchestrator);
        ContentEditCommand cmd = factory.createContentEdit(
            1, 3, "- zero", ContentEditCommand.Mode.PREPEND, null);
        cmd.execute();

        TextBody after = readTextBody(3);
        assertEquals("PREPEND should produce 3 paragraphs", 3, after.getParagraphs().size());
        assertEquals("zero", paragraphText(after.getParagraphs().get(0)));
        assertEquals("one",  paragraphText(after.getParagraphs().get(1)));
        assertEquals("two",  paragraphText(after.getParagraphs().get(2)));
    }

    @Test
    public void appendNumberedPreservesNumberedFormat() throws Exception {
        orchestrator.editShapeText(1, 3, "1. first\n2. second");
        TextBody before = readTextBody(3);
        assertEquals(2, before.getParagraphs().size());

        ShapeCommandFactory factory = new ShapeCommandFactory(orchestrator);
        ContentEditCommand cmd = factory.createContentEdit(
            1, 3, "3. third", ContentEditCommand.Mode.APPEND, null);
        cmd.execute();

        TextBody after = readTextBody(3);
        assertEquals(3, after.getParagraphs().size());
        // All three should carry numbered (autonum) formatting.
        for (int i = 0; i < 3; i++) {
            TextParagraph p = after.getParagraphs().get(i);
            assertEquals("paragraph " + i + " should be AUTONUMBER",
                com.excudo.core.model.BulletType.AUTONUMBER, p.getBulletType());
            assertEquals("autonumType should be arabicPeriod", "arabicPeriod", p.getAutonumType());
        }
    }

    @Test
    public void prependEmptyTextIsNoOp() throws Exception {
        orchestrator.editShapeText(1, 3, "- one\n- two");
        TextBody before = readTextBody(3);

        ShapeCommandFactory factory = new ShapeCommandFactory(orchestrator);
        ContentEditCommand cmd = factory.createContentEdit(
            1, 3, "", ContentEditCommand.Mode.PREPEND, null);
        cmd.execute();

        TextBody after = readTextBody(3);
        assertEquals("No-op prepend should leave paragraph count unchanged",
            before.getParagraphs().size(), after.getParagraphs().size());
        for (int i = 0; i < before.getParagraphs().size(); i++) {
            assertEquals("Paragraph " + i + " text should be unchanged",
                paragraphText(before.getParagraphs().get(i)),
                paragraphText(after.getParagraphs().get(i)));
        }
    }

    @Test
    public void prependAndAppendAreMutuallyExclusive() throws Exception {
        // Dispatch-path enforcement -- fromParameters throws when both flags
        // are set, with a clear error message. After the class-registry sweep
        // the dispatch path is CommandClassRegistry.createFromParameters,
        // keyed on the derived name ContentEditCommand.NAME.
        try {
            var parsed = new com.excudo.core.parsing.CommandParameters(ContentEditCommand.NAME,
                java.util.Map.of("slide", "1", "spid", "3", "text", "x",
                                 "prepend", "true", "append", "true"));
            com.excudo.core.commands.CommandClassRegistry.createFromParameters(
                parsed, new com.excudo.core.commands.CommandContext(orchestrator, null));
            fail("Expected IllegalArgumentException for conflicting --prepend + --append");
        } catch (IllegalArgumentException e) {
            assertTrue("Error should mention mutual exclusivity",
                e.getMessage().toLowerCase().contains("mutually exclusive"));
        }
    }

    // ========== helpers ==========

    private TextBody readTextBody(int spid) {
        try {
            var slideData = orchestrator.getSlideData(1).getData().orElseThrow();
            SlideShape shape = slideData.getShapeRegistry().getShape(spid);
            assertNotNull("shape SPID " + spid + " must exist", shape);
            return TextBodyExtractor.extractFromShape(shape.getXmlElement());
        } catch (Exception e) {
            throw new RuntimeException("Failed to read shape SPID " + spid, e);
        }
    }

    private String concatRunText(TextBody body) {
        StringBuilder sb = new StringBuilder();
        for (TextParagraph p : body.getParagraphs()) {
            sb.append(paragraphText(p));
        }
        return sb.toString();
    }

    private String paragraphText(TextParagraph p) {
        StringBuilder sb = new StringBuilder();
        p.getRuns().forEach(r -> sb.append(r.getText() != null ? r.getText() : ""));
        return sb.toString();
    }
}
