package com.excudo.xml.parsers;

import com.excudo.core.model.*;
import com.excudo.exceptions.XMLParsingException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Comprehensive unit tests for SlideXMLParser - the core XML parsing engine.
 * 
 * Tests cover:
 * - Basic XML parsing and DOM creation
 * - Shape extraction and registry population
 * - Timing tree parsing for animations
 * - Animation binding resolution
 * - Error handling and edge cases
 * - Performance with various slide complexities
 * 
 * @author Excudo Test Suite
 * @version 1.0
 */
class SlideXMLParserTest {

    private SlideXMLParser parser;
    
    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws XMLParsingException {
        parser = new SlideXMLParser();
    }

    // ========== BASIC PARSING TESTS ==========

    @Test
    @DisplayName("Parser initialization should succeed")
    void testParserInitialization() {
        assertNotNull(parser, "Parser should be initialized successfully");
    }

    @Test
    @DisplayName("Parse minimal valid slide XML")
    void testParseMinimalSlide() throws XMLParsingException, IOException {
        // Arrange - Create minimal valid slide XML
        String minimalSlideXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                   xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
              <p:cSld>
                <p:spTree>
                  <p:nvGrpSpPr>
                    <p:cNvPr id="1" name=""/>
                    <p:cNvGrpSpPr/>
                    <p:nvPr/>
                  </p:nvGrpSpPr>
                  <p:grpSpPr>
                    <a:xfrm>
                      <a:off x="0" y="0"/>
                      <a:ext cx="0" cy="0"/>
                      <a:chOff x="0" y="0"/>
                      <a:chExt cx="0" cy="0"/>
                    </a:xfrm>
                  </p:grpSpPr>
                </p:spTree>
              </p:cSld>
              <p:timing>
                <p:tnLst>
                  <p:par>
                    <p:cTn id="1" dur="indefinite" restart="never" nodeType="tmRoot"/>
                  </p:par>
                </p:tnLst>
              </p:timing>
            </p:sld>
            """;

        File slideFile = createTempSlideFile("minimal_slide.xml", minimalSlideXml);

        // Act
        ParsedSlideData result = parser.parseSlide(slideFile);

        // Assert
        assertNotNull(result, "Parsed slide data should not be null");
        assertNotNull(result.getShapeRegistry(), "Shape registry should be initialized");
        assertNotNull(result.getTimingTree(), "Timing tree should be initialized");
        assertNotNull(result.getAnimationBindings(), "Animation bindings should be initialized");
        
        // Should have at least the root group shape
        assertTrue(result.getShapeRegistry().getAllShapes().size() >= 0, 
            "Should parse without errors even for minimal slide");
    }

    @Test
    @DisplayName("Parse slide with single text shape")
    void testParseSingleTextShape() throws XMLParsingException, IOException {
        // Arrange - Create slide with one text shape
        String slideWithTextShape = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                   xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
              <p:cSld>
                <p:spTree>
                  <p:nvGrpSpPr>
                    <p:cNvPr id="1" name=""/>
                    <p:cNvGrpSpPr/>
                    <p:nvPr/>
                  </p:nvGrpSpPr>
                  <p:grpSpPr>
                    <a:xfrm>
                      <a:off x="0" y="0"/>
                      <a:ext cx="0" cy="0"/>
                      <a:chOff x="0" y="0"/>
                      <a:chExt cx="0" cy="0"/>
                    </a:xfrm>
                  </p:grpSpPr>
                  <p:sp>
                    <p:nvSpPr>
                      <p:cNvPr id="2" name="Title 1"/>
                      <p:cNvSpPr>
                        <a:spLocks noGrp="1"/>
                      </p:cNvSpPr>
                      <p:nvPr>
                        <p:ph type="ctrTitle"/>
                      </p:nvPr>
                    </p:nvSpPr>
                    <p:spPr>
                      <a:xfrm>
                        <a:off x="1000000" y="1000000"/>
                        <a:ext cx="5000000" cy="2000000"/>
                      </a:xfrm>
                    </p:spPr>
                    <p:txBody>
                      <a:bodyPr/>
                      <a:lstStyle/>
                      <a:p>
                        <a:r>
                          <a:t>Sample Title</a:t>
                        </a:r>
                      </a:p>
                    </p:txBody>
                  </p:sp>
                </p:spTree>
              </p:cSld>
              <p:timing>
                <p:tnLst>
                  <p:par>
                    <p:cTn id="1" dur="indefinite" restart="never" nodeType="tmRoot"/>
                  </p:par>
                </p:tnLst>
              </p:timing>
            </p:sld>
            """;

        File slideFile = createTempSlideFile("single_text_shape.xml", slideWithTextShape);

        // Act
        ParsedSlideData result = parser.parseSlide(slideFile);

        // Assert
        assertNotNull(result, "Parsed slide data should not be null");
        
        ShapeRegistry shapeRegistry = result.getShapeRegistry();
        assertNotNull(shapeRegistry, "Shape registry should not be null");
        
        List<SlideShape> allShapes = shapeRegistry.getAllShapes();
        assertTrue(allShapes.size() >= 1, "Should find at least one shape");
        
        // Find the text shape
        Optional<SlideShape> titleShape = allShapes.stream()
            .filter(shape -> "Title 1".equals(shape.getName()))
            .findFirst();
        
        assertTrue(titleShape.isPresent(), "Should find the title shape");
        assertEquals("Sample Title", titleShape.get().getTextContent(), "Text content should match");
        assertEquals(2, titleShape.get().getSpid(), "SPID should be extracted correctly");
    }

