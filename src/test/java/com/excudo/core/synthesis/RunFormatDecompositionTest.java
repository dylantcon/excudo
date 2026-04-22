package com.excudo.core.synthesis;

import com.excudo.core.introspection.LayoutBaseline;
import com.excudo.core.model.LayoutInfo;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.SlideShape;
import com.excudo.core.model.TextBody;
import com.excudo.core.model.TextColor;
import com.excudo.core.model.TextParagraph;
import com.excudo.core.model.TextRun;
import com.excudo.core.synthesis.spec.CommandSpec;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Verifies the synthesizer's TEXT field decomposition: emit per-run
 * {@link CommandSpec.SetRunFormatSpec} when only run formatting
 * differs, fall back to {@link CommandSpec.SetTextSpec} when paragraph
 * structure or text content changed.
 */
public class RunFormatDecompositionTest {

    private static final LayoutBaseline DUMMY_BASELINE = new LayoutBaseline(
        new LayoutInfo("slideLayout1", "Title Slide", "slideLayouts/slideLayout1.xml",
            true, false, true, "Title slide"),
        Collections.emptyMap(), Collections.emptyMap(), null);

    @Test
    public void onlyRunColorChanged_emitsSetRunFormatSpec() {
        TextBody before = oneParaOneRun(TextRun.builder("hello").build());
        TextBody after  = oneParaOneRun(TextRun.builder("hello")
            .color(TextColor.hex("FF0000")).build());

        SlideStateDiff diff = diffForTextBodyChange(before, after);
        ScriptSynthesizer.Result r = ScriptSynthesizer.synthesize(diff, 1);
        assertEquals("Exactly one spec", 1, r.script().size());
        CommandSpec only = r.script().nodes().iterator().next();
        assertTrue("Expected SetRunFormatSpec, got " + only.getClass().getSimpleName(),
            only instanceof CommandSpec.SetRunFormatSpec);
        CommandSpec.SetRunFormatSpec fmt = (CommandSpec.SetRunFormatSpec) only;
        assertEquals("Targets paragraph 0", 0, fmt.paragraphIdx());
        assertEquals("Targets run 0", 0, fmt.runIdx());
        assertNotNull(fmt.newRun().getColor());
    }

    @Test
    public void runTextChanged_fallsBackToSetTextSpec() {
        TextBody before = oneParaOneRun(TextRun.builder("hello").build());
        TextBody after  = oneParaOneRun(TextRun.builder("goodbye").build());
        SlideStateDiff diff = diffForTextBodyChange(before, after);
        ScriptSynthesizer.Result r = ScriptSynthesizer.synthesize(diff, 1);
        assertEquals(1, r.script().size());
        CommandSpec only = r.script().nodes().iterator().next();
        assertTrue("Expected SetTextSpec, got " + only.getClass().getSimpleName(),
            only instanceof CommandSpec.SetTextSpec);
    }

    @Test
    public void paragraphCountChanged_fallsBackToSetTextSpec() {
        TextBody before = oneParaOneRun(TextRun.builder("hello").build());
        TextBody after = TextBody.builder()
            .addParagraph(TextParagraph.builder()
                .addRun(TextRun.builder("hello").build()).build())
            .addParagraph(TextParagraph.builder()
                .addRun(TextRun.builder("world").build()).build())
            .build();
        SlideStateDiff diff = diffForTextBodyChange(before, after);
        ScriptSynthesizer.Result r = ScriptSynthesizer.synthesize(diff, 1);
        assertEquals(1, r.script().size());
        assertTrue(r.script().nodes().iterator().next() instanceof CommandSpec.SetTextSpec);
    }

    @Test
    public void multipleRunFormatChanges_emitOnePerRun() {
        TextBody before = TextBody.builder().addParagraph(TextParagraph.builder()
            .addRun(TextRun.builder("A").build())
            .addRun(TextRun.builder("B").build())
            .addRun(TextRun.builder("C").build()).build())
            .build();
        TextBody after = TextBody.builder().addParagraph(TextParagraph.builder()
            .addRun(TextRun.builder("A").color(TextColor.hex("FF0000")).build())
            .addRun(TextRun.builder("B").build()) // unchanged
            .addRun(TextRun.builder("C").bold(true).build()).build())
            .build();
        SlideStateDiff diff = diffForTextBodyChange(before, after);
        ScriptSynthesizer.Result r = ScriptSynthesizer.synthesize(diff, 1);
        long runSpecs = r.script().nodes().stream()
            .filter(s -> s instanceof CommandSpec.SetRunFormatSpec).count();
        long textSpecs = r.script().nodes().stream()
            .filter(s -> s instanceof CommandSpec.SetTextSpec).count();
        assertEquals("Two runs changed -> two SetRunFormatSpecs", 2L, runSpecs);
        assertEquals("No SetTextSpec (structural match on all runs)", 0L, textSpecs);
    }

    // ========== helpers ==========

    private static TextBody oneParaOneRun(TextRun run) {
        return TextBody.builder()
            .addParagraph(TextParagraph.builder().addRun(run).build())
            .build();
    }

    private static SlideStateDiff diffForTextBodyChange(TextBody before, TextBody after) {
        ShapeSnapshot b = shape(5, before);
        ShapeSnapshot c = shape(5, after);
        SlideState sb = new SlideState(1, Map.of(5, b), List.of(), null, DUMMY_BASELINE);
        SlideState sc = new SlideState(1, Map.of(5, c), List.of(), null, DUMMY_BASELINE);
        return SlideStateDiffer.diff(sb, sc);
    }

    private static ShapeSnapshot shape(int spid, TextBody body) {
        return new ShapeSnapshot(spid, "R", SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(0, 0, 1000, 500), null, body, false, null);
    }
}
