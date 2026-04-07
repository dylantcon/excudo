package com.excudo.xml.writers;

import com.excudo.core.model.LayoutInfo;
import com.excudo.core.model.LayoutManager;
import com.excudo.core.model.PPTXDocument;
import com.excudo.core.model.PPTXDocumentParser;
import com.excudo.exceptions.XMLParsingException;
import com.excudo.core.utils.XMLConstants;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import java.io.File;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SlideDocumentBuilder -- blank and layout slide DOM creation.
 */
class SlideDocumentBuilderTest {

  @TempDir
  Path tempDir;

  private SlideDocumentBuilder builder;
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
    WriterTestFixtures.createSlideMasterFile(extractedPptxDir);

    PPTXDocument pptxDoc = PPTXDocument.load(extractedPptxDir, null);
    PPTXDocumentParser.ParsedPresentationState parsedState = PPTXDocumentParser.parse(pptxDoc);
    LayoutManager initialLm = new LayoutManager(parsedState.getLayouts());
    SPIDManager spidManager = SPIDManager.createFromParsedState(parsedState, initialLm);
    builder = new SlideDocumentBuilder(documentBuilder, spidManager);
  }

  @AfterEach
  void tearDown() {
    SPIDManager.resetInstance();
  }

  // ========== createBlankSlideDocument ==========

  @Test
  @DisplayName("Blank slide has p:sld root element")
  void testBlankSlide_hasSldRoot() throws XMLParsingException {
    Document doc = builder.createBlankSlideDocument("Test", 2, false);

    Element root = doc.getDocumentElement();
    assertEquals("p:sld", root.getTagName(), "Root should be p:sld");
  }

  @Test
  @DisplayName("Blank slide has correct namespaces")
  void testBlankSlide_hasNamespaces() throws XMLParsingException {
    Document doc = builder.createBlankSlideDocument("Test", 2, false);

    Element root = doc.getDocumentElement();
    String aNs = root.getAttributeNS("http://www.w3.org/2000/xmlns/", "a");
    String rNs = root.getAttributeNS("http://www.w3.org/2000/xmlns/", "r");
    String pNs = root.getAttributeNS("http://www.w3.org/2000/xmlns/", "p");

    assertEquals(XMLConstants.DRAWING_NS, aNs, "Should declare drawing namespace");
    assertEquals(XMLConstants.RELATIONSHIPS_NS, rNs, "Should declare relationships namespace");
    assertEquals(XMLConstants.PRESENTATION_NS, pNs, "Should declare presentation namespace");

    // Semantic namespace validation
    WriterTestFixtures.assertRequiredNamespaces(doc);
  }

  @Test
  @DisplayName("Blank slide has spTree with group shape props")
  void testBlankSlide_hasSpTree() throws Exception {
    Document doc = builder.createBlankSlideDocument("Test", 2, false);

    Node spTree = (Node) xpath.evaluate("//p:spTree", doc, XPathConstants.NODE);
    assertNotNull(spTree, "Should have p:spTree");

    Node nvGrpSpPr = (Node) xpath.evaluate("//p:spTree/p:nvGrpSpPr", doc, XPathConstants.NODE);
    assertNotNull(nvGrpSpPr, "Should have group shape non-visual properties");

    Node grpSpPr = (Node) xpath.evaluate("//p:spTree/p:grpSpPr", doc, XPathConstants.NODE);
    assertNotNull(grpSpPr, "Should have group shape properties");
  }

  @Test
  @DisplayName("Blank slide has clrMapOvr element")
  void testBlankSlide_hasClrMapOvr() throws Exception {
    Document doc = builder.createBlankSlideDocument("Test", 2, false);

    Node clrMapOvr = (Node) xpath.evaluate("//p:clrMapOvr", doc, XPathConstants.NODE);
    assertNotNull(clrMapOvr, "Should have clrMapOvr");

    Node masterClrMapping = (Node) xpath.evaluate("//p:clrMapOvr/a:masterClrMapping", doc, XPathConstants.NODE);
    assertNotNull(masterClrMapping, "Should have masterClrMapping");
  }

  @Test
  @DisplayName("Blank slide group shape has transform elements")
  void testBlankSlide_groupShapeTransforms() throws Exception {
    Document doc = builder.createBlankSlideDocument("Test", 2, false);

    Node xfrm = (Node) xpath.evaluate("//p:grpSpPr/a:xfrm", doc, XPathConstants.NODE);
    assertNotNull(xfrm, "Group shape should have transform");

    assertNotNull(xpath.evaluate("//p:grpSpPr/a:xfrm/a:off", doc, XPathConstants.NODE),
        "Should have offset element");
    assertNotNull(xpath.evaluate("//p:grpSpPr/a:xfrm/a:ext", doc, XPathConstants.NODE),
        "Should have extent element");
  }

  // ========== createSlideWithLayoutDocument ==========

  @Test
  @DisplayName("Layout slide falls back when layout not found")
  void testLayoutSlide_fallbackWhenNotFound() throws Exception {
    // Pass null LayoutInfo; should fall back to default title shape
    Document doc = builder.createSlideWithLayoutDocument("Fallback", 2, (LayoutInfo) null);

    // Should still produce a valid slide
    Element root = doc.getDocumentElement();
    assertNotNull(root);
    assertEquals("p:sld", root.getTagName());

    Node spTree = (Node) xpath.evaluate("//p:spTree", doc, XPathConstants.NODE);
    assertNotNull(spTree, "Should still have shape tree");
  }

  @Test
  @DisplayName("Layout slide has correct root structure")
  void testLayoutSlide_rootStructure() throws Exception {
    PPTXDocument layoutDoc1 = PPTXDocument.load(extractedPptxDir, null);
    PPTXDocumentParser.ParsedPresentationState layoutState1 = PPTXDocumentParser.parse(layoutDoc1);
    LayoutManager lm = new LayoutManager(layoutState1.getLayouts());
    LayoutInfo layoutInfo = lm.getLayoutInfo("slideLayout1");
    Document doc = builder.createSlideWithLayoutDocument("Layout Test", 2, layoutInfo);

    Element root = doc.getDocumentElement();
    assertEquals("p:sld", root.getTagName());

    WriterTestFixtures.assertRequiredNamespaces(doc);
  }

  // ========== createTitleShape (pkg-private) ==========

  @Test
  @DisplayName("Title shape has title placeholder type")
  void testTitleShape_hasPlaceholderType() throws Exception {
    Document doc = documentBuilder.newDocument();
    Element root = doc.createElementNS(XMLConstants.PRESENTATION_NS, "p:sld");
    root.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:a", XMLConstants.DRAWING_NS);
    doc.appendChild(root);

    Element titleShape = builder.createTitleShape(doc, "My Title", 2, "title");

    String phType = xpath.evaluate(".//p:ph/@type", titleShape);
    assertEquals("title", phType, "Should have title placeholder type");
  }

  @Test
  @DisplayName("Title shape SPID is allocated by SPIDManager")
  void testTitleShape_spidAllocated() throws Exception {
    Document doc = documentBuilder.newDocument();
    Element root = doc.createElementNS(XMLConstants.PRESENTATION_NS, "p:sld");
    root.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:a", XMLConstants.DRAWING_NS);
    doc.appendChild(root);

    Element titleShape = builder.createTitleShape(doc, "Title", 2, "title");

    String spid = xpath.evaluate(".//p:cNvPr/@id", titleShape);
    assertNotNull(spid, "Should have SPID attribute");
    int spidVal = Integer.parseInt(spid);
    assertTrue(spidVal > 0, "SPID should be positive");
  }

  // ========== addTransformElement (pkg-private) ==========

  @Test
  @DisplayName("addTransformElement creates offset with x/y for non-ext names")
  void testAddTransformElement_offsetAttributes() throws Exception {
    Document doc = documentBuilder.newDocument();
    Element parent = doc.createElementNS(XMLConstants.DRAWING_NS, "a:xfrm");
    doc.appendChild(parent);

    builder.addTransformElement(doc, parent, "a:off", "100", "200");

    Node off = parent.getFirstChild();
    assertNotNull(off);
    assertEquals("100", ((Element) off).getAttribute("x"));
    assertEquals("200", ((Element) off).getAttribute("y"));
  }

  @Test
  @DisplayName("addTransformElement creates extent with cx/cy for ext names")
  void testAddTransformElement_extentAttributes() throws Exception {
    Document doc = documentBuilder.newDocument();
    Element parent = doc.createElementNS(XMLConstants.DRAWING_NS, "a:xfrm");
    doc.appendChild(parent);

    builder.addTransformElement(doc, parent, "a:ext", "300", "400");

    Node ext = parent.getFirstChild();
    assertNotNull(ext);
    assertEquals("300", ((Element) ext).getAttribute("cx"));
    assertEquals("400", ((Element) ext).getAttribute("cy"));
  }

  // ========== Title/Content layout: single body placeholder ==========

  @Test
  @DisplayName("Title-Content layout produces exactly one body placeholder")
  void testTitleContentLayout_singleBodyPlaceholder() throws Exception {
    WriterTestFixtures.createTitleContentLayoutFile(extractedPptxDir, "slideLayout2");

    SPIDManager.resetInstance();
    PPTXDocument doc2 = PPTXDocument.load(extractedPptxDir, null);
    PPTXDocumentParser.ParsedPresentationState state2 = PPTXDocumentParser.parse(doc2);
    LayoutManager lm2 = new LayoutManager(state2.getLayouts());
    SPIDManager spidManager = SPIDManager.createFromParsedState(state2, lm2);
    SlideDocumentBuilder layoutBuilder = new SlideDocumentBuilder(documentBuilder, spidManager);
    LayoutManager lm = lm2;
    LayoutInfo layoutInfo = lm.getLayoutInfo("slideLayout2");

    Document doc = layoutBuilder.createSlideWithLayoutDocument("Test", 2, layoutInfo);

    // Count body placeholders (ph type="body")
    NodeList bodyPlaceholders = (NodeList) xpath.evaluate(
        "//p:sp/p:nvSpPr/p:nvPr/p:ph[@type='body']", doc, XPathConstants.NODESET);

    assertEquals(1, bodyPlaceholders.getLength(),
        "Title-Content layout should produce exactly 1 body placeholder, not " + bodyPlaceholders.getLength());
  }

  // ========== Title Slide layout: title and subtitle have geometry ==========

  @Test
  @DisplayName("Title slide shapes have non-overlapping geometry from layout")
  void testTitleSlideLayout_shapesHaveGeometry() throws Exception {
    WriterTestFixtures.createTitleSlideLayoutFile(extractedPptxDir, "slideLayout1");

    SPIDManager.resetInstance();
    PPTXDocument doc3 = PPTXDocument.load(extractedPptxDir, null);
    PPTXDocumentParser.ParsedPresentationState state3 = PPTXDocumentParser.parse(doc3);
    LayoutManager lm3 = new LayoutManager(state3.getLayouts());
    SPIDManager spidManager = SPIDManager.createFromParsedState(state3, lm3);
    SlideDocumentBuilder layoutBuilder = new SlideDocumentBuilder(documentBuilder, spidManager);
    LayoutManager lm = lm3;
    LayoutInfo layoutInfo = lm.getLayoutInfo("slideLayout1");

    Document doc = layoutBuilder.createSlideWithLayoutDocument("Welcome", 1, layoutInfo);

    NodeList shapes = (NodeList) xpath.evaluate("//p:spTree/p:sp", doc, XPathConstants.NODESET);
    assertTrue(shapes.getLength() >= 2, "Title slide should have at least 2 shapes (title + subtitle)");

    Set<String> shapePositions = new HashSet<>();
    for (int i = 0; i < shapes.getLength(); i++) {
      Element shape = (Element) shapes.item(i);
      String yVal = xpath.evaluate(".//p:spPr/a:xfrm/a:off/@y", shape);
      assertNotNull(yVal, "Shape " + i + " should have y position (a:xfrm/a:off/@y)");
      assertFalse(yVal.isEmpty(), "Shape " + i + " must have explicit geometry from layout");
      shapePositions.add(yVal);
    }

    assertEquals(shapes.getLength(), shapePositions.size(),
        "All shapes should have distinct y positions -- overlapping shapes detected");
  }

  @Test
  @DisplayName("Title shape has a:xfrm geometry when layout provides it")
  void testTitleShape_hasGeometryFromLayout() throws Exception {
    WriterTestFixtures.createTitleSlideLayoutFile(extractedPptxDir, "slideLayout1");

    SPIDManager.resetInstance();
    PPTXDocument doc4 = PPTXDocument.load(extractedPptxDir, null);
    PPTXDocumentParser.ParsedPresentationState state4 = PPTXDocumentParser.parse(doc4);
    LayoutManager lm4 = new LayoutManager(state4.getLayouts());
    SPIDManager spidManager = SPIDManager.createFromParsedState(state4, lm4);
    SlideDocumentBuilder layoutBuilder = new SlideDocumentBuilder(documentBuilder, spidManager);
    LayoutManager lm = lm4;
    LayoutInfo layoutInfo = lm.getLayoutInfo("slideLayout1");

    Document doc = layoutBuilder.createSlideWithLayoutDocument("Test Title", 1, layoutInfo);

    Node titleXfrm = (Node) xpath.evaluate(
        "//p:sp[.//p:ph[@type='ctrTitle']]/p:spPr/a:xfrm", doc, XPathConstants.NODE);
    assertNotNull(titleXfrm, "Title shape should have a:xfrm geometry from layout");

    String titleY = xpath.evaluate(".//a:off/@y", titleXfrm);
    assertEquals("1122363", titleY, "Title y position should match layout");
  }

  @Test
  @DisplayName("Subtitle shape has a:xfrm geometry when layout provides it")
  void testSubtitleShape_hasGeometryFromLayout() throws Exception {
    WriterTestFixtures.createTitleSlideLayoutFile(extractedPptxDir, "slideLayout1");

    SPIDManager.resetInstance();
    PPTXDocument doc5 = PPTXDocument.load(extractedPptxDir, null);
    PPTXDocumentParser.ParsedPresentationState state5 = PPTXDocumentParser.parse(doc5);
    LayoutManager lm5 = new LayoutManager(state5.getLayouts());
    SPIDManager spidManager = SPIDManager.createFromParsedState(state5, lm5);
    SlideDocumentBuilder layoutBuilder = new SlideDocumentBuilder(documentBuilder, spidManager);
    LayoutManager lm = lm5;
    LayoutInfo layoutInfo = lm.getLayoutInfo("slideLayout1");

    Document doc = layoutBuilder.createSlideWithLayoutDocument("Test", 1, layoutInfo);

    Node subtitleXfrm = (Node) xpath.evaluate(
        "//p:sp[.//p:ph[@type='subTitle']]/p:spPr/a:xfrm", doc, XPathConstants.NODE);
    assertNotNull(subtitleXfrm, "Subtitle shape should have a:xfrm geometry from layout");

    String subtitleY = xpath.evaluate(".//a:off/@y", subtitleXfrm);
    assertEquals("3602038", subtitleY, "Subtitle y position should match layout");
  }
}
