package com.excudo.view.rendering.tables;

import com.excudo.core.model.PPTXDocument;
import com.excudo.core.model.ParsedSlideData;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.ShapeRegistry;
import com.excudo.core.model.SlideShape;
import com.excudo.core.model.TableModel;
import com.excudo.core.model.TimingTree;
import com.excudo.core.orchestration.PPTXOrchestratorImpl;
import com.excudo.core.rendering.surface.Graphics2DRenderSurface;
import com.excudo.view.rendering.HeadlessSlideRenderer;
import com.excudo.view.rendering.SlideRenderer;
import org.junit.Test;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * A6 table rendering, pinned to PowerPoint's PDF export of the
 * tables-basic and tables-merges parity decks (960x540pt content
 * streams; probe pixels are pt * 4/3 at the 1280x720 render size).
 *
 * <p>Calibration extracted from the truth PDFs:
 * <ul>
 *   <li>Style {5C22544A-7EE6-4342-B048-85BDC9FD1C3A} (Medium Style 2 -
 *       Accent 1, the python-pptx default; the decks' tableStyles.xml
 *       is empty so resolution comes from the built-in definition):
 *       firstRow fill = solid accent1 #4F81BD, band1H = accent1 tint
 *       40% = #D0D8E8, wholeTbl/band2H = accent1 tint 20% = #E9EDF4 —
 *       tint applied in linearized sRGB, PDF values 0.31/0.506/0.741,
 *       0.816/0.847/0.91, 0.914/0.929/0.957 exactly.</li>
 *   <li>Borders: every inside/outer edge lt1 white at 1pt ('1 w 1 G');
 *       the firstRow bottom edge is 3pt ('3 w'). Border segments skip
 *       merge interiors (x=568.8pt strokes 435.3-363.1 and 223.7-152.5
 *       around the 2x2 block in the merges truth).</li>
 *   <li>firstRow text: bold + lt1 (PDF font BCDEEE+Calibri-Bold, '1 g');
 *       body text dk1 ('0 g', BCDFEE+Calibri).</li>
 *   <li>Fills paint once over the full merged span (the tall merge is
 *       one 177.6x280.8pt rect at band1H in the truth stream).</li>
 * </ul>
 *
 * Fail-first: red against the pre-A6 renderer, where TABLE shapes fall
 * through to GeometricShapeRenderer's default no-fill rect.
 */
public class TableRenderTest {

    private static final File TABLES_BASIC =
        new File("parity-corpus/tables-basic/deck.pptx");
    private static final File TABLES_MERGES =
        new File("parity-corpus/tables-merges/deck.pptx");

    private static final int W = 1280, H = 720;

    // Truth fill colors (see class javadoc).
    private static final int[] ACCENT1_SOLID = { 0x4F, 0x81, 0xBD };
    private static final int[] BAND1_TINT40  = { 0xD0, 0xD8, 0xE8 };
    private static final int[] WHOLE_TINT20  = { 0xE9, 0xED, 0xF4 };

    // ===================================================================
    // tables-basic: style fills
    // ===================================================================

    @Test
    public void tablesBasic_headerRowFillsSolidAccent1() throws Exception {
        BufferedImage img = renderDeck(TABLES_BASIC, 1);
        // Header row spans y 36..98.4pt; probe right of the "Region" text.
        assertColor(img, px(400), px(67.2), ACCENT1_SOLID, "header cell fill");
        assertColor(img, px(750), px(67.2), ACCENT1_SOLID, "header fill, Q2 column");
    }

    @Test
    public void tablesBasic_bandedRowsAlternateTint40Tint20() throws Exception {
        BufferedImage img = renderDeck(TABLES_BASIC, 1);
        assertColor(img, px(400), px(129.6), BAND1_TINT40, "data row 1 = band1H tint40");
        assertColor(img, px(400), px(192.0), WHOLE_TINT20, "data row 2 = band2H -> wholeTbl tint20");
        assertColor(img, px(400), px(254.4), BAND1_TINT40, "data row 3 = band1H tint40");
        assertColor(img, px(400), px(316.8), WHOLE_TINT20, "data row 4 = band2H -> wholeTbl tint20");
    }

    // ===================================================================
    // tables-basic: borders
    // ===================================================================

