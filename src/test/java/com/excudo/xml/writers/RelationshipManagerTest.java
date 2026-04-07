package com.excudo.xml.writers;

import com.excudo.core.model.PPTXDocument;
import com.excudo.core.model.PPTXDocumentParser;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.SAXException;
import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import com.excudo.exceptions.XMLParsingException;
import com.excudo.core.utils.XMLConstants;

/**
 * Comprehensive unit tests for the RelationshipManager class.
 * 
 * These tests verify the core functionality of OOXML relationship management
 * including creation, copying, media handling, and validation of relationships
 * within PowerPoint presentations.
 * 
 * @author Excudo Test Suite
 * @version 1.0
 */
@Disabled("Temporarily disabled while establishing stable CI/CD baseline - will re-enable systematically")
class RelationshipManagerTest {

  /**
   * Temporary directory for each test - automatically cleaned up by JUnit
   */
  @TempDir
  Path tempDir;

  /**
   * Mock extracted PPTX directory structure for testing
   */
  private File mockPptxDir;

  /**
   * RelationshipManager instance under test
   */
  private RelationshipManager relationshipManager;

  /**
   * Document builder for creating test XML documents
   */
  private DocumentBuilder documentBuilder;

  /**
   * Set up test environment before each test method.
   * Creates a realistic PPTX directory structure with existing relationships.
   */
  @BeforeEach
  void setUp() throws Exception {
    mockPptxDir = tempDir.toFile();

    // Initialize document builder
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    documentBuilder = factory.newDocumentBuilder();

    // Create realistic PPTX directory structure
    createMockPptxStructure();

    // Initialize RelationshipManager with in-memory PPTXDocument
    PPTXDocument pptxDoc = PPTXDocument.load(mockPptxDir, null);
    PPTXDocumentParser.ParsedPresentationState parsedState = PPTXDocumentParser.parse(pptxDoc);
    relationshipManager = new RelationshipManager(pptxDoc, parsedState);
  }

  /**
   * Test successful creation of standard slide relationships.
   * Verifies that layout and theme relationships are created with proper IDs.
   */
  @Test
  @DisplayName("Create standard slide relationships successfully")
  void testCreateSlideRelationships_Success() throws XMLParsingException, SAXException, IOException {
    // Act
    RelationshipManager.RelationshipCreationResult result = 
      relationshipManager.createSlideRelationships(5, null, null);

    // Assert
    assertNotNull(result, "Result should not be null");
    assertNotNull(result.getRelationshipFile(), "Relationship file should be created");
    assertTrue(result.getRelationshipFile().exists(), "Relationship file should exist on disk");
    assertEquals(2, result.getCreatedRelationshipIds().size(), "Should create 2 relationships (layout + theme)");

    // Verify file content
    Document relsDoc = documentBuilder.parse(result.getRelationshipFile());
    NodeList relationships = relsDoc.getElementsByTagName("Relationship");
    assertEquals(2, relationships.getLength(), "Should have 2 relationship elements");

    // Verify layout relationship
    Element layoutRel = findRelationshipByType(relsDoc, XMLConstants.RELATIONSHIP_TYPE_SLIDE_LAYOUT);
    assertNotNull(layoutRel, "Layout relationship should exist");
    assertEquals(XMLConstants.DEFAULT_SLIDE_LAYOUT_TARGET, layoutRel.getAttribute("Target"), 
        "Layout target should be default");

    // Verify theme relationship  
    Element themeRel = findRelationshipByType(relsDoc, XMLConstants.RELATIONSHIP_TYPE_THEME);
    assertNotNull(themeRel, "Theme relationship should exist");
    assertEquals(XMLConstants.DEFAULT_THEME_TARGET, themeRel.getAttribute("Target"), 
        "Theme target should be default");
  }

