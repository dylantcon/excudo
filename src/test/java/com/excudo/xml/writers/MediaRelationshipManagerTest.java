package com.excudo.xml.writers;

import com.excudo.core.model.PPTXDocument;
import com.excudo.core.model.PPTXDocumentParser;
import com.excudo.exceptions.XMLParsingException;
import com.excudo.core.utils.XMLConstants;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MediaRelationshipManager -- media relationship CRUD via RelationshipManager facade.
 */
class MediaRelationshipManagerTest {

  @TempDir
  Path tempDir;

  private RelationshipManager relationshipManager;
  private PPTXDocument pptxDoc;
  private File extractedPptxDir;

  @BeforeEach
  void setUp() throws Exception {
    SPIDManager.resetInstance();
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

  // ========== addMediaRelationship ==========

  @Test
  @DisplayName("addMediaRelationship returns valid rId")
  void testAddMedia_returnsValidRId() throws Exception {
    String rId = relationshipManager.addMediaRelationship(1,
        XMLConstants.RELATIONSHIP_TYPE_IMAGE, "../media/image1.png");

    assertNotNull(rId, "Should return non-null rId");
    assertTrue(rId.startsWith("rId"), "Should start with rId prefix");
  }

  @Test
  @DisplayName("addMediaRelationship writes to .rels file")
  void testAddMedia_writesToRelsFile() throws Exception {
    relationshipManager.addMediaRelationship(1,
        XMLConstants.RELATIONSHIP_TYPE_IMAGE, "../media/image2.png");

    assertTrue(pptxDoc.hasPart("ppt/slides/_rels/slide1.xml.rels"), "Rels part should be in PPTXDocument");
    String content = xmlPartToString(pptxDoc.getXmlPart("ppt/slides/_rels/slide1.xml.rels"));
    assertTrue(content.contains("image2.png"), "Rels part should reference media target");
  }

  @Test
  @DisplayName("addMediaRelationship throws for invalid slide number")
  void testAddMedia_throwsForInvalidSlideNumber() {
    assertThrows(IllegalArgumentException.class,
        () -> relationshipManager.addMediaRelationship(0,
            XMLConstants.RELATIONSHIP_TYPE_IMAGE, "../media/img.png"),
        "Should reject slide number 0");
  }

  @Test
  @DisplayName("addMediaRelationship throws for null media type")
  void testAddMedia_throwsForNullMediaType() {
    assertThrows(IllegalArgumentException.class,
        () -> relationshipManager.addMediaRelationship(1, null, "../media/img.png"),
        "Should reject null media type");
  }

  @Test
  @DisplayName("addMediaRelationship throws for empty media target")
  void testAddMedia_throwsForEmptyTarget() {
    assertThrows(IllegalArgumentException.class,
        () -> relationshipManager.addMediaRelationship(1,
            XMLConstants.RELATIONSHIP_TYPE_IMAGE, ""),
        "Should reject empty media target");
  }

  // ========== getSlideRelationships ==========

  @Test
  @DisplayName("getSlideRelationships returns all for existing slide")
  void testGetSlideRelationships_returnsExisting() throws Exception {
    Map<String, String> rels = relationshipManager.getSlideRelationships(1);

    assertNotNull(rels, "Should return non-null map");
    assertFalse(rels.isEmpty(), "Slide 1 should have at least the layout relationship");
  }

  @Test
  @DisplayName("getSlideRelationships returns empty for non-existent slide")
  void testGetSlideRelationships_emptyForNonExistent() {
    Map<String, String> rels = relationshipManager.getSlideRelationships(999);
    assertNotNull(rels, "Should return non-null map");
    assertTrue(rels.isEmpty(), "Non-existent slide should have empty relationships");
  }

  // ========== getAllMediaRelationships ==========

  @Test
  @DisplayName("getAllMediaRelationships filters to media types only")
  void testGetAllMedia_filtersToMediaOnly() throws Exception {
    // Add a media relationship
    relationshipManager.addMediaRelationship(1,
        XMLConstants.RELATIONSHIP_TYPE_IMAGE, "../media/test.png");

    Map<String, String> mediaRels = relationshipManager.getAllMediaRelationships();

    // All entries should be media types
    for (String type : mediaRels.values()) {
      assertTrue(
          type.equals(XMLConstants.RELATIONSHIP_TYPE_IMAGE) ||
          type.contains("video") || type.contains("audio"),
          "Should only contain media relationship types, found: " + type);
    }
  }

  @Test
  @DisplayName("getAllMediaRelationships includes added image")
  void testGetAllMedia_includesAddedImage() throws Exception {
    relationshipManager.addMediaRelationship(1,
        XMLConstants.RELATIONSHIP_TYPE_IMAGE, "../media/photo.jpg");

    Map<String, String> mediaRels = relationshipManager.getAllMediaRelationships();

    boolean found = mediaRels.containsKey("../media/photo.jpg");
    assertTrue(found, "Should include the added image relationship");
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