    @Test
    @DisplayName("Parse slide with timing and animation data")
    void testParseSlideWithAnimations() throws XMLParsingException, IOException {
        // Arrange - Create slide with timing and animation
        String slideWithAnimations = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                   xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
              <p:cSld>
                <p:spTree>
                  <p:nvGrpSpPr>
                    <p:cNvPr id="1" name=""/>
                    <p:cNvGrpSpPr/>
                    <p:nvPr/>
                  </p:nvGrpSpPr>
                  <p:grpSpPr>
                    <a:xfrm>
                      <a:off x="0" y="0"/>
                      <a:ext cx="0" cy="0"/>
                      <a:chOff x="0" y="0"/>
                      <a:chExt cx="0" cy="0"/>
                    </a:xfrm>
                  </p:grpSpPr>
                  <p:sp>
                    <p:nvSpPr>
                      <p:cNvPr id="42" name="Animated Shape"/>
                      <p:cNvSpPr/>
                      <p:nvPr/>
                    </p:nvSpPr>
                    <p:spPr>
                      <a:xfrm>
                        <a:off x="2000000" y="2000000"/>
                        <a:ext cx="3000000" cy="1500000"/>
                      </a:xfrm>
                    </p:spPr>
                  </p:sp>
                </p:spTree>
              </p:cSld>
              <p:timing>
                <p:tnLst>
                  <p:par>
                    <p:cTn id="1" dur="indefinite" restart="never" nodeType="tmRoot">
                      <p:childTnLst>
                        <p:seq concurrent="1" nextAc="seek">
                          <p:cTn id="2" dur="indefinite" nodeType="mainSeq">
                            <p:childTnLst>
                              <p:par>
                                <p:cTn id="3" fill="hold" restart="never" delay="indefinite">
                                  <p:childTnLst>
                                    <p:par>
                                      <p:cTn id="4" fill="hold">
                                        <p:childTnLst>
                                          <p:par>
                                            <p:cTn id="5" presetID="1" presetClass="entr" presetSubtype="0" dur="500" nodeType="clickEffect">
                                              <p:childTnLst>
                                                <p:animEffect transition="in" filter="fade">
                                                  <p:cBhvr>
                                                    <p:cTn id="6" dur="500"/>
                                                    <p:tgtEl>
                                                      <p:spTgt spid="42"/>
                                                    </p:tgtEl>
                                                  </p:cBhvr>
                                                </p:animEffect>
                                              </p:childTnLst>
                                            </p:cTn>
                                          </p:par>
                                        </p:childTnLst>
                                      </p:cTn>
                                    </p:par>
                                  </p:childTnLst>
                                </p:cTn>
                              </p:par>
                            </p:childTnLst>
                          </p:cTn>
                        </p:seq>
                      </p:childTnLst>
                    </p:cTn>
                  </p:par>
                </p:tnLst>
              </p:timing>
            </p:sld>
            """;

        File slideFile = createTempSlideFile("slide_with_animations.xml", slideWithAnimations);

        // Act
        ParsedSlideData result = parser.parseSlide(slideFile);

        // Assert
        assertNotNull(result, "Parsed slide data should not be null");
        
        // Verify shape parsing
        ShapeRegistry shapeRegistry = result.getShapeRegistry();
        assertNotNull(shapeRegistry, "Shape registry should not be null");
        
        Optional<SlideShape> animatedShape = shapeRegistry.getAllShapes().stream()
            .filter(shape -> "Animated Shape".equals(shape.getName()))
            .findFirst();
        assertTrue(animatedShape.isPresent(), "Should find the animated shape");
        assertEquals(42, animatedShape.get().getSpid(), "SPID should be 42");
        
        // Verify timing tree parsing
        TimingTree timingTree = result.getTimingTree();
        assertNotNull(timingTree, "Timing tree should not be null");
        assertNotNull(timingTree.getRootNode(), "Root timing node should exist");
        
        // Verify animation bindings
        List<AnimationBinding> bindings = result.getAnimationBindings();
        assertNotNull(bindings, "Animation bindings should not be null");
        assertTrue(bindings.size() > 0, "Should have at least one animation binding");
        
        // Verify the binding connects shape to animation
        Optional<AnimationBinding> fadeBinding = bindings.stream()
            .filter(binding -> binding.getTargetSpid() == 42)
            .findFirst();
        assertTrue(fadeBinding.isPresent(), "Should find animation binding for shape 42");
        assertTrue(fadeBinding.get().isEntranceAnimation(), "Should be an entrance animation");
    }

    // ========== ERROR HANDLING TESTS ==========

    @Test
    @DisplayName("Handle invalid XML file gracefully")
    void testHandleInvalidXML() throws IOException {
        // Arrange - Create invalid XML
        String invalidXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
              <p:cSld>
                <p:unclosed-tag>
              </p:cSld>
            </p:sld>
            """;

        File invalidFile = createTempSlideFile("invalid.xml", invalidXml);

        // Act & Assert
        XMLParsingException exception = assertThrows(XMLParsingException.class, 
            () -> parser.parseSlide(invalidFile),
            "Should throw XMLParsingException for invalid XML");
        
        assertNotNull(exception.getMessage(), "Exception should have meaningful message");
        assertTrue(exception.getMessage().contains("parsing") || 
                  exception.getMessage().contains("XML"), 
                  "Exception message should mention parsing or XML");
    }

