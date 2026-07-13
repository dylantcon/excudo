package com.excudo.view.rendering.shapes;

import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.SlideShape;
import com.excudo.core.rendering.surface.Graphics2DRenderSurface;
import com.excudo.view.rendering.CoordinateMapper;
import com.excudo.view.rendering.RenderingContext;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Plumbing for behavioural shape-render tests: a real
 * {@link Graphics2DRenderSurface} at 1280x720 for a 12192000 EMU slide
 * (composite scale exactly 1.0, so 9525 EMU = 1 px and probe pixels are
 * hand-derivable), a minimal p:sp DOM carrying an explicit red solid
 * fill, and pixel probes that stay off anti-aliased edges.
 */
final class GeometryRenderTestSupport {

    static final String P_NS = "http://schemas.openxmlformats.org/presentationml/2006/main";
    static final String A_NS = "http://schemas.openxmlformats.org/drawingml/2006/main";

    static final long EMU_PER_PX = 9525;

    private GeometryRenderTestSupport() {}

    /** p:sp element with a red solid fill and the given spPr geometry XML. */
    static Element spElement(String geometryXml) {
        return spElementRaw(geometryXml
            + "<a:solidFill><a:srgbClr val=\"FF0000\"/></a:solidFill>");
    }

    /** p:sp element whose p:spPr contains exactly {@code spPrXml}. */
    static Element spElementRaw(String spPrXml) {
        return parse("<p:sp xmlns:p=\"" + P_NS + "\" xmlns:a=\"" + A_NS + "\">"
            + "<p:nvSpPr><p:cNvPr id=\"7\" name=\"probe\"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>"
            + "<p:spPr>" + spPrXml + "</p:spPr></p:sp>");
    }

    /** p:cxnSp element whose p:spPr contains exactly {@code spPrXml}. */
    static Element cxnSpElement(String spPrXml) {
        return parse("<p:cxnSp xmlns:p=\"" + P_NS + "\" xmlns:a=\"" + A_NS + "\">"
            + "<p:nvCxnSpPr><p:cNvPr id=\"8\" name=\"conn\"/><p:cNvCxnSpPr/><p:nvPr/></p:nvCxnSpPr>"
            + "<p:spPr>" + spPrXml + "</p:spPr></p:cxnSp>");
    }

    private static Element parse(String xml) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            return f.newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
                .getDocumentElement();
        } catch (Exception e) {
            throw new IllegalStateException("bad test fixture XML", e);
        }
    }

    /** Render {@code shape} on a fresh 1280x720 AWT surface. */
    static BufferedImage render(SlideShape shape) {
        Graphics2DRenderSurface surface = new Graphics2DRenderSurface(1280, 720);
        RenderingContext ctx = new RenderingContext(surface, new CoordinateMapper(1280, 720));
        new GeometricShapeRenderer().render(shape, ctx, null);
        return surface.toBufferedImage();
    }

    static SlideShape shape(SlideShape.ShapeType type, ShapeGeometry geom, Element spEl) {
        return new SlideShape(7, "probe", type, null, geom, spEl);
    }

    /** Pixel-box geometry in EMU: (xPx, yPx, wPx, hPx) at 9525 EMU/px. */
    static long emu(int px) {
        return px * EMU_PER_PX;
    }

    static void assertFilled(BufferedImage img, int x, int y) {
        int argb = img.getRGB(x, y);
        assertEquals("alpha at (" + x + "," + y + ")", 0xFF, argb >>> 24);
        assertTrue("expected red fill at (" + x + "," + y + "), got #"
            + Integer.toHexString(argb), ((argb >> 16) & 0xFF) > 200
            && ((argb >> 8) & 0xFF) < 80 && (argb & 0xFF) < 80);
    }

    static void assertEmpty(BufferedImage img, int x, int y) {
        int argb = img.getRGB(x, y);
        assertEquals("expected untouched pixel at (" + x + "," + y + "), got #"
            + Integer.toHexString(argb), 0, argb >>> 24);
    }
}
