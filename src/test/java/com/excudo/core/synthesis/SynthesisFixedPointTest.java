package com.excudo.core.synthesis;

import com.excudo.core.commands.CommandInvoker;
import com.excudo.core.commands.mutating.slide.RunSlideScriptCommand;
import com.excudo.core.commands.mutating.slide.SetTransitionCommand;
import com.excudo.core.model.AnimationBinding;
import com.excudo.core.model.AnimationType;
import com.excudo.core.model.PPTXDocument;
import com.excudo.core.model.ShapeFill;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.ShapeStyle;
import com.excudo.core.model.SlideShape;
import com.excudo.core.model.TransitionType;
import com.excudo.core.orchestration.PPTXOrchestratorImpl;
import com.excudo.core.orchestration.PresentationScaffolder;
import com.excudo.core.synthesis.spec.CommandSpec;
import com.excudo.core.synthesis.spec.CommandSpecJson;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * The convergence proof for the synthesis stack. SlideSpec's core
 * promise is that {@code synth(diff(baseline, current))} is the
 * <em>minimum</em> script reproducing a slide's divergence from its
 * layout. That promise has a precise fixed-point formulation:
 *
 * <ol>
 *   <li><b>Zero residue:</b> a freshly created slide has diverged from
 *       its layout only by its title text. A fresh blank-layout slide
 *       synthesizes to an EMPTY script; a fresh placeholder-layout
 *       slide synthesizes to exactly one SetTextSpec (the title).</li>
 *   <li><b>Resynthesis equivalence:</b> after applying synth(source) to
 *       a same-layout target, synth(target) must produce a script with
 *       the same spec-type multiset as synth(source) -- nothing lost,
 *       nothing duplicated, no junk introduced by the apply itself.</li>
 *   <li><b>Generational stability:</b> copy-of-copy-of-copy must not
 *       drift. Slide 3 (generation 3) must be structurally identical
 *       to slide 1 (generation 1).</li>
 * </ol>
 *
 * <p>Historical context: before 2026-06-06 a fresh slideLayout2 slide
 * synthesized SEVEN junk specs including a phantom RemoveShapeSpec
 * (PlaceholderProjector double-projected the content placeholder and
 * used names that didn't match SlideDocumentBuilder's), so every
 * GUI apply dragged placeholder-mutating junk onto the target. These
 * tests pin the fixed point so that class of drift can't return.
 */
public class SynthesisFixedPointTest {

    // ===================================================================
    // 1. Zero residue on fresh slides
    // ===================================================================

    @Test
    public void freshBlankLayoutSlide_synthesizesToEmptyScript() {
        PPTXOrchestratorImpl orch = newScaffolded();
        orch.createSlide(1, "Fresh", "slideLayout7"); // Blank: no placeholders

        ScriptSynthesizer.Result r = synth(orch, 1);
        assertTrue("Fresh blank slide must synthesize ZERO specs, got: "
            + describe(r), r.script().isEmpty());
        assertTrue("No warnings for a fresh slide: " + r.warnings(),
            r.warnings().isEmpty());
    }

    @Test
    public void freshPlaceholderSlide_synthesizesOnlyTitleText() {
        PPTXOrchestratorImpl orch = newScaffolded();
        orch.createSlide(1, "Fresh", "slideLayout2"); // Title + Content

        ScriptSynthesizer.Result r = synth(orch, 1);
        List<CommandSpec> specs = r.script().topologicalOrder();

        assertTrue("No warnings for a fresh slide: " + r.warnings(),
            r.warnings().isEmpty());
        assertEquals("Fresh placeholder slide must synthesize EXACTLY one spec "
            + "(the title text). Anything else is projector/equality residue. Got: "
            + describe(r), 1, specs.size());
        assertTrue("The single spec must be a SetTextSpec: " + specs.get(0),
            specs.get(0) instanceof CommandSpec.SetTextSpec);
        CommandSpec.SetTextSpec title = (CommandSpec.SetTextSpec) specs.get(0);
        assertEquals("Title placeholder is canonically SPID 2", 2, title.spid());
        assertTrue("Title spec must carry the actual title text",
            flatten(title.textBody()).contains("Fresh"));
    }

