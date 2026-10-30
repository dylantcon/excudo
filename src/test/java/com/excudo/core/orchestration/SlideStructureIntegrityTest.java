package com.excudo.core.orchestration;

import com.excudo.core.commands.mutating.deck.DeleteSlideCommand;
import com.excudo.core.model.PPTXDocument;
import com.excudo.core.model.PackageIntegrityValidator;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

/**
 * Slide structure operations on a deck loaded from a file must keep the package
 * consistent: every slide in presentation.xml resolves to a part, every slide
 * part is listed once, and the slides come out in the expected order.
 *
 * <p>Regression for the review deck PowerPoint repaired by dropping two slides
 * and adding two blank ones: a mid-deck delete renamed later slide parts down
 * by one but left presentation.xml.rels pointing at the old names. Copy and
 * move were also stubs that reported success without doing anything.
 */
public class SlideStructureIntegrityTest {

    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    private PPTXOrchestratorImpl orchestrator;
    private File file;

    @Before
    public void setUp() throws Exception {
        PPTXOrchestratorImpl scaffold = new PPTXOrchestratorImpl();
        scaffold.initialize(com.excudo.core.orchestration.PresentationScaffolder.scaffoldDocument("excudo"));
        for (int i = 1; i <= 6; i++) scaffold.createSlide(i, "S" + i, "slideLayout2");
        file = tmp.newFile("deck.pptx");
        assertTrue(scaffold.savePresentation(file).isSuccess());
        reload();
    }

    @Test
    public void midDeckDeleteKeepsRelationshipsInStep() throws Exception {
        assertTrue(orchestrator.deleteSlide(3).isSuccess());
        assertOrder("S1", "S2", "S4", "S5", "S6");
        saveReload();
        assertOrder("S1", "S2", "S4", "S5", "S6");
    }

    @Test
    public void repeatedDeletesThenInsertsLikeTheReviewSession() throws Exception {
        // The session: delete 11 twice, then new slides at 12 and 13
        assertTrue(orchestrator.deleteSlide(4).isSuccess());
        assertTrue(orchestrator.deleteSlide(4).isSuccess());
        assertTrue(orchestrator.createSlide(4, "N4", "slideLayout2").isSuccess());
        assertTrue(orchestrator.createSlide(5, "N5", "slideLayout2").isSuccess());
        assertOrder("S1", "S2", "S3", "N4", "N5", "S6");
        saveReload();
        assertOrder("S1", "S2", "S3", "N4", "N5", "S6");
    }

    @Test
    public void copyActuallyCopiesAndTakesTheRightSource() throws Exception {
        assertTrue(orchestrator.copySlide(2, 5, "C2").isSuccess());   // source before target
        assertOrder("S1", "S2", "S3", "S4", "C2", "S5", "S6");
        assertTrue(orchestrator.copySlide(6, 1, "C5").isSuccess());   // source after target: S5 sits at 6
        assertOrder("C5", "S1", "S2", "S3", "S4", "C2", "S5", "S6");
        assertEquals("the copy carries the source's content", textOf(1).replace("C5", ""),
            textOf(7).replace("S5", ""));
        saveReload();
        assertOrder("C5", "S1", "S2", "S3", "S4", "C2", "S5", "S6");
    }

    @Test
    public void moveActuallyMovesBothWays() throws Exception {
        assertTrue(orchestrator.moveSlide(2, 5).isSuccess());
        assertOrder("S1", "S3", "S4", "S5", "S2", "S6");
        assertTrue(orchestrator.moveSlide(6, 1).isSuccess());
        assertOrder("S6", "S1", "S3", "S4", "S5", "S2");
        saveReload();
        assertOrder("S6", "S1", "S3", "S4", "S5", "S2");
    }

    @Test
    public void mixedSequenceAfterCopiesBreakRIdOrder() throws Exception {
        // A copy gets a fresh rId, so rId order stops following slide order;
        // inserts and deletes after that must still work by slide number
        assertTrue(orchestrator.copySlide(1, 3, "C1").isSuccess());
        assertTrue(orchestrator.createSlide(2, "N2", "slideLayout2").isSuccess());
        assertTrue(orchestrator.deleteSlide(5).isSuccess());
        assertTrue(orchestrator.moveSlide(1, 7).isSuccess());
        assertOrder("N2", "S2", "C1", "S4", "S5", "S6", "S1");
        saveReload();
        assertOrder("N2", "S2", "C1", "S4", "S5", "S6", "S1");
    }

