package com.excudo.view;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.orchestration.PresentationMetadata;
import com.excudo.core.orchestration.SlideMetadata;
import com.excudo.core.orchestration.OrchestrationStateListener;
import com.excudo.core.orchestration.SessionManager;
import com.excudo.view.components.PresentationExplorerController;
import com.excudo.view.components.SlideEditorController;
import com.excudo.view.components.TechnicalConsoleController;
import com.excudo.view.components.PropertiesController;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.concurrent.Task;
import javafx.application.Platform;
import com.excudo.core.utils.OOXMLAttributeOrder;
import com.excudo.core.utils.XMLFactoryProvider;
import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.Optional;
import java.util.List;
import com.excudo.core.utils.Logger;
import com.excudo.core.utils.ComponentLogger;

/**
 * Main controller for the Excudo application.
 * Coordinates between different view components and manages application state.
 */
public class MainController implements Initializable, OrchestrationStateListener {

    private static final ComponentLogger logger = Logger.rendering();

    // ========== FXML COMPONENTS ==========
    
    @FXML private BorderPane rootPane;
    @FXML private MenuBar menuBar;
    @FXML private ToolBar toolBar;
    @FXML private Label statusBar;
    
    // Menu items
    @FXML private MenuItem newPresentationItem;
    @FXML private MenuItem openPresentationItem;
    @FXML private MenuItem savePresentationItem;
    @FXML private MenuItem saveAsPresentationItem;
    @FXML private MenuItem exportPresentationItem;
    @FXML private MenuItem exitItem;

    // Edit menu items
    @FXML private MenuItem undoMenuItem;
    @FXML private MenuItem redoMenuItem;
    
    // View menu items
    @FXML private CheckMenuItem showGridItem;
    @FXML private CheckMenuItem showBoundsItem;
    @FXML private CheckMenuItem showSpidsItem;
    @FXML private CheckMenuItem debugModeItem;
    
    // Tools menu items
    @FXML private MenuItem validatePresentationItem;
    @FXML private MenuItem regenerateSpidsItem;
    @FXML private MenuItem settingsItem;
    
    // Tool bar buttons
    @FXML private Button newButton;
    @FXML private Button openButton;
    @FXML private Button saveButton;
    @FXML private Button zoomInButton;
    @FXML private Button zoomOutButton;
    @FXML private Button zoomFitButton;
    @FXML private MenuItem zoomInMenuItem;
    @FXML private MenuItem zoomOutMenuItem;
    @FXML private MenuItem zoomFitMenuItem;
    @FXML private MenuItem zoom100MenuItem;
    
    // Main content areas
    @FXML private SplitPane mainSplitPane;
    @FXML private VBox leftPanel;
    @FXML private VBox centerPanel;
    @FXML private TabPane bottomTabPane;
    @FXML private Tab propertiesTab;
    @FXML private TabPane sessionTabPane;
    @FXML private Button newSessionButton;

    // Presentation Explorer
    @FXML private TreeView<String> presentationTree;
    @FXML private Label presentationInfo;
    @FXML private Button addSlideButton;
    @FXML private Button deleteSlideButton;
    @FXML private Button moveUpButton;
    @FXML private Button moveDownButton;
    @FXML private Button refreshButton;
    
    // Slide Editor
    @FXML private ScrollPane canvasScrollPane;

    // Console
    @FXML private Tab consoleTab;
    @FXML private ScrollPane consoleScrollPane;
    @FXML private javafx.scene.text.TextFlow consoleOutput;
    @FXML private TextField commandInput;
    @FXML private Button executeButton;

    // Properties
    @FXML private TableView propertiesTable;
    @FXML private Label propertiesTitle;
    @FXML private Button refreshPropertiesButton;
    
    // ========== CONTROLLERS ==========

    private ViewManager viewManager;

    // Component controllers
    private PresentationExplorerController explorerController;
    private SlideEditorController editorController;
    private TechnicalConsoleController consoleController;
    private PropertiesController propertiesController;
    private com.excudo.view.components.SessionTabController sessionTabController;


    // ========== STATE ==========
    
    private File currentWorkingDirectory;
    private boolean presentationLoaded = false;
    
    // ========== PUBLIC ACCESSORS ==========
    
