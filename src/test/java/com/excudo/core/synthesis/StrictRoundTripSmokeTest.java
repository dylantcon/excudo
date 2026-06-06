package com.excudo.core.synthesis;

import com.excudo.core.model.PPTXDocument;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.ShapeStyle;
import com.excudo.core.model.SlideShape;
import com.excudo.core.orchestration.PPTXOrchestratorImpl;
import com.excudo.core.orchestration.PresentationScaffolder;
import com.excudo.test.utils.StrictRoundTrip;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * Smoke-validates {@link StrictRoundTrip} on the simplest non-trivial
 * scenarios before per-spec coverage builds on top of it. If the
 * renormalization logic is wrong, these fail before more complex tests
 * can mislead about which spec is broken.
 */
public class StrictRoundTripSmokeTest {

    @Test
    public void emptySlide_strictRoundTrip() {
        PPTXOrchestratorImpl orch = newScaffolded();
        orch.createSlide(1, "Src", "slideLayout7");
        orch.createSlide(2, "Tgt", "slideLayout7");
        StrictRoundTrip.assertStrictRoundTrip(orch, 1, 2);
    }

    @Test
    public void singleRectangle_strictRoundTrip() {
        PPTXOrchestratorImpl orch = newScaffolded();
        orch.createSlide(1, "Src", "slideLayout7");
        orch.createSlide(2, "Tgt", "slideLayout7");
        orch.addShape(1, SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(1_000_000, 1_000_000, 3_000_000, 2_000_000),
            "Hello", "R1", ShapeStyle.defaultStyle());
        StrictRoundTrip.assertStrictRoundTrip(orch, 1, 2);
    }

    @Test
    public void twoRectangles_strictRoundTrip() {
        PPTXOrchestratorImpl orch = newScaffolded();
        orch.createSlide(1, "Src", "slideLayout7");
        orch.createSlide(2, "Tgt", "slideLayout7");
        var r1 = orch.addShape(1, SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(0, 0, 1_000_000, 1_000_000), "",
            "A", ShapeStyle.defaultStyle());
        var r2 = orch.addShape(1, SlideShape.ShapeType.ELLIPSE,
            new ShapeGeometry(2_000_000, 0, 1_000_000, 1_000_000), "",
            "B", ShapeStyle.defaultStyle());
        assertTrue(r1.isSuccess());
        assertTrue(r2.isSuccess());
        StrictRoundTrip.assertStrictRoundTrip(orch, 1, 2);
    }

    private static PPTXOrchestratorImpl newScaffolded() {
        try {
            PPTXDocument doc = PresentationScaffolder.scaffoldDocument("excudo");
            PPTXOrchestratorImpl orch = new PPTXOrchestratorImpl();
            orch.initialize(doc);
            return orch;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
