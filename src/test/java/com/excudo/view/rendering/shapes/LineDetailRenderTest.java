package com.excudo.view.rendering.shapes;

import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.SlideShape;
import org.junit.Test;
import org.w3c.dom.Element;

import java.awt.image.BufferedImage;
import java.util.Map;

import static com.excudo.view.rendering.shapes.GeometryRenderTestSupport.*;

/**
 * Pins a:ln stroke details against PowerPoint's PDF export of the
 * lines-dash-cap-join corpus deck (the ground truth):
 *
 * <ul>
 *   <li>Zero-extent line shapes (cy=0 or cx=0 EMU -- every horizontal or
 *       vertical connector python-pptx authors) must still stroke.</li>
 *   <li>prstDash arrays are exact multiples of the line width; the truth
 *       PDF emits {@code [4 3] 0 d} for "dash" at every width -- gap is
 *       3w, not 2w.</li>
 *   <li>cap: rnd/sq extend half a width past the endpoint, flat does not
 *       (PDF {@code 1 J}/{@code 2 J}/default).</li>
 *   <li>join: PowerPoint strokes with ROUND joins by default (every
 *       stroke in every truth PDF carries {@code 1 j} unless a:bevel or
 *       a:miter is authored).</li>
 *   <li>cmpd dbl/thickThin render as parallel filled bands, not one
 *       solid stroke (truth flattens them to ring fills; dbl = equal
 *       thirds, thickThin = 3:1:1 outer-to-inner).</li>
 * </ul>
 *
 * <p>Fail-first (verified red 2026-07-13 on the pre-A5 renderer): every
 * test except {@link #joinMiterSpikes} failed -- zero-extent shapes were
 * culled entirely, joins defaulted to miter, caps were ignored, and
 * compound rects stroked solid. joinMiterSpikes passes pre-A5 (miter was
 * the old default) and pins that explicit a:miter survives the
 * round-default change.
 */
public class LineDetailRenderTest {

    private static final String RED = "FF0000";

    /** cxnSp line preset from (100,300) spanning 300 px right, cy=0. */
    private static BufferedImage renderHLine(String lnXml) {
        Element el = cxnSpElement(
            "<a:xfrm><a:off x=\"" + emu(100) + "\" y=\"" + emu(300) + "\"/>"
            + "<a:ext cx=\"" + emu(300) + "\" cy=\"0\"/></a:xfrm>"
            + "<a:prstGeom prst=\"line\"><a:avLst/></a:prstGeom>" + lnXml);
        ShapeGeometry geom = new ShapeGeometry(emu(100), emu(300), emu(300), 0,
            0, false, false, "line", Map.of(), null);
        return render(shape(SlideShape.ShapeType.CONNECTION, geom, el));
    }

    private static String ln(int widthEmu, String inner) {
        return "<a:ln w=\"" + widthEmu + "\">"
            + "<a:solidFill><a:srgbClr val=\"" + RED + "\"/></a:solidFill>" + inner + "</a:ln>";
    }

    private static String lnCap(int widthEmu, String cap) {
        return "<a:ln w=\"" + widthEmu + "\" cap=\"" + cap + "\">"
            + "<a:solidFill><a:srgbClr val=\"" + RED + "\"/></a:solidFill></a:ln>";
    }

    // ========== zero extent ==========

    @Test
    public void zeroHeightLineStillStrokes() {
        // 3pt = 4 px wide, solid
        BufferedImage img = renderHLine(ln(38100, ""));
        assertFilled(img, 250, 300);
        assertFilled(img, 102, 300);
        assertEmpty(img, 250, 306);
    }

    @Test
    public void zeroWidthLineStillStrokes() {
        Element el = cxnSpElement(
            "<a:xfrm><a:off x=\"" + emu(300) + "\" y=\"" + emu(100) + "\"/>"
            + "<a:ext cx=\"0\" cy=\"" + emu(200) + "\"/></a:xfrm>"
            + "<a:prstGeom prst=\"line\"><a:avLst/></a:prstGeom>" + ln(38100, ""));
        ShapeGeometry geom = new ShapeGeometry(emu(300), emu(100), 0, emu(200),
            0, false, false, "line", Map.of(), null);
        BufferedImage img = render(shape(SlideShape.ShapeType.CONNECTION, geom, el));
        assertFilled(img, 300, 200);
        assertEmpty(img, 306, 200);
    }

    // ========== dash calibration ==========

    @Test
    public void dashPresetGapIsThreeWidths() {
        // 3pt = 4 px: truth dash array [16, 12] px -- ink [100,116] and
        // [128,144]. The old [4w, 2w] table put ink at [124,140], so the
        // (126,300) probe discriminates the calibrated gap.
        BufferedImage img = renderHLine(ln(38100, "<a:prstDash val=\"dash\"/>"));
        assertFilled(img, 110, 300);
        assertEmpty(img, 126, 300);
        assertFilled(img, 130, 300);
    }

