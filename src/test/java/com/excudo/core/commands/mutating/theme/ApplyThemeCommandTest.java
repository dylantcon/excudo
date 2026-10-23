package com.excudo.core.commands.mutating.theme;

import com.excudo.core.model.PPTXDocument;
import com.excudo.core.orchestration.PPTXOrchestratorImpl;
import com.excudo.core.orchestration.PresentationScaffolder;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.List;

import static org.junit.Assert.*;

/**
 * apply-theme on in-memory presentations (no extracted directory), which is
 * what new-presentation and load produce.
 */
public class ApplyThemeCommandTest {

    private PPTXOrchestratorImpl orchestrator;
    private PPTXDocument doc;

    @Before
    public void setUp() throws Exception {
        doc = PresentationScaffolder.scaffoldDocument("minimal");
        assertNull("precondition: in-memory deck", doc.getExtractedDir());
        orchestrator = new PPTXOrchestratorImpl();
        orchestrator.initialize(doc);
        orchestrator.createSlide(1, "Themed Slide", "slideLayout2");
    }

    private static String majorLatinFont(Document themeDoc) {
        NodeList major = themeDoc.getElementsByTagNameNS("*", "majorFont");
        Element latin = (Element) ((Element) major.item(0)).getElementsByTagNameNS("*", "latin").item(0);
        return latin.getAttribute("typeface");
    }

    private static String slideLayoutTarget(PPTXDocument d, int slide) {
        Document rels = d.getXmlPart("ppt/slides/_rels/slide" + slide + ".xml.rels");
        NodeList nodes = rels.getElementsByTagNameNS("*", "Relationship");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element rel = (Element) nodes.item(i);
            if (rel.getAttribute("Type").endsWith("/slideLayout")) return rel.getAttribute("Target");
        }
        return null;
    }

    @Test
    public void appliesThemeToInMemoryDeck() {
        assertEquals("Inter", majorLatinFont(doc.getXmlPart("ppt/theme/theme1.xml")));

        new ApplyThemeCommand("excudo", orchestrator, null).execute();

        assertEquals("Segoe UI", majorLatinFont(doc.getXmlPart("ppt/theme/theme1.xml")));
        assertEquals("slide keeps its layout slot",
            "../slideLayouts/slideLayout2.xml", slideLayoutTarget(doc, 1));
        assertTrue(doc.getContentTypes().hasOverride("/ppt/theme/theme1.xml"));
    }

    @Test
    public void themedDeckSavesAndReloads() throws Exception {
        new ApplyThemeCommand("corporate", orchestrator, null).execute();

        File out = File.createTempFile("apply-theme-test", ".pptx");
        try {
            doc.save(out);
            PPTXDocument reloaded = PPTXDocument.loadFromZip(out);
            assertEquals(1, reloaded.getSlideCount());
            assertEquals("Calibri", majorLatinFont(reloaded.getXmlPart("ppt/theme/theme1.xml")));
            assertNotNull(reloaded.getXmlPart("ppt/slideLayouts/slideLayout2.xml"));
        } finally {
            out.delete();
        }
    }

    @Test
    public void pickLayout_matchesByType() {
        List<String> newTypes = List.of("title", "obj", "secHead", "twoObj");
        assertEquals("same slot, same type", 2, ApplyThemeCommand.pickLayout(2, "obj", newTypes));
        assertEquals("type moved slots", 4, ApplyThemeCommand.pickLayout(1, "twoObj", newTypes));
        assertEquals("unknown type keeps slot", 3, ApplyThemeCommand.pickLayout(3, "cust", newTypes));
        assertEquals("slot gone, falls back to content", 2, ApplyThemeCommand.pickLayout(9, "cust", newTypes));
    }
}
