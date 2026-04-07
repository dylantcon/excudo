package com.excudo.view;

import com.excudo.core.orchestration.PresentationMetadata;
import com.excudo.core.orchestration.SlideMetadata;
import com.excudo.core.orchestration.SlideType;
import com.excudo.view.components.PresentationExplorerController;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.control.TreeView;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test PresentationExplorerController in headless mode
 */
public class PresentationExplorerTest {
    
    private PresentationExplorerController controller;
    private TreeView<String> treeView;
    private Label presentationInfo;
    private Button addSlideButton;
    private Button deleteSlideButton;
    
    @BeforeAll
    static void initializeJavaFX() {
        // Initialize JavaFX toolkit for headless testing with proper error handling
        try {
            // Use System property to enable headless mode
            System.setProperty("java.awt.headless", "true");
            System.setProperty("testfx.robot", "glass");
            System.setProperty("testfx.headless", "true");
            System.setProperty("prism.order", "sw");
            System.setProperty("glass.platform", "Monocle");
            System.setProperty("monocle.platform", "Headless");
            
            // Initialize JavaFX Platform
            if (!Platform.isFxApplicationThread()) {
                new JFXPanel(); // This will initialize the FX toolkit
            }
        } catch (Exception e) {
            // If JavaFX initialization fails, skip this test class
            org.junit.jupiter.api.Assumptions.assumeTrue(false, 
                "JavaFX initialization failed in headless environment: " + e.getMessage());
        }
    }
    
    @BeforeEach
    void setUp() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        
        Platform.runLater(() -> {
            try {
                // Create controller and mock FXML components
                controller = new PresentationExplorerController();
                
                // Create and inject FXML components
                treeView = new TreeView<>();
                presentationInfo = new Label();
                addSlideButton = new Button("Add");
                deleteSlideButton = new Button("Delete");
                
                // Set the components (simulate FXML injection)
                controller.setPresentationTree(treeView);
                
                // Initialize the controller
                controller.initialize(null, null);
                
                latch.countDown();
            } catch (Exception e) {
                e.printStackTrace();
                latch.countDown();
            }
        });
        
        assertTrue(latch.await(5, TimeUnit.SECONDS), "JavaFX initialization timed out");
    }
    
    @Test
    void testUpdatePresentationWithValidData() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        
        Platform.runLater(() -> {
            try {
                // Create test data
                PresentationMetadata metadata = createTestMetadata();
                List<SlideMetadata> slides = createTestSlides();
                
                System.out.println("TEST: Before updatePresentation()");
                System.out.println("  Metadata: " + metadata.getTitle());
                System.out.println("  Slide count: " + slides.size());
                
                // Call updatePresentation
                controller.updatePresentation(metadata, slides);
                
                // Verify state
                System.out.println("TEST: After updatePresentation()");
                System.out.println("  Tree root: " + treeView.getRoot());
                if (treeView.getRoot() != null) {
                    System.out.println("  Tree root value: " + treeView.getRoot().getValue());
                    System.out.println("  Tree children count: " + treeView.getRoot().getChildren().size());
                }
                
                // Verify current state
                PresentationMetadata currentPresentation = controller.getCurrentPresentation();
                List<SlideMetadata> currentSlides = controller.getCurrentSlides();
                
                System.out.println("TEST: Current state");
                System.out.println("  Current presentation: " + (currentPresentation != null ? currentPresentation.getTitle() : "NULL"));
                System.out.println("  Current slides: " + (currentSlides != null ? currentSlides.size() : "NULL"));
                
                // Assertions
                assertNotNull(currentPresentation, "Current presentation should not be null");
                assertEquals("Current Presentation", currentPresentation.getTitle());
                assertNotNull(currentSlides, "Current slides should not be null");
                assertEquals(2, currentSlides.size());
                
                latch.countDown();
            } catch (Exception e) {
                e.printStackTrace();
                latch.countDown();
            }
        });
        
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Test execution timed out");
    }
    
    @Test
    void testButtonStatesWithPresentationLoaded() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        
        Platform.runLater(() -> {
            try {
                // Create test data
                PresentationMetadata metadata = createTestMetadata();
                List<SlideMetadata> slides = createTestSlides();
                
                // Simulate FXML injection for buttons
                java.lang.reflect.Field addSlideField = PresentationExplorerController.class.getDeclaredField("addSlideButton");
                addSlideField.setAccessible(true);
                addSlideField.set(controller, addSlideButton);
                
                java.lang.reflect.Field deleteSlideField = PresentationExplorerController.class.getDeclaredField("deleteSlideButton");
                deleteSlideField.setAccessible(true);
                deleteSlideField.set(controller, deleteSlideButton);
                
                // Update presentation
                controller.updatePresentation(metadata, slides);
                
                // Check button states
                System.out.println("TEST: Button states after updatePresentation()");
                System.out.println("  Add button disabled: " + addSlideButton.isDisabled());
                System.out.println("  Delete button disabled: " + deleteSlideButton.isDisabled());
                
                // Add button should be enabled when presentation is loaded
                assertFalse(addSlideButton.isDisabled(), "Add button should be enabled when presentation is loaded");
                
                latch.countDown();
            } catch (Exception e) {
                e.printStackTrace();
                latch.countDown();
            }
        });
        
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Test execution timed out");
    }
    
    private PresentationMetadata createTestMetadata() {
        return new PresentationMetadata(
            2, // slideCount
            "Current Presentation", // title
            java.time.Instant.now(), // lastModified
            List.of("Test Author") // authors
        );
    }
    
    private List<SlideMetadata> createTestSlides() {
        SlideMetadata slide1 = new SlideMetadata(
            1, // slideNumber
            "Slide 1", // title
            SlideType.CONTENT, // type
            2, // shapeCount
            Arrays.asList(2, 3) // spids
        );
        
        SlideMetadata slide2 = new SlideMetadata(
            2, // slideNumber
            "Slide 2", // title
            SlideType.CONTENT, // type
            3, // shapeCount
            Arrays.asList(2, 3, 4) // spids
        );
        
        return Arrays.asList(slide1, slide2);
    }
}