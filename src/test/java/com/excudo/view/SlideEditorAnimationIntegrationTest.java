package com.excudo.view;

import com.excudo.core.model.AnimationBinding;
import com.excudo.core.model.AnimationType;
import com.excudo.core.model.ParsedSlideData;
import com.excudo.core.model.ShapeRegistry;
import com.excudo.core.model.TimingTree;
import com.excudo.core.model.TimingNode;
import com.excudo.view.animation.AnimationController;
import javafx.scene.Group;
import javafx.scene.shape.Rectangle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Disabled;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Integration test for animation functionality within SlideEditorController.
 * Tests the end-to-end animation preview pipeline.
 */
@Disabled("Lives under src/test/java/com/excudo/view and is auto-excluded by the headless test runner's path-prefix filter; requires a GUI-enabled test environment.")
class SlideEditorAnimationIntegrationTest {
    
    @Test
    @DisplayName("Animation integration - Verify AnimationController with slide data")
    void testAnimationIntegrationWithRealSlideData() {
        // Create realistic slide data with animations
        ParsedSlideData slideData = createSlideDataWithAnimations();
        
        // Create mock scene graph with actual shapes
        Group sceneGraph = createMockSceneGraphWithShapes();
        
        // Test the integration by loading animations directly
        AnimationController animationController = createTestAnimationController();
        
        // Load animations - this should work without JavaFX Platform
        animationController.loadSlideAnimations(slideData, sceneGraph);
        
        // Verify the animation controller has the expected state
        var status = animationController.getAnimationStatus();
        assertNotNull(status, "Animation status should not be null");
        assertTrue(status.getTotalTriggers() > 0, "Should have animation triggers");
        
        // Verify animation sequences were created
        var sequences = animationController.getAnimationSequences();
        assertNotNull(sequences, "Animation sequences should not be null");
        assertFalse(sequences.isEmpty(), "Should have animation sequences");
    }
    
    @Test
    @DisplayName("Animation state management - Test basic controller operations")
    void testAnimationStateManagement() {
        AnimationController animationController = createTestAnimationController();
        ParsedSlideData slideData = createSlideDataWithAnimations();
        Group sceneGraph = createMockSceneGraphWithShapes();
        
        // Load animations
        animationController.loadSlideAnimations(slideData, sceneGraph);
        
        // Test initial state
        var initialStatus = animationController.getAnimationStatus();
        assertFalse(initialStatus.isPlaying(), "Should not be playing initially");
        assertFalse(initialStatus.isPaused(), "Should not be paused initially");
        assertEquals(0, initialStatus.getCurrentTrigger(), "Should start at trigger 0");
        
        // Test that we can call control methods without errors
        assertDoesNotThrow(() -> {
            animationController.playAnimations();
            animationController.pauseAnimations();
            animationController.resumeAnimations();
            animationController.stopAnimations();
            animationController.resetAnimations();
        }, "Animation control methods should not throw exceptions");
    }
    
    @Test
    @DisplayName("Animation shape registration - Test SPID mapping")
    void testAnimationShapeRegistration() {
        AnimationController animationController = createTestAnimationController();
        
        // Test shape registration
        Rectangle testShape = new Rectangle(100, 100);
        testShape.setUserData("spid:1");
        
        assertDoesNotThrow(() -> {
            animationController.registerShapeNode(1, testShape);
        }, "Shape registration should not throw exceptions");
        
        // Verify registered shapes
        var registeredShapes = animationController.getRegisteredShapes();
        assertNotNull(registeredShapes, "Registered shapes should not be null");
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
        animationBindings.add(new AnimationBinding(1, AnimationType.FADE, "in", "330", "0"));
        animationBindings.add(new AnimationBinding(2, AnimationType.FLY_IN_LEFT, "in", "500", "0"));
        animationBindings.add(new AnimationBinding(1, AnimationType.FADE, "out", "250", "0"));
        animationBindings.add(new AnimationBinding(2, AnimationType.WIPE_DOWN, "out", "400", "0"));
        
        // Create shape registry
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
        
        sceneGraph.getChildren().addAll(shape1, shape2);
        return sceneGraph;
    }
}