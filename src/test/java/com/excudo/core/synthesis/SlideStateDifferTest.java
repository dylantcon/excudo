package com.excudo.core.synthesis;

import com.excudo.core.introspection.LayoutBaseline;
import com.excudo.core.introspection.TransitionDescriptor;
import com.excudo.core.model.AnimationBinding;
import com.excudo.core.model.AnimationType;
import com.excudo.core.model.FillType;
import com.excudo.core.model.LayoutInfo;
import com.excudo.core.model.ShapeFill;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.ShapeStyle;
import com.excudo.core.model.SlideShape;
import com.excudo.core.model.TextBody;
import com.excudo.core.model.TextColor;
import com.excudo.core.model.TransitionType;
import org.junit.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Pure-function tests for {@link SlideStateDiffer}. Inputs are crafted
 * directly as value records so the tests exercise the diff rules
 * without any orchestrator or XML-parser dependency.
 *
 * <p>Each test fixates on one axis of change (geometry position vs.
 * size, animation identity, transition inheritance indifference, etc.)
 * so a regression in one rule surfaces as one failing test, not a tangle.
 */
public class SlideStateDifferTest {

    private static final LayoutBaseline DUMMY_BASELINE = new LayoutBaseline(
        new LayoutInfo("slideLayout1", "Title Slide", "slideLayouts/slideLayout1.xml",
            true, false, true, "Title slide"),
        Collections.emptyMap(), Collections.emptyMap(), null);

    // ========== Identity diff ==========

    @Test
    public void identicalStatesProduceEmptyDiff() {
        SlideState s = state(1, Map.of(2, rect(2, 0, 0, 1000, 500)), List.of(), null);
        SlideStateDiff d = SlideStateDiffer.diff(s, s);
        assertTrue("Diff of state against itself must be empty", d.isEmpty());
    }

    // ========== Shape added / removed ==========

    @Test
    public void shapeAddedReportsInAddedList() {
        SlideState before = state(1, Map.of(), List.of(), null);
        SlideState after  = state(1, Map.of(2, rect(2, 0, 0, 1000, 500)), List.of(), null);
        SlideStateDiff d = SlideStateDiffer.diff(before, after);
        assertEquals("Exactly one shape added", 1, d.added().size());
        assertEquals("Added shape's SPID is surfaced", 2, d.added().get(0).shape().spid());
        assertTrue("Nothing removed", d.removed().isEmpty());
        assertTrue("Nothing modified", d.modified().isEmpty());
    }

    @Test
    public void shapeRemovedReportsInRemovedList() {
        SlideState before = state(1, Map.of(2, rect(2, 0, 0, 1000, 500)), List.of(), null);
        SlideState after  = state(1, Map.of(), List.of(), null);
        SlideStateDiff d = SlideStateDiffer.diff(before, after);
        assertEquals("Exactly one shape removed", 1, d.removed().size());
        assertEquals("Removed shape's SPID", 2, d.removed().get(0).shape().spid());
    }

    // ========== Shape modified: one field axis per test ==========

    @Test
    public void positionChangeFlagsOnlyPosition() {
        SlideState before = state(1, Map.of(2, rect(2, 0, 0, 1000, 500)), List.of(), null);
        SlideState after  = state(1, Map.of(2, rect(2, 100, 0, 1000, 500)), List.of(), null);
        SlideStateDiff d = SlideStateDiffer.diff(before, after);
        assertEquals(1, d.modified().size());
        assertEquals("Only GEOMETRY_POSITION changed",
            java.util.Set.of(SlideStateDiff.ShapeField.GEOMETRY_POSITION),
            d.modified().get(0).changedFields());
    }

    @Test
    public void sizeChangeFlagsOnlySize() {
        SlideState before = state(1, Map.of(2, rect(2, 0, 0, 1000, 500)), List.of(), null);
        SlideState after  = state(1, Map.of(2, rect(2, 0, 0, 2000, 500)), List.of(), null);
        SlideStateDiff d = SlideStateDiffer.diff(before, after);
        assertEquals(1, d.modified().size());
        assertEquals(java.util.Set.of(SlideStateDiff.ShapeField.GEOMETRY_SIZE),
            d.modified().get(0).changedFields());
    }