    @Test
    public void tablesBasic_insideVerticalBordersAreWhiteAndCrossTheHeader() throws Exception {
        BufferedImage img = renderDeck(TABLES_BASIC, 1);
        // Column boundary at x=480pt, both in a data row and inside the
        // header (no merges here, so the boundary line crosses it). Each
        // border probe is paired with the fills flanking it so a blank
        // render (all white) cannot fake-pass the whitish assertion.
        assertWhitish(img, px(480), px(129.6), "insideV in data region");
        assertColor(img, px(474), px(129.6), BAND1_TINT40, "fill left of insideV");
        assertColor(img, px(486), px(129.6), BAND1_TINT40, "fill right of insideV");
        assertBorderOverAccent1(img, px(480), px(67.2), "insideV crossing the header row");
        assertColor(img, px(474), px(67.2), ACCENT1_SOLID, "header fill left of insideV");
        assertColor(img, px(486), px(67.2), ACCENT1_SOLID, "header fill right of insideV");
        assertWhitish(img, px(702), px(129.6), "second insideV");
    }

    @Test
    public void tablesBasic_headerBottomBorderIsThreePoints() throws Exception {
        BufferedImage img = renderDeck(TABLES_BASIC, 1);
        // The 3pt firstRow bottom border spans y 96.9..99.9pt; the 1pt
        // inside borders span only +-0.5pt around their boundary.
        assertWhitish(img, px(400), px(98.4), "header bottom border center");
        // 3pt width pins: clearly above and below the line are fills.
        assertColor(img, px(400), px(94.5), ACCENT1_SOLID, "just above the 3pt border");
        assertColor(img, px(400), px(102.8), BAND1_TINT40, "just below the 3pt border");
        // A plain inside boundary is only 1pt: 2pt off-center is fill again.
        assertWhitish(img, px(400), px(160.8), 240, "insideH row1/row2");
        assertColor(img, px(400), px(158.2), BAND1_TINT40, "2.6pt above insideH is fill");
        assertColor(img, px(400), px(163.4), WHOLE_TINT20, "2.6pt below insideH is fill");
    }

    @Test
    public void tablesBasic_outerBordersPaint() throws Exception {
        BufferedImage img = renderDeck(TABLES_BASIC, 1);
        // Every border probe pairs with the fill just inside it so a
        // blank render cannot fake-pass.
        assertWhitish(img, px(36), px(129.6), "outer left border");
        assertColor(img, px(41), px(129.6), BAND1_TINT40, "fill inside the left border");
        // Top border straddles the white background / header fill edge:
        // the inner-half pixel reads as a white-over-accent1 blend.
        assertBorderOverAccent1(img, px(400), px(36.2), "outer top border");
        assertColor(img, px(400), px(40), ACCENT1_SOLID, "header fill below the top border");
        // Bottom border: probe the pixel whose no-border reading would be
        // the tint20 fill (233) -- the blend reads distinctly whiter.
        assertWhitish(img, px(400), px(347.4), 240, "outer bottom border");
        assertColor(img, px(400), px(344), WHOLE_TINT20, "fill above the bottom border");
    }

    // ===================================================================
    // tables-basic: cell text through the shared pipeline
    // ===================================================================

    @Test
    public void tablesBasic_headerTextIsWhiteBoldOverAccent1() throws Exception {
        BufferedImage img = renderDeck(TABLES_BASIC, 1);
        // "Region" glyph box: x 43..120pt, glyph ink between the header
        // top border and the baseline at 54.6pt from the slide top. Both
        // white ink AND surrounding accent1 fill must be present -- a
        // blank white render has ink pixels but no fill pixels.
        int whiteInk = countPixels(img, px(43), px(43.5), px(120), px(54.0),
            (r, g, b) -> r >= 240 && g >= 240 && b >= 240);
        int fill = countPixels(img, px(43), px(43.5), px(120), px(54.0),
            (r, g, b) -> Math.abs(r - 0x4F) <= 12 && Math.abs(g - 0x81) <= 12
                && Math.abs(b - 0xBD) <= 12);
        assertTrue("header cell must paint white text ink over the accent1 fill, found "
            + whiteInk + " white / " + fill + " fill pixels",
            whiteInk > 10 && fill > 50);
    }

