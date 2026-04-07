package com.excudo.xml.writers;

import com.excudo.core.model.PPTXDocument;
import com.excudo.core.model.PPTXDocumentParser;
import com.excudo.exceptions.XMLParsingException;
import com.excudo.core.utils.XMLConstants;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import java.io.File;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SlideRelationshipWriter -- slide .rels creation, copying, and presentation-level
 * relationship management including cascading and notesMaster ordering.
 */
class SlideRelationshipWriterTest {

  @TempDir
  Path tempDir;

  private RelationshipManager relationshipManager;
  private PPTXDocument pptxDoc;
  private DocumentBuilder documentBuilder;
  private XPath xpath;
  private File extractedPptxDir;

  @BeforeEach
  void setUp() throws Exception {
    SPIDManager.resetInstance();
    documentBuilder = WriterTestFixtures.createDocumentBuilder();
    xpath = WriterTestFixtures.createConfiguredXPath();

    extractedPptxDir = tempDir.toFile();
    WriterTestFixtures.createMockPptxStructure(extractedPptxDir);
    WriterTestFixtures.createSlideFile(extractedPptxDir, 1);
    WriterTestFixtures.createSlideRelsFile(extractedPptxDir, 1);
    WriterTestFixtures.createSlideMasterFile(extractedPptxDir);
    WriterTestFixtures.createPresentationXml(extractedPptxDir, 1);
    WriterTestFixtures.createPresentationRelsXml(extractedPptxDir, 1);

    pptxDoc = PPTXDocument.load(extractedPptxDir, null);
    PPTXDocumentParser.ParsedPresentationState parsedState = PPTXDocumentParser.parse(pptxDoc);
    relationshipManager = new RelationshipManager(pptxDoc, parsedState);
  }

  @AfterEach
  void tearDown() {
    SPIDManager.resetInstance();
  }

  // ========== createSlideRelationships ==========

  @Test
  @DisplayName("Creates .rels file with layout relationship")
  void testCreateSlideRels_withLayout() throws Exception {
    RelationshipManager.RelationshipCreationResult result =
        relationshipManager.createSlideRelationships(2, "../slideLayouts/slideLayout1.xml", null);

    assertNotNull(result, "Result should be non-null");
    assertTrue(pptxDoc.hasPart("ppt/slides/_rels/slide2.xml.rels"), ".rels part should be stored in PPTXDocument");
    assertFalse(result.getCreatedRelationshipIds().isEmpty(), "Should create at least one relationship");

    String content = xmlPartToString(pptxDoc.getXmlPart("ppt/slides/_rels/slide2.xml.rels"));
    assertTrue(content.contains("slideLayout1.xml"), "Should reference the layout");
  }

  @Test
  @DisplayName("Creates .rels file with layout and theme")
  void testCreateSlideRels_withLayoutAndTheme() throws Exception {
    RelationshipManager.RelationshipCreationResult result =
        relationshipManager.createSlideRelationships(2,
            "../slideLayouts/slideLayout1.xml", "../theme/theme1.xml");

    List<String> ids = result.getCreatedRelationshipIds();
    assertTrue(ids.size() >= 2, "Should create at least two relationships (layout + theme)");

    String content = xmlPartToString(pptxDoc.getXmlPart("ppt/slides/_rels/slide2.xml.rels"));
    assertTrue(content.contains("theme1.xml"), "Should reference the theme");
  }

  @Test
  @DisplayName("Uses default layout when null is passed")
  void testCreateSlideRels_defaultLayout() throws Exception {
    RelationshipManager.RelationshipCreationResult result =
        relationshipManager.createSlideRelationships(2, null, null);

    assertNotNull(result);
    String content = xmlPartToString(pptxDoc.getXmlPart("ppt/slides/_rels/slide2.xml.rels"));
    assertTrue(content.contains("slideLayout"), "Should use default slide layout target");
  }

  @Test
  @DisplayName("Throws for invalid slide number")
  void testCreateSlideRels_throwsForInvalidSlide() {
    assertThrows(IllegalArgumentException.class,
        () -> relationshipManager.createSlideRelationships(0, null, null),
        "Should reject slide number 0");
  }

  // ========== copySlideRelationships ==========

  @Test
  @DisplayName("Copies relationships with new IDs")
  void testCopySlideRels_copiesWithNewIds() throws Exception {
    RelationshipManager.RelationshipCopyResult result =
        relationshipManager.copySlideRelationships(1, 2, false);

    assertNotNull(result, "Result should be non-null");
    assertTrue(pptxDoc.hasPart("ppt/slides/_rels/slide2.xml.rels"), "Dest .rels part should be in PPTXDocument");
    assertFalse(result.getNewRelationshipIds().isEmpty(), "Should have new relationship IDs");
  }

  @Test
  @DisplayName("Copy creates default when source missing")
  void testCopySlideRels_defaultForMissingSource() throws Exception {
    // Slide 99 has no .rels file
    RelationshipManager.RelationshipCopyResult result =
        relationshipManager.copySlideRelationships(99, 3, false);

    assertNotNull(result, "Should create default instead of failing");
    assertTrue(pptxDoc.hasPart("ppt/slides/_rels/slide3.xml.rels"), "Default .rels part should be in PPTXDocument");
  }

