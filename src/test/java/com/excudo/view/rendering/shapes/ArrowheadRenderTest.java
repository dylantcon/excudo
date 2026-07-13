package com.excudo.view.rendering.shapes;

import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.SlideShape;
import org.junit.Test;
import org.w3c.dom.Element;

import java.awt.image.BufferedImage;
import java.util.Map;

import static com.excudo.view.rendering.shapes.GeometryRenderTestSupport.*;

/**
 * Pins a:headEnd/a:tailEnd rendering: arrowheads are filled with the
 * line color, sized in multiples of the line width, tip anchored at the
 * path endpoint pointing outward. Size multipliers are calibrated from
 * PowerPoint's PDF export of the arrowheads corpus deck (2.25pt lines):
 * triangle/stealth/diamond/oval lg spans 5w x 5w, med 3w x 3w, sm 2w x 2w;
 * diamond and oval are CENTERED on the endpoint; open-arrow arms are
 * stroked at the line width with round caps.
 *
 * <p>Fail-first: verified red 2026-07-13 on the pre-A5 renderer -- no
 * arrowheads were drawn at all (and these zero-height connector lines
 * were culled entirely).
 *
 * <p>Fixture: horizontal cxnSp line (100,300)-(400,300), 3pt (4 px) red.
 */
public class ArrowheadRenderTest {

    private static BufferedImage renderEnds(String endsXml) {
        Element el = cxnSpElement(
            "<a:xfrm><a:off x=\"" + emu(100) + "\" y=\"" + emu(300) + "\"/>"
            + "<a:ext cx=\"" + emu(300) + "\" cy=\"0\"/></a:xfrm>"
            + "<a:prstGeom prst=\"line\"><a:avLst/></a:prstGeom>"
            + "<a:ln w=\"38100\"><a:solidFill><a:srgbClr val=\"FF0000\"/></a:solidFill>"
            + endsXml + "</a:ln>");
        ShapeGeometry geom = new ShapeGeometry(emu(100), emu(300), emu(300), 0,
            0, false, false, "line", Map.of(), null);
        return render(shape(SlideShape.ShapeType.CONNECTION, geom, el));
    }

    @Test
    public void tailTriangleLgFillsTheBarbs() {
        // lg at 4 px line: 20 x 20 triangle, tip (400,300), base x=380.
        BufferedImage img = renderEnds("<a:tailEnd type=\"triangle\" w=\"lg\" len=\"lg\"/>");
        assertFilled(img, 388, 300); // inside the head, past the trimmed stroke
        assertFilled(img, 382, 306); // lower barb, far outside the 4 px stroke
        assertFilled(img, 382, 294); // upper barb
        assertEmpty(img, 382, 312);  // outside the triangle
        assertEmpty(img, 118, 306);  // head end has no arrowhead
    }

    @Test
    public void headOvalMedIsCenteredOnTheEndpoint() {
        // med at 4 px line: 12 x 12 disc centered (100,300).
        BufferedImage img = renderEnds("<a:headEnd type=\"oval\" w=\"med\" len=\"med\"/>");
        assertFilled(img, 97, 303);  // inside the disc, behind the endpoint
        assertFilled(img, 104, 300); // line side
        assertEmpty(img, 94, 309);
    }

    @Test
    public void headDiamondLgIsCenteredOnTheEndpoint() {
        // lg at 4 px line: 20 x 20 diamond centered (100,300).
        BufferedImage img = renderEnds("<a:headEnd type=\"diamond\" w=\"lg\" len=\"lg\"/>");
        assertFilled(img, 96, 303);  // inside, behind the endpoint
        assertFilled(img, 102, 304);
        assertEmpty(img, 93, 308);   // outside the diamond edge
    }

    @Test
    public void headStealthLgFillsTheLobes() {
        // lg: apex (100,300), wings back at x=120 spread +/-10, notch (108,300).
        BufferedImage img = renderEnds("<a:headEnd type=\"stealth\" w=\"lg\" len=\"lg\"/>");
        assertFilled(img, 107, 298); // upper lobe (incircle at ~(107.4, 298.3))
        assertFilled(img, 105, 301); // center wedge between apex and notch
        assertEmpty(img, 117, 285);
    }

    @Test
    public void headArrowMedStrokesOpenArms() {
        // med at 4 px: arm cap centers at vertex + (12, +/-7); the arms are
        // 4 px strokes, so their cap centers are solidly inked.
        BufferedImage img = renderEnds("<a:headEnd type=\"arrow\" w=\"med\" len=\"med\"/>");
        assertFilled(img, 116, 293);
        assertFilled(img, 116, 307);
        assertEmpty(img, 116, 285);
    }
}