    // ===================================================================
    // 2. Resynthesis equivalence (no residue through apply)
    // ===================================================================

    @Test
    public void resynthesisAfterApply_yieldsEquivalentScript() {
        PPTXOrchestratorImpl orch = newScaffolded();
        buildRichSlide(orch, 1);
        orch.createSlide(2, "Target", "slideLayout2");

        ScriptSynthesizer.Result r1 = synth(orch, 1);
        assertTrue("Source synth must be warning-free: " + r1.warnings(),
            r1.warnings().isEmpty());
        List<CommandSpec> script1 = r1.script().topologicalOrder();

        applyTo(orch, script1, 2);

        ScriptSynthesizer.Result r2 = synth(orch, 2);
        assertTrue("Target re-synth must be warning-free: " + r2.warnings(),
            r2.warnings().isEmpty());
        List<CommandSpec> script2 = r2.script().topologicalOrder();

        assertEquals("Re-synthesized script must have the same spec-type "
            + "multiset as the original -- residue means the apply mutated "
            + "something it shouldn't, loss means a spec didn't survive.\n"
            + "script1=" + classNames(script1) + "\nscript2=" + classNames(script2),
            classNames(script1), classNames(script2));
    }

    // ===================================================================
    // 3. Generational stability (copy-of-copy doesn't drift)
    // ===================================================================

    @Test
    public void generationalStability_thirdGenerationMatchesFirst() {
        PPTXOrchestratorImpl orch = newScaffolded();
        buildRichSlide(orch, 1);
        orch.createSlide(2, "Gen2", "slideLayout2");
        orch.createSlide(3, "Gen3", "slideLayout2");

        // Gen 1 -> Gen 2.
        applyTo(orch, synth(orch, 1).script().topologicalOrder(), 2);
        // Gen 2 -> Gen 3: synthesized FROM the applied copy, not the original.
        applyTo(orch, synth(orch, 2).script().topologicalOrder(), 3);

        // Generation 3 must match generation 1 structurally.
        SlideStateBuilder b = new SlideStateBuilder(orch);
        SlideState gen1 = b.current(1);
        SlideState gen3 = b.current(3);

        assertEquals("Shape count must be stable across generations",
            gen1.shapes().size(), gen3.shapes().size());
        assertEquals("Shape type multiset must be stable across generations",
            typeMultiset(gen1), typeMultiset(gen3));
        assertEquals("Geometry multiset must be stable across generations",
            geometryMultiset(gen1), geometryMultiset(gen3));
        assertEquals("Animation count must be stable across generations",
            gen1.animations().size(), gen3.animations().size());
        assertEquals("Transition must be stable across generations",
            gen1.transition() != null ? gen1.transition().type() : null,
            gen3.transition() != null ? gen3.transition().type() : null);

        // And the synthesized scripts of gen 1 and gen 3 must match.
        assertEquals("Gen-3 script must have gen-1's spec multiset",
            classNames(synth(orch, 1).script().topologicalOrder()),
            classNames(synth(orch, 3).script().topologicalOrder()));
    }

    // ===================================================================
    // Rich slide construction
    // ===================================================================

