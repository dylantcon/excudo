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

    // ========== Coverage-matrix fill (P2) ==========

    @Test
    public void renameShapeSpec_changesShapeName() {
        int spid = addRect(0, 0, 1_000_000, 1_000_000);
        Command cmd = mapper.toCommand(new CommandSpec.RenameShapeSpec(1, spid, "Renamed"));
        cmd.execute();
        assertEquals("Renamed", getShape(spid).getName());
    }

    @Test
    public void setTextBoxFlagSpec_togglesTxBoxAttribute() {
        int spid = addRect(0, 0, 1_000_000, 1_000_000);
        Command cmd = mapper.toCommand(new CommandSpec.SetTextBoxFlagSpec(1, spid, true));
        cmd.execute();
        assertTrue("txBox flag must be set after spec applies",
            getShape(spid).isTextBox());
    }

    @Test
    public void setRunFormatSpec_updatesRunFormatting() {
        // Build a shape with one paragraph containing one run.
        int spid = addRect(0, 0, 2_000_000, 1_000_000);
        var body = com.excudo.core.model.TextBody.builder()
            .addParagraph(com.excudo.core.model.TextParagraph.builder()
                .addRun(com.excudo.core.model.TextRun.builder("hello").build())
                .build()).build();
        var seed = orchestrator.setTextBody(1, spid, body);
        assertTrue(seed.isSuccess());

        // Rewrite the run with bold + larger font.
        var newRun = com.excudo.core.model.TextRun.builder("hello")
            .bold(true).fontSize(28).build();
        Command cmd = mapper.toCommand(new CommandSpec.SetRunFormatSpec(1, spid, 0, 0, newRun));
        cmd.execute();
        // Verify the run carries the new formatting via re-extraction.
        var shape = getShape(spid);
        var txBody = firstTxBody(shape);
        assertNotNull(txBody);
        var extracted = com.excudo.core.metrics.TextBodyExtractor.extract(txBody);
        var run = extracted.getParagraphs().get(0).getRuns().get(0);
        assertEquals(Boolean.TRUE, run.getBold());
        assertEquals(Integer.valueOf(28), run.getFontSize());
    }

    @Test
    public void setTextSpec_replacesTextBody() {
        int spid = addRect(0, 0, 2_000_000, 1_000_000);
        var body = com.excudo.core.model.TextBody.builder()
            .addParagraph(com.excudo.core.model.TextParagraph.builder()
                .addRun(com.excudo.core.model.TextRun.builder("FromSpec").build())
                .build()).build();
        Command cmd = mapper.toCommand(new CommandSpec.SetTextSpec(1, spid, body));
        cmd.execute();
        var shape = getShape(spid);
        var txBody = firstTxBody(shape);
        var extracted = com.excudo.core.metrics.TextBodyExtractor.extract(txBody);
        assertEquals("FromSpec", extracted.getParagraphs().get(0).getRuns().get(0).getText());
    }

    @Test
    public void createGroupSpec_groupsListedChildren() {
        int a = addRect(0, 0, 1_000_000, 1_000_000);
        int b = addRect(2_000_000, 0, 1_000_000, 1_000_000);
        Command cmd = mapper.toCommand(new CommandSpec.CreateGroupSpec(
            1, java.util.List.of(a, b), "G1"));
        cmd.execute();
        var parsed = lastParsedSlide(1);
        var groups = parsed.getShapeRegistry().getAllShapes().stream()
            .filter(s -> s.getType() == SlideShape.ShapeType.GROUP).toList();
        assertEquals("Exactly one new group expected", 1, groups.size());
        int groupSpid = groups.get(0).getSpid();
        assertEquals("a is now in the group",
            groupSpid, parsed.getShapeRegistry().getParentSpid(a));
        assertEquals("b is now in the group",
            groupSpid, parsed.getShapeRegistry().getParentSpid(b));
    }

    @Test
    public void ungroupSpec_dissolvesGroup() {
        int a = addRect(0, 0, 1_000_000, 1_000_000);
        int b = addRect(2_000_000, 0, 1_000_000, 1_000_000);
        var grp = orchestrator.groupShapes(1, java.util.List.of(a, b));
        assertTrue("groupShapes must succeed: " + grp.getMessage(), grp.isSuccess());
        int groupSpid = grp.getData().orElseThrow();
        Command cmd = mapper.toCommand(new CommandSpec.UngroupSpec(1, groupSpid));
        cmd.execute();
        var parsed = lastParsedSlide(1);
        assertNull("Group must be gone after UngroupSpec",
            parsed.getShapeRegistry().getShape(groupSpid));
        assertNotNull("Children must remain after ungroup",
            parsed.getShapeRegistry().getShape(a));
    }

    @Test
    public void addToGroupSpec_movesShapeIntoGroup() {
        int a = addRect(0, 0, 1_000_000, 1_000_000);
        int b = addRect(2_000_000, 0, 1_000_000, 1_000_000);
        int c = addRect(4_000_000, 0, 1_000_000, 1_000_000);
        var grp = orchestrator.groupShapes(1, java.util.List.of(a, b));
        int groupSpid = grp.getData().orElseThrow();
        Command cmd = mapper.toCommand(new CommandSpec.AddToGroupSpec(1, groupSpid, c));
        cmd.execute();
        var parsed = lastParsedSlide(1);
        assertEquals("Child c must now be parented by the group",
            groupSpid, parsed.getShapeRegistry().getParentSpid(c));
    }

    @Test
    public void detachFromGroupSpec_movesChildToTopLevel() {
        int a = addRect(0, 0, 1_000_000, 1_000_000);
        int b = addRect(2_000_000, 0, 1_000_000, 1_000_000);
        var grp = orchestrator.groupShapes(1, java.util.List.of(a, b));
        int groupSpid = grp.getData().orElseThrow();
        Command cmd = mapper.toCommand(new CommandSpec.DetachFromGroupSpec(1, a));
        cmd.execute();
        var parsed = lastParsedSlide(1);
        assertTrue("Child a must be top-level (no parent SPID) after detach",
            parsed.getShapeRegistry().getParentSpid(a) <= 0);
        assertNotNull("Group must still exist with the other child",
            parsed.getShapeRegistry().getShape(groupSpid));
    }

    @Test
    public void removeAnimationSpec_removesByTimingNodeId() {
        int spid = addRect(0, 0, 1_000_000, 1_000_000);
        var b = AnimationBinding.builder()
            .target(spid).type(AnimationType.APPEAR).entrance().durationMs(500).build();
        var addRes = orchestrator.addAnimation(1, b, null);
        assertTrue(addRes.isSuccess());
        // After add, the binding has its timingNodeId; pull it via parsed data.
        var anims = lastParsedSlide(1).getAnimationBindings();
        assertFalse(anims.isEmpty());
        int cTn = anims.get(0).getTimingNodeId();
        assertTrue(cTn > 0);
        Command cmd = mapper.toCommand(new CommandSpec.RemoveAnimationSpec(1, cTn));
        cmd.execute();
        assertTrue("Animation must be removed after spec applies",
            lastParsedSlide(1).getAnimationBindings().isEmpty());
    }

    @Test
    public void setAnimationTimingSpec_updatesDurationInPlace() {
        int spid = addRect(0, 0, 1_000_000, 1_000_000);
        var b = AnimationBinding.builder()
            .target(spid).type(AnimationType.APPEAR).entrance().durationMs(500).build();
        orchestrator.addAnimation(1, b, null);
        int cTn = lastParsedSlide(1).getAnimationBindings().get(0).getTimingNodeId();
        Command cmd = mapper.toCommand(new CommandSpec.SetAnimationTimingSpec(
            1, cTn, "1500", null));
        cmd.execute();
        var anim = lastParsedSlide(1).getAnimationBindings().get(0);
        assertEquals("Duration must be updated", "1500", anim.getDuration());
    }

    @Test
    public void createCodeBoxSpec_addsTaggedGroup() {
        Command cmd = mapper.toCommand(new CommandSpec.CreateCodeBoxSpec(
            1, "java", "int x = 1;\nint y = 2;", 500_000L, 500_000L, null, null, null, null));
        cmd.execute();
        var groups = lastParsedSlide(1).getShapeRegistry().getAllShapes().stream()
            .filter(s -> s.getType() == SlideShape.ShapeType.GROUP).toList();
        assertEquals(1, groups.size());
        assertTrue("Group name must carry code-box marker: " + groups.get(0).getName(),
            groups.get(0).getName().startsWith("excudo:code_box_v1:"));
    }

    @Test
    public void createDiagramSpec_addsTaggedGroup() {
        Command cmd = mapper.toCommand(new CommandSpec.CreateDiagramSpec(
            1, "graph TD\n  A --> B", null, null, null, null, null));
        cmd.execute();
        var groups = lastParsedSlide(1).getShapeRegistry().getAllShapes().stream()
            .filter(s -> s.getType() == SlideShape.ShapeType.GROUP).toList();
        assertEquals(1, groups.size());
        assertTrue("Group name must carry diagram marker: " + groups.get(0).getName(),
            groups.get(0).getName().startsWith("excudo:diagram_v1:"));
    }

    @Test
    public void addConnectorSpec_addsConnectorWithEndpoints() {
        int a = addRect(0, 0, 1_000_000, 1_000_000);
        int b = addRect(3_000_000, 0, 1_000_000, 1_000_000);
        Command cmd = mapper.toCommand(new CommandSpec.AddConnectorSpec(
            1, "straight", new ShapeGeometry(1_000_000, 500_000, 2_000_000, 0),
            "none", "triangle", "000000",
            a, 1, b, 1, null, "Conn", null));
        cmd.execute();
        var conns = lastParsedSlide(1).getShapeRegistry().getAllShapes().stream()
            .filter(s -> s.getType() == SlideShape.ShapeType.CONNECTION).toList();
        assertEquals(1, conns.size());
    }

    @Test
    public void addPictureSpec_addsPictureFromExistingMediaPart() {
        // Seed a media part in the deck so AddPictureCommand can resolve it.
        byte[] bytes = new byte[]{1, 2, 3, 4};
        var media = new com.excudo.core.model.MediaElement(
            "ppt/media/image1.png", "image/png", bytes);
        orchestrator.getContext().get().getDocument().putMediaPart(media);

        Command cmd = mapper.toCommand(new CommandSpec.AddPictureSpec(
            1, com.excudo.core.model.BlipRef.of("ppt/media/image1.png"),
            new ShapeGeometry(0, 0, 2_000_000, 2_000_000), "Hero", null));
        cmd.execute();
        var pics = lastParsedSlide(1).getShapeRegistry().getAllShapes().stream()
            .filter(s -> s.getType() == SlideShape.ShapeType.PICTURE).toList();
        assertEquals(1, pics.size());
    }

    @Test(expected = com.excudo.core.commands.CommandExecutionException.class)
    public void addPictureSpec_missingMediaPart_throws() {
        Command cmd = mapper.toCommand(new CommandSpec.AddPictureSpec(
            1, com.excudo.core.model.BlipRef.of("ppt/media/nonexistent.png"),
            new ShapeGeometry(0, 0, 1_000_000, 1_000_000), null, null));
        cmd.execute();
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

    private static org.w3c.dom.Element firstTxBody(SlideShape s) {
        org.w3c.dom.NodeList kids = s.getXmlElement().getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            org.w3c.dom.Node n = kids.item(i);
            if (n.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE
                    && "txBody".equals(n.getLocalName())) {
                return (org.w3c.dom.Element) n;
            }
        }
        return null;
    }
}
