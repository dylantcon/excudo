package com.excudo.core.llm;

import com.excudo.core.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

/**
 * Comprehensive unit test for SlideContentContext
 * Tests JSON serialization, natural language generation, and slide analysis functionality
 * Uses real objects for reliable testing without mocking dependencies
 * 
 * @author Excudo Test Suite
 * @version 3.0 - Real Object Testing for High Coverage
 */
public class SlideContentContextTest {
    
    private SlideContentContext context;
    private ParsedSlideData slideData;
    private ShapeRegistry shapeRegistry;
    private TimingTree timingTree;
    private List<AnimationBinding> animationBindings;
    
    @BeforeEach
    void setUp() {
        // Create real objects for testing
        shapeRegistry = new ShapeRegistry();
        
        // Create test shapes with real geometries
        ShapeGeometry titleGeometry = new ShapeGeometry(1000000L, 1500000L, 7000000L, 1000000L);
        ShapeGeometry contentGeometry = new ShapeGeometry(1000000L, 3000000L, 7000000L, 2000000L);
        ShapeGeometry pictureGeometry = new ShapeGeometry(1000000L, 5500000L, 3000000L, 2000000L);
        
        SlideShape titleShape = new SlideShape(2, "Title 1", SlideShape.ShapeType.PLACEHOLDER, 
            "Welcome to the Presentation", titleGeometry, null);
        SlideShape contentShape = new SlideShape(3, "Content Placeholder 2", SlideShape.ShapeType.PLACEHOLDER,
            "This is the main content of the slide", contentGeometry, null);
        SlideShape pictureShape = new SlideShape(4, "Picture 3", SlideShape.ShapeType.PICTURE,
            null, pictureGeometry, null);
        
        shapeRegistry.addShape(titleShape);
        shapeRegistry.addShape(contentShape);
        shapeRegistry.addShape(pictureShape);
        
        // Create timing tree with basic structure
        timingTree = new TimingTree();
        TimingNode rootNode = new TimingNode("1", "seq", "indefinite");
        timingTree.setRootNode(rootNode);
        
        // Create animation bindings
        animationBindings = new ArrayList<>();
        animationBindings.add(new AnimationBinding(2, AnimationType.FADE, "in", "500ms", "0ms"));
        animationBindings.add(new AnimationBinding(3, AnimationType.FLY_IN_LEFT, "in", "1000ms", "0ms"));
        
        // Create slide data
        slideData = new ParsedSlideData(shapeRegistry, timingTree, animationBindings);
        
        // Create context
        context = new SlideContentContext(1, slideData);
    }
    
    @Test
    void testConstructor() {
        assertEquals(1, context.getSlideNumber());
        assertEquals(slideData, context.getSlideData());
    }
    
    @Test
    void testToJsonBasicStructure() {
        String json = context.toJson();
        
        assertNotNull(json);
        assertTrue(json.contains("\"slideNumber\": 1"));
        assertTrue(json.contains("\"shapes\":"));
        assertTrue(json.contains("\"animations\":"));
        assertTrue(json.contains("\"animationSequence\":"));
        assertTrue(json.contains("\"timingTree\":"));
        assertTrue(json.contains("\"shapeHierarchy\":"));
        assertTrue(json.contains("\"capabilities\":"));
    }
    
    @Test
    void testToJsonShapeSerialization() {
        String json = context.toJson();
        
        // Check that shapes are included
        assertTrue(json.contains("\"spid\": 2"));
        assertTrue(json.contains("\"spid\": 3"));
        assertTrue(json.contains("\"spid\": 4"));
        
        // Check shape names and content
        assertTrue(json.contains("\"name\": \"Title 1\""));
        assertTrue(json.contains("\"name\": \"Content Placeholder 2\""));
        assertTrue(json.contains("\"name\": \"Picture 3\""));
        assertTrue(json.contains("\"text\": \"Welcome to the Presentation\""));
        assertTrue(json.contains("\"text\": \"This is the main content of the slide\""));
        
        // Check geometry serialization
        assertTrue(json.contains("\"geometry\":"));
        assertTrue(json.contains("\"x\": 1000000"));
        assertTrue(json.contains("\"y\": 1500000") || json.contains("\"y\": 3000000"));
    }
    
