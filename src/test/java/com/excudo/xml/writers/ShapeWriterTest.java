package com.excudo.xml.writers;

import com.excudo.core.model.LayoutManager;
import com.excudo.core.model.PPTXDocument;
import com.excudo.core.model.PPTXDocumentParser;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.SlideShape;
import com.excudo.exceptions.XMLParsingException;
import com.excudo.core.utils.XMLConstants;
import com.excudo.xml.shapes.ShapeFactoryRegistry;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ShapeWriter -- shape CRUD operations on slide DOM structures.
 */
class ShapeWriterTest {

  @TempDir
  Path tempDir;

  private ShapeWriter shapeWriter;
  private Document slideDocument;
  private DocumentBuilder documentBuilder;
  private XPath xpath;
  private File extractedPptxDir;
  private SPIDManager spidManager;

  @BeforeEach
  void setUp() throws Exception {
    SPIDManager.resetInstance();

    documentBuilder = WriterTestFixtures.createDocumentBuilder();
    xpath = WriterTestFixtures.createConfiguredXPath();

    extractedPptxDir = tempDir.toFile();
    WriterTestFixtures.createMockPptxStructure(extractedPptxDir);
    WriterTestFixtures.createSlideFile(extractedPptxDir, 1);

    PPTXDocument pptxDoc = PPTXDocument.load(extractedPptxDir, null);
    PPTXDocumentParser.ParsedPresentationState parsedState = PPTXDocumentParser.parse(pptxDoc);
    LayoutManager layoutManager = new LayoutManager(parsedState.getLayouts());
    spidManager = SPIDManager.createFromParsedState(parsedState, layoutManager);
    slideDocument = WriterTestFixtures.createMinimalSlideDocument(documentBuilder);

    Element spTree = (Element) xpath.evaluate("//p:spTree", slideDocument, XPathConstants.NODE);
    ShapeFactoryRegistry registry = new ShapeFactoryRegistry();

    shapeWriter = new ShapeWriter(slideDocument, xpath, spTree, spidManager, registry);
  }

  @AfterEach
  void tearDown() {
    SPIDManager.resetInstance();
  }

  // ========== injectBasicShapeWithSlideContext ==========

  @Test
  @DisplayName("Inject shape adds element to shape tree")
  void testInjectShape_addsToTree() throws Exception {
    ShapeGeometry geometry = new ShapeGeometry(100000, 200000, 300000, 400000);
    int spid = shapeWriter.injectBasicShapeWithSlideContext(
        SlideShape.ShapeType.RECTANGLE, geometry, "Test", "TestRect", 1);

    assertTrue(spid > 0, "SPID should be positive");

    Node shape = findShapeBySpid(spid);
    assertNotNull(shape, "Injected shape should be findable in document");
  }

  @Test
  @DisplayName("Inject shape allocates valid SPID")
  void testInjectShape_validSpid() throws Exception {
    ShapeGeometry geometry = new ShapeGeometry(0, 0, 914400, 914400);
    int spid = shapeWriter.injectBasicShapeWithSlideContext(
        SlideShape.ShapeType.RECTANGLE, geometry, "Text", "Shape1", 1);

    assertTrue(spid > 0, "Should return positive SPID");
  }

  @Test
  @DisplayName("Inject shape sets correct geometry")
  void testInjectShape_correctGeometry() throws Exception {
    ShapeGeometry geometry = new ShapeGeometry(1000000, 2000000, 3000000, 4000000);
    int spid = shapeWriter.injectBasicShapeWithSlideContext(
        SlideShape.ShapeType.RECTANGLE, geometry, "Geo", "GeoShape", 1);

    Node shape = findShapeBySpid(spid);
    assertNotNull(shape);

    String x = xpath.evaluate(".//a:off/@x", shape);
    String y = xpath.evaluate(".//a:off/@y", shape);
    String cx = xpath.evaluate(".//a:ext/@cx", shape);
    String cy = xpath.evaluate(".//a:ext/@cy", shape);

    assertEquals("1000000", x);
    assertEquals("2000000", y);
    assertEquals("3000000", cx);
    assertEquals("4000000", cy);
  }

