package com.excudo.view.rendering.shapes;

import com.excudo.core.geometry.CustomGeometryParser;
import com.excudo.core.geometry.GeometryDefinition;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.SlideShape;
import org.junit.Test;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static com.excudo.view.rendering.shapes.GeometryRenderTestSupport.*;
import static org.junit.Assert.*;

/**
 * Pins the renderer's geometry-engine routing: custGeom shapes draw
 * their ACTUAL outline (never a bounding-box rectangle), presets
 * outside the ShapeType enum resolve through their raw preset name,
 * fill="none" paths never fill, and lighten/darken paths derive their
 * paint from the shape fill.
 *
 * <p>Fail-first: against the pre-A4 renderer (verified 2026-07-13)
 * every test here FAILED -- custGeom and unknown-preset shapes were
 * painted as their full bounding box, and multi-path fill modes did
 * not exist.
 */
public class CustGeomRenderTest {

    /** Isoceles triangle in a 21600x21600 local space. */
    private static final String CUSTGEOM_XML =
        "<a:custGeom xmlns:a=\"" + A_NS + "\">"
        + "<a:avLst/><a:gdLst/>"
        + "<a:pathLst><a:path w=\"21600\" h=\"21600\">"
        + "<a:moveTo><a:pt x=\"0\" y=\"21600\"/></a:moveTo>"
        + "<a:lnTo><a:pt x=\"10800\" y=\"0\"/></a:lnTo>"
        + "<a:lnTo><a:pt x=\"21600\" y=\"21600\"/></a:lnTo>"
        + "<a:close/>"
        + "</a:path></a:pathLst></a:custGeom>";

    private static GeometryDefinition custGeomDef() {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            Element el = f.newDocumentBuilder()
                .parse(new ByteArrayInputStream(CUSTGEOM_XML.getBytes(StandardCharsets.UTF_8)))
                .getDocumentElement();
            return CustomGeometryParser.parse(el);
        } catch (Exception e) {
            throw new IllegalStateException("bad test fixture XML", e);
        }
    }

    @Test
    public void custGeomDrawsItsOutlineNotItsBoundingBox() {
        // Box (100,100)-(500,300): the local 21600-space triangle maps
        // to apex (300,100), base (100,300)-(500,300). Interior probes
        // must fill; the box's top corners must stay EMPTY -- a
        // bounding-box fallback paints them.
        ShapeGeometry geom = new ShapeGeometry(emu(100), emu(100), emu(400), emu(200),
            0, false, false, null, Map.of(), custGeomDef());
        Element sp = spElement(
            "<a:xfrm><a:off x=\"" + emu(100) + "\" y=\"" + emu(100) + "\"/>"
            + "<a:ext cx=\"" + emu(400) + "\" cy=\"" + emu(200) + "\"/></a:xfrm>"
            + CUSTGEOM_XML.replace(" xmlns:a=\"" + A_NS + "\"", ""));
        BufferedImage img = render(shape(SlideShape.ShapeType.CUSTOM_GEOMETRY, geom, sp));

        assertFilled(img, 300, 150);   // under the apex
        assertFilled(img, 300, 280);   // base center
        assertFilled(img, 150, 280);   // base left
        assertEmpty(img, 130, 130);    // top-left corner of the box
        assertEmpty(img, 470, 130);    // top-right corner of the box
    }

    @Test
    public void presetOutsideTheShapeTypeEnumRendersItsRealGeometry() {
        // gear6 maps to ShapeType.CUSTOM_GEOMETRY (no enum member), so
        // the renderer must route through the raw preset name. A gear in
        // a square box leaves the box corners empty; the hub area fills.
        ShapeGeometry geom = new ShapeGeometry(emu(100), emu(100), emu(200), emu(200),
            0, false, false, "gear6", Map.of(), null);
        Element sp = spElement(
            "<a:xfrm><a:off x=\"" + emu(100) + "\" y=\"" + emu(100) + "\"/>"
            + "<a:ext cx=\"" + emu(200) + "\" cy=\"" + emu(200) + "\"/></a:xfrm>"
            + "<a:prstGeom prst=\"gear6\"><a:avLst/></a:prstGeom>");
        BufferedImage img = render(shape(SlideShape.ShapeType.CUSTOM_GEOMETRY, geom, sp));

        assertFilled(img, 200, 200);   // center of the gear
        assertEmpty(img, 108, 108);    // box corner, outside the teeth
        assertEmpty(img, 292, 108);
        assertEmpty(img, 108, 292);
        assertEmpty(img, 292, 292);
    }

    @Test
    public void noneFillPathsPaintNothing() {
        // The 'arc' preset's only path is fill="none" (a stroked open
        // arc). With no line style resolvable the shape must paint
        // NOTHING -- the pre-A4 fallback filled the whole bounding box.
        ShapeGeometry geom = new ShapeGeometry(emu(100), emu(100), emu(200), emu(200),
            0, false, false, "arc", Map.of(), null);
        Element sp = spElement(
            "<a:xfrm><a:off x=\"" + emu(100) + "\" y=\"" + emu(100) + "\"/>"
            + "<a:ext cx=\"" + emu(200) + "\" cy=\"" + emu(200) + "\"/></a:xfrm>"
            + "<a:prstGeom prst=\"arc\"><a:avLst/></a:prstGeom>");
        BufferedImage img = render(shape(SlideShape.ShapeType.ARC, geom, sp));

        assertEmpty(img, 200, 200);
        assertEmpty(img, 150, 150);
        assertEmpty(img, 250, 250);
    }

    @Test
    public void lightenPathsDeriveALighterPaintFromTheShapeFill() {
        // 'can' layers: body (norm), top ellipse (fill="lighten"),
        // outline (fill="none"). adj=25000, 200x200 box at (100,100):
        // y1 = ss*a/200000 = 25px -> top ellipse spans y in
        // [100,150] around cy=125; body below. The lid must be strictly
        // lighter than the body but still reddish, and both filled.
        ShapeGeometry geom = new ShapeGeometry(emu(100), emu(100), emu(200), emu(200),
            0, false, false, "can", Map.of(), null);
        Element sp = spElement(
            "<a:xfrm><a:off x=\"" + emu(100) + "\" y=\"" + emu(100) + "\"/>"
            + "<a:ext cx=\"" + emu(200) + "\" cy=\"" + emu(200) + "\"/></a:xfrm>"
            + "<a:prstGeom prst=\"can\"><a:avLst/></a:prstGeom>");
        BufferedImage img = render(shape(SlideShape.ShapeType.CAN, geom, sp));

        int lid = img.getRGB(200, 125);
        int body = img.getRGB(200, 250);
        assertEquals("body opaque", 0xFF, body >>> 24);
        assertEquals("lid opaque", 0xFF, lid >>> 24);
        // body is the plain shape fill (red)
        assertEquals("body keeps the shape fill", 0xFFFF0000, body);
        // lid = lighten(red): strictly brighter in green/blue (toward
        // white), red channel still saturated
        assertTrue("lid must lighten toward white, got #" + Integer.toHexString(lid),
            ((lid >> 8) & 0xFF) > ((body >> 8) & 0xFF)
            && (lid & 0xFF) > (body & 0xFF)
            && ((lid >> 16) & 0xFF) >= 200);
    }
}
