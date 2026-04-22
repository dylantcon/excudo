package com.excudo.core.commands;

import com.excudo.core.commands.mutating.deck.MoveSlideCommand;

import com.excudo.core.model.PPTXDocument;
import com.excudo.core.orchestration.PPTXOrchestratorImpl;
import com.excudo.core.orchestration.PresentationScaffolder;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests MoveSlideCommand reordering slides within a presentation.
 */
public class MoveSlideCommandTest {

    private PPTXOrchestratorImpl orchestrator;

    @Before
    public void setUp() throws Exception {
        PPTXDocument doc = PresentationScaffolder.scaffoldDocument("minimal");
        orchestrator = new PPTXOrchestratorImpl();
        orchestrator.initialize(doc);
        orchestrator.createSlide(1, "Slide A", "slideLayout2");
        orchestrator.createSlide(2, "Slide B", "slideLayout2");
        orchestrator.createSlide(3, "Slide C", "slideLayout2");
    }

    @Test
    public void moveSlideForward() {
        var result = orchestrator.moveSlide(1, 3);
        assertTrue("Move should succeed", result.isSuccess());
    }

    @Test
    public void moveSlideBackward() {
        var result = orchestrator.moveSlide(3, 1);
        assertTrue("Move should succeed", result.isSuccess());
    }

    @Test
    public void moveSlideSamePosition() {
        var result = orchestrator.moveSlide(2, 2);
        // Should succeed (no-op) or report no change
        assertNotNull(result);
    }

    @Test
    public void slideCountPreservedAfterMove() {
        int before = orchestrator.getAllSlideMetadata().size();
        orchestrator.moveSlide(1, 3);
        int after = orchestrator.getAllSlideMetadata().size();
        assertEquals("Slide count should not change", before, after);
    }
}
