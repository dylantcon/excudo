package com.excudo.core.introspection;

import com.excudo.core.metrics.LayoutIssue;
import com.excudo.core.model.PPTXDocument;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.ShapeStyle;
import com.excudo.core.model.SlideShape;
import com.excudo.core.orchestration.PPTXOrchestratorImpl;
import com.excudo.core.orchestration.PresentationScaffolder;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.List;

import static org.junit.Assert.*;

/** Text too close in colour to what's behind it, by WCAG contrast ratio. */
public class ContrastCheckerTest {

    private static final String P = "http://schemas.openxmlformats.org/presentationml/2006/main";
    private static final String A = "http://schemas.openxmlformats.org/drawingml/2006/main";
    private PPTXOrchestratorImpl orchestrator;
    private PPTXDocument doc;

    @Before
    public void setUp() throws Exception {
        orchestrator = new PPTXOrchestratorImpl();
        orchestrator.initialize(PresentationScaffolder.scaffoldDocument("excudo"));
        orchestrator.createSlide(1, "Contrast", "slideLayout7");
        doc = orchestrator.getContext().get().getDocument();
    }

    @Test
    public void ratioMatchesWcag() {
        assertEquals(21.0, ContrastChecker.ratio("#000000", "#FFFFFF"), 0.01);
        assertEquals(1.0, ContrastChecker.ratio("#777777", "#777777"), 0.01);
        assertEquals(4.48, ContrastChecker.ratio("#777777", "#FFFFFF"), 0.01);
    }

    @Test
    public void darkTextOnDarkBackgroundIsFlagged() {
        // The handoff's case: default-dark text on a dark background
        setSlideBackground("111111");
        int spid = textBox("hard to read", 1_000_000, null, "222222", 1800);
        LayoutIssue issue = onlyIssueFor(spid);
        assertEquals("1.1:1 is nearly invisible", LayoutIssue.Type.LOW_CONTRAST, issue.getType());
        assertTrue(issue.getDescription(), issue.getDescription().contains("#222222"));
        assertTrue(issue.getDescription(), issue.getDescription().contains("#111111 (slide background)"));
        assertTrue(issue.getSuggestion(), issue.getSuggestion().contains("#FFFFFF"));
    }

    @Test
    public void textOnAShapeUnderneathIsCheckedAgainstThatShape() {
        setSlideBackground("FFFFFF");
        int under = textBox("", 500_000, "58A6FF", null, 1800);         // blue panel, no text
        int label = textBox("label", 1_000_000, null, "79C0FF", 1800);  // light blue text box on it
        LayoutIssue issue = onlyIssueFor(label);
        assertTrue(issue.getDescription(), issue.getDescription().contains("shape " + under));
        assertTrue(issue.getDescription(), issue.getDescription().contains("#58A6FF"));
    }

    @Test
    public void largeTextUsesTheLowerThreshold() {
        setSlideBackground("FFFFFF");
        // #949494 on white is ~3.03:1: fails 4.5 (normal) but passes 3.0 (large)
        int small = textBox("small", 500_000, null, "949494", 1400);
        int large = textBox("large", 2_500_000, null, "949494", 2400);
        List<LayoutIssue> issues = issues();
        assertTrue(issues.stream().anyMatch(i -> i.getSpids()[0] == small));
        assertFalse(issues.stream().anyMatch(i -> i.getSpids()[0] == large));
    }

    @Test
    public void belowAaButReadableIsOnlyAnAdvisory() {
        // White on #58A6FF is ~2.5:1: fails AA, common for accent labels
        setSlideBackground("FFFFFF");
        int spid = textBox("label", 1_000_000, "58A6FF", "FFFFFF", 1800);
        LayoutIssue issue = onlyIssueFor(spid);
        assertEquals(LayoutIssue.Type.CONTRAST_ADVISORY, issue.getType());
        assertTrue(issue.getDescription(), issue.getDescription().contains("below the WCAG AA"));
    }

    @Test
    public void legibleTextPasses() {
        setSlideBackground("0D1117");
        int spid = textBox("readable", 1_000_000, null, "E6EDF3", 1800);
        assertFalse(issues().stream().anyMatch(i -> i.getSpids()[0] == spid));
    }