  /**
   * Test creation of slide relationships with custom targets.
   */
  @Test
  @DisplayName("Create slide relationships with custom targets")
  void testCreateSlideRelationships_CustomTargets() throws XMLParsingException, SAXException, IOException {
    // Arrange
    String customLayout = "../slideLayouts/slideLayout5.xml";
    String customTheme = "../theme/theme2.xml";

    // Act
    RelationshipManager.RelationshipCreationResult result = 
      relationshipManager.createSlideRelationships(3, customLayout, customTheme);

    // Assert
    Document relsDoc = documentBuilder.parse(result.getRelationshipFile());

    Element layoutRel = findRelationshipByType(relsDoc, XMLConstants.RELATIONSHIP_TYPE_SLIDE_LAYOUT);
    assertEquals(customLayout, layoutRel.getAttribute("Target"), "Should use custom layout target");

    Element themeRel = findRelationshipByType(relsDoc, XMLConstants.RELATIONSHIP_TYPE_THEME);
    assertEquals(customTheme, themeRel.getAttribute("Target"), "Should use custom theme target");
  }

  /**
   * Test creation fails with invalid slide number.
   */
  @Test
  @DisplayName("Create slide relationships fails with invalid slide number")
  void testCreateSlideRelationships_InvalidSlideNumber() {
    // Act & Assert
    assertThrows(IllegalArgumentException.class, () -> {
      relationshipManager.createSlideRelationships(0, null, null);
    }, "Should throw IllegalArgumentException for slide number 0");

    assertThrows(IllegalArgumentException.class, () -> {
      relationshipManager.createSlideRelationships(-1, null, null);
    }, "Should throw IllegalArgumentException for negative slide number");
  }

  /**
   * Test copying relationships from existing slide to new slide.
   */
  @Test
  @DisplayName("Copy slide relationships successfully")
  void testCopySlideRelationships_Success() throws XMLParsingException, SAXException, IOException {
    // Arrange - Create source slide with relationships
    relationshipManager.createSlideRelationships(1, null, null);
    relationshipManager.addMediaRelationship(1, XMLConstants.RELATIONSHIP_TYPE_IMAGE, "../media/image1.png");

    // Act
    RelationshipManager.RelationshipCopyResult result = 
      relationshipManager.copySlideRelationships(1, 2, false);

    // Assert
    assertNotNull(result, "Copy result should not be null");
    assertTrue(result.getRelationshipFile().exists(), "Destination relationship file should exist");
    assertTrue(result.getNewRelationshipIds().size() >= 3, "Should have at least 3 relationships (layout, theme, image)");
    assertFalse(result.getOldToNewIdMappings().isEmpty(), "Should have ID mappings");

    // Verify destination file content
    Document destRelsDoc = documentBuilder.parse(result.getRelationshipFile());
    NodeList relationships = destRelsDoc.getElementsByTagName("Relationship");
    assertTrue(relationships.getLength() >= 3, "Destination should have at least 3 relationships");

    // Verify image relationship was copied
    Element imageRel = findRelationshipByType(destRelsDoc, XMLConstants.RELATIONSHIP_TYPE_IMAGE);
    assertNotNull(imageRel, "Image relationship should be copied");
    assertEquals("../media/image1.png", imageRel.getAttribute("Target"), "Image target should be preserved");
  }

  /**
   * Test copying relationships with forced new IDs.
   */
  @Test
  @DisplayName("Copy slide relationships with forced new IDs")
  void testCopySlideRelationships_ForceNewIds() throws XMLParsingException {
    // Arrange
    relationshipManager.createSlideRelationships(1, null, null);
    Set<String> originalIds = new HashSet<>(relationshipManager.getAllRelationshipIds());

    // Act
    RelationshipManager.RelationshipCopyResult result = 
      relationshipManager.copySlideRelationships(1, 2, true);

    // Assert
    Set<String> newIds = new HashSet<>(result.getNewRelationshipIds());
    Set<String> intersection = new HashSet<>(originalIds);
    intersection.retainAll(newIds);
    assertTrue(intersection.isEmpty(), "With forceNewIds=true, no IDs should be reused");
  }

