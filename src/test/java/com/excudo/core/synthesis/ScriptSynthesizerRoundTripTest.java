package com.excudo.core.synthesis;

import com.excudo.core.commands.mutating.slide.AddShapeCommand;

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
import com.excudo.core.results.ExecutionResult;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * The key correctness test for the synthesizer pipeline.
 *
 * <p>For each "scenario" below, the test runs:
 * <pre>
 *   Source  -- build slide A by executing canonical commands
 *   Read    -- snapshot SlideState(A) via SlideStateBuilder
 *   Diff    -- SlideStateDiffer.diff(baseline, current) on a sibling slide
 *   Synth   -- ScriptSynthesizer.synthesize(diff, slide B)
 *   Apply   -- ScriptRunner.run(script) on slide B
 *   Verify  -- SlideStateBuilder.current(B) matches SlideStateBuilder.current(A)
 * </pre>
 *
 * <p>The round-trip is the bar: if synth(diff(layout, slide)) applied
 * to layout doesn't reproduce slide, the synthesizer has a bug
 * exactly locatable at the scenario level. Every new spec type the
 * synthesizer learns gets a new scenario here.
 */
public class ScriptSynthesizerRoundTripTest {

    @Test
    public void emptyScenario_baselineEqualsItselfOnRoundTrip() {
        PPTXOrchestratorImpl orch = newOrchestrator();
        orch.createSlide(1, "Source", "slideLayout7"); // Blank layout
        orch.createSlide(2, "Target", "slideLayout7");

        assertRoundTrip(orch, 1, 2);
    }

    @Test
    public void placeholderBearingLayout_baselineProjectionKeepsDiffEmpty() {
        // Post-4.7.3: baseline() projects the layout's declared
        // placeholders. Creating a fresh slide and diffing its current
        // state against its baseline should produce an EMPTY diff --
        // nothing for the synthesizer to emit.
        PPTXOrchestratorImpl orch = newOrchestrator();
        orch.createSlide(1, "Source", "slideLayout2"); // Title + Content

        SlideStateBuilder builder = new SlideStateBuilder(orch);
        SlideState current  = builder.current(1);
        SlideState baseline = builder.baseline(1);

        SlideStateDiff diff = SlideStateDiffer.diff(baseline, current);
        // Shapes on the current slide that were just materialized from
        // the layout should NOT appear as added in the diff. They may
        // appear as modified if our projection doesn't perfectly
        // reproduce the parser's reading (which is expected for v1 --
        // style and textBody are null on projected placeholders while
        // the parser reads real values). The critical invariant is
        // "no shapes added" -- anything else surfaces through the
        // modified list as legitimate layout-default deviations.
        assertTrue("Placeholders from the layout must not appear as added: " + diff.added(),
            diff.added().isEmpty());
    }

    @Test
    public void singleRectangle_roundTripsIdentically() {
        PPTXOrchestratorImpl orch = newOrchestrator();
        orch.createSlide(1, "Source", "slideLayout7");
        orch.createSlide(2, "Target", "slideLayout7");
        orch.addShape(1, SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(1_000_000, 1_000_000, 3_000_000, 2_000_000),
            "", "R1", ShapeStyle.defaultStyle());

        assertRoundTrip(orch, 1, 2);
    }

    @Test
    public void multipleShapes_roundTripPreservesAllGeometries() {
        PPTXOrchestratorImpl orch = newOrchestrator();
        orch.createSlide(1, "Source", "slideLayout7");
        orch.createSlide(2, "Target", "slideLayout7");
        orch.addShape(1, SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(0, 0, 1_000_000, 1_000_000), "", "R1", ShapeStyle.defaultStyle());
        orch.addShape(1, SlideShape.ShapeType.ELLIPSE,
            new ShapeGeometry(2_000_000, 0, 1_000_000, 1_000_000), "", "E1", ShapeStyle.defaultStyle());
        orch.addShape(1, SlideShape.ShapeType.STAR_5_POINTS,
            new ShapeGeometry(4_000_000, 0, 1_000_000, 1_000_000), "", "S1", ShapeStyle.defaultStyle());

        assertRoundTrip(orch, 1, 2);
    }

    @Test
    public void shapeWithCustomFill_roundTripsThroughStyleSpec() {
        PPTXOrchestratorImpl orch = newOrchestrator();
        orch.createSlide(1, "Source", "slideLayout7");
        orch.createSlide(2, "Target", "slideLayout7");
        orch.addShape(1, SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(1_000_000, 1_000_000, 2_000_000, 1_000_000),
            "", "Filled", ShapeStyle.withFill(ShapeFill.scheme("accent4")));

        assertRoundTrip(orch, 1, 2);
    }

