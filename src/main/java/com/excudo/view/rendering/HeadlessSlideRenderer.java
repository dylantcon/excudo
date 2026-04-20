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

    // Per-phase timing log. Enabled via EXCUDO_RENDER_TIMING=1 env var. When
    // on, each render prints the parse/context/render/snapshot/encode
    // breakdown to INFO so we can measure optimisation phases against a
    // real baseline instead of eyeballing wall-clock from outside.
    private static final boolean TIMING_ENABLED = timingEnabled();

    private static boolean timingEnabled() {
        String v = System.getenv("EXCUDO_RENDER_TIMING");
        return v != null && !v.isBlank() && !"0".equals(v) && !"false".equalsIgnoreCase(v);
    }

    private static double ms(long nanos) { return nanos / 1_000_000.0; }

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
        long t0 = TIMING_ENABLED ? System.nanoTime() : 0;
        Document slideDom = doc.getSlideDocument(slideNumber);
        if (slideDom == null) {
            throw new IOException("Slide " + slideNumber + " not found in PPTXDocument");
        }

        long t1 = TIMING_ENABLED ? System.nanoTime() : 0;
        SlideRenderContext slideContext = buildSlideContext(doc, slideNumber, theme, clrMap,
            backgroundColorHex, masterStyles);
        long t2 = TIMING_ENABLED ? System.nanoTime() : 0;

        BufferedImage image = renderToBufferedImage(slideDom, slideContext);
        long t3 = TIMING_ENABLED ? System.nanoTime() : 0;

        outputFile.getParentFile().mkdirs();
        ImageIO.write(image, "png", outputFile);
        long t4 = TIMING_ENABLED ? System.nanoTime() : 0;

        if (TIMING_ENABLED) {
            logger.info("render-timing slide={} context={}ms render+snapshot={}ms encode={}ms total={}ms",
                slideNumber, ms(t2 - t1), ms(t3 - t2), ms(t4 - t3), ms(t4 - t0));
        } else {
            logger.info("Rendered slide {} to {} ({}x{})", slideNumber, outputFile.getName(), width, height);
        }
    }

    /**
     * Render a slide DOM to a BufferedImage with theme/layout context.
     */
    public BufferedImage renderToBufferedImage(Document slideDom, SlideRenderContext slideContext) throws IOException {
        AtomicReference<BufferedImage> result = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        // JavaFX rendering must happen on the FX Application Thread
        AtomicReference<long[]> innerTimings = new AtomicReference<>();
        Platform.runLater(() -> {
            long f0 = TIMING_ENABLED ? System.nanoTime() : 0;
            try {
                Canvas canvas = new Canvas(width, height);
                SlideRenderer renderer = new SlideRenderer(canvas);
                if (slideContext != null) {
                    renderer.setSlideContext(slideContext);
                }
                long f1 = TIMING_ENABLED ? System.nanoTime() : 0;
                renderer.renderSlide(slideDom);
                long f2 = TIMING_ENABLED ? System.nanoTime() : 0;

                // Snapshot the canvas to an image
                SnapshotParameters params = new SnapshotParameters();
                params.setFill(Color.WHITE);
                WritableImage fxImage = canvas.snapshot(params, null);

                // Convert JavaFX image to AWT BufferedImage for ImageIO
                result.set(SwingFXUtils.fromFXImage(fxImage, null));
                long f3 = TIMING_ENABLED ? System.nanoTime() : 0;
                if (TIMING_ENABLED) {
                    innerTimings.set(new long[]{f1 - f0, f2 - f1, f3 - f2});
                }
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

        if (TIMING_ENABLED && innerTimings.get() != null) {
            long[] t = innerTimings.get();
            logger.info("  fx-thread: setup={}ms render={}ms snapshot+convert={}ms",
                ms(t[0]), ms(t[1]), ms(t[2]));
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
