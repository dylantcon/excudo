package com.excudo.xml.writers;

import com.excudo.core.model.PPTXDocument;
import com.excudo.core.model.PPTXDocumentParser;
import com.excudo.exceptions.XMLParsingException;
import com.excudo.core.utils.XMLConstants;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SlideInsertionPipeline -- orchestrates the 6-stage slide insertion process.
 */
class SlideInsertionPipelineTest {

  @TempDir
  Path tempDir;

  private SlideInsertionPipeline pipeline;
  private RelationshipManager relationshipManager;
  private DocumentBuilder documentBuilder;
  private File extractedPptxDir;

  @BeforeEach
  void setUp() throws Exception {
    SPIDManager.resetInstance();
    documentBuilder = WriterTestFixtures.createDocumentBuilder();

    extractedPptxDir = tempDir.toFile();
    WriterTestFixtures.createMockPptxStructure(extractedPptxDir);
    WriterTestFixtures.createSlideMasterFile(extractedPptxDir);

    PPTXDocument pptxDoc = PPTXDocument.load(extractedPptxDir, null);
    PPTXDocumentParser.ParsedPresentationState parsedState = PPTXDocumentParser.parse(pptxDoc);
    relationshipManager = new RelationshipManager(pptxDoc, parsedState);
    pipeline = new SlideInsertionPipeline(documentBuilder, relationshipManager);
    pipeline.setPPTXDocument(pptxDoc);
  }

  @AfterEach
  void tearDown() {
    SPIDManager.resetInstance();
  }

  // ========== getExistingSlideNumbers ==========

  @Test
  @DisplayName("Returns empty list for empty slides directory")
  void testGetExistingSlides_emptyDir() {
    List<Integer> slides = pipeline.getExistingSlideNumbers();
    assertTrue(slides.isEmpty(), "Should return empty list when no slides exist");
  }

  @Test
  @Disabled("Disk-based slide discovery replaced by PPTXDocument.getSlideNumbers() -- test requires in-memory rewrite")
  @DisplayName("Returns correct slide numbers")
  void testGetExistingSlides_correctNumbers() throws Exception {
    WriterTestFixtures.createSlideFile(extractedPptxDir, 1);
    WriterTestFixtures.createSlideFile(extractedPptxDir, 3);
    WriterTestFixtures.createSlideFile(extractedPptxDir, 5);

    List<Integer> slides = pipeline.getExistingSlideNumbers();
    assertEquals(3, slides.size(), "Should find 3 slides");
    assertTrue(slides.contains(1));
    assertTrue(slides.contains(3));
    assertTrue(slides.contains(5));
  }

  @Test
  @Disabled("Disk-based slide discovery replaced by PPTXDocument.getSlideNumbers() -- test requires in-memory rewrite")
  @DisplayName("Ignores non-slide files")
  void testGetExistingSlides_ignoresNonSlideFiles() throws Exception {
    WriterTestFixtures.createSlideFile(extractedPptxDir, 1);

    // Create non-slide file in slides directory
    File slidesDir = new File(extractedPptxDir, "ppt/slides");
    Files.writeString(new File(slidesDir, "notaslide.xml").toPath(), "<xml/>");
    Files.writeString(new File(slidesDir, "slide.xml").toPath(), "<xml/>");

    List<Integer> slides = pipeline.getExistingSlideNumbers();
    assertEquals(1, slides.size(), "Should only find slide1.xml");
    assertTrue(slides.contains(1));
  }

  // ========== renameSubsequentSlides ==========

  @Test
  @Disabled("Disk-based slide renaming replaced by PPTXDocument.rekeySlides() -- test requires in-memory rewrite")
  @DisplayName("Renames slides in reverse order to avoid collisions")
  void testRename_reversesOrder() throws Exception {
    WriterTestFixtures.createSlideFile(extractedPptxDir, 1);
    WriterTestFixtures.createSlideFile(extractedPptxDir, 2);
    WriterTestFixtures.createSlideFile(extractedPptxDir, 3);

    pipeline.renameSubsequentSlides(2);

    File slidesDir = new File(extractedPptxDir, "ppt/slides");
    assertTrue(new File(slidesDir, "slide1.xml").exists(), "Slide 1 should remain");
    assertFalse(new File(slidesDir, "slide2.xml").exists(), "Slide 2 should be renamed");
    assertTrue(new File(slidesDir, "slide3.xml").exists(), "Slide 3 should now be at position 3 (was renamed from 2)");
    assertTrue(new File(slidesDir, "slide4.xml").exists(), "Slide 4 should exist (renamed from 3)");
  }

