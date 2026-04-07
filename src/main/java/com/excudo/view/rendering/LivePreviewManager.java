package com.excudo.view.rendering;

import com.excudo.xml.parsers.SlideXMLParser;
import com.excudo.core.model.ParsedSlideData;
import com.excudo.core.utils.XMLFactoryProvider;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.scene.canvas.Canvas;
import javafx.util.Duration;
import org.w3c.dom.Document;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.excudo.core.utils.Logger;
import com.excudo.core.utils.ComponentLogger;

/**
 * Manages live preview updates for slide editing.
 * Provides real-time visual feedback as LLM agents make changes to slides.
 */
public class LivePreviewManager {

    private static final ComponentLogger logger = Logger.rendering();

    private final SlideRenderer slideRenderer;
    private SlideXMLParser slideParser;
    private final ExecutorService renderingExecutor;
    
    // Properties for binding
    private final BooleanProperty rendering = new SimpleBooleanProperty(false);
    private final ObjectProperty<Document> currentSlideDocument = new SimpleObjectProperty<>();
    private final ObjectProperty<ParsedSlideData> currentSlideData = new SimpleObjectProperty<>();
    private final StringProperty statusMessage = new SimpleStringProperty("Ready");
    
    // Rendering control
    private Timeline renderDebouncer;
    private static final Duration RENDER_DELAY = Duration.millis(100);
    
    // Performance tracking
    private long lastRenderTime = 0;
    private int renderCount = 0;
    
