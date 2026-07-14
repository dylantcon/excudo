package com.excudo.core.model;

import org.junit.Test;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

/**
 * Pins {@link TableModel#parse} to ECMA-376 21.1.3 CT_Table semantics:
 * grid/row structure, tblPr flags + styleId, per-cell tcPr payloads,
 * the hMerge/vMerge/gridSpan/rowSpan merge encoding, and — critically —
 * that malformed table XML throws instead of producing a silently
 * empty or half-parsed table.
 */
public class TableModelTest {

    private static final String A_NS =
        "http://schemas.openxmlformats.org/drawingml/2006/main";

    // ===================================================================
    // Structure
    // ===================================================================

    @Test
    public void basicTable_gridRowsFlagsAndStyleIdParse() {
        TableModel t = parse(
            "<a:tbl>"
            + "<a:tblPr firstRow=\"1\" bandRow=\"1\">"
            + "<a:tableStyleId>{5C22544A-7EE6-4342-B048-85BDC9FD1C3A}</a:tableStyleId>"
            + "</a:tblPr>"
            + "<a:tblGrid><a:gridCol w=\"5638800\"/><a:gridCol w=\"2819400\"/></a:tblGrid>"
            + tr(792480, tc("Region"), tc("Q1"))
            + tr(792480, tc("North"), tc("104"))
            + "</a:tbl>");

        assertEquals(2, t.getColumnCount());
        assertEquals(2, t.getRowCount());
        assertEquals(5638800L, t.getColumnWidthEmu(0));
        assertEquals(2819400L, t.getColumnWidthEmu(1));
        assertEquals(8458200L, t.getTotalWidthEmu());
        assertEquals(792480L, t.getRows().get(0).heightEmu());
        assertEquals(1584960L, t.getTotalHeightEmu());

        TableModel.Properties p = t.getProperties();
        assertTrue(p.firstRow());
        assertTrue(p.bandRow());
        assertFalse(p.lastRow());
        assertFalse(p.firstCol());
        assertFalse(p.lastCol());
        assertFalse(p.bandCol());
        assertEquals("{5C22544A-7EE6-4342-B048-85BDC9FD1C3A}", p.styleId());

        TableModel.Cell cell = t.getCell(0, 0);
        assertEquals(1, cell.gridSpan());
        assertEquals(1, cell.rowSpan());
        assertFalse(cell.isMergeContinuation());
        assertNotNull("cell text body element must be captured", cell.txBody());
        assertNull(cell.anchor());
        assertNull(cell.fill());
        assertNull(cell.marLEmu());
    }

    @Test
    public void missingTblPr_defaultsAllFlagsOffAndNoStyle() {
        TableModel t = parse(
            "<a:tbl>"
            + "<a:tblGrid><a:gridCol w=\"1000\"/></a:tblGrid>"
            + tr(500, tc("x"))
            + "</a:tbl>");
        assertFalse(t.getProperties().firstRow());
        assertFalse(t.getProperties().bandRow());
        assertNull(t.getProperties().styleId());
    }

    // ===================================================================
    // tcPr payload
    // ===================================================================

    @Test
    public void tcPr_marginsAnchorFillAndEdgeBordersParse() {
        TableModel t = parse(
            "<a:tbl>"
            + "<a:tblGrid><a:gridCol w=\"1000\"/></a:tblGrid>"
            + "<a:tr h=\"500\"><a:tc>"
            + "<a:txBody><a:bodyPr/><a:p/></a:txBody>"
            + "<a:tcPr marL=\"12700\" marR=\"25400\" marT=\"6350\" marB=\"3175\" anchor=\"ctr\">"
            + "<a:lnL w=\"12700\"><a:solidFill><a:srgbClr val=\"FF0000\"/></a:solidFill></a:lnL>"
            + "<a:lnR w=\"25400\"><a:solidFill><a:srgbClr val=\"00FF00\"/></a:solidFill></a:lnR>"
            + "<a:lnT w=\"38100\"><a:solidFill><a:srgbClr val=\"0000FF\"/></a:solidFill></a:lnT>"
            + "<a:lnB w=\"50800\"><a:solidFill><a:srgbClr val=\"FFFF00\"/></a:solidFill></a:lnB>"
            + "<a:solidFill><a:srgbClr val=\"C00000\"/></a:solidFill>"
            + "</a:tcPr>"
            + "</a:tc></a:tr>"
            + "</a:tbl>");

        TableModel.Cell cell = t.getCell(0, 0);
        assertEquals(Long.valueOf(12700), cell.marLEmu());
        assertEquals(Long.valueOf(25400), cell.marREmu());
        assertEquals(Long.valueOf(6350), cell.marTEmu());
        assertEquals(Long.valueOf(3175), cell.marBEmu());
        assertEquals("ctr", cell.anchor());
        assertNotNull("fill choice element must be captured", cell.fill());
        assertEquals("solidFill", cell.fill().getLocalName());
        assertEquals("12700", cell.lnL().getAttribute("w"));
        assertEquals("25400", cell.lnR().getAttribute("w"));
        assertEquals("38100", cell.lnT().getAttribute("w"));
        assertEquals("50800", cell.lnB().getAttribute("w"));
    }

