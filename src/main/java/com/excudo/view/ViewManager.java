package com.excudo.view;

import javafx.stage.Stage;
import javafx.stage.FileChooser;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.application.Platform;
import java.io.File;
import java.util.Optional;
import java.util.Map;
import java.util.HashMap;

/**
 * Centralized view management and navigation for the Excudo application.
 * Handles view transitions, dialog management, and application state coordination.
 */
public class ViewManager {
    
    private final Stage primaryStage;
    private final Map<String, Object> viewCache;
    private File currentPresentationFile;
    private boolean hasUnsavedChanges;
    
    // View state
    private String currentView;
    private Object currentViewController;
    
    public ViewManager(Stage primaryStage) {
        this.primaryStage = primaryStage;
        this.viewCache = new HashMap<>();
        this.currentPresentationFile = null;
        this.hasUnsavedChanges = false;
        this.currentView = "main";
    }
    
    // ========== FILE OPERATIONS ==========
    
    /**
     * Show file chooser dialog for opening PPTX files
     */
    public Optional<File> showOpenPresentationDialog() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open PowerPoint Presentation");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("PowerPoint Files", "*.pptx", "*.ppt"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        
        // Set initial directory to user home
        fileChooser.setInitialDirectory(new File(System.getProperty("user.home")));
        