    @Test
    public void sysDotIsOneOnOneOff() {
        // 3pt = 4 px: [4, 4] -- ink [100,104], [108,112]...
        BufferedImage img = renderHLine(ln(38100, "<a:prstDash val=\"sysDot\"/>"));
        assertFilled(img, 102, 300);
        assertEmpty(img, 106, 300);
        assertFilled(img, 110, 300);
    }

    // ========== caps (9pt = 12 px line, endpoint at x=400) ==========

    @Test
    public void capRoundExtendsPastEndpoint() {
        BufferedImage img = renderHLine(lnCap(114300, "rnd"));
        assertFilled(img, 397, 300);
        assertFilled(img, 403, 300); // inside the half-width-6 cap disc
    }

    @Test
    public void capSquareExtendsPastEndpoint() {
        BufferedImage img = renderHLine(lnCap(114300, "sq"));
        assertFilled(img, 403, 300);
        assertFilled(img, 403, 304);
    }

    @Test
    public void capFlatStopsAtEndpoint() {
        BufferedImage img = renderHLine(lnCap(114300, "flat"));
        assertFilled(img, 397, 300);
        assertEmpty(img, 403, 300);
    }

    // ========== joins ==========

    /**
     * Isosceles triangle, apex (300,100), 12pt (16 px) stroke, no fill.
     * Apex half-angle 33.69 deg: a miter join spikes to y=85.6 above the
     * apex, a round join caps at radius 8 (y=92), a bevel cuts flat at
     * y=95.6.
     */
    private static BufferedImage renderTriangle(String joinXml) {
        Element el = spElementRaw(
            "<a:xfrm><a:off x=\"" + emu(100) + "\" y=\"" + emu(100) + "\"/>"
            + "<a:ext cx=\"" + emu(400) + "\" cy=\"" + emu(300) + "\"/></a:xfrm>"
            + "<a:prstGeom prst=\"triangle\"><a:avLst/></a:prstGeom>"
            + "<a:noFill/>" + ln(152400, joinXml));
        ShapeGeometry geom = new ShapeGeometry(emu(100), emu(100), emu(400), emu(300),
            0, false, false, "triangle", Map.of(), null);
        return render(shape(SlideShape.ShapeType.TRIANGLE, geom, el));
    }

    @Test
    public void joinDefaultsToRound() {
        BufferedImage img = renderTriangle("");
        assertFilled(img, 300, 93); // inside the radius-8 round join
        assertEmpty(img, 300, 89);  // where only a miter spike would reach
    }

    @Test
    public void joinRound() {
        BufferedImage img = renderTriangle("<a:round/>");
        assertFilled(img, 300, 93);
        assertEmpty(img, 300, 89);
    }

    @Test
    public void joinBevelCutsTheApex() {
        BufferedImage img = renderTriangle("<a:bevel/>");
        assertFilled(img, 300, 98);
        assertEmpty(img, 300, 93);
    }

    @Test
    public void joinMiterSpikes() {
        BufferedImage img = renderTriangle("<a:miter lim=\"800000\"/>");
        assertFilled(img, 300, 88);
        assertEmpty(img, 300, 83);
    }

    // ========== compound lines ==========

    /** Rect (100,100)-(400,300), 12pt (16 px) line, no fill. */
    private static BufferedImage renderRect(String cmpd) {
        Element el = spElementRaw(
            "<a:xfrm><a:off x=\"" + emu(100) + "\" y=\"" + emu(100) + "\"/>"
            + "<a:ext cx=\"" + emu(300) + "\" cy=\"" + emu(200) + "\"/></a:xfrm>"
            + "<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom>"
            + "<a:noFill/>"
            + "<a:ln w=\"152400\" cmpd=\"" + cmpd + "\">"
            + "<a:solidFill><a:srgbClr val=\"" + RED + "\"/></a:solidFill></a:ln>");
        ShapeGeometry geom = new ShapeGeometry(emu(100), emu(100), emu(300), emu(200),
            0, false, false, "rect", Map.of(), null);
        return render(shape(SlideShape.ShapeType.RECTANGLE, geom, el));
    }

    @Test
    public void compoundDoubleLeavesGapOnCenterline() {
        // Left edge x=100, band [92,108]: ink [92,97.3] + [102.7,108]
        // (equal thirds, calibrated from the truth PDF's ring fills).
        BufferedImage img = renderRect("dbl");
        assertFilled(img, 94, 200);
        assertEmpty(img, 100, 200);
        assertFilled(img, 106, 200);
    }

    @Test
    public void compoundThickThinIsThreeToOneToOne() {
        // Left edge band [92,108]: thick [92,101.6], gap, thin [104.8,108].
        BufferedImage img = renderRect("thickThin");
        assertFilled(img, 96, 200);
        assertFilled(img, 100, 200); // centerline is inside the thick band
        assertEmpty(img, 103, 200);
        assertFilled(img, 107, 200);
    }
}
