package com.excudo.view.rendering.shapes;

import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.SlideShape;
import org.junit.Test;
import org.w3c.dom.Element;

import java.awt.image.BufferedImage;
import java.util.Map;

import static com.excudo.view.rendering.shapes.GeometryRenderTestSupport.*;

/**
 * Pins p:cxnSp rendering through the ECMA-376 geometry engine: bent and
 * curved connectors must follow their preset paths (bentConnector3 =
 * H-V-H elbow, curvedConnector3 = two mirror-image cubics), not collapse
 * to the bounding-box diagonal.
 *
 * <p>Fail-first: against the pre-A5 renderer (special-cased CONNECTION
 * branch drawing only the diagonal) verified red 2026-07-13:
 * bentConnector3FollowsElbow and curvedConnector3FollowsCubics both
 * failed -- the elbow/curve probes were empty and the diagonal probes
 * were stroked. straightLine tests pass before and after (the diagonal
 * IS the correct path for prst=line) and pin the flip handling through
 * the refactor.
 *
 * <p>Fixture: 400x200 px box at (100,100)-(500,300), 3pt (4 px) red
 * stroke, no fill. Truth geometry for curvedConnector3 (adj1=50000):
 * moveTo(100,100) cubic[(200,100),(300,150)->(300,200)]
 * cubic[(300,250),(400,300)->(500,300)]; B(0.2) = (159.2, 105.6).
 */
public class ConnectorRenderTest {

    private static final String LN_RED_3PT =
        "<a:ln w=\"38100\"><a:solidFill><a:srgbClr val=\"FF0000\"/></a:solidFill></a:ln>";

    private static BufferedImage renderConnector(String preset, boolean flipH, boolean flipV) {
        Element el = cxnSpElement(
            "<a:xfrm" + (flipH ? " flipH=\"1\"" : "") + (flipV ? " flipV=\"1\"" : "") + ">"
            + "<a:off x=\"" + emu(100) + "\" y=\"" + emu(100) + "\"/>"
            + "<a:ext cx=\"" + emu(400) + "\" cy=\"" + emu(200) + "\"/></a:xfrm>"
            + "<a:prstGeom prst=\"" + preset + "\"><a:avLst/></a:prstGeom>"
            + LN_RED_3PT);
        ShapeGeometry geom = new ShapeGeometry(emu(100), emu(100), emu(400), emu(200),
            0, flipH, flipV, preset, Map.of(), null);
        return render(shape(SlideShape.ShapeType.CONNECTION, geom, el));
    }

    @Test
    public void straightLineRunsMainDiagonal() {
        BufferedImage img = renderConnector("line", false, false);
        assertFilled(img, 300, 200); // diagonal midpoint
        assertFilled(img, 110, 105);
        assertEmpty(img, 300, 120);
    }

    @Test
    public void straightLineFlipVRunsAntiDiagonal() {
        BufferedImage img = renderConnector("line", false, true);
        assertFilled(img, 300, 200); // anti-diagonal midpoint
        assertFilled(img, 110, 295);
        assertEmpty(img, 110, 105);
    }

    @Test
    public void bentConnector3FollowsElbow() {
        BufferedImage img = renderConnector("bentConnector3", false, false);
        // Elbow (adj1=50%): (100,100) -> (300,100) -> (300,300) -> (500,300)
        assertFilled(img, 200, 100); // first horizontal run
        assertFilled(img, 300, 200); // vertical run
        assertFilled(img, 400, 300); // second horizontal run
        assertEmpty(img, 200, 150);  // point on the old (wrong) diagonal
        assertEmpty(img, 150, 200);
    }

    @Test
    public void bentConnector3FlipHMirrorsElbow() {
        BufferedImage img = renderConnector("bentConnector3", true, false);
        // Mirrored elbow: (500,100) -> (300,100) -> (300,300) -> (100,300)
        assertFilled(img, 400, 100);
        assertFilled(img, 300, 200);
        assertFilled(img, 200, 300);
        assertEmpty(img, 400, 300);
    }

    @Test
    public void curvedConnector3FollowsCubics() {
        BufferedImage img = renderConnector("curvedConnector3", false, false);
        assertFilled(img, 159, 106); // first cubic at t=0.2: (159.2, 105.6)
        assertFilled(img, 441, 294); // mirror point on the second cubic
        assertFilled(img, 300, 200); // curve center
        assertEmpty(img, 400, 250);  // point on the old (wrong) diagonal
        assertEmpty(img, 300, 150);
    }
}
