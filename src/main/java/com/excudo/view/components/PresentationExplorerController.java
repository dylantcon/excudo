package com.excudo.view.components;

import com.excudo.core.orchestration.OrchestrationStateListener;
import com.excudo.core.orchestration.PresentationMetadata;
import com.excudo.core.orchestration.SessionManager;
import com.excudo.core.orchestration.SlideMetadata;
import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.results.ExecutionResult;
import com.excudo.core.results.SlideExecutionResult;
import com.excudo.view.MainController;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTreeCell;
import javafx.collections.ObservableList;
import javafx.collections.FXCollections;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.List;

/**
 * Controller for the presentation explorer tree view.
 * Manages slide hierarchy, selection, and navigation.
 *
 * <p>Subscribes to {@link SessionManager}'s state-listener fan-out so
 * the tree refreshes whenever the active session changes -- via GUI
 * menu, console command, OR MCP tool. Before the Session Unification
 * refactor, MCP-loaded decks left this tree stale because MCP never
 * fed MainController's one-way orchestrator callback.
 */
public class PresentationExplorerController implements Initializable, OrchestrationStateListener {
    
    // ========== FXML COMPONENTS ==========
    
    @FXML private TreeView<String> presentationTree;
    @FXML private Label presentationInfo;
    @FXML private Button refreshButton;
    @FXML private Button addSlideButton;
    @FXML private Button deleteSlideButton;
    @FXML private Button moveUpButton;
    @FXML private Button moveDownButton;
    
    // ========== STATE ==========
    
