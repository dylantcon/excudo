package com.excudo.core.synthesis;

import com.excudo.core.model.AnimationBinding;
import com.excudo.core.model.AnimationType;
import com.excudo.core.model.PPTXDocument;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.ShapeStyle;
import com.excudo.core.model.SlideShape;
import com.excudo.core.orchestration.PPTXOrchestratorImpl;
import com.excudo.core.orchestration.PresentationScaffolder;
import org.junit.Test;

import java.io.File;
import java.util.List;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/**
 * Pins the exact warning messages the synthesizer emits for every
 * "deferred / unrepresentable" case. CLAUDE.md's "never default
 * silently" rule applies to the synthesizer too: every spec it can't
 * emit must surface through {@link ScriptSynthesizer.Result#warnings}.
 *
 * <p>If a warning string changes here, the GUI's
 * {@code SlideSpecController} (which presents warnings to the user)
 * gets out of sync with the synthesizer. Pinning the substrings binds
 * the contract.
 */
public class WarningsChannelTest {

    @Test
    public void picture_withoutBlipRef_emitsSkipWarning() {
        // Build a synthetic snapshot with a PICTURE type but no BlipRef
        // -- the case a linked-not-embedded picture hits.
        ShapeSnapshot synthetic = new ShapeSnapshot(
            10, "LinkedPic", SlideShape.ShapeType.PICTURE,
            new ShapeGeometry(0, 0, 1000, 1000), null, null, false, null, null, null);
        SlideStateDiff diff = oneAddedDiff(synthetic);
        ScriptSynthesizer.Result result = ScriptSynthesizer.synthesize(diff, 1);

        boolean warned = result.warnings().stream()
            .anyMatch(w -> w.contains("Picture SPID 10")
                && w.toLowerCase().contains("blipref"));
        assertTrue("Picture-without-BlipRef must warn naming SPID. warnings="
            + result.warnings(), warned);
    }

    @Test
    public void animationRemoval_withoutCTnId_emitsSkipWarning() {
        // Synthesizer's diffAnimations emits a warning when a
        // baseline-present animation has no timingNodeId and can't be
        // removed by id. Trigger via a baseline state with an animation
        // and a current state without it.
        PPTXOrchestratorImpl orch = newScaffolded();
        orch.createSlide(1, "S", "slideLayout7");
        AnimationBinding b = AnimationBinding.builder()
            .target(99).type(AnimationType.APPEAR).build();  // no timing node id
        SlideState baseline = new SlideState(1, java.util.Map.of(),
            List.of(b), null, builder(orch).baseline(1).baseline());
        SlideState current = new SlideState(1, java.util.Map.of(),
            List.of(), null, builder(orch).baseline(1).baseline());
        SlideStateDiff diff = SlideStateDiffer.diff(baseline, current);

        ScriptSynthesizer.Result result = ScriptSynthesizer.synthesize(diff, 1);
        boolean warned = result.warnings().stream()
            .anyMatch(w -> w.contains("Animation removal skipped")
                && w.contains("target SPID 99"));
        assertTrue("Removal-without-cTn must warn naming target SPID. warnings="
            + result.warnings(), warned);
    }

    @Test
    public void styleBecameNull_emitsSkipWarning() {
        // Build base vs. current where the same SPID has a style in
        // baseline and null in current. The differ emits STYLE field
        // changed; the synthesizer emits a warning instead of a
        // SetShapeStyleSpec with null.
        ShapeSnapshot baseShape = new ShapeSnapshot(5, "S",
            SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(0, 0, 1000, 1000),
            ShapeStyle.defaultStyle(), null, false, null, null, null);
        ShapeSnapshot currShape = new ShapeSnapshot(5, "S",
            SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(0, 0, 1000, 1000),
            null, null, false, null, null, null);

        SlideStateDiff diff = new SlideStateDiff(
            List.of(), List.of(),
            List.of(new SlideStateDiff.ShapeModified(baseShape, currShape,
                java.util.EnumSet.of(SlideStateDiff.ShapeField.STYLE))),
            List.of(), List.of(), List.of(), null, List.of());
        ScriptSynthesizer.Result result = ScriptSynthesizer.synthesize(diff, 1);

        boolean warned = result.warnings().stream()
            .anyMatch(w -> w.contains("SPID 5") && w.contains("style became null"));
        assertTrue("Style-became-null must warn. warnings=" + result.warnings(), warned);
    }