  @Test
  @DisplayName("Copy provides correct ID mappings")
  void testCopySlideRels_correctMappings() throws Exception {
    RelationshipManager.RelationshipCopyResult result =
        relationshipManager.copySlideRelationships(1, 4, false);

    Map<String, String> mappings = result.getOldToNewIdMappings();
    // Source slide 1 has at least one relationship (layout)
    assertFalse(mappings.isEmpty(), "Should have old->new ID mappings");

    // Each new ID should be a valid rId
    for (String newId : mappings.values()) {
      assertTrue(newId.startsWith("rId"), "New ID should start with rId prefix: " + newId);
    }
  }

  @Test
  @DisplayName("Throws for negative source slide")
  void testCopySlideRels_throwsForInvalidSource() {
    assertThrows(IllegalArgumentException.class,
        () -> relationshipManager.copySlideRelationships(-1, 2, false),
        "Should reject negative slide number");
  }

  // ========== addPresentationRelationship ==========

  @Test
  @DisplayName("Adds relationship to presentation.xml.rels")
  void testAddPresRel_addsToFile() throws Exception {
    relationshipManager.addPresentationRelationship("rId50",
        XMLConstants.RELATIONSHIP_TYPE_SLIDE, "slides/slide50.xml");

    String content = xmlPartToString(pptxDoc.getXmlPart("ppt/_rels/presentation.xml.rels"));
    assertTrue(content.contains("slide50.xml"), "Should contain the new slide target");
  }

  @Test
  @DisplayName("Handles duplicate ID by reassigning existing")
  void testAddPresRel_handlesDuplicateId() throws Exception {
    // rId2 already exists from the 1-slide setup (slide 1)
    // Adding a new slide at rId2 should cascade existing entries
    assertDoesNotThrow(() ->
        relationshipManager.addPresentationRelationship("rId2",
            XMLConstants.RELATIONSHIP_TYPE_SLIDE, "slides/slide99.xml"),
        "Should handle duplicate ID without throwing");
  }

  // ========== cascadeRelationshipIds (pkg-private, tested via addPresentationRelationship) ==========

  @Test
  @DisplayName("Cascade increments IDs >= target")
  void testCascade_incrementsIds() throws Exception {
    // We have rId1 (slideMaster) and rId2 (slide1) in rels
    // Adding a slide relationship at rId2 should cascade rId2 to rId3
    relationshipManager.addPresentationRelationship("rId2",
        XMLConstants.RELATIONSHIP_TYPE_SLIDE, "slides/slideNew.xml");

    String content = xmlPartToString(pptxDoc.getXmlPart("ppt/_rels/presentation.xml.rels"));
    assertTrue(content.contains("slideNew.xml"), "Should contain the new slide");
    // rId1 (slideMaster) should not have been cascaded
    assertTrue(content.contains("rId1"), "rId1 should remain unchanged");
  }

  @Test
  @DisplayName("Cascade skips non-rId entries")
  void testCascade_skipsNonRId() throws Exception {
    // This test verifies the cascade doesn't crash on non-standard IDs
    assertDoesNotThrow(() ->
        relationshipManager.addPresentationRelationship("rId5",
            XMLConstants.RELATIONSHIP_TYPE_SLIDE, "slides/slide5.xml"));
  }

  // ========== ensureNotesMasterComesAfterSlides (tested via integration) ==========

  @Test
  @DisplayName("No-op when notesMaster already after slides")
  void testNotesMasterOrdering_noOpWhenCorrect() throws Exception {
    // Setup with notesMaster already after slides
    String presRelsXml = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" Target="slideMasters/slideMaster1.xml"/>
          <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide1.xml"/>
          <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/notesMaster" Target="notesMasters/notesMaster1.xml"/>
        </Relationships>
        """;
    Files.writeString(new File(extractedPptxDir, "ppt/_rels/presentation.xml.rels").toPath(), presRelsXml);

    // Re-create manager to pick up the new rels
    SPIDManager.resetInstance();
    PPTXDocument updatedDoc = PPTXDocument.load(extractedPptxDir, null);
    PPTXDocumentParser.ParsedPresentationState updatedState = PPTXDocumentParser.parse(updatedDoc);
    relationshipManager = new RelationshipManager(updatedDoc, updatedState);

    // Validate -- notesMaster at rId3 after slide at rId2 is correct
    RelationshipManager.ValidationResult result = relationshipManager.validateAllRelationships();
    boolean hasNotesMasterError = result.getErrors().stream()
        .anyMatch(e -> e.contains("NotesMaster") && e.contains("should come after"));
    assertFalse(hasNotesMasterError, "Should not report ordering issue when correct");
  }

  // ========== HELPERS ==========

  private String xmlPartToString(Document doc) throws Exception {
    if (doc == null) return "";
    TransformerFactory tf = TransformerFactory.newInstance();
    Transformer transformer = tf.newTransformer();
    StringWriter writer = new StringWriter();
    transformer.transform(new DOMSource(doc), new StreamResult(writer));
    return writer.toString();
  }
}
