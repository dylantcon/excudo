package com.excudo.core.commands;

import com.excudo.core.commands.mutating.slide.RemoveShapeCommand;
import com.excudo.core.model.AnimationBinding;
import com.excudo.core.model.AnimationType;
import com.excudo.core.model.PPTXDocument;
import com.excudo.core.model.PackageIntegrityValidator;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.ShapeStyle;
import com.excudo.core.model.SlideShape;
import com.excudo.core.orchestration.PPTXOrchestratorImpl;
import com.excudo.core.orchestration.PresentationScaffolder;
import com.excudo.core.results.ExecutionResult;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Removing a shape removes its animations, and a removed shape's id is never
 * handed out again on that slide. Before, the animations stayed behind: they
 * pointed at a missing shape, or (once the id was reused) silently animated
 * whatever shape was added next.
 */
public class RemoveShapeAnimationsTest {

    private PPTXOrchestratorImpl orchestrator;
    private PPTXDocument doc;
    private int a, b;

    @Before
    public void setUp() throws Exception {
        orchestrator = new PPTXOrchestratorImpl();
        orchestrator.initialize(PresentationScaffolder.scaffoldDocument("excudo"));
        orchestrator.createSlide(1, "Remove", "slideLayout7");
        doc = orchestrator.getContext().get().getDocument();
        a = rect(0);
        b = rect(1_000_000);
        fade(a);
        fade(b);
    }

    @Test
    public void removingAShapeRemovesItsAnimationsOnly() {
        assertTrue(orchestrator.removeShape(1, b).isSuccess());
        assertEquals(Set.of(String.valueOf(a)), animatedSpids());
        assertFalse("no build entry left for the removed shape", hasBuildEntryFor(b));
        assertEquals(java.util.List.of(), PackageIntegrityValidator.validate(doc));
    }

    @Test
    public void removingTheLastAnimatedShapeLeavesNoEmptyTimeline() {
        assertTrue(orchestrator.removeShape(1, a).isSuccess());
        assertTrue(orchestrator.removeShape(1, b).isSuccess());
        assertEquals(0, doc.getSlideDocument(1).getElementsByTagNameNS("*", "timing").getLength());
        assertEquals(java.util.List.of(), PackageIntegrityValidator.validate(doc));
    }

    @Test
    public void aRemovedShapesIdIsNotReused() {
        // b has the highest id: lowest-free and max+1 would both hand it out again
        assertTrue(orchestrator.removeShape(1, b).isSuccess());
        int next = rect(2_000_000);
        assertNotEquals("the removed shape's id came back", b, next);
        assertTrue(next > b);
        assertEquals("the new shape isn't animated", Set.of(String.valueOf(a)), animatedSpids());

        // A gap lower down isn't refilled either
        assertTrue(orchestrator.removeShape(1, a).isSuccess());
        int after = rect(3_000_000);
        assertTrue(after > next);
    }

    @Test
    public void undoRestoresTheShapeAndItsAnimations() {
        RemoveShapeCommand remove = new RemoveShapeCommand(1, b, orchestrator);
        remove.execute();
        assertEquals(Set.of(String.valueOf(a)), animatedSpids());

        remove.undo();
        assertEquals(Set.of(String.valueOf(a), String.valueOf(b)), animatedSpids());
        assertTrue(hasBuildEntryFor(b));
        assertEquals(java.util.List.of(), PackageIntegrityValidator.validate(doc));
    }

    // ========== Helpers ==========

    private int rect(long x) {
        ExecutionResult<Integer> r = orchestrator.addShape(1, SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(x, 0, 900_000, 500_000), "Box", "Box", ShapeStyle.defaultStyle());
        assertTrue(r.getMessage(), r.isSuccess());
        return r.getData().orElseThrow();
    }

    private void fade(int spid) {
        ExecutionResult<String> r = orchestrator.addAnimation(1, AnimationBinding.builder().target(spid)
            .type(AnimationType.FADE).entrance().duration("500").delay("0").animationGroup("on-click").build());
        assertTrue(r.getMessage(), r.isSuccess());
    }

    private Set<String> animatedSpids() {
        Set<String> out = new HashSet<>();
        NodeList targets = doc.getSlideDocument(1).getElementsByTagNameNS("*", "spTgt");
        for (int i = 0; i < targets.getLength(); i++) out.add(((Element) targets.item(i)).getAttribute("spid"));
        return out;
    }

    private boolean hasBuildEntryFor(int spid) {
        NodeList bld = doc.getSlideDocument(1).getElementsByTagNameNS("*", "bldP");
        for (int i = 0; i < bld.getLength(); i++) {
            if (((Element) bld.item(i)).getAttribute("spid").equals(String.valueOf(spid))) return true;
        }
        return false;
    }
}