    @Test
    public void tcPr_noFillIsCapturedAsTheFillChoice() {
        TableModel t = parse(
            "<a:tbl>"
            + "<a:tblGrid><a:gridCol w=\"1000\"/></a:tblGrid>"
            + "<a:tr h=\"500\"><a:tc><a:txBody><a:bodyPr/><a:p/></a:txBody>"
            + "<a:tcPr><a:noFill/></a:tcPr></a:tc></a:tr>"
            + "</a:tbl>");
        assertEquals("noFill", t.getCell(0, 0).fill().getLocalName());
    }

    // ===================================================================
    // Merges: the exact encoding python-pptx / PowerPoint author
    // ===================================================================

    @Test
    public void merges_anchorResolutionMatchesTheSpecEncoding() {
        // 3x3 with: full-width header merge, 2x2 block merge at (1,1).
        TableModel t = parse(
            "<a:tbl>"
            + "<a:tblGrid><a:gridCol w=\"1000\"/><a:gridCol w=\"1000\"/><a:gridCol w=\"1000\"/></a:tblGrid>"
            + "<a:tr h=\"500\">"
            + "<a:tc gridSpan=\"3\">" + body("hdr") + "</a:tc>"
            + "<a:tc hMerge=\"1\">" + body("") + "</a:tc>"
            + "<a:tc hMerge=\"1\">" + body("") + "</a:tc>"
            + "</a:tr>"
            + "<a:tr h=\"500\">"
            + "<a:tc>" + body("a") + "</a:tc>"
            + "<a:tc gridSpan=\"2\" rowSpan=\"2\">" + body("block") + "</a:tc>"
            + "<a:tc rowSpan=\"2\" hMerge=\"1\">" + body("") + "</a:tc>"
            + "</a:tr>"
            + "<a:tr h=\"500\">"
            + "<a:tc>" + body("b") + "</a:tc>"
            + "<a:tc gridSpan=\"2\" vMerge=\"1\">" + body("") + "</a:tc>"
            + "<a:tc hMerge=\"1\" vMerge=\"1\">" + body("") + "</a:tc>"
            + "</a:tr>"
            + "</a:tbl>");

        // Header merge: every slot of row 0 anchors at (0,0).
        assertTrue(t.isAnchor(0, 0));
        assertArrayEquals(new int[]{0, 0}, t.anchorOf(0, 1));
        assertArrayEquals(new int[]{0, 0}, t.anchorOf(0, 2));
        assertFalse(t.isAnchor(0, 2));

        // Block merge: all four slots anchor at (1,1).
        assertTrue(t.isAnchor(1, 1));
        assertArrayEquals(new int[]{1, 1}, t.anchorOf(1, 2));
        assertArrayEquals(new int[]{1, 1}, t.anchorOf(2, 1));
        assertArrayEquals(new int[]{1, 1}, t.anchorOf(2, 2));

        // Plain cells anchor at themselves.
        assertTrue(t.isAnchor(1, 0));
        assertTrue(t.isAnchor(2, 0));
    }

    // ===================================================================
    // Malformed XML throws — never a silently empty table
    // ===================================================================

    @Test
    public void missingTblGrid_throws() {
        assertParseThrows("<a:tbl>" + tr(500, tc("x")) + "</a:tbl>", "tblGrid");
    }

    @Test
    public void emptyTblGrid_throws() {
        assertParseThrows("<a:tbl><a:tblGrid/>" + tr(500, tc("x")) + "</a:tbl>", "gridCol");
    }

    @Test
    public void nonNumericColumnWidth_throws() {
        assertParseThrows(
            "<a:tbl><a:tblGrid><a:gridCol w=\"wide\"/></a:tblGrid>"
            + tr(500, tc("x")) + "</a:tbl>", "not a number");
    }

    @Test
    public void nonPositiveColumnWidth_throws() {
        assertParseThrows(
            "<a:tbl><a:tblGrid><a:gridCol w=\"0\"/></a:tblGrid>"
            + tr(500, tc("x")) + "</a:tbl>", "positive");
    }