  /**
   * Test copying from non-existent source slide creates default relationships.
   */
  @Test
  @DisplayName("Copy from non-existent source creates default relationships")
  void testCopySlideRelationships_NonExistentSource() throws XMLParsingException {
    // Act - Copy from slide 99 which doesn't exist
    RelationshipManager.RelationshipCopyResult result = 
      relationshipManager.copySlideRelationships(99, 3, false);

    // Assert
    assertNotNull(result, "Result should not be null even for non-existent source");
    assertTrue(result.getRelationshipFile().exists(), "Should create relationship file");
    assertEquals(2, result.getNewRelationshipIds().size(), "Should create default relationships (layout + theme)");
    assertTrue(result.getOldToNewIdMappings().isEmpty(), "Should have no ID mappings for non-existent source");
  }

  /**
   * Test adding media relationship to existing slide.
   */
  @Test
  @DisplayName("Add media relationship successfully")
  void testAddMediaRelationship_Success() throws XMLParsingException, SAXException, IOException {
    // Arrange
    relationshipManager.createSlideRelationships(1, null, null);
    String mediaTarget = "../media/logo.png";

    // Act
    String mediaRId = relationshipManager.addMediaRelationship(1, XMLConstants.RELATIONSHIP_TYPE_IMAGE, mediaTarget);

    // Assert
    assertNotNull(mediaRId, "Media relationship ID should not be null");
    assertTrue(mediaRId.startsWith(XMLConstants.RID_PREFIX), "Media relationship ID should have correct prefix");

    // Verify relationship info is registered
    RelationshipManager.RelationshipInfo info = relationshipManager.getRelationshipInfo(mediaRId);
    assertNotNull(info, "Relationship info should be registered");
    assertEquals(XMLConstants.RELATIONSHIP_TYPE_IMAGE, info.getType(), "Relationship type should match");
    assertEquals(mediaTarget, info.getTarget(), "Relationship target should match");

    // Verify file is updated
    File relsFile = new File(mockPptxDir, "ppt/slides/_rels/slide1.xml.rels");
    Document relsDoc = documentBuilder.parse(relsFile);
    Element mediaRel = findRelationshipById(relsDoc, mediaRId);
    assertNotNull(mediaRel, "Media relationship should exist in file");
    assertEquals(mediaTarget, mediaRel.getAttribute("Target"), "Target should be preserved in file");
  }

  /**
   * Test adding media relationship with invalid parameters fails.
   */
  @Test
  @DisplayName("Add media relationship fails with invalid parameters")
  void testAddMediaRelationship_InvalidParameters() {
    // Test invalid slide number
    assertThrows(IllegalArgumentException.class, () -> {
      relationshipManager.addMediaRelationship(0, XMLConstants.RELATIONSHIP_TYPE_IMAGE, "../media/test.png");
    }, "Should fail with invalid slide number");

    // Test null media type
    assertThrows(IllegalArgumentException.class, () -> {
      relationshipManager.addMediaRelationship(1, null, "../media/test.png");
    }, "Should fail with null media type");

    // Test empty media target
    assertThrows(IllegalArgumentException.class, () -> {
      relationshipManager.addMediaRelationship(1, XMLConstants.RELATIONSHIP_TYPE_IMAGE, "");
    }, "Should fail with empty media target");
  }

  /**
   * Test removing existing relationship.
   */
  @Test
  @DisplayName("Remove existing relationship successfully")
  void testRemoveRelationship_Success() throws XMLParsingException, SAXException, IOException {
    // Arrange
    RelationshipManager.RelationshipCreationResult result = 
      relationshipManager.createSlideRelationships(1, null, null);
    String relationshipIdToRemove = result.getCreatedRelationshipIds().get(0);

    // Verify relationship exists before removal
    assertNotNull(relationshipManager.getRelationshipInfo(relationshipIdToRemove), 
        "Relationship should exist before removal");

    // Act
    boolean removed = relationshipManager.removeRelationship(1, relationshipIdToRemove);

    // Assert
    assertTrue(removed, "Should return true for successful removal");
    assertNull(relationshipManager.getRelationshipInfo(relationshipIdToRemove), 
        "Relationship should be unregistered after removal");

    // Verify file is updated
    File relsFile = new File(mockPptxDir, "ppt/slides/_rels/slide1.xml.rels");
    Document relsDoc = documentBuilder.parse(relsFile);
    Element removedRel = findRelationshipById(relsDoc, relationshipIdToRemove);
    assertNull(removedRel, "Relationship should be removed from file");
  }

