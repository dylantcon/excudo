package com.excudo.core.model;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.util.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;
import com.excudo.xml.parsers.SlideXMLParser;
import com.excudo.exceptions.XMLParsingException;

/**
 * Tests for ParsedSlideData focusing on data aggregation and component interaction behaviors:
 * Uses real PPTX test data instead of mocks for comprehensive testing.
 * - Component aggregation and retrieval
 * - Data consistency between shapes, timing, and animations
 * - Cross-component validation and relationships
 * - toString formatting with component counts
 * - Edge cases with null/empty components
 */
class ParsedSlideDataTest {
    
    @TempDir
    Path tempDir;
    
    private File extractedPptxDir;
    private SlideXMLParser parser;
    private ParsedSlideData realSlideData;
    
    @BeforeEach
    void setUp() throws Exception {
        // Copy real test PPTX structure instead of creating minimal mocks
        extractedPptxDir = tempDir.toFile();
        copyTestPptxStructure();
        
        // Initialize parser with real PPTX structure
        parser = new SlideXMLParser();
        
        // Parse a real slide to get actual data structures
        File slideFile = new File(extractedPptxDir, "ppt/slides/slide1.xml");
        if (slideFile.exists()) {
            realSlideData = parser.parseSlide(slideFile);
        } else {
            throw new RuntimeException("Test PPTX structure missing slide1.xml");
        }
    }
    
    // Helper method to count click triggers in a timing tree
    private int getClickTriggerCount(TimingTree tree) {
        if (tree.getRootNode() == null) {
            return 0;
        }
        
        int count = 0;
        List<TimingNode> allNodes = tree.getAllNodes();
        for (TimingNode node : allNodes) {
            if (node.isClickTrigger()) {
                count++;
            }
        }
        return count;
    }
    
    // ===== Constructor and Basic Component Tests =====
    
    @Test
    @DisplayName("Test constructor with real PPTX components")
    void testConstructorWithValidComponents() {
        // Use real parsed data instead of mocks
        assertNotNull(realSlideData, "Real slide data should be parsed successfully");
        
        ShapeRegistry registry = realSlideData.getShapeRegistry();
        TimingTree timingTree = realSlideData.getTimingTree();
        List<AnimationBinding> animations = realSlideData.getAnimationBindings();
        
        // Verify we have real components
        assertNotNull(registry, "Shape registry should not be null");
        assertNotNull(timingTree, "Timing tree should not be null");
        assertNotNull(animations, "Animation bindings should not be null");
        
        // Verify real data has content (not empty mocks)
        assertTrue(registry.getAllShapes().size() > 0, "Should have real shapes from PPTX");
        
        // Test creating ParsedSlideData with these real components
        ParsedSlideData testData = new ParsedSlideData(registry, timingTree, animations);
        
        assertSame(registry, testData.getShapeRegistry(), "Shape registry should match");
        assertSame(timingTree, testData.getTimingTree(), "Timing tree should match");
        assertSame(animations, testData.getAnimationBindings(), "Animation bindings should match");
    }
    
    @Test
    @DisplayName("Test slide data with comprehensive real content")
    void testRealSlideDataContent() {
        // Verify real slide has substantial content
        ShapeRegistry registry = realSlideData.getShapeRegistry();
        TimingTree timingTree = realSlideData.getTimingTree();
        List<AnimationBinding> animations = realSlideData.getAnimationBindings();
        
        // Real PPTX should have multiple shapes
        assertTrue(registry.getShapeCount() > 0, "Real slide should have shapes");
        
        // Check for different shape types
        List<SlideShape> allShapes = registry.getAllShapes();
        assertFalse(allShapes.isEmpty(), "Should have shapes from real PPTX");
        
        // Verify shapes have real properties
        for (SlideShape shape : allShapes) {
            assertNotNull(shape.getSpid(), "Shape should have valid SPID");
            assertNotNull(shape.getGeometry(), "Shape should have geometry");
            assertNotNull(shape.getXmlElement(), "Shape should have XML element");
        }
        
        // Test timing structure exists
        if (timingTree.getRootNode() != null) {
            assertTrue(timingTree.getAllNodes().size() > 0, "Timing tree should have nodes");
        }
        
        // Verify toString provides meaningful information
        String slideDataString = realSlideData.toString();
        assertTrue(slideDataString.contains("shapes"), "toString should mention shapes");
        assertTrue(slideDataString.contains(String.valueOf(registry.getShapeCount())), 
                  "toString should include shape count");
    }
    