    @Test
    public void noRows_throws() {
        assertParseThrows(
            "<a:tbl><a:tblGrid><a:gridCol w=\"1000\"/></a:tblGrid></a:tbl>", "a:tr");
    }

    @Test
    public void missingRowHeight_throws() {
        assertParseThrows(
            "<a:tbl><a:tblGrid><a:gridCol w=\"1000\"/></a:tblGrid>"
            + "<a:tr><a:tc>" + body("x") + "</a:tc></a:tr></a:tbl>", "@h");
    }

    @Test
    public void cellCountDisagreesWithGrid_throws() {
        assertParseThrows(
            "<a:tbl><a:tblGrid><a:gridCol w=\"1000\"/><a:gridCol w=\"1000\"/></a:tblGrid>"
            + tr(500, tc("only-one")) + "</a:tbl>", "columns");
    }

    @Test
    public void zeroGridSpan_throws() {
        assertParseThrows(
            "<a:tbl><a:tblGrid><a:gridCol w=\"1000\"/></a:tblGrid>"
            + "<a:tr h=\"500\"><a:tc gridSpan=\"0\">" + body("x") + "</a:tc></a:tr></a:tbl>",
            ">= 1");
    }

    @Test
    public void spanLeavingTheGrid_throws() {
        assertParseThrows(
            "<a:tbl><a:tblGrid><a:gridCol w=\"1000\"/><a:gridCol w=\"1000\"/></a:tblGrid>"
            + "<a:tr h=\"500\"><a:tc gridSpan=\"3\">" + body("x") + "</a:tc>"
            + "<a:tc hMerge=\"1\">" + body("") + "</a:tc></a:tr></a:tbl>",
            "leaves the");
    }

    @Test
    public void continuationWithoutCoveringAnchor_throws() {
        assertParseThrows(
            "<a:tbl><a:tblGrid><a:gridCol w=\"1000\"/><a:gridCol w=\"1000\"/></a:tblGrid>"
            + "<a:tr h=\"500\"><a:tc>" + body("x") + "</a:tc>"
            + "<a:tc vMerge=\"1\">" + body("") + "</a:tc></a:tr></a:tbl>",
            "no anchor span covers");
    }

    @Test
    public void anchorSpanCoveringANonContinuationCell_throws() {
        assertParseThrows(
            "<a:tbl><a:tblGrid><a:gridCol w=\"1000\"/><a:gridCol w=\"1000\"/></a:tblGrid>"
            + "<a:tr h=\"500\"><a:tc gridSpan=\"2\">" + body("x") + "</a:tc>"
            + "<a:tc>" + body("not-marked") + "</a:tc></a:tr></a:tbl>",
            "not marked");
    }

    @Test
    public void nonTblElement_throws() {
        Element notATable = element("<a:tblGrid/>");
        try {
            TableModel.parse(notATable);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("a:tbl"));
        }
    }

    // ===================================================================
    // Fixture helpers
    // ===================================================================

    private static String body(String text) {
        return "<a:txBody><a:bodyPr/><a:p><a:r><a:t>" + text + "</a:t></a:r></a:p></a:txBody>";
    }

    private static String tc(String text) {
        return "<a:tc>" + body(text) + "<a:tcPr/></a:tc>";
    }

    private static String tr(long h, String... cells) {
        return "<a:tr h=\"" + h + "\">" + String.join("", cells) + "</a:tr>";
    }

    private static TableModel parse(String tblXml) {
        return TableModel.parse(element(tblXml));
    }

    private static void assertParseThrows(String tblXml, String messageFragment) {
        try {
            TableModel.parse(element(tblXml));
            fail("expected IllegalArgumentException containing '" + messageFragment + "'");
        } catch (IllegalArgumentException e) {
            assertTrue("message should mention '" + messageFragment + "': " + e.getMessage(),
                e.getMessage().contains(messageFragment));
        }
    }

    private static Element element(String xml) {
        // Splice the drawingml namespace into the root tag, handling both
        // <a:x>...</a:x> and self-closing <a:x/> roots.
        String withNs = xml.replaceFirst("(/?)>", " xmlns:a=\"" + A_NS + "\"$1>");
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            return f.newDocumentBuilder()
                .parse(new ByteArrayInputStream(withNs.getBytes(StandardCharsets.UTF_8)))
                .getDocumentElement();
        } catch (Exception e) {
            throw new IllegalStateException("bad test fixture XML", e);
        }
    }
}