    @Test
    public void pictureFillIsReportedAsUncheckedNotGuessed() {
        int spid = textBox("on a photo", 1_000_000, null, "FFFFFF", 1800);
        Element spPr = (Element) shape(spid).getElementsByTagNameNS(P, "spPr").item(0);
        removeFills(spPr);
        spPr.appendChild(spPr.getOwnerDocument().createElementNS(A, "a:blipFill"));
        LayoutIssue issue = onlyIssueFor(spid);
        assertEquals(LayoutIssue.Type.CONTRAST_ADVISORY, issue.getType());
        assertTrue(issue.getDescription(), issue.getDescription().contains("not checked"));
    }

    // ========== Helpers ==========

    private List<LayoutIssue> issues() {
        List<SlideShape> shapes = doc.getParsedSlideData(1,
            (dom, n) -> new com.excudo.xml.parsers.SlideXMLParser().parseSlide(dom, n)).getShapeRegistry().getAllShapes();
        return ContrastChecker.check(doc, 1, shapes, orchestrator.getClrMap());
    }

    private LayoutIssue onlyIssueFor(int spid) {
        List<LayoutIssue> mine = issues().stream().filter(i -> i.getSpids()[0] == spid).toList();
        assertEquals(mine.toString(), 1, mine.size());
        return mine.get(0);
    }

    /** A shape with an optional solid fill (null = none) and text in the given colour and size. */
    private int textBox(String text, long x, String fillHex, String textHex, int sz) {
        int spid = orchestrator.addShape(1, SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(x, 1_000_000, 4_000_000, 2_000_000), text, "Box",
            ShapeStyle.defaultStyle()).getData().orElseThrow();
        Element sp = shape(spid);
        Document d = sp.getOwnerDocument();
        Element spPr = (Element) sp.getElementsByTagNameNS(P, "spPr").item(0);
        removeFills(spPr);
        Element prstGeom = (Element) spPr.getElementsByTagNameNS(A, "prstGeom").item(0);
        Element fill = fillHex != null ? solid(d, fillHex) : d.createElementNS(A, "a:noFill");
        spPr.insertBefore(fill, prstGeom != null ? prstGeom.getNextSibling() : null);
        NodeList rPrs = sp.getElementsByTagNameNS(A, "rPr");
        for (int i = 0; i < rPrs.getLength(); i++) {
            Element rPr = (Element) rPrs.item(i);
            removeFills(rPr);
            rPr.setAttribute("sz", String.valueOf(sz));
            rPr.removeAttribute("b");
            if (textHex != null) rPr.insertBefore(solid(d, textHex), rPr.getFirstChild());
        }
        doc.putSlideDocument(1, d);
        return spid;
    }

    private void setSlideBackground(String hex) {
        Document d = doc.getSlideDocument(1);
        Element cSld = (Element) d.getElementsByTagNameNS(P, "cSld").item(0);
        NodeList old = cSld.getElementsByTagNameNS(P, "bg");
        for (int i = old.getLength() - 1; i >= 0; i--) cSld.removeChild(old.item(i));
        Element bg = d.createElementNS(P, "p:bg");
        Element bgPr = d.createElementNS(P, "p:bgPr");
        bgPr.appendChild(solid(d, hex));
        bgPr.appendChild(d.createElementNS(A, "a:effectLst"));
        bg.appendChild(bgPr);
        cSld.insertBefore(bg, cSld.getFirstChild());
        doc.putSlideDocument(1, d);
    }

    private static Element solid(Document d, String hex) {
        Element fill = d.createElementNS(A, "a:solidFill");
        Element c = d.createElementNS(A, "a:srgbClr");
        c.setAttribute("val", hex);
        fill.appendChild(c);
        return fill;
    }

    private static void removeFills(Element parent) {
        for (String tag : new String[] {"solidFill", "noFill", "gradFill", "blipFill", "pattFill"}) {
            for (org.w3c.dom.Node n = parent.getFirstChild(); n != null; ) {
                org.w3c.dom.Node next = n.getNextSibling();
                if (n instanceof Element e && tag.equals(e.getLocalName())) parent.removeChild(e);
                n = next;
            }
        }
    }

    private Element shape(int spid) {
        NodeList ids = doc.getSlideDocument(1).getElementsByTagNameNS(P, "cNvPr");
        for (int i = 0; i < ids.getLength(); i++) {
            Element c = (Element) ids.item(i);
            if (c.getAttribute("id").equals(String.valueOf(spid))) return (Element) c.getParentNode().getParentNode();
        }
        fail("no shape " + spid);
        return null;
    }
}
