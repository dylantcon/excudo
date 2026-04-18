package com.excudo.integration;

import com.excudo.core.model.PPTXDocument;
import com.excudo.view.rendering.HeadlessSlideRenderer;
import com.excudo.core.orchestration.PresentationScaffolder;
import org.junit.Test;
import static org.junit.Assert.*;

import java.io.File;

public class HeadlessSlideRendererTest {

    @Test
    public void renderScaffoldedSlideProducesPNG() throws Exception {
        // Scaffold a presentation with a slide
        PPTXDocument doc = PresentationScaffolder.scaffoldDocument("excudo");

        // Create a slide via the orchestrator path
        com.excudo.core.orchestration.PPTXOrchestratorImpl orchestrator =
            new com.excudo.core.orchestration.PPTXOrchestratorImpl();
        orchestrator.initialize(doc);
        orchestrator.createSlide(1, "Render Test", "slideLayout2");

        // Re-get the document (may have been replaced by initialize)
        doc = orchestrator.getContext().get().getDocument();

        // Render slide 1
        HeadlessSlideRenderer renderer = new HeadlessSlideRenderer(1280, 720);
        File output = File.createTempFile("excudo-render-test", ".png");
        output.deleteOnExit();

        try {
            // Edit content with nested bullets to test level differentiation
            orchestrator.editShapeText(1, 3,
                "- First bullet point with enough text to test word wrapping behavior across the placeholder width\n"
                + "- Second bullet point\n"
                + "  - Nested level 1 bullet\n"
                + "  - Another nested bullet\n"
                + "    - Deep nested level 2\n"
                + "- Third bullet point");

            // Re-get doc after edits
            doc = orchestrator.getContext().get().getDocument();

            com.excudo.core.themes.ThemeDefinition theme =
                com.excudo.core.themes.ThemeLoader.get("excudo");
            var clrMap = orchestrator.getClrMap();
            String bgHex = orchestrator.getBackgroundColorHex(1);
            var masterStyles = orchestrator.getMasterStyles();
            renderer.renderToFile(doc, 1, output, theme, clrMap, bgHex, masterStyles);

            assertTrue("PNG file should exist", output.exists());
            assertTrue("PNG file should be non-empty", output.length() > 100);

            System.out.println("Rendered slide to: " + output.getAbsolutePath()
                + " (" + output.length() + " bytes)");
        } finally {
            output.delete();
        }
    }
}