    @Test
    void testToJsonAnimationSerialization() {
        String json = context.toJson();
        
        // Check that animations are included
        assertTrue(json.contains("\"targetSpid\": 2"));
        assertTrue(json.contains("\"targetSpid\": 3"));
        assertTrue(json.contains("\"animationType\": \"FADE\""));
        assertTrue(json.contains("\"animationType\": \"FLY_IN_LEFT\""));
        assertTrue(json.contains("\"transition\": \"in\""));
        assertTrue(json.contains("\"duration\": \"500ms\""));
        assertTrue(json.contains("\"duration\": \"1000ms\""));
    }
    
    @Test
    void testToJsonAnimationSequence() {
        String json = context.toJson();
        
        // Check animation sequence structure
        assertTrue(json.contains("\"animationSequence\""));
        assertTrue(json.contains("\"totalClicks\""));
        assertTrue(json.contains("\"clicks\""));
        assertTrue(json.contains("\"clickNumber\": 1"));
    }
    
    @Test
    void testToJsonTimingTreeSerialization() {
        String json = context.toJson();
        
        assertTrue(json.contains("\"timingTree\":"));
        assertTrue(json.contains("\"rootNode\":"));
        assertTrue(json.contains("\"metadata\":"));
        assertTrue(json.contains("\"totalNodes\":"));
        assertTrue(json.contains("\"clickTriggers\":"));
        assertTrue(json.contains("\"maxDepth\":"));
    }
    
    @Test
    void testToJsonShapeHierarchy() {
        String json = context.toJson();
        
        assertTrue(json.contains("\"shapeHierarchy\":"));
        assertTrue(json.contains("\"byType\":"));
        assertTrue(json.contains("\"byPosition\":"));
        assertTrue(json.contains("\"logicalGroups\":"));
        assertTrue(json.contains("\"PLACEHOLDER\":"));
    }
    
    @Test
    void testToJsonCapabilities() {
        String json = context.toJson();
        
        assertTrue(json.contains("\"capabilities\":"));
        assertTrue(json.contains("\"shapeOperations\":"));
        assertTrue(json.contains("\"animationOperations\":"));
        assertTrue(json.contains("\"animationTypes\":"));
        assertTrue(json.contains("add-shape"));
        assertTrue(json.contains("add-animation"));
    }
    
    @Test
    void testToNaturalLanguageBasicStructure() {
        String description = context.toNaturalLanguage();
        
        assertNotNull(description);
        assertTrue(description.contains("Slide 1 contains:"));
        assertTrue(description.contains("Text elements:"));
        assertTrue(description.contains("Welcome to the Presentation"));
        assertTrue(description.contains("This is the main content"));
    }
    
    @Test
    void testToNaturalLanguageAnimationDescription() {
        String description = context.toNaturalLanguage();
        
        assertTrue(description.contains("2 animations configured:"));
        assertTrue(description.contains("Animation sequence:"));
        assertTrue(description.contains("Click"));
        assertTrue(description.contains("FADE animation"));
        assertTrue(description.contains("FLY_IN_LEFT animation"));
    }
    
    @Test
    void testEmptyShapeList() {
        // Create empty slide data
        ShapeRegistry emptyRegistry = new ShapeRegistry();
        ParsedSlideData emptySlideData = new ParsedSlideData(emptyRegistry, timingTree, animationBindings);
        SlideContentContext emptyContext = new SlideContentContext(1, emptySlideData);
        
        String json = emptyContext.toJson();
        String description = emptyContext.toNaturalLanguage();
        
        assertTrue(json.contains("\"shapes\": []"));
        assertFalse(description.contains("Text elements:"));
    }
    
    @Test
    void testEmptyAnimationList() {
        // Create slide data with no animations
        ParsedSlideData noAnimData = new ParsedSlideData(shapeRegistry, timingTree, Collections.emptyList());
        SlideContentContext noAnimContext = new SlideContentContext(1, noAnimData);
        
        String json = noAnimContext.toJson();
        String description = noAnimContext.toNaturalLanguage();
        
        assertTrue(json.contains("\"animations\": []"));
        assertTrue(json.contains("\"totalClicks\": 0"));
        assertFalse(description.contains("animations configured:"));
    }
    
