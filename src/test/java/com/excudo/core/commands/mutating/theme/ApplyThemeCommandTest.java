package com.excudo.core.commands.mutating.theme;

import com.excudo.core.model.PPTXDocument;
import com.excudo.core.orchestration.PPTXOrchestratorImpl;
import com.excudo.core.orchestration.PresentationScaffolder;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import com.excudo.test.utils.PackageIntegrity;

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

    /** Every theme / master / layout / slide-rels / content-types part, serialized. */
    private static java.util.Map<String, String> styleParts(PPTXDocument d) throws Exception {
        java.util.Map<String, String> out = new java.util.TreeMap<>();
        for (String prefix : new String[]{"ppt/theme/", "ppt/slideMasters/", "ppt/slideLayouts/", "ppt/slides/_rels/"}) {
            for (String part : d.getPartNamesByPrefix(prefix)) out.put(part, serialize(d.getXmlPart(part)));
        }
        out.put("[Content_Types].xml", serialize(d.getContentTypesDoc()));
        return out;
    }

    private static String serialize(Document dom) throws Exception {
        java.io.StringWriter w = new java.io.StringWriter();
        javax.xml.transform.TransformerFactory.newInstance().newTransformer()
            .transform(new javax.xml.transform.dom.DOMSource(dom), new javax.xml.transform.stream.StreamResult(w));
        return w.toString();
    }

    private static String themeFontOfMaster(PPTXDocument d, int master) {
        Document rels = d.getXmlPart("ppt/slideMasters/_rels/slideMaster" + master + ".xml.rels");
        NodeList nodes = rels.getElementsByTagNameNS("*", "Relationship");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element rel = (Element) nodes.item(i);
            if (rel.getAttribute("Type").endsWith("/theme")) {
                String target = rel.getAttribute("Target");
                return majorLatinFont(d.getXmlPart("ppt/theme/" + target.substring(target.lastIndexOf('/') + 1)));
            }
        }
        return null;
    }

    private static int masterOfLayoutTarget(PPTXDocument d, String target) {
        String n = target.replaceAll(".*slideLayout(\\d+)\\.xml$", "$1");
        Document rels = d.getXmlPart("ppt/slideLayouts/_rels/slideLayout" + n + ".xml.rels");
        Element rel = (Element) rels.getElementsByTagNameNS("*", "Relationship").item(0);
        return Integer.parseInt(rel.getAttribute("Target").replaceAll(".*slideMaster(\\d+)\\.xml$", "$1"));
    }

    /** Equal part sets, reporting which parts differ instead of dumping both packages. */
    private static void assertSameParts(java.util.Map<String, String> expected, java.util.Map<String, String> actual) {
        java.util.Set<String> keys = new java.util.TreeSet<>(expected.keySet());
        keys.addAll(actual.keySet());
        java.util.List<String> diffs = new java.util.ArrayList<>();
        for (String k : keys) {
            String e = expected.get(k), a = actual.get(k);
            if (e == null) diffs.add("added " + k);
            else if (a == null) diffs.add("removed " + k);
            else if (!e.equals(a)) {
                int i = 0;
                while (i < Math.min(e.length(), a.length()) && e.charAt(i) == a.charAt(i)) i++;
                diffs.add("changed " + k + " at char " + i + ": expected ..." + e.substring(i, Math.min(e.length(), i + 80))
                    + "... got ..." + a.substring(i, Math.min(a.length(), i + 80)) + "...");
            }
        }
        if (!diffs.isEmpty()) fail(String.join("\n", diffs));
    }

    @Test
    public void undoRestoresEveryPartExactly() throws Exception {
        java.util.Map<String, String> before = styleParts(doc);
        ApplyThemeCommand cmd = new ApplyThemeCommand("excudo", orchestrator, null);
        cmd.execute();
        assertNotEquals(before, styleParts(doc));
        assertTrue(cmd.canUndo());

        cmd.undo();
        assertSameParts(before, styleParts(doc));
        assertEquals("Inter", majorLatinFont(doc.getXmlPart("ppt/theme/theme1.xml")));
        assertFalse(cmd.canUndo());
    }

    @Test
    public void appliesToEveryMasterOfAMultiMasterDeck() throws Exception {
        PackageIntegrity.addSecondMaster(doc, 9, 10);
        orchestrator.createSlide(2, "On master 2", "slideLayout10");
        String slide2Type = doc.getXmlPart("ppt/slideLayouts/slideLayout10.xml").getDocumentElement().getAttribute("type");
        PackageIntegrity.assertIntact(doc);

        new ApplyThemeCommand("corporate", orchestrator, null).execute();

        assertEquals("Calibri", themeFontOfMaster(doc, 1));
        assertEquals("Calibri", themeFontOfMaster(doc, 2));
        PackageIntegrity.assertIntact(doc);

        String target = slideLayoutTarget(doc, 2);
        assertEquals("slide stays on its own master", 2, masterOfLayoutTarget(doc, target));
        String n = target.replaceAll(".*slideLayout(\\d+)\\.xml$", "$1");
        assertEquals("slide keeps its layout type", slide2Type,
            doc.getXmlPart("ppt/slideLayouts/slideLayout" + n + ".xml").getDocumentElement().getAttribute("type"));
        assertEquals("slide 1 untouched", "../slideLayouts/slideLayout2.xml", slideLayoutTarget(doc, 1));
    }

    @Test
    public void masterOptionRestylesOnlyThatMaster() throws Exception {
        PackageIntegrity.addSecondMaster(doc, 9, 10);
        java.util.Map<String, String> before = styleParts(doc);

        ApplyThemeCommand cmd = new ApplyThemeCommand("corporate", 2, orchestrator, null);
        cmd.execute();
        assertEquals("Inter", themeFontOfMaster(doc, 1));
        assertEquals("Calibri", themeFontOfMaster(doc, 2));
        PackageIntegrity.assertIntact(doc);

        cmd.undo();
        assertSameParts(before, styleParts(doc));
    }

    @Test
    public void unknownMasterIsRejectedWithoutChanges() throws Exception {
        java.util.Map<String, String> before = styleParts(doc);
        try {
            new ApplyThemeCommand("corporate", 7, orchestrator, null).execute();
            fail("expected failure");
        } catch (com.excudo.core.commands.CommandExecutionException expected) {
            assertTrue(expected.getMessage().contains("slideMaster7"));
        }
        assertSameParts(before, styleParts(doc));
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
