package com.excudo.core.commands;

import com.excudo.core.commands.mutating.slide.ClearSlideContentCommand;

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
 * End-to-end state-assertions for the synthesizer-backed
 * {@link ClearSlideContentCommand}. Adds user shapes to a slide, runs
 * the clear, asserts only layout-baseline shapes remain. Then undoes
 * and asserts the user shapes return.
 */
public class ClearSlideContentCommandTest {

    private PPTXOrchestratorImpl orchestrator;

    @Before
    public void setUp() throws Exception {
        PPTXDocument doc = PresentationScaffolder.scaffoldDocument("excudo");
        orchestrator = new PPTXOrchestratorImpl();
        orchestrator.initialize(doc);
    }

    @Test
    public void clearRemovesUserShapesKeepsPlaceholders() {
        // slideLayout2 has title + content placeholders. Adding user
        // shapes on top should leave them untouched after clear.
        orchestrator.createSlide(1, "Clear Test", "slideLayout2");
        int placeholdersBefore = shapeCount(1);
        assertTrue("Layout must project placeholders before clear (>0 shapes)",
            placeholdersBefore > 0);

        addRect(3_000_000, 3_000_000, 1_000_000, 1_000_000);
        addRect(5_000_000, 3_000_000, 1_000_000, 1_000_000);
        int afterAdds = shapeCount(1);
        assertEquals("Two user shapes added",
            placeholdersBefore + 2, afterAdds);

        ClearSlideContentCommand cmd = new ClearSlideContentCommand(1, orchestrator);
        cmd.execute();

        assertEquals("Clear must leave only layout placeholders",
            placeholdersBefore, shapeCount(1));
    }

    @Test
    public void clearOnBlankLayoutRemovesAllShapes() {
        orchestrator.createSlide(1, "Blank Clear", "slideLayout7");
        // Blank layout has no placeholders, so user-only shapes go away.
        addRect(0, 0, 1_000_000, 500_000);
        addRect(2_000_000, 0, 1_000_000, 500_000);
        assertEquals(2, shapeCount(1));

        new ClearSlideContentCommand(1, orchestrator).execute();
        assertEquals("Blank layout + clear = no shapes", 0, shapeCount(1));
    }

    @Test
    public void undoRestoresUserShapes() {
        orchestrator.createSlide(1, "Undo Test", "slideLayout7");
        addRect(1_000_000, 1_000_000, 2_000_000, 1_000_000);
        int initial = shapeCount(1);
        assertEquals(1, initial);

        ClearSlideContentCommand cmd = new ClearSlideContentCommand(1, orchestrator);
        cmd.execute();
        assertEquals("After clear, no shapes", 0, shapeCount(1));

        cmd.undo();
        assertEquals("Undo restores the removed shape count", initial, shapeCount(1));
    }

    @Test
    public void emptySlideYieldsEmptyScript() {
        orchestrator.createSlide(1, "Already Empty", "slideLayout7");
        // No user content added. Clear must succeed as a no-op.
        ClearSlideContentCommand cmd = new ClearSlideContentCommand(1, orchestrator);
        cmd.execute();
        assertEquals("Still empty after clear", 0, shapeCount(1));
        assertTrue(cmd.isExecuted());
    }

    @Test
    public void animationsScopeKeepsShapes() {
        orchestrator.createSlide(1, "Anim Clear", "slideLayout7");
        int spid = addRect(0, 0, 1_000_000, 500_000, "keep me");
        addFade(spid);
        assertEquals(1, animationCount(1));

        ClearSlideContentCommand cmd = new ClearSlideContentCommand(1,
            ClearSlideContentCommand.parseScope("animations"), orchestrator);
        cmd.execute();
        assertEquals("Animations cleared", 0, animationCount(1));
        assertEquals("Shape kept", 1, shapeCount(1));
        assertEquals("Text kept", "keep me", textOf(1, spid));

        cmd.undo();
        assertEquals("Undo restores the animation", 1, animationCount(1));
    }

    @Test
    public void textScopeEmptiesTextKeepsShapesAndAnimations() {
        orchestrator.createSlide(1, "Text Clear", "slideLayout7");
        int spid = addRect(0, 0, 1_000_000, 500_000, "wipe me");
        addFade(spid);

        ClearSlideContentCommand cmd = new ClearSlideContentCommand(1,
            ClearSlideContentCommand.parseScope("text"), orchestrator);
        cmd.execute();
        assertEquals("Text emptied", "", textOf(1, spid));
        assertEquals("Shape kept", 1, shapeCount(1));
        assertEquals("Animation kept", 1, animationCount(1));

        cmd.undo();
        assertEquals("Undo restores the text", "wipe me", textOf(1, spid));
    }

    @Test
    public void shapesScopeTakesTheirAnimationsAlong() {
        orchestrator.createSlide(1, "Shape Clear", "slideLayout7");
        int spid = addRect(0, 0, 1_000_000, 500_000, "");
        addFade(spid);

        new ClearSlideContentCommand(1, ClearSlideContentCommand.parseScope("shapes"), orchestrator)
            .execute();
        assertEquals(0, shapeCount(1));
        assertEquals("No animation left targeting a removed shape", 0, animationCount(1));
    }

    @Test
    public void parseScopeAcceptsListsAndRejectsUnknownParts() {
        assertEquals(java.util.EnumSet.allOf(ClearSlideContentCommand.Part.class),
            ClearSlideContentCommand.parseScope("all"));
        assertEquals(java.util.EnumSet.of(ClearSlideContentCommand.Part.ANIMATIONS,
                ClearSlideContentCommand.Part.TRANSITION),
            ClearSlideContentCommand.parseScope("Animations, transition"));
        assertThrows(IllegalArgumentException.class,
            () -> ClearSlideContentCommand.parseScope("pictures"));
    }

    // ========== Helpers ==========

    private int addRect(long x, long y, long w, long h) {
        return addRect(x, y, w, h, "");
    }

    private int addRect(long x, long y, long w, long h, String text) {
        ExecutionResult<Integer> r = orchestrator.addShape(1,
            SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(x, y, w, h),
            text, "UserRect", ShapeStyle.defaultStyle());
        assertTrue(r.isSuccess());
        return r.getData().orElseThrow();
    }

    private void addFade(int spid) {
        var binding = com.excudo.core.model.AnimationBinding.builder()
            .target(spid).type(com.excudo.core.model.AnimationType.FADE).entrance()
            .duration("500").delay("0").animationGroup("on-click").build();
        assertTrue(orchestrator.addAnimation(1, binding).isSuccess());
    }

    private com.excudo.core.model.ParsedSlideData parsed(int slideNumber) {
        var doc = orchestrator.getContext().get().getDocument();
        return doc.getParsedSlideData(slideNumber,
            (dom, n) -> new com.excudo.xml.parsers.SlideXMLParser().parseSlide(dom, n));
    }

    private int animationCount(int slideNumber) {
        return parsed(slideNumber).getAnimationBindings().size();
    }

    private String textOf(int slideNumber, int spid) {
        String text = parsed(slideNumber).getShapeRegistry().getShape(spid).getTextContent();
        return text == null ? "" : text.trim();
    }

    private int shapeCount(int slideNumber) {
        var doc = orchestrator.getContext().get().getDocument();
        var parsed = doc.getParsedSlideData(slideNumber,
            (dom, n) -> new com.excudo.xml.parsers.SlideXMLParser().parseSlide(dom, n));
        return parsed.getShapeRegistry().getAllShapes().size();
    }
}