  @Test
  @DisplayName("No-op when no slides >= position")
  void testRename_noOpWhenNoSlidesAtPosition() throws Exception {
    WriterTestFixtures.createSlideFile(extractedPptxDir, 1);

    pipeline.renameSubsequentSlides(5);

    File slidesDir = new File(extractedPptxDir, "ppt/slides");
    assertTrue(new File(slidesDir, "slide1.xml").exists(), "Slide 1 should remain unchanged");
  }

  @Test
  @Disabled("Disk-based rels renaming replaced by PPTXDocument in-memory rels management -- test requires in-memory rewrite")
  @DisplayName("Also renames .rels files")
  void testRename_alsoRenamesRels() throws Exception {
    WriterTestFixtures.createSlideFile(extractedPptxDir, 1);
    WriterTestFixtures.createSlideRelsFile(extractedPptxDir, 1);

    pipeline.renameSubsequentSlides(1);

    File relsDir = new File(extractedPptxDir, "ppt/slides/_rels");
    assertFalse(new File(relsDir, "slide1.xml.rels").exists(), "Original rels should be renamed");
    assertTrue(new File(relsDir, "slide2.xml.rels").exists(), "Rels should move to slide 2 position");
  }

  // ========== writeDocument (disk-backed -- removed in in-memory migration) ==========

  @Test
  @Disabled("writeDocument() was removed -- disk-backed slide writing replaced by PPTXDocument.putSlideDocument()")
  @DisplayName("Creates file from Document")
  void testWriteDocument_createsFile() throws Exception {
    // writeDocument() no longer exists -- slides are stored via PPTXDocument.putSlideDocument()
  }

  @Test
  @Disabled("writeDocument() was removed -- disk-backed slide writing replaced by PPTXDocument.putSlideDocument()")
  @DisplayName("Creates parent directories")
  void testWriteDocument_createsParentDirs() throws Exception {
    // writeDocument() no longer exists -- slides are stored via PPTXDocument.putSlideDocument()
  }

  // ========== calculateNextSlideId (pkg-private) ==========

  @Test
  @DisplayName("Returns DEFAULT_SLIDE_ID_START for empty presentation")
  void testCalculateNextSlideId_defaultForEmpty() {
    String content = "<p:sldIdLst></p:sldIdLst>";
    int nextId = pipeline.calculateNextSlideId(content);
    assertEquals(XMLConstants.DEFAULT_SLIDE_ID_START, nextId,
        "Should return DEFAULT_SLIDE_ID_START for empty list");
  }

  @Test
  @DisplayName("Returns max+1 for existing slides")
  void testCalculateNextSlideId_maxPlusOne() {
    String content = """
        <p:sldIdLst>
          <p:sldId id="256" r:id="rId2"/>
          <p:sldId id="260" r:id="rId3"/>
          <p:sldId id="258" r:id="rId4"/>
        </p:sldIdLst>
        """;
    int nextId = pipeline.calculateNextSlideId(content);
    assertEquals(261, nextId, "Should return max(260)+1 = 261");
  }

  // ========== insertSlideIdElement (pkg-private) ==========

  @Test
  @DisplayName("Inserts at beginning when slide number < all existing")
  void testInsertSlideId_atBeginning() {
    String content = """
        <p:sldIdLst>
        <p:sldId id="256" r:id="rId2"/>
        </p:sldIdLst>""";

    String newElement = "<p:sldId id=\"257\" r:id=\"rId10\"/>";

    // slideNumber 0 (mapped from rId10-1=9, but we pass the desired position)
    // With rId2 -> slideNumber = 2-1 = 1, so inserting at position 0 goes before
    // Actually, the method uses slideNumber parameter directly
    String result = pipeline.insertSlideIdElement(content, newElement, 0);
    int newPos = result.indexOf("rId10");
    int existingPos = result.indexOf("rId2");
    assertTrue(newPos < existingPos, "New element should be before existing");
  }

  @Test
  @DisplayName("Appends at end when slide number > all existing")
  void testInsertSlideId_atEnd() {
    String content = """
        <p:sldIdLst>
        <p:sldId id="256" r:id="rId2"/>
        </p:sldIdLst>""";

    String newElement = "<p:sldId id=\"257\" r:id=\"rId10\"/>";
    String result = pipeline.insertSlideIdElement(content, newElement, 999);

    int newPos = result.indexOf("rId10");
    int existingPos = result.indexOf("rId2");
    assertTrue(newPos > existingPos, "New element should be after existing");
  }

  // ========== parseExistingSlideIds (pkg-private) ==========

