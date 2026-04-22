package com.excudo.integration;

import com.excudo.core.model.PPTXDocument;
import com.excudo.core.orchestration.PPTXOrchestratorImpl;
import com.excudo.core.orchestration.PresentationScaffolder;
import com.excudo.view.rendering.HeadlessSlideRenderer;
import org.junit.Before;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;

import static org.junit.Assert.*;

/**
 * Contact-sheet state assertions: verifies that the composite dimensions
 * match what the caller asked for (columns * thumbW + gutters, rows
 * * thumbH + gutters), that the output is a decodable PNG, and that
 * multi-call cache behavior does not corrupt the grid (a second call
 * must return the same dimensions and the same byte content when the
 * document is untouched).
 */
public class HeadlessSlideRendererContactSheetTest {

    private PPTXDocument doc;
    private PPTXOrchestratorImpl orchestrator;

    @Before
    public void setUp() throws Exception {
        doc = PresentationScaffolder.scaffoldDocument("excudo");
        orchestrator = new PPTXOrchestratorImpl();
        orchestrator.initialize(doc);
        orchestrator.createSlide(1, "First",  "slideLayout1");
        orchestrator.createSlide(2, "Second", "slideLayout2");
        orchestrator.createSlide(3, "Third",  "slideLayout2");
        doc = orchestrator.getContext().get().getDocument();
        HeadlessSlideRenderer.clearRenderCache();
    }

    @Test
    public void contactSheetMatchesRequestedGridDimensions() throws Exception {
        HeadlessSlideRenderer renderer = new HeadlessSlideRenderer(1280, 720);
        var theme = com.excudo.core.themes.ThemeLoader.get("excudo");
        var clrMap = orchestrator.getClrMap();
        var masterStyles = orchestrator.getMasterStyles();
        java.util.function.IntFunction<String> bgFn = orchestrator::getBackgroundColorHex;

        int thumbW = 320, thumbH = 180, cols = 2, gutter = 10;
        int[] slides = {1, 2, 3};
        // 3 slides, 2 cols -> 2 rows
        BufferedImage sheet = renderer.renderContactSheet(
            doc, slides, thumbW, thumbH, cols, gutter, theme, clrMap, bgFn, masterStyles);

        int rows = 2;
        int expectedW = cols * thumbW + (cols + 1) * gutter;
        int expectedH = rows * thumbH + (rows + 1) * gutter;
        assertEquals("Contact sheet width mismatch", expectedW, sheet.getWidth());
        assertEquals("Contact sheet height mismatch", expectedH, sheet.getHeight());
    }

    @Test
    public void contactSheetEncodesAsDecodablePng() throws Exception {
        HeadlessSlideRenderer renderer = new HeadlessSlideRenderer(1280, 720);
        var theme = com.excudo.core.themes.ThemeLoader.get("excudo");
        var clrMap = orchestrator.getClrMap();
        var masterStyles = orchestrator.getMasterStyles();
        java.util.function.IntFunction<String> bgFn = orchestrator::getBackgroundColorHex;

        BufferedImage sheet = renderer.renderContactSheet(
            doc, new int[]{1, 2}, 240, 135, 2, 4, theme, clrMap, bgFn, masterStyles);

        File tmp = File.createTempFile("excudo-sheet-test-", ".png");
        tmp.deleteOnExit();
        try {
            ImageIO.write(sheet, "png", tmp);
            assertTrue("PNG file should exist", tmp.exists());
            assertTrue("PNG should be non-trivial (> 100 bytes)", tmp.length() > 100);

            byte[] bytes = java.nio.file.Files.readAllBytes(tmp.toPath());
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(bytes));
            assertNotNull("PNG must be decodable", decoded);
            assertEquals("Decoded width should match in-memory", sheet.getWidth(), decoded.getWidth());
            assertEquals("Decoded height should match in-memory", sheet.getHeight(), decoded.getHeight());
        } finally {
            tmp.delete();
        }
    }

    @Test
    public void rejectsEmptySlideList() {
        HeadlessSlideRenderer renderer = new HeadlessSlideRenderer(1280, 720);
        try {
            renderer.renderContactSheet(doc, new int[0], 320, 180, 3, 8,
                null, null, null, null);
            fail("Empty slide list should throw");
        } catch (IllegalArgumentException expected) {
            assertTrue("Error should mention slideNumbers: " + expected.getMessage(),
                expected.getMessage().toLowerCase().contains("slide"));
        } catch (Exception e) {
            fail("Wrong exception type: " + e);
        }
    }

    @Test
    public void repeatCallHitsCacheAndProducesIdenticalBytes() throws Exception {
        HeadlessSlideRenderer renderer = new HeadlessSlideRenderer(1280, 720);
        var theme = com.excudo.core.themes.ThemeLoader.get("excudo");
        var clrMap = orchestrator.getClrMap();
        var masterStyles = orchestrator.getMasterStyles();
        java.util.function.IntFunction<String> bgFn = orchestrator::getBackgroundColorHex;

        int[] slides = {1, 2, 3};
        BufferedImage first  = renderer.renderContactSheet(
            doc, slides, 200, 112, 3, 6, theme, clrMap, bgFn, masterStyles);
        BufferedImage second = renderer.renderContactSheet(
            doc, slides, 200, 112, 3, 6, theme, clrMap, bgFn, masterStyles);

        assertEquals(first.getWidth(),  second.getWidth());
        assertEquals(first.getHeight(), second.getHeight());

        // Byte-for-byte PNG equality: forces cache hit to produce a
        // deterministic result. If the per-slide cache path diverged
        // across calls, scaling + compositing over fresh decodes would
        // still match pixel-for-pixel, but we compare encoded bytes to
        // catch any non-determinism in the render pipeline cheaply.
        byte[] firstPng  = encodePng(first);
        byte[] secondPng = encodePng(second);
        assertArrayEquals("Cached contact sheet should be byte-identical",
            firstPng, secondPng);
    }

    private byte[] encodePng(BufferedImage img) throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        ImageIO.write(img, "png", bos);
        return bos.toByteArray();
    }
}