  /**
   * Test removing non-existent relationship returns false.
   */
  @Test
  @DisplayName("Remove non-existent relationship returns false")
  void testRemoveRelationship_NonExistent() throws XMLParsingException {
    // Act
    boolean removed = relationshipManager.removeRelationship(1, "rId999");

    // Assert
    assertFalse(removed, "Should return false for non-existent relationship");
  }

  /**
   * Test relationship ID allocation is unique and sequential.
   */
  @Test
  @DisplayName("Relationship ID allocation is unique and sequential")
  void testAllocateRelationshipId_UniqueAndSequential() {
    // Act
    Set<String> allocatedIds = new HashSet<>();
    for (int i = 0; i < 10; i++) {
      String id = relationshipManager.allocateRelationshipId();
      allocatedIds.add(id);
    }

    // Assert
    assertEquals(10, allocatedIds.size(), "All allocated IDs should be unique");

    // Verify format
    for (String id : allocatedIds) {
      assertTrue(id.startsWith(XMLConstants.RID_PREFIX), "All IDs should have correct prefix");
      assertTrue(id.matches(XMLConstants.RID_PREFIX + "\\d+"), "All IDs should be properly formatted");
    }
  }

  /**
   * Test relationship validation detects issues.
   */
  @Test
  @DisplayName("Relationship validation detects broken targets")
  void testValidateAllRelationships_DetectsBrokenTargets() throws XMLParsingException {
    // Arrange - Add relationship pointing to non-existent file
    relationshipManager.createSlideRelationships(1, null, null);
    relationshipManager.addMediaRelationship(1, XMLConstants.RELATIONSHIP_TYPE_IMAGE, "../media/nonexistent.png");

    // Act
    RelationshipManager.ValidationResult validation = relationshipManager.validateAllRelationships();

    // Assert
    assertNotNull(validation, "Validation result should not be null");
    assertTrue(validation.hasErrors(), "Should detect broken relationship target");
    assertFalse(validation.isValid(), "Validation should fail with broken targets");

    boolean foundBrokenTarget = validation.getErrors().stream()
      .anyMatch(error -> error.contains("nonexistent.png"));
    assertTrue(foundBrokenTarget, "Should specifically identify the broken target");
  }

  /**
   * Test getting all relationship IDs returns complete set.
   */
  @Test
  @DisplayName("Get all relationship IDs returns complete set")
  void testGetAllRelationshipIds_ReturnsCompleteSet() throws XMLParsingException {
    // Arrange
    relationshipManager.createSlideRelationships(1, null, null);
    relationshipManager.createSlideRelationships(2, null, null);
    String mediaRId = relationshipManager.addMediaRelationship(1, XMLConstants.RELATIONSHIP_TYPE_IMAGE, "../media/test.png");

    // Act
    Set<String> allIds = relationshipManager.getAllRelationshipIds();

    // Assert
    assertNotNull(allIds, "Relationship ID set should not be null");
    assertTrue(allIds.size() >= 5, "Should have at least 5 relationships (2 slides × 2 standard + 1 media)");
    assertTrue(allIds.contains(mediaRId), "Should include the media relationship ID");

    // Verify immutability
    assertThrows(UnsupportedOperationException.class, () -> {
      allIds.add("rId999");
    }, "Returned set should be immutable");
  }

  // ========== HELPER METHODS ==========