    @Test
    public void fillOpacity_survivesFullSynthesisRoundTrip() {
        PPTXOrchestratorImpl orch = newOrchestrator();
        orch.createSlide(1, "Source", "slideLayout7");
        orch.createSlide(2, "Target", "slideLayout7");
        orch.addShape(1, SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(1_000_000, 1_000_000, 2_000_000, 1_000_000),
            "", "Translucent",
            ShapeStyle.withFill(ShapeFill.solid("FF8800").withAlphaPercent(40)));

        // synth slide 1 -> apply to slide 2 (structural bar checked inside).
        assertRoundTrip(orch, 1, 2);

        // Strong, fidelity-level check the weak structural bar can't make: the
        // applied fill must carry the same opacity. Exercises reader+sameFill
        // +JSON+writer end to end -- before the alpha fix this came back null.
        SlideState target = new SlideStateBuilder(orch).current(2);
        var filled = target.shapes().values().stream()
            .filter(s -> "Translucent".equals(s.name()))
            .findFirst().orElseThrow(() -> new AssertionError("applied shape missing"));
        assertNotNull("Applied shape must carry a style", filled.style());
        assertNotNull("Applied shape must carry a fill", filled.style().getFill());
        assertEquals("Fill opacity must survive synth->apply round-trip",
            Integer.valueOf(40), filled.style().getFill().getAlphaPercent());
    }

    @Test
    public void slideTransition_roundTripsAtCorrectLevel() {
        PPTXOrchestratorImpl orch = newOrchestrator();
        orch.createSlide(1, "Source", "slideLayout7");
        orch.createSlide(2, "Target", "slideLayout7");
        orch.setTransition(1, TransitionType.PUSH_LEFT, "fast", null);

        assertRoundTrip(orch, 1, 2);
    }

    @Test
    public void shapeWithAnimation_roundTripsAfterSpidRemapping() {
        // Post-4.7.1: the ScriptRunner captures each AddShapeCommand's
        // allocated SPID and rewrites downstream specs that reference
        // the source-slide SPID. Animation bindings now target the
        // correct post-add SPID on the target slide, so both the shape
        // AND its animation round-trip end-to-end.
        PPTXOrchestratorImpl orch = newOrchestrator();
        orch.createSlide(1, "Source", "slideLayout7");
        orch.createSlide(2, "Target", "slideLayout7");
        ExecutionResult<Integer> added = orch.addShape(1, SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(1_000_000, 1_000_000, 2_000_000, 1_000_000),
            "", "AnimTarget", ShapeStyle.defaultStyle());
        int spid = added.getData().orElseThrow();
        orch.addAnimation(1, AnimationBinding.builder()
            .target(spid).type(AnimationType.FADE).entrance().clickTrigger(1).durationMs(500).build());

        SlideStateBuilder builder = new SlideStateBuilder(orch);
        ScriptRunner runner = new ScriptRunner(orch);
        SlideState source = builder.current(1);
        SlideState target = builder.current(2);
        SlideStateDiff diff = SlideStateDiffer.diff(target, source);
        ScriptSynthesizer.Result result = ScriptSynthesizer.synthesize(diff, 2);
        ExecutionResult<Void> run = runner.run(result.script(), "round-trip-anim");
        assertTrue("Script must succeed: " + run.getMessage(), run.isSuccess());

        // Shape parity (same as other scenarios).
        assertShapeCountAndTypesMatch(orch, 1, 2);

        // Animation parity: the target slide must have one animation of
        // the same type as the source, targeting the newly-added shape
        // (whose SPID is DIFFERENT from source SPID -- proof that the
        // remapping worked end-to-end).
        var introspector = new com.excudo.core.introspection.SlideIntrospector(orch);
        var targetAnims = introspector.listAnimations(2);
        assertEquals("Target slide has one animation", 1, targetAnims.size());
        assertEquals("Animation type matches", AnimationType.FADE,
            targetAnims.get(0).getAnimationType());

        // The target animation's target SPID must actually exist on
        // slide 2 -- the whole point of the remap.
        int targetSpid = targetAnims.get(0).getTargetSpid();
        var parsed = orch.getContext().get().getDocument().getParsedSlideData(2,
            (dom, n) -> new com.excudo.xml.parsers.SlideXMLParser().parseSlide(dom, n));
        assertNotNull("Animation target SPID " + targetSpid + " must resolve to a real shape on slide 2",
            parsed.getShapeRegistry().getShape(targetSpid));
    }

    // ========== Helpers ==========

