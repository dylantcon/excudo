package com.excudo.core.model;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;

/**
 * Parsed {@code a:tbl} payload of a {@code p:graphicFrame} (ECMA-376
 * 21.1.3). Structural table data — grid column widths, row heights,
 * merge spans, per-cell {@code a:tcPr} margins / anchor / fill / edge
 * borders — is parsed eagerly and validated here; malformed table XML
 * throws rather than producing a silently empty or half-parsed table.
 *
 * <p>Cell text bodies stay as raw {@code a:txBody} DOM elements (the
 * same pattern as {@link SlideShape#getXmlElement()}): the renderer
 * feeds them through the existing TextBodyExtractor / TextMeasurer /
 * TextPainter pipeline, which lives downstream of this compile step.
 *
 * <p>Merge encoding per the spec: every grid slot has an {@code a:tc}.
 * An anchor cell carries {@code gridSpan}/{@code rowSpan} &gt; 1; the
 * covered slots carry {@code hMerge="1"} (same row), {@code vMerge="1"}
 * (rows below), or both. {@link #anchorOf} resolves any slot to its
 * anchor; the covering is validated at parse time — a continuation cell
 * with no covering anchor, an out-of-bounds span, or two anchors
 * claiming one slot all throw.
 */
public final class TableModel {

    /** {@code a:tblPr} flags plus the {@code a:tableStyleId} GUID (null when absent). */
    public record Properties(boolean firstRow, boolean lastRow, boolean firstCol,
                             boolean lastCol, boolean bandRow, boolean bandCol,
                             String styleId) {}

    /** One {@code a:tr}: minimum row height plus one Cell per grid column. */
    public record Row(long heightEmu, List<Cell> cells) {}

    /**
     * One {@code a:tc}. Margin fields are null when tcPr omits them —
     * the OOXML defaults (marL/marR 91440, marT/marB 45720 EMU) match
     * the text pipeline's bodyPr inset defaults, so consumers may leave
     * null untouched. {@code fill} is the tcPr fill choice element
     * ({@code a:solidFill} / {@code a:noFill} / ...), {@code lnL..lnB}
     * are the per-edge {@code a:ln} border elements; all nullable.
     */
    public record Cell(int gridSpan, int rowSpan, boolean hMerge, boolean vMerge,
                       Element txBody, Long marLEmu, Long marREmu, Long marTEmu,
                       Long marBEmu, String anchor, Element fill,
                       Element lnL, Element lnR, Element lnT, Element lnB) {

        /** True for hMerge/vMerge continuation slots covered by another cell's span. */
        public boolean isMergeContinuation() {
            return hMerge || vMerge;
        }
    }

    private static final List<String> FILL_CHOICES = List.of(
        "noFill", "solidFill", "gradFill", "blipFill", "pattFill", "grpFill");

    private final Properties properties;
    private final long[] gridColWidthsEmu;
    private final List<Row> rows;
    // anchor coordinates per grid slot, computed and validated at parse
    private final int[][] anchorRow;
    private final int[][] anchorCol;

    private TableModel(Properties properties, long[] gridColWidthsEmu, List<Row> rows) {
        this.properties = properties;
        this.gridColWidthsEmu = gridColWidthsEmu;
        this.rows = rows;
        this.anchorRow = new int[rows.size()][gridColWidthsEmu.length];
        this.anchorCol = new int[rows.size()][gridColWidthsEmu.length];
        resolveMerges();
    }

    public Properties getProperties() { return properties; }
    public int getColumnCount() { return gridColWidthsEmu.length; }
    public int getRowCount() { return rows.size(); }
    public long getColumnWidthEmu(int col) { return gridColWidthsEmu[col]; }
    public List<Row> getRows() { return rows; }
    public Cell getCell(int row, int col) { return rows.get(row).cells().get(col); }

    /** Total grid width — the sum of the gridCol widths. The frame's
     *  {@code p:xfrm} ext can disagree after column resizes; PowerPoint
     *  lays out from the grid. */
    public long getTotalWidthEmu() {
        long sum = 0;
        for (long w : gridColWidthsEmu) sum += w;
        return sum;
    }

    /** Total grid height — the sum of the row heights. */
    public long getTotalHeightEmu() {
        long sum = 0;
        for (Row r : rows) sum += r.heightEmu();
        return sum;
    }

    /** The (row, col) of the anchor cell covering the given grid slot;
     *  the slot itself when it is not part of a merge. */
    public int[] anchorOf(int row, int col) {
        return new int[]{ anchorRow[row][col], anchorCol[row][col] };
    }

