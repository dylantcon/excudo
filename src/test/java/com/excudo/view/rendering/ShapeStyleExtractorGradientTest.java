package com.excudo.view.rendering;

import com.excudo.core.model.PPTXDocument;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.SlideShape;
import com.excudo.core.rendering.surface.Graphics2DRenderSurface;
import com.excudo.core.rendering.surface.SurfacePaint;
import com.excudo.core.utils.XMLFactoryProvider;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Pins the A2 fill semantics on the direct-fill path: real gradient
 * types (radial via a:path), OOXML angle convention on a:lin, color
 * modifiers on direct fills and gradient stops, preset pattern fills,
 * and picture fills. Each expectation is derived from the parity ground
 * truth (PowerPoint PDF export of the fills-* corpus decks) — see the
 * per-test comments.
 */
public class ShapeStyleExtractorGradientTest {

    // ==================== radial gradients ====================

    /**
     * a:path path="circle" must produce a radial paint, not a linear one.
     * PowerPoint's PDF export renders circle-path gradients as a circle
     * centered on the shape with radius = half-diagonal (ShadingType 3,
     * Coords [0 0 0 0 0 1], uniform matrix centered on the shape rect —
     * measured in parity-corpus/fills-gradient/deck.pdf), which is the
     * CIRCLE geometry of SurfacePaint.RadialGradient.
     */
    @Test
    public void circlePathProducesCenteredCircularRadial() throws Exception {
        SurfacePaint fill = resolveFill(shapeXml("""
            <a:gradFill>
                <a:gsLst>
                    <a:gs pos="0"><a:srgbClr val="FFFFFF"/></a:gs>
                    <a:gs pos="100000"><a:srgbClr val="2E74B5"/></a:gs>
                </a:gsLst>
                <a:path path="circle">
                    <a:fillToRect l="50000" t="50000" r="50000" b="50000"/>
                </a:path>
            </a:gradFill>
            """));

        assertTrue("circle path should produce RadialGradient, got "
                + fill.getClass().getSimpleName(),
            fill instanceof SurfacePaint.RadialGradient);
        SurfacePaint.RadialGradient rg = (SurfacePaint.RadialGradient) fill;
        assertEquals(SurfacePaint.RadialGradient.Geometry.CIRCLE, rg.geometry());
        assertEquals(0.5, rg.centerX(), 1e-9);
        assertEquals(0.5, rg.centerY(), 1e-9);

        // Stop ends preserved: white at the focus, blue at the boundary.
        List<SurfacePaint.LinearGradient.Stop> stops = rg.stops();
        assertEquals(0.0, stops.get(0).position(), 1e-9);
        assertEquals(0xFFFFFFFF, stops.get(0).color().argb());
        assertEquals(1.0, stops.get(stops.size() - 1).position(), 1e-9);
        assertEquals(0xFF2E74B5, stops.get(stops.size() - 1).color().argb());
    }

    /** a:path path="rect" is approximated as a bbox-scaled (elliptical)
     *  radial — contours follow the shape bounds. */
    @Test
    public void rectPathProducesEllipticalRadial() throws Exception {
        SurfacePaint fill = resolveFill(shapeXml("""
            <a:gradFill>
                <a:gsLst>
                    <a:gs pos="0"><a:srgbClr val="E2EFDA"/></a:gs>
                    <a:gs pos="100000"><a:srgbClr val="375623"/></a:gs>
                </a:gsLst>
                <a:path path="rect">
                    <a:fillToRect l="50000" t="50000" r="50000" b="50000"/>
                </a:path>
            </a:gradFill>
            """));

        assertTrue(fill instanceof SurfacePaint.RadialGradient);
        SurfacePaint.RadialGradient rg = (SurfacePaint.RadialGradient) fill;
        assertEquals(SurfacePaint.RadialGradient.Geometry.ELLIPSE, rg.geometry());
        assertEquals(0.5, rg.centerX(), 1e-9);
        assertEquals(0.5, rg.centerY(), 1e-9);
    }

    // ==================== linear angle convention ====================

