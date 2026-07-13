package com.excudo.core.geometry;

import org.junit.Test;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Pins the {@code a:custGeom} parser: namespaced slide-XML form and
 * default-namespaced (vendored-file) form parse identically, every
 * command/attribute lands where the model says, and malformed geometry
 * throws {@link IllegalArgumentException} -- never a rectangle fallback.
 */
public class CustomGeometryParserTest {

    private static final String A_NS =
        "http://schemas.openxmlformats.org/drawingml/2006/main";

    private static Element parseXml(String xml) {
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

    /** Wrap custGeom children in an a:-prefixed root, as slide XML has it. */
    private static Element custGeom(String inner) {
        return parseXml("<a:custGeom xmlns:a=\"" + A_NS + "\">" + inner + "</a:custGeom>");
    }

    // ========== the full grammar, a:-prefixed ==========

    private static final String REALISTIC =
        "<a:avLst><a:gd name=\"adj1\" fmla=\"val 40000\"/></a:avLst>"
        + "<a:gdLst>"
        + "  <a:gd name=\"x1\" fmla=\"*/ w adj1 100000\"/>"
        + "  <a:gd name=\"y1\" fmla=\"+- h 0 x1\"/>"
        + "</a:gdLst>"
        + "<a:ahLst><a:ahXY gdRefX=\"adj1\"><a:pos x=\"x1\" y=\"t\"/></a:ahXY></a:ahLst>"
        + "<a:cxnLst><a:cxn ang=\"0\"><a:pos x=\"r\" y=\"vc\"/></a:cxn></a:cxnLst>"
        + "<a:rect l=\"0\" t=\"0\" r=\"x1\" b=\"y1\"/>"
        + "<a:pathLst>"
        + "  <a:path w=\"21600\" h=\"21600\" fill=\"lightenLess\" stroke=\"0\" extrusionOk=\"0\">"
        + "    <a:moveTo><a:pt x=\"0\" y=\"10800\"/></a:moveTo>"
        + "    <a:lnTo><a:pt x=\"10800\" y=\"0\"/></a:lnTo>"
        + "    <a:arcTo wR=\"10800\" hR=\"5400\" stAng=\"3cd4\" swAng=\"cd4\"/>"
        + "    <a:cubicBezTo>"
        + "      <a:pt x=\"1\" y=\"2\"/><a:pt x=\"3\" y=\"4\"/><a:pt x=\"5\" y=\"6\"/>"
        + "    </a:cubicBezTo>"
        + "    <a:quadBezTo><a:pt x=\"7\" y=\"8\"/><a:pt x=\"9\" y=\"10\"/></a:quadBezTo>"
        + "    <a:close/>"
        + "  </a:path>"
        + "  <a:path>"
        + "    <a:moveTo><a:pt x=\"x1\" y=\"t\"/></a:moveTo>"
        + "    <a:lnTo><a:pt x=\"r\" y=\"b\"/></a:lnTo>"
        + "  </a:path>"
        + "</a:pathLst>";

    @Test
    public void parsesRealisticCustGeom() {
        GeometryDefinition def = CustomGeometryParser.parse(custGeom(REALISTIC));

        assertEquals("custGeom", def.getName());
        assertEquals(1, def.getAdjustDefaults().size());
        assertEquals("adj1", def.getAdjustDefaults().get(0).name());
        assertEquals("val 40000", def.getAdjustDefaults().get(0).fmla());
        assertEquals(2, def.getGuides().size());
        assertEquals("x1", def.getGuides().get(0).name());
        assertEquals("*/ w adj1 100000", def.getGuides().get(0).fmla());

        assertNotNull(def.getTextRect());
        assertEquals("x1", def.getTextRect().right());
        assertEquals("y1", def.getTextRect().bottom());

        assertEquals(2, def.getPaths().size());

        GeometryPath p0 = def.getPaths().get(0);
        assertEquals(21600.0, p0.getWidth(), 0);
        assertEquals(21600.0, p0.getHeight(), 0);
        assertEquals(GeometryPath.FillMode.LIGHTEN_LESS, p0.getFill());
        assertFalse("stroke=\"0\" must parse as unstroked", p0.isStroked());

        List<GeometryPath.Command> cmds = p0.getCommands();
        assertEquals(6, cmds.size());
        assertEquals(new GeometryPath.MoveTo("0", "10800"), cmds.get(0));
        assertEquals(new GeometryPath.LnTo("10800", "0"), cmds.get(1));
        assertEquals(new GeometryPath.ArcTo("10800", "5400", "3cd4", "cd4"), cmds.get(2));
        assertEquals(new GeometryPath.CubicBezTo("1", "2", "3", "4", "5", "6"), cmds.get(3));
        assertEquals(new GeometryPath.QuadBezTo("7", "8", "9", "10"), cmds.get(4));
        assertTrue(cmds.get(5) instanceof GeometryPath.Close);

        // Second path: no local space, defaults NORM + stroked; guide-name
        // coordinate tokens survive verbatim for render-time resolution.
        GeometryPath p1 = def.getPaths().get(1);
        assertEquals(0.0, p1.getWidth(), 0);
        assertEquals(0.0, p1.getHeight(), 0);
        assertEquals(GeometryPath.FillMode.NORM, p1.getFill());
        assertTrue(p1.isStroked());
        assertEquals(new GeometryPath.MoveTo("x1", "t"), p1.getCommands().get(0));
    }

    @Test
    public void defaultNamespaceFormParsesIdentically() {
        // The vendored preset file uses the drawingml namespace as the
        // DEFAULT namespace (no prefix). Same grammar, same result.
        Element el = parseXml(
            "<custGeom xmlns=\"" + A_NS + "\">"
            + "<avLst><gd name=\"adj\" fmla=\"val 16667\"/></avLst>"
            + "<pathLst><path><moveTo><pt x=\"l\" y=\"t\"/></moveTo>"
            + "<lnTo><pt x=\"r\" y=\"b\"/></lnTo><close/></path></pathLst>"
            + "</custGeom>");
        GeometryDefinition def = CustomGeometryParser.parse(el);
        assertEquals(1, def.getAdjustDefaults().size());
        assertEquals("adj", def.getAdjustDefaults().get(0).name());
        assertEquals(3, def.getPaths().get(0).getCommands().size());
    }

    @Test
    public void unprefixedNoNamespaceFormParses() {
        // Non-namespace-aware DOM (getLocalName() == null): matching must
        // fall back to the tag name.
        Element el = parseXml(
            "<custGeom>"
            + "<pathLst><path><moveTo><pt x=\"0\" y=\"0\"/></moveTo><close/></path></pathLst>"
            + "</custGeom>");
        GeometryDefinition def = CustomGeometryParser.parse(el);
        assertEquals(2, def.getPaths().get(0).getCommands().size());
    }

    @Test
    public void emptyCustGeomYieldsEmptyDefinition() {
        // Spec-valid degenerate: all children optional. No paths -> the
        // shape draws nothing, but parsing must not invent geometry.
        GeometryDefinition def = CustomGeometryParser.parse(custGeom(""));
        assertTrue(def.getPaths().isEmpty());
        assertTrue(def.getGuides().isEmpty());
        assertNull(def.getTextRect());
    }

    // ========== the no-fallback contract: malformed input throws ==========

    @Test(expected = IllegalArgumentException.class)
    public void missingPtXAttributeThrows() {
        CustomGeometryParser.parse(custGeom(
            "<a:pathLst><a:path>"
            + "<a:moveTo><a:pt y=\"5\"/></a:moveTo>"
            + "</a:path></a:pathLst>"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void moveToWithoutPtThrows() {
        CustomGeometryParser.parse(custGeom(
            "<a:pathLst><a:path><a:moveTo/></a:path></a:pathLst>"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void unknownPathCommandThrows() {
        CustomGeometryParser.parse(custGeom(
            "<a:pathLst><a:path>"
            + "<a:moveTo><a:pt x=\"0\" y=\"0\"/></a:moveTo>"
            + "<a:spiralTo><a:pt x=\"1\" y=\"1\"/></a:spiralTo>"
            + "</a:path></a:pathLst>"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void cubicBezToWithTwoPointsThrows() {
        CustomGeometryParser.parse(custGeom(
            "<a:pathLst><a:path>"
            + "<a:moveTo><a:pt x=\"0\" y=\"0\"/></a:moveTo>"
            + "<a:cubicBezTo><a:pt x=\"1\" y=\"2\"/><a:pt x=\"3\" y=\"4\"/></a:cubicBezTo>"
            + "</a:path></a:pathLst>"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void lnToWithTwoPointsThrows() {
        CustomGeometryParser.parse(custGeom(
            "<a:pathLst><a:path>"
            + "<a:lnTo><a:pt x=\"1\" y=\"2\"/><a:pt x=\"3\" y=\"4\"/></a:lnTo>"
            + "</a:path></a:pathLst>"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void arcToMissingSwAngThrows() {
        CustomGeometryParser.parse(custGeom(
            "<a:pathLst><a:path>"
            + "<a:moveTo><a:pt x=\"0\" y=\"0\"/></a:moveTo>"
            + "<a:arcTo wR=\"10\" hR=\"10\" stAng=\"0\"/>"
            + "</a:path></a:pathLst>"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void unknownFillModeThrows() {
        CustomGeometryParser.parse(custGeom(
            "<a:pathLst><a:path fill=\"sparkle\">"
            + "<a:moveTo><a:pt x=\"0\" y=\"0\"/></a:moveTo>"
            + "</a:path></a:pathLst>"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativePathWidthThrows() {
        CustomGeometryParser.parse(custGeom(
            "<a:pathLst><a:path w=\"-21600\" h=\"21600\">"
            + "<a:moveTo><a:pt x=\"0\" y=\"0\"/></a:moveTo>"
            + "</a:path></a:pathLst>"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void guideWithEmptyFormulaThrows() {
        CustomGeometryParser.parse(custGeom(
            "<a:gdLst><a:gd name=\"x1\" fmla=\"\"/></a:gdLst>"));
    }
}
