package com.excudo.xml.parsers;

import com.excudo.core.model.PPTXDocument;
import com.excudo.core.model.ParsedSlideData;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.SlideShape;
import com.excudo.core.model.TableModel;
import com.excudo.exceptions.XMLParsingException;
import org.junit.Test;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Deck-level pins for A6 graphicFrame parsing: real corpus decks parse
 * into TABLE-typed shapes with correct geometry and table payloads,
 * non-table graphicFrames (charts) stay invisible pending their own
 * phases, and a malformed a:tbl fails the whole slide parse loudly.
 * Fixtures HARD-FAIL when missing.
 */
public class GraphicFrameParsingTest {

    private static final File TABLES_BASIC =
        new File("parity-corpus/tables-basic/deck.pptx");
    private static final File TABLES_MERGES =
        new File("parity-corpus/tables-merges/deck.pptx");
    private static final File CHARTS_DECK =
        new File("parity-corpus/charts-placeholder/deck.pptx");
    private static final File STRESS_DECK =
        new File("test-pptx-samples/textel-crud/native/stress_test_complex_text.pptx");

    // ===================================================================
    // tables-basic: one 4x5 table, no merges
    // ===================================================================

    @Test
    public void tablesBasicDeck_parsesTheTableShapeWithGeometryAndModel() throws Exception {
        List<SlideShape> shapes = parseSlide(TABLES_BASIC, 1);
        assertEquals("slide 1 carries exactly the table", 1, shapes.size());

        SlideShape table = shapes.get(0);
        assertEquals(SlideShape.ShapeType.TABLE, table.getType());
        assertEquals(2, table.getSpid());
        assertEquals("Table 1", table.getName());

        // p:xfrm geometry, straight from the deck XML.
        ShapeGeometry g = table.getGeometry();
        assertEquals(457200L, g.getX());
        assertEquals(457200L, g.getY());
        assertEquals(14097000L, g.getWidth());
        assertEquals(3962400L, g.getHeight());

        TableModel model = table.getTableModel();
        assertNotNull("TABLE shape must carry its parsed model", model);
        assertEquals(4, model.getColumnCount());
        assertEquals(5, model.getRowCount());
        assertEquals(5638800L, model.getColumnWidthEmu(0));
        assertEquals(2819400L, model.getColumnWidthEmu(1));
        assertEquals(792480L, model.getRows().get(0).heightEmu());
        assertTrue(model.getProperties().firstRow());
        assertTrue(model.getProperties().bandRow());
        assertEquals("{5C22544A-7EE6-4342-B048-85BDC9FD1C3A}",
            model.getProperties().styleId());
        assertNotNull("cells carry their a:txBody elements",
            model.getCell(0, 0).txBody());
    }

    // ===================================================================
    // tables-merges: 5x5 with header merge, tall merge, 2x2 block
    // ===================================================================

    @Test
    public void tablesMergesDeck_mergeMapAndFillOverrideParse() throws Exception {
        List<SlideShape> shapes = parseSlide(TABLES_MERGES, 1);
        assertEquals(1, shapes.size());
        TableModel model = shapes.get(0).getTableModel();
        assertNotNull(model);
        assertEquals(5, model.getColumnCount());
        assertEquals(5, model.getRowCount());

        // Header row merge: all five slots anchor at (0,0).
        for (int c = 0; c < 5; c++) {
            assertArrayEquals("(0," + c + ") anchors at the header merge",
                new int[]{0, 0}, model.anchorOf(0, c));
        }
        // Tall merge down column 0, rows 1-4.
        for (int r = 1; r < 5; r++) {
            assertArrayEquals("(" + r + ",0) anchors at the tall merge",
                new int[]{1, 0}, model.anchorOf(r, 0));
        }
        // 2x2 block merge at rows 2-3, cols 2-3.
        assertArrayEquals(new int[]{2, 2}, model.anchorOf(2, 2));
        assertArrayEquals(new int[]{2, 2}, model.anchorOf(2, 3));
        assertArrayEquals(new int[]{2, 2}, model.anchorOf(3, 2));
        assertArrayEquals(new int[]{2, 2}, model.anchorOf(3, 3));
        // Neighbours outside the block are their own anchors.
        assertTrue(model.isAnchor(2, 1));
        assertTrue(model.isAnchor(4, 2));

        // The r1c4 per-cell fill override (srgbClr C00000).
        TableModel.Cell override = model.getCell(1, 4);
        assertNotNull("r1c4 must carry its tcPr fill override", override.fill());
        assertEquals("solidFill", override.fill().getLocalName());
    }