    @Test
    public void tablesBasic_dataTextIsDark() throws Exception {
        BufferedImage img = renderDeck(TABLES_BASIC, 1);
        // "North" glyph box: baseline 115.1pt from top, 14pt run.
        int darkInk = countPixels(img, px(43), px(104.5), px(110), px(115.0),
            (r, g, b) -> r < 120 && g < 120 && b < 120);
        assertTrue("data cells must paint dark (dk1) text, found " + darkInk
            + " dark pixels", darkInk > 10);
    }

    // ===================================================================
    // tables-merges: merge-aware fills and borders
    // ===================================================================

    @Test
    public void tablesMerges_mergedHeaderPaintsOneFillAndNoInteriorBorder() throws Exception {
        BufferedImage img = renderDeck(TABLES_MERGES, 1);
        // Header merge spans the full 36..924pt width at solid accent1.
        assertColor(img, px(600), px(71.25), ACCENT1_SOLID, "merged header fill");
        // The c0|c1 boundary at x=213.6pt must NOT paint inside the merge
        // (contrast with tablesBasic where insideV crosses the header).
        assertColor(img, px(213.6), px(71.25), ACCENT1_SOLID,
            "no insideV inside the merged header");
    }

    @Test
    public void tablesMerges_tallMergePaintsBand1AcrossAllRows() throws Exception {
        BufferedImage img = renderDeck(TABLES_MERGES, 1);
        // Tall merge (c0, rows 1-4) fills band1H from its anchor row.
        assertColor(img, px(100), px(150), BAND1_TINT40, "tall merge fill, row 1 area");
        assertColor(img, px(100), px(360), BAND1_TINT40, "tall merge fill, row 4 area");
        // Row boundaries do not paint inside the merge.
        assertColor(img, px(100), px(246.6), BAND1_TINT40, "no insideH inside tall merge");
        assertColor(img, px(100), px(316.8), BAND1_TINT40, "no insideH inside tall merge (2)");
    }

    @Test
    public void tablesMerges_blockMergePaintsOneFillNoInteriorBorders() throws Exception {
        BufferedImage img = renderDeck(TABLES_MERGES, 1);
        // 2x2 block at rows 2-3 x cols 2-3, anchored on a band2 row ->
        // wholeTbl tint20 across the whole block.
        assertColor(img, px(525), px(225), WHOLE_TINT20, "block merge fill");
        // Interior boundaries (x=568.8pt, y=246.6pt from top) skip the merge.
        assertColor(img, px(568.8), px(225), WHOLE_TINT20, "no insideV inside block");
        assertColor(img, px(500), px(246.6), WHOLE_TINT20, "no insideH inside block");
    }

    @Test
    public void tablesMerges_bordersResumeOutsideTheMerges() throws Exception {
        BufferedImage img = renderDeck(TABLES_MERGES, 1);
        // The x=568.8pt boundary strokes above (row 1) and below (row 4)
        // the block merge -- truth segments 435.3-363.1 and 223.7-152.5.
        // Fill pins flank every border probe so a blank render fails.
        assertWhitish(img, px(568.8), px(141.3), "c2|c3 boundary above the block");
        assertColor(img, px(560), px(141.3), BAND1_TINT40, "r1c2 fill left of boundary");
        assertColor(img, px(578), px(141.3), BAND1_TINT40, "r1c3 fill right of boundary");
        // Both flanks are tint20 (233): the 240 floor separates border
        // blend from bare fill.
        assertWhitish(img, px(568.8), px(352), 240, "c2|c3 boundary below the block");
        assertColor(img, px(560), px(352), WHOLE_TINT20, "r4c2 fill left of boundary");
        assertColor(img, px(578), px(352), WHOLE_TINT20, "r4c3 fill right of boundary");
        // And the c0|c1 boundary strokes the full data height alongside
        // the tall merge (line spans px 284.1-285.5; probe the mostly
        // covered pixel).
        assertWhitish(img, 284, px(250), 240, "c0|c1 boundary beside tall merge");
        assertColor(img, px(206), px(250), BAND1_TINT40, "tall-merge fill left of boundary");
    }

    @Test
    public void tablesMerges_perCellFillOverrideBeatsTheStyle() throws Exception {
        BufferedImage img = renderDeck(TABLES_MERGES, 1);
        // r1c4 carries tcPr solidFill srgbClr C00000 over a band1H row.
        assertColor(img, px(880), px(141.3), new int[]{ 0xC0, 0x00, 0x00 },
            "tcPr solidFill override");
    }

