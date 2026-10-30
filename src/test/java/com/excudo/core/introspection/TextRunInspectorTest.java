package com.excudo.core.introspection;

import com.excudo.core.model.PPTXDocument;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.ShapeStyle;
import com.excudo.core.model.SlideShape;
import com.excudo.core.orchestration.PPTXOrchestratorImpl;
import com.excudo.core.orchestration.PresentationScaffolder;
import com.excudo.core.themes.ThemeManager;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.List;
import java.util.Locale;

import static org.junit.Assert.*;

/**
 * Resolved run colour / font / size and their sources. Default dark text on a
 * dark background went unnoticed because the inspection tools showed none of
 * this; the inspector reports what each run resolves to and where from.
 */
public class TextRunInspectorTest {

    private static final String A = "http://schemas.openxmlformats.org/drawingml/2006/main";
    private PPTXOrchestratorImpl orchestrator;
    private PPTXDocument doc;

    @Before
    public void setUp() throws Exception {
        orchestrator = new PPTXOrchestratorImpl();
        orchestrator.initialize(PresentationScaffolder.scaffoldDocument("excudo"));
        orchestrator.createSlide(1, "Title here", "slideLayout2");
        doc = orchestrator.getContext().get().getDocument();
    }

    @Test
    public void placeholderTitleInheritsFromMasterTitleStyle() {
        Element title = shapeWithText("Title here");
        clearRunColours(title); // Excudo writes an explicit colour on runs it creates
        TextRunInspector.RunReport run = only(title);
        assertNull("nothing set on the run", run.explicitColor());
        assertEquals("master titleStyle", run.color().source());
        assertTrue(run.color().value(), run.color().value().matches("#[0-9A-F]{6}"));
        assertEquals("master titleStyle", run.size().source());
    }

    @Test
    public void plainTextBoxWithoutColourResolvesThroughDefaults() {
        Element box = addBox("plain");
        clearRunColours(box);
        TextRunInspector.RunReport run = only(box);
        assertNull(run.explicitColor());
        // No run colour: either the shape style's fontRef or the presentation default supplies it
        assertTrue(run.color().source(), List.of("shape style fontRef", "presentation defaultTextStyle",
            "theme text colour (tx1)").contains(run.color().source()));
        assertTrue(run.color().value().matches("#[0-9A-F]{6}"));
    }

    @Test
    public void shapeStyleFontRefBeatsPresentationDefault() {
        Element box = addBox("styled");
        clearRunColours(box);
        Element fontRef = (Element) box.getElementsByTagNameNS(A, "fontRef").item(0);
        assertNotNull("added shapes carry a style", fontRef);
        while (fontRef.getFirstChild() != null) fontRef.removeChild(fontRef.getFirstChild());
        Element scheme = fontRef.getOwnerDocument().createElementNS(A, "a:schemeClr");
        scheme.setAttribute("val", "accent2");
        fontRef.appendChild(scheme);

        TextRunInspector.RunReport run = only(box);
        assertEquals("shape style fontRef", run.color().source());
        assertEquals(themeHex("accent2"), run.color().value());
    }

    @Test
    public void runColourWithModifiersIsApplied() {
        Element box = addBox("dimmed");
        Element rPr = runProps(box);
        Element fill = rPr.getOwnerDocument().createElementNS(A, "a:solidFill");
        Element srgb = rPr.getOwnerDocument().createElementNS(A, "a:srgbClr");
        srgb.setAttribute("val", "FFFFFF");
        Element lumMod = rPr.getOwnerDocument().createElementNS(A, "a:lumMod");
        lumMod.setAttribute("val", "50000");
        srgb.appendChild(lumMod);
        fill.appendChild(srgb);
        rPr.insertBefore(fill, rPr.getFirstChild());

        TextRunInspector.RunReport run = only(box);
        assertEquals("run", run.color().source());
        assertEquals("white at 50% luminance", "#808080", run.color().value());
        assertEquals(run.color().value(), run.explicitColor());
    }

    @Test
    public void noFillTextIsReportedAsInvisible() {
        Element box = addBox("ghost");
        Element rPr = runProps(box);
        rPr.insertBefore(rPr.getOwnerDocument().createElementNS(A, "a:noFill"), rPr.getFirstChild());
        assertTrue(only(box).color().value().contains("invisible"));
    }

    @Test
    public void paragraphDefaultBeatsInheritedButNotRun() {
        Element box = addBox("para");
        Element p = (Element) box.getElementsByTagNameNS(A, "p").item(0);
        Element pPr = (Element) p.getElementsByTagNameNS(A, "pPr").item(0);
        if (pPr == null) {
            pPr = p.getOwnerDocument().createElementNS(A, "a:pPr");
            p.insertBefore(pPr, p.getFirstChild());
        }
        Element defRPr = p.getOwnerDocument().createElementNS(A, "a:defRPr");
        defRPr.setAttribute("sz", "3200");
        pPr.appendChild(defRPr);
        TextRunInspector.RunReport run = only(box);
        if (run.explicitSizePt() == null) {
            assertEquals("paragraph defRPr", run.size().source());
            assertEquals("32", run.size().value());
        } else {
            assertEquals("run", run.size().source());
        }
    }

    // ========== Helpers ==========

    private Element addBox(String text) {
        int spid = orchestrator.addShape(1, SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(500_000, 3_000_000, 3_000_000, 1_000_000), text, "Box",
            ShapeStyle.defaultStyle()).getData().orElseThrow();
        return shapeById(spid);
    }

    private TextRunInspector.RunReport only(Element shape) {
        List<TextRunInspector.RunReport> runs = TextRunInspector.inspect(doc, 1, shape, orchestrator.getClrMap());
        assertEquals(1, runs.size());
        return runs.get(0);
    }

    private static void clearRunColours(Element shape) {
        NodeList rPrs = shape.getElementsByTagNameNS(A, "rPr");
        for (int i = 0; i < rPrs.getLength(); i++) {
            Element rPr = (Element) rPrs.item(i);
            for (String tag : new String[] {"solidFill", "noFill", "gradFill"}) {
                NodeList fills = rPr.getElementsByTagNameNS(A, tag);
                for (int j = fills.getLength() - 1; j >= 0; j--) rPr.removeChild(fills.item(j));
            }
        }
    }

    private Element runProps(Element shape) {
        Element r = (Element) shape.getElementsByTagNameNS(A, "r").item(0);
        Element rPr = (Element) r.getElementsByTagNameNS(A, "rPr").item(0);
        if (rPr == null) {
            rPr = r.getOwnerDocument().createElementNS(A, "a:rPr");
            r.insertBefore(rPr, r.getFirstChild());
        }
        return rPr;
    }

    private Element shapeWithText(String text) {
        NodeList sps = slide().getElementsByTagNameNS("*", "sp");
        for (int i = 0; i < sps.getLength(); i++) {
            Element sp = (Element) sps.item(i);
            if (sp.getTextContent().contains(text)) return sp;
        }
        fail("no shape with text " + text);
        return null;
    }

    private Element shapeById(int spid) {
        NodeList ids = slide().getElementsByTagNameNS("*", "cNvPr");
        for (int i = 0; i < ids.getLength(); i++) {
            Element c = (Element) ids.item(i);
            if (c.getAttribute("id").equals(String.valueOf(spid))) return (Element) c.getParentNode().getParentNode();
        }
        fail("no shape " + spid);
        return null;
    }

    private Document slide() {
        return doc.getSlideDocument(1);
    }

    private String themeHex(String scheme) {
        return ThemeManager.forDocument(doc).getThemeColor(scheme).withHash().toUpperCase(Locale.ROOT);
    }
}
