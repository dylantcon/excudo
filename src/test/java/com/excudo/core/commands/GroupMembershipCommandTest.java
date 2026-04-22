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

import java.util.List;

import static org.junit.Assert.*;

/**
 * End-to-end state assertions for the in-place group membership primitives:
 * {@link AddToGroupCommand} and {@link DetachFromGroupCommand}. Builds
 * a group with an initial child via {@code GroupShapesCommand}, then
 * tests adding another shape in and detaching one out.
 */
public class GroupMembershipCommandTest {

    private PPTXOrchestratorImpl orchestrator;
    private int groupSpid;
    private int firstChild;
    private int looseShape;

    @Before
    public void setUp() throws Exception {
        PPTXDocument doc = PresentationScaffolder.scaffoldDocument("excudo");
        orchestrator = new PPTXOrchestratorImpl();
        orchestrator.initialize(doc);
        orchestrator.createSlide(1, "Group Test", "slideLayout7");

        // Two shapes to go in the group, one loose shape to add later.
        int a = addRect(0, 0, 1_000_000, 500_000);
        int b = addRect(1_500_000, 0, 1_000_000, 500_000);
        looseShape = addRect(4_000_000, 0, 800_000, 800_000);

        // Group a + b.
        GroupShapesCommand grp = new GroupShapesCommand(1, List.of(a, b), orchestrator);
        grp.execute();
        // The group itself gets a fresh SPID; derive it by finding a
        // shape with type GROUP on the slide.
        groupSpid = parsed().getShapeRegistry().getAllShapes().stream()
            .filter(s -> s.getType() == SlideShape.ShapeType.GROUP)
            .findFirst().orElseThrow().getSpid();
        firstChild = a;
    }

    @Test
    public void addToGroup_movesShapeUnderGroupDom() {
        // Before: looseShape is at top level.
        assertEquals("looseShape must start at top level",
            -1, parsed().getShapeRegistry().getParentSpid(looseShape));

        AddToGroupCommand cmd = new AddToGroupCommand(1, groupSpid, looseShape, orchestrator);
        cmd.execute();

        // After: looseShape's parent registers as groupSpid.
        assertEquals("looseShape must now be a child of groupSpid",
            groupSpid, parsed().getShapeRegistry().getParentSpid(looseShape));
    }

    @Test
    public void addToGroupUndo_restoresTopLevelPosition() {
        AddToGroupCommand cmd = new AddToGroupCommand(1, groupSpid, looseShape, orchestrator);
        cmd.execute();
        cmd.undo();
        assertEquals("Undo must send looseShape back to top level",
            -1, parsed().getShapeRegistry().getParentSpid(looseShape));
    }

    @Test
    public void detachFromGroup_movesChildToTopLevel() {
        assertEquals("firstChild starts in the group",
            groupSpid, parsed().getShapeRegistry().getParentSpid(firstChild));

        DetachFromGroupCommand cmd = new DetachFromGroupCommand(1, firstChild, orchestrator);
        cmd.execute();
        assertEquals("firstChild must now be at top level",
            -1, parsed().getShapeRegistry().getParentSpid(firstChild));
        assertTrue("Command reports undoable with captured group", cmd.canUndo());
    }

    @Test
    public void detachUndo_returnsChildToOriginalGroup() {
        DetachFromGroupCommand cmd = new DetachFromGroupCommand(1, firstChild, orchestrator);
        cmd.execute();
        cmd.undo();
        assertEquals("Undo must restore group membership",
            groupSpid, parsed().getShapeRegistry().getParentSpid(firstChild));
    }

    @Test(expected = CommandExecutionException.class)
    public void detachOnTopLevelShape_throws() {
        // looseShape is already at top level; detach should fail clean.
        new DetachFromGroupCommand(1, looseShape, orchestrator).execute();
    }

    // ========== Helpers ==========

    private int addRect(long x, long y, long w, long h) {
        ExecutionResult<Integer> r = orchestrator.addShape(1,
            SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(x, y, w, h),
            "", "R", ShapeStyle.defaultStyle());
        assertTrue(r.isSuccess());
        return r.getData().orElseThrow();
    }

    private com.excudo.core.model.ParsedSlideData parsed() {
        var doc = orchestrator.getContext().get().getDocument();
        return doc.getParsedSlideData(1,
            (dom, n) -> new com.excudo.xml.parsers.SlideXMLParser().parseSlide(dom, n));
    }
}