    /**
     * OOXML a:lin/@ang is clockwise from east: ang=0 points RIGHT, so the
     * first stop sits on the LEFT edge. Ground truth: fills-gradient
     * slide 1 cell 1 (ang="0", C00000 -> 1F4E79) rasterises red on the
     * left, blue on the right. The old renderer mapped ang=0 to
     * top-to-bottom.
     */
    @Test
    public void linearAngle0PointsEast() throws Exception {
        SurfacePaint fill = resolveFill(shapeXml("""
            <a:gradFill>
                <a:gsLst>
                    <a:gs pos="0"><a:srgbClr val="C00000"/></a:gs>
                    <a:gs pos="100000"><a:srgbClr val="1F4E79"/></a:gs>
                </a:gsLst>
                <a:lin ang="0" scaled="0"/>
            </a:gradFill>
            """));

        assertTrue(fill instanceof SurfacePaint.LinearGradient);
        SurfacePaint.LinearGradient lg = (SurfacePaint.LinearGradient) fill;
        assertEquals(0.0, lg.startX(), 1e-6);
        assertEquals(0.5, lg.startY(), 1e-6);
        assertEquals(1.0, lg.endX(), 1e-6);
        assertEquals(0.5, lg.endY(), 1e-6);
    }

    /** ang=5400000 (90 degrees clockwise) points DOWN: first stop on top. */
    @Test
    public void linearAngle90PointsSouth() throws Exception {
        SurfacePaint fill = resolveFill(shapeXml("""
            <a:gradFill>
                <a:gsLst>
                    <a:gs pos="0"><a:srgbClr val="C00000"/></a:gs>
                    <a:gs pos="100000"><a:srgbClr val="1F4E79"/></a:gs>
                </a:gsLst>
                <a:lin ang="5400000" scaled="0"/>
            </a:gradFill>
            """));

        SurfacePaint.LinearGradient lg = (SurfacePaint.LinearGradient) fill;
        assertEquals(0.5, lg.startX(), 1e-6);
        assertEquals(0.0, lg.startY(), 1e-6);
        assertEquals(0.5, lg.endX(), 1e-6);
        assertEquals(1.0, lg.endY(), 1e-6);
    }

    /**
     * scaled="1" interprets the angle in a squashed-to-square space:
     * the absolute direction obeys tan(theta') = (w/h)*tan(theta).
     * Measured from the ground-truth PDF (fills-gradient slide 2 cell 1:
     * ang=45deg scaled=1 on a 222x175.5pt shape exports a gradient axis
     * at 51.7deg = atan(1.2649 * tan 45)). For a 2:1 shape the 45-degree
     * axis therefore runs at atan(2) and spans the shape's projection.
     */
    @Test
    public void linearAngle45ScaledSkewsWithAspect() throws Exception {
        SurfacePaint fill = resolveFill(shapeXml("""
            <a:gradFill>
                <a:gsLst>
                    <a:gs pos="0"><a:srgbClr val="C00000"/></a:gs>
                    <a:gs pos="100000"><a:srgbClr val="1F4E79"/></a:gs>
                </a:gsLst>
                <a:lin ang="2700000" scaled="1"/>
            </a:gradFill>
            """), 200, 100);

        SurfacePaint.LinearGradient lg = (SurfacePaint.LinearGradient) fill;
        // direction (dx, dy) proportional to (h*cos45, w*sin45) -> (1, 2)/sqrt(5)
        double dx = 1 / Math.sqrt(5), dy = 2 / Math.sqrt(5);
        // extent = half the shape's projection onto the axis
        double ext = 0.5 * (200 * dx + 100 * dy);
        assertEquals(0.5 - ext * dx / 200, lg.startX(), 1e-6);
        assertEquals(0.5 - ext * dy / 100, lg.startY(), 1e-6);
        assertEquals(0.5 + ext * dx / 200, lg.endX(), 1e-6);
        assertEquals(0.5 + ext * dy / 100, lg.endY(), 1e-6);
    }

    // ==================== modifiers on direct fills ====================

    /** Direct spPr solidFill colors must run the full modifier pipeline
     *  (lumMod shown here; exact value pinned in ColorTransformsTest). */
    @Test
    public void solidFillAppliesLumMod() throws Exception {
        SurfacePaint fill = resolveFill(shapeXml("""
            <a:solidFill>
                <a:srgbClr val="4F81BD"><a:lumMod val="75000"/></a:srgbClr>
            </a:solidFill>
            """));
        assertTrue(fill instanceof SurfacePaint.Solid);
        assertEquals(0xFF376092, ((SurfacePaint.Solid) fill).argb());
    }

