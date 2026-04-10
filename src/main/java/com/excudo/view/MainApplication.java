package com.excudo.view;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import java.io.IOException;

/**
 * Main JavaFX Application class for Excudo.
 * Initializes the primary application window and manages global application state.
 */
public class MainApplication extends Application {
    
    private Stage primaryStage;
    private ViewManager viewManager;
    
    public static void main(String[] args) {
        launch(args);
    }
    
    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        
        try {
            initializeApplication();
            showMainWindow();
        } catch (Exception e) {
            showErrorDialog("Application Startup Error", 
                          "Failed to start Excudo", e);
            System.exit(1);
        }
    }
    
    private void initializeApplication() {
        // Configure primary stage
        primaryStage.setTitle("Excudo - PowerPoint XML Editor");
        primaryStage.setMinWidth(1200);
        primaryStage.setMinHeight(800);
        
        // Initialize view manager
        viewManager = new ViewManager(primaryStage);
        
        // Set application icon (if available)
        // primaryStage.getIcons().add(new Image("icon.png"));
        
        // Configure application shutdown behavior
        primaryStage.setOnCloseRequest(event -> {
            if (!viewManager.confirmApplicationExit()) {
                event.consume(); // Cancel the close request
            }
        });
    }
    
    private void showMainWindow() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainApplication.fxml"));
        Scene scene = new Scene(loader.load());

        // Apply CSS styling
        scene.getStylesheets().add(getClass().getResource("/styles/application.css").toExternalForm());

        primaryStage.setScene(scene);
        primaryStage.show();

        // Initialize the main controller after scene is set
        MainController controller = loader.getController();
        controller.initialize(viewManager);
    }
    
    private void showErrorDialog(String title, String header, Exception e) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(e.getMessage());
        alert.showAndWait();
    }
    
    /**
     * Get the primary application stage
     */
    public Stage getPrimaryStage() {
        return primaryStage;
    }
    
    /**
     * Get the view manager for this application
     */
    public ViewManager getViewManager() {
        return viewManager;
    }
}