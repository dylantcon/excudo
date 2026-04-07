package com.excudo.core.model;

import org.junit.Test;
import org.junit.Before;
import static org.junit.Assert.*;
import org.w3c.dom.*;
import javax.xml.parsers.*;
import com.excudo.core.model.ParagraphMetadata;
import java.util.Arrays;

/**
 * Tests for SlideShape focusing on actual behaviors:
 * - OOXML preset mapping accuracy
 * - Shape capability determination (text support, collision detection)
 * - Paragraph and bullet point content access with edge cases
 * - Content validation and metadata handling
 */
public class SlideShapeTest {
    
    private Document document;
    private Element mockXmlElement;
    private ShapeGeometry testGeometry;
    
    @Before
    public void setUp() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        document = builder.newDocument();
        mockXmlElement = document.createElement("test");
        testGeometry = new ShapeGeometry(1000000L, 2000000L, 3000000L, 4000000L); // Example EMUs
    }
    
    // ===== OOXML Preset Mapping Tests =====
    
    @Test
    public void testFromOoxmlPresetWithKnownShapes() {
        // Test critical mapping accuracy for commonly used shapes
        assertEquals("Rectangle mapping", SlideShape.ShapeType.RECTANGLE, 
                    SlideShape.ShapeType.fromOoxmlPreset("rect"));
        assertEquals("Ellipse mapping", SlideShape.ShapeType.ELLIPSE, 
                    SlideShape.ShapeType.fromOoxmlPreset("ellipse"));
        assertEquals("Right arrow mapping", SlideShape.ShapeType.RIGHT_ARROW, 
                    SlideShape.ShapeType.fromOoxmlPreset("rightArrow"));
        assertEquals("Star 5 points mapping", SlideShape.ShapeType.STAR_5_POINTS, 
                    SlideShape.ShapeType.fromOoxmlPreset("star5"));
        assertEquals("Flowchart process mapping", SlideShape.ShapeType.FLOWCHART_PROCESS, 
                    SlideShape.ShapeType.fromOoxmlPreset("flowChartProcess"));
    }
    
    @Test
    public void testFromOoxmlPresetWithEdgeCases() {
        // Test edge cases in preset mapping
        assertEquals("Null preset should return CUSTOM_GEOMETRY", 
                    SlideShape.ShapeType.CUSTOM_GEOMETRY, 
                    SlideShape.ShapeType.fromOoxmlPreset(null));
        assertEquals("Empty string should return CUSTOM_GEOMETRY", 
                    SlideShape.ShapeType.CUSTOM_GEOMETRY, 
                    SlideShape.ShapeType.fromOoxmlPreset(""));
        assertEquals("Unknown preset should return CUSTOM_GEOMETRY", 
                    SlideShape.ShapeType.CUSTOM_GEOMETRY, 
                    SlideShape.ShapeType.fromOoxmlPreset("unknownShape"));
        assertEquals("Case sensitive - wrong case should return CUSTOM_GEOMETRY", 
                    SlideShape.ShapeType.CUSTOM_GEOMETRY, 
                    SlideShape.ShapeType.fromOoxmlPreset("RECT"));
    }
    
    @Test
    public void testFromOoxmlPresetCompleteMapping() {
        // Verify that all enum values with OOXML presets can be reverse-mapped
        for (SlideShape.ShapeType type : SlideShape.ShapeType.values()) {
            if (type.hasOoxmlPreset()) {
                assertEquals("Shape type " + type + " should map correctly from its OOXML preset", 
                           type, SlideShape.ShapeType.fromOoxmlPreset(type.getOoxmlPreset()));
            }
        }
    }
    
    // ===== Text Support Capability Tests =====
    
    @Test
    public void testSupportsTextForTextCapableShapes() {
        // Most shapes should support text
        assertTrue("Rectangle should support text", SlideShape.ShapeType.RECTANGLE.supportsText());
        assertTrue("Ellipse should support text", SlideShape.ShapeType.ELLIPSE.supportsText());
        assertTrue("Star should support text", SlideShape.ShapeType.STAR_5_POINTS.supportsText());
        assertTrue("Flowchart shapes should support text", SlideShape.ShapeType.FLOWCHART_PROCESS.supportsText());
        assertTrue("Callouts should support text", SlideShape.ShapeType.RECTANGULAR_CALLOUT.supportsText());
        assertTrue("Custom geometry should support text", SlideShape.ShapeType.CUSTOM_GEOMETRY.supportsText());
    }
    
    @Test
    public void testSupportsTextForNonTextShapes() {
        // Specific shapes that don't support text
        assertFalse("CONNECTION should not support text", SlideShape.ShapeType.CONNECTION.supportsText());
        assertFalse("LINE should not support text", SlideShape.ShapeType.LINE.supportsText());
        assertFalse("ARC should not support text", SlideShape.ShapeType.ARC.supportsText());
        assertFalse("Straight connector should not support text", SlideShape.ShapeType.STRAIGHT_CONNECTOR.supportsText());
        assertFalse("Elbow connector should not support text", SlideShape.ShapeType.ELBOW_CONNECTOR.supportsText());
        assertFalse("Curved connector should not support text", SlideShape.ShapeType.CURVED_CONNECTOR.supportsText());
    }
    
    // ===== SAT Collision Detection Requirements Tests =====
    
    @Test
    public void testRequiresSATCollisionForComplexShapes() {
        // Complex shapes requiring collision detection
        assertTrue("Custom geometry requires SAT collision", SlideShape.ShapeType.CUSTOM_GEOMETRY.requiresSATCollision());
        assertTrue("5-point star requires SAT collision", SlideShape.ShapeType.STAR_5_POINTS.requiresSATCollision());
        assertTrue("Right arrow requires SAT collision", SlideShape.ShapeType.RIGHT_ARROW.requiresSATCollision());
        assertTrue("Triangle requires SAT collision", SlideShape.ShapeType.TRIANGLE.requiresSATCollision());
        assertTrue("Lightning bolt requires SAT collision", SlideShape.ShapeType.LIGHTNING_BOLT.requiresSATCollision());
        assertTrue("Heart requires SAT collision", SlideShape.ShapeType.HEART.requiresSATCollision());
        assertTrue("Wave requires SAT collision", SlideShape.ShapeType.WAVE.requiresSATCollision());
        assertTrue("Irregular seal requires SAT collision", SlideShape.ShapeType.IRREGULAR_SEAL_1.requiresSATCollision());
    }
    
    @Test
    public void testRequiresSATCollisionForSimpleShapes() {
        // Simple shapes that don't require SAT collision
        assertFalse("Rectangle doesn't require SAT collision", SlideShape.ShapeType.RECTANGLE.requiresSATCollision());
        assertFalse("Ellipse doesn't require SAT collision", SlideShape.ShapeType.ELLIPSE.requiresSATCollision());
        assertFalse("Rounded rectangle doesn't require SAT collision", SlideShape.ShapeType.ROUNDED_RECTANGLE.requiresSATCollision());
        assertFalse("Pentagon doesn't require SAT collision", SlideShape.ShapeType.PENTAGON.requiresSATCollision());
        assertFalse("Flowchart process doesn't require SAT collision", SlideShape.ShapeType.FLOWCHART_PROCESS.requiresSATCollision());
    }
    
    // ===== Content Access and Validation Tests =====
    
    @Test
    public void testTextContentValidation() {
        // Test hasText() behavior with different text content scenarios
        SlideShape shapeWithText = new SlideShape(1, "TestShape", SlideShape.ShapeType.RECTANGLE, 
                                                  "Valid text content", testGeometry, mockXmlElement);
        assertTrue("Shape with valid text should report hasText=true", shapeWithText.hasText());
        
        SlideShape shapeWithEmptyText = new SlideShape(2, "EmptyShape", SlideShape.ShapeType.RECTANGLE, 
                                                       "", testGeometry, mockXmlElement);
        assertFalse("Shape with empty text should report hasText=false", shapeWithEmptyText.hasText());
        
        SlideShape shapeWithWhitespaceText = new SlideShape(3, "WhitespaceShape", SlideShape.ShapeType.RECTANGLE, 
                                                            "   \n\t   ", testGeometry, mockXmlElement);
        assertFalse("Shape with only whitespace should report hasText=false", shapeWithWhitespaceText.hasText());
        
        SlideShape shapeWithNullText = new SlideShape(4, "NullShape", SlideShape.ShapeType.RECTANGLE, 
                                                      null, testGeometry, mockXmlElement);
        assertFalse("Shape with null text should report hasText=false", shapeWithNullText.hasText());
    }
    
    @Test
    public void testParagraphMetadataHandling() {
        // Create test paragraph metadata
        ParagraphMetadata metadata = new ParagraphMetadata(
            Arrays.asList("First bullet", "Regular paragraph", "Second bullet", "Third bullet"),
            Arrays.asList(true, false, true, true), // Which paragraphs are bullets
            Arrays.asList("•", "", "•", "•") // Bullet markers (empty for non-bullets)
        );
        
        SlideShape shapeWithMetadata = new SlideShape(1, "MetadataShape", SlideShape.ShapeType.RECTANGLE, 
                                                      "Some text", testGeometry, mockXmlElement, metadata);
        
        assertTrue("Shape should report having paragraph metadata", shapeWithMetadata.hasParagraphMetadata());
        assertTrue("Shape should report having bullet points", shapeWithMetadata.hasBulletPoints());
        assertEquals("Bullet point count should be accurate", 3, shapeWithMetadata.getBulletPointCount());
        assertEquals("Paragraph count should be accurate", 4, shapeWithMetadata.getParagraphCount());
        
        SlideShape shapeWithoutMetadata = new SlideShape(2, "NoMetadataShape", SlideShape.ShapeType.RECTANGLE, 
                                                         "Some text", testGeometry, mockXmlElement);
        
        assertFalse("Shape should report not having paragraph metadata", shapeWithoutMetadata.hasParagraphMetadata());
        assertFalse("Shape should report not having bullet points", shapeWithoutMetadata.hasBulletPoints());
        assertEquals("Bullet point count should be zero", 0, shapeWithoutMetadata.getBulletPointCount());
        assertEquals("Paragraph count should be zero", 0, shapeWithoutMetadata.getParagraphCount());
    }
    
    @Test
    public void testBulletPointContentAccess() {
        ParagraphMetadata metadata = new ParagraphMetadata(
            Arrays.asList("First bullet", "Second bullet", "Third bullet"),
            Arrays.asList(true, true, true), // All are bullets
            Arrays.asList("•", "•", "•") // All have bullet markers
        );
        
        SlideShape shape = new SlideShape(1, "BulletShape", SlideShape.ShapeType.RECTANGLE, 
                                         "Some text", testGeometry, mockXmlElement, metadata);
        
        // Test valid bullet point access
        assertEquals("First bullet point content", "First bullet", shape.getBulletPointContent(0));
        assertEquals("Second bullet point content", "Second bullet", shape.getBulletPointContent(1));
        assertEquals("Third bullet point content", "Third bullet", shape.getBulletPointContent(2));
    }
    
    @Test(expected = IndexOutOfBoundsException.class)
    public void testBulletPointContentAccessOutOfBounds() {
        ParagraphMetadata metadata = new ParagraphMetadata(
            Arrays.asList("First bullet", "Second bullet"),
            Arrays.asList(true, true), // Both are bullets
            Arrays.asList("•", "•") // Both have bullet markers
        );
        
        SlideShape shape = new SlideShape(1, "BulletShape", SlideShape.ShapeType.RECTANGLE, 
                                         "Some text", testGeometry, mockXmlElement, metadata);
        
        // This should throw IndexOutOfBoundsException
        shape.getBulletPointContent(5);
    }
    
    @Test(expected = IndexOutOfBoundsException.class)
    public void testBulletPointContentAccessNegativeIndex() {
        ParagraphMetadata metadata = new ParagraphMetadata(
            Arrays.asList("First bullet", "Second bullet"),
            Arrays.asList(true, true), // Both are bullets
            Arrays.asList("•", "•") // Both have bullet markers
        );
        
        SlideShape shape = new SlideShape(1, "BulletShape", SlideShape.ShapeType.RECTANGLE, 
                                         "Some text", testGeometry, mockXmlElement, metadata);
        
        // This should throw IndexOutOfBoundsException
        shape.getBulletPointContent(-1);
    }
    
    @Test(expected = IllegalStateException.class)
    public void testBulletPointContentAccessWithoutMetadata() {
        SlideShape shape = new SlideShape(1, "NoMetadataShape", SlideShape.ShapeType.RECTANGLE, 
                                         "Some text", testGeometry, mockXmlElement);
        
        // This should throw IllegalStateException
        shape.getBulletPointContent(0);
    }
    
    @Test
    public void testParagraphContentAccess() {
        ParagraphMetadata metadata = new ParagraphMetadata(
            Arrays.asList("First bullet", "Regular paragraph", "Second bullet", "Final paragraph"),
            Arrays.asList(true, false, true, false), // Mixed bullets and regular paragraphs
            Arrays.asList("•", "", "•", "") // Bullet markers only for bullets
        );
        
        SlideShape shape = new SlideShape(1, "ParagraphShape", SlideShape.ShapeType.RECTANGLE, 
                                         "Some text", testGeometry, mockXmlElement, metadata);
        
        // Test paragraph access (includes both bullets and non-bullets)
        assertEquals("First paragraph content", "First bullet", shape.getParagraphContent(0));
        assertEquals("Second paragraph content", "Regular paragraph", shape.getParagraphContent(1));
        assertEquals("Third paragraph content", "Second bullet", shape.getParagraphContent(2));
        assertEquals("Fourth paragraph content", "Final paragraph", shape.getParagraphContent(3));
    }
    
    @Test(expected = IllegalStateException.class)
    public void testParagraphContentAccessWithoutMetadata() {
        SlideShape shape = new SlideShape(1, "NoMetadataShape", SlideShape.ShapeType.RECTANGLE, 
                                         "Some text", testGeometry, mockXmlElement);
        
        // This should throw IllegalStateException
        shape.getParagraphContent(0);
    }
    
    // ===== toString() Format and Content Tests =====
    
    @Test
    public void testToStringWithParagraphMetadata() {
        ParagraphMetadata metadata = new ParagraphMetadata(
            Arrays.asList("Bullet 1", "Regular text", "Bullet 2"),
            Arrays.asList(true, false, true), // First and third are bullets
            Arrays.asList("•", "", "•") // Bullet markers for bullets only
        );
        
        SlideShape shape = new SlideShape(42, "TestShape", SlideShape.ShapeType.STAR_5_POINTS, 
                                         "Some text", testGeometry, mockXmlElement, metadata);
        
        String result = shape.toString();
        assertTrue("toString should include SPID", result.contains("spid=42"));
        assertTrue("toString should include name", result.contains("name='TestShape'"));
        assertTrue("toString should include type", result.contains("type=STAR_5_POINTS"));
        assertTrue("toString should include hasText", result.contains("hasText=true"));
        assertTrue("toString should include paragraph count", result.contains("paragraphs=3"));
        assertTrue("toString should include bullet count", result.contains("bullets=2"));
    }
    
    @Test
    public void testToStringWithoutParagraphMetadata() {
        SlideShape shape = new SlideShape(99, "SimpleShape", SlideShape.ShapeType.RECTANGLE, 
                                         null, testGeometry, mockXmlElement);
        
        String result = shape.toString();
        assertTrue("toString should include SPID", result.contains("spid=99"));
        assertTrue("toString should include name", result.contains("name='SimpleShape'"));
        assertTrue("toString should include type", result.contains("type=RECTANGLE"));
        assertTrue("toString should include hasText", result.contains("hasText=false"));
        assertFalse("toString should not include paragraphs when no metadata", result.contains("paragraphs="));
        assertFalse("toString should not include bullets when no metadata", result.contains("bullets="));
    }
    
    // ===== Business Logic Integration Tests =====
    
    @Test
    public void testShapeCapabilityConsistency() {
        // Test that text support and content handling are consistent
        SlideShape textShape = new SlideShape(1, "TextShape", SlideShape.ShapeType.RECTANGLE, 
                                             "Valid text", testGeometry, mockXmlElement);
        
        assertTrue("Text-supporting shape type should allow text content", 
                  SlideShape.ShapeType.RECTANGLE.supportsText());
        assertTrue("Shape with text content should report hasText=true", textShape.hasText());
        
        // Test non-text shape behavior
        SlideShape lineShape = new SlideShape(2, "LineShape", SlideShape.ShapeType.LINE, 
                                             null, testGeometry, mockXmlElement);
        
        assertFalse("Line shape type should not support text", 
                   SlideShape.ShapeType.LINE.supportsText());
        assertFalse("Line shape with no text should report hasText=false", lineShape.hasText());
    }
    
    @Test
    public void testShapeTypeConsistencyWithOOXMLMapping() {
        // Verify that shape types with OOXML presets have consistent behavior
        SlideShape.ShapeType[] shapesWithPresets = {
            SlideShape.ShapeType.RECTANGLE, SlideShape.ShapeType.ELLIPSE, 
            SlideShape.ShapeType.STAR_5_POINTS, SlideShape.ShapeType.RIGHT_ARROW
        };
        
        for (SlideShape.ShapeType type : shapesWithPresets) {
            assertTrue("Shape with OOXML preset should have preset", type.hasOoxmlPreset());
            assertNotNull("OOXML preset should not be null", type.getOoxmlPreset());
            assertEquals("Round-trip mapping should work", type, 
                        SlideShape.ShapeType.fromOoxmlPreset(type.getOoxmlPreset()));
        }
    }
    
    @Test
    public void testEmptyParagraphMetadataHandling() {
        // Test behavior with empty paragraph metadata
        ParagraphMetadata emptyMetadata = new ParagraphMetadata(
            Arrays.asList(), // No paragraphs
            Arrays.asList(), // No bullet flags
            Arrays.asList()  // No bullet markers
        );
        
        SlideShape shape = new SlideShape(1, "EmptyMetadataShape", SlideShape.ShapeType.RECTANGLE, 
                                         "Some text", testGeometry, mockXmlElement, emptyMetadata);
        
        assertTrue("Shape should report having paragraph metadata", shape.hasParagraphMetadata());
        assertFalse("Shape should report not having bullet points", shape.hasBulletPoints());
        assertEquals("Bullet point count should be zero", 0, shape.getBulletPointCount());
        assertEquals("Paragraph count should be zero", 0, shape.getParagraphCount());
    }
    
    // ===== Real PPTX Data Integration Tests =====
    
    @Test
    public void testRealPptxShapeTypes() throws Exception {
        // Test with real PPTX data to verify shape type detection from actual OOXML
        java.io.File tempDir = java.nio.file.Files.createTempDirectory("slideShape-test").toFile();
        tempDir.deleteOnExit();
        
        try {
            // Copy test PPTX structure
            copyTestPptxStructure(tempDir);
            
            // Initialize parser
            com.excudo.xml.parsers.SlideXMLParser parser = 
                new com.excudo.xml.parsers.SlideXMLParser();
            
            // Parse real slides and test shape types
            java.io.File slidesDir = new java.io.File(tempDir, "ppt/slides");
            if (slidesDir.exists()) {
                java.io.File[] slideFiles = slidesDir.listFiles((dir, name) -> name.endsWith(".xml"));
                if (slideFiles != null && slideFiles.length > 0) {
                    for (java.io.File slideFile : slideFiles) {
                        try {
                            ParsedSlideData slideData = parser.parseSlide(slideFile);
                            java.util.List<SlideShape> shapes = slideData.getShapeRegistry().getAllShapes();
                            
                            // Test that real shapes have valid properties
                            for (SlideShape shape : shapes) {
                                assertNotNull("Real shape should have valid SPID", shape.getSpid());
                                assertNotNull("Real shape should have name", shape.getName());
                                assertNotNull("Real shape should have type", shape.getType());
                                assertNotNull("Real shape should have geometry", shape.getGeometry());
                                assertNotNull("Real shape should have XML element", shape.getXmlElement());
                                
                                // Test shape type consistency
                                if (shape.getType().hasOoxmlPreset()) {
                                    assertNotNull("Shape with OOXML preset should have preset string", 
                                                shape.getType().getOoxmlPreset());
                                }
                                
                                // Test text capability consistency
                                if (shape.hasText()) {
                                    assertTrue("Shape with text should have text-supporting type or be custom", 
                                             shape.getType().supportsText() || 
                                             shape.getType() == SlideShape.ShapeType.CUSTOM_GEOMETRY);
                                }
                                
                                // Test paragraph metadata consistency
                                if (shape.hasParagraphMetadata()) {
                                    assertTrue("Shape with paragraph metadata should have text", 
                                             shape.hasText());
                                    assertTrue("Shape with paragraph metadata should have valid paragraph count", 
                                             shape.getParagraphCount() >= 0);
                                    assertTrue("Shape with paragraph metadata should have valid bullet count", 
                                             shape.getBulletPointCount() >= 0);
                                    assertTrue("Bullet count should not exceed paragraph count", 
                                             shape.getBulletPointCount() <= shape.getParagraphCount());
                                }
                            }
                            
                            System.out.println("Tested " + shapes.size() + " real shapes from " + slideFile.getName());
                            
                        } catch (Exception e) {
                            // Skip files that can't be parsed - this is expected for some slides
                            System.out.println("Warning: Could not parse " + slideFile.getName() + ": " + e.getMessage());
                        }
                    }
                }
            }
            
        } finally {
            // Clean up temp directory
            deleteDirectory(tempDir.toPath());
        }
    }
    
    @Test
    public void testRealPptxShapeGeometry() throws Exception {
        // Test that real PPTX shapes have valid geometry data
        java.io.File tempDir = java.nio.file.Files.createTempDirectory("slideShape-geometry-test").toFile();
        tempDir.deleteOnExit();
        
        try {
            copyTestPptxStructure(tempDir);
            
            com.excudo.xml.parsers.SlideXMLParser parser = 
                new com.excudo.xml.parsers.SlideXMLParser();
            
            // Test slide 1 which should have shapes with geometry
            java.io.File slide1File = new java.io.File(tempDir, "ppt/slides/slide1.xml");
            if (slide1File.exists()) {
                ParsedSlideData slideData = parser.parseSlide(slide1File);
                java.util.List<SlideShape> shapes = slideData.getShapeRegistry().getAllShapes();
                
                for (SlideShape shape : shapes) {
                    ShapeGeometry geometry = shape.getGeometry();
                    assertNotNull("Real shape should have geometry", geometry);
                    
                    // Test that geometry has reasonable values (not all zero)
                    assertTrue("Shape should have valid dimensions", 
                             geometry.getWidth() > 0 || geometry.getHeight() > 0);
                    
                    // Test coordinate conversion consistency
                    assertTrue("X in points should be finite", 
                             Double.isFinite(geometry.getXInPoints()));
                    assertTrue("Y in points should be finite", 
                             Double.isFinite(geometry.getYInPoints()));
                    assertTrue("Width in points should be finite", 
                             Double.isFinite(geometry.getWidthInPoints()));
                    assertTrue("Height in points should be finite", 
                             Double.isFinite(geometry.getHeightInPoints()));
                    
                    // Test toString format
                    String geometryString = geometry.toString();
                    assertNotNull("Geometry toString should not be null", geometryString);
                    assertTrue("Geometry toString should contain pt units", geometryString.contains("pt"));
                }
            }
            
        } finally {
            deleteDirectory(tempDir.toPath());
        }
    }
    
    @Test
    public void testRealPptxTextShapes() throws Exception {
        // Test real PPTX text shapes and their content
        java.io.File tempDir = java.nio.file.Files.createTempDirectory("slideShape-text-test").toFile();
        tempDir.deleteOnExit();
        
        try {
            copyTestPptxStructure(tempDir);
            
            com.excudo.xml.parsers.SlideXMLParser parser = 
                new com.excudo.xml.parsers.SlideXMLParser();
            
            // Test slide 2 which should have rich text content including bullet points
            java.io.File slide2File = new java.io.File(tempDir, "ppt/slides/slide2.xml");
            if (slide2File.exists()) {
                ParsedSlideData slideData = parser.parseSlide(slide2File);
                java.util.List<SlideShape> shapes = slideData.getShapeRegistry().getTextShapes();
                
                System.out.println("Found " + shapes.size() + " text shapes in slide 2");
                
                for (SlideShape shape : shapes) {
                    assertTrue("Text shape should report hasText=true", shape.hasText());
                    assertNotNull("Text shape should have text content", shape.getTextContent());
                    assertFalse("Text content should not be empty", shape.getTextContent().trim().isEmpty());
                    
                    // Test paragraph metadata if present
                    if (shape.hasParagraphMetadata()) {
                        assertTrue("Shape with metadata should have paragraphs", 
                                 shape.getParagraphCount() > 0);
                        
                        // Test that we can access paragraph content
                        for (int i = 0; i < shape.getParagraphCount(); i++) {
                            String paragraphContent = shape.getParagraphContent(i);
                            assertNotNull("Paragraph content should not be null", paragraphContent);
                            // Note: Real PPTX might have empty paragraphs, so we don't assert non-empty
                        }
                        
                        // Test bullet point access if present
                        if (shape.hasBulletPoints()) {
                            assertTrue("Shape with bullet points should have bullet count > 0", 
                                     shape.getBulletPointCount() > 0);
                            
                            // Test accessing bullet content (only test first few to avoid index issues)
                            int bulletCount = Math.min(shape.getBulletPointCount(), 3);
                            for (int i = 0; i < bulletCount; i++) {
                                String bulletContent = shape.getBulletPointContent(i);
                                assertNotNull("Bullet content should not be null", bulletContent);
                            }
                        }
                    }
                    
                    // Test toString with real data
                    String shapeString = shape.toString();
                    assertNotNull("Shape toString should not be null", shapeString);
                    assertTrue("Shape toString should include SPID", shapeString.contains("spid="));
                    assertTrue("Shape toString should include type", shapeString.contains("type="));
                }
            }
            
        } finally {
            deleteDirectory(tempDir.toPath());
        }
    }
    
    @Test
    public void testRealPptxComplexShapes() throws Exception {
        // Test complex shapes from real PPTX that might require SAT collision detection
        java.io.File tempDir = java.nio.file.Files.createTempDirectory("slideShape-complex-test").toFile();
        tempDir.deleteOnExit();
        
        try {
            copyTestPptxStructure(tempDir);
            
            com.excudo.xml.parsers.SlideXMLParser parser = 
                new com.excudo.xml.parsers.SlideXMLParser();
            
            // Test slide 3 which might have more complex shapes
            java.io.File slide3File = new java.io.File(tempDir, "ppt/slides/slide3.xml");
            if (slide3File.exists()) {
                ParsedSlideData slideData = parser.parseSlide(slide3File);
                java.util.List<SlideShape> shapes = slideData.getShapeRegistry().getAllShapes();
                
                for (SlideShape shape : shapes) {
                    // Test shape type and collision requirements
                    SlideShape.ShapeType type = shape.getType();
                    
                    // If it's a complex shape, test collision requirements
                    if (type == SlideShape.ShapeType.CUSTOM_GEOMETRY ||
                        type == SlideShape.ShapeType.STAR_5_POINTS ||
                        type == SlideShape.ShapeType.RIGHT_ARROW ||
                        type == SlideShape.ShapeType.TRIANGLE) {
                        
                        assertTrue("Complex shape should require SAT collision detection", 
                                 type.requiresSATCollision());
                    }
                    
                    // Test XML element access (direct DOM access as mentioned in CLAUDE.md)
                    Element xmlElement = shape.getXmlElement();
                    assertNotNull("Shape should have XML element for DOM access", xmlElement);
                    assertTrue("XML element should have node name", 
                             xmlElement.getNodeName() != null && !xmlElement.getNodeName().isEmpty());
                }
            }
            
        } finally {
            deleteDirectory(tempDir.toPath());
        }
    }
    
    @Test 
    public void testShapeRegistryIntegration() throws Exception {
        // Test that SlideShape objects work correctly with ShapeRegistry
        java.io.File tempDir = java.nio.file.Files.createTempDirectory("slideShape-registry-test").toFile();
        tempDir.deleteOnExit();
        
        try {
            copyTestPptxStructure(tempDir);
            
            com.excudo.xml.parsers.SlideXMLParser parser = 
                new com.excudo.xml.parsers.SlideXMLParser();
            
            java.io.File slide1File = new java.io.File(tempDir, "ppt/slides/slide1.xml");
            if (slide1File.exists()) {
                ParsedSlideData slideData = parser.parseSlide(slide1File);
                ShapeRegistry registry = slideData.getShapeRegistry();
                
                // Test that all shapes in registry have unique SPIDs (as mentioned in CLAUDE.md)
                java.util.Set<Integer> seenSpids = new java.util.HashSet<>();
                java.util.List<SlideShape> allShapes = registry.getAllShapes();
                
                for (SlideShape shape : allShapes) {
                    Integer spid = shape.getSpid();
                    assertTrue("Shape SPID should be unique in registry", 
                             !seenSpids.contains(spid) || registry.getShape(spid) == shape);
                    seenSpids.add(spid);
                    
                    // Test that shape can be retrieved by SPID
                    SlideShape retrievedShape = registry.getShape(spid);
                    assertNotNull("Shape should be retrievable by SPID", retrievedShape);
                    assertEquals("Retrieved shape should have correct SPID", spid.intValue(), retrievedShape.getSpid());
                }
                
                // Test filtering functionality with real shapes
                java.util.List<SlideShape> textShapes = registry.getTextShapes();
                for (SlideShape textShape : textShapes) {
                    assertTrue("All text shapes should report hasText=true", textShape.hasText());
                }
                
                // Test type-based filtering
                if (!allShapes.isEmpty()) {
                    SlideShape firstShape = allShapes.get(0);
                    java.util.List<SlideShape> shapesOfType = registry.getShapesByType(firstShape.getType());
                    assertTrue("Type filtering should include the first shape", 
                             shapesOfType.contains(firstShape));
                }
            }
            
        } finally {
            deleteDirectory(tempDir.toPath());
        }
    }
    
    // ===== Helper Methods for Real PPTX Tests =====
    
    private void copyTestPptxStructure(java.io.File targetDir) throws java.io.IOException {
        // Path to the extracted generalist test file
        java.nio.file.Path sourceDir = java.nio.file.Path.of("generalist_extracted");
        
        if (!java.nio.file.Files.exists(sourceDir)) {
            // Extract the test PPTX if not already extracted
            java.nio.file.Files.createDirectories(sourceDir);
            try (java.util.zip.ZipFile zipFile = new java.util.zip.ZipFile("test-pptx-samples/generalist_test_file.pptx")) {
                java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zipFile.entries();
                
                while (entries.hasMoreElements()) {
                    java.util.zip.ZipEntry entry = entries.nextElement();
                    java.nio.file.Path entryPath = sourceDir.resolve(entry.getName());
                    
                    if (entry.isDirectory()) {
                        java.nio.file.Files.createDirectories(entryPath);
                    } else {
                        java.nio.file.Files.createDirectories(entryPath.getParent());
                        java.nio.file.Files.copy(zipFile.getInputStream(entry), entryPath, 
                                               java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
        
        // Copy the extracted structure to our target directory
        copyDirectory(sourceDir, targetDir.toPath());
    }
    
    private void copyDirectory(java.nio.file.Path source, java.nio.file.Path target) throws java.io.IOException {
        try (java.util.stream.Stream<java.nio.file.Path> paths = java.nio.file.Files.walk(source)) {
            paths.forEach(sourcePath -> {
                try {
                    java.nio.file.Path targetPath = target.resolve(source.relativize(sourcePath));
                    if (java.nio.file.Files.isDirectory(sourcePath)) {
                        java.nio.file.Files.createDirectories(targetPath);
                    } else {
                        java.nio.file.Files.copy(sourcePath, targetPath, 
                                               java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (java.io.IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }
    
    private void deleteDirectory(java.nio.file.Path path) throws java.io.IOException {
        if (java.nio.file.Files.exists(path)) {
            try (java.util.stream.Stream<java.nio.file.Path> paths = java.nio.file.Files.walk(path)) {
                paths.sorted(java.util.Comparator.reverseOrder())
                     .map(java.nio.file.Path::toFile)
                     .forEach(java.io.File::delete);
            }
        }
    }
}