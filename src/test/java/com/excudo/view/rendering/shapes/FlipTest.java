package com.excudo.view.rendering.shapes;

import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.SlideShape;
import org.junit.Test;
import org.w3c.dom.Element;

import java.awt.image.BufferedImage;
import java.util.Map;

import static com.excudo.view.rendering.shapes.GeometryRenderTestSupport.*;

/**
 * Pins a:xfrm/@flipH/@flipV for ALL shapes (not just connectors):
 * reflection about the shape center, composed with rotation the way
 * PowerPoint does -- mirror the shape first, THEN rotate the mirrored
 * shape about its center.
 *
 * <p>Fail-first: against the pre-A4 renderer (PresetGeometryPaths, no
 * flip transform for non-connectors) every flipped case here FAILED
 * (verified 2026-07-13: flipH/flipV/both drew the unflipped triangle,
 * flipHThenRotate drew the unflipped rotated triangle); only the
 * unflipped baseline passed.
 *
 * <p>Fixture: rtTriangle in the 400x200 px box (100,100)-(500,300).
 * Spec path (l,b) (l,t) (r,b): the right angle sits at bottom-left and
 * the hypotenuse runs (100,100) -> (500,300). Probe points sit deep
 * inside one orientation and far outside the others:
 * <pre>
 *   A=(110,200)  B=(490,200)  C=(300,120)  D=(300,280)
 *   none:  A in, B out, C out, D in
 *   flipH: A out, B in,  C out, D in
 *   flipV: A in,  B out, C in,  D out
 *   both:  A out, B in,  C in,  D out
 * </pre>
 */
public class FlipTest {

    private static Element rtTriangleSp() {
        return spElement(
            "<a:xfrm><a:off x=\"" + emu(100) + "\" y=\"" + emu(100) + "\"/>"
            + "<a:ext cx=\"" + emu(400) + "\" cy=\"" + emu(200) + "\"/></a:xfrm>"
            + "<a:prstGeom prst=\"rtTriangle\"><a:avLst/></a:prstGeom>");
    }

    private static BufferedImage renderFlipped(boolean flipH, boolean flipV, int rot60k) {
        ShapeGeometry geom = new ShapeGeometry(emu(100), emu(100), emu(400), emu(200),
            rot60k, flipH, flipV, "rtTriangle", Map.of(), null);
        return render(shape(SlideShape.ShapeType.RIGHT_TRIANGLE, geom, rtTriangleSp()));
    }

    @Test
    public void unflippedBaseline() {
        BufferedImage img = renderFlipped(false, false, 0);
        assertFilled(img, 110, 200);
        assertEmpty(img, 490, 200);
        assertEmpty(img, 300, 120);
        assertFilled(img, 300, 280);
    }

    @Test
    public void flipHMirrorsAboutTheVerticalCenter() {
        BufferedImage img = renderFlipped(true, false, 0);
        assertEmpty(img, 110, 200);
        assertFilled(img, 490, 200);
        assertEmpty(img, 300, 120);
        assertFilled(img, 300, 280);
    }

    @Test
    public void flipVMirrorsAboutTheHorizontalCenter() {
        BufferedImage img = renderFlipped(false, true, 0);
        assertFilled(img, 110, 200);
        assertEmpty(img, 490, 200);
        assertFilled(img, 300, 120);
        assertEmpty(img, 300, 280);
    }

    @Test
    public void bothFlipsEqualPointReflection() {
        BufferedImage img = renderFlipped(true, true, 0);
        assertEmpty(img, 110, 200);
        assertFilled(img, 490, 200);
        assertFilled(img, 300, 120);
        assertEmpty(img, 300, 280);
    }

    @Test
    public void flipAppliesBeforeRotationLikePowerPoint() {
        // flipH + rot 90deg. Mirror-then-rotate about the center
        // (300,200) maps the flipped vertices (500,300),(500,100),
        // (100,300) through (x,y) -> (500-y, x-100) to the triangle
        // (200,400),(400,400),(200,0): at y=200 it spans x in [200,300],
        // at y=350 x in [200,375], at y=50 x in [200,225].
        // The WRONG order (rotate, then mirror) yields (400,0),(200,0),
        // (400,400): x in [300,400] at y=200, [375,400] at y=350,
        // [225,400] at y=50. Rotating WITHOUT any flip yields (200,0),
        // (400,0),(200,400): x in [200,300] at y=200 (agrees!), but
        // [200,225] at y=350 and [200,375] at y=50. The four probes
        // below separate all three candidates unambiguously.
        BufferedImage img = renderFlipped(true, false, 90 * 60000);
        assertFilled(img, 250, 200);
        assertEmpty(img, 350, 200);
        assertFilled(img, 250, 350);
        assertEmpty(img, 250, 50);
    }
}