    // ===================================================================
    // Synthetic, style-free table: tcPr fills + explicit borders render
    // without any table style (unknown/absent styleId = no fills, no
    // borders — PowerPoint's No Style, No Grid behavior)
    // ===================================================================

    @Test
    public void syntheticTable_tcPrFillAndExplicitBorder_noStyleMeansNoDefaults() throws Exception {
        // 2 cols x 1 row at 9525 EMU/px: cells 200x100 px, table at origin.
        long pxEmu = 9525;
        String tblXml =
            "<a:tbl>"
            + "<a:tblGrid><a:gridCol w=\"" + (200 * pxEmu) + "\"/>"
            + "<a:gridCol w=\"" + (200 * pxEmu) + "\"/></a:tblGrid>"
            + "<a:tr h=\"" + (100 * pxEmu) + "\">"
            + "<a:tc><a:txBody><a:bodyPr/><a:p/></a:txBody>"
            + "<a:tcPr>"
            + "<a:lnB w=\"25400\"><a:solidFill><a:srgbClr val=\"000000\"/></a:solidFill></a:lnB>"
            + "<a:solidFill><a:srgbClr val=\"FF0000\"/></a:solidFill>"
            + "</a:tcPr></a:tc>"
            + "<a:tc><a:txBody><a:bodyPr/><a:p/></a:txBody><a:tcPr/></a:tc>"
            + "</a:tr></a:tbl>";

        BufferedImage img = renderSynthetic(tblXml, 400 * pxEmu, 100 * pxEmu);

        // tcPr fill paints; the unfilled neighbour shows the white slide bg.
        assertColor(img, 100, 50, new int[]{ 0xFF, 0x00, 0x00 }, "tcPr solidFill");
        assertColor(img, 300, 50, new int[]{ 0xFF, 0xFF, 0xFF }, "no style -> no fill");
        // No style: the cell boundary at x=200 must NOT paint a border.
        assertColor(img, 199, 50, new int[]{ 0xFF, 0x00, 0x00 }, "no border left of boundary");
        assertColor(img, 202, 50, new int[]{ 0xFF, 0xFF, 0xFF }, "no border right of boundary");
        // The explicit 2pt lnB paints black along the filled cell's bottom.
        int rgbB = img.getRGB(100, 100);
        assertTrue("explicit lnB must stroke dark, got #" + Integer.toHexString(rgbB),
            ((rgbB >> 16) & 0xFF) < 90 && ((rgbB >> 8) & 0xFF) < 90 && (rgbB & 0xFF) < 90);
        // ...and only along that cell: the neighbour's bottom stays clean.
        assertColor(img, 300, 99, new int[]{ 0xFF, 0xFF, 0xFF }, "lnB scoped to its cell");
    }

    // ===================================================================
    // Helpers
    // ===================================================================

    /** pt (PDF, 960x540) -> px (1280x720). */
    private static int px(double pt) {
        return (int) Math.round(pt * 4.0 / 3.0);
    }

    /**
     * Full-pipeline render of a corpus deck slide: the same
     * HeadlessSlideRenderer AWT path the parity harness drives.
     */
    private static BufferedImage renderDeck(File deck, int slide) throws Exception {
        assertTrue("Fixture missing (hard failure, never skip): " + deck.getAbsolutePath(),
            deck.isFile());
        // The render cache keys on deck REVISION, not deck identity, and
        // freshly loaded decks share revision 0 -- without clearing,
        // tables-merges probes read tables-basic pixels and vice versa.
        HeadlessSlideRenderer.clearRenderCache();
        PPTXOrchestratorImpl orch = new PPTXOrchestratorImpl();
        orch.loadPresentation(deck);
        PPTXDocument doc = orch.getContext().get().getDocument();

        File out = File.createTempFile("table-render-", ".png");
        out.deleteOnExit();
        new HeadlessSlideRenderer(W, H).renderToFile(doc, slide, out,
            null, orch.getClrMap(), orch.getBackgroundColorHex(slide), orch.getMasterStyles());
        BufferedImage img = javax.imageio.ImageIO.read(out);
        assertNotNull("render produced no image", img);
        return img;
    }