  @Test
  @DisplayName("Extracts slide info from list content")
  void testParseExistingSlideIds_extractsInfo() {
    String listContent = """
        <p:sldId id="256" r:id="rId2"/>
        <p:sldId id="257" r:id="rId3"/>
        """;

    List<SlideInsertionPipeline.SlideIdInfo> slides = pipeline.parseExistingSlideIds(listContent);
    assertEquals(2, slides.size(), "Should find 2 slide entries");
    assertEquals(256, slides.get(0).slideId);
    assertEquals("rId2", slides.get(0).rId);
    assertEquals(257, slides.get(1).slideId);
    assertEquals("rId3", slides.get(1).rId);
  }

  @Test
  @DisplayName("Returns empty for empty content")
  void testParseExistingSlideIds_emptyForEmpty() {
    List<SlideInsertionPipeline.SlideIdInfo> slides = pipeline.parseExistingSlideIds("");
    assertTrue(slides.isEmpty(), "Should return empty list for empty content");
  }

  // ========== executeInsertion (full pipeline) ==========

  @Test
  @Disabled("Disk file assertions replaced by PPTXDocument in-memory state -- test requires in-memory rewrite")
  @DisplayName("Full pipeline creates file, updates presentation.xml, updates content types")
  void testExecuteInsertion_fullPipeline() throws Exception {
    // Setup: existing slide 1 with all supporting files
    WriterTestFixtures.createSlideFile(extractedPptxDir, 1);
    WriterTestFixtures.createSlideRelsFile(extractedPptxDir, 1);
    WriterTestFixtures.createPresentationXml(extractedPptxDir, 1);
    WriterTestFixtures.createPresentationRelsXml(extractedPptxDir, 1);
    WriterTestFixtures.createContentTypesXml(extractedPptxDir, 1);
    WriterTestFixtures.createSlideLayoutFile(extractedPptxDir, "slideLayout1");

    // Re-create manager/pipeline so they see the slides
    SPIDManager.resetInstance();
    PPTXDocument pptxDoc1 = PPTXDocument.load(extractedPptxDir, null);
    PPTXDocumentParser.ParsedPresentationState state1 = PPTXDocumentParser.parse(pptxDoc1);
    relationshipManager = new RelationshipManager(pptxDoc1, state1);
    pipeline = new SlideInsertionPipeline(documentBuilder, relationshipManager);
    pipeline.setPPTXDocument(pptxDoc1);

    int slideNum = pipeline.executeInsertion(2, "slideLayout1",
        (num) -> {
          try {
            return WriterTestFixtures.createMinimalSlideDocument(documentBuilder);
          } catch (Exception e) {
            throw new XMLParsingException("Failed to create test document", e);
          }
        });

    assertEquals(2, slideNum, "Should create slide at position 2");

    // Verify slide file created
    File slideFile = new File(extractedPptxDir, "ppt/slides/slide2.xml");
    assertTrue(slideFile.exists(), "Slide file should be created");

    // Verify presentation.xml updated -- should have a new sldId entry
    String presContent = Files.readString(new File(extractedPptxDir, "ppt/presentation.xml").toPath());
    // After insertion, presentation.xml should have 2 sldId entries (compact single-line XML)
    long sldIdCount = java.util.regex.Pattern.compile("<p:sldId ")
        .matcher(presContent).results().count();
    assertTrue(sldIdCount >= 2, "presentation.xml should have at least 2 sldId entries after insertion, found: " + sldIdCount);

    // Verify content types updated
    String ctContent = Files.readString(new File(extractedPptxDir, "[Content_Types].xml").toPath());
    assertTrue(ctContent.contains("slide2.xml"), "Content types should include slide2");
  }