    /** Gradient stop colors run through the same modifier pipeline. */
    @Test
    public void gradientStopAppliesShade() throws Exception {
        SurfacePaint fill = resolveFill(shapeXml("""
            <a:gradFill>
                <a:gsLst>
                    <a:gs pos="0"><a:srgbClr val="4F81BD"><a:shade val="50000"/></a:srgbClr></a:gs>
                    <a:gs pos="100000"><a:srgbClr val="FFFFFF"/></a:gs>
                </a:gsLst>
                <a:lin ang="0" scaled="0"/>
            </a:gradFill>
            """));
        SurfacePaint.LinearGradient lg = (SurfacePaint.LinearGradient) fill;
        // linear-sRGB shade (see ColorTransformsTest.shade50_accent1)
        assertEquals(0xFF385D8A, lg.stops().get(0).color().argb());
    }

    // ==================== pattern fills ====================

    /**
     * a:pattFill must produce a pattern paint with fg/bg colors, never
     * fall through to transparent. Tile bits pinned against PowerPoint's
     * own rasterisation: its PDF export tiles preset patterns as 16x16
     * images (2x-doubled 8x8 bitmaps); ltDnDiag extracted from
     * parity-corpus/fills-pattern/deck.pdf is the classic single-pixel
     * down-diagonal.
     */
    @Test
    public void pattFillProducesPatternPaint() throws Exception {
        SurfacePaint fill = resolveFill(shapeXml("""
            <a:pattFill prst="ltDnDiag">
                <a:fgClr><a:srgbClr val="1F4E79"/></a:fgClr>
                <a:bgClr><a:srgbClr val="FFF2CC"/></a:bgClr>
            </a:pattFill>
            """));

        assertTrue("pattFill should produce PatternFill, got "
                + fill.getClass().getSimpleName(),
            fill instanceof SurfacePaint.PatternFill);
        SurfacePaint.PatternFill p = (SurfacePaint.PatternFill) fill;
        assertEquals(0xFF1F4E79, p.foreground().argb());
        assertEquals(0xFFFFF2CC, p.background().argb());
        assertEquals(bits(
            "#...#...",
            ".#...#..",
            "..#...#.",
            "...#...#",
            "#...#...",
            ".#...#..",
            "..#...#.",
            "...#...#"), p.bits());
    }

    /** Every OOXML preset pattern name must resolve to a non-empty,
     *  non-full tile (a degenerate tile would render as a solid). */
    @Test
    public void allPresetPatternsHaveRealTiles() throws Exception {
        String[] presets = {
            "pct5", "pct10", "pct20", "pct25", "pct30", "pct40", "pct50",
            "pct60", "pct70", "pct75", "pct80", "pct90",
            "horz", "vert", "ltHorz", "ltVert", "dkHorz", "dkVert",
            "narHorz", "narVert", "dashHorz", "dashVert",
            "cross", "dnDiag", "upDiag", "ltDnDiag", "ltUpDiag",
            "dkDnDiag", "dkUpDiag", "wdDnDiag", "wdUpDiag",
            "dashDnDiag", "dashUpDiag", "diagCross",
            "smCheck", "lgCheck", "smGrid", "lgGrid", "dotGrid",
            "smConfetti", "lgConfetti", "horzBrick", "diagBrick",
            "solidDmnd", "openDmnd", "dotDmnd", "plaid", "sphere",
            "weave", "divot", "shingle", "wave", "trellis", "zigZag"};
        for (String prst : presets) {
            SurfacePaint fill = resolveFill(shapeXml(
                "<a:pattFill prst=\"" + prst + "\">"
                + "<a:fgClr><a:srgbClr val=\"000000\"/></a:fgClr>"
                + "<a:bgClr><a:srgbClr val=\"FFFFFF\"/></a:bgClr>"
                + "</a:pattFill>"));
            assertTrue(prst + " should be a PatternFill",
                fill instanceof SurfacePaint.PatternFill);
            long bits = ((SurfacePaint.PatternFill) fill).bits();
            int on = Long.bitCount(bits);
            assertTrue(prst + " tile must not be empty", on > 0);
            assertTrue(prst + " tile must not be solid", on < 64);
        }
    }

    // ==================== picture fills ====================