    @Test
    public void copyingAnAnimatedSlideKeepsItsAnimationsOnItsOwnShapes() throws Exception {
        int a = box(2, 500_000), b = box(2, 3_000_000);
        for (int spid : new int[] {a, b}) {
            assertTrue(orchestrator.addAnimation(2, com.excudo.core.model.AnimationBinding.builder()
                .target(spid).type(com.excudo.core.model.AnimationType.FADE).entrance()
                .duration("500").delay("0").animationGroup("on-click").build()).isSuccess());
        }
        assertTrue(orchestrator.copySlide(2, 4, "C2").isSuccess());
        // assertOrder validates: no animation or build entry targets a shape
        // that isn't on the slide (copy used to renumber every shape to one id
        // and leave the animations on the old ones)
        assertOrder("S1", "S2", "S3", "C2", "S4", "S5", "S6");
        Document copy = orchestrator.getContext().get().getDocument().getSlideDocument(4);
        NodeList ids = copy.getElementsByTagNameNS("*", "cNvPr");
        java.util.Set<String> distinct = new java.util.HashSet<>();
        for (int i = 0; i < ids.getLength(); i++) distinct.add(((Element) ids.item(i)).getAttribute("id"));
        assertEquals("every shape on the copy keeps a distinct id", ids.getLength(), distinct.size());
        assertEquals(2, copy.getElementsByTagNameNS("*", "spTgt").getLength() / 2);
        saveReload();
        assertOrder("S1", "S2", "S3", "C2", "S4", "S5", "S6");
    }

    private int box(int slide, long x) {
        return orchestrator.addShape(slide, com.excudo.core.model.SlideShape.ShapeType.RECTANGLE,
            new com.excudo.core.model.ShapeGeometry(x, 2_000_000, 2_000_000, 1_000_000),
            "", "Box", com.excudo.core.model.ShapeStyle.defaultStyle()).getData().orElseThrow();
    }

    @Test
    public void undoingADeleteBringsTheSlideBack() throws Exception {
        DeleteSlideCommand delete = new DeleteSlideCommand(3, false, "test", orchestrator);
        delete.execute();
        assertOrder("S1", "S2", "S4", "S5", "S6");
        delete.undo();
        assertOrder("S1", "S2", "S3", "S4", "S5", "S6");
        saveReload();
        assertOrder("S1", "S2", "S3", "S4", "S5", "S6");
    }

    @Test
    public void saveRefusesAPackagePowerPointWouldRepair() throws Exception {
        // Recreate the review deck's defect: a slide relationship pointing at a
        // part that doesn't exist, and the real part left unlisted
        PPTXDocument doc = orchestrator.getContext().get().getDocument();
        Document rels = doc.getXmlPart("ppt/_rels/presentation.xml.rels");
        NodeList list = rels.getElementsByTagNameNS("*", "Relationship");
        for (int i = 0; i < list.getLength(); i++) {
            Element rel = (Element) list.item(i);
            if (rel.getAttribute("Target").equals("slides/slide6.xml")) rel.setAttribute("Target", "slides/slide8.xml");
        }
        doc.putXmlPart("ppt/_rels/presentation.xml.rels", rels);

        List<String> problems = PackageIntegrityValidator.validate(doc);
        assertTrue(problems.toString(), problems.stream().anyMatch(p -> p.contains("slide8.xml, which doesn't exist")));
        assertTrue(problems.toString(), problems.stream().anyMatch(p -> p.contains("slide6.xml exists but isn't in the slide list")));
        assertFalse("the broken package must not be written", orchestrator.savePresentation(tmp.newFile("broken.pptx")).isSuccess());
    }

    // ========== Helpers ==========

    private void reload() throws Exception {
        orchestrator = new PPTXOrchestratorImpl();
        assertTrue(orchestrator.loadPresentation(file).isSuccess());
    }

    private void saveReload() throws Exception {
        assertTrue(orchestrator.savePresentation(file).isSuccess());
        reload();
    }

    /** Asserts the package is consistent and slide titles come in this order. */
    private void assertOrder(String... expected) {
        PPTXDocument doc = orchestrator.getContext().get().getDocument();
        assertEquals("package problems", List.of(), PackageIntegrityValidator.validate(doc));
        assertEquals(List.of(expected), titlesInPresentationOrder(doc));
    }

    private List<String> titlesInPresentationOrder(PPTXDocument doc) {
        Document rels = doc.getXmlPart("ppt/_rels/presentation.xml.rels");
        java.util.Map<String, String> target = new java.util.HashMap<>();
        NodeList relNodes = rels.getElementsByTagNameNS("*", "Relationship");
        for (int i = 0; i < relNodes.getLength(); i++) {
            Element r = (Element) relNodes.item(i);
            target.put(r.getAttribute("Id"), "ppt/" + r.getAttribute("Target"));
        }
        List<String> titles = new ArrayList<>();
        NodeList ids = doc.getXmlPart("ppt/presentation.xml").getElementsByTagNameNS("*", "sldId");
        for (int i = 0; i < ids.getLength(); i++) {
            String rId = ((Element) ids.item(i)).getAttributeNS(
                "http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id");
            titles.add(firstText(doc.getXmlPart(target.get(rId))));
        }
        return titles;
    }

    private String textOf(int slideNumber) {
        return allText(orchestrator.getContext().get().getDocument().getSlideDocument(slideNumber));
    }

    private static String firstText(Document slide) {
        NodeList t = slide.getElementsByTagNameNS("*", "t");
        return t.getLength() == 0 ? "?" : t.item(0).getTextContent();
    }

    private static String allText(Document slide) {
        StringBuilder sb = new StringBuilder();
        NodeList t = slide.getElementsByTagNameNS("*", "t");
        for (int i = 0; i < t.getLength(); i++) sb.append(t.item(i).getTextContent()).append('|');
        return sb.toString();
    }
}
