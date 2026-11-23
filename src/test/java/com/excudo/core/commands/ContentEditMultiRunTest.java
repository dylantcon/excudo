package com.excudo.core.commands;

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

import static org.junit.Assert.*;

/**
 * Replacing a shape's text must replace all of it. PowerPoint often splits a
 * sentence into several runs (spell-check marks, partial formatting); only the
 * first run used to be rewritten, leaving the rest of the old sentence after
 * the new text.
 */
public class ContentEditMultiRunTest {

    private static final String A = "http://schemas.openxmlformats.org/drawingml/2006/main";
    private PPTXOrchestratorImpl orchestrator;
    private PPTXDocument doc;
    private int spid;

    @Before
    public void setUp() throws Exception {
        orchestrator = new PPTXOrchestratorImpl();
        orchestrator.initialize(PresentationScaffolder.scaffoldDocument("excudo"));
        orchestrator.createSlide(1, "Edits", "slideLayout7");
        doc = orchestrator.getContext().get().getDocument();
        spid = orchestrator.addShape(1, SlideShape.ShapeType.RECTANGLE,
            new ShapeGeometry(0, 0, 4_000_000, 1_000_000), "placeholder", "Footnote",
            ShapeStyle.defaultStyle()).getData().orElseThrow();
        splitIntoRunsAndParagraphs();
    }

    @Test
    public void singleLineReplaceReplacesEveryRunAndParagraph() {
        assertTrue(orchestrator.editShapeText(1, spid, "*Simplified: one new sentence.").isSuccess());

        Element txBody = txBody();
        NodeList paragraphs = txBody.getElementsByTagNameNS(A, "p");
        assertEquals("one paragraph", 1, paragraphs.getLength());
        NodeList runs = txBody.getElementsByTagNameNS(A, "r");
        assertEquals("one run", 1, runs.getLength());
        assertEquals("*Simplified: one new sentence.", txBody.getElementsByTagNameNS(A, "t").item(0).getTextContent());
        Element rPr = (Element) ((Element) runs.item(0)).getElementsByTagNameNS(A, "rPr").item(0);
        assertEquals("the kept run keeps its formatting", "1", rPr.getAttribute("i"));
    }

    /** "*Simplified: " | "GitFlow" | " merges..." in one paragraph, plus a second paragraph. */
    private void splitIntoRunsAndParagraphs() {
        Element txBody = txBody();
        Document d = txBody.getOwnerDocument();
        Element p = (Element) txBody.getElementsByTagNameNS(A, "p").item(0);
        Element first = (Element) p.getElementsByTagNameNS(A, "r").item(0);
        ((Element) first.getElementsByTagNameNS(A, "rPr").item(0)).setAttribute("i", "1");
        first.getElementsByTagNameNS(A, "t").item(0).setTextContent("*Simplified: ");
        for (String text : new String[] {"GitFlow", " merges a hotfix into main and develop."}) {
            Element r = (Element) first.cloneNode(true);
            r.getElementsByTagNameNS(A, "t").item(0).setTextContent(text);
            p.insertBefore(r, null);
        }
        Element second = (Element) p.cloneNode(true);
        txBody.appendChild(second);
        doc.putSlideDocument(1, d);
    }

    private Element txBody() {
        NodeList ids = doc.getSlideDocument(1).getElementsByTagNameNS("*", "cNvPr");
        for (int i = 0; i < ids.getLength(); i++) {
            Element c = (Element) ids.item(i);
            if (c.getAttribute("id").equals(String.valueOf(spid))) {
                Element sp = (Element) c.getParentNode().getParentNode();
                return (Element) sp.getElementsByTagNameNS("*", "txBody").item(0);
            }
        }
        fail("no shape " + spid);
        return null;
    }
}
