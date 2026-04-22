package com.excudo.core.commands;

import com.excudo.core.commands.mutating.slide.RenameShapeCommand;

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
 * State-level verification of RenameShapeCommand: execute must update
 * the parsed shape's name, undo must restore the original, and the
 * command must reject invalid input at construction time.
 */
public class RenameShapeCommandTest {

    private PPTXOrchestratorImpl orchestrator;
    private int spid;

    @Before
    public void setUp() throws Exception {
        PPTXDocument doc = PresentationScaffolder.scaffoldDocument("excudo");
        orchestrator = new PPTXOrchestratorImpl();
        orchestrator.initialize(doc);
        orchestrator.createSlide(1, "Rename Test", "slideLayout7");
        ExecutionResult<Integer> res = orchestrator.addShape(1,
            SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(1_000_000, 1_000_000, 2_000_000, 1_000_000),
            "", "Original", ShapeStyle.defaultStyle());
        assertTrue(res.isSuccess());
        spid = res.getData().orElseThrow();
    }

    @Test
    public void executeChangesNameAndUndoRestoresIt() {
        String originalName = shape().getName();
        assertEquals("Original", originalName);

        RenameShapeCommand cmd = new RenameShapeCommand(1, spid, "Renamed", orchestrator);
        cmd.execute();
        assertEquals("Renamed", shape().getName());
        assertTrue(cmd.canUndo());

        cmd.undo();
        assertEquals("Rename undo must restore the original name",
            originalName, shape().getName());
        assertFalse(cmd.isExecuted());
    }

    @Test(expected = CommandExecutionException.class)
    public void executeOnMissingSpidThrows() {
        RenameShapeCommand cmd = new RenameShapeCommand(1, 99_999, "X", orchestrator);
        cmd.execute();
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullOrchestratorThrows() {
        new RenameShapeCommand(1, 5, "x", null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void zeroSpidThrows() {
        new RenameShapeCommand(1, 0, "x", orchestrator);
    }

    private SlideShape shape() {
        var doc = orchestrator.getContext().get().getDocument();
        var parsed = doc.getParsedSlideData(1,
            (dom, n) -> new com.excudo.xml.parsers.SlideXMLParser().parseSlide(dom, n));
        return parsed.getShapeRegistry().getShape(spid);
    }
}