    private MainController mainController;
    private TreeItem<String> rootItem;
    private PresentationMetadata currentPresentation;
    private List<SlideMetadata> currentSlides;
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupTreeView();
        setupEventHandlers();
        setupInitialState();
        // Subscribe once for the controller's lifetime. The Explorer is
        // a long-lived singleton in the GUI; listener fan-out is
        // idempotent, so no cleanup is wired here.
        SessionManager.getInstance().addStateListener(this);
        refreshFromActiveSession();
    }

    // ========== OrchestrationStateListener ==========

    /**
     * Fired whenever the active session pointer moves -- from any engine
     * (UIConsoleEngine, MCPConsoleEngine, future). Reads the live active
     * orchestrator and rebuilds the tree. Both args may be null for
     * "no active session" which clears the tree.
     */
    @Override
    public void onActiveSessionChanged(String sessionId, PPTXOrchestrator orchestrator) {
        Platform.runLater(this::refreshFromActiveSession);
    }

    @Override
    public void onPresentationStructureChanged() {
        // Add/remove/reorder -- re-read metadata from the active session.
        Platform.runLater(this::refreshFromActiveSession);
    }

    @Override
    public void onSlideModified(int slideNumber) {
        // Per-slide shape / animation / transition mutation. The tree
        // label for each slide embeds shape + animation counts, so the
        // labels go stale on any mutation. Re-read metadata. A more
        // surgical update (touch only the one slide's subtree) is a
        // future optimization; the full rebuild is negligible at v1
        // slide counts.
        Platform.runLater(this::refreshFromActiveSession);
    }

    private void refreshFromActiveSession() {
        PPTXOrchestrator orch = SessionManager.getInstance().getActiveOrchestrator();
        if (orch == null) {
            // Empty state: clear the tree.
            updatePresentation(null, java.util.Collections.emptyList());
            return;
        }
        try {
            PresentationMetadata meta = orch.getPresentationMetadata();
            List<SlideMetadata> slides = orch.getAllSlideMetadata();
            updatePresentation(meta, slides);
        } catch (Exception ignored) {
            // If the orchestrator can't produce metadata (race with close,
            // uninitialised session) treat as empty state rather than
            // propagating to the UI.
            updatePresentation(null, java.util.Collections.emptyList());
        }
    }
    
    // ========== INITIALIZATION ==========
    
    /**
     * Set reference to main controller. Called by MainController after
     * fx:include auto-injection resolves this controller.
     */
    public void setMainController(MainController mainController) {
        this.mainController = mainController;
    }

    private void setupTreeView() {
        // Create root item
        rootItem = new TreeItem<>("No Presentation Loaded");
        rootItem.setExpanded(true);
        
        // Configure tree view
        if (presentationTree != null) {
            presentationTree.setRoot(rootItem);
            presentationTree.setShowRoot(true);
            presentationTree.setEditable(false);
            presentationTree.setCellFactory(TextFieldTreeCell.forTreeView());
        }
    }
    
    private void setupEventHandlers() {
        presentationTree.getSelectionModel().selectedItemProperty().addListener(
            (observable, oldValue, newValue) -> handleSlideSelection(newValue)
        );
        refreshButton.setOnAction(e -> handleRefresh());
        addSlideButton.setOnAction(e -> handleAddSlide());
        deleteSlideButton.setOnAction(e -> handleDeleteSlide());
        moveUpButton.setOnAction(e -> handleMoveSlideUp());
        moveDownButton.setOnAction(e -> handleMoveSlideDown());
    }
    
    private void setupInitialState() {
        updateButtonStates();
        updatePresentationInfo(null);
    }
    
    // ========== PRESENTATION MANAGEMENT ==========
    
    /**
     * Update the presentation explorer with new presentation data
     */
    public void updatePresentation(PresentationMetadata presentation, List<SlideMetadata> slides) {
        this.currentPresentation = presentation;
        this.currentSlides = slides;
        
        // Update tree view
        rebuildTree();
        
        // Update presentation info
        updatePresentationInfo(presentation);
        
        // Update button states
        updateButtonStates();
    }
    
    /**
     * Clear presentation explorer
     */
    public void clearPresentation() {
        this.currentPresentation = null;
        this.currentSlides = null;
        
        rootItem.getChildren().clear();
        rootItem.setValue("No Presentation Loaded");
        
        updatePresentationInfo(null);
        updateButtonStates();
    }
    
    /**
     * Rebuild the tree view from current presentation data
     */
    private void rebuildTree() {
        // Clear existing items
        rootItem.getChildren().clear();
        
        if (currentPresentation != null) {
            rootItem.setValue(currentPresentation.getTitle());
            
            // Add slides
            if (currentSlides != null) {
                for (int i = 0; i < currentSlides.size(); i++) {
                    SlideMetadata slide = currentSlides.get(i);
                    String slideLabel = String.format("Slide %d: %s", 
                            i + 1, 
                            slide.getTitle() != null ? slide.getTitle() : "Untitled");
                    
                    TreeItem<String> slideItem = new TreeItem<>(slideLabel);
                    slideItem.setExpanded(false);
                    
                    // Add slide shapes/content summary
                    addSlideContent(slideItem, slide);
                    
                    rootItem.getChildren().add(slideItem);
                }
            }
        }
        
        // Expand root
        rootItem.setExpanded(true);
    }
    
    /**
     * Add slide content information to tree item
     */
    private void addSlideContent(TreeItem<String> slideItem, SlideMetadata slide) {
        // Add shapes count
        int shapeCount = slide.getShapeCount();
        if (shapeCount > 0) {
            TreeItem<String> shapesItem = new TreeItem<>(String.format("Shapes (%d)", shapeCount));
            slideItem.getChildren().add(shapesItem);
        }
        
        // Add animations count if any
        int animationCount = slide.getAnimationCount();
        if (animationCount > 0) {
            TreeItem<String> animationsItem = new TreeItem<>(String.format("Animations (%d)", animationCount));
            slideItem.getChildren().add(animationsItem);
        }
        
        // Add layout information
        String layoutName = slide.getLayoutName();
        if (layoutName != null && !layoutName.isEmpty()) {
            TreeItem<String> layoutItem = new TreeItem<>("Layout: " + layoutName);
            slideItem.getChildren().add(layoutItem);
        }
    }
    
    // ========== EVENT HANDLERS ==========
    
    /**
     * Handle slide selection in tree view
     */
    private void handleSlideSelection(TreeItem<String> selectedItem) {
        if (selectedItem == null || selectedItem == rootItem) {
            return;
        }
        
        // Find slide number from tree structure
        TreeItem<String> parent = selectedItem.getParent();
        if (parent == rootItem) {
            // This is a slide item
            int slideIndex = rootItem.getChildren().indexOf(selectedItem);
            if (slideIndex >= 0 && mainController != null) {
                mainController.onSlideSelected(slideIndex + 1); // 1-based slide numbers
            }
        } else if (parent != null && parent.getParent() == rootItem) {
            // This is a slide content item, select the parent slide
            int slideIndex = rootItem.getChildren().indexOf(parent);
            if (slideIndex >= 0 && mainController != null) {
                mainController.onSlideSelected(slideIndex + 1);
            }
        }
        
        updateButtonStates();
    }
    
    @FXML
    private void handleRefresh() {
        if (mainController != null) {
            // Trigger refresh from main controller
            // mainController.refreshPresentationData();
        }
    }
    
    @FXML
    private void handleAddSlide() {
        if (mainController != null && currentPresentation != null) {
            PPTXOrchestrator orchestrator = mainController.getOrchestrator();
            if (orchestrator == null) {
                showStatus("Orchestrator not available");
                return;
            }
            
            // Add new slide after current selection
            TreeItem<String> selected = presentationTree.getSelectionModel().getSelectedItem();
            int insertIndex = getSlideInsertIndex(selected);
            
            // Prompt for slide title
            TextInputDialog dialog = new TextInputDialog("New Slide");
            dialog.setTitle("Add New Slide");
            dialog.setHeaderText("Create a new slide");
            dialog.setContentText("Enter slide title:");
            
            java.util.Optional<String> result = dialog.showAndWait();
            if (result.isPresent()) {
                String title = result.get().trim();
                if (title.isEmpty()) {
                    title = "New Slide";
                }
                
                try {
                    // Create slide through orchestrator (1-based indexing)
                    SlideExecutionResult slideResult = orchestrator.createSlide(insertIndex + 1, title);

                    if (slideResult.isSuccess()) {
                        showStatus("Slide created successfully: " + title);
                        refreshPresentation();
                    } else {
                        showStatus("Failed to create slide: " + slideResult.getMessage());
                    }
                } catch (Exception e) {
                    showStatus("Error creating slide: " + e.getMessage());
                }
            }
        }
    }
    
    @FXML
    private void handleDeleteSlide() {
        TreeItem<String> selected = presentationTree.getSelectionModel().getSelectedItem();
        if (selected != null && selected != rootItem && mainController != null) {
            PPTXOrchestrator orchestrator = mainController.getOrchestrator();
            if (orchestrator == null) {
                showStatus("Orchestrator not available");
                return;
            }
            
            int slideIndex = getSlideIndex(selected);
            if (slideIndex >= 0) {
                // Confirm deletion
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setTitle("Delete Slide");
                alert.setHeaderText("Delete slide " + (slideIndex + 1) + "?");
                alert.setContentText("This action cannot be undone.");
                
                alert.showAndWait().ifPresent(response -> {
                    if (response == ButtonType.OK) {
                        try {
                            // Delete slide through orchestrator (1-based indexing)
                            SlideExecutionResult slideResult = orchestrator.deleteSlide(slideIndex + 1);
                            
                            if (slideResult.isSuccess()) {
                                showStatus("Slide " + (slideIndex + 1) + " deleted successfully");
                                refreshPresentation();
                            } else {
                                showStatus("Failed to delete slide: " + slideResult.getMessage());
                            }
                        } catch (Exception e) {
                            showStatus("Error deleting slide: " + e.getMessage());
                        }
                    }
                });
            }
        }
    }
    
    @FXML
    private void handleMoveSlideUp() {
        TreeItem<String> selected = presentationTree.getSelectionModel().getSelectedItem();
        if (selected != null && selected != rootItem && mainController != null) {
            PPTXOrchestrator orchestrator = mainController.getOrchestrator();
            if (orchestrator == null) {
                showStatus("Orchestrator not available");
                return;
            }
            
            int slideIndex = getSlideIndex(selected);
            if (slideIndex > 0) {
                try {
                    // Move slide up through orchestrator (1-based indexing)
                    SlideExecutionResult slideResult = orchestrator.moveSlide(slideIndex + 1, slideIndex);
                    
                    if (slideResult.isSuccess()) {
                        showStatus("Slide " + (slideIndex + 1) + " moved up successfully");
                        refreshPresentation();
                        selectSlideByIndex(slideIndex - 1);
                    } else {
                        showStatus("Failed to move slide up: " + slideResult.getMessage());
                    }
                } catch (Exception e) {
                    showStatus("Error moving slide up: " + e.getMessage());
                }
            }
        }
    }
    
    @FXML
    private void handleMoveSlideDown() {
        TreeItem<String> selected = presentationTree.getSelectionModel().getSelectedItem();
        if (selected != null && selected != rootItem && mainController != null) {
            PPTXOrchestrator orchestrator = mainController.getOrchestrator();
            if (orchestrator == null) {
                showStatus("Orchestrator not available");
                return;
            }
            
            int slideIndex = getSlideIndex(selected);
            if (slideIndex >= 0 && slideIndex < rootItem.getChildren().size() - 1) {
                try {
                    // Move slide down through orchestrator (1-based indexing)
                    SlideExecutionResult slideResult = orchestrator.moveSlide(slideIndex + 1, slideIndex + 2);
                    
                    if (slideResult.isSuccess()) {
                        showStatus("Slide " + (slideIndex + 1) + " moved down successfully");
                        refreshPresentation();
                        selectSlideByIndex(slideIndex + 1);
                    } else {
                        showStatus("Failed to move slide down: " + slideResult.getMessage());
                    }
                } catch (Exception e) {
                    showStatus("Error moving slide down: " + e.getMessage());
                }
            }
        }
    }
    
    // ========== UTILITY METHODS ==========
    
    /**
     * Get slide index from tree item
     */
    private int getSlideIndex(TreeItem<String> item) {
        if (item == null || item == rootItem) {
            return -1;
        }
        
        // If item is a slide
        if (item.getParent() == rootItem) {
            return rootItem.getChildren().indexOf(item);
        }
        
        // If item is slide content, get parent slide
        if (item.getParent() != null && item.getParent().getParent() == rootItem) {
            return rootItem.getChildren().indexOf(item.getParent());
        }
        
        return -1;
    }
    
    /**
     * Get appropriate index for inserting new slide
     */
    private int getSlideInsertIndex(TreeItem<String> selected) {
        int slideIndex = getSlideIndex(selected);
        if (slideIndex >= 0) {
            return slideIndex + 1; // Insert after selected slide
        }
        return currentSlides != null ? currentSlides.size() : 0; // Insert at end
    }
    
    /**
     * Update button enabled/disabled states
     */
    private void updateButtonStates() {
        boolean hasPresentation = currentPresentation != null;
        boolean hasSelection = presentationTree != null && 
                presentationTree.getSelectionModel().getSelectedItem() != null;
        
        if (addSlideButton != null) {
            addSlideButton.setDisable(!hasPresentation);
        }
        if (deleteSlideButton != null) {
            deleteSlideButton.setDisable(!hasSelection || !hasPresentation);
        }
        if (moveUpButton != null) {
            int slideIndex = hasSelection ? getSlideIndex(presentationTree.getSelectionModel().getSelectedItem()) : -1;
            moveUpButton.setDisable(slideIndex <= 0);
        }
        if (moveDownButton != null) {
            int slideIndex = hasSelection ? getSlideIndex(presentationTree.getSelectionModel().getSelectedItem()) : -1;
            int maxIndex = currentSlides != null ? currentSlides.size() - 1 : -1;
            moveDownButton.setDisable(slideIndex < 0 || slideIndex >= maxIndex);
        }
        if (refreshButton != null) {
            refreshButton.setDisable(!hasPresentation);
        }
    }
    
    /**
     * Update presentation information display
     */
    private void updatePresentationInfo(PresentationMetadata presentation) {
        if (presentationInfo != null) {
            if (presentation != null) {
                StringBuilder info = new StringBuilder();
                info.append("Title: ").append(presentation.getTitle()).append("\n");
                info.append("Slides: ").append(currentSlides != null ? currentSlides.size() : 0).append("\n");
                info.append("Author: ").append(presentation.getAuthor() != null ? presentation.getAuthor() : "Unknown");
                
                presentationInfo.setText(info.toString());
            } else {
                presentationInfo.setText("No presentation loaded");
            }
        }
    }
    
    /**
     * Show status message (delegate to main controller)
     */
    private void showStatus(String message) {
        if (mainController != null) {
            // mainController.showStatus(message);
        } else {
            System.out.println("Status: " + message);
        }
    }
    
    /**
     * Refresh the presentation data from the orchestrator
     */
    private void refreshPresentation() {
        if (mainController != null && mainController.getOrchestrator() != null) {
            // Trigger a complete refresh of the presentation data through MainController
            mainController.updatePresentationViews();
        }
    }
    
    /**
     * Select a slide by its index in the tree
     */
    private void selectSlideByIndex(int index) {
        if (rootItem != null && index >= 0 && index < rootItem.getChildren().size()) {
            TreeItem<String> slideItem = rootItem.getChildren().get(index);
            presentationTree.getSelectionModel().select(slideItem);
        }
    }
    
    // ========== GETTERS ==========
    
    public TreeView<String> getPresentationTree() {
        return presentationTree;
    }
    
    public PresentationMetadata getCurrentPresentation() {
        return currentPresentation;
    }
    
    public List<SlideMetadata> getCurrentSlides() {
        return currentSlides;
    }
}