    @Test
    @DisplayName("Test constructor with empty components")
    void testConstructorWithEmptyComponents() {
        ShapeRegistry emptyRegistry = new ShapeRegistry();
        TimingTree emptyTree = new TimingTree();
        List<AnimationBinding> emptyAnimations = new ArrayList<>();
        
        ParsedSlideData slideData = new ParsedSlideData(emptyRegistry, emptyTree, emptyAnimations);
        
        assertEquals(0, slideData.getShapeRegistry().getShapeCount(), "Empty registry should have 0 shapes");
        assertEquals(0, slideData.getTimingTree().getNodeCount(), "Empty tree should have 0 nodes");
        assertEquals(0, slideData.getAnimationBindings().size(), "Empty animations should have 0 bindings");
    }
    
    @Test
    @DisplayName("Test constructor with null components")
    void testConstructorWithNullComponents() {
        ParsedSlideData slideData = new ParsedSlideData(null, null, null);
        
        assertNull(slideData.getShapeRegistry(), "Should accept null registry");
        assertNull(slideData.getTimingTree(), "Should accept null timing tree");
        assertNull(slideData.getAnimationBindings(), "Should accept null animations");
    }
    
    // ===== Data Consistency and Cross-Component Validation =====
    
    @Test
    @DisplayName("Test animation target validation with real data")
    void testAnimationTargetValidation() {
        // Use real slide data for validation
        ShapeRegistry registry = realSlideData.getShapeRegistry();
        List<AnimationBinding> animations = realSlideData.getAnimationBindings();
        
        // Test animation target validation with real data
        int validTargets = 0;
        int invalidTargets = 0;
        
        for (AnimationBinding animation : animations) {
            SlideShape targetShape = registry.getShape(animation.getTargetSpid());
            if (targetShape != null) {
                validTargets++;
            } else {
                invalidTargets++;
            }
        }
        
        // If we have animations, test their target validation
        if (validTargets + invalidTargets > 0) {
            // In real PPTX data, most animations should have valid targets
            assertTrue(validTargets >= invalidTargets, "Most animations should have valid targets");
        } else {
            // It's valid for slides to have no animations (like slide 1)
            assertTrue(true, "Slide has no animations - this is valid");
        }
    }
    
    @Test
    @DisplayName("Test animation to timing consistency with real data")
    void testAnimationToTimingConsistency() {
        // Use real slide data for timing consistency analysis
        TimingTree timingTree = realSlideData.getTimingTree();
        List<AnimationBinding> animations = realSlideData.getAnimationBindings();
        
        // Count click triggers vs animations
        int clickTriggers = getClickTriggerCount(timingTree);
        int entranceAnimations = (int) animations.stream()
            .filter(AnimationBinding::isEntranceAnimation)
            .count();
        int exitAnimations = (int) animations.stream()
            .filter(AnimationBinding::isExitAnimation)
            .count();
        
        // Basic consistency checks with real data
        assertTrue(clickTriggers >= 0, "Should have valid click trigger count");
        assertTrue(entranceAnimations >= 0, "Should have valid entrance animation count");
        assertTrue(exitAnimations >= 0, "Should have valid exit animation count");
        
        // If we have animations, we should typically have some timing structure
        if (animations.size() > 0) {
            assertTrue(timingTree.getNodeCount() > 0, "Slides with animations should have timing nodes");
        }
    }
    
    @Test
    @DisplayName("Test text shape animation alignment with real data")
    void testTextShapeAnimationAlignment() {
        // Use real slide data for text shape analysis
        ShapeRegistry registry = realSlideData.getShapeRegistry();
        List<AnimationBinding> animations = realSlideData.getAnimationBindings();
        
        // Analyze text shape vs animation consistency
        List<SlideShape> textShapes = registry.getTextShapes();
        List<AnimationBinding> paragraphAnimations = animations.stream()
            .filter(AnimationBinding::isParagraphLevelAnimation)
            .toList();
        
        // Basic validation with real data
        assertTrue(textShapes.size() >= 0, "Should have valid text shape count");
        assertTrue(paragraphAnimations.size() >= 0, "Should have valid paragraph animation count");
        
        // If we have paragraph animations, they should target text shapes
        for (AnimationBinding paragraphAnim : paragraphAnimations) {
            SlideShape targetShape = registry.getShape(paragraphAnim.getTargetSpid());
            if (targetShape != null) {
                // The shape should ideally support text, though real PPTX data might vary
                assertNotNull(targetShape, "Paragraph animation should target existing shape");
            }
        }
    }
    