    @Test
    public void textBodyBecameNull_emitsSkipWarning() {
        ShapeSnapshot baseShape = new ShapeSnapshot(5, "S",
            SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(0, 0, 1000, 1000),
            null,
            com.excudo.core.model.TextBody.builder().build(),
            false, null, null, null);
        ShapeSnapshot currShape = new ShapeSnapshot(5, "S",
            SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(0, 0, 1000, 1000),
            null, null, false, null, null, null);

        SlideStateDiff diff = new SlideStateDiff(
            List.of(), List.of(),
            List.of(new SlideStateDiff.ShapeModified(baseShape, currShape,
                java.util.EnumSet.of(SlideStateDiff.ShapeField.TEXT))),
            List.of(), List.of(), List.of(), null, List.of());
        ScriptSynthesizer.Result result = ScriptSynthesizer.synthesize(diff, 1);

        boolean warned = result.warnings().stream()
            .anyMatch(w -> w.contains("SPID 5") && w.contains("textBody became null"));
        assertTrue("TextBody-became-null must warn. warnings=" + result.warnings(), warned);
    }

    @Test
    public void crossGroupMove_emitsDriftWarning() {
        // Shape moved between TWO groups: detach + add. Synthesizer warns
        // because coordinate-system scales may differ, drifting position.
        ShapeSnapshot baseShape = new ShapeSnapshot(5, "S",
            SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(0, 0, 1000, 1000),
            null, null, false, /*parent=*/100, null, null);
        ShapeSnapshot currShape = new ShapeSnapshot(5, "S",
            SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(0, 0, 1000, 1000),
            null, null, false, /*parent=*/200, null, null);

        SlideStateDiff diff = new SlideStateDiff(
            List.of(), List.of(),
            List.of(new SlideStateDiff.ShapeModified(baseShape, currShape,
                java.util.EnumSet.of(SlideStateDiff.ShapeField.PARENT_GROUP))),
            List.of(), List.of(), List.of(), null, List.of());
        ScriptSynthesizer.Result result = ScriptSynthesizer.synthesize(diff, 1);

        boolean warned = result.warnings().stream()
            .anyMatch(w -> w.contains("SPID 5") && w.contains("moved between groups")
                && w.contains("drift"));
        assertTrue("Cross-group move must warn about coordinate-system drift. "
            + "warnings=" + result.warnings(), warned);
    }

    @Test
    public void compoundCodeBoxWithoutChildren_emitsFallbackWarning() {
        // A GROUP carrying the code_box_v1 marker but missing the "Code"
        // child snapshot (e.g. parser miss) must warn-and-fallback
        // rather than silently dropping.
        ShapeSnapshot orphanGroup = new ShapeSnapshot(
            42, "excudo:code_box_v1:java", SlideShape.ShapeType.GROUP,
            new ShapeGeometry(0, 0, 1000, 1000), null, null, false, null, null, null);
        SlideStateDiff diff = oneAddedDiff(orphanGroup);
        ScriptSynthesizer.Result result = ScriptSynthesizer.synthesize(diff, 1);

        boolean warned = result.warnings().stream()
            .anyMatch(w -> w.contains("Code box group SPID 42")
                && w.contains("falling back"));
        assertTrue("Compound code box without children must warn before fallback. "
            + "warnings=" + result.warnings(), warned);
    }

    // ===================================================================
    // Sanity: when nothing is unrepresentable, warnings list is empty.
    // ===================================================================

    @Test
    public void cleanDiff_yieldsEmptyWarningsList() {
        PPTXOrchestratorImpl orch = newScaffolded();
        orch.createSlide(1, "S", "slideLayout7");
        orch.addShape(1, SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(0, 0, 1_000_000, 1_000_000), "",
            "R1", ShapeStyle.defaultStyle());
        SlideStateBuilder b = new SlideStateBuilder(orch);
        SlideStateDiff diff = SlideStateDiffer.diff(b.baseline(1), b.current(1));
        ScriptSynthesizer.Result result = ScriptSynthesizer.synthesize(diff, 1);
        assertTrue("Clean diff must produce no warnings: " + result.warnings(),
            result.warnings().isEmpty());
    }

    // ===================================================================
    // Helpers
    // ===================================================================

    private static SlideStateDiff oneAddedDiff(ShapeSnapshot shape) {
        return new SlideStateDiff(
            List.of(new SlideStateDiff.ShapeAdded(shape)),
            List.of(), List.of(),
            List.of(), List.of(), List.of(),
            null, List.of());
    }

    private static SlideStateBuilder builder(PPTXOrchestratorImpl orch) {
        return new SlideStateBuilder(orch);
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