    /**
     * Get the current orchestrator instance.
     *
     * <p>As of the Session Unification refactor, this always reads from
     * {@link SessionManager#getActiveOrchestrator()} rather than a
     * GUI-local field. May return {@code null} when no session is
     * active (app just started, all sessions closed). Callers must
     * null-check.
     */
    public PPTXOrchestrator getCurrentOrchestrator() {
        return SessionManager.getInstance().getActiveOrchestrator();
    }

    /**
     * Shorthand alias matching the historical field name. Kept for
     * call-site convenience during the refactor; every read goes
     * through {@link #getCurrentOrchestrator()}.
     */
    public PPTXOrchestrator getOrchestrator() {
        return getCurrentOrchestrator();
    }
    
    /**
     * Refresh the presentation view after modifications
     */
    public void refreshPresentation() {
        Platform.runLater(() -> {
            updatePresentationViews();
            showStatus("Presentation refreshed");
        });
    }
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Initialize will be called after FXML loading
        setupInitialState();
        setupEventHandlers();
        setupComponentControllers();
    }
    
    /**
     * Initialize the controller with ViewManager (called from MainApplication)
     */
    public void initialize(ViewManager viewManager) {
        this.viewManager = viewManager;

        // Subscribe to state changes from any code path that loads/modifies a presentation
        // (console commands, menu actions, MCP tools, programmatic flows). Callbacks hop
        // onto the FX thread since they may fire from background or engine threads.
        // The active-session pointer on SessionManager is now the sole authority; this
        // controller no longer holds its own orchestrator reference.
        SessionManager.getInstance().addStateListener(this);

        // Reflect whatever the active session is right now -- e.g. if a console
        // engine pre-seeded one before the GUI attached.
        updatePresentationViews();

        // Setup initial view state
        updateUIState();
    }

    // ========== OrchestrationStateListener ==========

    @Override
    public void onPresentationLoaded() {
        Platform.runLater(this::updatePresentationViews);
    }

    @Override
    public void onPresentationClosed() {
        Platform.runLater(this::updatePresentationViews);
    }

    @Override
    public void onPresentationStructureChanged() {
        Platform.runLater(this::updatePresentationViews);
    }

    @Override
    public void onActiveSessionChanged(String sessionId, PPTXOrchestrator orchestrator) {
        // New authoritative handoff from any engine (UIConsoleEngine, MCPConsoleEngine,
        // future). GUI refresh -> always re-read current orchestrator via
        // SessionManager rather than trusting the parameter, so "no active session"
        // (both args null) naturally resolves to the empty-state branches in
        // updatePresentationViews.
        Platform.runLater(this::updatePresentationViews);
    }

    @Override
    public void onSlideModified(int slideNumber) {
        Platform.runLater(() -> {
            if (editorController != null) {
                editorController.loadSlide(slideNumber);
            }
        });
    }
    
    // ========== INITIALIZATION ==========
    
    private void setupInitialState() {
        // Set initial UI state
        presentationLoaded = false;
        currentWorkingDirectory = new File(System.getProperty("user.home"));
        
        // Configure split pane
        if (mainSplitPane != null) {
            mainSplitPane.setDividerPositions(0.25, 0.75); // 25% left, 50% center, 25% right
        }
        
        // Configure debug menu items
        if (showGridItem != null) showGridItem.setSelected(false);
        if (showBoundsItem != null) showBoundsItem.setSelected(false);
        if (showSpidsItem != null) showSpidsItem.setSelected(false);
        if (debugModeItem != null) debugModeItem.setSelected(false);
    }
    
    private void setupEventHandlers() {
        // File menu handlers
        if (newPresentationItem != null) {
            newPresentationItem.setOnAction(e -> handleNewPresentation());
        }
        if (openPresentationItem != null) {
            openPresentationItem.setOnAction(e -> handleOpenPresentation());
        }
        if (savePresentationItem != null) {
            savePresentationItem.setOnAction(e -> handleSavePresentation());
        }
        if (saveAsPresentationItem != null) {
            saveAsPresentationItem.setOnAction(e -> handleSaveAsPresentation());
        }
        if (exportPresentationItem != null) {
            exportPresentationItem.setOnAction(e -> handleExportPresentation());
        }
        if (exitItem != null) {
            exitItem.setOnAction(e -> handleExit());
        }

        // Edit menu handlers (Undo/Redo)
        if (undoMenuItem != null) {
            undoMenuItem.setOnAction(e -> handleUndo());
        }
        if (redoMenuItem != null) {
            redoMenuItem.setOnAction(e -> handleRedo());
        }

        // View menu handlers
        if (showGridItem != null) {
            showGridItem.setOnAction(e -> handleToggleGrid());
        }
        if (showBoundsItem != null) {
            showBoundsItem.setOnAction(e -> handleToggleBounds());
        }
        if (showSpidsItem != null) {
            showSpidsItem.setOnAction(e -> handleToggleSpids());
        }
        if (debugModeItem != null) {
            debugModeItem.setOnAction(e -> handleToggleDebugMode());
        }
        
        // Tools menu handlers
        if (validatePresentationItem != null) {
            validatePresentationItem.setOnAction(e -> handleValidatePresentation());
        }
        if (regenerateSpidsItem != null) {
            regenerateSpidsItem.setOnAction(e -> handleRegenerateSpids());
        }
        if (settingsItem != null) {
            settingsItem.setOnAction(e -> handleShowSettings());
        }
        
        // Toolbar handlers
        if (newButton != null) {
            newButton.setOnAction(e -> handleNewPresentation());
        }
        if (openButton != null) {
            openButton.setOnAction(e -> handleOpenPresentation());
        }
        if (saveButton != null) {
            saveButton.setOnAction(e -> handleSavePresentation());
        }
        
        // Zoom button handlers (delegate to SlideEditorController)
        if (zoomInButton != null) {
            zoomInButton.setOnAction(e -> {
                if (editorController != null) {
                    editorController.handleZoomIn();
                }
            });
        }
        if (zoomOutButton != null) {
            zoomOutButton.setOnAction(e -> {
                if (editorController != null) {
                    editorController.handleZoomOut();
                }
            });
        }
        if (zoomFitButton != null) {
            zoomFitButton.setOnAction(e -> {
                if (editorController != null) {
                    editorController.handleZoomFit();
                }
            });
        }

        // Zoom menu items share the same handlers as their toolbar
        // siblings + add keyboard shortcuts declared in FXML.
        if (zoomInMenuItem != null) {
            zoomInMenuItem.setOnAction(e -> {
                if (editorController != null) editorController.handleZoomIn();
            });
        }
        if (zoomOutMenuItem != null) {
            zoomOutMenuItem.setOnAction(e -> {
                if (editorController != null) editorController.handleZoomOut();
            });
        }
        if (zoomFitMenuItem != null) {
            zoomFitMenuItem.setOnAction(e -> {
                if (editorController != null) editorController.handleZoomFit();
            });
        }
        if (zoom100MenuItem != null) {
            zoom100MenuItem.setOnAction(e -> {
                if (editorController != null) editorController.handleZoomActualSize();
            });
        }
    }
    
    private void setupComponentControllers() {
        // Initialize component controllers and connect them to FXML components
        try {
            // Initialize presentation explorer controller and connect to tree
            explorerController = new PresentationExplorerController();
            explorerController.setMainController(this);
            explorerController.setPresentationTree(presentationTree);
            explorerController.setPresentationInfo(presentationInfo);
            explorerController.setButtons(addSlideButton, deleteSlideButton, 
                                         moveUpButton, moveDownButton, refreshButton);
            
            // Initialize slide editor controller and connect to preview components
            editorController = new SlideEditorController();
            editorController.setMainController(this);
            editorController.setCanvasScrollPane(canvasScrollPane);

            // Initialize console controller and connect to console components
            consoleController = new TechnicalConsoleController();
            consoleController.setMainController(this);
            consoleController.setConsoleComponents(consoleOutput, consoleScrollPane, commandInput, executeButton);

            // Initialize properties controller and connect to properties table
            propertiesController = new PropertiesController();
            propertiesController.setMainController(this);
            propertiesController.setPropertiesTable(propertiesTable);

            // Session tab bar: drives multi-session switching from the UI.
            // The controller subscribes to SessionManager internally and
            // keeps its tab state synchronized.
            sessionTabController = new com.excudo.view.components.SessionTabController();
            sessionTabController.setTabPane(sessionTabPane);
            sessionTabController.setNewSessionButton(newSessionButton);

        } catch (Exception e) {
            System.err.println("Failed to initialize component controllers: " + e.getMessage());
        }
    }
    
    // ========== FILE OPERATIONS ==========
    
    @FXML
    private void handleNewPresentation() {
        if (viewManager != null && !viewManager.confirmUnsavedChanges()) {
            return; // User cancelled
        }
        
        // Create new presentation
        showStatus("Creating new presentation...");
        
        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                // Initialize new presentation
                // orchestrator = new PPTXOrchestratorImpl(); // Would create new instance
                return null;
            }
            
            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    presentationLoaded = true;
                    if (viewManager != null) {
                        viewManager.setCurrentPresentationFile(null);
                        viewManager.markAsSaved();
                    }
                    updateUIState();
                    showStatus("New presentation created");
                });
            }
            
            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    showError("Failed to create new presentation", getException());
                });
            }
        };
        
        runBackgroundTask(task);
    }
    
    @FXML
    private void handleOpenPresentation() {
        handleOpenPresentationFromMenu();
    }
    
    /**
     * Public method to handle opening presentation from programmatic UI
     */
    public void handleOpenPresentationFromMenu() {
        if (viewManager != null && !viewManager.confirmUnsavedChanges()) {
            return; // User cancelled
        }
        
        Optional<File> fileOpt = viewManager != null ? 
                viewManager.showOpenPresentationDialog() : Optional.empty();
        
        if (fileOpt.isPresent()) {
            File file = fileOpt.get();
            openPresentationFile(file);
        }
    }
    
    private void openPresentationFile(File file) {
        showStatus("Opening presentation: " + file.getName());

        // Route through the console engine's session-create flow when one
        // is available so the console session state and the GUI's
        // orchestrator converge on the same instance. Without this, the
        // GUI Open path used MainController.orchestrator directly while
        // UIConsoleEngine kept its separate session state, and the
        // console panel stayed unaware of files loaded via the menu.
        // The orchestratorChangeHandler we already wire up in
        // TechnicalConsoleController -> MainController.setOrchestrator
        // propagates the new session orchestrator back to the GUI.
        // Falls back to the legacy direct-load path if no console engine
        // is available (e.g. console UI not yet initialised).
        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                com.excudo.view.console.UIConsoleEngine engine =
                    consoleController != null ? consoleController.getConsoleEngine() : null;
                if (engine != null) {
                    // createSession() creates the session and returns a
                    // SessionResult but does NOT activate it as "current"
                    // -- currentSessionId stays null, so subsequent console
                    // commands see "No active session". Activate it via
                    // setCurrentSession() which UIConsoleEngine overrides
                    // to also fire orchestratorChangeHandler and sync the
                    // GUI's orchestrator back.
                    com.excudo.core.commands.SessionResult result =
                        engine.createSession(file.getAbsolutePath());
                    if (!result.isSuccess()) {
                        throw new Exception(result.getErrorMessage());
                    }
                    // Pass the session's own file reference rather than the
                    // outer `file` closure -- they're the same File here,
                    // but this keeps the engine state self-consistent with
                    // whatever ConsoleSessionManager tracked internally.
                    engine.setCurrentSession(result.getSessionId(),
                        result.getOrchestrator(),
                        result.getLlmHandler(),
                        result.getCurrentFile());
                } else {
                    PPTXOrchestrator orch = getOrchestrator();
                    if (orch != null) {
                        var result = orch.loadPresentation(file);
                        if (!result.isSuccess()) {
                            throw new Exception(result.getMessage());
                        }
                    }
                }
                return null;
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    presentationLoaded = true;
                    if (viewManager != null) {
                        viewManager.setCurrentPresentationFile(file);
                        viewManager.markAsSaved();
                    }

                    // Update UI with loaded presentation
                    updatePresentationViews();
                    updateUIState();
                    showStatus("Presentation loaded: " + file.getName());
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    showError("Failed to open presentation", getException());
                });
            }
        };

        runBackgroundTask(task);
    }
    
    @FXML
    private void handleSavePresentation() {
        if (viewManager != null) {
            Optional<File> currentFile = viewManager.getCurrentPresentationFile();
            if (currentFile.isPresent()) {
                savePresentationToFile(currentFile.get());
            } else {
                handleSaveAsPresentation();
            }
        }
    }
    
    @FXML
    private void handleSaveAsPresentation() {
        if (viewManager != null) {
            Optional<File> fileOpt = viewManager.showSavePresentationDialog();
            if (fileOpt.isPresent()) {
                savePresentationToFile(fileOpt.get());
            }
        }
    }
    
    private void savePresentationToFile(File file) {
        showStatus("Saving presentation: " + file.getName());
        
        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                // Save presentation using the active-session orchestrator
                PPTXOrchestrator orch = getOrchestrator();
                if (orch == null) {
                    throw new Exception("No active session to save. Open or create a presentation first.");
                }
                var result = orch.savePresentation(file);
                if (!result.isSuccess()) {
                    throw new Exception(result.getMessage());
                }
                return null;
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    if (viewManager != null) {
                        viewManager.setCurrentPresentationFile(file);
                        viewManager.markAsSaved();
                    }
                    showStatus("Presentation saved: " + file.getName());
                });
            }
            
            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    showError("Failed to save presentation", getException());
                });
            }
        };
        
        runBackgroundTask(task);
    }
    
    @FXML
    private void handleExportPresentation() {
        if (viewManager != null) {
            Optional<File> fileOpt = viewManager.showExportDialog();
            if (fileOpt.isPresent()) {
                exportPresentationToFile(fileOpt.get());
            }
        }
    }
    
    private void exportPresentationToFile(File file) {
        showStatus("Exporting presentation: " + file.getName());
        
        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                // Export presentation with full validation, targeting the active session
                PPTXOrchestrator orch = getOrchestrator();
                if (orch == null) {
                    throw new Exception("No active session to export. Open or create a presentation first.");
                }
                var result = orch.finalizePresentation();
                if (!result.isSuccess()) {
                    throw new Exception(result.getMessage());
                }

                // Save to export file
                var saveResult = orch.savePresentation(file);
                if (!saveResult.isSuccess()) {
                    throw new Exception(saveResult.getMessage());
                }
                return null;
            }
            
            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    showStatus("Presentation exported: " + file.getName());
                    if (viewManager != null) {
                        viewManager.showInformation("Export Complete", 
                                "Presentation exported successfully", 
                                "File saved to: " + file.getAbsolutePath());
                    }
                });
            }
            
            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    showError("Failed to export presentation", getException());
                });
            }
        };
        
        runBackgroundTask(task);
    }
    
    @FXML
    private void handleExit() {
        if (viewManager != null && viewManager.confirmApplicationExit()) {
            Platform.exit();
        }
    }
    
    // ========== VIEW OPERATIONS ==========
    
    @FXML
    private void handleToggleGrid() {
        boolean showGrid = showGridItem != null && showGridItem.isSelected();
        if (editorController != null) {
            editorController.setShowGrid(showGrid);
        }
        showStatus("Grid " + (showGrid ? "enabled" : "disabled"));
    }
    
    @FXML
    private void handleToggleBounds() {
        boolean showBounds = showBoundsItem != null && showBoundsItem.isSelected();
        if (editorController != null) {
            editorController.setShowBounds(showBounds);
        }
        showStatus("Bounds " + (showBounds ? "enabled" : "disabled"));
    }
    
    @FXML
    private void handleToggleSpids() {
        boolean showSpids = showSpidsItem != null && showSpidsItem.isSelected();
        if (editorController != null) {
            editorController.setShowSpids(showSpids);
        }
        showStatus("SPIDs " + (showSpids ? "enabled" : "disabled"));
    }
    
    @FXML
    private void handleToggleDebugMode() {
        boolean debugMode = debugModeItem != null && debugModeItem.isSelected();
        if (editorController != null) {
            editorController.setDebugMode(debugMode);
        }
        showStatus("Debug mode " + (debugMode ? "enabled" : "disabled"));
    }
    
    
    // ========== PRESENTATION UPDATES ==========
    
    /**
     * Update views when presentation is loaded
     */
    public void updatePresentationViews() {
        PPTXOrchestrator orch = getOrchestrator();
        if (orch == null) {
            // No active session -> render empty state. The explorer is
            // cleared by its own onActiveSessionChanged path; we still
            // update local flags + the console so downstream handlers
            // observe the "nothing loaded" transition consistently.
            presentationLoaded = false;
            updateUIState();
            return;
        }
        try {
            // Get presentation metadata from the active-session orchestrator
            PresentationMetadata metadata = orch.getPresentationMetadata();
            List<SlideMetadata> slides = orch.getAllSlideMetadata();

            System.out.println("Presentation updated: " +
                (metadata != null ? metadata.getTitle() + " (" + slides.size() + " slides)" : "No metadata"));


            // Update component controllers with loaded data
            if (explorerController != null) {
                explorerController.updatePresentation(metadata, slides);
            }

            if (editorController != null && slides != null && !slides.isEmpty()) {
                // Load first slide in editor
                editorController.loadSlide(slides.get(0).getSlideNumber());
            }

            // Notify console controller that presentation is loaded
            if (consoleController != null) {
                consoleController.onPresentationLoaded();
            }

            // Keep Edit menu in sync with the session's command history
            updateUndoRedoState();

            presentationLoaded = true;
            showStatus("Presentation loaded: " + slides.size() + " slides");

        } catch (Exception e) {
            logger.error("Failed to update presentation views: " + e.getMessage(), e);
            showStatus("Error loading presentation data");
        }
    }

    /**
     * Handle slide selection change from presentation explorer
     */
    public void onSlideSelected(int slideNumber) {
        if (editorController != null) {
            editorController.loadSlide(slideNumber);
        }

        // Update properties panel with slide metadata
        PPTXOrchestrator orch = getOrchestrator();
        if (propertiesController != null && orch != null) {
            var slideMetadata = orch.getSlideMetadata(slideNumber);
            slideMetadata.ifPresent(metadata -> propertiesController.displaySlideProperties(metadata));
        }
        
        showStatus("Selected slide " + slideNumber);
    }
    
    /**
     * Handle slide content changes from editor
     */
    public void onSlideContentChanged() {
        if (viewManager != null) {
            viewManager.markAsModified();
        }
    }
    
    // ========== UI STATE MANAGEMENT ==========
    
    private void updateUIState() {
        // Update menu and toolbar based on current state
        boolean hasPresentation = presentationLoaded;
        
        if (savePresentationItem != null) {
            savePresentationItem.setDisable(!hasPresentation);
        }
        if (saveAsPresentationItem != null) {
            saveAsPresentationItem.setDisable(!hasPresentation);
        }
        if (exportPresentationItem != null) {
            exportPresentationItem.setDisable(!hasPresentation);
        }
        if (saveButton != null) {
            saveButton.setDisable(!hasPresentation);
        }
        
        // Tools menu items
        if (validatePresentationItem != null) {
            validatePresentationItem.setDisable(!hasPresentation);
        }
        if (regenerateSpidsItem != null) {
            regenerateSpidsItem.setDisable(!hasPresentation);
        }
    }
    
    // ========== TOOLS MENU OPERATIONS ==========
    
    @FXML
    private void handleValidatePresentation() {
        PPTXOrchestrator orch = getOrchestrator();
        if (!presentationLoaded || orch == null) {
            showError("No presentation loaded", new IllegalStateException("Please load a presentation first"));
            return;
        }

        showStatus("Validating presentation...");

        Task<String> task = new Task<String>() {
            @Override
            protected String call() throws Exception {
                // Get the current context to access managers
                var context = orch.getContext();
                if (!context.isPresent()) {
                    throw new Exception("No presentation context available");
                }
                
                var slideCreator = context.get().getSlideCreator();
                var validationResult = slideCreator.validatePresentation();
                
                StringBuilder report = new StringBuilder();
                report.append("=== PRESENTATION VALIDATION REPORT ===\n\n");
                
                if (validationResult.isValid()) {
                    report.append("[OK] VALIDATION PASSED\n");
                    report.append("No errors found in presentation structure.\n");
                } else {
                    report.append("[FAIL] VALIDATION FAILED\n");
                    report.append("Errors found: ").append(validationResult.getErrors().size()).append("\n\n");
                    
                    report.append("ERRORS:\n");
                    for (String error : validationResult.getErrors()) {
                        report.append("  • ").append(error).append("\n");
                    }
                }
                
                if (validationResult.hasWarnings()) {
                    report.append("\nWARNINGS:\n");
                    for (String warning : validationResult.getWarnings()) {
                        report.append("  [WARN] ").append(warning).append("\n");
                    }
                }
                
                return report.toString();
            }
            
            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    String report = getValue();
                    // Validation tab removed in Tier 6.7; surface the
                    // report via status/log for the developer path that
                    // still triggers it from Tools menu.
                    showStatus("Validation completed: " + (report != null
                        ? report.lines().count() + " issue(s)" : "clean"));
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    showError("Validation failed", getException());
                });
            }
        };

        runBackgroundTask(task);
    }

    @FXML
    private void handleRegenerateSpids() {
        PPTXOrchestrator orch = getOrchestrator();
        if (!presentationLoaded || orch == null) {
            showError("No presentation loaded", new IllegalStateException("Please load a presentation first"));
            return;
        }

        // Confirm action as this modifies the presentation
        if (viewManager != null) {
            boolean confirmed = viewManager.showConfirmation(
                "Regenerate SPIDs",
                "This will regenerate all Shape IDs in the presentation",
                "This operation will modify the presentation structure. Are you sure you want to continue?"
            );
            if (!confirmed) {
                return;
            }
        }

        showStatus("Regenerating SPIDs...");

        Task<String> task = new Task<String>() {
            @Override
            protected String call() throws Exception {
                // Get the current context to access managers
                var context = orch.getContext();
                if (!context.isPresent()) {
                    throw new Exception("No presentation context available");
                }

                var spidManager = context.get().getSpidManager();
                var relationshipManager = context.get().getRelationshipManager();
                
                StringBuilder report = new StringBuilder();
                report.append("=== SPID REGENERATION REPORT ===\n\n");
                
                // First validate current state
                var spidValidation = spidManager.validateSpidUniqueness();
                report.append("Pre-regeneration validation:\n");
                report.append("  Errors: ").append(spidValidation.getErrors().size()).append("\n");
                report.append("  Warnings: ").append(spidValidation.getWarnings().size()).append("\n\n");
                
                // Perform batch SPID regeneration by iterating through all slides
                List<SlideMetadata> slides = orch.getAllSlideMetadata();
                int totalSlidesProcessed = 0;
                int totalShapesProcessed = 0;
                int totalAnimationsUpdated = 0;
                
                com.excudo.core.model.PPTXDocument pptxDocForRegen = context.get().getDocument();
                javax.xml.parsers.DocumentBuilder docBuilder = XMLFactoryProvider.createDocumentBuilder();

                for (SlideMetadata slideMetadata : slides) {
                    int slideNumber = slideMetadata.getSlideNumber();

                    if (pptxDocForRegen != null && pptxDocForRegen.hasSlide(slideNumber)) {
                        org.w3c.dom.Document slideDoc = pptxDocForRegen.getSlideDocument(slideNumber);
                        var slideRegenerationResult = spidManager.regenerateSpids(slideDoc, slideNumber);

                        // Mark the slide dirty so it gets serialized on next save
                        pptxDocForRegen.markSlideDirty(slideNumber);

                        totalSlidesProcessed++;
                        totalShapesProcessed += slideRegenerationResult.getShapesProcessed();
                        totalAnimationsUpdated += slideRegenerationResult.getAnimationsUpdated();
                    }
                }
                
                report.append("SPID Regeneration Results:\n");
                report.append("  Slides processed: ").append(totalSlidesProcessed).append("\n");
                report.append("  Shapes updated: ").append(totalShapesProcessed).append("\n");
                report.append("  Animations updated: ").append(totalAnimationsUpdated).append("\n\n");
                
                // Validate relationships after SPID changes
                var relationshipValidation = relationshipManager.validateAllRelationships();
                report.append("Post-regeneration relationship validation:\n");
                report.append("  Errors: ").append(relationshipValidation.getErrors().size()).append("\n");
                report.append("  Warnings: ").append(relationshipValidation.getWarnings().size()).append("\n");
                
                if (relationshipValidation.hasErrors()) {
                    report.append("\nRelationship Errors:\n");
                    for (String error : relationshipValidation.getErrors()) {
                        report.append("  • ").append(error).append("\n");
                    }
                }
                
                report.append("\n[OK] SPID regeneration completed successfully!");
                
                return report.toString();
            }
            
            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    String report = getValue();
                    showStatus("Post-regen validation: " + (report != null
                        ? report.lines().count() + " issue(s)" : "clean"));

                    // Mark presentation as modified
                    if (viewManager != null) {
                        viewManager.markAsModified();
                    }

                    // Refresh views to show updated data
                    updatePresentationViews();

                    showStatus("SPID regeneration completed");
                });
            }
            
            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    showError("SPID regeneration failed", getException());
                });
            }
        };
        
        runBackgroundTask(task);
    }
    
    // ========== EDIT OPERATIONS (Undo/Redo) ==========

    /**
     * Undo the last command via the active session's CommandInvoker.
     * The Command interface already tracks undoability per-command; this
     * menu handler is just a thin bridge to that infrastructure.
     */
    @FXML
    private void handleUndo() {
        com.excudo.core.commands.CommandInvoker invoker = getCurrentCommandInvoker();
        if (invoker == null) {
            showStatus("No active session");
            return;
        }
        if (!invoker.canUndo()) {
            showStatus("Nothing to undo");
            return;
        }
        try {
            invoker.undo();
            SessionManager.getInstance().firePresentationStructureChanged();
            showStatus("Undone: " + invoker.getRedoDescription());
        } catch (Exception e) {
            logger.error("Undo failed: " + e.getMessage(), e);
            showError("Undo failed", e);
        }
        updateUndoRedoState();
    }

    /**
     * Redo the last undone command.
     */
    @FXML
    private void handleRedo() {
        com.excudo.core.commands.CommandInvoker invoker = getCurrentCommandInvoker();
        if (invoker == null) {
            showStatus("No active session");
            return;
        }
        if (!invoker.canRedo()) {
            showStatus("Nothing to redo");
            return;
        }
        try {
            String description = invoker.getRedoDescription();
            invoker.redo();
            SessionManager.getInstance().firePresentationStructureChanged();
            showStatus("Redone: " + description);
        } catch (Exception e) {
            logger.error("Redo failed: " + e.getMessage(), e);
            showError("Redo failed", e);
        }
        updateUndoRedoState();
    }

    /**
     * Retrieve the CommandInvoker for the current session.
     * Returns null if no session is active.
     */
    private com.excudo.core.commands.CommandInvoker getCurrentCommandInvoker() {
        if (consoleController == null) {
            return null;
        }
        com.excudo.view.console.UIConsoleEngine engine = consoleController.getConsoleEngine();
        return engine != null ? engine.getCurrentCommandInvoker() : null;
    }

    /**
     * Refresh Undo/Redo menu item enabled state based on the current session's
     * CommandInvoker. Called from updatePresentationViews() so every state
     * change (via the OrchestrationStateListener) keeps the menu in sync.
     */
    private void updateUndoRedoState() {
        com.excudo.core.commands.CommandInvoker invoker = getCurrentCommandInvoker();
        boolean canUndo = invoker != null && invoker.canUndo();
        boolean canRedo = invoker != null && invoker.canRedo();
        if (undoMenuItem != null) {
            undoMenuItem.setDisable(!canUndo);
        }
        if (redoMenuItem != null) {
            redoMenuItem.setDisable(!canRedo);
        }
    }

    @FXML
    private void handleShowSettings() {
        // Placeholder for settings dialog
        if (viewManager != null) {
            viewManager.showInformation("Settings", "Settings Dialog", 
                "Settings functionality will be implemented in a future version.");
        }
    }
    
    // ========== UTILITY METHODS ==========
    
    private void runBackgroundTask(Task<?> task) {
        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }
    
    private void showStatus(String message) {
        if (viewManager != null) {
            viewManager.showStatus(message);
        } else {
            System.out.println("Status: " + message);
        }
    }
    
    private void showError(String message, Throwable exception) {
        String errorMsg = exception != null ? exception.getMessage() : "Unknown error";
        if (viewManager != null) {
            viewManager.showError("Error", message, errorMsg);
        } else {
            System.err.println("Error: " + message + " - " + errorMsg);
        }
    }
    
    // ========== GETTERS ==========

    // getOrchestrator() + getCurrentOrchestrator() are defined near the
    // top of the class -- both resolve through SessionManager.
    // setOrchestrator() was removed in the Session Unification refactor:
    // the active session is set by whichever engine is driving it, and
    // the GUI subscribes to onActiveSessionChanged to refresh.

    public ViewManager getViewManager() {
        return viewManager;
    }

}