    @Test
    public void positionAndSizeChangeTogetherFlagsBoth() {
        SlideState before = state(1, Map.of(2, rect(2, 0, 0, 1000, 500)), List.of(), null);
        SlideState after  = state(1, Map.of(2, rect(2, 50, 50, 2000, 500)), List.of(), null);
        SlideStateDiff d = SlideStateDiffer.diff(before, after);
        assertEquals(1, d.modified().size());
        assertEquals(java.util.Set.of(
                SlideStateDiff.ShapeField.GEOMETRY_POSITION,
                SlideStateDiff.ShapeField.GEOMETRY_SIZE),
            d.modified().get(0).changedFields());
    }

    @Test
    public void styleChangeIsFlagged() {
        ShapeSnapshot b = rect(2, 0, 0, 1000, 500);
        ShapeSnapshot c = new ShapeSnapshot(
            b.spid(), b.name(), b.type(), b.geometry(),
            ShapeStyle.withFill(ShapeFill.scheme("accent5")),
            b.textBody(), b.isTextBox(), b.parentGroupSpid());
        SlideState before = state(1, Map.of(2, b), List.of(), null);
        SlideState after  = state(1, Map.of(2, c), List.of(), null);
        SlideStateDiff d = SlideStateDiffer.diff(before, after);
        assertEquals(1, d.modified().size());
        assertTrue("STYLE must be among changed fields",
            d.modified().get(0).changedFields().contains(SlideStateDiff.ShapeField.STYLE));
    }

    @Test
    public void identicalStyleIsNotFlaggedEvenAcrossSeparateInstances() {
        ShapeSnapshot b = new ShapeSnapshot(2, "R", SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(0, 0, 1000, 500),
            ShapeStyle.withFill(ShapeFill.solid("FF0000")),
            null, false, null);
        ShapeSnapshot c = new ShapeSnapshot(2, "R", SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(0, 0, 1000, 500),
            ShapeStyle.withFill(ShapeFill.solid("FF0000")),
            null, false, null);
        SlideState before = state(1, Map.of(2, b), List.of(), null);
        SlideState after  = state(1, Map.of(2, c), List.of(), null);
        SlideStateDiff d = SlideStateDiffer.diff(before, after);
        assertTrue("Structural equality must yield empty diff even with "
            + "separately-constructed ShapeStyle instances",
            d.isEmpty());
    }

    @Test
    public void parentGroupChangeIsFlagged() {
        ShapeSnapshot b = new ShapeSnapshot(5, "R", SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(0, 0, 1000, 500), null, null, false, null);
        ShapeSnapshot c = new ShapeSnapshot(5, "R", SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(0, 0, 1000, 500), null, null, false, 9);  // now in group 9
        SlideState before = state(1, Map.of(5, b), List.of(), null);
        SlideState after  = state(1, Map.of(5, c), List.of(), null);
        SlideStateDiff d = SlideStateDiffer.diff(before, after);
        assertEquals(1, d.modified().size());
        assertTrue(d.modified().get(0).changedFields()
            .contains(SlideStateDiff.ShapeField.PARENT_GROUP));
    }

    // ========== Animations ==========

    @Test
    public void animationAddedReportsInAnimAddedList() {
        AnimationBinding ab = AnimationBinding.builder()
            .target(3).type(AnimationType.FADE).entrance().clickTrigger(1).build();
        SlideState before = state(1, Map.of(3, rect(3, 0, 0, 100, 100)), List.of(), null);
        SlideState after  = state(1, Map.of(3, rect(3, 0, 0, 100, 100)), List.of(ab), null);
        SlideStateDiff d = SlideStateDiffer.diff(before, after);
        assertEquals(1, d.animationsAdded().size());
        assertEquals(AnimationType.FADE, d.animationsAdded().get(0).binding().getAnimationType());
    }