    /**
     * a:blipFill in spPr resolves the relationship to the embedded image
     * and produces an ImageFill. Uses the committed fills-picture corpus
     * deck (rId2 -> ppt/media/image1.png, 200x150) and the AWT surface
     * for decoding.
     */
    @Test
    public void blipFillStretchProducesImageFill() throws Exception {
        PPTXDocument doc = loadPictureDeck();
        SlideRenderContext ctx = new SlideRenderContext(
            null, null, doc, 1, java.util.Map.of(), null);
        Graphics2DRenderSurface surface = new Graphics2DRenderSurface(64, 64);

        SlideShape shape = parseShape(pictureShapeXml(
            "<a:stretch><a:fillRect/></a:stretch>"), 1905000, 1428750);
        SurfacePaint fill = ShapeStyleExtractor.resolveFillColor(shape, ctx, surface);

        assertTrue("blipFill should produce ImageFill, got "
                + fill.getClass().getSimpleName(),
            fill instanceof SurfacePaint.ImageFill);
        SurfacePaint.ImageFill img = (SurfacePaint.ImageFill) fill;
        assertFalse(img.tile());
        assertNotNull(img.image());
    }

    /**
     * a:tile with sx/sy tiles at native image size scaled by sx/sy.
     * Shape is authored 200x150 px (in EMU) and the source image is
     * 200x150 px, so sx=sy=50% must give tile fractions of exactly 0.5.
     */
    @Test
    public void blipFillTileComputesTileFractions() throws Exception {
        PPTXDocument doc = loadPictureDeck();
        SlideRenderContext ctx = new SlideRenderContext(
            null, null, doc, 1, java.util.Map.of(), null);
        Graphics2DRenderSurface surface = new Graphics2DRenderSurface(64, 64);

        SlideShape shape = parseShape(pictureShapeXml(
            "<a:tile tx=\"0\" ty=\"0\" sx=\"50000\" sy=\"50000\" flip=\"none\" algn=\"tl\"/>"),
            1905000, 1428750); // 200 x 150 px in EMU (9525 EMU/px)
        SurfacePaint fill = ShapeStyleExtractor.resolveFillColor(shape, ctx, surface);

        assertTrue(fill instanceof SurfacePaint.ImageFill);
        SurfacePaint.ImageFill img = (SurfacePaint.ImageFill) fill;
        assertTrue(img.tile());
        assertEquals(0.5, img.tileFracW(), 1e-6);
        assertEquals(0.5, img.tileFracH(), 1e-6);
    }

    // ========== HELPERS ==========

    private static long bits(String... rows) {
        long v = 0;
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                if (rows[y].charAt(x) == '#') v |= 1L << (y * 8 + x);
            }
        }
        return v;
    }

    private static PPTXDocument loadPictureDeck() throws Exception {
        java.io.File f = new java.io.File("parity-corpus/fills-picture/deck.pptx");
        if (!f.exists()) f = new java.io.File("../../../parity-corpus/fills-picture/deck.pptx");
        assertTrue("fixture missing: " + f.getAbsolutePath(), f.exists());
        return PPTXDocument.loadFromZip(f);
    }

    private static String shapeXml(String fillXml) {
        return """
            <p:sp xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                  xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                  xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                <p:nvSpPr><p:cNvPr id="4" name="Rect"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>
                <p:spPr>
                    <a:xfrm><a:off x="0" y="0"/><a:ext cx="100" cy="100"/></a:xfrm>
                    %s
                </p:spPr>
            </p:sp>
            """.formatted(fillXml);
    }

    private static String pictureShapeXml(String tileOrStretch) {
        return shapeXml("<a:blipFill><a:blip r:embed=\"rId2\"/>" + tileOrStretch + "</a:blipFill>");
    }

    /** Resolve fill for a square (100x100 EMU) shape with no context. */
    private static SurfacePaint resolveFill(String xml) throws Exception {
        return resolveFill(xml, 100, 100);
    }

    private static SurfacePaint resolveFill(String xml, long cx, long cy) throws Exception {
        return ShapeStyleExtractor.resolveFillColor(parseShape(xml, cx, cy), null);
    }

    private static SlideShape parseShape(String xml, long cx, long cy) throws Exception {
        Document doc = XMLFactoryProvider.parseDocument(xml);
        Element el = doc.getDocumentElement();
        return new SlideShape(99, "TestShape", SlideShape.ShapeType.RECTANGLE, null,
            new ShapeGeometry(0, 0, cx, cy), el, null);
    }
}
