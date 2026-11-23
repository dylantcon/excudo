package com.excudo.core.introspection;

import com.excudo.core.metrics.LayoutIssue;
import com.excudo.core.model.PPTXDocument;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.ShapeStyle;
import com.excudo.core.model.SlideShape;
import com.excudo.core.orchestration.PPTXOrchestratorImpl;
import com.excudo.core.orchestration.PresentationScaffolder;
import com.excudo.core.themes.BundledThemes;
import com.excudo.xml.parsers.SlideXMLParser;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Shapes added with default styling are readable on every bundled theme.
 * New text was given lt1, the theme's light colour, and text shapes an lt1
 * fill: white on white on light themes, light on light in a filled shape on
 * dark ones (Excudo).
 */
public class DefaultTextContrastTest {

    @Test
    public void defaultShapesAreReadableOnEveryBundledTheme() throws Exception {
        List<String> problems = new ArrayList<>();
        for (String theme : BundledThemes.getAvailableIds()) {
            PPTXOrchestratorImpl orchestrator = new PPTXOrchestratorImpl();
            orchestrator.initialize(PresentationScaffolder.scaffoldDocument(theme));
            orchestrator.createSlide(1, "Defaults", "slideLayout7");
            PPTXDocument doc = orchestrator.getContext().get().getDocument();

            add(orchestrator, SlideShape.ShapeType.RECTANGLE, ShapeStyle.defaultStyle(), 0);
            add(orchestrator, SlideShape.ShapeType.ROUNDED_RECTANGLE, ShapeStyle.defaultStyle(), 1_000_000);
            add(orchestrator, SlideShape.ShapeType.RECTANGLE, ShapeStyle.textBox(), 2_000_000);

            List<SlideShape> shapes = doc.getParsedSlideData(1, (dom, n) -> new SlideXMLParser().parseSlide(dom, n))
                .getShapeRegistry().getAllShapes();
            for (LayoutIssue issue : ContrastChecker.check(doc, 1, shapes, orchestrator.getClrMap())) {
                if (issue.getType() == LayoutIssue.Type.LOW_CONTRAST) problems.add(theme + ": " + issue.getDescription());
            }
        }
        assertEquals(String.join("\n", problems), 0, problems.size());
    }

    private static void add(PPTXOrchestratorImpl o, SlideShape.ShapeType type, ShapeStyle style, long y) {
        assertTrue(o.addShape(1, type, new ShapeGeometry(500_000, y + 500_000, 3_000_000, 600_000),
            "Default text", "Shape", style).isSuccess());
    }
}