    /** Direct SlideRenderer dispatch over a synthetic TABLE shape, no theme. */
    private static BufferedImage renderSynthetic(String tblXml, long widthEmu, long heightEmu)
            throws Exception {
        String frameXml =
            "<p:graphicFrame xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\""
            + " xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\">"
            + "<p:nvGraphicFramePr><p:cNvPr id=\"7\" name=\"probe\"/>"
            + "<p:cNvGraphicFramePr/><p:nvPr/></p:nvGraphicFramePr>"
            + "<p:xfrm><a:off x=\"0\" y=\"0\"/>"
            + "<a:ext cx=\"" + widthEmu + "\" cy=\"" + heightEmu + "\"/></p:xfrm>"
            + "<a:graphic><a:graphicData"
            + " uri=\"http://schemas.openxmlformats.org/drawingml/2006/table\">"
            + tblXml
            + "</a:graphicData></a:graphic></p:graphicFrame>";

        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        Element frame = f.newDocumentBuilder()
            .parse(new ByteArrayInputStream(frameXml.getBytes(StandardCharsets.UTF_8)))
            .getDocumentElement();
        Element tbl = (Element) frame.getElementsByTagNameNS(
            "http://schemas.openxmlformats.org/drawingml/2006/main", "tbl").item(0);
        assertNotNull(tbl);

        SlideShape shape = new SlideShape(7, "probe", SlideShape.ShapeType.TABLE, null,
            new ShapeGeometry(0, 0, widthEmu, heightEmu, 0, false, false,
                null, Map.of(), null),
            frame, null, false, TableModel.parse(tbl));

        ShapeRegistry registry = new ShapeRegistry();
        registry.addShape(shape);
        ParsedSlideData data = new ParsedSlideData(registry, new TimingTree(),
            java.util.List.of(), null);

        Graphics2DRenderSurface surface = new Graphics2DRenderSurface(W, H);
        SlideRenderer renderer = new SlideRenderer(surface);
        renderer.renderSlide(data);
        return surface.toBufferedImage();
    }

    // ---------- pixel assertions ----------

    private interface RgbPredicate { boolean test(int r, int g, int b); }

    private static void assertColor(BufferedImage img, int x, int y, int[] rgb, String what) {
        int argb = img.getRGB(x, y);
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        int tol = 8;
        assertTrue(what + " at (" + x + "," + y + "): expected #"
            + String.format("%02X%02X%02X", rgb[0], rgb[1], rgb[2])
            + " +-" + tol + ", got #" + String.format("%06X", argb & 0xFFFFFF),
            Math.abs(r - rgb[0]) <= tol && Math.abs(g - rgb[1]) <= tol
                && Math.abs(b - rgb[2]) <= tol);
    }

    private static void assertWhitish(BufferedImage img, int x, int y, String what) {
        assertWhitish(img, x, y, 230, what);
    }

    /**
     * Border-pixel probe with an explicit floor: a 1pt border is only
     * 1.33px at this render size, so probe pixels are partial blends of
     * white line over the flanking fill. The floor is chosen per probe
     * so the assertion still discriminates: it must sit ABOVE what the
     * bare fill blend would read with no border painted.
     */
    private static void assertWhitish(BufferedImage img, int x, int y, int min, String what) {
        int argb = img.getRGB(x, y);
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        assertTrue(what + " at (" + x + "," + y + "): expected a white border pixel (>= "
            + min + "), got #" + String.format("%06X", argb & 0xFFFFFF),
            r >= min && g >= min && b >= min);
    }

    /**
     * A 1pt white border over the solid accent1 header blends to about
     * (196,213,233) at best -- no probe pixel is fully inside a 1.33px
     * line unless it is pixel-aligned. Floors sit well above the bare
     * fill (79,129,189), so a missing border still fails.
     */
    private static void assertBorderOverAccent1(BufferedImage img, int x, int y, String what) {
        int argb = img.getRGB(x, y);
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        assertTrue(what + " at (" + x + "," + y + "): expected a white-over-accent1 border "
            + "blend, got #" + String.format("%06X", argb & 0xFFFFFF),
            r >= 150 && g >= 170 && b >= 200);
    }

    private static int countPixels(BufferedImage img, int x0, int y0, int x1, int y1,
            RgbPredicate p) {
        int n = 0;
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                int argb = img.getRGB(x, y);
                if (p.test((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF)) n++;
            }
        }
        return n;
    }
}