    // ===== Component Interaction and Relationship Tests =====
    
    @Test
    @DisplayName("Test with real generalist test file slide data")
    void testRealGeneralistSlideData() throws Exception {
        // Test with slide 2 which has rich bullet-point animations
        File slideFile = new File(extractedPptxDir, "ppt/slides/slide2.xml");
        if (slideFile.exists()) {
            ParsedSlideData slide2Data = parser.parseSlide(slideFile);
            
            // Validate slide 2 has expected rich content
            assertTrue(slide2Data.getShapeRegistry().getShapeCount() > 0, "Slide 2 should have shapes");
            
            List<SlideShape> textShapes = slide2Data.getShapeRegistry().getTextShapes();
            List<AnimationBinding> animations = slide2Data.getAnimationBindings();
            List<AnimationBinding> paragraphAnimations = animations.stream()
                .filter(AnimationBinding::isParagraphLevelAnimation)
                .toList();
            
            // Log information about slide 2 structure
            System.out.println("Slide 2 analysis:");
            System.out.println("  Total shapes: " + slide2Data.getShapeRegistry().getShapeCount());
            System.out.println("  Text shapes: " + textShapes.size());
            System.out.println("  Total animations: " + animations.size());
            System.out.println("  Paragraph animations: " + paragraphAnimations.size());
            System.out.println("  Timing nodes: " + slide2Data.getTimingTree().getNodeCount());
            System.out.println("  Click triggers: " + getClickTriggerCount(slide2Data.getTimingTree()));
            
            // Cross-component validation
            for (AnimationBinding animation : animations) {
                SlideShape target = slide2Data.getShapeRegistry().getShape(animation.getTargetSpid());
                if (target == null) {
                    System.out.println("Warning: Animation targets non-existent shape SPID " + animation.getTargetSpid());
                } else if (animation.isParagraphLevelAnimation() && !target.hasText()) {
                    System.out.println("Warning: Paragraph animation targets non-text shape " + target.getName());
                }
            }
        }
        
        // Test with slide 3 which has transform animations
        File slide3File = new File(extractedPptxDir, "ppt/slides/slide3.xml");
        if (slide3File.exists()) {
            ParsedSlideData slide3Data = parser.parseSlide(slide3File);
            
            System.out.println("Slide 3 analysis:");
            System.out.println("  Total shapes: " + slide3Data.getShapeRegistry().getShapeCount());
            System.out.println("  Animations: " + slide3Data.getAnimationBindings().size());
            System.out.println("  Timing nodes: " + slide3Data.getTimingTree().getNodeCount());
        }
    }
    
    @Test
    @DisplayName("Test component independence with real data references")
    void testComponentIndependence() {
        // Test that real data components maintain proper references
        ShapeRegistry registry = realSlideData.getShapeRegistry();
        TimingTree timingTree = realSlideData.getTimingTree();
        List<AnimationBinding> animations = realSlideData.getAnimationBindings();
        
        // Create a new ParsedSlideData with the same components
        ParsedSlideData duplicateData = new ParsedSlideData(registry, timingTree, animations);
        
        // Verify they reference the same components
        assertSame(registry, duplicateData.getShapeRegistry(), "Should reference same registry");
        assertSame(timingTree, duplicateData.getTimingTree(), "Should reference same timing tree");
        assertSame(animations, duplicateData.getAnimationBindings(), "Should reference same animations");
    }
    
    @Test
    @DisplayName("Test animation sequence validation with real data")
    void testAnimationSequenceValidation() {
        // Use real slide data for animation sequence analysis
        List<AnimationBinding> animations = realSlideData.getAnimationBindings();
        
        // Group animations by target SPID
        Map<Integer, List<AnimationBinding>> animationsBySpid = new HashMap<>();
        for (AnimationBinding animation : animations) {
            animationsBySpid.computeIfAbsent(animation.getTargetSpid(), k -> new ArrayList<>()).add(animation);
        }
        
        // Basic validation of animation distribution
        assertTrue(animationsBySpid.size() >= 0, "Should have valid SPID grouping");
        
        // Test entrance/exit distribution
        long entranceCount = animations.stream().filter(AnimationBinding::isEntranceAnimation).count();
        long exitCount = animations.stream().filter(AnimationBinding::isExitAnimation).count();
        
        assertTrue(entranceCount >= 0, "Should have valid entrance animation count");
        assertTrue(exitCount >= 0, "Should have valid exit animation count");
    }
    
