package com.excudo.core.llm;

import com.excudo.core.commands.CommandFactory;
import com.excudo.core.commands.CommandInvoker;
import com.excudo.core.metrics.TextBodyExtractor;
import com.excudo.core.model.PPTXDocument;
import com.excudo.core.model.ParsedSlideData;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.SlideShape;
import com.excudo.core.orchestration.PPTXOrchestratorImpl;
import com.excudo.core.orchestration.PresentationScaffolder;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Pins the create_code_box width semantics: auto-size from content by
 * default, honor explicit width when passed. Beta report flagged that
 * agents creating two-column layouts couldn't widen short snippets to
 * fill their column without falling back to a follow-up resize.
 */
public class CompoundShapeToolsCodeBoxWidthTest {

    private CompoundShapeTools tools;
    private PPTXOrchestratorImpl orchestrator;
    private static final long EMU_PER_INCH = 914400L;

    @Before
    public void setUp() throws Exception {
        PPTXDocument doc = PresentationScaffolder.scaffoldDocument("excudo");
        orchestrator = new PPTXOrchestratorImpl();
        orchestrator.initialize(doc);
        orchestrator.createSlide(1, "Code Test", "slideLayout2");
        tools = new CompoundShapeTools(orchestrator);
    }

    @Test
    public void autoWidthShrinkWrapsToContent() {
        // Default behavior: no width passed, code box auto-sizes to content.
        // Two-line short snippet should produce a narrow box (well under 6 inches).
        String result = tools.createCodeBox(
            "{\"slideNumber\":1,\"code\":\"x = 1\\ny = 2\",\"language\":\"python\"}");
        assertTrue("Creation should succeed: " + result,
            result.toLowerCase().startsWith("created code box"));

        long groupWidth = totalCodeBoxWidth(1);
        // Short snippet should be visibly narrower than a typical layout column.
        assertTrue("Auto-size on short content should produce a narrow box, got " + groupWidth + " EMU",
            groupWidth < 4 * EMU_PER_INCH);
    }

    @Test
    public void explicitWidthOverridesAutoSize() {
        // Pass an explicit 8-inch width to widen a short snippet.
        long explicitWidth = 8L * EMU_PER_INCH;
        String result = tools.createCodeBox(
            "{\"slideNumber\":1,\"code\":\"hi\",\"language\":\"python\",\"width\":" + explicitWidth + "}");
        assertTrue("Creation should succeed: " + result,
            result.toLowerCase().startsWith("created code box"));

        long groupWidth = totalCodeBoxWidth(1);
        // The total width (line numbers + code panel) should match the
        // explicit value within a small tolerance for inset rounding.
        assertEquals("Explicit width should be honored", explicitWidth, groupWidth);
    }

    @Test
    public void absurdlySmallExplicitWidthFallsBackToAutoSize() {
        // If the requested width can't fit the line-number gutter plus
        // any code, the implementation falls back to auto-size to avoid
        // negative-width children. Specify a width smaller than the
        // gutter.
        String result = tools.createCodeBox(
            "{\"slideNumber\":1,\"code\":\"x = 1\\ny = 2\\nz = 3\",\"language\":\"python\",\"width\":1000}");
        assertTrue("Creation should still succeed (fall back to auto): " + result,
            result.toLowerCase().startsWith("created code box"));

        long groupWidth = totalCodeBoxWidth(1);
        assertTrue("Fallback width should be at least the line-number gutter, got " + groupWidth,
            groupWidth > 1000);
    }

    /**
     * Sum the widths of the LineNumbers + Code rectangles to get the
     * effective code box width. The CompoundShapeTools API creates the
     * two child rectangles and groups them; reading the group's own
     * geometry would also work but the children are more directly
     * comparable to the auto-sized vs explicit-width assertions.
     */
    private long totalCodeBoxWidth(int slideNumber) {
        try {
            ParsedSlideData slideData = orchestrator.getSlideData(slideNumber).getData().orElseThrow();
            List<SlideShape> shapes = slideData.getShapeRegistry().getAllShapes();
            long sum = 0;
            for (SlideShape s : shapes) {
                if ("LineNumbers".equals(s.getName()) || "Code".equals(s.getName())) {
                    ShapeGeometry g = s.getGeometry();
                    if (g != null) sum += g.getWidth();
                }
            }
            return sum;
        } catch (Exception e) {
            throw new RuntimeException("Failed to read shapes on slide " + slideNumber, e);
        }
    }
}
