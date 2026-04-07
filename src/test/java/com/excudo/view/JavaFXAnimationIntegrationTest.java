package com.excudo.view;

import com.excudo.core.model.AnimationBinding;
import com.excudo.core.model.AnimationType;
import com.excudo.core.model.ParsedSlideData;
import com.excudo.core.model.ShapeRegistry;
import com.excudo.core.model.TimingTree;
import com.excudo.core.model.TimingNode;
import com.excudo.view.animation.AnimationController;
import com.excudo.view.animation.AnimationControllerListener;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Group;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Disabled;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Proper JavaFX integration test that initializes the JavaFX Toolkit.
 * Tests the complete animation integration pipeline with real JavaFX environment.
 */
@Disabled("Temporarily disabled while establishing stable CI/CD baseline - will re-enable systematically")
class JavaFXAnimationIntegrationTest {
    
    private static boolean javaFXInitialized = false;
    private static final Object initLock = new Object();
    
    @BeforeAll
    static void initializeJavaFX() throws InterruptedException {
        synchronized (initLock) {
            if (!javaFXInitialized) {
                CountDownLatch initLatch = new CountDownLatch(1);
                
                // Start JavaFX toolkit in a separate thread
                Thread javaFXThread = new Thread(() -> {
                    try {
                        // Launch a minimal JavaFX application to initialize toolkit
                        Application.launch(TestApplication.class);
                    } catch (Exception e) {
                        // JavaFX is already initialized, this is expected
                        initLatch.countDown();
                    }
                });
                javaFXThread.setDaemon(true);
                javaFXThread.start();
                
                // Alternative initialization method for headless environments
                Platform.runLater(() -> {
                    javaFXInitialized = true;
                    initLatch.countDown();
                });
                
                // Wait for initialization
                assertTrue(initLatch.await(10, TimeUnit.SECONDS), 
                          "JavaFX should initialize within 10 seconds");
            }
        }
    }
    
    @AfterAll
    static void cleanupJavaFX() {
        Platform.runLater(() -> {
            // Platform.exit(); // Don't exit as it might affect other tests
        });
    }
    
    @Test
    @DisplayName("Animation Integration - Verify full pipeline with JavaFX Toolkit")
    void testAnimationIntegrationWithJavaFXToolkit() throws InterruptedException {
        CountDownLatch animationsLoadedLatch = new CountDownLatch(1);
        CountDownLatch sequencesBuiltLatch = new CountDownLatch(1);
        AtomicReference<Exception> testException = new AtomicReference<>();
        
        Platform.runLater(() -> {
            try {
                // Create animation controller with real JavaFX environment
                AnimationController animationController = createTestAnimationController();
                
                // Create realistic slide data
                ParsedSlideData slideData = createSlideDataWithAnimations();
                Group sceneGraph = createMockSceneGraphWithShapes();
                
                // Set up comprehensive listener
                animationController.addListener(new AnimationControllerListener() {
                    @Override
                    public void onAnimationsLoaded(ParsedSlideData slideData) {
                        animationsLoadedLatch.countDown();
                    }
                    
                    @Override
                    public void onSequencesBuilt(int sequenceCount) {
                        sequencesBuiltLatch.countDown();
                        
                        // Verify sequences in JavaFX environment
                        assertTrue(sequenceCount > 0, "Should have animation sequences");
                        var status = animationController.getAnimationStatus();
                        assertNotNull(status, "Animation status should not be null");
                        assertEquals(sequenceCount, status.getTotalTriggers(), "Total triggers should match");
                    }
                    
                    @Override
                    public void onPlaybackStarted() {}
                    @Override
                    public void onPlaybackPaused() {}
                    @Override
                    public void onPlaybackResumed() {}
                    @Override
                    public void onPlaybackStopped() {}
                    @Override
                    public void onSequencePlayed(int triggerIndex) {}
                    @Override
                    public void onAnimationCompleted(int spid, boolean isEntrance) {}
                    @Override
                    public void onAutoAdvanceChanged(boolean enabled) {}
                });
                
                // Load animations - this should work in JavaFX environment
                animationController.loadSlideAnimations(slideData, sceneGraph);
                
            } catch (Exception e) {
                testException.set(e);
                animationsLoadedLatch.countDown();
                sequencesBuiltLatch.countDown();
            }
        });
        
        // Wait for operations to complete
        assertTrue(animationsLoadedLatch.await(5, TimeUnit.SECONDS), 
                  "Animations should be loaded within 5 seconds");
        assertTrue(sequencesBuiltLatch.await(5, TimeUnit.SECONDS), 
                  "Animation sequences should be built within 5 seconds");
        
        // Check for exceptions
        Exception exception = testException.get();
        if (exception != null) {
            fail("Animation integration failed: " + exception.getMessage(), exception);
        }
    }
    
