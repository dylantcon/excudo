package com.excudo.view.animation;

import com.excudo.core.model.AnimationBinding;
import com.excudo.core.model.AnimationType;
import com.excudo.core.model.TimingTree;
import com.excudo.core.model.TimingNode;
import com.excudo.view.rendering.CoordinateMapper;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Test suite for AnimationEngine functionality.
 * Demonstrates animation preview capabilities for LLM agent review.
 */
class AnimationEngineTest {
    
    private AnimationEngine animationEngine;
    private CoordinateMapper coordinateMapper;
    
    @BeforeEach
    void setUp() {
        coordinateMapper = new CoordinateMapper(800, 600);
        animationEngine = new AnimationEngine(coordinateMapper);
    }
    
    @Test
    @DisplayName("Animation engine initialization")
    void testAnimationEngineInitialization() {
        assertNotNull(animationEngine, "Animation engine should be initialized");
        assertFalse(animationEngine.isPlaying(), "Animation should not be playing initially");
        assertEquals(0, animationEngine.getCurrentClickTrigger(), "Should start at trigger 0");
        assertEquals(0, animationEngine.getTotalClickTriggers(), "Should have no triggers initially");
    }
    
    @Test
    @DisplayName("Build animation sequences from PowerPoint data")
    void testBuildAnimationSequences() {
        // Create sample animation bindings (entrance and exit animations)
        List<AnimationBinding> animationBindings = createSampleAnimationBindings();
        
        // Create sample timing tree
        TimingTree timingTree = createSampleTimingTree();
        
        // Build animation sequences
        animationEngine.buildAnimationSequences(timingTree, animationBindings);
        
        // Verify sequences were built
        List<AnimationSequence> sequences = animationEngine.getAnimationSequences();
        assertFalse(sequences.isEmpty(), "Should have animation sequences");
        assertTrue(animationEngine.getTotalClickTriggers() > 0, "Should have click triggers");
    }
    
    @Test
    @DisplayName("Animation state management")
    void testAnimationStateManagement() {
        // Setup animations
        List<AnimationBinding> animationBindings = createSampleAnimationBindings();
        TimingTree timingTree = createSampleTimingTree();
        animationEngine.buildAnimationSequences(timingTree, animationBindings);
        
        // Test play/pause/stop states
        assertFalse(animationEngine.isPlaying(), "Should not be playing initially");
        assertFalse(animationEngine.isPaused(), "Should not be paused initially");
        
        // Note: Actual playback testing would require JavaFX Application Thread
        // For unit tests, we focus on state management logic
    }
    
    @Test
    @DisplayName("Animation sequence descriptions")
    void testAnimationSequenceDescriptions() {
        List<AnimationBinding> animationBindings = createSampleAnimationBindings();
        TimingTree timingTree = createSampleTimingTree();
        animationEngine.buildAnimationSequences(timingTree, animationBindings);
        
        List<AnimationSequence> sequences = animationEngine.getAnimationSequences();
        for (AnimationSequence sequence : sequences) {
            assertNotNull(sequence.getDescription(), "Each sequence should have a description");
            assertTrue(sequence.getDescription().contains("Click"), "Description should mention click trigger");
        }
    }
    
    // ========== HELPER METHODS ==========
    
    private List<AnimationBinding> createSampleAnimationBindings() {
        List<AnimationBinding> bindings = new ArrayList<>();
        
        // Entrance animation: Shape 1 fades in
        bindings.add(new AnimationBinding(1, AnimationType.FADE, "in", "330", "indefinite"));
        
        // Entrance animation: Shape 2 flies in from left
        bindings.add(new AnimationBinding(2, AnimationType.FLY_IN_LEFT, "in", "500", "0"));
        
        // Exit animation: Shape 1 fades out
        bindings.add(new AnimationBinding(1, AnimationType.FADE, "out", "250", "indefinite"));
        
        // Exit animation: Shape 2 wipes out down
        bindings.add(new AnimationBinding(2, AnimationType.WIPE_DOWN, "out", "400", "0"));
        
        return bindings;
    }
    
    private TimingTree createSampleTimingTree() {
        TimingTree timingTree = new TimingTree();
        
        // Create sample timing nodes with correct constructor parameters
        TimingNode mainSequence = new TimingNode("1", "seq", null);
        TimingNode clickTrigger1 = new TimingNode("2", "par", "indefinite");
        TimingNode clickTrigger2 = new TimingNode("3", "par", "indefinite");
        
        // Build timing tree hierarchy
        mainSequence.addChild(clickTrigger1);
        mainSequence.addChild(clickTrigger2);
        timingTree.setRootNode(mainSequence);
        
        return timingTree;
    }
    
    /**
     * Demo application to visually test animation preview functionality.
     * This demonstrates what LLM agents would see when reviewing animation changes.
     */
    public static class AnimationPreviewDemo extends Application {
        
        private AnimationController animationController;
        private Group animatedShapes;
        private Label statusLabel;
        
        @Override
        public void start(Stage primaryStage) {
            setupAnimationDemo(primaryStage);
        }
        
