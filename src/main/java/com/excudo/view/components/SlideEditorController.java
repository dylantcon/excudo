package com.excudo.view.components;

import com.excudo.view.MainController;
import com.excudo.view.rendering.RenderingContext;
import com.excudo.view.rendering.SlideRenderer;
import com.excudo.view.rendering.SlideRenderContext;
import com.excudo.view.rendering.CoordinateMapper;
import com.excudo.view.rendering.LivePreviewManager;
import com.excudo.view.animation.AnimationController;
import com.excudo.view.animation.AnimationControllerListener;
import com.excudo.core.model.LayoutInfo;
import com.excudo.core.model.PPTXDocument;
import com.excudo.core.model.ParsedSlideData;
import com.excudo.core.themes.ThemeDefinition;
import com.excudo.core.themes.ThemeManager;
import com.excudo.xml.parsers.SlideXMLParser;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.concurrent.Task;
import javafx.application.Platform;
import com.excudo.core.utils.XMLFactoryProvider;
import org.w3c.dom.Document;
import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;
import com.excudo.core.utils.Logger;
import com.excudo.core.utils.ComponentLogger;

/**
 * Controller for the slide preview pane.
 * Shows the currently-selected slide as a rendered canvas with zoom and
 * animation playback controls. Raw XML editing has been removed.
 */
public class SlideEditorController implements Initializable {

    private static final ComponentLogger logger = Logger.rendering();

    // ========== FXML COMPONENTS ==========

    @FXML private VBox canvasPane;

    // Canvas components
    @FXML private Canvas slideCanvas;
    @FXML private ScrollPane canvasScrollPane;
    @FXML private HBox canvasToolbar;
    @FXML private Button zoomInButton;
    @FXML private Button zoomOutButton;
    @FXML private Button zoomFitButton;
    @FXML private Label zoomLabel;
    @FXML private Button constructButton;
    
    // Animation controls
    @FXML private HBox animationToolbar;
    @FXML private Button playAnimationsButton;
    @FXML private Button nextTriggerButton;
    @FXML private Button pauseAnimationsButton;
    @FXML private Button stopAnimationsButton;
    @FXML private Button resetAnimationsButton;
    @FXML private CheckBox autoAdvanceCheckBox;
    @FXML private Label animationStatusLabel;
    @FXML private ProgressBar animationProgressBar;
    
    // ========== STATE ==========
    
    private MainController mainController;
    private SlideRenderer slideRenderer;
    private RenderingContext renderingContext;
    private AnimationController animationController;
    private LivePreviewManager livePreviewManager;
    private Document currentSlideDocument;
    private ParsedSlideData currentSlideData;
    private int currentSlideNumber = -1;
    private double zoomLevel = 1.0;
    
