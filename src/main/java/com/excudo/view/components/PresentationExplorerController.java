package com.excudo.view.components;

import com.excudo.core.orchestration.PresentationMetadata;
import com.excudo.core.orchestration.SlideMetadata;
import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.results.ExecutionResult;
import com.excudo.core.results.SlideExecutionResult;
import com.excudo.view.MainController;
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
 */
public class PresentationExplorerController implements Initializable {
    
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
    }
    
    // ========== INITIALIZATION ==========
    
    /**
     * Set reference to main controller
     */
    public void setMainController(MainController mainController) {
        this.mainController = mainController;
    }
    
    /**
     * Set the TreeView component from FXML
     */
    public void setPresentationTree(TreeView<String> presentationTree) {
        this.presentationTree = presentationTree;
        setupTreeView(); // Setup the tree now that we have the component
        setupEventHandlers();
    }
    
    /**
     * Set the presentation info label (manual injection)
     */
    public void setPresentationInfo(Label presentationInfo) {
        this.presentationInfo = presentationInfo;
    }
    
    /**
     * Set the buttons (manual injection)
     */
    public void setButtons(Button addSlideButton, Button deleteSlideButton, 
                          Button moveUpButton, Button moveDownButton, Button refreshButton) {
        this.addSlideButton = addSlideButton;
        this.deleteSlideButton = deleteSlideButton;
        this.moveUpButton = moveUpButton;
        this.moveDownButton = moveDownButton;
        this.refreshButton = refreshButton;
        
        // Setup event handlers after injection
        setupEventHandlers();
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
        // Tree selection handler
        if (presentationTree != null) {
            presentationTree.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> handleSlideSelection(newValue)
            );
        }
        
        // Button handlers
        if (refreshButton != null) {
            refreshButton.setOnAction(e -> handleRefresh());
        }
        if (addSlideButton != null) {
            addSlideButton.setOnAction(e -> handleAddSlide());
        }
        if (deleteSlideButton != null) {
            deleteSlideButton.setOnAction(e -> handleDeleteSlide());
        }
        if (moveUpButton != null) {
            moveUpButton.setOnAction(e -> handleMoveSlideUp());
        }
        if (moveDownButton != null) {
            moveDownButton.setOnAction(e -> handleMoveSlideDown());
        }
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
                        
                        // Log to output tab
                        if (mainController.getOutputController() != null) {
                            mainController.getOutputController().showSlideAction(
                                "created", insertIndex + 1, true);
                        }
                        
                        // Refresh the presentation view
                        refreshPresentation();
                    } else {
                        showStatus("Failed to create slide: " + slideResult.getMessage());
                        
                        // Log error to output tab
                        if (mainController.getOutputController() != null) {
                            mainController.getOutputController().showSlideAction(
                                "creation failed", insertIndex + 1, false);
                        }
                    }
                } catch (Exception e) {
                    showStatus("Error creating slide: " + e.getMessage());
                    if (mainController.getOutputController() != null) {
                        mainController.getOutputController().showError("Add Slide", e);
                    }
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
                                
                                // Log to output tab
                                if (mainController.getOutputController() != null) {
                                    mainController.getOutputController().showSlideAction(
                                        "deleted", slideIndex + 1, true);
                                }
                                
                                // Refresh the presentation view
                                refreshPresentation();
                            } else {
                                showStatus("Failed to delete slide: " + slideResult.getMessage());
                                
                                // Log error to output tab
                                if (mainController.getOutputController() != null) {
                                    mainController.getOutputController().showSlideAction(
                                        "deletion failed", slideIndex + 1, false);
                                }
                            }
                        } catch (Exception e) {
                            showStatus("Error deleting slide: " + e.getMessage());
                            if (mainController.getOutputController() != null) {
                                mainController.getOutputController().showError("Delete Slide", e);
                            }
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
                        
                        // Log to output tab
                        if (mainController.getOutputController() != null) {
                            mainController.getOutputController().showSlideAction(
                                "moved up", slideIndex + 1, true);
                        }
                        
                        // Refresh the presentation view and maintain selection
                        refreshPresentation();
                        selectSlideByIndex(slideIndex - 1);
                    } else {
                        showStatus("Failed to move slide up: " + slideResult.getMessage());
                        
                        // Log error to output tab
                        if (mainController.getOutputController() != null) {
                            mainController.getOutputController().showSlideAction(
                                "move up failed", slideIndex + 1, false);
                        }
                    }
                } catch (Exception e) {
                    showStatus("Error moving slide up: " + e.getMessage());
                    if (mainController.getOutputController() != null) {
                        mainController.getOutputController().showError("Move Slide Up", e);
                    }
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
                        
                        // Log to output tab
                        if (mainController.getOutputController() != null) {
                            mainController.getOutputController().showSlideAction(
                                "moved down", slideIndex + 1, true);
                        }
                        
                        // Refresh the presentation view and maintain selection
                        refreshPresentation();
                        selectSlideByIndex(slideIndex + 1);
                    } else {
                        showStatus("Failed to move slide down: " + slideResult.getMessage());
                        
                        // Log error to output tab
                        if (mainController.getOutputController() != null) {
                            mainController.getOutputController().showSlideAction(
                                "move down failed", slideIndex + 1, false);
                        }
                    }
                } catch (Exception e) {
                    showStatus("Error moving slide down: " + e.getMessage());
                    if (mainController.getOutputController() != null) {
                        mainController.getOutputController().showError("Move Slide Down", e);
                    }
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