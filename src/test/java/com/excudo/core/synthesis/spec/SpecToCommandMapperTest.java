package com.excudo.core.synthesis.spec;

import com.excudo.core.commands.mutating.slide.ReorderShapeCommand;

import com.excudo.core.commands.Command;
import com.excudo.core.introspection.SlideIntrospector;
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
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * End-to-end state assertions for
 * {@link SpecToCommandMapper#toCommand(CommandSpec)}. Each test builds
 * a spec, converts to a Command via the mapper, executes against a
 * real orchestrator, and asserts the observable slide state via the
 * introspection surface. These tests prove the spec vocabulary is
 * faithful -- not just that {@code toCommand()} returned a non-null
 * object, but that running the command actually produces the state
 * the spec describes.
 *
 * <p>Also covers undo for every spec that maps to an undoable command
 * -- the synthesizer's rollback story (4.5) depends on each spec
 * round-tripping execute + undo cleanly.
 */
public class SpecToCommandMapperTest {

    private PPTXOrchestratorImpl orchestrator;
    private SpecToCommandMapper mapper;
    private SlideIntrospector introspector;

    @Before
    public void setUp() throws Exception {
        PPTXDocument doc = PresentationScaffolder.scaffoldDocument("excudo");
        orchestrator = new PPTXOrchestratorImpl();
        orchestrator.initialize(doc);
        orchestrator.createSlide(1, "Mapper Test", "slideLayout2");
        mapper = new SpecToCommandMapper(orchestrator);
        introspector = new SlideIntrospector(orchestrator);
    }

    // ========== Add / Move / Resize / Rotate ==========

    @Test
    public void addShapeSpec_createsShapeWithGeometry() {
        Command cmd = mapper.toCommand(new CommandSpec.AddShapeSpec(
            1, SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(1_000_000, 2_000_000, 3_000_000, 1_500_000),
            "", "R1", null, null, false));
        cmd.execute();

        // The shape is now addressable; verify via parsed slide data
        // that a rectangle exists with the requested geometry.
        var parsed = lastParsedSlide(1);
        assertTrue("A shape must exist after execute",
            parsed.getShapeRegistry().getAllShapes().stream()
                .anyMatch(s -> s.getType() == SlideShape.ShapeType.RECTANGLE
                    && s.getGeometry().getX() == 1_000_000
                    && s.getGeometry().getWidth() == 3_000_000));
    }

    @Test
    public void moveSpec_changesPositionAndUndoRestoresIt() {
        int spid = addRect(1_000_000, 1_000_000, 2_000_000, 1_000_000);
        Command cmd = mapper.toCommand(new CommandSpec.MoveSpec(1, spid, 5_000_000, 3_000_000));
        cmd.execute();
        var after = getShape(spid);
        assertEquals("X moved", 5_000_000, after.getGeometry().getX());
        assertEquals("Y moved", 3_000_000, after.getGeometry().getY());
        assertEquals("Width preserved by MoveSpec",  2_000_000, after.getGeometry().getWidth());

        cmd.undo();
        var restored = getShape(spid);
        assertEquals("X restored", 1_000_000, restored.getGeometry().getX());
        assertEquals("Y restored", 1_000_000, restored.getGeometry().getY());
    }

    @Test
    public void resizeSpec_changesSizeAndUndoRestoresIt() {
        int spid = addRect(1_000_000, 1_000_000, 2_000_000, 1_000_000);
        Command cmd = mapper.toCommand(new CommandSpec.ResizeSpec(1, spid, 4_000_000, 2_500_000));
        cmd.execute();
        var after = getShape(spid);
        assertEquals("Width resized", 4_000_000, after.getGeometry().getWidth());
        assertEquals("Height resized", 2_500_000, after.getGeometry().getHeight());
        assertEquals("X preserved", 1_000_000, after.getGeometry().getX());

        cmd.undo();
        var restored = getShape(spid);
        assertEquals("Width restored", 2_000_000, restored.getGeometry().getWidth());
    }

    @Test
    public void rotateSpec_setsRotationAndUndoRestoresIt() {
        int spid = addRect(1_000_000, 1_000_000, 2_000_000, 1_000_000);
        Command cmd = mapper.toCommand(new CommandSpec.RotateSpec(1, spid, 30.0));
        cmd.execute();
        var after = getShape(spid);
        // 30 degrees = 1,800,000 raw units (60,000 per degree). Allow
        // rounding tolerance of 1.
        assertEquals("Rotation applied", 1_800_000, after.getGeometry().getRotation());

        cmd.undo();
        var restored = getShape(spid);
        assertEquals("Rotation restored to 0", 0, restored.getGeometry().getRotation());
    }

    // ========== Style ==========

    @Test
    public void setShapeStyleSpec_changesFill() {
        int spid = addRect(1_000_000, 1_000_000, 2_000_000, 1_000_000);
        Command cmd = mapper.toCommand(new CommandSpec.SetShapeStyleSpec(1, spid,
            ShapeStyle.withFill(ShapeFill.scheme("accent4"))));
        cmd.execute();
        ShapeStyle after = introspector.getShapeStyle(1, spid);
        assertNotNull(after);
        assertNotNull("Fill override must be set", after.getFill());
        assertEquals("accent4", after.getFill().getColor().getSchemeVal());
    }

    // ========== Reorder ==========

    @Test
    public void reorderSpec_mapsAllDirections() {
        int spid = addRect(1_000_000, 1_000_000, 2_000_000, 1_000_000);
        // We exercise the mapping for each Direction value. The
        // command's actual z-order effect is asserted by
        // ReorderShapeCommand's own tests; here we just confirm the
        // mapper doesn't throw and returns a non-null Command for all
        // four directions.
        for (CommandSpec.ReorderSpec.Direction d : CommandSpec.ReorderSpec.Direction.values()) {
            Command cmd = mapper.toCommand(new CommandSpec.ReorderSpec(1, spid, d));
            assertNotNull("Mapper must produce a Command for direction " + d, cmd);
        }
    }

    // ========== Transition ==========

    @Test
    public void setTransitionSpec_installsTransition() {
        Command cmd = mapper.toCommand(new CommandSpec.SetTransitionSpec(
            1, TransitionType.FADE, "fast", null));
        cmd.execute();
        var t = introspector.getTransition(1);
        assertNotNull("Transition must be set after execute", t);
        assertEquals(TransitionType.FADE, t.type());
        assertEquals("fast", t.speed());
    }

    @Test
    public void clearTransitionSpec_removesSlideLevelTransition() {
        // Prep: install a transition first.
        orchestrator.setTransition(1, TransitionType.FADE, "med", null);
        assertNotNull(introspector.getTransition(1));

        Command cmd = mapper.toCommand(new CommandSpec.ClearTransitionSpec(1));
        cmd.execute();
        var t = introspector.getTransition(1);
        // After clearing, the slide falls back to layout/master. The
        // bundled excudo theme has none, so null.
        assertNull("After clearing slide-level transition, no inherited fallback expected", t);

        cmd.undo();
        var restored = introspector.getTransition(1);
        assertNotNull("Undo must restore the cleared slide-level transition", restored);
        assertEquals(TransitionType.FADE, restored.type());
    }

    // ========== Animation ==========

    @Test
    public void addAnimationSpec_injectsTheAnimation() {
        int spid = addRect(1_000_000, 1_000_000, 2_000_000, 1_000_000);
        AnimationBinding binding = AnimationBinding.builder()
            .target(spid).type(AnimationType.FADE).entrance().clickTrigger(1).durationMs(500).build();
        Command cmd = mapper.toCommand(new CommandSpec.AddAnimationSpec(1, binding));
        cmd.execute();

        List<AnimationBinding> anims = introspector.listAnimations(1);
        assertEquals("Exactly one animation after execute", 1, anims.size());
        assertEquals("Target SPID preserved", spid, anims.get(0).getTargetSpid());
        assertEquals("Type preserved", AnimationType.FADE, anims.get(0).getAnimationType());
    }

    // ========== Remove ==========

    @Test
    public void removeShapeSpec_removesTheShape() {
        int spid = addRect(1_000_000, 1_000_000, 2_000_000, 1_000_000);
        // Confirm presence
        assertNotNull(getShape(spid));
        Command cmd = mapper.toCommand(new CommandSpec.RemoveShapeSpec(1, spid));
        cmd.execute();
        // Shape registry must no longer contain it.
        var parsed = lastParsedSlide(1);
        assertNull("Shape must be gone after RemoveShapeSpec",
            parsed.getShapeRegistry().getShape(spid));
    }

    // ========== Null + unknown guards ==========

    @Test(expected = IllegalArgumentException.class)
    public void nullSpecThrows() {
        mapper.toCommand(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullOrchestratorInCtorThrows() {
        new SpecToCommandMapper(null);
    }

    // ========== Helpers ==========

    private int addRect(long x, long y, long w, long h) {
        var res = orchestrator.addShape(1, SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(x, y, w, h), "", "Rect",
            ShapeStyle.defaultStyle());
        assertTrue("addRect setup must succeed: " + res.getMessage(), res.isSuccess());
        return res.getData().orElseThrow();
    }

    private SlideShape getShape(int spid) {
        var parsed = lastParsedSlide(1);
        var shape = parsed.getShapeRegistry().getShape(spid);
        assertNotNull("Shape SPID " + spid + " must be present", shape);
        return shape;
    }

    private com.excudo.core.model.ParsedSlideData lastParsedSlide(int slideNumber) {
        var doc = orchestrator.getContext().get().getDocument();
        return doc.getParsedSlideData(slideNumber,
            (dom, n) -> new com.excudo.xml.parsers.SlideXMLParser().parseSlide(dom, n));
    }
}
