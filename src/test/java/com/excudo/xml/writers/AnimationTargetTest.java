package com.excudo.xml.writers;

import com.excudo.core.model.AnimationBinding;
import com.excudo.core.model.AnimationType;
import com.excudo.core.model.PPTXDocument;
import com.excudo.core.orchestration.PPTXOrchestratorImpl;
import com.excudo.core.orchestration.PresentationScaffolder;
import com.excudo.core.results.ExecutionResult;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * An animation on a shape that isn't on the slide is refused when it's added.
 * It used to be written, and only the save check ("animations target shapes
 * that aren't on the slide") caught it, after the agent had built on it.
 */
public class AnimationTargetTest {

    @Test
    public void anAnimationOnAMissingShapeIsRefused() throws Exception {
        PPTXOrchestratorImpl orchestrator = new PPTXOrchestratorImpl();
        orchestrator.initialize(PresentationScaffolder.scaffoldDocument("excudo"));
        orchestrator.createSlide(1, "Targets", "slideLayout7");
        PPTXDocument doc = orchestrator.getContext().get().getDocument();

        ExecutionResult<String> r = orchestrator.addAnimation(1, AnimationBinding.builder().target(42)
            .type(AnimationType.FADE).entrance().duration("500").delay("0").animationGroup("on-click").build());

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("No shape with id 42 on this slide"));
        assertEquals("nothing written", 0, doc.getSlideDocument(1).getElementsByTagNameNS("*", "timing").getLength());
    }
}
