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

    // ========== Helpers ==========

    private int addRect(long x, long y, long w, long h) {
        ExecutionResult<Integer> r = orchestrator.addShape(1,
            SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(x, y, w, h),
            "", "UserRect", ShapeStyle.defaultStyle());
        assertTrue(r.isSuccess());
        return r.getData().orElseThrow();
    }

    private int shapeCount(int slideNumber) {
        var doc = orchestrator.getContext().get().getDocument();
        var parsed = doc.getParsedSlideData(slideNumber,
            (dom, n) -> new com.excudo.xml.parsers.SlideXMLParser().parseSlide(dom, n));
        return parsed.getShapeRegistry().getAllShapes().size();
    }
}
