package com.excudo.core.commands;

import com.excudo.core.commands.mutating.slide.SetTextBoxFlagCommand;

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
 * State-level coverage for the in-place TEXT_BOX_FLAG toggle.
 */
public class SetTextBoxFlagCommandTest {

    private PPTXOrchestratorImpl orchestrator;
    private int spid;

    @Before
    public void setUp() throws Exception {
        PPTXDocument doc = PresentationScaffolder.scaffoldDocument("excudo");
        orchestrator = new PPTXOrchestratorImpl();
        orchestrator.initialize(doc);
        orchestrator.createSlide(1, "txBox Test", "slideLayout7");
        ExecutionResult<Integer> res = orchestrator.addShape(1,
            SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(1_000_000, 1_000_000, 2_000_000, 1_000_000),
            "", "R", ShapeStyle.defaultStyle());
        assertTrue(res.isSuccess());
        spid = res.getData().orElseThrow();
    }

    @Test
    public void executeSetsFlagAndUndoClearsIt() {
        assertFalse("Shape starts as a plain rectangle", shape().isTextBox());
        SetTextBoxFlagCommand cmd = new SetTextBoxFlagCommand(1, spid, true, orchestrator);
        cmd.execute();
        assertTrue("After execute, shape is marked as a text box", shape().isTextBox());

        cmd.undo();
        assertFalse("After undo, txBox flag is back to its original (false)", shape().isTextBox());
    }

    @Test
    public void clearingAnExistingFlagAndRestoring() {
        // First set it to true via orchestrator directly, then the
        // command clears + undo restores.
        orchestrator.updateShapeTextBoxFlag(1, spid, true);
        assertTrue(shape().isTextBox());

        SetTextBoxFlagCommand cmd = new SetTextBoxFlagCommand(1, spid, false, orchestrator);
        cmd.execute();
        assertFalse("After execute, flag must be cleared", shape().isTextBox());

        cmd.undo();
        assertTrue("Undo must restore the flag", shape().isTextBox());
    }

    @Test(expected = IllegalArgumentException.class)
    public void zeroSpidRejectedAtConstruction() {
        new SetTextBoxFlagCommand(1, 0, true, orchestrator);
    }

    private SlideShape shape() {
        var doc = orchestrator.getContext().get().getDocument();
        var parsed = doc.getParsedSlideData(1,
            (dom, n) -> new com.excudo.xml.parsers.SlideXMLParser().parseSlide(dom, n));
        return parsed.getShapeRegistry().getShape(spid);
    }
}
