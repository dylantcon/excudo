package com.excudo.view.rendering.shapes;

import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.SlideShape;
import org.junit.Test;
import org.w3c.dom.Element;

import java.awt.image.BufferedImage;
import java.util.Map;

import static com.excudo.view.rendering.shapes.GeometryRenderTestSupport.*;
import static org.junit.Assert.assertTrue;

/**
 * Pins ECMA-376 path-pass ordering: ALL fillable paths paint first, then
 * ALL stroked paths -- PowerPoint never buries a stroke-only sub-path
 * under a later fill.
 *
 * <p>chartStar is the canonical case: path 1 is fill="none" (the X plus
 * the vertical bar), path 2 is the stroke="false" filled square. The
 * truth render (preset-shapes-stars-banners slide 1, first shape) shows
 * the internal lines ON TOP of the fill; the pre-A5 renderer interleaved
 * fill/stroke per path in definition order, so the square's fill painted
 * over the already-stroked lines and they vanished.
 *
 * <p>Fail-first: verified red 2026-07-13 on the pre-A5 renderer -- both
 * probes saw the red fill where blue internal strokes belong.
 */
public class StrokeOnlySubPathTest {

    private static void assertBlueStroke(BufferedImage img, int x, int y) {
        int argb = img.getRGB(x, y);
        int r = (argb >> 16) & 0xFF, b = argb & 0xFF;
        assertTrue("expected blue stroke over red fill at (" + x + "," + y + "), got #"
            + Integer.toHexString(argb), (argb >>> 24) == 0xFF && b > 150 && r < 100);
    }

    @Test
    public void chartStarInternalLinesPaintOverTheFill() {
        // 200x200 box at (100,100): vertical bar x=300... no -- box
        // (100,100)-(300,300): vertical internal line at x=200, X through
        // the center. 3pt (4 px) blue stroke over red fill.
        Element el = spElementRaw(
            "<a:xfrm><a:off x=\"" + emu(100) + "\" y=\"" + emu(100) + "\"/>"
            + "<a:ext cx=\"" + emu(200) + "\" cy=\"" + emu(200) + "\"/></a:xfrm>"
            + "<a:prstGeom prst=\"chartStar\"><a:avLst/></a:prstGeom>"
            + "<a:solidFill><a:srgbClr val=\"FF0000\"/></a:solidFill>"
            + "<a:ln w=\"38100\"><a:solidFill><a:srgbClr val=\"0000FF\"/></a:solidFill></a:ln>");
        ShapeGeometry geom = new ShapeGeometry(emu(100), emu(100), emu(200), emu(200),
            0, false, false, "chartStar", Map.of(), null);
        BufferedImage img = render(shape(SlideShape.ShapeType.CUSTOM_GEOMETRY, geom, el));

        assertBlueStroke(img, 200, 150); // vertical internal bar
        assertBlueStroke(img, 150, 150); // main diagonal of the X
        assertFilled(img, 170, 130);     // square fill still present (red)
    }
}
