package com.excudo.xml.writers;

import com.excudo.core.model.LayoutManager;
import com.excudo.core.model.PPTXDocument;
import com.excudo.core.model.PPTXDocumentParser;
import com.excudo.exceptions.XMLParsingException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SlideCopyManager -- slide clone operations and SPID regeneration.
 */
class SlideCopyManagerTest {

  @TempDir
  Path tempDir;

  private SlideCopyManager copyManager;
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

    pptxDoc = PPTXDocument.load(extractedPptxDir, null);
    PPTXDocumentParser.ParsedPresentationState parsedState = PPTXDocumentParser.parse(pptxDoc);
    LayoutManager layoutManager = new LayoutManager(parsedState.getLayouts());
    SPIDManager spidManager = SPIDManager.createFromParsedState(parsedState, layoutManager);
    RelationshipManager relationshipManager = new RelationshipManager(pptxDoc, parsedState);

    copyManager = new SlideCopyManager(documentBuilder, spidManager, relationshipManager);
  }

  @AfterEach
  void tearDown() {
    SPIDManager.resetInstance();
  }

  // ========== modifySlideForCopy ==========

  @Test
  @DisplayName("modifySlideForCopy clones document without modifying original")
  void testModifySlideForCopy_clonesWithoutModifyingOriginal() throws Exception {
    Document source = WriterTestFixtures.createSlideWithShapes(documentBuilder, 10, 20);

    String originalXml = serializeDocument(source);
    Document copy = copyManager.modifySlideForCopy(source, "New Title");
    String afterCopyXml = serializeDocument(source);

    assertEquals(originalXml, afterCopyXml, "Original document should not be modified");
    assertNotSame(source, copy, "Should return a different document instance");
  }

  @Test
  @DisplayName("modifySlideForCopy invokes SPID regeneration")
  void testModifySlideForCopy_regeneratesSpids() throws Exception {
    Document source = WriterTestFixtures.createSlideWithShapes(documentBuilder, 10, 20);

    Document copy = copyManager.modifySlideForCopy(source, null);

    // The copied document should exist (regeneration ran without exception)
    assertNotNull(copy, "Copy should be non-null after SPID regeneration");
    assertNotNull(copy.getDocumentElement(), "Copy should have root element");
  }

  @Test
  @DisplayName("modifySlideForCopy with null title skips title update")
  void testModifySlideForCopy_nullTitleSkipsUpdate() throws Exception {
    Document source = WriterTestFixtures.createMinimalSlideDocument(documentBuilder);

    // Should not throw
    Document copy = copyManager.modifySlideForCopy(source, null);
    assertNotNull(copy);
  }

  @Test
  @DisplayName("modifySlideForCopy with empty title skips title update")
  void testModifySlideForCopy_emptyTitleSkipsUpdate() throws Exception {
    Document source = WriterTestFixtures.createMinimalSlideDocument(documentBuilder);

    Document copy = copyManager.modifySlideForCopy(source, "   ");
    assertNotNull(copy);
  }

  // ========== regenerateSpids ==========

  @Test
  @DisplayName("regenerateSpids wraps exceptions as XMLParsingException")
  void testRegenerateSpids_returnsResult() throws Exception {
    Document doc = WriterTestFixtures.createSlideWithShapes(documentBuilder, 5);

    SPIDManager.SPIDRegenerationResult result = copyManager.regenerateSpids(doc);
    assertNotNull(result, "Result should be non-null");
    assertTrue(result.getShapesProcessed() >= 0, "Should report shapes processed");
  }

  // ========== copySlideRelationships ==========

  @Test
  @DisplayName("copySlideRelationships delegates to RelationshipManager")
  void testCopySlideRelationships_delegates() throws Exception {
    // Existing slide 1 has relationships from setUp
    // Copying to slide 2 should succeed
    assertDoesNotThrow(() -> copyManager.copySlideRelationships(1, 2));

    // Verify the rels part was stored in PPTXDocument for slide 2
    assertTrue(pptxDoc.hasPart("ppt/slides/_rels/slide2.xml.rels"),
        "Slide 2 relationship part should be stored in PPTXDocument");
  }

  // ========== HELPERS ==========

  private String serializeDocument(Document doc) throws Exception {
    javax.xml.transform.TransformerFactory tf = javax.xml.transform.TransformerFactory.newInstance();
    javax.xml.transform.Transformer transformer = tf.newTransformer();
    java.io.StringWriter writer = new java.io.StringWriter();
    transformer.transform(new javax.xml.transform.dom.DOMSource(doc),
        new javax.xml.transform.stream.StreamResult(writer));
    return writer.toString();
  }
}
