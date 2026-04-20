package com.excudo.integration;

import com.excudo.core.model.PPTXDocument;
import com.excudo.core.orchestration.PPTXOrchestratorImpl;
import com.excudo.core.orchestration.PresentationScaffolder;
import com.excudo.core.themes.ThemeDefinition;
import com.excudo.core.themes.ThemeLoader;
import com.excudo.view.rendering.HeadlessSlideRenderer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;

import static org.junit.Assert.*;

/**
 * End-to-end verification that {@link HeadlessSlideRenderer}'s render-result
 * cache hits on identical repeats and invalidates correctly after a document
 * mutation. The cache keys on the PPTXDocument revision counter -- any put
 * to xml/media/binary parts advances it, rendering every prior entry
 * unreachable. These tests pin that invariant.
 *
 * Notes on the test scaffolding: we mirror the MCP/GUI render path and
 * supply bgHex / clrMap / masterStyles from the orchestrator rather than
 * nulls. Passing nulls makes {@link com.excudo.view.rendering.SlideRenderContext#getBackgroundColorHex()}
 * throw, which the renderer catches and paints an error state -- every
 * render then produces an identical error PNG, making cache invariants
 * vacuously true. Using real context values exercises the actual shape
 * rendering pipeline.
 */
public class HeadlessSlideRendererCacheTest {

    private File output;
    private PPTXOrchestratorImpl orch;
    private ThemeDefinition theme;

    @Before
    public void setUp() throws Exception {
        HeadlessSlideRenderer.clearRenderCache();
        output = File.createTempFile("excudo-cache-test", ".png");
        output.deleteOnExit();
        theme = ThemeLoader.get("excudo");
    }

    @After
    public void tearDown() {
        if (output != null && output.exists()) output.delete();
        HeadlessSlideRenderer.clearRenderCache();
    }

    @Test
    public void repeatedRenderUsesCachedBytes() throws Exception {
        PPTXDocument doc = buildSingleSlideDoc();
        HeadlessSlideRenderer renderer = new HeadlessSlideRenderer(640, 360);

        renderSlide(renderer, doc, 1, output);
        assertTrue(output.exists());
        byte[] firstBytes = Files.readAllBytes(output.toPath());
        assertTrue("render produced non-trivial PNG", firstBytes.length > 100);

        File second = File.createTempFile("excudo-cache-test-2", ".png");
        second.deleteOnExit();
        try {
            renderSlide(renderer, doc, 1, second);
            byte[] secondBytes = Files.readAllBytes(second.toPath());
            assertArrayEquals("cache hit should produce byte-identical output",
                firstBytes, secondBytes);
        } finally {
            second.delete();
        }
    }

    @Test
    public void mutationInvalidatesCachedRender() throws Exception {
        PPTXDocument doc = buildSingleSlideDoc();

        HeadlessSlideRenderer renderer = new HeadlessSlideRenderer(640, 360);

        renderSlide(renderer, doc, 1, output);
        byte[] beforeBytes = Files.readAllBytes(output.toPath());
        long revBefore = doc.getRevision();

        // Drop a large, distinctly-coloured rectangle on slide 1. addShape
        // returns an ExecutionResult -- if it failed silently we'd be
        // chasing a test ghost, so fail loudly on the spot.
        var shapeResult = orch.addShape(1,
            com.excudo.core.model.SlideShape.ShapeType.RECTANGLE,
            new com.excudo.core.model.ShapeGeometry(1000000, 2000000, 5000000, 2000000),
            "MUTATION-MARKER",
            "MutationMarker",
            com.excudo.core.model.ShapeStyle.withFill(
                com.excudo.core.model.ShapeFill.solid("FF00FF")));
        assertTrue("addShape must succeed: " + shapeResult.getMessage(),
            shapeResult.isSuccess());

        long revAfter = doc.getRevision();
        assertTrue("revision must advance after addShape", revAfter > revBefore);

        File second = File.createTempFile("excudo-cache-test-mut", ".png");
        second.deleteOnExit();
        try {
            renderSlide(renderer, doc, 1, second);
            byte[] afterBytes = Files.readAllBytes(second.toPath());
            assertFalse("post-mutation render must differ from cached bytes "
                    + "(before=" + beforeBytes.length + " bytes, after="
                    + afterBytes.length + " bytes)",
                java.util.Arrays.equals(beforeBytes, afterBytes));
        } finally {
            second.delete();
        }
    }

    @Test
    public void differentDimensionsAreCachedSeparately() throws Exception {
        PPTXDocument doc = buildSingleSlideDoc();

        HeadlessSlideRenderer small = new HeadlessSlideRenderer(640, 360);
        renderSlide(small, doc, 1, output);
        byte[] smallBytes = Files.readAllBytes(output.toPath());

        File large = File.createTempFile("excudo-cache-test-large", ".png");
        large.deleteOnExit();
        try {
            HeadlessSlideRenderer big = new HeadlessSlideRenderer(1280, 720);
            renderSlide(big, doc, 1, large);
            byte[] largeBytes = Files.readAllBytes(large.toPath());
            assertFalse("dimensions must be part of the cache key",
                java.util.Arrays.equals(smallBytes, largeBytes));
        } finally {
            large.delete();
        }
    }

    private PPTXDocument buildSingleSlideDoc() throws Exception {
        PPTXDocument doc = PresentationScaffolder.scaffoldDocument("excudo");
        orch = new PPTXOrchestratorImpl();
        orch.initialize(doc);
        orch.createSlide(1, "Cache Test Title", "slideLayout2");
        return orch.getContext().get().getDocument();
    }

    /**
     * Render a slide using the same context-resolution logic as the MCP and
     * GUI render paths (ToolDispatcher.handleRenderSlide, SlideEditorController).
     * Null bg/clrMap/masterStyles make SlideRenderContext throw; we catch that
     * as an error-state render, which is technically valid as a cache entry
     * but defeats the point of these tests.
     */
    private void renderSlide(HeadlessSlideRenderer renderer, PPTXDocument doc,
                             int slideNumber, File out) throws java.io.IOException {
        java.util.Map<String, String> clrMap = orch.getClrMap();
        String bgHex = orch.getBackgroundColorHex(slideNumber);
        var masterStyles = orch.getMasterStyles();
        renderer.renderToFile(doc, slideNumber, out, theme, clrMap, bgHex, masterStyles);
    }
}