  @Test
  @Disabled("Disk file assertions replaced by PPTXDocument in-memory state -- test requires in-memory rewrite")
  @DisplayName("Full pipeline preserves presentation.xml consistency")
  void testExecuteInsertion_presentationXmlConsistency() throws Exception {
    // Setup: 2 existing slides
    WriterTestFixtures.createSlideFile(extractedPptxDir, 1);
    WriterTestFixtures.createSlideRelsFile(extractedPptxDir, 1);
    WriterTestFixtures.createSlideFile(extractedPptxDir, 2);
    WriterTestFixtures.createSlideRelsFile(extractedPptxDir, 2);
    WriterTestFixtures.createPresentationXml(extractedPptxDir, 2);
    WriterTestFixtures.createPresentationRelsXml(extractedPptxDir, 2);
    WriterTestFixtures.createContentTypesXml(extractedPptxDir, 2);
    WriterTestFixtures.createSlideLayoutFile(extractedPptxDir, "slideLayout1");

    SPIDManager.resetInstance();
    PPTXDocument pptxDoc2 = PPTXDocument.load(extractedPptxDir, null);
    PPTXDocumentParser.ParsedPresentationState state2 = PPTXDocumentParser.parse(pptxDoc2);
    relationshipManager = new RelationshipManager(pptxDoc2, state2);
    pipeline = new SlideInsertionPipeline(documentBuilder, relationshipManager);
    pipeline.setPPTXDocument(pptxDoc2);

    pipeline.executeInsertion(3, "slideLayout1",
        (num) -> {
          try {
            return WriterTestFixtures.createMinimalSlideDocument(documentBuilder);
          } catch (Exception e) {
            throw new XMLParsingException("Failed to create test document", e);
          }
        });

    // Verify presentation.xml has unique slide IDs
    String presContent = Files.readString(new File(extractedPptxDir, "ppt/presentation.xml").toPath());
    Pattern sldIdPattern = Pattern.compile("id=\"(\\d+)\"");
    Matcher matcher = sldIdPattern.matcher(presContent);
    Set<String> slideIds = new HashSet<>();
    while (matcher.find()) {
      String id = matcher.group(1);
      // Skip the slide master ID (very large number)
      if (Long.parseLong(id) < 2000000000L) {
        assertTrue(slideIds.add(id),
            "Duplicate slide ID found in presentation.xml: " + id);
      }
    }

    // Verify content types has an entry for each slide
    String ctContent = Files.readString(new File(extractedPptxDir, "[Content_Types].xml").toPath());
    for (int i = 1; i <= 3; i++) {
      assertTrue(ctContent.contains("slide" + i + ".xml"),
          "[Content_Types].xml should reference slide" + i + ".xml");
    }
  }

  @Test
  @DisplayName("Clamps position when exceeding slide count")
  void testExecuteInsertion_clampsPosition() throws Exception {
    WriterTestFixtures.createPresentationXml(extractedPptxDir, 0);
    WriterTestFixtures.createPresentationRelsXml(extractedPptxDir, 0);
    WriterTestFixtures.createContentTypesXml(extractedPptxDir, 0);
    WriterTestFixtures.createSlideLayoutFile(extractedPptxDir, "slideLayout1");

    // Re-create pipeline
    SPIDManager.resetInstance();
    PPTXDocument pptxDoc3 = PPTXDocument.load(extractedPptxDir, null);
    PPTXDocumentParser.ParsedPresentationState state3 = PPTXDocumentParser.parse(pptxDoc3);
    relationshipManager = new RelationshipManager(pptxDoc3, state3);
    pipeline = new SlideInsertionPipeline(documentBuilder, relationshipManager);
    pipeline.setPPTXDocument(pptxDoc3);

    // Insert at position 100 when there are 0 slides -- should clamp to 1
    int slideNum = pipeline.executeInsertion(100, null,
        (num) -> {
          try {
            return WriterTestFixtures.createMinimalSlideDocument(documentBuilder);
          } catch (Exception e) {
            throw new XMLParsingException("Failed to create test document", e);
          }
        });

    assertEquals(1, slideNum, "Should clamp to position 1 when no slides exist");
  }

  @Test
  @DisplayName("insertSlideIdElement handles self-closing sldIdLst (all slides deleted)")
  void testInsertSlideId_selfClosingSldIdLst() {
    String content = "<p:presentation><p:sldIdLst/><p:sldSz/></p:presentation>";
    String newElement = "<p:sldId id=\"256\" r:id=\"rId2\"/>";

    String result = pipeline.insertSlideIdElement(content, newElement, 1);

    assertTrue(result.contains("<p:sldIdLst><p:sldId id=\"256\" r:id=\"rId2\"/></p:sldIdLst>"),
        "Should expand self-closing to contain new sldId element");
    assertFalse(result.contains("<p:sldIdLst/>"),
        "Self-closing form should be replaced");
  }

  @Test
  @DisplayName("insertSlideIdElement handles empty sldIdLst (open+close tags)")
  void testInsertSlideId_emptySldIdLst() {
    String content = "<p:presentation><p:sldIdLst></p:sldIdLst><p:sldSz/></p:presentation>";
    String newElement = "<p:sldId id=\"256\" r:id=\"rId2\"/>";

    String result = pipeline.insertSlideIdElement(content, newElement, 1);

    assertTrue(result.contains("<p:sldIdLst><p:sldId id=\"256\" r:id=\"rId2\"/></p:sldIdLst>"),
        "Should insert element into empty list");
  }
}