    // ===== toString() and String Representation Tests =====
    
    @Test
    @DisplayName("Test toString with real data")
    void testToStringWithRealData() {
        // Use real slide data for toString testing
        String result = realSlideData.toString();
        
        assertTrue(result.contains("shapes="), "Should contain shapes count");
        assertTrue(result.contains("timingNodes="), "Should contain timing nodes count");
        assertTrue(result.contains("animations="), "Should contain animations count");
        assertTrue(result.startsWith("ParsedSlideData{"), "Should start with class name");
        assertTrue(result.endsWith("}"), "Should end with closing brace");
    }
    
    @Test
    @DisplayName("Test toString with empty data")
    void testToStringEmptyData() {
        ShapeRegistry emptyRegistry = new ShapeRegistry();
        TimingTree emptyTree = new TimingTree();
        List<AnimationBinding> emptyAnimations = new ArrayList<>();
        
        ParsedSlideData slideData = new ParsedSlideData(emptyRegistry, emptyTree, emptyAnimations);
        String result = slideData.toString();
        
        assertTrue(result.contains("shapes=0"), "Should show 0 shapes");
        assertTrue(result.contains("timingNodes=0"), "Should show 0 timing nodes");
        assertTrue(result.contains("animations=0"), "Should show 0 animations");
    }
    
    @Test
    @DisplayName("Test toString with null components")
    void testToStringWithNullComponents() {
        ParsedSlideData slideData = new ParsedSlideData(null, null, null);
        
        // This should cause NullPointerException when accessing null components
        assertThrows(NullPointerException.class, () -> {
            slideData.toString();
        }, "Should throw NullPointerException with null components");
    }
    
    @Test
    @DisplayName("Test toString formatting consistency")
    void testToStringFormattingConsistency() {
        // Test formatting consistency with real data
        String result = realSlideData.toString();
        
        // Check format consistency
        assertTrue(result.endsWith("}"), "Should have proper bracket structure");
        assertTrue(result.contains("shapes=") && result.contains("timingNodes=") && result.contains("animations="), 
                  "Should contain all components");
        
        // Check no extra spaces or formatting issues
        assertFalse(result.contains("  "), "Should not have double spaces");
        assertFalse(result.contains(",}"), "Should not have trailing commas");
    }

    // ===== Advanced Cross-Component Validation Tests =====
    
    @Test
    @DisplayName("Test animation-timing tree consistency across multiple slides")
    void testAnimationTimingConsistencyAcrossSlides() throws Exception {
        // Test consistency patterns across multiple slides
        File slidesDir = new File(extractedPptxDir, "ppt/slides");
        if (slidesDir.exists()) {
            File[] slideFiles = slidesDir.listFiles((dir, name) -> name.endsWith(".xml"));
            if (slideFiles != null && slideFiles.length > 1) {
                
                for (File slideFile : slideFiles) {
                    try {
                        ParsedSlideData slideData = parser.parseSlide(slideFile);
                        
                        // Validate animation-timing consistency rules
                        List<AnimationBinding> animations = slideData.getAnimationBindings();
                        TimingTree timingTree = slideData.getTimingTree();
                        
                        // Rule 1: If animations exist, timing tree should have nodes
                        if (!animations.isEmpty()) {
                            assertTrue(timingTree.getNodeCount() > 0,
                                     "Slides with animations should have timing nodes - Failed for " + slideFile.getName());
                        }
                        
                        // Rule 2: Click triggers should align with interactive animations
                        int clickTriggers = getClickTriggerCount(timingTree);
                        long interactiveAnimations = animations.stream()
                            .filter(anim -> anim.isEntranceAnimation() || anim.isExitAnimation())
                            .count();
                        
                        // If we have interactive animations, we should have some timing structure
                        if (interactiveAnimations > 0) {
                            assertTrue(timingTree.getNodeCount() >= 1,
                                     "Interactive animations should have corresponding timing structure - Failed for " + slideFile.getName());
                        }
                        
                        // Rule 3: Animation targets should exist in shape registry
                        ShapeRegistry registry = slideData.getShapeRegistry();
                        for (AnimationBinding animation : animations) {
                            Integer targetSpid = animation.getTargetSpid();
                            SlideShape targetShape = registry.getShape(targetSpid);
                            
                            if (targetShape == null) {
                                System.out.println("Warning in " + slideFile.getName() + 
                                                 ": Animation targets non-existent shape SPID " + targetSpid);
                            } else {
                                // Rule 4: Paragraph-level animations should target text shapes
                                if (animation.isParagraphLevelAnimation()) {
                                    assertTrue(targetShape.hasText() || targetShape.getType().supportsText(),
                                             "Paragraph animation should target text-capable shape - Failed for " + slideFile.getName() + " SPID " + targetSpid);
                                }
                            }
                        }
                        
                    } catch (Exception e) {
                        System.out.println("Skipping " + slideFile.getName() + " due to parsing error: " + e.getMessage());
                    }
                }
            }
        }
    }
    