    /** True iff the slot is its own anchor (renders fill/borders/text). */
    public boolean isAnchor(int row, int col) {
        return anchorRow[row][col] == row && anchorCol[row][col] == col;
    }

    // ====================================================================
    // Parsing
    // ====================================================================

    /**
     * Parse an {@code a:tbl} element. Throws {@link IllegalArgumentException}
     * on malformed XML: missing/empty {@code a:tblGrid}, non-positive
     * column widths, unparseable row heights, a row whose {@code a:tc}
     * count disagrees with the grid, spans &lt; 1, or an inconsistent
     * merge covering.
     */
    public static TableModel parse(Element tbl) {
        if (tbl == null || !"tbl".equals(localName(tbl))) {
            throw new IllegalArgumentException("expected an a:tbl element, got "
                + (tbl == null ? "null" : tbl.getTagName()));
        }

        Properties props = parseProperties(childByLocalName(tbl, "tblPr"));

        Element grid = childByLocalName(tbl, "tblGrid");
        if (grid == null) {
            throw new IllegalArgumentException("a:tbl has no a:tblGrid");
        }
        List<Element> gridCols = childrenByLocalName(grid, "gridCol");
        if (gridCols.isEmpty()) {
            throw new IllegalArgumentException("a:tblGrid has no a:gridCol columns");
        }
        long[] widths = new long[gridCols.size()];
        for (int i = 0; i < gridCols.size(); i++) {
            widths[i] = requiredLong(gridCols.get(i), "w", "a:gridCol[" + i + "]");
            if (widths[i] <= 0) {
                throw new IllegalArgumentException(
                    "a:gridCol[" + i + "] width must be positive, got " + widths[i]);
            }
        }

        List<Element> trs = childrenByLocalName(tbl, "tr");
        if (trs.isEmpty()) {
            throw new IllegalArgumentException("a:tbl has no a:tr rows");
        }
        List<Row> rows = new ArrayList<>(trs.size());
        for (int r = 0; r < trs.size(); r++) {
            Element tr = trs.get(r);
            long h = requiredLong(tr, "h", "a:tr[" + r + "]");
            if (h < 0) {
                throw new IllegalArgumentException("a:tr[" + r + "] height must be >= 0, got " + h);
            }
            List<Element> tcs = childrenByLocalName(tr, "tc");
            if (tcs.size() != widths.length) {
                throw new IllegalArgumentException("a:tr[" + r + "] has " + tcs.size()
                    + " a:tc cells but the grid declares " + widths.length
                    + " columns (every grid slot needs a cell, merges included)");
            }
            List<Cell> cells = new ArrayList<>(tcs.size());
            for (int c = 0; c < tcs.size(); c++) {
                cells.add(parseCell(tcs.get(c), r, c));
            }
            rows.add(new Row(h, List.copyOf(cells)));
        }

        return new TableModel(props, widths, List.copyOf(rows));
    }

    private static Properties parseProperties(Element tblPr) {
        if (tblPr == null) {
            // tblPr is optional per CT_Table; all flags default off.
            return new Properties(false, false, false, false, false, false, null);
        }
        String styleId = null;
        Element styleIdEl = childByLocalName(tblPr, "tableStyleId");
        if (styleIdEl != null) {
            String text = styleIdEl.getTextContent();
            if (text != null && !text.isBlank()) styleId = text.trim();
        }
        return new Properties(
            boolAttr(tblPr, "firstRow"), boolAttr(tblPr, "lastRow"),
            boolAttr(tblPr, "firstCol"), boolAttr(tblPr, "lastCol"),
            boolAttr(tblPr, "bandRow"), boolAttr(tblPr, "bandCol"),
            styleId);
    }

    private static Cell parseCell(Element tc, int r, int c) {
        String at = "a:tc[" + r + "," + c + "]";
        int gridSpan = optionalSpan(tc, "gridSpan", at);
        int rowSpan = optionalSpan(tc, "rowSpan", at);
        boolean hMerge = boolAttr(tc, "hMerge");
        boolean vMerge = boolAttr(tc, "vMerge");

        Element txBody = childByLocalName(tc, "txBody");
        Element tcPr = childByLocalName(tc, "tcPr");

        Long marL = null, marR = null, marT = null, marB = null;
        String anchor = null;
        Element fill = null, lnL = null, lnR = null, lnT = null, lnB = null;
        if (tcPr != null) {
            marL = optionalLong(tcPr, "marL", at);
            marR = optionalLong(tcPr, "marR", at);
            marT = optionalLong(tcPr, "marT", at);
            marB = optionalLong(tcPr, "marB", at);
            String anchorAttr = tcPr.getAttribute("anchor");
            if (!anchorAttr.isEmpty()) anchor = anchorAttr;
            for (String choice : FILL_CHOICES) {
                fill = childByLocalName(tcPr, choice);
                if (fill != null) break;
            }
            lnL = childByLocalName(tcPr, "lnL");
            lnR = childByLocalName(tcPr, "lnR");
            lnT = childByLocalName(tcPr, "lnT");
            lnB = childByLocalName(tcPr, "lnB");
        }
        return new Cell(gridSpan, rowSpan, hMerge, vMerge, txBody,
            marL, marR, marT, marB, anchor, fill, lnL, lnR, lnT, lnB);
    }

