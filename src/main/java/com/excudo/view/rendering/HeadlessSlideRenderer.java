package com.excudo.view.rendering;

import com.excudo.core.model.PPTXDocument;
import com.excudo.core.utils.Logger;
import com.excudo.core.utils.ComponentLogger;
import com.excudo.view.rendering.surface.CanvasRenderSurface;
import com.excudo.core.rendering.surface.FontSubstitutionTracker;
import com.excudo.core.rendering.surface.Graphics2DRenderSurface;
import com.excudo.core.rendering.surface.RenderSurface;
import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
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

    // Rendering backend selection. Default is "canvas" (JavaFX Canvas
    // + WritableImage snapshot + SwingFXUtils conversion -- same as
    // pre-Phase-D behaviour). Set EXCUDO_RENDER_BACKEND=awt to use the
    // Graphics2D backend which skips the FX-thread hop, writes
    // directly to BufferedImage, and uses TextLayout for sub-pixel
    // typography. Phase E flips this default to "awt".
    private static final Backend BACKEND = selectBackend();

    private enum Backend { CANVAS, AWT }

    private static Backend selectBackend() {
        String v = System.getenv("EXCUDO_RENDER_BACKEND");
        if (v != null && "awt".equalsIgnoreCase(v.trim())) return Backend.AWT;
        return Backend.CANVAS;
    }

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
    //
    // Cached value carries both the PNG bytes AND the list of font
    // substitutions observed during that render, so cache hits emit the
    // same warnings as cold renders -- consistent agent feedback.
    public record CachedRender(byte[] bytes, java.util.List<FontSubstitutionTracker.Substitution> substitutions) {}

    /** Output of a render call, including any font substitutions observed during the paint path. */
    public record RenderReport(byte[] pngBytes, java.util.List<FontSubstitutionTracker.Substitution> substitutions, boolean cacheHit) {}

    private static final int CACHE_CAPACITY = 32;
    private static final java.util.LinkedHashMap<String, CachedRender> RENDER_CACHE =
        new java.util.LinkedHashMap<>(CACHE_CAPACITY, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(java.util.Map.Entry<String, CachedRender> eldest) {
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
        // Only the Canvas backend needs the JavaFX toolkit + Monocle.
        // AWT backend runs on the calling thread with no FX dependency.
        if (BACKEND == Backend.CANVAS) {
            ensureToolkitInitialized();
        }
    }

    public HeadlessSlideRenderer() {
        this(1280, 720);
    }

    /**
     * Render a slide to a PNG file with full theme/layout/color context.
     * Discards the {@link RenderReport}; prefer {@link #renderToFileWithReport}
     * when the caller needs to surface substitution warnings.
     */
    public void renderToFile(PPTXDocument doc, int slideNumber, File outputFile,
                             com.excudo.core.themes.ThemeDefinition theme,
                             java.util.Map<String, String> clrMap,
                             String backgroundColorHex,
                             java.util.Map<String, com.excudo.core.themes.TextLevelStyle[]> masterStyles)
            throws IOException {
        renderToFileWithReport(doc, slideNumber, outputFile, theme, clrMap, backgroundColorHex, masterStyles);
    }

    /**
     * Render variant that returns the cached PNG bytes + any font
     * substitutions observed during this render. The MCP render handler
     * uses this to attach substitution warnings to its response so the
     * agent knows host-font-availability is driving a visual divergence
     * rather than a theme bug.
     */
    public RenderReport renderToFileWithReport(PPTXDocument doc, int slideNumber, File outputFile,
                             com.excudo.core.themes.ThemeDefinition theme,
                             java.util.Map<String, String> clrMap,
                             String backgroundColorHex,
                             java.util.Map<String, com.excudo.core.themes.TextLevelStyle[]> masterStyles)
            throws IOException {
        CachedRender cr = renderPngBytesCached(doc, slideNumber, theme, clrMap, backgroundColorHex, masterStyles);
        writeBytesToFile(outputFile, cr.bytes());
        boolean hit = cr == getFromCache(cacheKey(doc, slideNumber)); // best-effort post-check; informational only
        if (!TIMING_ENABLED) {
            logger.info("Rendered slide {} to {} ({}x{}, {} bytes)",
                slideNumber, outputFile.getName(), width, height, cr.bytes().length);
        }
        return new RenderReport(cr.bytes(), cr.substitutions(), hit);
    }

    /**
     * Cache-aware renderer returning PNG bytes + substitutions without
     * touching disk. Extracted so both the file-writing path and the
     * contact-sheet path share the same caching behavior.
     */
    private CachedRender renderPngBytesCached(PPTXDocument doc, int slideNumber,
                             com.excudo.core.themes.ThemeDefinition theme,
                             java.util.Map<String, String> clrMap,
                             String backgroundColorHex,
                             java.util.Map<String, com.excudo.core.themes.TextLevelStyle[]> masterStyles)
            throws IOException {
        long t0 = TIMING_ENABLED ? System.nanoTime() : 0;

        String cacheKey = cacheKey(doc, slideNumber);
        CachedRender cached;
        synchronized (CACHE_LOCK) {
            cached = RENDER_CACHE.get(cacheKey);
        }
        if (cached != null) {
            if (TIMING_ENABLED) {
                logger.info("render-timing slide={} [cache hit] total={}ms",
                    slideNumber, ms(System.nanoTime() - t0));
            }
            return cached;
        }

        Document slideDom = doc.getSlideDocument(slideNumber);
        if (slideDom == null) {
            throw new IOException("Slide " + slideNumber + " not found in PPTXDocument");
        }

        FontSubstitutionTracker.beginRender();

        long t1 = TIMING_ENABLED ? System.nanoTime() : 0;
        SlideRenderContext slideContext = buildSlideContext(doc, slideNumber, theme, clrMap,
            backgroundColorHex, masterStyles);
        long t2 = TIMING_ENABLED ? System.nanoTime() : 0;

        com.excudo.core.model.ParsedSlideData parsed = doc.getParsedSlideData(slideNumber,
            (dom, n) -> new com.excudo.xml.parsers.SlideXMLParser().parseSlide(dom, n));
        BufferedImage image = parsed != null
            ? renderToBufferedImage(parsed, slideContext)
            : renderToBufferedImage(slideDom, slideContext);
        long t3 = TIMING_ENABLED ? System.nanoTime() : 0;

        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        ImageIO.write(image, "png", bos);
        byte[] pngBytes = bos.toByteArray();

        java.util.List<FontSubstitutionTracker.Substitution> substitutions =
            FontSubstitutionTracker.drain();

        CachedRender fresh = new CachedRender(pngBytes, substitutions);
        synchronized (CACHE_LOCK) {
            RENDER_CACHE.put(cacheKey, fresh);
        }

        long t4 = TIMING_ENABLED ? System.nanoTime() : 0;
        if (TIMING_ENABLED) {
            logger.info("render-timing slide={} context={}ms render+snapshot={}ms encode={}ms total={}ms",
                slideNumber, ms(t2 - t1), ms(t3 - t2), ms(t4 - t3), ms(t4 - t0));
        }
        return fresh;
    }

    private CachedRender getFromCache(String key) {
        synchronized (CACHE_LOCK) {
            return RENDER_CACHE.get(key);
        }
    }

    /**
     * Render a subset of slides into a single contact-sheet PNG. Each slide
     * renders at the instance's (width, height) and is scaled to the
     * requested thumbnail size before composition, so text metrics and
     * layout stay faithful to the normal render path. The per-slide cache
     * applies: calling twice over the same (unmutated) slide list is ~free
     * after the first pass.
     *
     * @param doc             source document
     * @param slideNumbers    1-indexed slide numbers, in the order they should appear in the grid
     * @param thumbWidth      pixel width of each thumbnail in the grid
     * @param thumbHeight     pixel height of each thumbnail in the grid
     * @param columns         grid columns; rows derived from {@code ceil(slideNumbers.length / columns)}
     * @param gutter          pixels of transparent padding between thumbnails (0 for flush grid)
     * @return contact-sheet BufferedImage sized {@code columns*thumbWidth + (columns+1)*gutter} by
     *         {@code rows*thumbHeight + (rows+1)*gutter}
     */
    public BufferedImage renderContactSheet(PPTXDocument doc, int[] slideNumbers,
                             int thumbWidth, int thumbHeight, int columns, int gutter,
                             com.excudo.core.themes.ThemeDefinition theme,
                             java.util.Map<String, String> clrMap,
                             java.util.function.IntFunction<String> backgroundHexForSlide,
                             java.util.Map<String, com.excudo.core.themes.TextLevelStyle[]> masterStyles)
            throws IOException {
        if (slideNumbers == null || slideNumbers.length == 0) {
            throw new IllegalArgumentException("slideNumbers must contain at least one slide");
        }
        if (columns < 1) columns = 1;
        if (thumbWidth < 1 || thumbHeight < 1) {
            throw new IllegalArgumentException("thumbnail dimensions must be positive");
        }
        int rows = (slideNumbers.length + columns - 1) / columns;

        int sheetW = columns * thumbWidth + (columns + 1) * gutter;
        int sheetH = rows * thumbHeight + (rows + 1) * gutter;
        BufferedImage sheet = new BufferedImage(sheetW, sheetH, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = sheet.createGraphics();
        try {
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING,
                java.awt.RenderingHints.VALUE_RENDER_QUALITY);

            for (int i = 0; i < slideNumbers.length; i++) {
                int slideNumber = slideNumbers[i];
                String bgHex = backgroundHexForSlide != null
                    ? backgroundHexForSlide.apply(slideNumber) : null;
                CachedRender cr = renderPngBytesCached(doc, slideNumber, theme, clrMap,
                    bgHex, masterStyles);
                BufferedImage full = ImageIO.read(new java.io.ByteArrayInputStream(cr.bytes()));
                if (full == null) {
                    throw new IOException("Failed to decode cached PNG for slide " + slideNumber);
                }
                int col = i % columns;
                int row = i / columns;
                int x = gutter + col * (thumbWidth + gutter);
                int y = gutter + row * (thumbHeight + gutter);
                g.drawImage(full, x, y, thumbWidth, thumbHeight, null);
            }
        } finally {
            g.dispose();
        }
        logger.info("Rendered contact sheet: {} slide(s) in {}x{} grid, sheet {}x{}",
            slideNumbers.length, columns, rows, sheetW, sheetH);
        return sheet;
    }

    private String cacheKey(PPTXDocument doc, int slideNumber) {
        // Deck + per-slide revisions, rather than the global mutations
        // counter, so an edit to slide N leaves slide M's cached render
        // reachable. Deck revision covers theme/master/layout/media --
        // any of those invalidates every entry (correct, since they
        // affect every slide's render).
        return doc.getDeckRevision() + "|" + doc.getSlideRevision(slideNumber)
            + "|" + slideNumber + "|" + width + "|" + height;
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
        return dispatchRender(slideContext, renderer -> renderer.renderSlide(slideData));
    }

    /**
     * Render a slide DOM to a BufferedImage with theme/layout context.
     * Re-parses the slide internally; prefer the ParsedSlideData overload.
     */
    public BufferedImage renderToBufferedImage(Document slideDom, SlideRenderContext slideContext) throws IOException {
        return dispatchRender(slideContext, renderer -> renderer.renderSlide(slideDom));
    }

    /**
     * Backend dispatcher. Picks the concrete render path based on the
     * static BACKEND selection. Canvas backend hops to the FX thread and
     * snapshots through WritableImage; AWT backend runs on the calling
     * thread with direct-to-BufferedImage output.
     */
    private BufferedImage dispatchRender(SlideRenderContext slideContext,
                                          java.util.function.Consumer<SlideRenderer> paintAction)
            throws IOException {
        return BACKEND == Backend.AWT
            ? renderOnAwtBackend(slideContext, paintAction)
            : renderOnFxThread(slideContext, paintAction);
    }

    /**
     * AWT backend: allocate Graphics2DRenderSurface + SlideRenderer on
     * the calling thread, run the paint action, return the surface's
     * BufferedImage directly. No FX toolkit, no Platform.runLater, no
     * Canvas->WritableImage->BufferedImage roundtrip.
     */
    private BufferedImage renderOnAwtBackend(SlideRenderContext slideContext,
                                              java.util.function.Consumer<SlideRenderer> paintAction)
            throws IOException {
        long f0 = TIMING_ENABLED ? System.nanoTime() : 0;
        try {
            Graphics2DRenderSurface surface = new Graphics2DRenderSurface(width, height);
            SlideRenderer renderer = new SlideRenderer(surface);
            if (slideContext != null) {
                renderer.setSlideContext(slideContext);
            }
            long f1 = TIMING_ENABLED ? System.nanoTime() : 0;
            paintAction.accept(renderer);
            long f2 = TIMING_ENABLED ? System.nanoTime() : 0;

            BufferedImage result = surface.toBufferedImage();
            long f3 = TIMING_ENABLED ? System.nanoTime() : 0;
            if (TIMING_ENABLED) {
                logger.info("  awt-backend: setup={}ms render={}ms toBufferedImage={}ms",
                    ms(f1 - f0), ms(f2 - f1), ms(f3 - f2));
            }
            return result;
        } catch (Exception e) {
            throw new IOException("Rendering failed: " + e.getMessage(), e);
        }
    }

    /**
     * Canvas backend: hop to the FX thread, allocate Canvas + SlideRenderer,
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

                // Snapshot via the Canvas backend's toBufferedImage (which
                // owns the snapshot + SwingFXUtils conversion internally).
                CanvasRenderSurface surface = new CanvasRenderSurface(canvas);
                result.set(surface.toBufferedImage());
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