    public LivePreviewManager(Canvas canvas) {
        this.slideRenderer = new SlideRenderer(canvas);
        try {
            this.slideParser = new SlideXMLParser();
        } catch (Exception e) {
            System.err.println("Error initializing SlideXMLParser: " + e.getMessage());
            // Create a fallback parser that will handle errors gracefully
            this.slideParser = null;
        }
        this.renderingExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("SlideRenderThread");
            return t;
        });
        
        // Setup render debouncing to avoid excessive updates
        setupRenderDebouncer();
    }
    
    // ========== LIVE PREVIEW METHODS ==========
    
    /**
     * Update preview from slide file (async)
     */
    public CompletableFuture<Void> updatePreviewFromFile(File slideFile) {
        if (slideFile == null || !slideFile.exists()) {
            updateStatus("No slide file selected");
            return CompletableFuture.completedFuture(null);
        }
        
        updateStatus("Loading slide: " + slideFile.getName());
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Parse slide XML and get the document
                Document slideDoc = XMLFactoryProvider.parseDocument(slideFile);
                
                // Parse slide data if parser is available
                // TODO: Pass extracted PPTX directory for layout parsing when available
                ParsedSlideData slideData = null;
                if (slideParser != null) {
                    slideData = slideParser.parseSlide(slideFile);
                }
                
                final ParsedSlideData finalSlideData = slideData;
                Platform.runLater(() -> {
                    currentSlideData.set(finalSlideData);
                    currentSlideDocument.set(slideDoc);
                    scheduleRender();
                });
                
                return null;
            } catch (Exception e) {
                Platform.runLater(() -> 
                    updateStatus("Error loading slide: " + e.getMessage())
                );
                throw new RuntimeException(e);
            }
        }, renderingExecutor);
    }
    
    /**
     * Update preview from XML document directly
     */
    public void updatePreviewFromDocument(Document slideDocument) {
        if (slideDocument == null) {
            updateStatus("No document to render");
            return;
        }
        
        currentSlideDocument.set(slideDocument);
        
        // Parse slide data for metadata
        CompletableFuture.supplyAsync(() -> {
            try {
                // TODO: Extract slide number and pass extracted PPTX directory for layout parsing
                ParsedSlideData slideData = null;
                if (slideParser != null) {
                    slideData = slideParser.parseSlide(slideDocument);
                }
                final ParsedSlideData finalSlideData = slideData;
                Platform.runLater(() -> {
                    currentSlideData.set(finalSlideData);
                    scheduleRender();
                });
            } catch (Exception e) {
                Platform.runLater(() -> 
                    updateStatus("Error parsing slide: " + e.getMessage())
                );
            }
            return null;
        }, renderingExecutor);
    }
    
    /**
     * Update preview from XML string
     */
    public void updatePreviewFromXML(String xmlContent) {
        if (xmlContent == null || xmlContent.trim().isEmpty()) {
            updateStatus("Empty XML content");
            return;
        }
        
        CompletableFuture.supplyAsync(() -> {
            try {
                // Parse XML string into Document
                Document doc = XMLFactoryProvider.parseDocument(xmlContent);
                
                Platform.runLater(() -> updatePreviewFromDocument(doc));
            } catch (Exception e) {
                Platform.runLater(() -> 
                    updateStatus("Error parsing XML: " + e.getMessage())
                );
            }
            return null;
        }, renderingExecutor);
    }
    
    // ========== RENDERING CONTROL ==========
    
    /**
     * Schedule a render with debouncing
     */
    private void scheduleRender() {
        renderDebouncer.stop();
        renderDebouncer.play();
    }
    
    /**
     * Perform the actual render
     */
    private void performRender() {
        Document doc = currentSlideDocument.get();
        if (doc == null) return;
        
        rendering.set(true);
        updateStatus("Rendering...");
        
        long startTime = System.currentTimeMillis();
        
        try {
            slideRenderer.renderSlide(doc);
            
            long renderTime = System.currentTimeMillis() - startTime;
            lastRenderTime = renderTime;
            renderCount++;
            
            updateStatus(String.format("Rendered: %d shapes in %dms",
                slideRenderer.getShapesRendered(),
                renderTime));
                
        } catch (Exception e) {
            updateStatus("Render error: " + e.getMessage());
            logger.error("Failed to render live preview: " + e.getMessage(), e);
        } finally {
            rendering.set(false);
        }
    }
    
    /**
     * Force immediate render (bypasses debouncing)
     */
    public void forceRender() {
        renderDebouncer.stop();
        performRender();
    }
    
    // ========== VISUAL COMPARISON ==========
    
    /**
     * Enable side-by-side comparison mode
     */
    public void enableComparisonMode(Document beforeDocument) {
        // TODO: Implement split-screen comparison
        // This would show before/after views side by side
    }
    
    /**
     * Disable comparison mode
     */
    public void disableComparisonMode() {
        // TODO: Return to single view mode
    }
    
    /**
     * Highlight changes between documents
     */
    public void highlightChanges(Document beforeDoc, Document afterDoc) {
        // TODO: Implement visual diff highlighting
        // Could highlight added/removed/modified shapes
    }
    
    // ========== CONFIGURATION ==========
    
    /**
     * Set rendering options
     */
    public void setRenderingOptions(RenderingOptions options) {
        RenderingContext ctx = slideRenderer.getRenderingContext();
        ctx.setDebugMode(options.debugMode);
        ctx.setShowBounds(options.showBounds);
        ctx.setShowSpids(options.showSpids);
        ctx.setShowGrid(options.showGrid);
    }
    
    /**
     * Get current slide renderer
     */
    public SlideRenderer getSlideRenderer() {
        return slideRenderer;
    }
    
    // ========== UTILITY METHODS ==========
    
    private void setupRenderDebouncer() {
        renderDebouncer = new Timeline(
            new KeyFrame(RENDER_DELAY, e -> performRender())
        );
    }
    
    private void updateStatus(String message) {
        Platform.runLater(() -> statusMessage.set(message));
    }
    
    /**
     * Cleanup resources
     */
    public void shutdown() {
        renderingExecutor.shutdown();
        if (renderDebouncer != null) {
            renderDebouncer.stop();
        }
    }
    
    // ========== PROPERTIES ==========
    
    public BooleanProperty renderingProperty() { return rendering; }
    public ObjectProperty<Document> currentSlideDocumentProperty() { return currentSlideDocument; }
    public ObjectProperty<ParsedSlideData> currentSlideDataProperty() { return currentSlideData; }
    public StringProperty statusMessageProperty() { return statusMessage; }
    
    // ========== RENDERING OPTIONS ==========
    
    public static class RenderingOptions {
        public boolean debugMode = false;
        public boolean showBounds = false;
        public boolean showSpids = false;
        public boolean showGrid = false;
        public double zoomLevel = 1.0;
        
        public RenderingOptions() {}
        
        public RenderingOptions withDebugMode(boolean debug) {
            this.debugMode = debug;
            return this;
        }
        
        public RenderingOptions withShowBounds(boolean show) {
            this.showBounds = show;
            return this;
        }
        
        public RenderingOptions withShowSpids(boolean show) {
            this.showSpids = show;
            return this;
        }
        
        public RenderingOptions withShowGrid(boolean show) {
            this.showGrid = show;
            return this;
        }
        
        public RenderingOptions withZoom(double zoom) {
            this.zoomLevel = zoom;
            return this;
        }
    }
}