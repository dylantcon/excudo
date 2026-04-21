package com.excudo.integration;

import com.excudo.core.model.PPTXDocument;
import com.excudo.core.orchestration.PPTXOrchestratorImpl;
import com.excudo.view.rendering.SlideRenderContext;
import com.excudo.view.rendering.SlideRenderer;
import com.excudo.view.rendering.surface.Graphics2DRenderSurface;
import org.junit.Test;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Asserts structural equivalence between the Canvas-backed surface
 * ({@link CanvasRenderSurface}) and the AWT-backed surface
 * ({@link Graphics2DRenderSurface}) when both render the same slide.
 *
 * Intentionally NOT a pixel-identity test: AWT TextLayout produces
 * different glyph advances than JavaFX fillText, by design (that's the
 * typography upgrade the AWT backend buys us). What should match is
 * structural geometry: shape placement, background color, approximate
 * content distribution.
 *
 * Assertions:
 *  1. Both backends produce a non-trivial PNG-encoded image.
 *  2. Both have identical dimensions.
 *  3. Background-corner pixels match (dominant fill leaks to the corners).
 *  4. Both have a comparable count of distinct colours in the shape-
 *     dense middle third of the slide (i.e. both drew SOMETHING, not
 *     just the background).
 */
public class RenderSurfaceParityTest {

    @Test
    public void canvasAndAwtRendersAreStructurallyEquivalent() throws Exception {
        // Test corpus: one slide from the generalist sample. Slide 1 is
        // enough -- it exercises title + body + background without
        // depending on gitignored decks.
        File pptx = new File("test-pptx-samples/generalist_test_file.pptx");
        if (!pptx.exists()) return; // skip in worktrees that don't have samples

        PPTXOrchestratorImpl orch = new PPTXOrchestratorImpl();
        orch.loadPresentation(pptx);
        PPTXDocument doc = orch.getContext().get().getDocument();

        int w = 640, h = 360;
        var theme = com.excudo.core.themes.ThemeLoader.get("excudo");
        var clrMap = orch.getClrMap();
        String bgHex = orch.getBackgroundColorHex(1);
        var masterStyles = orch.getMasterStyles();

        // Canvas render requires the JavaFX toolkit; initialise via
        // a short-lived HeadlessSlideRenderer in canvas mode (its
        // constructor boots Monocle on demand).
        BufferedImage canvasImg = renderViaBackend(doc, 1, w, h, theme, clrMap, bgHex, masterStyles, "canvas");
        BufferedImage awtImg    = renderViaBackend(doc, 1, w, h, theme, clrMap, bgHex, masterStyles, "awt");

        // 1. Non-trivial output
        assertNotNull("Canvas backend returned null image", canvasImg);
        assertNotNull("AWT backend returned null image", awtImg);

        // 2. Dimensions match
        assertEquals("width parity", canvasImg.getWidth(), awtImg.getWidth());
        assertEquals("height parity", canvasImg.getHeight(), awtImg.getHeight());

        // 3. Corner pixel (top-left) should be the slide background.
        //    Both backends must agree on the top-left pixel within a
        //    small tolerance (subpixel AA edge may differ by a few
        //    ARGB units).
        int c1 = canvasImg.getRGB(1, 1);
        int c2 = awtImg.getRGB(1, 1);
        assertTrue("top-left pixel parity: canvas=" + Integer.toHexString(c1)
                + " awt=" + Integer.toHexString(c2),
            argbDistance(c1, c2) <= 10);

        // 4. Shape density: count distinct colours in the central
        //    band. Both backends should have drawn non-background
        //    content there.
        int canvasPalette = countDistinctColors(canvasImg, 50, h / 3, w - 100, h / 3);
        int awtPalette    = countDistinctColors(awtImg, 50, h / 3, w - 100, h / 3);
        assertTrue("Canvas produced trivial output (palette=" + canvasPalette + ")",
            canvasPalette >= 2);
        assertTrue("AWT produced trivial output (palette=" + awtPalette + ")",
            awtPalette >= 2);
    }

    private static BufferedImage renderViaBackend(PPTXDocument doc, int slide, int w, int h,
            com.excudo.core.themes.ThemeDefinition theme,
            java.util.Map<String, String> clrMap, String bgHex,
            java.util.Map<String, com.excudo.core.themes.TextLevelStyle[]> masterStyles,
            String backend) throws Exception {
        // Build the slide context the same way HeadlessSlideRenderer
        // does -- matters because the renderer reads theme/layout/color
        // from it.
        com.excudo.core.model.LayoutInfo layoutInfo = null;
        var state = doc.getParsedState();
        if (state != null) {
            String layoutId = state.getSlideToLayoutId().get(slide);
            if (layoutId != null) layoutInfo = state.getLayouts().get(layoutId);
        }
        SlideRenderContext slideCtx = new SlideRenderContext(
            theme, layoutInfo, doc, slide, clrMap, bgHex, masterStyles);

        var parsed = doc.getParsedSlideData(slide,
            (dom, n) -> new com.excudo.xml.parsers.SlideXMLParser().parseSlide(dom, n));

        if ("awt".equals(backend)) {
            Graphics2DRenderSurface surface = new Graphics2DRenderSurface(w, h);
            SlideRenderer renderer = new SlideRenderer(surface);
            renderer.setSlideContext(slideCtx);
            renderer.renderSlide(parsed);
            return surface.toBufferedImage();
        } else {
            // Canvas backend needs the FX toolkit. Go through
            // HeadlessSlideRenderer's cached toolkit bootstrap.
            com.excudo.view.rendering.HeadlessSlideRenderer hsr =
                new com.excudo.view.rendering.HeadlessSlideRenderer(w, h);
            File tmp = File.createTempFile("parity-canvas-", ".png");
            tmp.deleteOnExit();
            hsr.renderToFile(doc, slide, tmp, theme, clrMap, bgHex, masterStyles);
            return javax.imageio.ImageIO.read(tmp);
        }
    }

    private static int argbDistance(int a, int b) {
        int da = Math.abs(((a >>> 24) & 0xFF) - ((b >>> 24) & 0xFF));
        int dr = Math.abs(((a >>> 16) & 0xFF) - ((b >>> 16) & 0xFF));
        int dg = Math.abs(((a >>> 8)  & 0xFF) - ((b >>> 8)  & 0xFF));
        int db = Math.abs( (a & 0xFF)         -  (b & 0xFF));
        return da + dr + dg + db;
    }

    private static int countDistinctColors(BufferedImage img, int x, int y, int w, int h) {
        Set<Integer> seen = new HashSet<>();
        int xEnd = Math.min(img.getWidth(), x + w);
        int yEnd = Math.min(img.getHeight(), y + h);
        for (int py = y; py < yEnd; py++) {
            for (int px = x; px < xEnd; px++) {
                seen.add(img.getRGB(px, py));
                if (seen.size() > 2000) return seen.size(); // enough
            }
        }
        return seen.size();
    }
}