    @Test
    void testNullTimingTree() {
        // Create timing tree with null root
        TimingTree nullTree = new TimingTree();
        nullTree.setRootNode(null);
        ParsedSlideData nullTimingData = new ParsedSlideData(shapeRegistry, nullTree, animationBindings);
        SlideContentContext nullTimingContext = new SlideContentContext(1, nullTimingData);
        
        String json = nullTimingContext.toJson();
        String description = nullTimingContext.toNaturalLanguage();
        
        assertNotNull(json);
        assertNotNull(description);
        assertTrue(json.contains("\"timingTree\":"));
    }
    
    @Test
    void testJsonEscaping() {
        // Create shape with special characters requiring escaping
        ShapeRegistry escapingRegistry = new ShapeRegistry();
        SlideShape specialShape = new SlideShape(5, "Test \"Shape\" with quotes", SlideShape.ShapeType.RECTANGLE,
            "Line 1\nLine 2\tTabbed", null, null);
        escapingRegistry.addShape(specialShape);
        
        ParsedSlideData escapingData = new ParsedSlideData(escapingRegistry, timingTree, Collections.emptyList());
        SlideContentContext escapingContext = new SlideContentContext(1, escapingData);
        
        String json = escapingContext.toJson();
        
        // Check proper JSON escaping
        assertTrue(json.contains("Test \\\"Shape\\\" with quotes"));
        assertTrue(json.contains("Line 1\\nLine 2\\tTabbed"));
    }
    
    @Test
    void testComplexTimingTreeStructure() {
        // Create timing tree with click triggers
        TimingTree complexTree = new TimingTree();
        TimingNode complexRoot = new TimingNode("1", "seq", "indefinite");
        
        // Add click trigger child
        TimingNode clickNode = new TimingNode("2", "par", "indefinite");
        clickNode.setDelay("indefinite"); // This makes it a click trigger
        complexRoot.addChild(clickNode);
        
        complexTree.setRootNode(complexRoot);
        
        ParsedSlideData complexData = new ParsedSlideData(shapeRegistry, complexTree, animationBindings);
        SlideContentContext complexContext = new SlideContentContext(1, complexData);
        
        String json = complexContext.toJson();
        String description = complexContext.toNaturalLanguage();
        
        assertTrue(json.contains("\"isClickTrigger\": true"));
        assertTrue(description.contains("click triggers"));
        assertTrue(description.contains("Animation grouping:"));
    }
    
    @Test
    void testNullAnimationProperties() {
        // Create animation with null properties
        List<AnimationBinding> nullPropsAnimations = new ArrayList<>();
        nullPropsAnimations.add(new AnimationBinding(5, AnimationType.APPEAR, "in", null, null));
        
        ParsedSlideData nullPropsData = new ParsedSlideData(shapeRegistry, timingTree, nullPropsAnimations);
        SlideContentContext nullPropsContext = new SlideContentContext(1, nullPropsData);
        
        String json = nullPropsContext.toJson();
        
        // Should handle null properties gracefully
        assertTrue(json.contains("\"targetSpid\": 5"));
        assertTrue(json.contains("\"animationType\": \"APPEAR\""));
        assertFalse(json.contains("\"duration\": null"));
        assertFalse(json.contains("\"delay\": null"));
    }
    
    @Test
    void testShapePositionCategorization() {
        String json = context.toJson();
        
        // Should categorize shapes by position
        assertTrue(json.contains("\"byPosition\":"));
        
        // With mock geometry (x=1000000, y=2000000), should categorize position
        assertTrue(json.contains("top-left") || json.contains("middle-") || json.contains("top-"));
    }
    
    @Test
    void testLogicalGroupIdentification() {
        String json = context.toJson();
        
        assertTrue(json.contains("\"logicalGroups\":"));
        
        // Should identify placeholder groups since we have placeholder shapes
        assertTrue(json.contains("title-group") || json.contains("content-group"));
    }
}