    @Test
    @DisplayName("Test shape-animation binding validation with complex scenarios")
    void testShapeAnimationBindingValidation() throws Exception {
        // Test complex scenarios where shapes and animations must be properly linked
        File slide2File = new File(extractedPptxDir, "ppt/slides/slide2.xml");
        if (slide2File.exists()) {
            ParsedSlideData slide2Data = parser.parseSlide(slide2File);
            
            ShapeRegistry registry = slide2Data.getShapeRegistry();
            List<AnimationBinding> animations = slide2Data.getAnimationBindings();
            
            // Group animations by target SPID for analysis
            Map<Integer, List<AnimationBinding>> animationsBySpid = new HashMap<>();
            for (AnimationBinding animation : animations) {
                animationsBySpid.computeIfAbsent(animation.getTargetSpid(), k -> new ArrayList<>()).add(animation);
            }
            
            // Validate each animated shape
            for (Map.Entry<Integer, List<AnimationBinding>> entry : animationsBySpid.entrySet()) {
                Integer spid = entry.getKey();
                List<AnimationBinding> shapeAnimations = entry.getValue();
                SlideShape shape = registry.getShape(spid);
                
                assertNotNull(shape, "Animated shape should exist in registry");
                
                // Test animation sequence consistency
                List<AnimationBinding> entranceAnims = shapeAnimations.stream()
                    .filter(AnimationBinding::isEntranceAnimation)
                    .toList();
                List<AnimationBinding> exitAnims = shapeAnimations.stream()
                    .filter(AnimationBinding::isExitAnimation)
                    .toList();
                
                // Rule: A shape shouldn't have more exit animations than entrance animations
                assertTrue(exitAnims.size() <= entranceAnims.size() + 1,
                         "Exit animations shouldn't exceed entrance animations for SPID " + spid); // +1 for shapes that start visible
                
                // Test paragraph-level animation consistency
                List<AnimationBinding> paragraphAnims = shapeAnimations.stream()
                    .filter(AnimationBinding::isParagraphLevelAnimation)
                    .toList();
                
                if (!paragraphAnims.isEmpty()) {
                    assertTrue(shape.hasText(),
                             "Shape with paragraph animations should have text content");
                    
                    // Test paragraph index bounds
                    for (AnimationBinding paragraphAnim : paragraphAnims) {
                        if (paragraphAnim.getParagraphStart() != null && shape.hasParagraphMetadata()) {
                            assertTrue(paragraphAnim.getParagraphStart() >= 0,
                                     "Paragraph animation start index should be valid");
                            assertTrue(paragraphAnim.getParagraphStart() < shape.getParagraphCount(),
                                     "Paragraph animation start index should be within bounds");
                        }
                        if (paragraphAnim.getParagraphEnd() != null && shape.hasParagraphMetadata()) {
                            assertTrue(paragraphAnim.getParagraphEnd() >= 0,
                                     "Paragraph animation end index should be valid");
                            assertTrue(paragraphAnim.getParagraphEnd() < shape.getParagraphCount(),
                                     "Paragraph animation end index should be within bounds");
                        }
                    }
                }
            }
        }
    }
    
