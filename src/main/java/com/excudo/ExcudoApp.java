package com.excudo;

import com.excudo.view.MainController;
import com.excudo.view.ViewManager;
import com.excudo.core.utils.Logger;
import com.excudo.core.utils.ComponentLogger;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.application.Platform;
import java.io.IOException;

/**
 * Main JavaFX application class for Excudo.
 * Initializes the application, loads the main view, and manages application lifecycle.
 */
public class ExcudoApp extends Application {
    
    private static final ComponentLogger logger = Logger.getLogger(ExcudoApp.class);
    private ViewManager viewManager;
    private MainController mainController;
    private Stage primaryStage;
    
    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        
        try {
            // Initialize view manager
            viewManager = new ViewManager(primaryStage);
            
            // Load and setup main view
            setupMainView();
            
            // Configure primary stage
            setupPrimaryStage();
            
            // Show the application
            primaryStage.show();
            
        } catch (Exception e) {
            showStartupError(e);
        }
    }
    
    /**
     * Setup the main application view
     */
    private void setupMainView() throws IOException {
        // Load FXML and get controller
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainApplication.fxml"));
        BorderPane root = loader.load();
        
        // Get the controller from FXML loader
        mainController = loader.getController();
        mainController.initialize(viewManager);
        
        // Create scene with FXML root
        Scene scene = new Scene(root, 1200, 800);
        scene.getStylesheets().add(getClass().getResource("/styles/application.css").toExternalForm());
        
        primaryStage.setScene(scene);
    }
    
    
    /**
     * Configure primary stage properties
     */
    private void setupPrimaryStage() {
        primaryStage.setTitle("Excudo");
        primaryStage.setMinWidth(800);
        primaryStage.setMinHeight(600);
        
        // Handle close request
        primaryStage.setOnCloseRequest(event -> {
            if (viewManager != null && !viewManager.confirmApplicationExit()) {
                event.consume();
            } else {
                Platform.exit();
                System.exit(0);
            }
        });
    }
    
    /**
     * Show startup error dialog
     */
    private void showStartupError(Exception e) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Startup Error");
        alert.setHeaderText("Failed to start Excudo");
        alert.setContentText("Error: " + e.getMessage());
        alert.showAndWait();
        
        Platform.exit();
        System.exit(1);
    }
    
    @Override
    public void stop() throws Exception {
        // Cleanup resources
        if (viewManager != null) {
            viewManager.clearViewCache();
        }
        
        super.stop();
    }
    
    /**
     * Main entry point
     */
    public static void main(String[] args) {
        // Set system properties for better JavaFX experience
        System.setProperty("prism.lcdtext", "false");
        System.setProperty("prism.text", "t2k");
        
        // Check if JavaFX is available
        try {
            Class.forName("javafx.application.Application");
        } catch (ClassNotFoundException e) {
            logger.error("JavaFX not available. This application requires JavaFX to run.");
            logger.error("Please install JavaFX or run with --module-path pointing to JavaFX modules.");
            System.exit(1);
        }
        
        launch(args);
    }
}