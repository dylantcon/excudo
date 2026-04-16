package com.excudo.view.rendering;

import com.excudo.core.model.PPTXDocument;
import com.excudo.core.utils.Logger;
import com.excudo.core.utils.ComponentLogger;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import org.w3c.dom.Document;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Headless slide renderer that produces PNG images from PPTXDocument slide DOMs.
 * Works without a visible GUI window via Monocle headless JavaFX.
 *
 * Usage:
 *   HeadlessSlideRenderer renderer = new HeadlessSlideRenderer(1280, 720);
 *   renderer.renderToFile(pptxDoc, 1, new File("slide1.png"));
 */
public class HeadlessSlideRenderer {

    private static final ComponentLogger logger = Logger.getLogger(HeadlessSlideRenderer.class);
    private static boolean toolkitInitialized = false;

    private final int width;
    private final int height;

    public HeadlessSlideRenderer(int width, int height) {
        this.width = width;
        this.height = height;
        ensureToolkitInitialized();
    }

    public HeadlessSlideRenderer() {
        this(1280, 720);
    }

    /**
     * Render a slide to a PNG file with full theme/layout/color context.
     */
    public void renderToFile(PPTXDocument doc, int slideNumber, File outputFile,
                             com.excudo.core.themes.ThemeDefinition theme,
                             java.util.Map<String, String> clrMap,
                             String backgroundColorHex,
                             java.util.Map<String, com.excudo.core.themes.TextLevelStyle[]> masterStyles)
            throws IOException {
        Document slideDom = doc.getSlideDocument(slideNumber);
        if (slideDom == null) {
            throw new IOException("Slide " + slideNumber + " not found in PPTXDocument");
        }

        SlideRenderContext slideContext = buildSlideContext(doc, slideNumber, theme, clrMap,
            backgroundColorHex, masterStyles);
        BufferedImage image = renderToBufferedImage(slideDom, slideContext);
        outputFile.getParentFile().mkdirs();
        ImageIO.write(image, "png", outputFile);
        logger.info("Rendered slide {} to {} ({}x{})", slideNumber, outputFile.getName(), width, height);
    }

    /**
     * Render a slide DOM to a BufferedImage with theme/layout context.
     */
    public BufferedImage renderToBufferedImage(Document slideDom, SlideRenderContext slideContext) throws IOException {
        AtomicReference<BufferedImage> result = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        // JavaFX rendering must happen on the FX Application Thread
        Platform.runLater(() -> {
            try {
                Canvas canvas = new Canvas(width, height);
                SlideRenderer renderer = new SlideRenderer(canvas);
                if (slideContext != null) {
                    renderer.setSlideContext(slideContext);
                }
                renderer.renderSlide(slideDom);

                // Snapshot the canvas to an image
                SnapshotParameters params = new SnapshotParameters();
                params.setFill(Color.WHITE);
                WritableImage fxImage = canvas.snapshot(params, null);

                // Convert JavaFX image to AWT BufferedImage for ImageIO
                result.set(SwingFXUtils.fromFXImage(fxImage, null));
            } catch (Exception e) {
                error.set(e);
            } finally {
                latch.countDown();
            }
        });

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Rendering interrupted", e);
        }

        if (error.get() != null) {
            throw new IOException("Rendering failed: " + error.get().getMessage(), error.get());
        }

        return result.get();
    }

    private SlideRenderContext buildSlideContext(PPTXDocument doc, int slideNumber,
                                                 com.excudo.core.themes.ThemeDefinition explicitTheme,
                                                 java.util.Map<String, String> clrMap,
                                                 String backgroundColorHex,
                                                 java.util.Map<String, com.excudo.core.themes.TextLevelStyle[]> masterStyles) {
        try {
            // ThemeDefinition provides text-level styles (bullet chars, font sizes, margins).
            // Color resolution goes through ThemeManager + clrMap instead.
            com.excudo.core.themes.ThemeDefinition theme = explicitTheme;
            if (theme == null) {
                var all = com.excudo.core.themes.ThemeManager.getAvailableThemes();
                if (!all.isEmpty()) theme = all.get(0);
            }

            // Resolve layout for this slide from parsed state
            com.excudo.core.model.LayoutInfo layoutInfo = null;
            try {
                com.excudo.core.model.PPTXDocumentParser.ParsedPresentationState state =
                    com.excudo.core.model.PPTXDocumentParser.parse(doc);
                String layoutId = state.getSlideToLayoutId().get(slideNumber);
                if (layoutId != null) {
                    layoutInfo = state.getLayouts().get(layoutId);
                }
            } catch (Exception e) {
                logger.debug("Could not resolve layout for slide {}: {}", slideNumber, e.getMessage());
            }

            return new SlideRenderContext(theme, layoutInfo, doc, slideNumber, clrMap,
                backgroundColorHex, masterStyles);
        } catch (Exception e) {
            logger.warn("Failed to build slide context: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Initialize the JavaFX toolkit for headless rendering.
     * Safe to call multiple times -- only initializes once.
     */
    private static synchronized void ensureToolkitInitialized() {
        if (toolkitInitialized) return;

        // Set headless properties if not already set
        if (System.getProperty("glass.platform") == null) {
            System.setProperty("glass.platform", "Monocle");
            System.setProperty("monocle.platform", "Headless");
            System.setProperty("prism.order", "sw");
            System.setProperty("java.awt.headless", "true");
        }

        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException e) {
            // Toolkit already initialized (running inside a JavaFX app)
        }

        // Keep the FX thread alive
        Platform.setImplicitExit(false);
        toolkitInitialized = true;
        logger.debug("JavaFX toolkit initialized for headless rendering");
    }
}
