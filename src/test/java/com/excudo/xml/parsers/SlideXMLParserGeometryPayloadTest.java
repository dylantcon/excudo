package com.excudo.xml.parsers;

import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.SlideShape;
import com.excudo.exceptions.XMLParsingException;
import org.junit.Test;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

/**
 * Pins the geometry payload the parser hangs on {@link ShapeGeometry}:
 * the raw prstGeom name (full 187-preset vocabulary, not just the
 * ShapeType enum), avLst adjust overrides, and parsed custGeom. The
 * renderer resolves shapes exclusively from this payload, so dropping
 * any of it silently regresses to wrong geometry.
 */
public class SlideXMLParserGeometryPayloadTest {

    private static final String P_NS = "http://schemas.openxmlformats.org/presentationml/2006/main";
    private static final String A_NS = "http://schemas.openxmlformats.org/drawingml/2006/main";

    private static Document slideDoc(String spTreeShapes) {
        String xml = "<p:sld xmlns:p=\"" + P_NS + "\" xmlns:a=\"" + A_NS + "\">"
            + "<p:cSld><p:spTree>"
            + "<p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>"
            + "<p:grpSpPr/>"
            + spTreeShapes
            + "</p:spTree></p:cSld></p:sld>";
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            return f.newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("bad test fixture XML", e);
        }
    }

    private static String sp(int id, String name, String geometryXml) {
        return "<p:sp>"
            + "<p:nvSpPr><p:cNvPr id=\"" + id + "\" name=\"" + name + "\"/>"
            + "<p:cNvSpPr/><p:nvPr/></p:nvSpPr>"
            + "<p:spPr>"
            + "<a:xfrm><a:off x=\"914400\" y=\"914400\"/>"
            + "<a:ext cx=\"1828800\" cy=\"914400\"/></a:xfrm>"
            + geometryXml
            + "</p:spPr></p:sp>";
    }

    private static SlideShape shape(Document doc, int spid) throws Exception {
        return new SlideXMLParser().parseSlide(doc).getShapeRegistry()
            .getAllShapes().stream()
            .filter(s -> s.getSpid() == spid)
            .findFirst().orElseThrow();
    }

    @Test
    public void prstGeomNameAndAvListOverridesLandOnTheGeometry() throws Exception {
        Document doc = slideDoc(sp(2, "Arrow",
            "<a:prstGeom prst=\"leftArrow\"><a:avLst>"
            + "<a:gd name=\"adj1\" fmla=\"val 30000\"/>"
            + "<a:gd name=\"adj2\" fmla=\"val 40000\"/>"
            + "</a:avLst></a:prstGeom>"));

        ShapeGeometry g = shape(doc, 2).getGeometry();
        assertEquals("leftArrow", g.getPresetName());
        assertEquals(Integer.valueOf(30000), g.getAdjustValues().get("adj1"));
        assertEquals(Integer.valueOf(40000), g.getAdjustValues().get("adj2"));
        assertEquals(2, g.getAdjustValues().size());
        assertNull(g.getCustomGeometry());
    }

    @Test
    public void emptyAvListMeansPresetDefaults() throws Exception {
        Document doc = slideDoc(sp(2, "Box", "<a:prstGeom prst=\"roundRect\"><a:avLst/></a:prstGeom>"));
        ShapeGeometry g = shape(doc, 2).getGeometry();
        assertEquals("roundRect", g.getPresetName());
        assertTrue(g.getAdjustValues().isEmpty());
    }

    @Test
    public void presetOutsideTheShapeTypeEnumStillCarriesItsRawName() throws Exception {
        // gear6 is a real ECMA-376 preset the ShapeType enum does not
        // model (fromOoxmlPreset returns CUSTOM_GEOMETRY). The renderer
        // must still receive the true name via the geometry payload.
        Document doc = slideDoc(sp(2, "Gear", "<a:prstGeom prst=\"gear6\"><a:avLst/></a:prstGeom>"));
        SlideShape s = shape(doc, 2);
        assertEquals(SlideShape.ShapeType.CUSTOM_GEOMETRY, s.getType());
        assertEquals("gear6", s.getGeometry().getPresetName());
    }

    @Test
    public void custGeomParsesIntoTheGeometryPayload() throws Exception {
        Document doc = slideDoc(sp(3, "Freeform",
            "<a:custGeom>"
            + "<a:avLst/><a:gdLst/><a:ahLst/><a:cxnLst/>"
            + "<a:rect l=\"l\" t=\"t\" r=\"r\" b=\"b\"/>"
            + "<a:pathLst><a:path w=\"21600\" h=\"21600\">"
            + "<a:moveTo><a:pt x=\"0\" y=\"21600\"/></a:moveTo>"
            + "<a:lnTo><a:pt x=\"10800\" y=\"0\"/></a:lnTo>"
            + "<a:lnTo><a:pt x=\"21600\" y=\"21600\"/></a:lnTo>"
            + "<a:close/>"
            + "</a:path></a:pathLst></a:custGeom>"));

        SlideShape s = shape(doc, 3);
        assertEquals(SlideShape.ShapeType.CUSTOM_GEOMETRY, s.getType());
        ShapeGeometry g = s.getGeometry();
        assertNull(g.getPresetName());
        assertNotNull(g.getCustomGeometry());
        assertEquals(1, g.getCustomGeometry().getPaths().size());
        assertEquals(21600.0, g.getCustomGeometry().getPaths().get(0).getWidth(), 0);
        assertEquals(4, g.getCustomGeometry().getPaths().get(0).getCommands().size());
    }

    @Test
    public void groupChildKeepsItsGeometryPayloadWhenRebased() throws Exception {
        // Group at (0,0) 2x scale over a 914400^2 child space; the child
        // roundRect carries an adjust override that must survive the
        // slide-coordinate re-basing.
        Document doc = slideDoc(
            "<p:grpSp>"
            + "<p:nvGrpSpPr><p:cNvPr id=\"10\" name=\"G\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>"
            + "<p:grpSpPr><a:xfrm>"
            + "<a:off x=\"0\" y=\"0\"/><a:ext cx=\"1828800\" cy=\"1828800\"/>"
            + "<a:chOff x=\"0\" y=\"0\"/><a:chExt cx=\"914400\" cy=\"914400\"/>"
            + "</a:xfrm></p:grpSpPr>"
            + sp(11, "Child",
                "<a:prstGeom prst=\"roundRect\"><a:avLst>"
                + "<a:gd name=\"adj\" fmla=\"val 25000\"/></a:avLst></a:prstGeom>")
            + "</p:grpSp>");

        ShapeGeometry g = shape(doc, 11).getGeometry();
        assertEquals("roundRect", g.getPresetName());
        assertEquals(Integer.valueOf(25000), g.getAdjustValues().get("adj"));
        // re-based into slide space: (914400,914400) doubled
        assertEquals(1828800L, g.getX());
        assertEquals(3657600L, g.getWidth());
    }

    @Test(expected = XMLParsingException.class)
    public void malformedAvListFormulaThrows() throws Exception {
        // avLst gd formulas are "val N" by schema; anything else is
        // malformed and must fail the parse, not silently drop.
        Document doc = slideDoc(sp(2, "Bad",
            "<a:prstGeom prst=\"roundRect\"><a:avLst>"
            + "<a:gd name=\"adj\" fmla=\"*/ 1 2 3\"/></a:avLst></a:prstGeom>"));
        new SlideXMLParser().parseSlide(doc);
    }
}