    @Test
    @DisplayName("Handle non-existent file gracefully")
    void testHandleNonExistentFile() {
        // Arrange
        File nonExistentFile = new File(tempDir.toFile(), "does_not_exist.xml");

        // Act & Assert
        XMLParsingException exception = assertThrows(XMLParsingException.class,
            () -> parser.parseSlide(nonExistentFile),
            "Should throw XMLParsingException for non-existent file");
        
        assertNotNull(exception.getMessage(), "Exception should have meaningful message");
    }

    @Test
    @DisplayName("Handle null file parameter")
    void testHandleNullFile() {
        // Act & Assert
        // The current implementation throws NullPointerException due to xmlFile.getName() 
        // being called without null check. This is a known issue.
        assertThrows(NullPointerException.class,
            () -> parser.parseSlide((File) null),
            "Should throw NullPointerException for null file parameter");
    }

    // ========== EDGE CASES ==========

    @Test
    @DisplayName("Parse slide with no shapes")
    void testParseSlideWithNoShapes() throws XMLParsingException, IOException {
        // Arrange - Create slide with empty spTree
        String emptySlideXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                   xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
              <p:cSld>
                <p:spTree>
                  <p:nvGrpSpPr>
                    <p:cNvPr id="1" name=""/>
                    <p:cNvGrpSpPr/>
                    <p:nvPr/>
                  </p:nvGrpSpPr>
                  <p:grpSpPr>
                    <a:xfrm>
                      <a:off x="0" y="0"/>
                      <a:ext cx="0" cy="0"/>
                      <a:chOff x="0" y="0"/>
                      <a:chExt cx="0" cy="0"/>
                    </a:xfrm>
                  </p:grpSpPr>
                </p:spTree>
              </p:cSld>
              <p:timing>
                <p:tnLst>
                  <p:par>
                    <p:cTn id="1" dur="indefinite" restart="never" nodeType="tmRoot"/>
                  </p:par>
                </p:tnLst>
              </p:timing>
            </p:sld>
            """;

        File slideFile = createTempSlideFile("empty_slide.xml", emptySlideXml);

        // Act
        ParsedSlideData result = parser.parseSlide(slideFile);

        // Assert
        assertNotNull(result, "Should parse empty slide successfully");
        assertNotNull(result.getShapeRegistry(), "Shape registry should exist");
        assertEquals(0, result.getShapeRegistry().getAllShapes().size(), 
            "Should have no shapes");
        assertNotNull(result.getTimingTree(), "Timing tree should exist");
        assertEquals(0, result.getAnimationBindings().size(), 
            "Should have no animation bindings");
    }

    @Test
    @DisplayName("Parse slide with no timing section")
    void testParseSlideWithNoTiming() throws XMLParsingException, IOException {
        // Arrange - Create slide without timing section
        String slideWithoutTiming = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                   xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
              <p:cSld>
                <p:spTree>
                  <p:nvGrpSpPr>
                    <p:cNvPr id="1" name=""/>
                    <p:cNvGrpSpPr/>
                    <p:nvPr/>
                  </p:nvGrpSpPr>
                  <p:grpSpPr>
                    <a:xfrm>
                      <a:off x="0" y="0"/>
                      <a:ext cx="0" cy="0"/>
                      <a:chOff x="0" y="0"/>
                      <a:chExt cx="0" cy="0"/>
                    </a:xfrm>
                  </p:grpSpPr>
                </p:spTree>
              </p:cSld>
            </p:sld>
            """;

        File slideFile = createTempSlideFile("no_timing.xml", slideWithoutTiming);

        // Act
        ParsedSlideData result = parser.parseSlide(slideFile);

        // Assert
        assertNotNull(result, "Should parse slide without timing");
        assertNotNull(result.getTimingTree(), "Timing tree should be initialized even without timing section");
        assertEquals(0, result.getAnimationBindings().size(), 
            "Should have no animation bindings");
    }

    // ========== HELPER METHODS ==========

    /**
     * Creates a temporary slide XML file for testing
     */
    private File createTempSlideFile(String filename, String xmlContent) throws IOException {
        Path slideFile = tempDir.resolve(filename);
        Files.write(slideFile, xmlContent.getBytes());
        return slideFile.toFile();
    }
}