        File selectedFile = fileChooser.showOpenDialog(primaryStage);
        return Optional.ofNullable(selectedFile);
    }
    
    /**
     * Show file chooser dialog for saving PPTX files
     */
    public Optional<File> showSavePresentationDialog() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save PowerPoint Presentation");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PowerPoint Files", "*.pptx")
        );
        
        // Default filename
        if (currentPresentationFile != null) {
            fileChooser.setInitialFileName(currentPresentationFile.getName());
            fileChooser.setInitialDirectory(currentPresentationFile.getParentFile());
        } else {
            fileChooser.setInitialFileName("presentation.pptx");
            fileChooser.setInitialDirectory(new File(System.getProperty("user.home")));
        }
        
        File selectedFile = fileChooser.showSaveDialog(primaryStage);
        return Optional.ofNullable(selectedFile);
    }
    
    /**
     * Show export dialog for different formats
     */
    public Optional<File> showExportDialog() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Presentation");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("PowerPoint Files", "*.pptx"),
                new FileChooser.ExtensionFilter("ZIP Archive", "*.zip"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        
        if (currentPresentationFile != null) {
            String baseName = currentPresentationFile.getName().replaceFirst("[.][^.]+$", "");
            fileChooser.setInitialFileName(baseName + "_exported.pptx");
            fileChooser.setInitialDirectory(currentPresentationFile.getParentFile());
        }
        
        File selectedFile = fileChooser.showSaveDialog(primaryStage);
        return Optional.ofNullable(selectedFile);
    }
    
    // ========== DIALOG MANAGEMENT ==========
    
    /**
     * Show information dialog
     */
    public void showInformation(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
    
    /**
     * Show warning dialog
     */
    public void showWarning(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
    
    /**
     * Show error dialog
     */
    public void showError(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
    
    /**
     * Show confirmation dialog
     */
    public boolean showConfirmation(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }
    
    /**
     * Show unsaved changes confirmation dialog
     */
    public boolean confirmUnsavedChanges() {
        if (!hasUnsavedChanges) {
            return true; // No changes to save
        }
        
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Unsaved Changes");
        alert.setHeaderText("You have unsaved changes");
        alert.setContentText("Do you want to save your changes before continuing?");
        
        alert.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO, ButtonType.CANCEL);
        
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent()) {
            if (result.get() == ButtonType.YES) {
                // Trigger save operation
                return performSaveOperation();
            } else if (result.get() == ButtonType.NO) {
                return true; // Discard changes
            }
        }
        return false; // Cancel operation
    }
    
    /**
     * Confirm application exit
     */
    public boolean confirmApplicationExit() {
        if (hasUnsavedChanges) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Exit Excudo");
            alert.setHeaderText("You have unsaved changes");
            alert.setContentText("Are you sure you want to exit without saving?");
            
            Optional<ButtonType> result = alert.showAndWait();
            return result.isPresent() && result.get() == ButtonType.OK;
        }
        return true;
    }
    
    // ========== APPLICATION STATE ==========
    
    /**
     * Set current presentation file
     */
    public void setCurrentPresentationFile(File file) {
        this.currentPresentationFile = file;
        updateWindowTitle();
    }
    
    /**
     * Get current presentation file
     */
    public Optional<File> getCurrentPresentationFile() {
        return Optional.ofNullable(currentPresentationFile);
    }
    
    /**
     * Mark presentation as having unsaved changes
     */
    public void markAsModified() {
        if (!hasUnsavedChanges) {
            hasUnsavedChanges = true;
            updateWindowTitle();
        }
    }
    
    /**
     * Mark presentation as saved (no unsaved changes)
     */
    public void markAsSaved() {
        if (hasUnsavedChanges) {
            hasUnsavedChanges = false;
            updateWindowTitle();
        }
    }
    
    /**
     * Check if there are unsaved changes
     */
    public boolean hasUnsavedChanges() {
        return hasUnsavedChanges;
    }
    
    /**
     * Update window title to reflect current file and save status
     */
    private void updateWindowTitle() {
        StringBuilder title = new StringBuilder("Excudo");
        
        if (currentPresentationFile != null) {
            title.append(" - ").append(currentPresentationFile.getName());
        } else {
            title.append(" - New Presentation");
        }
        
        if (hasUnsavedChanges) {
            title.append(" *");
        }
        
        Platform.runLater(() -> primaryStage.setTitle(title.toString()));
    }
    
    // ========== VIEW NAVIGATION ==========
    
    /**
     * Switch to main presentation view
     */
    public void showMainView() {
        currentView = "main";
        // Implementation would load and show main FXML view
    }
    
    /**
     * Switch to settings view
     */
    public void showSettingsView() {
        currentView = "settings";
        // Implementation would load and show settings FXML view
    }
    
    /**
     * Show about dialog
     */
    public void showAbout() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("About Excudo");
        alert.setHeaderText("Excudo v1.0");
        alert.setContentText("A technical PowerPoint manipulation tool with LLM integration.\\n\\n" +
                           "Built with JavaFX and powered by Claude AI.\\n" +
                           "© 2024 Excudo Team");
        alert.showAndWait();
    }
    
    // ========== VIEW CACHING ==========
    
    /**
     * Cache a view controller for reuse
     */
    public void cacheViewController(String viewName, Object controller) {
        viewCache.put(viewName, controller);
    }
    
    /**
     * Get cached view controller
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getCachedViewController(String viewName, Class<T> controllerClass) {
        Object controller = viewCache.get(viewName);
        if (controllerClass.isInstance(controller)) {
            return Optional.of((T) controller);
        }
        return Optional.empty();
    }
    
    /**
     * Clear view cache
     */
    public void clearViewCache() {
        viewCache.clear();
    }
    
    // ========== UTILITY METHODS ==========
    
    /**
     * Get primary stage
     */
    public Stage getPrimaryStage() {
        return primaryStage;
    }
    
    /**
     * Get current view name
     */
    public String getCurrentView() {
        return currentView;
    }
    
    /**
     * Perform save operation (to be implemented by controller)
     */
    private boolean performSaveOperation() {
        // This would be delegated to the main controller
        // For now, return true as placeholder
        return true;
    }
    
    /**
     * Show progress dialog for long-running operations
     */
    public void showProgress(String title, String message) {
        // Implementation would show a progress dialog
        // Could be a custom dialog with progress bar
    }
    
    /**
     * Hide progress dialog
     */
    public void hideProgress() {
        // Implementation would hide the progress dialog
    }
    
    /**
     * Show status message in status bar
     */
    public void showStatus(String message) {
        // Implementation would update status bar
        System.out.println("Status: " + message); // Placeholder
    }
    
    /**
     * Show temporary status message that auto-clears
     */
    public void showTemporaryStatus(String message, int durationMs) {
        showStatus(message);
        // Implementation would clear status after duration
    }
}