    /**
     * Slide exercising every major channel: placeholder title text,
     * styled rectangle with text, ellipse, bound connector, entrance
     * animation, slide transition, and a group of two shapes.
     */
    private static void buildRichSlide(PPTXOrchestratorImpl orch, int slide) {
        var created = orch.createSlide(slide, "Rich Fixed Point", "slideLayout2");
        assertTrue(created.isSuccess());

        var rect = orch.addShape(slide, SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(500_000, 2_000_000, 2_000_000, 1_000_000),
            "Hello", "R1",
            ShapeStyle.withFillAndLine(ShapeFill.scheme("accent2"), null));
        assertTrue(rect.isSuccess());
        int rectSpid = rect.getData().orElseThrow();

        var ellipse = orch.addShape(slide, SlideShape.ShapeType.ELLIPSE,
            new ShapeGeometry(4_000_000, 2_000_000, 1_500_000, 1_500_000),
            "", "E1", ShapeStyle.defaultStyle());
        assertTrue(ellipse.isSuccess());
        int ellipseSpid = ellipse.getData().orElseThrow();

        var conn = orch.addConnector(slide, "straight",
            new ShapeGeometry(2_500_000, 2_500_000, 1_500_000, 0),
            "none", "triangle", "000000", "solid",
            rectSpid, 1, ellipseSpid, 1, null);
        assertTrue(conn.isSuccess());

        var anim = orch.addAnimation(slide, AnimationBinding.builder()
            .target(rectSpid).type(AnimationType.FADE).entrance()
            .durationMs(700).build(), null);
        assertTrue(anim.isSuccess());

        new SetTransitionCommand(slide, TransitionType.FADE, "med", null, orch).execute();

        var g1 = orch.addShape(slide, SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(500_000, 4_000_000, 800_000, 800_000),
            "", "G1", ShapeStyle.defaultStyle());
        var g2 = orch.addShape(slide, SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(1_500_000, 4_000_000, 800_000, 800_000),
            "", "G2", ShapeStyle.defaultStyle());
        var grouped = orch.groupShapes(slide,
            List.of(g1.getData().orElseThrow(), g2.getData().orElseThrow()));
        assertTrue(grouped.isSuccess());
    }

    // ===================================================================
    // Helpers
    // ===================================================================

    private static ScriptSynthesizer.Result synth(PPTXOrchestratorImpl orch, int slide) {
        SlideStateBuilder b = new SlideStateBuilder(orch);
        SlideStateDiff diff = SlideStateDiffer.diff(b.baseline(slide), b.current(slide));
        return ScriptSynthesizer.synthesize(diff, slide);
    }

    private static void applyTo(PPTXOrchestratorImpl orch, List<CommandSpec> specs, int target) {
        String json = CommandSpecJson.toJsonArray(specs);
        RunSlideScriptCommand cmd = new RunSlideScriptCommand(target, json, orch, null);
        new CommandInvoker().executeCommand(cmd);
        assertTrue("Apply to slide " + target + " must succeed", cmd.isExecuted());
        assertTrue("No specs may be skipped on a same-layout apply: "
            + cmd.getRuntimeWarnings(), cmd.getRuntimeWarnings().isEmpty());
    }

    private static List<String> classNames(List<CommandSpec> specs) {
        return specs.stream().map(s -> s.getClass().getSimpleName()).sorted().toList();
    }

    private static List<String> typeMultiset(SlideState s) {
        return s.shapes().values().stream()
            .map(sn -> sn.type().name()).sorted().toList();
    }

    private static List<String> geometryMultiset(SlideState s) {
        return s.shapes().values().stream()
            .map(sn -> sn.geometry() == null ? "null" : sn.geometry().toString())
            .sorted().toList();
    }

    private static String flatten(com.excudo.core.model.TextBody body) {
        StringBuilder sb = new StringBuilder();
        for (var p : body.getParagraphs())
            for (var r : p.getRuns())
                if (r.getText() != null) sb.append(r.getText());
        return sb.toString();
    }

    private static String describe(ScriptSynthesizer.Result r) {
        return r.script().topologicalOrder().stream()
            .map(Object::toString).toList() + " warnings=" + r.warnings();
    }

    private static PPTXOrchestratorImpl newScaffolded() {
        try {
            PPTXDocument doc = PresentationScaffolder.scaffoldDocument("excudo");
            PPTXOrchestratorImpl orch = new PPTXOrchestratorImpl();
            orch.initialize(doc);
            return orch;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
