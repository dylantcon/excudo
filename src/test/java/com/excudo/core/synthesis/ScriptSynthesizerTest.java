package com.excudo.core.synthesis;

import com.excudo.core.introspection.LayoutBaseline;
import com.excudo.core.introspection.TransitionDescriptor;
import com.excudo.core.model.AnimationBinding;
import com.excudo.core.model.AnimationType;
import com.excudo.core.model.LayoutInfo;
import com.excudo.core.model.ShapeFill;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.ShapeStyle;
import com.excudo.core.model.SlideShape;
import com.excudo.core.model.TextBody;
import com.excudo.core.model.TransitionType;
import com.excudo.core.synthesis.spec.CommandSpec;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * State assertions for {@link ScriptSynthesizer}. Each test crafts a
 * specific diff shape and asserts the emitted script contains exactly
 * the specs the plan prescribes -- no more, no less. Dependency edges
 * are asserted explicitly so "minimum DAG" stays enforced.
 */
public class ScriptSynthesizerTest {

    private static final LayoutBaseline DUMMY_BASELINE = new LayoutBaseline(
        new LayoutInfo("slideLayout1", "Title Slide", "slideLayouts/slideLayout1.xml",
            true, false, true, "Title slide"),
        Collections.emptyMap(), Collections.emptyMap(), null);

    // ========== Empty diff ==========

    @Test
    public void emptyDiff_producesEmptyScript() {
        SlideStateDiff diff = SlideStateDiffer.diff(state(Map.of()), state(Map.of()));
        ScriptSynthesizer.Result r = ScriptSynthesizer.synthesize(diff, 1);
        assertTrue("Empty diff must produce an empty script", r.script().isEmpty());
        assertTrue("No warnings for empty diff", r.warnings().isEmpty());
    }

    // ========== Shape additions ==========

    @Test
    public void addedShape_emitsSingleAddShapeSpec() {
        ShapeSnapshot added = rect(5, 0, 0, 1000, 500);
        SlideStateDiff diff = SlideStateDiffer.diff(state(Map.of()), state(Map.of(5, added)));
        ScriptSynthesizer.Result r = ScriptSynthesizer.synthesize(diff, 1);
        assertEquals("Exactly one AddShapeSpec", 1, r.script().size());
        CommandSpec only = r.script().nodes().iterator().next();
        assertTrue(only instanceof CommandSpec.AddShapeSpec);
        CommandSpec.AddShapeSpec add = (CommandSpec.AddShapeSpec) only;
        assertEquals(SlideShape.ShapeType.RECTANGLE, add.shapeType());
        assertEquals("No warnings for simple add", 0, r.warnings().size());
    }

    @Test
    public void addedShapeWithRichText_alsoEmitsDependentSetTextSpec() {
        // Bullet-level text can't ride along on AddShapeSpec's plain
        // text field; synthesizer emits a follow-up SetTextSpec that
        // DEPENDS ON the AddShapeSpec.
        TextBody rich = TextBody.builder().addParagraph(
            com.excudo.core.model.TextParagraph.builder()
                .level(1)
                .addRun(com.excudo.core.model.TextRun.builder("first bullet").build())
                .build())
            .build();
        ShapeSnapshot added = new ShapeSnapshot(5, "Bullets", SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(0, 0, 1000, 500), null, rich, false, null);
        SlideStateDiff diff = SlideStateDiffer.diff(state(Map.of()), state(Map.of(5, added)));
        ScriptSynthesizer.Result r = ScriptSynthesizer.synthesize(diff, 1);
        assertEquals("Two specs: AddShape + SetText", 2, r.script().size());

        CommandSpec.AddShapeSpec addSpec = findAddShape(r.script());
        CommandSpec.SetTextSpec setText = findSetText(r.script());
        assertNotNull(addSpec);
        assertNotNull(setText);
        assertEquals("SetText must depend on AddShape",
            Set.of((CommandSpec) addSpec), r.script().dependenciesOf(setText));
    }

    // ========== Shape modifications ==========

    @Test
    public void positionChange_emitsSingleMoveSpec() {
        ShapeSnapshot before = rect(5, 0, 0, 1000, 500);
        ShapeSnapshot after  = rect(5, 100, 50, 1000, 500);
        SlideStateDiff diff = SlideStateDiffer.diff(
            state(Map.of(5, before)), state(Map.of(5, after)));
        ScriptSynthesizer.Result r = ScriptSynthesizer.synthesize(diff, 1);
        assertEquals(1, r.script().size());
        CommandSpec only = r.script().nodes().iterator().next();
        assertTrue("Expected MoveSpec, got " + only.getClass().getSimpleName(),
            only instanceof CommandSpec.MoveSpec);
        assertEquals(100L, ((CommandSpec.MoveSpec) only).newX());
        assertEquals(50L,  ((CommandSpec.MoveSpec) only).newY());
    }

