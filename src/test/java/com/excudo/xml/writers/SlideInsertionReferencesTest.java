package com.excudo.xml.writers;

import com.excudo.core.model.PPTXDocument;
import com.excudo.core.orchestration.PPTXOrchestratorImpl;
import com.excudo.core.orchestration.PresentationScaffolder;
import com.excudo.test.utils.PackageIntegrity;
import org.junit.Test;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Inserting a slide renumbers presentation-level relationships; every
 * reference to them in presentation.xml must follow -- not just the slide
 * list. (A second slide master's sldMasterId used to keep its old rId and
 * end up pointing at tableStyles.xml, which PowerPoint refuses to open.)
 */
public class SlideInsertionReferencesTest {

    private static final String RNS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";

    /** Expected relationship type suffix for each presentation.xml element carrying an r:id. */
    private static final Map<String, String> EXPECTED_TYPE = Map.of(
        "sldMasterId", "/slideMaster",
        "notesMasterId", "/notesMaster",
        "handoutMasterId", "/handoutMaster",
        "sldId", "/slide");

    /** Every r:* attribute in presentation.xml resolves, and to the right kind of part. */
    private static void assertPresentationReferencesResolve(PPTXDocument doc) {
        Map<String, String> typeById = new HashMap<>();
        NodeList rels = doc.getXmlPart("ppt/_rels/presentation.xml.rels").getElementsByTagNameNS("*", "Relationship");
        for (int i = 0; i < rels.getLength(); i++) {
            Element rel = (Element) rels.item(i);
            assertNull("duplicate rId " + rel.getAttribute("Id"), typeById.put(rel.getAttribute("Id"), rel.getAttribute("Type")));
        }
        check(doc.getPresentationXml().getDocumentElement(), typeById);
    }

    private static void check(Element element, Map<String, String> typeById) {
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Attr attr = (Attr) attributes.item(i);
            if (!RNS.equals(attr.getNamespaceURI())) continue;
            String type = typeById.get(attr.getValue());
            assertNotNull(element.getLocalName() + " r:" + attr.getLocalName() + "=" + attr.getValue() + " is dangling", type);
            String expected = EXPECTED_TYPE.get(element.getLocalName());
            if (expected != null) {
                assertTrue(element.getLocalName() + " " + attr.getValue() + " points at " + type, type.endsWith(expected));
            }
        }
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element e) check(e, typeById);
        }
    }

    @Test
    public void secondMasterReferenceSurvivesSlideInsertion() throws Exception {
        PPTXDocument doc = PresentationScaffolder.scaffoldDocument("minimal");
        PPTXOrchestratorImpl orchestrator = new PPTXOrchestratorImpl();
        orchestrator.initialize(doc);
        orchestrator.createSlide(1, "First", "slideLayout2");
        PackageIntegrity.addSecondMaster(doc, 9, 10);
        assertPresentationReferencesResolve(doc);

        orchestrator.createSlide(1, "Inserted before", "slideLayout2");
        orchestrator.createSlide(3, "Appended", "slideLayout10");

        assertPresentationReferencesResolve(doc);
        PackageIntegrity.assertIntact(doc);
    }

    @Test
    public void notesMasterReferenceSurvivesSlideInsertionInARealDeck() throws Exception {
        File deck = new File("test-pptx-samples/generalist_test_file.pptx");
        if (!deck.exists()) fail("Required fixture not found: " + deck.getAbsolutePath());
        PPTXDocument doc = PPTXDocument.loadFromZip(deck);
        try {
            PPTXOrchestratorImpl orchestrator = new PPTXOrchestratorImpl();
            orchestrator.initialize(doc);
            assertPresentationReferencesResolve(doc);

            orchestrator.createSlide(1, "Inserted first", "slideLayout2");
            orchestrator.createSlide(4, "Inserted middle", "slideLayout2");

            assertPresentationReferencesResolve(doc);
        } finally {
            doc.close();
        }
    }
}