    @Test
    public void animationWithSameIdentityAndDifferentDuration_producesTimingChange() {
        // Same identity (target/type/trigger/pRg) + different duration =
        // AnimationTimingChanged delta, which the synthesizer turns
        // into a SetAnimationTimingSpec.
        AnimationBinding a1 = AnimationBinding.builder()
            .target(3).type(AnimationType.FADE).entrance().clickTrigger(1).durationMs(500).build();
        AnimationBinding a2 = AnimationBinding.builder()
            .target(3).type(AnimationType.FADE).entrance().clickTrigger(1).durationMs(1000).build();
        SlideState before = state(1, Map.of(3, rect(3, 0, 0, 100, 100)), List.of(a1), null);
        SlideState after  = state(1, Map.of(3, rect(3, 0, 0, 100, 100)), List.of(a2), null);
        SlideStateDiff d = SlideStateDiffer.diff(before, after);
        assertTrue("Identity match means no add/remove",
            d.animationsAdded().isEmpty() && d.animationsRemoved().isEmpty());
        assertEquals("Exactly one timing-change delta", 1, d.animationsTimingChanged().size());
        assertEquals("500", d.animationsTimingChanged().get(0).before().getDuration());
        assertEquals("1000", d.animationsTimingChanged().get(0).after().getDuration());
    }

    @Test
    public void animationWithSameIdentityAndSameTiming_noDelta() {
        AnimationBinding a1 = AnimationBinding.builder()
            .target(3).type(AnimationType.FADE).entrance().clickTrigger(1).durationMs(500).build();
        AnimationBinding a2 = AnimationBinding.builder()
            .target(3).type(AnimationType.FADE).entrance().clickTrigger(1).durationMs(500).build();
        SlideState before = state(1, Map.of(3, rect(3, 0, 0, 100, 100)), List.of(a1), null);
        SlideState after  = state(1, Map.of(3, rect(3, 0, 0, 100, 100)), List.of(a2), null);
        SlideStateDiff d = SlideStateDiffer.diff(before, after);
        assertTrue("No animation delta of any kind",
            d.animationsAdded().isEmpty() && d.animationsRemoved().isEmpty()
                && d.animationsTimingChanged().isEmpty());
    }

    // ========== Transition ==========

    @Test
    public void transitionBecomingSetProducesTransitionChange() {
        TransitionDescriptor after = new TransitionDescriptor(
            TransitionType.FADE, "med", null, null,
            TransitionDescriptor.Source.SLIDE);
        SlideState before = state(1, Map.of(), List.of(), null);
        SlideState afterState = state(1, Map.of(), List.of(), after);
        SlideStateDiff d = SlideStateDiffer.diff(before, afterState);
        assertNotNull("TransitionChange must be emitted", d.transitionChange());
        assertNull("before side must be null (was unset)", d.transitionChange().before());
        assertEquals(after, d.transitionChange().after());
    }

    @Test
    public void identicalTransitionsFromDifferentSourcesDoNotFlag() {
        // Key invariant: the synthesizer should not emit a SetTransitionSpec
        // when the effective transition is the same, even if one side is
        // inherited from layout and the other from master.
        TransitionDescriptor layoutFade = new TransitionDescriptor(
            TransitionType.FADE, "med", null, null,
            TransitionDescriptor.Source.LAYOUT);
        TransitionDescriptor masterFade = new TransitionDescriptor(
            TransitionType.FADE, "med", null, null,
            TransitionDescriptor.Source.MASTER);
        SlideState a = state(1, Map.of(), List.of(), layoutFade);
        SlideState b = state(1, Map.of(), List.of(), masterFade);
        SlideStateDiff d = SlideStateDiffer.diff(a, b);
        assertNull("Source mismatch must NOT produce a transition change",
            d.transitionChange());
    }

    // ========== Group deltas ==========