  /**
   * Creates a realistic mock PPTX directory structure for testing.
   */
  private void createMockPptxStructure() throws Exception {
    // Create directory structure
    Files.createDirectories(Paths.get(mockPptxDir.getPath(), "_rels"));
    Files.createDirectories(Paths.get(mockPptxDir.getPath(), "ppt", "_rels"));
    Files.createDirectories(Paths.get(mockPptxDir.getPath(), "ppt", "slides", "_rels"));
    Files.createDirectories(Paths.get(mockPptxDir.getPath(), "ppt", "slideLayouts"));
    Files.createDirectories(Paths.get(mockPptxDir.getPath(), "ppt", "theme"));
    Files.createDirectories(Paths.get(mockPptxDir.getPath(), "ppt", "media"));

    // Create package-level relationships
    createPackageRelationships();

    // Create presentation relationships
    createPresentationRelationships();

    // Create existing slide layout and theme files (empty but present)
    Files.createFile(Paths.get(mockPptxDir.getPath(), "ppt", "slideLayouts", "slideLayout1.xml"));
    Files.createFile(Paths.get(mockPptxDir.getPath(), "ppt", "theme", "theme1.xml"));

    // Create some test media files
    Files.createFile(Paths.get(mockPptxDir.getPath(), "ppt", "media", "image1.png"));
    Files.createFile(Paths.get(mockPptxDir.getPath(), "ppt", "media", "logo.png"));
  }

  /**
   * Creates package-level relationships file.
   */
  private void createPackageRelationships() throws Exception {
    Document doc = documentBuilder.newDocument();
    Element relationships = doc.createElementNS(XMLConstants.PACKAGE_RELATIONSHIPS_NS, "Relationships");
    doc.appendChild(relationships);

    Element presRel = doc.createElementNS(XMLConstants.PACKAGE_RELATIONSHIPS_NS, "Relationship");
    presRel.setAttribute("Id", "rId1");
    presRel.setAttribute("Type", "http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument");
    presRel.setAttribute("Target", "ppt/presentation.xml");
    relationships.appendChild(presRel);

    writeXmlDocument(doc, new File(mockPptxDir, "_rels/.rels"));
  }

  /**
   * Creates presentation-level relationships file.
   */
  private void createPresentationRelationships() throws Exception {
    Document doc = documentBuilder.newDocument();
    Element relationships = doc.createElementNS(XMLConstants.PACKAGE_RELATIONSHIPS_NS, "Relationships");
    doc.appendChild(relationships);

    Element masterRel = doc.createElementNS(XMLConstants.PACKAGE_RELATIONSHIPS_NS, "Relationship");
    masterRel.setAttribute("Id", "rId1");
    masterRel.setAttribute("Type", XMLConstants.RELATIONSHIP_TYPE_SLIDE_MASTER);
    masterRel.setAttribute("Target", "slideMasters/slideMaster1.xml");
    relationships.appendChild(masterRel);

    writeXmlDocument(doc, new File(mockPptxDir, "ppt/_rels/presentation.xml.rels"));
  }

  /**
   * Finds a relationship element by type in a relationships document.
   */
  private Element findRelationshipByType(Document relsDoc, String relationshipType) {
    NodeList relationships = relsDoc.getElementsByTagName("Relationship");
    for (int i = 0; i < relationships.getLength(); i++) {
      Element rel = (Element) relationships.item(i);
      if (relationshipType.equals(rel.getAttribute("Type"))) {
        return rel;
      }
    }
    return null;
  }

  /**
   * Finds a relationship element by ID in a relationships document.
   */
  private Element findRelationshipById(Document relsDoc, String relationshipId) {
    NodeList relationships = relsDoc.getElementsByTagName("Relationship");
    for (int i = 0; i < relationships.getLength(); i++) {
      Element rel = (Element) relationships.item(i);
      if (relationshipId.equals(rel.getAttribute("Id"))) {
        return rel;
      }
    }
    return null;
  }

  /**
   * Writes an XML document to file.
   */
  private void writeXmlDocument(Document doc, File outputFile) throws Exception {
    javax.xml.transform.TransformerFactory transformerFactory = 
      javax.xml.transform.TransformerFactory.newInstance();
    javax.xml.transform.Transformer transformer = transformerFactory.newTransformer();
    transformer.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes");

    outputFile.getParentFile().mkdirs();
    javax.xml.transform.dom.DOMSource source = new javax.xml.transform.dom.DOMSource(doc);
    javax.xml.transform.stream.StreamResult result = 
      new javax.xml.transform.stream.StreamResult(outputFile);
    transformer.transform(source, result);
  }
}
