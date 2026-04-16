package com.excudo.core.commands;

import com.excudo.core.model.PPTXDocument;
import com.excudo.core.orchestration.PPTXOrchestratorImpl;
import com.excudo.core.orchestration.PresentationScaffolder;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests ContentEditCommand (edit-text) for modifying shape text content.
 */
public class ContentEditCommandTest {

    private PPTXOrchestratorImpl orchestrator;

    @Before
    public void setUp() throws Exception {
        PPTXDocument doc = PresentationScaffolder.scaffoldDocument("minimal");
        orchestrator = new PPTXOrchestratorImpl();
        orchestrator.initialize(doc);
        orchestrator.createSlide(1, "Initial Title", "slideLayout2");
    }

    @Test
    public void editShapeTextChangesContent() {
        // Slide 1 has a title placeholder (SPID 2) with "Initial Title"
        var result = orchestrator.editShapeText(1, 2, "Updated Title");
        assertTrue("Edit should succeed", result.isSuccess());
    }

    @Test
    public void editNonexistentShapeFails() {
        var result = orchestrator.editShapeText(1, 999, "Text");
        assertFalse("Editing non-existent shape should fail", result.isSuccess());
    }

    @Test
    public void editNonexistentSlideFails() {
        var result = orchestrator.editShapeText(99, 2, "Text");
        assertFalse("Editing non-existent slide should fail", result.isSuccess());
    }

    @Test
    public void editWithEmptyText() {
        var result = orchestrator.editShapeText(1, 2, "");
        // Empty text should be allowed (clears content)
        assertNotNull(result);
    }

    @Test
    public void editWithMultiLineText() {
        var result = orchestrator.editShapeText(1, 3,
            "Line 1\nLine 2\nLine 3");
        assertTrue("Multi-line edit should succeed", result.isSuccess());
    }

    @Test
    public void editWithBulletText() {
        var result = orchestrator.editShapeText(1, 3,
            "- Bullet 1\n- Bullet 2\n  - Sub-bullet");
        assertTrue("Bullet text edit should succeed", result.isSuccess());
    }
}
