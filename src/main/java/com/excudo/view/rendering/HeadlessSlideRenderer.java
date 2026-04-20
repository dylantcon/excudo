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

    // Render-result cache: keyed on (PPTXDocument.revision, slide#, W, H).
    // Any document mutation bumps the revision which makes every previously
    // cached entry for that document unreachable -- conservative but
    // correct. Bounded LRU so long-running sessions don't accumulate.
    // Access-order iteration so the newest entry lives at the tail.
    private static final int CACHE_CAPACITY = 32;
    private static final java.util.LinkedHashMap<String, byte[]> RENDER_CACHE =
        new java.util.LinkedHashMap<>(CACHE_CAPACITY, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(java.util.Map.Entry<String, byte[]> eldest) {
                return size() > CACHE_CAPACITY;
            }
        };
    private static final Object CACHE_LOCK = new Object();

    /** Clear the render-result cache. Useful for tests and memory release. */
    public static void clearRenderCache() {
        synchronized (CACHE_LOCK) {
            RENDER_CACHE.clear();
        }
    }

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

        // Cache lookup: skip the entire render + encode pipeline on hit.
        // The revision counter means any mutation since the last cache
        // put makes this key unreachable.
        String cacheKey = cacheKey(doc, slideNumber);
        byte[] cachedPng;
        synchronized (CACHE_LOCK) {
            cachedPng = RENDER_CACHE.get(cacheKey);
        }
        if (cachedPng != null) {
            writeBytesToFile(outputFile, cachedPng);
            if (TIMING_ENABLED) {
                logger.info("render-timing slide={} [cache hit] total={}ms",
                    slideNumber, ms(System.nanoTime() - t0));
            } else {
                logger.info("Rendered slide {} to {} (cache hit, {} bytes)",
                    slideNumber, outputFile.getName(), cachedPng.length);
            }
            return;
        }

        Document slideDom = doc.getSlideDocument(slideNumber);
        if (slideDom == null) {
            throw new IOException("Slide " + slideNumber + " not found in PPTXDocument");
        }

        long t1 = TIMING_ENABLED ? System.nanoTime() : 0;
        SlideRenderContext slideContext = buildSlideContext(doc, slideNumber, theme, clrMap,
            backgroundColorHex, masterStyles);
        long t2 = TIMING_ENABLED ? System.nanoTime() : 0;

        // Prefer the PPTXDocument's cached ParsedSlideData so SlideRenderer
        // doesn't re-run SlideXMLParser.parseSlide inside its render path.
        // Parser is injected because PPTXDocument can't import from
        // xml/parsers (that package imports core/model).
        com.excudo.core.model.ParsedSlideData parsed = doc.getParsedSlideData(slideNumber,
            (dom, n) -> new com.excudo.xml.parsers.SlideXMLParser().parseSlide(dom, n));
        BufferedImage image = parsed != null
            ? renderToBufferedImage(parsed, slideContext)
            : renderToBufferedImage(slideDom, slideContext);
        long t3 = TIMING_ENABLED ? System.nanoTime() : 0;

        // Encode PNG to bytes ONCE, then write to file AND cache the bytes
        // so the next request for this (revision, slide, w, h) tuple can
        // skip the whole pipeline above.
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        ImageIO.write(image, "png", bos);
        byte[] pngBytes = bos.toByteArray();

        synchronized (CACHE_LOCK) {
            RENDER_CACHE.put(cacheKey, pngBytes);
        }

        writeBytesToFile(outputFile, pngBytes);
        long t4 = TIMING_ENABLED ? System.nanoTime() : 0;

        if (TIMING_ENABLED) {
            logger.info("render-timing slide={} context={}ms render+snapshot={}ms encode={}ms total={}ms",
                slideNumber, ms(t2 - t1), ms(t3 - t2), ms(t4 - t3), ms(t4 - t0));
        } else {
            logger.info("Rendered slide {} to {} ({}x{})", slideNumber, outputFile.getName(), width, height);
        }
    }

    private String cacheKey(PPTXDocument doc, int slideNumber) {
        return doc.getRevision() + "|" + slideNumber + "|" + width + "|" + height;
    }

    private void writeBytesToFile(File outputFile, byte[] bytes) throws IOException {
        if (outputFile.getParentFile() != null) {
            outputFile.getParentFile().mkdirs();
        }
        java.nio.file.Files.write(outputFile.toPath(), bytes);
    }

    /**
     * Render pre-parsed slide data to a BufferedImage. Preferred over the
     * Document-accepting overload because it avoids re-parsing inside
     * SlideRenderer.renderSlide(Document).
     */
    public BufferedImage renderToBufferedImage(com.excudo.core.model.ParsedSlideData slideData,
                                                SlideRenderContext slideContext) throws IOException {
        return renderOnFxThread(slideContext, renderer -> renderer.renderSlide(slideData));
    }

    /**
     * Render a slide DOM to a BufferedImage with theme/layout context.
     * Re-parses the slide internally; prefer the ParsedSlideData overload.
     */
    public BufferedImage renderToBufferedImage(Document slideDom, SlideRenderContext slideContext) throws IOException {
        return renderOnFxThread(slideContext, renderer -> renderer.renderSlide(slideDom));
    }

    /**
     * Shared core: hop to the FX thread, allocate Canvas + SlideRenderer,
     * run the caller's paint closure, snapshot, convert, return.
     */
    private BufferedImage renderOnFxThread(SlideRenderContext slideContext,
                                            java.util.function.Consumer<SlideRenderer> paintAction)
            throws IOException {
        AtomicReference<BufferedImage> result = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
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
                paintAction.accept(renderer);
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

            // Resolve layout for this slide from parsed state. Use the
            // PPTXDocument's cached parsed state -- the underlying parse
            // walks every layout + master + theme XML part (20-50ms cold)
            // and the result is stable between mutations, so re-parsing on
            // every render is pure waste.
            com.excudo.core.model.LayoutInfo layoutInfo = null;
            com.excudo.core.model.PPTXDocumentParser.ParsedPresentationState state =
                doc.getParsedState();
            if (state != null) {
                String layoutId = state.getSlideToLayoutId().get(slideNumber);
                if (layoutId != null) {
                    layoutInfo = state.getLayouts().get(layoutId);
                }
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