    @Test
    public void newGroupWithTwoChildrenEmitsGroupAddedWithBothChildren() {
        ShapeSnapshot g = new ShapeSnapshot(10, "G", SlideShape.ShapeType.GROUP,
            new ShapeGeometry(0, 0, 2000, 1000), null, null, false, null);
        ShapeSnapshot c1 = new ShapeSnapshot(11, "A", SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(0, 0, 500, 500), null, null, false, 10);
        ShapeSnapshot c2 = new ShapeSnapshot(12, "B", SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(500, 0, 500, 500), null, null, false, 10);

        SlideState before = state(1, Map.of(), List.of(), null);
        SlideState after  = state(1, Map.of(10, g, 11, c1, 12, c2), List.of(), null);

        SlideStateDiff d = SlideStateDiffer.diff(before, after);
        assertEquals("Exactly one group delta for SPID 10",
            1, d.groupDeltas().size());
        SlideStateDiff.GroupDelta gd = d.groupDeltas().get(0);
        assertEquals(10, gd.groupSpid());
        assertTrue("groupAdded flag must be set (no baseline membership)", gd.groupAdded());
        assertFalse("groupRemoved flag must not be set", gd.groupRemoved());
        assertEquals("Both children must be listed as added to the group",
            List.of(11, 12), gd.childrenAdded());
        assertTrue("No children removed from a fresh group", gd.childrenRemoved().isEmpty());
    }

    // ========== Determinism ==========

    @Test
    public void diffIsDeterministic() {
        Map<Integer, ShapeSnapshot> before = new LinkedHashMap<>();
        before.put(3, rect(3, 0, 0, 100, 100));
        before.put(5, rect(5, 100, 0, 100, 100));
        Map<Integer, ShapeSnapshot> after = new LinkedHashMap<>();
        after.put(3, rect(3, 50, 0, 100, 100));           // moved
        after.put(5, rect(5, 100, 0, 100, 100));          // unchanged
        after.put(7, rect(7, 0, 100, 100, 100));          // added
        SlideState sb = state(1, before, List.of(), null);
        SlideState sa = state(1, after,  List.of(), null);

        SlideStateDiff d1 = SlideStateDiffer.diff(sb, sa);
        SlideStateDiff d2 = SlideStateDiffer.diff(sb, sa);
        assertEquals("Two diffs over the same inputs must produce identical shape-added lists",
            d1.added().stream().map(x -> x.shape().spid()).toList(),
            d2.added().stream().map(x -> x.shape().spid()).toList());
        assertEquals("Modified list must be sorted by SPID in both runs",
            d1.modified().stream().map(m -> m.current().spid()).toList(),
            d2.modified().stream().map(m -> m.current().spid()).toList());
    }

    // ========== Validation ==========

    @Test(expected = IllegalArgumentException.class)
    public void nullBaselineThrows() {
        SlideStateDiffer.diff(null, state(1, Map.of(), List.of(), null));
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullCurrentThrows() {
        SlideStateDiffer.diff(state(1, Map.of(), List.of(), null), null);
    }

    // ========== Helpers ==========

    private static SlideState state(int slideNumber,
            Map<Integer, ShapeSnapshot> shapes,
            List<AnimationBinding> anims,
            TransitionDescriptor transition) {
        return new SlideState(slideNumber, shapes, anims, transition, DUMMY_BASELINE);
    }

    private static ShapeSnapshot rect(int spid, long x, long y, long w, long h) {
        return new ShapeSnapshot(spid, "Rect" + spid,
            SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(x, y, w, h),
            null, null, false, null);
    }

    // Ensure FillType enum reference stays honest at compile time.
    @SuppressWarnings("unused")
    private static final FillType FILL_TYPE_SANITY = FillType.SOLID;

    // Ensure TextColor stays wired in: a style-change test uses hex colors.
    @SuppressWarnings("unused")
    private static final TextColor COLOR_SANITY = TextColor.hex("000000");

    // TextBody typed use: TextBody import sanity in case the differ path
    // ever needs it for richer text delta tests later.
    @SuppressWarnings("unused")
    private static final TextBody TEXT_BODY_SANITY = TextBody.fromPlainText("", true);
}