    @Test
    public void sizeAndPositionChangeTogether_emitsTwoParallelSpecs() {
        ShapeSnapshot before = rect(5, 0, 0, 1000, 500);
        ShapeSnapshot after  = rect(5, 100, 50, 2000, 1000);
        SlideStateDiff diff = SlideStateDiffer.diff(
            state(Map.of(5, before)), state(Map.of(5, after)));
        ScriptSynthesizer.Result r = ScriptSynthesizer.synthesize(diff, 1);
        assertEquals("Two specs emitted", 2, r.script().size());
        CommandSpec.MoveSpec move = (CommandSpec.MoveSpec) r.script().nodes().stream()
            .filter(n -> n instanceof CommandSpec.MoveSpec).findFirst().orElseThrow();
        CommandSpec.ResizeSpec resize = (CommandSpec.ResizeSpec) r.script().nodes().stream()
            .filter(n -> n instanceof CommandSpec.ResizeSpec).findFirst().orElseThrow();
        assertTrue("Move and resize must be parallel (no edges between them)",
            r.script().dependenciesOf(move).isEmpty()
                && r.script().dependenciesOf(resize).isEmpty());
    }

    @Test
    public void styleChange_emitsSetShapeStyleSpec() {
        ShapeSnapshot before = rect(5, 0, 0, 1000, 500);
        ShapeStyle newStyle = ShapeStyle.withFill(ShapeFill.scheme("accent3"));
        // Keep the name identical so only STYLE diffs, not NAME.
        ShapeSnapshot after = new ShapeSnapshot(5, before.name(),
            SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(0, 0, 1000, 500), newStyle, null, false, null);
        SlideStateDiff diff = SlideStateDiffer.diff(
            state(Map.of(5, before)), state(Map.of(5, after)));
        ScriptSynthesizer.Result r = ScriptSynthesizer.synthesize(diff, 1);
        assertEquals(1, r.script().size());
        CommandSpec only = r.script().nodes().iterator().next();
        assertTrue(only instanceof CommandSpec.SetShapeStyleSpec);
        assertEquals(newStyle, ((CommandSpec.SetShapeStyleSpec) only).style());
    }

    // ========== Transition ==========

    @Test
    public void transitionBecomingSet_emitsSetTransitionSpec() {
        TransitionDescriptor after = new TransitionDescriptor(
            TransitionType.FADE, "med", null, null,
            TransitionDescriptor.Source.SLIDE);
        SlideStateDiff diff = SlideStateDiffer.diff(
            state(Map.of(), null), state(Map.of(), after));
        ScriptSynthesizer.Result r = ScriptSynthesizer.synthesize(diff, 1);
        assertEquals(1, r.script().size());
        CommandSpec only = r.script().nodes().iterator().next();
        assertTrue(only instanceof CommandSpec.SetTransitionSpec);
        assertEquals(TransitionType.FADE, ((CommandSpec.SetTransitionSpec) only).transitionType());
    }

    @Test
    public void transitionBecomingUnset_emitsClearTransitionSpec() {
        TransitionDescriptor before = new TransitionDescriptor(
            TransitionType.FADE, "med", null, null,
            TransitionDescriptor.Source.SLIDE);
        SlideStateDiff diff = SlideStateDiffer.diff(
            state(Map.of(), before), state(Map.of(), null));
        ScriptSynthesizer.Result r = ScriptSynthesizer.synthesize(diff, 1);
        assertEquals(1, r.script().size());
        CommandSpec only = r.script().nodes().iterator().next();
        assertTrue(only instanceof CommandSpec.ClearTransitionSpec);
    }

    // ========== Animations ==========