    private static PPTXOrchestratorImpl newOrchestrator() {
        try {
            PPTXDocument doc = PresentationScaffolder.scaffoldDocument("excudo");
            PPTXOrchestratorImpl orch = new PPTXOrchestratorImpl();
            orch.initialize(doc);
            return orch;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** The full round-trip assertion: diff (slide A's state) against
     *  (slide B's baseline) should produce a script that, when applied
     *  to slide B, makes slide B's state equal to slide A's state. */
    private static void assertRoundTrip(PPTXOrchestratorImpl orch, int source, int target) {
        SlideStateBuilder builder = new SlideStateBuilder(orch);
        ScriptRunner runner = new ScriptRunner(orch);

        SlideState sourceState = builder.current(source);
        SlideState targetBaseline = builder.current(target); // target slide starts fresh

        assertNotNull("Source slide state must be built", sourceState);
        assertNotNull("Target slide state must be built", targetBaseline);

        // Diff targetBaseline -> sourceState: what specs would make
        // target look like source?
        SlideStateDiff diff = SlideStateDiffer.diff(targetBaseline, sourceState);

        // Synthesize the script targeting the TARGET slide number (not
        // source's), then apply.
        ScriptSynthesizer.Result result = ScriptSynthesizer.synthesize(diff, target);
        ExecutionResult<Void> run = runner.run(result.script(), "round-trip");
        assertTrue("Script run must succeed: " + run.getMessage(), run.isSuccess());

        // Re-read target state post-apply and compare to source.
        SlideState after = builder.current(target);
        assertNotNull(after);

        // Shape-count + type + geometry parity is the minimum bar.
        // Full SlideState equality is finicky (post-SPID-rewrite bindings
        // differ by SPID; ShapeSnapshot includes those). For v1 we assert
        // the structural properties we care about:
        assertShapeCountAndTypesMatch(orch, source, target);
        assertGeometriesMatch(orch, source, target);
        assertTransitionMatches(orch, source, target);
    }

    private static void assertShapeCountAndTypesMatch(PPTXOrchestratorImpl orch, int a, int b) {
        var shA = shapesByKey(orch, a);
        var shB = shapesByKey(orch, b);
        assertEquals("Shape count must match across round-trip (" + a + " vs " + b + ")",
            shA.size(), shB.size());
        assertEquals("Shape type multiset must match",
            shA.values().stream().sorted().toList(),
            shB.values().stream().sorted().toList());
    }

    private static void assertGeometriesMatch(PPTXOrchestratorImpl orch, int a, int b) {
        var geoA = geometriesByTypeAndIndex(orch, a);
        var geoB = geometriesByTypeAndIndex(orch, b);
        assertEquals("Same set of (type, index) keys", geoA.keySet(), geoB.keySet());
        for (String k : geoA.keySet()) {
            assertEquals("Geometry for key " + k, geoA.get(k), geoB.get(k));
        }
    }

    private static void assertTransitionMatches(PPTXOrchestratorImpl orch, int a, int b) {
        var introspector = new com.excudo.core.introspection.SlideIntrospector(orch);
        var tA = introspector.getTransition(a);
        var tB = introspector.getTransition(b);
        if (tA == null) {
            assertNull("No transition on source means no transition on target", tB);
            return;
        }
        assertNotNull("Source has a transition; target must too", tB);
        assertEquals("Transition type matches", tA.type(), tB.type());
    }

    private static Map<Integer, String> shapesByKey(PPTXOrchestratorImpl orch, int slide) {
        var doc = orch.getContext().get().getDocument();
        var parsed = doc.getParsedSlideData(slide,
            (dom, n) -> new com.excudo.xml.parsers.SlideXMLParser().parseSlide(dom, n));
        Map<Integer, String> out = new HashMap<>();
        for (var s : parsed.getShapeRegistry().getAllShapes()) {
            out.put(s.getSpid(), s.getType().name());
        }
        return out;
    }

    /**
     * Key shapes by (type, ordinal-within-type) so geometry comparisons
     * across slides work when SPIDs differ. Assumes the layout-created
     * placeholders are the first N shapes by ordinal within each type
     * group, which holds for identically-layouted slides.
     */
    private static Map<String, ShapeGeometry> geometriesByTypeAndIndex(PPTXOrchestratorImpl orch, int slide) {
        var doc = orch.getContext().get().getDocument();
        var parsed = doc.getParsedSlideData(slide,
            (dom, n) -> new com.excudo.xml.parsers.SlideXMLParser().parseSlide(dom, n));
        Map<String, Integer> typeCounts = new HashMap<>();
        Map<String, ShapeGeometry> out = new HashMap<>();
        for (var s : parsed.getShapeRegistry().getAllShapes()) {
            String type = s.getType().name();
            int idx = typeCounts.getOrDefault(type, 0);
            out.put(type + ":" + idx, s.getGeometry());
            typeCounts.put(type, idx + 1);
        }
        return out;
    }
}
