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
 * Pins the ECMA-376 path fill modes (lighten/lightenLess/darken/
 * darkenLess) to PowerPoint's actual facet arithmetic, calibrated from
 * the preset-shapes-basic ground truth: the bevel shape's facets are
 * plain sRGB multiplies of the base fill --
 *
 * <pre>
 *   darken      c' = 0.6 c              (truth right facet: (144,184,247) -> (86,110,148))
 *   darkenLess  c' = 0.8 c              (bottom facet: (64,129,206) -> (51,103,165))
 *   lighten     c' = 0.6 c + 0.4*255    (left facet: (144,184,247) -> (186,212,250))
 *   lightenLess c' = 0.8 c + 0.2*255    (top facet)
 * </pre>
 *
 * NOT the linearized-sRGB tint/shade math of a:tint/a:shade color
 * modifiers -- that pipeline stays linear; only the path fill modes are
 * byte-space.
 *
 * <p>Fail-first: verified red 2026-07-14 -- the renderer derived facets
 * through ColorTransforms (linear space), rendering the bevel's darken
 * facet at ~(205,0,0) instead of (153,0,0) over a red base.
 *
 * <p>Fixture: bevel 200x200 at (100,100), solid FF0000 fill, no line.
 * Default adj=12500 puts the facet band 25 px wide; probes sit at facet
 * centers.
 */
public class PathFillModeTest {

    private static void assertNear(BufferedImage img, int x, int y, int r, int g, int b) {
        int argb = img.getRGB(x, y);
        int ar = (argb >> 16) & 0xFF, ag = (argb >> 8) & 0xFF, ab = argb & 0xFF;
        assertTrue("at (" + x + "," + y + ") expected ~(" + r + "," + g + "," + b
                + "), got (" + ar + "," + ag + "," + ab + ")",
            Math.abs(ar - r) <= 4 && Math.abs(ag - g) <= 4 && Math.abs(ab - b) <= 4);
    }

    @Test
    public void bevelFacetsAreSrgbMultiplies() {
        Element el = spElementRaw(
            "<a:xfrm><a:off x=\"" + emu(100) + "\" y=\"" + emu(100) + "\"/>"
            + "<a:ext cx=\"" + emu(200) + "\" cy=\"" + emu(200) + "\"/></a:xfrm>"
            + "<a:prstGeom prst=\"bevel\"><a:avLst/></a:prstGeom>"
            + "<a:solidFill><a:srgbClr val=\"FF0000\"/></a:solidFill>"
            + "<a:ln><a:noFill/></a:ln>");
        ShapeGeometry geom = new ShapeGeometry(emu(100), emu(100), emu(200), emu(200),
            0, false, false, "bevel", Map.of(), null);
        BufferedImage img = render(shape(SlideShape.ShapeType.BEVEL, geom, el));

        assertNear(img, 200, 200, 255, 0, 0);    // center: base red untouched
        assertNear(img, 288, 200, 153, 0, 0);    // right facet: darken = 0.6c
        assertNear(img, 200, 288, 204, 0, 0);    // bottom facet: darkenLess = 0.8c
        assertNear(img, 112, 200, 255, 102, 102); // left facet: lighten
        assertNear(img, 200, 112, 255, 51, 51);  // top facet: lightenLess
    }
}