    @Test
    public void animationAddedOnExistingShape_emitsAddAnimationSpecWithNoDependency() {
        ShapeSnapshot shape = rect(5, 0, 0, 1000, 500);
        AnimationBinding ab = AnimationBinding.builder()
            .target(5).type(AnimationType.FADE).entrance().clickTrigger(1).build();
        SlideStateDiff diff = SlideStateDiffer.diff(
            state(Map.of(5, shape), List.of(), null),
            state(Map.of(5, shape), List.of(ab), null));
        ScriptSynthesizer.Result r = ScriptSynthesizer.synthesize(diff, 1);
        assertEquals(1, r.script().size());
        CommandSpec addAnim = r.script().nodes().iterator().next();
        assertTrue(addAnim instanceof CommandSpec.AddAnimationSpec);
        assertTrue("No dependency edge (shape existed in baseline)",
            r.script().dependenciesOf(addAnim).isEmpty());
    }

    @Test
    public void animationAddedOnNewShape_edgeDependsOnAddShape() {
        ShapeSnapshot newShape = rect(5, 0, 0, 1000, 500);
        AnimationBinding ab = AnimationBinding.builder()
            .target(5).type(AnimationType.FADE).entrance().clickTrigger(1).build();
        SlideStateDiff diff = SlideStateDiffer.diff(
            state(Map.of(), List.of(), null),
            state(Map.of(5, newShape), List.of(ab), null));
        ScriptSynthesizer.Result r = ScriptSynthesizer.synthesize(diff, 1);
        assertEquals("AddShape + AddAnimation", 2, r.script().size());

        CommandSpec.AddShapeSpec addSpec = findAddShape(r.script());
        CommandSpec animSpec = r.script().nodes().stream()
            .filter(n -> n instanceof CommandSpec.AddAnimationSpec).findFirst().orElseThrow();
        assertEquals("AddAnimation must depend on AddShape",
            Set.of((CommandSpec) addSpec), r.script().dependenciesOf(animSpec));
    }

    // ========== Group delta ==========

    @Test
    public void newGroup_emitsCreateGroupSpecWithEdgesToChildAdds() {
        ShapeSnapshot group = new ShapeSnapshot(10, "G", SlideShape.ShapeType.GROUP,
            new ShapeGeometry(0, 0, 2000, 1000), null, null, false, null);
        ShapeSnapshot c1 = new ShapeSnapshot(11, "A", SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(0, 0, 500, 500), null, null, false, 10);
        ShapeSnapshot c2 = new ShapeSnapshot(12, "B", SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(500, 0, 500, 500), null, null, false, 10);
        SlideStateDiff diff = SlideStateDiffer.diff(
            state(Map.of()),
            state(Map.of(10, group, 11, c1, 12, c2)));
        ScriptSynthesizer.Result r = ScriptSynthesizer.synthesize(diff, 1);

        // 2 AddShape (the children only) + 1 CreateGroup. The group
        // container is NOT emitted as an AddShapeSpec -- GROUP has no OOXML
        // preset, so it's materialized solely by CreateGroupSpec, which
        // depends on each child's AddShape.
        long adds = r.script().nodes().stream()
            .filter(n -> n instanceof CommandSpec.AddShapeSpec).count();
        assertEquals("Two AddShapeSpecs (the children only)", 2L, adds);

        // The group container must never become an AddShapeSpec.
        boolean groupAddLeaked = r.script().nodes().stream()
            .anyMatch(n -> n instanceof CommandSpec.AddShapeSpec a
                && a.shapeType() == SlideShape.ShapeType.GROUP);
        assertFalse("Group container must not leak an AddShapeSpec(GROUP)", groupAddLeaked);

        CommandSpec createGroup = r.script().nodes().stream()
            .filter(n -> n instanceof CommandSpec.CreateGroupSpec).findFirst().orElseThrow();
        // CreateGroupSpec depends on the child adds (SPID 11 and 12).
        CommandSpec addSpec11 = r.script().nodes().stream()
            .filter(n -> n instanceof CommandSpec.AddShapeSpec
                && ((CommandSpec.AddShapeSpec) n).name().equals("A"))
            .findFirst().orElseThrow();
        CommandSpec addSpec12 = r.script().nodes().stream()
            .filter(n -> n instanceof CommandSpec.AddShapeSpec
                && ((CommandSpec.AddShapeSpec) n).name().equals("B"))
            .findFirst().orElseThrow();
        Set<CommandSpec> deps = r.script().dependenciesOf(createGroup);
        assertTrue("CreateGroup must depend on child A's add", deps.contains(addSpec11));
        assertTrue("CreateGroup must depend on child B's add", deps.contains(addSpec12));
    }

    // ========== Warnings for deferred deltas ==========