    // ===================================================================
    // Mixed deck: table joins the existing shapes, displacing none
    // ===================================================================

    @Test
    public void stressDeckSlide8_tableJoinsTheRegistryWithoutDisplacingShapes() throws Exception {
        List<SlideShape> shapes = parseSlide(STRESS_DECK, 8);
        long tables = shapes.stream()
            .filter(s -> s.getType() == SlideShape.ShapeType.TABLE).count();
        assertEquals("exactly one table on slide 8", 1, tables);
        // Pre-A6 the registry held 7 shapes (title, content placeholder,
        // two snip-corner boxes, two hexagons, text box); the table is
        // purely additive.
        assertEquals(8, shapes.size());
        SlideShape table = shapes.stream()
            .filter(s -> s.getType() == SlideShape.ShapeType.TABLE).findFirst().orElseThrow();
        assertNotNull(table.getTableModel());
        assertEquals("Table 3", table.getName());
    }

    // ===================================================================
    // Non-table graphicFrames stay invisible (charts pending their phase)
    // ===================================================================

    @Test
    public void chartGraphicFrame_staysInvisibleToTheRegistry() throws Exception {
        List<SlideShape> shapes = parseSlide(CHARTS_DECK, 1);
        assertTrue("no TABLE shapes in the charts deck",
            shapes.stream().noneMatch(s -> s.getType() == SlideShape.ShapeType.TABLE));
        // The chart graphicFrame itself must not surface as any type.
        assertTrue("chart graphicFrame must not become a shape",
            shapes.stream().noneMatch(s -> s.getXmlElement() != null
                && "p:graphicFrame".equals(s.getXmlElement().getTagName())));
    }

    // ===================================================================
    // Malformed table XML fails the slide parse loudly
    // ===================================================================

    @Test
    public void malformedTable_failsSlideParse() throws Exception {
        String slideXml =
            "<p:sld xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\""
            + " xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\">"
            + "<p:cSld><p:spTree>"
            + "<p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>"
            + "<p:grpSpPr/>"
            + "<p:graphicFrame>"
            + "<p:nvGraphicFramePr><p:cNvPr id=\"2\" name=\"Broken\"/>"
            + "<p:cNvGraphicFramePr/><p:nvPr/></p:nvGraphicFramePr>"
            + "<p:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"1000\" cy=\"1000\"/></p:xfrm>"
            + "<a:graphic><a:graphicData"
            + " uri=\"http://schemas.openxmlformats.org/drawingml/2006/table\">"
            // a:tbl with a grid but a row whose cell count disagrees
            + "<a:tbl><a:tblGrid><a:gridCol w=\"500\"/><a:gridCol w=\"500\"/></a:tblGrid>"
            + "<a:tr h=\"500\"><a:tc><a:txBody><a:bodyPr/><a:p/></a:txBody></a:tc></a:tr>"
            + "</a:tbl>"
            + "</a:graphicData></a:graphic>"
            + "</p:graphicFrame>"
            + "</p:spTree></p:cSld></p:sld>";

        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        Document dom = f.newDocumentBuilder()
            .parse(new ByteArrayInputStream(slideXml.getBytes(StandardCharsets.UTF_8)));

        try {
            new SlideXMLParser().parseSlide(dom, 1);
            fail("malformed a:tbl must fail the slide parse, not produce an empty table");
        } catch (XMLParsingException expected) {
            // The parser wraps the model's IllegalArgumentException.
        }
    }

    // ===================================================================
    // Helpers
    // ===================================================================

    private static List<SlideShape> parseSlide(File deck, int slideNumber) throws Exception {
        assertTrue("Fixture missing (hard failure, never skip): " + deck.getAbsolutePath(),
            deck.isFile());
        PPTXDocument doc = PPTXDocument.loadFromZip(deck);
        ParsedSlideData parsed = doc.getParsedSlideData(slideNumber,
            (dom, n) -> new SlideXMLParser().parseSlide(dom, n));
        assertNotNull("slide " + slideNumber + " must parse", parsed);
        return parsed.getShapeRegistry().getAllShapes();
    }
}