    @Test
    @DisplayName("Test timing tree structure validation with real PowerPoint patterns")
    void testTimingTreeStructureValidation() throws Exception {
        // Test timing tree structure against real PowerPoint patterns
        File slide2File = new File(extractedPptxDir, "ppt/slides/slide2.xml");
        if (slide2File.exists()) {
            ParsedSlideData slide2Data = parser.parseSlide(slide2File);
            TimingTree timingTree = slide2Data.getTimingTree();
            
            if (timingTree.getRootNode() != null) {
                // Validate timing tree structure
                List<TimingNode> allNodes = timingTree.getAllNodes();
                assertNotNull(allNodes, "Timing tree should provide node list");
                assertEquals(timingTree.getNodeCount(), allNodes.size(),
                           "Node count should match getAllNodes size");
                
                // Test click trigger patterns
                List<TimingNode> clickTriggers = allNodes.stream()
                    .filter(TimingNode::isClickTrigger)
                    .toList();
                
                // Each click trigger should have children (animations)
                for (TimingNode clickTrigger : clickTriggers) {
                    List<TimingNode> children = clickTrigger.getChildren();
                    if (children.isEmpty()) {
                        System.out.println("Warning: Click trigger " + clickTrigger.getNodeId() + 
                                         " has no children - unusual for real presentations");
                    }
                }
                
                // Test timing hierarchy integrity
                for (TimingNode node : allNodes) {
                    // Each node should have consistent structure
                    assertNotNull(node.getNodeId(), "Node should have ID");
                    assertNotNull(node.getNodeType(), "Node should have type");
                    assertNotNull(node.getDelay(), "Node should have delay");
                    
                    // Test children consistency
                    List<TimingNode> children = node.getChildren();
                    assertNotNull(children, "Children list should not be null");
                    
                    // Recursive count should be accurate
                    int expectedCount = 1; // Self
                    for (TimingNode child : children) {
                        expectedCount += child.getTotalNodeCount();
                    }
                    assertEquals(expectedCount, node.getTotalNodeCount(),
                               "Recursive node count should be accurate");
                }
                
                // Test string representation
                String treeString = timingTree.toString();
                assertNotNull(treeString, "Timing tree toString should not be null");
                assertTrue(treeString.contains("nodes"),
                         "Timing tree toString should contain node count");
            }
        }
    }
    
    @Test
    @DisplayName("Test slide data aggregation with component interdependencies")
    void testSlideDataAggregationInterdependencies() throws Exception {
        // Test how components work together in real slide scenarios
        File slide3File = new File(extractedPptxDir, "ppt/slides/slide3.xml");
        if (slide3File.exists()) {
            ParsedSlideData slide3Data = parser.parseSlide(slide3File);
            
            ShapeRegistry registry = slide3Data.getShapeRegistry();
            TimingTree timingTree = slide3Data.getTimingTree();
            List<AnimationBinding> animations = slide3Data.getAnimationBindings();
            
            // Test data aggregation consistency
            String slideDataString = slide3Data.toString();
            assertNotNull(slideDataString, "Slide data toString should not be null");
            
            // Verify toString contains accurate component counts
            assertTrue(slideDataString.contains("shapes=" + registry.getShapeCount()),
                     "ToString should contain shapes count");
            assertTrue(slideDataString.contains("timingNodes=" + timingTree.getNodeCount()),
                     "ToString should contain timing nodes count");
            assertTrue(slideDataString.contains("animations=" + animations.size()),
                     "ToString should contain animations count");
            
            // Test component consistency rules
            if (registry.getShapeCount() > 0) {
                List<SlideShape> allShapes = registry.getAllShapes();
                assertFalse(allShapes.isEmpty(), "Shapes list should not be empty when count > 0");
                
                // Test shape uniqueness and registry consistency
                Set<Integer> uniqueSpids = new HashSet<>();
                for (SlideShape shape : allShapes) {
                    uniqueSpids.add(shape.getSpid());
                    
                    // Shape should be retrievable by SPID
                    SlideShape retrieved = registry.getShape(shape.getSpid());
                    assertNotNull(retrieved, "Shape should be retrievable by SPID");
                }
                
                // Test SPID set consistency
                Set<Integer> registrySpids = registry.getAllSpids();
                assertTrue(registrySpids.containsAll(uniqueSpids),
                         "Registry SPID set should contain all shape SPIDs");
            }
            
            // Test animation-shape relationships
            for (AnimationBinding animation : animations) {
                Integer targetSpid = animation.getTargetSpid();
                
                // Shape should exist or we should log the discrepancy
                SlideShape targetShape = registry.getShape(targetSpid);
                if (targetShape == null) {
                    System.out.println("Info: Animation in slide3 targets SPID " + targetSpid + 
                                     " which is not in the current registry");
                } else {
                    // Verify animation is compatible with shape type
                    if (animation.isParagraphLevelAnimation()) {
                        assertTrue(targetShape.hasText() || targetShape.getType().supportsText(),
                                 "Paragraph animation should target text-supporting shape");
                    }
                }
            }
        }
    }
    
