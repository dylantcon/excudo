package com.excudo.xml.builders;

import com.excudo.core.model.*;
import com.excudo.core.utils.XMLConstants;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.Assert.*;

/**
 * Tests for ShapeStyleXMLWriter DOM output
 */
public class ShapeStyleXMLWriterTest {

    private Document doc;

    @Before
    public void setUp() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        doc = builder.newDocument();
    }

    // ===== Fill Tests =====

    @Test
    public void testSolidFillHex() {
        Element spPr = doc.createElementNS(XMLConstants.PRESENTATION_NS, "p:spPr");
        ShapeStyleXMLWriter.writeFill(doc, spPr, ShapeFill.solid("FF0000"));

        NodeList fills = spPr.getElementsByTagNameNS(XMLConstants.DRAWING_NS, "solidFill");
        assertEquals(1, fills.getLength());

        Element solidFill = (Element) fills.item(0);
        NodeList clrs = solidFill.getElementsByTagNameNS(XMLConstants.DRAWING_NS, "srgbClr");
        assertEquals(1, clrs.getLength());
        assertEquals("FF0000", ((Element) clrs.item(0)).getAttribute("val"));
    }

    @Test
    public void testSolidFillScheme() {
        Element spPr = doc.createElementNS(XMLConstants.PRESENTATION_NS, "p:spPr");
        ShapeStyleXMLWriter.writeFill(doc, spPr, ShapeFill.scheme("accent1"));

        Element solidFill = (Element) spPr.getElementsByTagNameNS(XMLConstants.DRAWING_NS, "solidFill").item(0);
        NodeList clrs = solidFill.getElementsByTagNameNS(XMLConstants.DRAWING_NS, "schemeClr");
        assertEquals(1, clrs.getLength());
        assertEquals("accent1", ((Element) clrs.item(0)).getAttribute("val"));
    }

    @Test
    public void testNoFill() {
        Element spPr = doc.createElementNS(XMLConstants.PRESENTATION_NS, "p:spPr");
        ShapeStyleXMLWriter.writeFill(doc, spPr, ShapeFill.noFill());

        NodeList noFills = spPr.getElementsByTagNameNS(XMLConstants.DRAWING_NS, "noFill");
        assertEquals(1, noFills.getLength());
    }

    @Test
    public void testNullFillNoOp() {
        Element spPr = doc.createElementNS(XMLConstants.PRESENTATION_NS, "p:spPr");
        ShapeStyleXMLWriter.writeFill(doc, spPr, null);
        assertEquals(0, spPr.getChildNodes().getLength());
    }

    // ===== Line Tests =====

    @Test
    public void testSolidLine() {
        Element spPr = doc.createElementNS(XMLConstants.PRESENTATION_NS, "p:spPr");
        ShapeStyleXMLWriter.writeLine(doc, spPr, ShapeLine.solid(25400, TextColor.hex("000000")));

        NodeList lns = spPr.getElementsByTagNameNS(XMLConstants.DRAWING_NS, "ln");
        assertEquals(1, lns.getLength());

        Element ln = (Element) lns.item(0);
        assertEquals("25400", ln.getAttribute("w"));

        // Should have solidFill child
        NodeList fills = ln.getElementsByTagNameNS(XMLConstants.DRAWING_NS, "solidFill");
        assertEquals(1, fills.getLength());

        // "solid" dash style should NOT produce a prstDash element
        NodeList dashes = ln.getElementsByTagNameNS(XMLConstants.DRAWING_NS, "prstDash");
        assertEquals(0, dashes.getLength());
    }

    @Test
    public void testDashedLine() {
        Element spPr = doc.createElementNS(XMLConstants.PRESENTATION_NS, "p:spPr");
        ShapeStyleXMLWriter.writeLine(doc, spPr, ShapeLine.of(12700, TextColor.scheme("dk1"), "dash"));

        Element ln = (Element) spPr.getElementsByTagNameNS(XMLConstants.DRAWING_NS, "ln").item(0);
        NodeList dashes = ln.getElementsByTagNameNS(XMLConstants.DRAWING_NS, "prstDash");
        assertEquals(1, dashes.getLength());
        assertEquals("dash", ((Element) dashes.item(0)).getAttribute("val"));
    }

    @Test
    public void testNullLineNoOp() {
        Element spPr = doc.createElementNS(XMLConstants.PRESENTATION_NS, "p:spPr");
        ShapeStyleXMLWriter.writeLine(doc, spPr, null);
        assertEquals(0, spPr.getChildNodes().getLength());
    }

    // ===== Theme Style Ref Tests =====

    @Test
    public void testDefaultThemeStyleWithText() {
        Element style = ShapeStyleXMLWriter.writeThemeStyleRef(doc, null, true);
        assertEquals("style", style.getLocalName());

        // lnRef
        Element lnRef = (Element) style.getElementsByTagNameNS(XMLConstants.DRAWING_NS, "lnRef").item(0);
        assertEquals("2", lnRef.getAttribute("idx"));
        Element lnClr = (Element) lnRef.getElementsByTagNameNS(XMLConstants.DRAWING_NS, "schemeClr").item(0);
        assertEquals("accent1", lnClr.getAttribute("val"));
        // shade child
        NodeList shades = lnClr.getElementsByTagNameNS(XMLConstants.DRAWING_NS, "shade");
        assertEquals(1, shades.getLength());
        assertEquals("15000", ((Element) shades.item(0)).getAttribute("val"));

        // fillRef -- lt1 for text shapes
        Element fillRef = (Element) style.getElementsByTagNameNS(XMLConstants.DRAWING_NS, "fillRef").item(0);
        assertEquals("1", fillRef.getAttribute("idx"));
        Element fillClr = (Element) fillRef.getElementsByTagNameNS(XMLConstants.DRAWING_NS, "schemeClr").item(0);
        assertEquals("lt1", fillClr.getAttribute("val"));

        // effectRef
        Element effectRef = (Element) style.getElementsByTagNameNS(XMLConstants.DRAWING_NS, "effectRef").item(0);
        assertEquals("0", effectRef.getAttribute("idx"));

        // fontRef
        Element fontRef = (Element) style.getElementsByTagNameNS(XMLConstants.DRAWING_NS, "fontRef").item(0);
        assertEquals("minor", fontRef.getAttribute("idx"));
    }

    @Test
    public void testDefaultThemeStyleWithoutText() {
        Element style = ShapeStyleXMLWriter.writeThemeStyleRef(doc, null, false);
        Element fillRef = (Element) style.getElementsByTagNameNS(XMLConstants.DRAWING_NS, "fillRef").item(0);
        Element fillClr = (Element) fillRef.getElementsByTagNameNS(XMLConstants.DRAWING_NS, "schemeClr").item(0);
        assertEquals("accent1", fillClr.getAttribute("val"));
    }

    @Test
    public void testCustomThemeRefNoShade() {
        ThemeStyleRef ref = ThemeStyleRef.of(1, "accent2", 3, "accent3", 0, "accent4", "major", "dk1");
        Element style = ShapeStyleXMLWriter.writeThemeStyleRef(doc, ref, false);

        Element lnRef = (Element) style.getElementsByTagNameNS(XMLConstants.DRAWING_NS, "lnRef").item(0);
        assertEquals("1", lnRef.getAttribute("idx"));
        Element lnClr = (Element) lnRef.getElementsByTagNameNS(XMLConstants.DRAWING_NS, "schemeClr").item(0);
        assertEquals("accent2", lnClr.getAttribute("val"));
        // No shade child
        NodeList shades = lnClr.getElementsByTagNameNS(XMLConstants.DRAWING_NS, "shade");
        assertEquals(0, shades.getLength());
    }

    // ===== Full applyStyle Tests =====

    @Test
    public void testApplyDefaultStyleAddsStyleElement() {
        Element shape = createMinimalShape();
        ShapeStyleXMLWriter.applyStyle(doc, shape, null, true);

        // Should have p:style element
        NodeList styles = shape.getElementsByTagNameNS(XMLConstants.PRESENTATION_NS, "style");
        assertEquals(1, styles.getLength());
    }

    @Test
    public void testApplyStyleWithFillAddsToSpPr() {
        Element shape = createMinimalShape();
        ShapeStyle style = ShapeStyle.withFill(ShapeFill.solid("FF0000"));
        ShapeStyleXMLWriter.applyStyle(doc, shape, style, false);

        // spPr should have solidFill
        Element spPr = (Element) shape.getElementsByTagNameNS(XMLConstants.PRESENTATION_NS, "spPr").item(0);
        NodeList fills = spPr.getElementsByTagNameNS(XMLConstants.DRAWING_NS, "solidFill");
        assertEquals(1, fills.getLength());
    }

    @Test
    public void testStyleInsertedBeforeTxBody() {
        Element shape = createMinimalShape();
        // Add txBody
        Element txBody = doc.createElementNS(XMLConstants.PRESENTATION_NS, "p:txBody");
        shape.appendChild(txBody);

        ShapeStyleXMLWriter.applyStyle(doc, shape, null, false);

        // p:style should come before p:txBody
        NodeList children = shape.getChildNodes();
        int styleIdx = -1, txBodyIdx = -1;
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element e) {
                if ("style".equals(e.getLocalName())) styleIdx = i;
                if ("txBody".equals(e.getLocalName())) txBodyIdx = i;
            }
        }
        assertTrue("p:style should exist", styleIdx >= 0);
        assertTrue("p:txBody should exist", txBodyIdx >= 0);
        assertTrue("p:style should come before p:txBody", styleIdx < txBodyIdx);
    }

    // Helper to create minimal <p:sp> with <p:spPr>
    private Element createMinimalShape() {
        Element shape = doc.createElementNS(XMLConstants.PRESENTATION_NS, "p:sp");
        Element spPr = doc.createElementNS(XMLConstants.PRESENTATION_NS, "p:spPr");
        shape.appendChild(spPr);
        return shape;
    }
}