  @Test
  @DisplayName("Inject shape sets correct name")
  void testInjectShape_correctName() throws Exception {
    ShapeGeometry geometry = new ShapeGeometry(0, 0, 914400, 914400);
    int spid = shapeWriter.injectBasicShapeWithSlideContext(
        SlideShape.ShapeType.RECTANGLE, geometry, "Content", "MyNamedShape", 1);

    Node shape = findShapeBySpid(spid);
    assertNotNull(shape);
    String name = xpath.evaluate(".//p:cNvPr/@name", shape);
    assertEquals("MyNamedShape", name);
  }

  @Test
  @DisplayName("Inject shape without SPIDManager uses calculated fallback")
  void testInjectShape_withoutSPIDManager() throws Exception {
    // Create ShapeWriter without SPIDManager
    Element spTree = (Element) xpath.evaluate("//p:spTree", slideDocument, XPathConstants.NODE);
    ShapeFactoryRegistry registry = new ShapeFactoryRegistry();
    ShapeWriter writerNoManager = new ShapeWriter(slideDocument, xpath, spTree, null, registry);

    ShapeGeometry geometry = new ShapeGeometry(0, 0, 914400, 914400);
    int spid = writerNoManager.injectBasicShapeWithSlideContext(
        SlideShape.ShapeType.RECTANGLE, geometry, "Fallback", "FallbackShape", 1);

    assertTrue(spid > 0, "Fallback SPID should be positive");
  }

  @Test
  @DisplayName("Multiple shape injections produce unique SPIDs")
  void testInjectShape_uniqueSpids() throws Exception {
    ShapeGeometry g = new ShapeGeometry(0, 0, 914400, 914400);

    int spid1 = shapeWriter.injectBasicShapeWithSlideContext(
        SlideShape.ShapeType.RECTANGLE, g, "A", "Shape1", 1);
    int spid2 = shapeWriter.injectBasicShapeWithSlideContext(
        SlideShape.ShapeType.RECTANGLE, g, "B", "Shape2", 1);

    assertNotEquals(spid1, spid2, "Each shape should get a unique SPID");

    // Semantic SPID uniqueness check across entire document
    WriterTestFixtures.assertUniqueShapeSpids(slideDocument);
  }

  // ========== updateShapeText ==========

  @Test
  @DisplayName("updateShapeText updates existing text")
  void testUpdateShapeText_updatesExisting() throws Exception {
    Document docWithShape = WriterTestFixtures.createSlideWithShapes(documentBuilder, 42);
    Element spTree = (Element) xpath.evaluate("//p:spTree", docWithShape, XPathConstants.NODE);
    ShapeWriter writer = new ShapeWriter(docWithShape, xpath, spTree, null, new ShapeFactoryRegistry());

    writer.updateShapeText(42, "Updated Content");

    String text = xpath.evaluate("//p:sp[.//p:cNvPr[@id='42']]//a:t", docWithShape);
    assertEquals("Updated Content", text);
  }

  @Test
  @DisplayName("updateShapeText throws for missing SPID")
  void testUpdateShapeText_throwsForMissingSpid() throws Exception {
    assertThrows(XMLParsingException.class,
        () -> shapeWriter.updateShapeText(9999, "Text"),
        "Should throw for non-existent SPID");
  }

  // ========== updateShapeGeometry ==========

  @Test
  @DisplayName("updateShapeGeometry updates offset and extent")
  void testUpdateShapeGeometry_updatesValues() throws Exception {
    Document docWithShape = WriterTestFixtures.createSlideWithShapes(documentBuilder, 55);
    Element spTree = (Element) xpath.evaluate("//p:spTree", docWithShape, XPathConstants.NODE);
    ShapeWriter writer = new ShapeWriter(docWithShape, xpath, spTree, null, new ShapeFactoryRegistry());

    ShapeGeometry newGeo = new ShapeGeometry(500000, 600000, 700000, 800000);
    writer.updateShapeGeometry(55, newGeo);

    Node shape = (Node) xpath.evaluate("//p:sp[.//p:cNvPr[@id='55']]", docWithShape, XPathConstants.NODE);
    assertEquals("500000", xpath.evaluate(".//a:off/@x", shape));
    assertEquals("600000", xpath.evaluate(".//a:off/@y", shape));
    assertEquals("700000", xpath.evaluate(".//a:ext/@cx", shape));
    assertEquals("800000", xpath.evaluate(".//a:ext/@cy", shape));
  }