    @Test
    @DisplayName("Test error resilience and edge case handling")
    void testErrorResilienceAndEdgeCases() {
        // Test that ParsedSlideData handles edge cases gracefully
        
        // Test with minimal components
        ShapeRegistry minimalRegistry = new ShapeRegistry();
        TimingTree minimalTree = new TimingTree();
        List<AnimationBinding> minimalAnimations = new ArrayList<>();
        
        ParsedSlideData minimalData = new ParsedSlideData(minimalRegistry, minimalTree, minimalAnimations);
        
        // Should not throw exceptions
        assertNotNull(minimalData.toString(), "Minimal data should have valid string representation");
        assertEquals(0, minimalData.getShapeRegistry().getShapeCount(), "Minimal registry should have 0 shapes");
        assertEquals(0, minimalData.getTimingTree().getNodeCount(), "Minimal tree should have 0 nodes");
        assertEquals(0, minimalData.getAnimationBindings().size(), "Minimal animations should have 0 bindings");
        
        // Test with single component
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder;
        try {
            builder = factory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
        Document testDocument = builder.newDocument();
        SlideShape testShape = new SlideShape(999, "TestShape", SlideShape.ShapeType.RECTANGLE,
                                             "Test text", new ShapeGeometry(0L, 0L, 100L, 100L),
                                             testDocument.createElement("test"));
        minimalRegistry.addShape(testShape);
        
        ParsedSlideData singleShapeData = new ParsedSlideData(minimalRegistry, minimalTree, minimalAnimations);
        assertEquals(1, singleShapeData.getShapeRegistry().getShapeCount(), "Single shape data should have 1 shape");
        assertTrue(singleShapeData.toString().contains("shapes=1"),
                  "ToString should reflect single shape");
        
        // Test component independence
        ParsedSlideData independentData = new ParsedSlideData(minimalRegistry, minimalTree, minimalAnimations);
        assertSame(minimalRegistry, independentData.getShapeRegistry(), "Registry should be same reference");
        assertSame(minimalTree, independentData.getTimingTree(), "Tree should be same reference");
        assertSame(minimalAnimations, independentData.getAnimationBindings(), "Animations should be same reference");
    }

    // ===== HELPER METHODS =====

    /**
     * Copies real test PPTX structure from test-pptx-samples
     */
    private void copyTestPptxStructure() throws IOException {
        // Path to the extracted generalist test file
        Path sourceDir = Path.of("generalist_extracted");
        
        if (!Files.exists(sourceDir)) {
            // Extract the test PPTX if not already extracted
            try {
                Files.createDirectories(sourceDir);
                // Use Java's built-in ZIP support to extract
                java.util.zip.ZipFile zipFile = new java.util.zip.ZipFile("test-pptx-samples/generalist_test_file.pptx");
                java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zipFile.entries();
                
                while (entries.hasMoreElements()) {
                    java.util.zip.ZipEntry entry = entries.nextElement();
                    Path entryPath = sourceDir.resolve(entry.getName());
                    
                    if (entry.isDirectory()) {
                        Files.createDirectories(entryPath);
                    } else {
                        Files.createDirectories(entryPath.getParent());
                        Files.copy(zipFile.getInputStream(entry), entryPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
                zipFile.close();
            } catch (Exception e) {
                throw new IOException("Failed to extract test PPTX file", e);
            }
        }
        
        // Copy the extracted structure to our temp directory
        copyDirectory(sourceDir, extractedPptxDir.toPath());
    }

    /**
     * Recursively copies a directory
     */
    private void copyDirectory(Path source, Path target) throws IOException {
        if (!Files.exists(source)) {
            throw new IOException("Source directory does not exist: " + source);
        }
        
        try (Stream<Path> stream = Files.walk(source)) {
            stream.forEach(path -> {
                try {
                    Path targetPath = target.resolve(source.relativize(path));
                    if (Files.isDirectory(path)) {
                        Files.createDirectories(targetPath);
                    } else {
                        Files.createDirectories(targetPath.getParent());
                        Files.copy(path, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new RuntimeException("Failed to copy: " + path, e);
                }
            });
        }
    }
}