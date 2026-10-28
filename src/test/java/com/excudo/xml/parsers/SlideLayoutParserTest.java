package com.excudo.xml.parsers;

import com.excudo.core.model.PPTXDocument;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.utils.XMLFactoryProvider;
import com.excudo.xml.parsers.SlideXMLParser.SlideLayoutParser;
import org.junit.jupiter.api.*;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SlideXMLParser.SlideLayoutParser - layout geometry resolution,
 * title placeholder detection, and parse-with-directory overload.
 * All tests operate through PPTXDocument (in-memory mode only).
 */
class SlideLayoutParserTest {

    private DocumentBuilder documentBuilder;

    @BeforeEach
    void setUp() throws Exception {
        documentBuilder = XMLFactoryProvider.createDocumentBuilder();
    }

    private PPTXDocument buildPptxDocument() throws Exception {
        PPTXDocument doc = PPTXDocument.createEmpty();

        doc.putXmlPart("ppt/slideMasters/slideMaster1.xml",
            documentBuilder.parse(new InputSource(new StringReader("""
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <p:sldMaster xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                             xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
                             xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
                  <p:cSld><p:spTree><p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/></p:spTree></p:cSld>
                  <p:sldLayoutIdLst>
                    <p:sldLayoutId id="2147483649" r:id="rId1"/>
                  </p:sldLayoutIdLst>
                </p:sldMaster>
                """))));

        doc.putXmlPart("ppt/slideMasters/_rels/slideMaster1.xml.rels",
            documentBuilder.parse(new InputSource(new StringReader("""
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/>
                </Relationships>
                """))));

        doc.putXmlPart("ppt/slideLayouts/slideLayout1.xml",
            documentBuilder.parse(new InputSource(new StringReader("""
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <p:sldLayout xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                             xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
                             xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                             type="title">
                  <p:cSld><p:spTree>
                    <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/>
                    <p:sp>
                      <p:nvSpPr><p:cNvPr id="2" name="Title 1"/><p:cNvSpPr/><p:nvPr><p:ph type="title"/></p:nvPr></p:nvSpPr>
                      <p:spPr><a:xfrm><a:off x="457200" y="274638"/><a:ext cx="8229600" cy="1143000"/></a:xfrm></p:spPr>
                    </p:sp>
                    <p:sp>
                      <p:nvSpPr><p:cNvPr id="3" name="Body 1"/><p:cNvSpPr/><p:nvPr><p:ph idx="1"/></p:nvPr></p:nvSpPr>
                      <p:spPr><a:xfrm><a:off x="457200" y="1600200"/><a:ext cx="8229600" cy="4525963"/></a:xfrm></p:spPr>
                    </p:sp>
                  </p:spTree></p:cSld>
                </p:sldLayout>
                """))));

        doc.putXmlPart("ppt/slides/_rels/slide1.xml.rels",
            documentBuilder.parse(new InputSource(new StringReader("""
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/>
                </Relationships>
                """))));

        doc.putXmlPart("ppt/slides/slide1.xml",
            documentBuilder.parse(new InputSource(new StringReader("""
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                       xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
                       xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
                  <p:cSld><p:spTree>
                    <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/>
                    <p:sp>
                      <p:nvSpPr><p:cNvPr id="2" name="Title 1"/><p:cNvSpPr><a:spLocks noGrp="1"/></p:cNvSpPr><p:nvPr><p:ph type="title"/></p:nvPr></p:nvSpPr>
                      <p:spPr/>
                      <p:txBody><a:bodyPr/><a:lstStyle/><a:p><a:r><a:t>Test Title</a:t></a:r></a:p></p:txBody>
                    </p:sp>
                  </p:spTree></p:cSld>
                </p:sld>
                """))));

        return doc;
    }

    private static SlideLayoutParser layouts(PPTXDocument doc) {
        return SlideLayoutParser.forDocument(doc);
    }

    // ========== EDGE CASES ==========

    @Test
    @DisplayName("getPlaceholderGeometry returns null for an empty deck")
    void testEmptyDeck() {
        assertNull(layouts(PPTXDocument.createEmpty()).getPlaceholderGeometry("title", "", 1));
    }

    @Test
    @DisplayName("forDocument(null) and foreign DOMs have no layout parser")
    void testNoOwner() throws Exception {
        assertNull(SlideLayoutParser.forDocument(null));
        org.w3c.dom.Document foreign = documentBuilder.newDocument();
        assertNull(SlideLayoutParser.forNode(foreign), "a DOM not handed out by a PPTXDocument has no owner");
    }

    @Test
    @DisplayName("getPlaceholderGeometry returns null for missing slide rels")
    void testMissingSlideRels() {
        assertNull(layouts(PPTXDocument.createEmpty()).getPlaceholderGeometry("title", "", 99));
    }

    // ========== SYNTHETIC LAYOUT TESTS ==========

    @Test
    @DisplayName("Resolves placeholder geometry from synthetic layout")
    void testResolvePlaceholderGeometry() throws Exception {
        ShapeGeometry geo = layouts(buildPptxDocument()).getPlaceholderGeometry("title", "", 1);
        assertNotNull(geo, "Should resolve title placeholder geometry");
        assertEquals(457200, geo.getX());
        assertEquals(274638, geo.getY());
        assertEquals(8229600, geo.getWidth());
        assertEquals(1143000, geo.getHeight());
    }