    @Test
    @DisplayName("Animation Playback - Test with various animation types")
    void testAnimationPlaybackWithMultipleTypes() throws InterruptedException {
        CountDownLatch playbackStartedLatch = new CountDownLatch(1);
        CountDownLatch animationCompletedLatch = new CountDownLatch(2); // Expect 2 animation completions
        AtomicReference<Exception> testException = new AtomicReference<>();
        
        Platform.runLater(() -> {
            try {
                AnimationController animationController = createTestAnimationController();
                ParsedSlideData slideData = createSlideDataWithVariousAnimations();
                Group sceneGraph = createMockSceneGraphWithShapes();
                
                animationController.addListener(new AnimationControllerListener() {
                    @Override
                    public void onAnimationsLoaded(ParsedSlideData slideData) {}
                    @Override
                    public void onSequencesBuilt(int sequenceCount) {}
                    
                    @Override
                    public void onPlaybackStarted() {
                        playbackStartedLatch.countDown();
                    }
                    
                    @Override
                    public void onPlaybackPaused() {}
                    @Override
                    public void onPlaybackResumed() {}
                    @Override
                    public void onPlaybackStopped() {}
                    @Override
                    public void onSequencePlayed(int triggerIndex) {}
                    
                    @Override
                    public void onAnimationCompleted(int spid, boolean isEntrance) {
                        animationCompletedLatch.countDown();
                    }
                    
                    @Override
                    public void onAutoAdvanceChanged(boolean enabled) {}
                });
                
                // Load and start animations
                animationController.loadSlideAnimations(slideData, sceneGraph);
                animationController.playAnimations();
                
                // Test trigger advancement
                Platform.runLater(() -> {
                    animationController.nextTrigger();
                });
                
            } catch (Exception e) {
                testException.set(e);
                playbackStartedLatch.countDown();
            }
        });
        
        // Verify playback events
        assertTrue(playbackStartedLatch.await(5, TimeUnit.SECONDS), 
                  "Playback should start within 5 seconds");
        assertTrue(animationCompletedLatch.await(10, TimeUnit.SECONDS), 
                  "Animations should complete within 10 seconds");
        
        Exception exception = testException.get();
        if (exception != null) {
            fail("Animation playback failed: " + exception.getMessage(), exception);
        }
    }
    
    // ========== HELPER METHODS ==========
    
    private AnimationController createTestAnimationController() {
        com.excudo.view.rendering.CoordinateMapper mapper = 
            new com.excudo.view.rendering.CoordinateMapper(800, 600);
        return new AnimationController(mapper);
    }
    
    private ParsedSlideData createSlideDataWithAnimations() {
        // Create timing tree
        TimingTree timingTree = new TimingTree();
        TimingNode mainSequence = new TimingNode("1", "seq", null);
        TimingNode clickTrigger1 = new TimingNode("2", "par", "indefinite");
        TimingNode clickTrigger2 = new TimingNode("3", "par", "indefinite");
        
        mainSequence.addChild(clickTrigger1);
        mainSequence.addChild(clickTrigger2);
        timingTree.setRootNode(mainSequence);
        
        // Create animation bindings
        List<AnimationBinding> animationBindings = new ArrayList<>();
        animationBindings.add(new AnimationBinding(1, AnimationType.FADE, "in", "330", "indefinite"));
        animationBindings.add(new AnimationBinding(2, AnimationType.FLY_IN_LEFT, "in", "500", "0"));
        animationBindings.add(new AnimationBinding(1, AnimationType.FADE, "out", "250", "indefinite"));
        animationBindings.add(new AnimationBinding(2, AnimationType.WIPE_DOWN, "out", "400", "0"));
        
        ShapeRegistry shapeRegistry = new ShapeRegistry();
        return new ParsedSlideData(shapeRegistry, timingTree, animationBindings);
    }
    
    private ParsedSlideData createSlideDataWithVariousAnimations() {
        // Test more animation types
        TimingTree timingTree = new TimingTree();
        TimingNode mainSequence = new TimingNode("1", "seq", null);
        TimingNode clickTrigger1 = new TimingNode("2", "par", "indefinite");
        
        mainSequence.addChild(clickTrigger1);
        timingTree.setRootNode(mainSequence);
        
        List<AnimationBinding> animationBindings = new ArrayList<>();
        // Test different animation types
        animationBindings.add(new AnimationBinding(1, AnimationType.FLY_IN_RIGHT, "in", "400", "indefinite"));
        animationBindings.add(new AnimationBinding(2, AnimationType.WIPE_LEFT, "in", "600", "100"));
        animationBindings.add(new AnimationBinding(3, AnimationType.PULSE, "emphasis", "300", "200"));
        
        ShapeRegistry shapeRegistry = new ShapeRegistry();
        return new ParsedSlideData(shapeRegistry, timingTree, animationBindings);
    }
    
    private Group createMockSceneGraphWithShapes() {
        Group sceneGraph = new Group();
        
        // Create JavaFX shapes with SPID identification
        Rectangle shape1 = new Rectangle(100, 100, 150, 80);
        shape1.setUserData("spid:1");
        
        Rectangle shape2 = new Rectangle(300, 200, 120, 100);
        shape2.setUserData("spid:2");
        
        Rectangle shape3 = new Rectangle(500, 150, 100, 60);
        shape3.setUserData("spid:3");
        
        sceneGraph.getChildren().addAll(shape1, shape2, shape3);
        return sceneGraph;
    }
    
    /**
     * Minimal JavaFX Application for toolkit initialization
     */
    public static class TestApplication extends Application {
        @Override
        public void start(Stage primaryStage) {
            // Minimal setup for toolkit initialization
            primaryStage.setWidth(1);
            primaryStage.setHeight(1);
            primaryStage.show();
            
            // Hide immediately for headless testing
            Platform.runLater(() -> primaryStage.hide());
        }
    }
}