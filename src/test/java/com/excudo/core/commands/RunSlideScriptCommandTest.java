package com.excudo.core.commands;

import com.excudo.core.model.PPTXDocument;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.ShapeStyle;
import com.excudo.core.model.SlideShape;
import com.excudo.core.orchestration.PPTXOrchestratorImpl;
import com.excudo.core.orchestration.PresentationScaffolder;
import com.excudo.core.results.ExecutionResult;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * End-to-end state tests for the synthesize + run script Commands.
 * Models the "clone slide with overrides" workflow: synthesize source,
 * run on target, verify structural equivalence. Also exercises undo
 * on the run command so the full invoker-history cycle works.
 */
public class RunSlideScriptCommandTest {

    private PPTXOrchestratorImpl orchestrator;

    @Before
    public void setUp() throws Exception {
        PPTXDocument doc = PresentationScaffolder.scaffoldDocument("excudo");
        orchestrator = new PPTXOrchestratorImpl();
        orchestrator.initialize(doc);
    }

    @Test
    public void synthesizeThenRun_clonesShapesToTargetSlide() {
        // Source slide: two rectangles.
        orchestrator.createSlide(1, "Source", "slideLayout7");
        orchestrator.createSlide(2, "Target", "slideLayout7");
        addRect(1, 1_000_000, 1_000_000, 2_000_000, 1_000_000);
        addRect(1, 4_000_000, 1_000_000, 2_000_000, 1_000_000);
        assertEquals("Source has 2 shapes pre-synth", 2, shapeCount(1));
        assertEquals("Target starts empty",           0, shapeCount(2));

        SynthesizeSlideScriptCommand synth = new SynthesizeSlideScriptCommand(1, orchestrator);
        synth.execute();
        // Each rectangle produces an AddShapeSpec + a SetTextSpec
        // (because ShapeFactory centers the paragraph for empty-text
        // shapes, which counts as non-plain formatting in
        // hasNonPlainContent). Two rects -> four specs.
        assertTrue("Synth reports at least 2 specs (AddShape per rect, possibly +SetText)",
            synth.getSpecCount() >= 2);
        assertNotNull(synth.getScriptJson());

        RunSlideScriptCommand run = new RunSlideScriptCommand(
            2, synth.getScriptJson(), orchestrator, null);
        run.execute();
        assertEquals("Target now has 2 shapes", 2, shapeCount(2));
        assertEquals("Source unchanged",        2, shapeCount(1));
        assertTrue("Run command is undoable", run.canUndo());
    }

    @Test
    public void run_undoRemovesAppliedShapes() {
        orchestrator.createSlide(1, "Source", "slideLayout7");
        orchestrator.createSlide(2, "Target", "slideLayout7");
        addRect(1, 1_000_000, 1_000_000, 2_000_000, 1_000_000);

        SynthesizeSlideScriptCommand synth = new SynthesizeSlideScriptCommand(1, orchestrator);
        synth.execute();
        RunSlideScriptCommand run = new RunSlideScriptCommand(
            2, synth.getScriptJson(), orchestrator, null);
        run.execute();
        assertEquals(1, shapeCount(2));

        run.undo();
        assertEquals("Undo removes every applied spec on the target", 0, shapeCount(2));
        assertFalse(run.isExecuted());
    }

    @Test
    public void sameScriptRunsOnMultipleTargetSlides_viaRetargeting() {
        // The script is synthesized for slide 1 but applied to slides 2 AND 3.
        // RetargetToSlide rewrites slideNumber on every spec.
        orchestrator.createSlide(1, "Source",  "slideLayout7");
        orchestrator.createSlide(2, "Target1", "slideLayout7");
        orchestrator.createSlide(3, "Target2", "slideLayout7");
        addRect(1, 1_000_000, 1_000_000, 2_000_000, 1_000_000);

        SynthesizeSlideScriptCommand synth = new SynthesizeSlideScriptCommand(1, orchestrator);
        synth.execute();
        String json = synth.getScriptJson();

        new RunSlideScriptCommand(2, json, orchestrator, null).execute();
        new RunSlideScriptCommand(3, json, orchestrator, null).execute();

        assertEquals("Target1 got the shape", 1, shapeCount(2));
        assertEquals("Target2 got the shape", 1, shapeCount(3));
        assertEquals("Source unchanged",      1, shapeCount(1));
    }

    @Test
    public void editedScript_appliesOverridesToTarget() {
        orchestrator.createSlide(1, "Source", "slideLayout7");
        orchestrator.createSlide(2, "Target", "slideLayout7");
        addRect(1, 1_000_000, 1_000_000, 2_000_000, 1_000_000);

        SynthesizeSlideScriptCommand synth = new SynthesizeSlideScriptCommand(1, orchestrator);
        synth.execute();
        // Hand-edit the JSON: swap the rectangle's width from 2_000_000 to 5_000_000.
        // The agent's real workflow does this in its own reasoning; here we do
        // the text substitution by hand to keep the test self-contained.
        String edited = synth.getScriptJson().replace("2000000", "5000000");

        new RunSlideScriptCommand(2, edited, orchestrator, null).execute();

        // Target's rect must have the EDITED width, not the source's.
        var parsed = orchestrator.getContext().get().getDocument().getParsedSlideData(2,
            (dom, n) -> new com.excudo.xml.parsers.SlideXMLParser().parseSlide(dom, n));
        long w = parsed.getShapeRegistry().getAllShapes().stream()
            .filter(s -> s.getType() == SlideShape.ShapeType.RECTANGLE)
            .findFirst().orElseThrow().getGeometry().getWidth();
        assertEquals("Target shape reflects the edited width, not source's",
            5_000_000L, w);
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyScriptJsonRejected() {
        new RunSlideScriptCommand(1, "   ", orchestrator, null);
    }

    @Test
    public void zeroSpecArray_isNoOpSuccess() {
        orchestrator.createSlide(1, "Empty Script", "slideLayout7");
        RunSlideScriptCommand cmd = new RunSlideScriptCommand(
            1, "[]", orchestrator, null);
        cmd.execute();
        assertTrue(cmd.isExecuted());
        assertEquals(0, cmd.getAppliedSpecCount());
    }

    // ========== Helpers ==========

    private int addRect(int slide, long x, long y, long w, long h) {
        ExecutionResult<Integer> r = orchestrator.addShape(slide,
            SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(x, y, w, h),
            "", "R", ShapeStyle.defaultStyle());
        assertTrue(r.isSuccess());
        return r.getData().orElseThrow();
    }

    private int shapeCount(int slide) {
        var doc = orchestrator.getContext().get().getDocument();
        var parsed = doc.getParsedSlideData(slide,
            (dom, n) -> new com.excudo.xml.parsers.SlideXMLParser().parseSlide(dom, n));
        return parsed.getShapeRegistry().getAllShapes().size();
    }
}