    @Test
    @DisplayName("Caches layout geometry across repeated calls")
    void testLayoutCaching() throws Exception {
        SlideLayoutParser parser = layouts(buildPptxDocument());
        ShapeGeometry first = parser.getPlaceholderGeometry("title", "", 1);
        assertNotNull(first);
        assertSame(first, parser.getPlaceholderGeometry("title", "", 1), "Second call should return cached geometry");
    }

    @Test
    @DisplayName("A layout edit refreshes cached geometry")
    void testCacheRefreshesAfterLayoutEdit() throws Exception {
        PPTXDocument doc = buildPptxDocument();
        assertEquals(457200, layouts(doc).getPlaceholderGeometry("title", "", 1).getX());

        org.w3c.dom.Document layout = doc.getXmlPart("ppt/slideLayouts/slideLayout1.xml");
        org.w3c.dom.Element off = (org.w3c.dom.Element) layout.getElementsByTagNameNS(
            "http://schemas.openxmlformats.org/drawingml/2006/main", "off").item(0);
        off.setAttribute("x", "999");
        doc.markDirty("ppt/slideLayouts/slideLayout1.xml");

        assertEquals(999, layouts(doc).getPlaceholderGeometry("title", "", 1).getX());
    }

    @Test
    @DisplayName("Two open decks never see each other's layouts")
    void testDecksAreIsolated() throws Exception {
        PPTXDocument a = buildPptxDocument();
        PPTXDocument b = buildPptxDocument();
        org.w3c.dom.Element offB = (org.w3c.dom.Element) b.getXmlPart("ppt/slideLayouts/slideLayout1.xml")
            .getElementsByTagNameNS("http://schemas.openxmlformats.org/drawingml/2006/main", "off").item(0);
        offB.setAttribute("x", "12345");
        b.markDirty("ppt/slideLayouts/slideLayout1.xml");

        assertEquals(457200, layouts(a).getPlaceholderGeometry("title", "", 1).getX());
        assertEquals(12345, layouts(b).getPlaceholderGeometry("title", "", 1).getX());
        assertEquals(457200, layouts(a).getPlaceholderGeometry("title", "", 1).getX(), "deck A unaffected by deck B");
    }

    @Test
    @DisplayName("Resolves body placeholder by idx")
    void testBodyPlaceholderByIdx() throws Exception {
        ShapeGeometry geo = layouts(buildPptxDocument()).getPlaceholderGeometry("body", "1", 1);
        assertNotNull(geo, "Should resolve body placeholder geometry");
        assertEquals(457200, geo.getX());
        assertEquals(1600200, geo.getY());
    }

    // ========== TITLE PLACEHOLDER DETECTION ==========

    @Test
    @DisplayName("layoutHasTitlePlaceholder detects title in layout")
    void testLayoutHasTitlePlaceholder() throws Exception {
        assertTrue(layouts(buildPptxDocument()).layoutHasTitlePlaceholder("ppt/slideLayouts/slideLayout1.xml"));
    }

    @Test
    @DisplayName("layoutHasTitlePlaceholder returns false for non-existent part")
    void testLayoutHasTitlePlaceholderMissingFile() {
        assertFalse(layouts(PPTXDocument.createEmpty()).layoutHasTitlePlaceholder("ppt/slideLayouts/nonexistent.xml"));
    }

    @Test
    @DisplayName("layoutHasTitlePlaceholder returns false for layout without title")
    void testLayoutWithoutTitle() throws Exception {
        PPTXDocument doc = PPTXDocument.createEmpty();
        doc.putXmlPart("ppt/slideLayouts/noTitle.xml",
            documentBuilder.parse(new InputSource(new StringReader("""
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <p:sldLayout xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                             xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
                             xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
                  <p:cSld><p:spTree>
                    <p:sp><p:nvSpPr><p:cNvPr id="2" name="Body 1"/><p:cNvSpPr/><p:nvPr><p:ph idx="1"/></p:nvPr></p:nvSpPr>
                      <p:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="100" cy="100"/></a:xfrm></p:spPr>
                    </p:sp>
                  </p:spTree></p:cSld>
                </p:sldLayout>
                """))));
        assertFalse(layouts(doc).layoutHasTitlePlaceholder("ppt/slideLayouts/noTitle.xml"));
    }

    @Test
    @DisplayName("slideLayoutHasTitlePlaceholder resolves via slide number")
    void testSlideLayoutHasTitlePlaceholderBySlideNumber() throws Exception {
        assertTrue(layouts(buildPptxDocument()).slideLayoutHasTitlePlaceholder(1));
    }

    @Test
    @DisplayName("slideLayoutHasTitlePlaceholder returns false for missing slide")
    void testSlideLayoutHasTitlePlaceholderMissingSlide() throws Exception {
        assertFalse(layouts(buildPptxDocument()).slideLayoutHasTitlePlaceholder(99));
    }

    // ========== parseSlide WITH PPTXDocument ==========

    @Test
    @DisplayName("parseSlide(Document, int) reaches the owning deck's layout")
    void testParseSlideWithPptxDocument() throws Exception {
        PPTXDocument doc = buildPptxDocument();
        var result = new SlideXMLParser().parseSlide(doc.getSlideDocument(1), 1);

        assertNotNull(result.getShapeRegistry(), "Should have a shape registry");
        assertEquals("slideLayout1", result.getLayoutId(), "layout resolved through the slide DOM's owner");
    }
}