  @Test
  @DisplayName("updateShapeGeometry throws for missing SPID")
  void testUpdateShapeGeometry_throwsForMissingSpid() {
    assertThrows(XMLParsingException.class,
        () -> shapeWriter.updateShapeGeometry(9999, new ShapeGeometry(0, 0, 100, 100)),
        "Should throw for non-existent SPID");
  }

  // ========== removeShapeBySpid ==========

  @Test
  @DisplayName("removeShapeBySpid removes shape and returns success result")
  void testRemoveShape_removesAndReturns() throws Exception {
    Document docWithShape = WriterTestFixtures.createSlideWithShapes(documentBuilder, 77);
    Element spTree = (Element) xpath.evaluate("//p:spTree", docWithShape, XPathConstants.NODE);
    ShapeWriter writer = new ShapeWriter(docWithShape, xpath, spTree, null, new ShapeFactoryRegistry());

    SlideXMLWriter.ShapeRemovalResult result = writer.removeShapeBySpid(77);

    assertTrue(result.isSuccess(), "Removal should succeed");
    assertFalse(result.isPicture(), "Regular shape is not a picture");
    assertEquals(77, result.getSpid());

    Node removed = (Node) xpath.evaluate("//p:sp[.//p:cNvPr[@id='77']]", docWithShape, XPathConstants.NODE);
    assertNull(removed, "Shape should no longer exist in document");
  }

  @Test
  @DisplayName("removeShapeBySpid throws for missing SPID")
  void testRemoveShape_throwsForMissingSpid() {
    assertThrows(XMLParsingException.class,
        () -> shapeWriter.removeShapeBySpid(9999),
        "Should throw for non-existent SPID");
  }

  // ========== addPictureShape ==========

  @Test
  @DisplayName("addPictureShape creates correct OOXML pic structure")
  void testAddPictureShape_correctStructure() throws Exception {
    ShapeGeometry geometry = new ShapeGeometry(0, 0, 914400, 914400);
    shapeWriter.addPictureShape(100, "TestPicture", "rId5", geometry);

    NodeList pics = (NodeList) xpath.evaluate("//p:pic", slideDocument, XPathConstants.NODESET);
    assertEquals(1, pics.getLength(), "Should have one picture element");

    Node pic = pics.item(0);
    String picSpid = xpath.evaluate(".//p:cNvPr/@id", pic);
    assertEquals("100", picSpid);
    String picName = xpath.evaluate(".//p:cNvPr/@name", pic);
    assertEquals("TestPicture", picName);
  }

  @Test
  @DisplayName("addPictureShape sets blip relationship ID correctly")
  void testAddPictureShape_blipRId() throws Exception {
    ShapeGeometry geometry = new ShapeGeometry(0, 0, 914400, 914400);
    shapeWriter.addPictureShape(101, "Pic", "rId7", geometry);

    String embed = xpath.evaluate("//p:pic//a:blip/@r:embed", slideDocument);
    assertEquals("rId7", embed, "Blip embed should reference the relationship ID");
  }

  @Test
  @DisplayName("Bulk shape injection maintains SPID uniqueness across all shapes")
  void testBulkInject_spidUniqueness() throws Exception {
    ShapeGeometry g = new ShapeGeometry(0, 0, 914400, 914400);
    for (int i = 0; i < 5; i++) {
      shapeWriter.injectBasicShapeWithSlideContext(
          SlideShape.ShapeType.RECTANGLE, g, "Text " + i, "Shape" + i, 1);
    }
    WriterTestFixtures.assertUniqueShapeSpids(slideDocument);
  }

  // ========== HELPERS ==========

  private Node findShapeBySpid(int spid) throws Exception {
    String expr = String.format(
        XMLConstants.XPATH_SHAPE_OR_PICTURE_BY_SPID_TEMPLATE, spid, spid);
    return (Node) xpath.evaluate(expr, slideDocument, XPathConstants.NODE);
  }
}
