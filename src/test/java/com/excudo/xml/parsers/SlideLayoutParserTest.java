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

    @AfterEach
    void resetParser() {
        // Re-initialize with null PPTXDocument to clear static state between tests
        SlideLayoutParser.initialize(PPTXDocument.createEmpty());
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

    // ========== INITIALIZATION AND EDGE CASES ==========

    @Test
    @DisplayName("getPlaceholderGeometry returns null when not initialized")
    void testNotInitialized() {
        SlideLayoutParser.initialize(PPTXDocument.createEmpty());
        ShapeGeometry geo = SlideLayoutParser.getPlaceholderGeometry("title", "", 1);
        assertNull(geo, "Should return null when not initialized");
    }

    @Test
    @DisplayName("Legacy getPlaceholderGeometry always returns null")
    void testLegacyMethodReturnsNull() {
        ShapeGeometry geo = SlideLayoutParser.getPlaceholderGeometry("title", "");
        assertNull(geo, "Legacy method should always return null");
    }

    @Test
    @DisplayName("getPlaceholderGeometry returns null for missing slide rels")
    void testMissingSlideRels() throws Exception {
        // PPTXDocument with no slide rels
        PPTXDocument doc = PPTXDocument.createEmpty();
        SlideLayoutParser.initialize(doc);

        ShapeGeometry geo = SlideLayoutParser.getPlaceholderGeometry("title", "", 99);
        assertNull(geo, "Should return null when slide rels are missing");
    }

    // ========== SYNTHETIC LAYOUT TESTS ==========

    @Test
    @DisplayName("Resolves placeholder geometry from synthetic layout")
    void testResolvePlaceholderGeometry() throws Exception {
        PPTXDocument doc = buildPptxDocument();
        SlideLayoutParser.initialize(doc);

        ShapeGeometry geo = SlideLayoutParser.getPlaceholderGeometry("title", "", 1);
        assertNotNull(geo, "Should resolve title placeholder geometry");
        assertEquals(457200, geo.getX());
        assertEquals(274638, geo.getY());
        assertEquals(8229600, geo.getWidth());
        assertEquals(1143000, geo.getHeight());
    }

    @Test
    @DisplayName("Caches layout geometry across repeated calls")
    void testLayoutCaching() throws Exception {
        PPTXDocument doc = buildPptxDocument();
        SlideLayoutParser.initialize(doc);

        ShapeGeometry first = SlideLayoutParser.getPlaceholderGeometry("title", "", 1);
        ShapeGeometry second = SlideLayoutParser.getPlaceholderGeometry("title", "", 1);
        assertNotNull(first);
        assertSame(first, second, "Second call should return cached geometry");
    }

    @Test
    @DisplayName("Resolves body placeholder by idx")
    void testBodyPlaceholderByIdx() throws Exception {
        PPTXDocument doc = buildPptxDocument();
        SlideLayoutParser.initialize(doc);

        ShapeGeometry geo = SlideLayoutParser.getPlaceholderGeometry("body", "1", 1);
        assertNotNull(geo, "Should resolve body placeholder geometry");
        assertEquals(457200, geo.getX());
        assertEquals(1600200, geo.getY());
    }

    // ========== TITLE PLACEHOLDER DETECTION ==========

    @Test
    @DisplayName("layoutHasTitlePlaceholder detects title in layout")
    void testLayoutHasTitlePlaceholder() throws Exception {
        PPTXDocument doc = buildPptxDocument();
        SlideLayoutParser.initialize(doc);

        assertTrue(SlideLayoutParser.layoutHasTitlePlaceholder("ppt/slideLayouts/slideLayout1.xml"),
            "Layout with title placeholder should return true");
    }

    @Test
    @DisplayName("layoutHasTitlePlaceholder returns false for non-existent part")
    void testLayoutHasTitlePlaceholderMissingFile() throws Exception {
        PPTXDocument doc = PPTXDocument.createEmpty();
        SlideLayoutParser.initialize(doc);

        assertFalse(SlideLayoutParser.layoutHasTitlePlaceholder("ppt/slideLayouts/nonexistent.xml"),
            "Non-existent part should return false");
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
        SlideLayoutParser.initialize(doc);

        assertFalse(SlideLayoutParser.layoutHasTitlePlaceholder("ppt/slideLayouts/noTitle.xml"),
            "Layout without title/ctrTitle should return false");
    }

    @Test
    @DisplayName("slideLayoutHasTitlePlaceholder resolves via slide number")
    void testSlideLayoutHasTitlePlaceholderBySlideNumber() throws Exception {
        PPTXDocument doc = buildPptxDocument();
        SlideLayoutParser.initialize(doc);

        assertTrue(SlideLayoutParser.slideLayoutHasTitlePlaceholder(1),
            "Slide 1 uses a layout with title placeholder");
    }

    @Test
    @DisplayName("slideLayoutHasTitlePlaceholder returns false for missing slide")
    void testSlideLayoutHasTitlePlaceholderMissingSlide() throws Exception {
        PPTXDocument doc = buildPptxDocument();
        SlideLayoutParser.initialize(doc);

        assertFalse(SlideLayoutParser.slideLayoutHasTitlePlaceholder(99),
            "Non-existent slide should return false");
    }

    // ========== parseSlide WITH PPTXDocument ==========

    @Test
    @DisplayName("parseSlide(Document, int) parses slide successfully")
    void testParseSlideWithPptxDocument() throws Exception {
        PPTXDocument doc = buildPptxDocument();
        SlideLayoutParser.initialize(doc);

        SlideXMLParser parser = new SlideXMLParser();
        org.w3c.dom.Document slideDoc = doc.getXmlPart("ppt/slides/slide1.xml");
        var result = parser.parseSlide(slideDoc, 1);

        assertNotNull(result, "Should parse slide successfully");
        assertNotNull(result.getShapeRegistry(), "Should have a shape registry");
    }
}