    /**
     * Walk every anchor's span, marking covered slots. Throws when a
     * span leaves the grid, two spans claim the same slot, or a
     * continuation cell ends up uncovered.
     */
    private void resolveMerges() {
        int rowCount = rows.size();
        int colCount = gridColWidthsEmu.length;
        boolean[][] covered = new boolean[rowCount][colCount];

        for (int r = 0; r < rowCount; r++) {
            for (int c = 0; c < colCount; c++) {
                Cell cell = getCell(r, c);
                if (cell.isMergeContinuation()) continue;
                if (r + cell.rowSpan() > rowCount || c + cell.gridSpan() > colCount) {
                    throw new IllegalArgumentException("a:tc[" + r + "," + c + "] span "
                        + cell.gridSpan() + "x" + cell.rowSpan() + " leaves the "
                        + colCount + "x" + rowCount + " grid");
                }
                for (int rr = r; rr < r + cell.rowSpan(); rr++) {
                    for (int cc = c; cc < c + cell.gridSpan(); cc++) {
                        if (covered[rr][cc]) {
                            throw new IllegalArgumentException("grid slot [" + rr + "," + cc
                                + "] is claimed by two merge spans");
                        }
                        if ((rr != r || cc != c) && !getCell(rr, cc).isMergeContinuation()) {
                            throw new IllegalArgumentException("a:tc[" + rr + "," + cc
                                + "] is covered by the span of [" + r + "," + c
                                + "] but is not marked hMerge/vMerge");
                        }
                        covered[rr][cc] = true;
                        anchorRow[rr][cc] = r;
                        anchorCol[rr][cc] = c;
                    }
                }
            }
        }

        for (int r = 0; r < rowCount; r++) {
            for (int c = 0; c < colCount; c++) {
                if (!covered[r][c]) {
                    throw new IllegalArgumentException("a:tc[" + r + "," + c
                        + "] is marked hMerge/vMerge but no anchor span covers it");
                }
            }
        }
    }

    // ====================================================================
    // DOM helpers (namespace-prefix tolerant, plain DOM — no XPath)
    // ====================================================================

    private static String localName(Element el) {
        String local = el.getLocalName();
        if (local != null) return local;
        String tag = el.getTagName();
        int colon = tag.indexOf(':');
        return colon < 0 ? tag : tag.substring(colon + 1);
    }

    private static Element childByLocalName(Element parent, String local) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element el && local.equals(localName(el))) return el;
        }
        return null;
    }

    private static List<Element> childrenByLocalName(Element parent, String local) {
        List<Element> out = new ArrayList<>();
        NodeList kids = parent.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            if (kids.item(i) instanceof Element el && local.equals(localName(el))) {
                out.add(el);
            }
        }
        return out;
    }

    private static boolean boolAttr(Element el, String name) {
        String v = el.getAttribute(name);
        return "1".equals(v) || "true".equalsIgnoreCase(v);
    }

    private static long requiredLong(Element el, String attr, String at) {
        String v = el.getAttribute(attr);
        if (v.isEmpty()) {
            throw new IllegalArgumentException(at + " is missing required @" + attr);
        }
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(at + " @" + attr + " is not a number: " + v, e);
        }
    }

    private static Long optionalLong(Element el, String attr, String at) {
        String v = el.getAttribute(attr);
        if (v.isEmpty()) return null;
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(at + " @" + attr + " is not a number: " + v, e);
        }
    }

    private static int optionalSpan(Element el, String attr, String at) {
        String v = el.getAttribute(attr);
        if (v.isEmpty()) return 1;
        int parsed;
        try {
            parsed = Integer.parseInt(v);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(at + " @" + attr + " is not a number: " + v, e);
        }
        if (parsed < 1) {
            throw new IllegalArgumentException(at + " @" + attr + " must be >= 1, got " + parsed);
        }
        return parsed;
    }
}