    @Test
    public void animationRemovalWithCTnId_emitsRemoveAnimationSpec() {
        // Post-4.7.2: bindings carry timingNodeId, so the synthesizer
        // can emit a scoped RemoveAnimationSpec targeting that id.
        ShapeSnapshot shape = rect(5, 0, 0, 1000, 500);
        AnimationBinding ab = AnimationBinding.builder()
            .target(5).type(AnimationType.FADE).entrance().clickTrigger(1)
            .timingNodeId(42).build();
        SlideStateDiff diff = SlideStateDiffer.diff(
            state(Map.of(5, shape), List.of(ab), null),
            state(Map.of(5, shape), List.of(), null));
        ScriptSynthesizer.Result r = ScriptSynthesizer.synthesize(diff, 1);
        assertEquals("One RemoveAnimationSpec emitted", 1, r.script().size());
        CommandSpec only = r.script().nodes().iterator().next();
        assertTrue(only instanceof CommandSpec.RemoveAnimationSpec);
        assertEquals("Spec must carry the binding's cTn id",
            42, ((CommandSpec.RemoveAnimationSpec) only).timingNodeId());
        assertTrue("No warning when we have everything we need", r.warnings().isEmpty());
    }

    @Test
    public void animationRemovalWithoutCTnId_warnsAndSkips() {
        // Bindings constructed manually (or parsed from malformed XML)
        // may lack a timingNodeId. Synthesizer must warn + skip rather
        // than emit a spec with a bogus id.
        ShapeSnapshot shape = rect(5, 0, 0, 1000, 500);
        AnimationBinding ab = AnimationBinding.builder()
            .target(5).type(AnimationType.FADE).entrance().clickTrigger(1).build();
        // No timingNodeId set -> default -1.
        SlideStateDiff diff = SlideStateDiffer.diff(
            state(Map.of(5, shape), List.of(ab), null),
            state(Map.of(5, shape), List.of(), null));
        ScriptSynthesizer.Result r = ScriptSynthesizer.synthesize(diff, 1);
        assertEquals("No spec emitted without a cTn id", 0, r.script().size());
        assertTrue("Warning must flag the missing id",
            r.warnings().stream().anyMatch(w -> w.toLowerCase().contains("timingnodeid")));
    }

    @Test
    public void nameChange_emitsRenameShapeSpec() {
        // Post-4.7.4: NAME delta emits a RenameShapeSpec, no warning.
        ShapeSnapshot before = new ShapeSnapshot(5, "Old", SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(0, 0, 1000, 500), null, null, false, null);
        ShapeSnapshot after = new ShapeSnapshot(5, "New", SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(0, 0, 1000, 500), null, null, false, null);
        SlideStateDiff diff = SlideStateDiffer.diff(
            state(Map.of(5, before)), state(Map.of(5, after)));
        ScriptSynthesizer.Result r = ScriptSynthesizer.synthesize(diff, 1);
        assertEquals("Exactly one RenameShapeSpec", 1, r.script().size());
        CommandSpec only = r.script().nodes().iterator().next();
        assertTrue(only instanceof CommandSpec.RenameShapeSpec);
        assertEquals("New", ((CommandSpec.RenameShapeSpec) only).newName());
        assertTrue("No warning emitted for NAME change now that it's fully expressible",
            r.warnings().isEmpty());
    }

    // ========== Helpers ==========

    private static SlideState state(Map<Integer, ShapeSnapshot> shapes) {
        return state(shapes, List.of(), null);
    }

    private static SlideState state(Map<Integer, ShapeSnapshot> shapes,
            TransitionDescriptor transition) {
        return state(shapes, List.of(), transition);
    }

    private static SlideState state(Map<Integer, ShapeSnapshot> shapes,
            List<AnimationBinding> anims, TransitionDescriptor transition) {
        return new SlideState(1, shapes, anims, transition, DUMMY_BASELINE);
    }

    private static ShapeSnapshot rect(int spid, long x, long y, long w, long h) {
        return new ShapeSnapshot(spid, "Rect" + spid, SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(x, y, w, h), null, null, false, null);
    }

    private static CommandSpec.AddShapeSpec findAddShape(CommandScript script) {
        for (CommandSpec s : script.nodes()) {
            if (s instanceof CommandSpec.AddShapeSpec a) return a;
        }
        return null;
    }

    private static CommandSpec.SetTextSpec findSetText(CommandScript script) {
        for (CommandSpec s : script.nodes()) {
            if (s instanceof CommandSpec.SetTextSpec t) return t;
        }
        return null;
    }
}
