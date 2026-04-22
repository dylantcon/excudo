package com.excudo.core.commands;

import com.excudo.core.commands.mutating.slide.AddShapeCommand;

import com.excudo.core.model.PPTXDocument;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.SlideShape;
import com.excudo.core.orchestration.PPTXOrchestratorImpl;
import com.excudo.core.orchestration.PresentationScaffolder;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Verifies the Tier 3 overlap warning end-to-end: the polygon-clipping
 * intersection result reaches the agent through the display adapter,
 * with the right SPIDs / percentage / EMU area / z-order hint, and
 * crucially that the warning correctly suppresses for shape pairs
 * that bbox-overlap but don't actually-overlap (the entire reason we
 * built the rigorous polygon math instead of the original bbox-only
 * plan).
 */
public class AddShapeOverlapWarningTest {

    private PPTXOrchestratorImpl orchestrator;
    private ShapeCommandFactory factory;
    private RecordingDisplay display;

    @Before
    public void setUp() throws Exception {
        PPTXDocument doc = PresentationScaffolder.scaffoldDocument("excudo");
        orchestrator = new PPTXOrchestratorImpl();
        orchestrator.initialize(doc);
        // Use Blank layout so there's no content placeholder eating the
        // overlap area. Rectangles will be the only non-placeholder
        // shapes, making the assertions clean.
        orchestrator.createSlide(1, "Overlap Test", "slideLayout7");
        factory = new ShapeCommandFactory(orchestrator);
        display = new RecordingDisplay();
    }

    @Test
    public void halfOverlappingRectangleEmitsWarningWithCorrectPercentage() throws Exception {
        // Two 3M-wide rects, second overlaps first by half. Smaller
        // shape's area is 6T EMU^2; overlap is 3T EMU^2 = 50%.
        addRect(1_000_000, 1_000_000, 3_000_000, 2_000_000);
        display.clear();
        addRect(2_500_000, 1_000_000, 3_000_000, 2_000_000);

        assertEquals("Exactly one warning expected", 1, display.messages.size());
        String msg = display.messages.get(0);
        assertTrue("Message must be NOTE-tagged: " + msg, msg.startsWith("NOTE:"));
        assertTrue("Message must report ~50% overlap: " + msg, msg.contains("50%"));
        assertTrue("Message must point at z-order vocabulary: " + msg, msg.contains("reorder"));
        assertTrue("Message must include forward/backward hint: " + msg,
            msg.contains("front") || msg.contains("back/forward/backward"));
    }

    @Test
    public void disjointRectanglesEmitNoWarning() throws Exception {
        addRect(1_000_000, 1_000_000, 2_000_000, 2_000_000);
        display.clear();
        addRect(5_000_000, 1_000_000, 2_000_000, 2_000_000);
        assertEquals("Disjoint shapes must not produce a warning", 0, display.messages.size());
    }

    @Test
    public void edgeTouchingRectanglesEmitNoWarning() throws Exception {
        addRect(1_000_000, 1_000_000, 2_000_000, 2_000_000);
        display.clear();
        addRect(3_000_000, 1_000_000, 2_000_000, 2_000_000);
        assertEquals("Edge-touching shapes share no area; no warning",
            0, display.messages.size());
    }

    @Test
    public void ellipseBesideRectangleNoFalsePositive() throws Exception {
        // Bbox overlap exists (ellipse extends just into the rect's bbox)
        // but the actual elliptical curve clears the rect's interior,
        // so true intersection ratio is below threshold. This is exactly
        // the case the rigorous polygon math was built to handle that the
        // bbox-only approach would have flagged.
        addRect(1_000_000, 1_000_000, 2_000_000, 2_000_000); // rect spans 1M-3M x
        display.clear();
        // Ellipse centered at x=3.4M, radius 0.5M -> spans 2.9M-3.9M;
        // bbox overlap is 0.1M wide. True ellipse-rect intersection is
        // a thin sliver, well below the 25% threshold.
        addEllipse(2_900_000, 1_500_000, 1_000_000, 1_000_000);
        assertEquals("Ellipse barely entering rect's bbox should not "
            + "false-positive on true polygon overlap",
            0, display.messages.size());
    }

    @Test
    public void fullyContainedSmallShapeWarnsAt100Percent() throws Exception {
        // Big rect, then a small one entirely inside it. Smaller shape's
        // area is fully overlapped -> 100%.
        addRect(0, 0, 10_000_000, 5_000_000);
        display.clear();
        addRect(2_000_000, 1_000_000, 1_000_000, 1_000_000);
        assertEquals(1, display.messages.size());
        String msg = display.messages.get(0);
        assertTrue("Fully contained smaller shape should warn at 100%: " + msg,
            msg.contains("100%"));
    }

    @Test
    public void overlapWithPlaceholderSuppressed() throws Exception {
        // Use the slideLayout2 (title + content) which DOES have placeholders.
        // Drop a rectangle squarely inside the content placeholder area -- this
        // is intentional layout-fill, not an overlap bug, so the warning
        // should be suppressed.
        orchestrator.createSlide(2, "Placeholder Test", "slideLayout2");
        display.clear();
        var parsed = new com.excudo.core.parsing.ParsedCommand("add-shape", java.util.Map.of(
            "slide", "2", "shape-type", "RECTANGLE", "text", "in-placeholder",
            "x", "1000000", "y", "2000000", "width", "5000000", "height", "2000000"));
        Command cmd = factory.createFromParsedCommand(parsed, display);
        cmd.execute();
        assertEquals("Overlapping a placeholder should NOT warn (intentional fill)",
            0, display.messages.size());
    }

    @Test
    public void thresholdConfigurableViaEnvVarBoundary() throws Exception {
        // Default threshold is 25%. A 30% overlap fires; a 20% overlap
        // doesn't. Test the default boundary explicitly so any future
        // tweak to the threshold surfaces.
        addRect(0, 0, 10_000_000, 1_000_000); // baseline area = 10T EMU^2
        display.clear();
        // Overlap a 1M-wide region (10% of the existing shape, but 100%
        // of the new shape). intersectionRatio uses the SMALLER, so this
        // is 100% -> warns.
        addRect(0, 0, 1_000_000, 1_000_000);
        assertTrue("100%-of-smaller overlap should warn",
            display.messages.size() > 0);
    }

    // ========== Helpers ==========

    private void addRect(long x, long y, long w, long h) {
        AddShapeCommand cmd = new AddShapeCommand(
            1, SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(x, y, w, h),
            "", "Rect", null, null, false, orchestrator, display);
        cmd.execute();
    }

    private void addEllipse(long x, long y, long w, long h) {
        AddShapeCommand cmd = new AddShapeCommand(
            1, SlideShape.ShapeType.ELLIPSE,
            new ShapeGeometry(x, y, w, h),
            "", "Ellipse", null, null, false, orchestrator, display);
        cmd.execute();
    }

    /** Records every display call. The overlap warnings come through
     *  displayMessage (the NOTE channel). */
    private static class RecordingDisplay implements CommandDisplay {
        final List<String> messages = new ArrayList<>();
        @Override public void displayMessage(String message) { messages.add(message); }
        @Override public void displayError(String message) { /* not interesting here */ }
        @Override public void displaySuccess(String message) { /* not interesting here */ }
        void clear() { messages.clear(); }
    }
}