    // View options
    private boolean showGrid = false;
    private boolean showBounds = false;
    private boolean showSpids = false;
    private boolean debugMode = false;
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupComponents();
        setupEventHandlers();
        setupCanvas();
        setupInitialState();
    }
    
    // ========== INITIALIZATION ==========
    
    /**
     * Set reference to main controller
     */
    public void setMainController(MainController mainController) {
        this.mainController = mainController;
        
        // Try to configure slideLayout directory now that we have the main controller
        configureSlideLayoutDirectory();
        configureSlideLayoutDirectoryForLivePreview();
    }
    
    /**
     * Public method to reconfigure slideLayout directory when PPTX context becomes available
     */
    public void updateSlideLayoutConfiguration() {
        configureSlideLayoutDirectory();
        configureSlideLayoutDirectoryForLivePreview();
    }
    
    /**
     * Set the canvas scroll pane component from FXML
     */
    public void setCanvasScrollPane(ScrollPane canvasScrollPane) {
        this.canvasScrollPane = canvasScrollPane;
        if (canvasScrollPane != null) {
            canvasScrollPane.setPannable(true);
            canvasScrollPane.setFitToWidth(true);
            canvasScrollPane.setFitToHeight(true);
            
            // If we don't have a canvas yet, create one and add it to the scroll pane
            if (slideCanvas == null) {
                setupCanvas();
            }
            
            System.out.println("Canvas ScrollPane successfully configured");
        } else {
            System.err.println("ERROR: Canvas ScrollPane not injected via FXML - visual preview will not be available");
        }
    }
    
    private void setupComponents() {
        // Configure canvas scroll pane
        if (canvasScrollPane != null) {
            canvasScrollPane.setPannable(true);
            canvasScrollPane.setFitToWidth(true);
            canvasScrollPane.setFitToHeight(true);
        }
    }
    
    private void setupEventHandlers() {
        // Canvas toolbar handlers
        if (zoomInButton != null) {
            zoomInButton.setOnAction(e -> handleZoomIn());
        }
        if (zoomOutButton != null) {
            zoomOutButton.setOnAction(e -> handleZoomOut());
        }
        if (zoomFitButton != null) {
            zoomFitButton.setOnAction(e -> handleZoomFit());
        }
        if (constructButton != null) {
            constructButton.setOnAction(e -> handleConstructSlide());
        }
        
        // Animation control handlers
        if (playAnimationsButton != null) {
            playAnimationsButton.setOnAction(e -> handlePlayAnimations());
        }
        if (nextTriggerButton != null) {
            nextTriggerButton.setOnAction(e -> handleNextTrigger());
        }
        if (pauseAnimationsButton != null) {
            pauseAnimationsButton.setOnAction(e -> handlePauseAnimations());
        }
        if (stopAnimationsButton != null) {
            stopAnimationsButton.setOnAction(e -> handleStopAnimations());
        }
        if (resetAnimationsButton != null) {
            resetAnimationsButton.setOnAction(e -> handleResetAnimations());
        }
        if (autoAdvanceCheckBox != null) {
            autoAdvanceCheckBox.setOnAction(e -> handleAutoAdvanceToggle());
        }
        
        // Canvas mouse events for shape selection
        if (slideCanvas != null) {
            slideCanvas.setOnMouseClicked(e -> handleCanvasClick(e.getX(), e.getY()));
        }
    }
    
    private void setupCanvas() {
        // Validate that required components are available
        if (canvasScrollPane == null) {
            System.err.println("ERROR: Canvas ScrollPane not injected via FXML - visual preview will not be available");
            return;
        }
        
        // Create canvas if not already created
        if (slideCanvas == null) {
            // Set initial canvas size (PowerPoint slide dimensions)
            double slideWidth = 720; // 10 inches at 72 DPI
            double slideHeight = 540; // 7.5 inches at 72 DPI
            
            slideCanvas = new Canvas(slideWidth, slideHeight);
            
            // Add canvas to scroll pane
            canvasScrollPane.setContent(slideCanvas);
            
            // Set up mouse events
            slideCanvas.setOnMouseClicked(e -> handleCanvasClick(e.getX(), e.getY()));
            
            System.out.println("Canvas successfully created and added to ScrollPane");
        }
        
        if (slideCanvas != null) {
            // Initialize rendering components
            slideRenderer = new SlideRenderer(slideCanvas);
            renderingContext = new RenderingContext(
                slideCanvas.getGraphicsContext2D(),
                new CoordinateMapper(slideCanvas.getWidth(), slideCanvas.getHeight())
            );
            
            // Configure slideLayout directory for bullet inheritance if available
            configureSlideLayoutDirectory();
            
            // Initialize animation controller with shared coordinate mapper
            animationController = new AnimationController(renderingContext.getCoordinateMapper());
            
            // Initialize live preview manager
            livePreviewManager = new LivePreviewManager(slideCanvas);

            // Configure slideLayout directory for live preview manager as well
            configureSlideLayoutDirectoryForLivePreview();
            
            // Set up animation listener
            animationController.addListener(new AnimationControllerListener() {
                @Override
                public void onAnimationsLoaded(ParsedSlideData slideData) {
                    updateAnimationStatus("Animations loaded: " + slideData.getAnimationBindings().size() + " bindings");
                }
                
                @Override
                public void onSequencesBuilt(int sequenceCount) {
                    updateAnimationStatus("Ready: " + sequenceCount + " click triggers");
                    updateAnimationProgress(0, sequenceCount);
                }
                
                @Override
                public void onPlaybackStarted() {
                    updateAnimationStatus("Playing animations...");
                    updateAnimationControls(true, false);
                }
                
                @Override
                public void onPlaybackPaused() {
                    updateAnimationStatus("Paused");
                    updateAnimationControls(false, true);
                }
                
                @Override
                public void onPlaybackResumed() {
                    updateAnimationStatus("Playing animations...");
                    updateAnimationControls(true, false);
                }
                
                @Override
                public void onPlaybackStopped() {
                    updateAnimationStatus("Stopped");
                    updateAnimationControls(false, false);
                    updateAnimationProgress(0, animationController.getAnimationStatus().getTotalTriggers());
                }
                
                @Override
                public void onSequencePlayed(int triggerIndex) {
                    updateAnimationStatus("Playing trigger " + (triggerIndex + 1));
                    updateAnimationProgress(triggerIndex + 1, animationController.getAnimationStatus().getTotalTriggers());
                }
                
                @Override
                public void onAnimationCompleted(int spid, boolean isEntrance) {
                    // Optional: Show shape-specific completion feedback
                }
                
                @Override
                public void onAutoAdvanceChanged(boolean enabled) {
                    if (autoAdvanceCheckBox != null) {
                        autoAdvanceCheckBox.setSelected(enabled);
                    }
                }
            });
            
            // Set initial zoom
            updateZoom();
        }
    }
    
    private void setupInitialState() {
        updateButtonStates();
        updateZoomLabel();

        // Clear canvas
        clearCanvas();
    }
    
    /**
     * Build a SlideRenderContext for the current slide using the already-parsed
     * state from OrchestrationContext. No redundant parsing -- the orchestrator
     * already holds the layout map and slide-to-layout mapping.
     */
    private SlideRenderContext buildSlideRenderContext(PPTXDocument pptxDoc, int slideNumber) {
        if (pptxDoc == null || mainController == null) return null;

        try {
            var orchestrator = mainController.getOrchestrator();
            if (orchestrator == null) return null;
            var ctxOpt = orchestrator.getContext();
            if (ctxOpt.isEmpty()) return null;
            var ctx = ctxOpt.get();

            // Resolve theme from ThemeManager (already initialized by orchestrator)
            ThemeDefinition theme = null;
            String themeName = ThemeManager.getCurrentThemeName();
            if (themeName != null && !themeName.contains("No Theme")) {
                for (ThemeDefinition t : ThemeManager.getAvailableThemes()) {
                    if (t.getDisplayName().equalsIgnoreCase(themeName)
                        || t.getId().equalsIgnoreCase(themeName)) {
                        theme = t;
                        break;
                    }
                }
                if (theme == null) {
                    var all = ThemeManager.getAvailableThemes();
                    if (!all.isEmpty()) theme = all.get(0);
                }
            }

            // Resolve layout from the already-parsed presentation state
            LayoutInfo layoutInfo = null;
            var parsedState = ctx.getParsedState();
            if (parsedState != null) {
                String layoutId = parsedState.getSlideToLayoutId().get(slideNumber);
                if (layoutId != null) {
                    layoutInfo = parsedState.getLayouts().get(layoutId);
                }
            }

            return new SlideRenderContext(theme, layoutInfo, pptxDoc, slideNumber);
        } catch (Exception e) {
            logger.warn("Failed to build slide render context: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Configure slideLayout directory for slideRenderer bullet inheritance
     */
    private void configureSlideLayoutDirectory() {
        if (slideRenderer != null && mainController != null && mainController.getOrchestrator() != null) {
            try {
                var context = mainController.getOrchestrator().getContext();
                
                if (context.isPresent()) {
                    // Layout data now comes through SlideRenderContext, no directory needed
                }
            } catch (Exception e) {
                logger.debug("Slide renderer context setup: " + e.getMessage());
            }
        }
    }
    
    /**
     * Configure slideLayout directory for livePreviewManager bullet inheritance
     */
    private void configureSlideLayoutDirectoryForLivePreview() {
        // Layout data now comes through SlideRenderContext -- no directory config needed
    }
    
    // ========== SLIDE MANAGEMENT ==========
    
    /**
     * Load slide for editing
     */
    public void loadSlide(int slideNumber) {
        this.currentSlideNumber = slideNumber;

        // Configure slideLayout directory now that orchestrator context is available
        updateSlideLayoutConfiguration();

        // Load slide XML through orchestrator
        if (mainController != null && mainController.getOrchestrator() != null) {
            Task<Void> loadTask = new Task<Void>() {
                private Document slideDoc;
                private PPTXDocument pptxDoc;

                @Override
                protected Void call() throws Exception {
                    var orchestrator = mainController.getOrchestrator();
                    var context = orchestrator.getContext();

                    if (context.isPresent()) {
                        pptxDoc = context.get().getDocument();
                        slideDoc = (pptxDoc != null && pptxDoc.hasSlide(slideNumber))
                            ? pptxDoc.getSlideDocument(slideNumber) : null;

                        if (slideDoc != null) {
                            return null;
                        }
                        // Legacy disk fallback
                        File extractedDirSE = (pptxDoc != null) ? pptxDoc.getExtractedDir() : null;
                        File slideFile = (extractedDirSE != null)
                            ? new File(extractedDirSE, "ppt/slides/slide" + slideNumber + ".xml") : null;

                        if (slideFile != null && slideFile.exists()) {
                            slideDoc = XMLFactoryProvider.parseDocument(slideFile);
                            return null;
                        }
                    }

                    // Fall back to sample if no real slide available
                    slideDoc = createSampleSlideDocument();
                    pptxDoc = null;
                    return null;
                }

                @Override
                protected void succeeded() {
                    Platform.runLater(() -> {
                        if (slideDoc != null) {
                            loadSlideDocument(slideDoc, pptxDoc, slideNumber);
                        }
                    });
                }

                @Override
                protected void failed() {
                    logger.warn("Failed to load slide {}: {}", slideNumber,
                        getException() != null ? getException().getMessage() : "unknown");
                }
            };

            runBackgroundTask(loadTask);
        } else {
            // Load placeholder slide
            loadSlideDocument(createSampleSlideDocument());
        }
    }
    
    /**
     * Load slide document into editor
     */
    private void loadSlideDocument(Document slideDocument) {
        loadSlideDocument(slideDocument, null, -1);
    }

    private void loadSlideDocument(Document slideDocument, PPTXDocument pptxDoc, int slideNumber) {
        this.currentSlideDocument = slideDocument;

        // Build SlideRenderContext so the renderer has theme, layout, and color info
        SlideRenderContext slideCtx = buildSlideRenderContext(pptxDoc, slideNumber);
        if (slideRenderer != null) {
            slideRenderer.setSlideContext(slideCtx);
        }

        // Parse slide data for animations
        try {
            SlideXMLParser parser = new SlideXMLParser();
            currentSlideData = parser.parseSlide(slideDocument, slideNumber);

            // Load animations into controller
            if (animationController != null && currentSlideData != null) {
                javafx.scene.Group mockSceneGraph = new javafx.scene.Group();
                animationController.loadSlideAnimations(currentSlideData, mockSceneGraph);
            }
        } catch (Exception e) {
            logger.warn("Error parsing slide data: {}", e.getMessage());
            updateAnimationStatus("Error loading animations: " + e.getMessage());
        }

        // Render slide on canvas
        renderSlide();

        // Update UI state
        updateButtonStates();
    }

    /**
     * Render current slide on canvas
     */
    private void renderSlide() {
        System.err.println("[PREVIEW-DIAG] renderSlide() called: renderer=" + (slideRenderer != null)
            + " ctx=" + (renderingContext != null) + " doc=" + (currentSlideDocument != null)
            + " canvas=" + (slideCanvas != null));
        if (slideRenderer != null && renderingContext != null && currentSlideDocument != null) {
            try {
                // Clear canvas
                clearCanvas();

                // Update rendering context with current settings
                renderingContext.setShowGrid(showGrid);
                renderingContext.setShowBounds(showBounds);
                renderingContext.setShowSpids(showSpids);
                renderingContext.setDebugMode(debugMode);

                // Render slide
                slideRenderer.renderSlide(currentSlideDocument);

                System.err.println("[PREVIEW-DIAG] renderSlide() complete: "
                    + slideRenderer.getShapesRendered() + " shapes rendered, "
                    + slideRenderer.getTotalShapes() + " total, "
                    + slideRenderer.getCulledShapes() + " culled"
                    + " canvas=" + slideCanvas.getWidth() + "x" + slideCanvas.getHeight());

            } catch (Exception e) {
                System.err.println("[PREVIEW-DIAG] renderSlide() ERROR: " + e.getMessage());
                e.printStackTrace(System.err);
                GraphicsContext gc = slideCanvas.getGraphicsContext2D();
                gc.setFill(Color.RED);
                gc.fillText("Rendering Error: " + e.getMessage(), 10, 30);
            }
        }
    }
    
    // ========== EVENT HANDLERS ==========

    @FXML
    public void handleZoomIn() {
        zoomLevel = Math.min(zoomLevel * 1.25, 5.0);
        updateZoom();
    }
    
    @FXML
    public void handleZoomOut() {
        zoomLevel = Math.max(zoomLevel / 1.25, 0.1);
        updateZoom();
    }
    
    @FXML
    public void handleZoomFit() {
        if (slideCanvas != null && canvasScrollPane != null) {
            double scrollWidth = canvasScrollPane.getViewportBounds().getWidth();
            double scrollHeight = canvasScrollPane.getViewportBounds().getHeight();
            
            double scaleX = scrollWidth / slideCanvas.getWidth();
            double scaleY = scrollHeight / slideCanvas.getHeight();
            
            zoomLevel = Math.min(scaleX, scaleY) * 0.9; // 90% to leave some margin
            updateZoom();
        }
    }
    
    /**
     * Zoom to actual size (100% scale)
     */
    @FXML
    public void handleZoomActualSize() {
        zoomLevel = 1.0;
        updateZoom();
    }
    
    /**
     * Enhanced zoom fit with configurable margins
     */
    public void zoomFitWithMargin(double marginPercentage) {
        if (slideCanvas != null && canvasScrollPane != null) {
            double scrollWidth = canvasScrollPane.getViewportBounds().getWidth();
            double scrollHeight = canvasScrollPane.getViewportBounds().getHeight();
            
            double scaleX = scrollWidth / slideCanvas.getWidth();
            double scaleY = scrollHeight / slideCanvas.getHeight();
            
            double marginFactor = 1.0 - (marginPercentage / 100.0);
            zoomLevel = Math.min(scaleX, scaleY) * marginFactor;
            updateZoom();
        }
    }
    
    /**
     * Zoom to a specific coordinate with the given zoom level
     */
    public void zoomToCoordinate(double x, double y, double targetZoomLevel) {
        if (slideCanvas != null && canvasScrollPane != null && renderingContext != null) {
            // Set the zoom level
            zoomLevel = Math.max(0.1, Math.min(targetZoomLevel, 5.0));
            updateZoom();
            
            // Calculate the scroll position to center on the coordinate
            double canvasX = x * zoomLevel;
            double canvasY = y * zoomLevel;
            
            double viewportWidth = canvasScrollPane.getViewportBounds().getWidth();
            double viewportHeight = canvasScrollPane.getViewportBounds().getHeight();
            
            double scrollX = Math.max(0, canvasX - viewportWidth / 2);
            double scrollY = Math.max(0, canvasY - viewportHeight / 2);
            
            // Normalize scroll values
            double maxScrollX = Math.max(0, slideCanvas.getWidth() * zoomLevel - viewportWidth);
            double maxScrollY = Math.max(0, slideCanvas.getHeight() * zoomLevel - viewportHeight);
            
            if (maxScrollX > 0) {
                canvasScrollPane.setHvalue(Math.min(1.0, scrollX / maxScrollX));
            }
            if (maxScrollY > 0) {
                canvasScrollPane.setVvalue(Math.min(1.0, scrollY / maxScrollY));
            }
        }
    }
    
    /**
     * Zoom to fit a specific rectangular area
     */
    public void zoomToArea(double x, double y, double width, double height) {
        if (slideCanvas != null && canvasScrollPane != null) {
            double viewportWidth = canvasScrollPane.getViewportBounds().getWidth();
            double viewportHeight = canvasScrollPane.getViewportBounds().getHeight();
            
            // Calculate zoom level to fit the area
            double scaleX = viewportWidth / width;
            double scaleY = viewportHeight / height;
            double targetZoom = Math.min(scaleX, scaleY) * 0.8; // 80% margin for better visibility
            
            // Center on the area
            double centerX = x + width / 2;
            double centerY = y + height / 2;
            
            zoomToCoordinate(centerX, centerY, targetZoom);
        }
    }
    
    /**
     * Zoom to fit selected shapes (requires shape selection functionality)
     */
    public void zoomToSelection(java.util.List<Integer> selectedSpids) {
        if (selectedSpids == null || selectedSpids.isEmpty() || currentSlideData == null) {
            return;
        }
        
        // Calculate bounding box of selected shapes
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE;
        double maxY = Double.MIN_VALUE;
        
        for (int spid : selectedSpids) {
            var shape = currentSlideData.getShapeRegistry().getShape(spid);
            if (shape != null && shape.getGeometry() != null) {
                var geometry = shape.getGeometry();
                double x = geometry.getX();
                double y = geometry.getY();
                double width = geometry.getWidth();
                double height = geometry.getHeight();
                
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x + width);
                maxY = Math.max(maxY, y + height);
            }
        }
        
        // If we found valid shapes, zoom to their bounding box
        if (minX != Double.MAX_VALUE && minY != Double.MAX_VALUE && 
            maxX != Double.MIN_VALUE && maxY != Double.MIN_VALUE) {
            double boundingWidth = maxX - minX;
            double boundingHeight = maxY - minY;
            
            // Add some padding around the selection
            double padding = Math.max(boundingWidth, boundingHeight) * 0.1;
            minX -= padding;
            minY -= padding;
            boundingWidth += 2 * padding;
            boundingHeight += 2 * padding;
            
            zoomToArea(minX, minY, boundingWidth, boundingHeight);
        }
    }
    
    /**
     * Set zoom level directly with bounds checking
     */
    public void setZoomLevel(double newZoomLevel) {
        zoomLevel = Math.max(0.1, Math.min(newZoomLevel, 5.0));
        updateZoom();
    }
    
    @FXML
    private void handleConstructSlide() {
        constructCurrentSlide();
    }
    
    private void handleCanvasClick(double x, double y) {
        // Handle shape selection at canvas coordinates
        // This would integrate with shape hit testing
        System.out.println("Canvas clicked at: " + x + ", " + y);
    }
    
    // ========== VIEW OPTIONS ==========
    
    public void setShowGrid(boolean showGrid) {
        this.showGrid = showGrid;
        if (livePreviewManager != null) {
            livePreviewManager.setRenderingOptions(
                new LivePreviewManager.RenderingOptions()
                    .withShowGrid(showGrid)
                    .withShowBounds(showBounds)
                    .withShowSpids(showSpids)
                    .withDebugMode(debugMode)
                    .withZoom(zoomLevel)
            );
            livePreviewManager.forceRender();
        }
    }
    
    public void setShowBounds(boolean showBounds) {
        this.showBounds = showBounds;
        if (livePreviewManager != null) {
            livePreviewManager.setRenderingOptions(
                new LivePreviewManager.RenderingOptions()
                    .withShowGrid(showGrid)
                    .withShowBounds(showBounds)
                    .withShowSpids(showSpids)
                    .withDebugMode(debugMode)
                    .withZoom(zoomLevel)
            );
            livePreviewManager.forceRender();
        }
    }
    
    public void setShowSpids(boolean showSpids) {
        this.showSpids = showSpids;
        if (livePreviewManager != null) {
            livePreviewManager.setRenderingOptions(
                new LivePreviewManager.RenderingOptions()
                    .withShowGrid(showGrid)
                    .withShowBounds(showBounds)
                    .withShowSpids(showSpids)
                    .withDebugMode(debugMode)
                    .withZoom(zoomLevel)
            );
            livePreviewManager.forceRender();
        }
    }
    
    public void setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
        if (livePreviewManager != null) {
            livePreviewManager.setRenderingOptions(
                new LivePreviewManager.RenderingOptions()
                    .withShowGrid(showGrid)
                    .withShowBounds(showBounds)
                    .withShowSpids(showSpids)
                    .withDebugMode(debugMode)
                    .withZoom(zoomLevel)
            );
            livePreviewManager.forceRender();
        }
    }
    
    /**
     * Construct current slide (trigger slide creation/validation)
     */
    public void constructCurrentSlide() {
        if (currentSlideDocument != null) {
            logger.info("Slide construction not yet implemented");
        }
    }
    
    // ========== ANIMATION CONTROL HANDLERS ==========
    
    private void handlePlayAnimations() {
        if (animationController != null) {
            animationController.playAnimations();
        }
    }
    
    private void handleNextTrigger() {
        if (animationController != null) {
            if (!animationController.nextTrigger()) {
                updateAnimationStatus("Animation sequence completed");
            }
        }
    }
    
    private void handlePauseAnimations() {
        if (animationController != null) {
            if (animationController.getAnimationStatus().isPlaying()) {
                animationController.pauseAnimations();
            } else if (animationController.getAnimationStatus().isPaused()) {
                animationController.resumeAnimations();
            }
        }
    }
    
    private void handleStopAnimations() {
        if (animationController != null) {
            animationController.stopAnimations();
        }
    }
    
    private void handleResetAnimations() {
        if (animationController != null) {
            animationController.resetAnimations();
            updateAnimationStatus("Animations reset");
        }
    }
    
    private void handleAutoAdvanceToggle() {
        if (animationController != null && autoAdvanceCheckBox != null) {
            animationController.setAutoAdvanceEnabled(autoAdvanceCheckBox.isSelected());
        }
    }
    
    // ========== ANIMATION UI UPDATES ==========
    
    private void updateAnimationStatus(String status) {
        if (animationStatusLabel != null) {
            animationStatusLabel.setText(status);
        }
    }
    
    private void updateAnimationProgress(int current, int total) {
        if (animationProgressBar != null) {
            if (total > 0) {
                animationProgressBar.setProgress((double) current / total);
            } else {
                animationProgressBar.setProgress(0.0);
            }
        }
    }
    
    private void updateAnimationControls(boolean playing, boolean paused) {
        if (playAnimationsButton != null) {
            playAnimationsButton.setDisable(playing && !paused);
        }
        if (nextTriggerButton != null) {
            nextTriggerButton.setDisable(!playing || paused);
        }
        if (pauseAnimationsButton != null) {
            pauseAnimationsButton.setText(paused ? "Resume" : "Pause");
            pauseAnimationsButton.setDisable(!playing);
        }
        if (stopAnimationsButton != null) {
            stopAnimationsButton.setDisable(!playing && !paused);
        }
        if (resetAnimationsButton != null) {
            resetAnimationsButton.setDisable(playing && !paused);
        }
    }
    
    // ========== UTILITY METHODS ==========
    
    private void updateZoom() {
        if (slideCanvas != null && renderingContext != null) {
            // Update coordinate mapper zoom
            renderingContext.getCoordinateMapper().setZoomLevel(zoomLevel);
            
            // Update live preview with new zoom
            if (livePreviewManager != null) {
                livePreviewManager.setRenderingOptions(
                    new LivePreviewManager.RenderingOptions()
                        .withShowGrid(showGrid)
                        .withShowBounds(showBounds)
                        .withShowSpids(showSpids)
                        .withDebugMode(debugMode)
                        .withZoom(zoomLevel)
                );
                livePreviewManager.forceRender();
            } else {
                renderSlide();
            }
            updateZoomLabel();
        }
    }
    
    private void updateZoomLabel() {
        if (zoomLabel != null) {
            zoomLabel.setText(String.format("%.0f%%", zoomLevel * 100));
        }
    }
    
    private void clearCanvas() {
        if (slideCanvas != null) {
            GraphicsContext gc = slideCanvas.getGraphicsContext2D();
            gc.clearRect(0, 0, slideCanvas.getWidth(), slideCanvas.getHeight());
            
            // Draw background
            gc.setFill(Color.WHITE);
            gc.fillRect(0, 0, slideCanvas.getWidth(), slideCanvas.getHeight());
            
            // Draw border
            gc.setStroke(Color.LIGHTGRAY);
            gc.strokeRect(0, 0, slideCanvas.getWidth(), slideCanvas.getHeight());
        }
    }
    
    private void updateButtonStates() {
        boolean hasSlide = currentSlideDocument != null;
        if (constructButton != null) {
            constructButton.setDisable(!hasSlide);
        }
    }

    private void runBackgroundTask(Task<?> task) {
        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.setName("SlideEditor-" + task.getClass().getSimpleName() + "-" + System.currentTimeMillis());

        thread.setUncaughtExceptionHandler((t, e) -> {
            logger.error("Failed to run background task: " + e.getMessage(), e);
        });

        thread.start();
    }
    
    /**
     * Create sample slide document for testing
     */
    private Document createSampleSlideDocument() {
        try {
            String sampleXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                       xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
                    <p:cSld>
                        <p:spTree>
                            <p:nvGrpSpPr>
                                <p:cNvPr id="1" name=""/>
                                <p:cNvGrpSpPr/>
                                <p:nvPr/>
                            </p:nvGrpSpPr>
                            <p:grpSpPr>
                                <a:xfrm>
                                    <a:off x="0" y="0"/>
                                    <a:ext cx="0" cy="0"/>
                                    <a:chOff x="0" y="0"/>
                                    <a:chExt cx="0" cy="0"/>
                                </a:xfrm>
                            </p:grpSpPr>
                            <p:sp>
                                <p:nvSpPr>
                                    <p:cNvPr id="2" name="Title"/>
                                    <p:cNvSpPr>
                                        <a:spLocks noGrp="1"/>
                                    </p:cNvSpPr>
                                    <p:nvPr>
                                        <p:ph type="title"/>
                                    </p:nvPr>
                                </p:nvSpPr>
                                <p:spPr>
                                    <a:xfrm>
                                        <a:off x="914400" y="457200"/>
                                        <a:ext cx="7315200" cy="1371600"/>
                                    </a:xfrm>
                                    <a:prstGeom prst="rect"/>
                                </p:spPr>
                                <p:txBody>
                                    <a:bodyPr/>
                                    <a:p>
                                        <a:r>
                                            <a:rPr lang="en-US" sz="2800"/>
                                            <a:t>Sample Slide Title</a:t>
                                        </a:r>
                                    </a:p>
                                </p:txBody>
                            </p:sp>
                        </p:spTree>
                    </p:cSld>
                </p:sld>
                """;
            
            return XMLFactoryProvider.parseDocument(sampleXml);
            
        } catch (Exception e) {
            System.err.println("Failed to create sample slide: " + e.getMessage());
            return null;
        }
    }
    
    // ========== GETTERS ==========
    
    public Document getCurrentSlideDocument() {
        return currentSlideDocument;
    }
    
    public int getCurrentSlideNumber() {
        return currentSlideNumber;
    }
    
    public Canvas getSlideCanvas() {
        return slideCanvas;
    }
    
    public double getZoomLevel() {
        return zoomLevel;
    }
    
    // ========== RESOURCE CLEANUP ==========
    
    /**
     * Cleanup method to prevent memory leaks
     * Should be called when the controller is being disposed
     */
    public void cleanup() {
        System.out.println("Cleaning up SlideEditorController resources...");
        
        // Shutdown live preview manager
        if (livePreviewManager != null) {
            livePreviewManager.shutdown();
            livePreviewManager = null;
        }
        
        // Cleanup animation controller
        if (animationController != null) {
            animationController.cleanup();
            animationController = null;
        }
        
        // Clear references to large objects to help GC
        currentSlideDocument = null;
        currentSlideData = null;
        slideRenderer = null;
        renderingContext = null;
        
        // Clear canvas event handlers
        if (slideCanvas != null) {
            slideCanvas.setOnMouseClicked(null);
        }
        
        System.out.println("SlideEditorController cleanup completed");
    }
}