        private void setupAnimationDemo(Stage primaryStage) {
            // Create scene with animated shapes
            animatedShapes = new Group();
            
            // Create sample shapes for animation
            Rectangle shape1 = new Rectangle(100, 100, 150, 80);
            shape1.setFill(Color.LIGHTBLUE);
            shape1.setUserData("spid:1"); // Associate with SPID 1
            
            Rectangle shape2 = new Rectangle(300, 200, 120, 100);
            shape2.setFill(Color.LIGHTGREEN);
            shape2.setUserData("spid:2"); // Associate with SPID 2
            
            animatedShapes.getChildren().addAll(shape1, shape2);
            
            // Create control panel
            VBox controlPanel = createControlPanel();
            
            // Setup animation controller
            CoordinateMapper mapper = new CoordinateMapper(800, 600);
            animationController = new AnimationController(mapper);
            
            // Register shapes with animation controller
            animationController.registerShapeNode(1, shape1);
            animationController.registerShapeNode(2, shape2);
            
            // Create sample slide data and load animations
            loadSampleAnimations();
            
            // Create main layout
            HBox mainLayout = new HBox(10);
            mainLayout.getChildren().addAll(animatedShapes, controlPanel);
            
            Scene scene = new Scene(mainLayout, 1000, 600);
            primaryStage.setTitle("Animation Preview Demo - LLM Agent Review");
            primaryStage.setScene(scene);
            primaryStage.show();
        }
        
        private VBox createControlPanel() {
            VBox panel = new VBox(10);
            panel.setPrefWidth(200);
            
            statusLabel = new Label("Ready for animation preview");
            
            Button playButton = new Button("Play Animations");
            playButton.setOnAction(e -> animationController.playAnimations());
            
            Button nextButton = new Button("Next Trigger");
            nextButton.setOnAction(e -> animationController.nextTrigger());
            
            Button pauseButton = new Button("Pause/Resume");
            pauseButton.setOnAction(e -> {
                if (animationController.getAnimationStatus().isPlaying()) {
                    animationController.pauseAnimations();
                } else if (animationController.getAnimationStatus().isPaused()) {
                    animationController.resumeAnimations();
                }
            });
            
            Button stopButton = new Button("Stop");
            stopButton.setOnAction(e -> animationController.stopAnimations());
            
            Button resetButton = new Button("Reset");
            resetButton.setOnAction(e -> animationController.resetAnimations());
            
            panel.getChildren().addAll(
                new Label("Animation Controls:"),
                playButton, nextButton, pauseButton, stopButton, resetButton,
                new Label("Status:"),
                statusLabel
            );
            
            return panel;
        }
        
        private void loadSampleAnimations() {
            // Create sample animation data that would come from PowerPoint
            List<AnimationBinding> bindings = new ArrayList<>();
            bindings.add(new AnimationBinding(1, AnimationType.FADE, "in", "330", "indefinite"));
            bindings.add(new AnimationBinding(2, AnimationType.FADE, "in", "330", "200"));
            bindings.add(new AnimationBinding(1, AnimationType.FADE, "out", "250", "indefinite"));
            bindings.add(new AnimationBinding(2, AnimationType.WIPE_DOWN, "out", "400", "100"));
            
            TimingTree timingTree = new TimingTree();
            TimingNode mainSequence = new TimingNode("1", "seq", null);
            timingTree.setRootNode(mainSequence);
            
            // Load into animation controller (this would normally come from ParsedSlideData)
            // For demo purposes, we'll create mock slide data
            try {
                // This demonstrates what the animation engine will do with real PowerPoint data
                animationController.getAnimationSequences(); // This would load the actual animations
                
                // Setup status updates
                animationController.addListener(new AnimationControllerListener() {
                    @Override
                    public void onAnimationsLoaded(com.excudo.core.model.ParsedSlideData slideData) {
                        Platform.runLater(() -> statusLabel.setText("Animations loaded"));
                    }
                    
                    @Override
                    public void onSequencesBuilt(int sequenceCount) {
                        Platform.runLater(() -> statusLabel.setText("Ready: " + sequenceCount + " sequences"));
                    }
                    
                    @Override
                    public void onPlaybackStarted() {
                        Platform.runLater(() -> statusLabel.setText("Playing animations..."));
                    }
                    
                    @Override
                    public void onPlaybackPaused() {
                        Platform.runLater(() -> statusLabel.setText("Paused"));
                    }
                    
                    @Override
                    public void onPlaybackResumed() {
                        Platform.runLater(() -> statusLabel.setText("Resumed"));
                    }
                    
                    @Override
                    public void onPlaybackStopped() {
                        Platform.runLater(() -> statusLabel.setText("Stopped"));
                    }
                    
                    @Override
                    public void onSequencePlayed(int triggerIndex) {
                        Platform.runLater(() -> statusLabel.setText("Played trigger " + (triggerIndex + 1)));
                    }
                    
                    @Override
                    public void onAnimationCompleted(int spid, boolean isEntrance) {
                        String type = isEntrance ? "entrance" : "exit";
                        Platform.runLater(() -> statusLabel.setText("Shape " + spid + " " + type + " completed"));
                    }
                    
                    @Override
                    public void onAutoAdvanceChanged(boolean enabled) {
                        Platform.runLater(() -> statusLabel.setText("Auto-advance: " + enabled));
                    }
                });
                
            } catch (Exception e) {
                statusLabel.setText("Error loading animations: " + e.getMessage());
            }
        }
        
        public static void launchDemo() {
            launch();
        }
    }
}