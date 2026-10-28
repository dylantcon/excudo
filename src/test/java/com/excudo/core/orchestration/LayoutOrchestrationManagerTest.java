package com.excudo.core.orchestration;

import com.excudo.core.model.PPTXDocument;
import com.excudo.core.results.ExecutionResult;
import com.excudo.core.themes.LayoutDefinition;
import com.excudo.core.themes.PlaceholderDefinition;
import com.excudo.core.utils.XMLFactoryProvider;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Layout CRUD on in-memory decks (no extracted directory), with package
 * integrity checked after every mutation.
 */
public class LayoutOrchestrationManagerTest {

    private static final String PNS = "http://schemas.openxmlformats.org/presentationml/2006/main";
    private static final String RNS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final String LAYOUT_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout";

    private PPTXOrchestratorImpl orchestrator;
    private PPTXDocument doc;
    private LayoutOrchestrationManager layouts;

    @Before
    public void setUp() throws Exception {
        doc = PresentationScaffolder.scaffoldDocument("minimal");
        assertNull(doc.getExtractedDir());
        orchestrator = new PPTXOrchestratorImpl();
        orchestrator.initialize(doc);
        orchestrator.createSlide(1, "Uses layout 2", "slideLayout2");
        orchestrator.createSlide(2, "Uses layout 9", "slideLayout9");
        layouts = new LayoutOrchestrationManager(orchestrator.getContext().orElseThrow());
        assertPackageIntact();
    }

    // ---------- integrity ----------

    private void assertPackageIntact() {
        com.excudo.test.utils.PackageIntegrity.assertIntact(doc);
    }

    private String slideLayoutTarget(int slide) {
        Document rels = doc.getXmlPart("ppt/slides/_rels/slide" + slide + ".xml.rels");
        NodeList nodes = rels.getElementsByTagNameNS("*", "Relationship");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element rel = (Element) nodes.item(i);
            if (LAYOUT_REL.equals(rel.getAttribute("Type"))) return rel.getAttribute("Target");
        }
        return null;
    }

    private String layoutName(int n) {
        Element cSld = (Element) doc.getXmlPart("ppt/slideLayouts/slideLayout" + n + ".xml")
            .getElementsByTagNameNS(PNS, "cSld").item(0);
        return cSld.getAttribute("name");
    }

    private int layoutCount() {
        int count = 0;
        for (String p : doc.getPartNamesByPrefix("ppt/slideLayouts/slideLayout")) if (p.endsWith(".xml")) count++;
        return count;
    }

    // ---------- tests ----------

    @Test
    public void duplicateCreatesAttachedLayout() throws Exception {
        ExecutionResult<String> r = layouts.duplicateLayout("slideLayout2", "My Copy");
        assertTrue(r.getMessage(), r.isSuccess());
        assertEquals("slideLayout11", r.getData().orElseThrow());
        assertEquals("My Copy", layoutName(11));
        assertPackageIntact();
        assertNotNull("layout list refreshed",
            orchestrator.getContext().orElseThrow().getContextService().getLayoutManager().getLayoutInfo("slideLayout11"));
    }

    @Test
    public void addLayoutFromDefinition() {
        PlaceholderDefinition title = new PlaceholderDefinition("title", null, 457200L, 274638L, 8229600L, 1143000L);
        LayoutDefinition def = new LayoutDefinition("dynamic", "Custom", LayoutDefinition.LayoutType.TITLE_ONLY,
            List.of(title), true);
        ExecutionResult<String> r = orchestrator.addLayout(def);
        assertTrue(r.getMessage(), r.isSuccess());
        assertPackageIntact();
        assertEquals("titleOnly", doc.getXmlPart("ppt/slideLayouts/slideLayout11.xml").getDocumentElement().getAttribute("type"));
    }

    @Test
    public void renameAndPlaceholderEdits() {
        assertTrue(layouts.renameLayout("slideLayout7", "Empty").isSuccess());
        assertEquals("Empty", layoutName(7));

        PlaceholderDefinition ph = new PlaceholderDefinition("body", 5, 100L, 200L, 300L, 400L);
        assertTrue(layouts.addPlaceholder("slideLayout7", ph).isSuccess());
        assertFalse("duplicate idx refused", layouts.addPlaceholder("slideLayout7", ph).isSuccess());

        PlaceholderDefinition moved = new PlaceholderDefinition("body", 5, 111L, 222L, 333L, 444L);
        assertTrue(layouts.editPlaceholder("slideLayout7", 5, moved).isSuccess());
        assertTrue(layouts.removePlaceholder("slideLayout7", 5).isSuccess());
        assertFalse(layouts.removePlaceholder("slideLayout7", 5).isSuccess());
        assertPackageIntact();
    }

    @Test
    public void deleteRefusesLayoutInUse() {
        ExecutionResult<Void> r = layouts.deleteLayout("slideLayout2");
        assertFalse(r.isSuccess());
        assertTrue(r.getMessage().contains("referenced by slides"));
        assertEquals(10, layoutCount());
    }

    @Test
    public void deleteRenumbersAndRetargetsSlides() throws Exception {
        String nameOfLayout9 = layoutName(9);
        assertEquals("../slideLayouts/slideLayout9.xml", slideLayoutTarget(2));

        ExecutionResult<Void> r = layouts.deleteLayout("slideLayout5");
        assertTrue(r.getMessage(), r.isSuccess());

        assertEquals(9, layoutCount());
        assertFalse(doc.hasPart("ppt/slideLayouts/slideLayout10.xml"));
        assertEquals("old layout 9 is now 8", nameOfLayout9, layoutName(8));
        assertEquals("slide 2 follows its layout", "../slideLayouts/slideLayout8.xml", slideLayoutTarget(2));
        assertEquals("slide 1 untouched", "../slideLayouts/slideLayout2.xml", slideLayoutTarget(1));
        assertPackageIntact();

        File out = File.createTempFile("layout-crud", ".pptx");
        try {
            doc.save(out);
            PPTXDocument reloaded = PPTXDocument.loadFromZip(out);
            assertEquals(2, reloaded.getSlideCount());
            assertEquals(9, reloaded.getPartNamesByPrefix("ppt/slideLayouts/slideLayout").stream()
                .filter(p -> p.endsWith(".xml")).count());
            reloaded.close();
        } finally {
            out.delete();
        }
    }

    @Test
    public void integrityCheckCatchesDanglingReferences() {
        // Guard against a checker that passes everything
        doc.removeXmlPart("ppt/slideLayouts/slideLayout4.xml");
        try {
            assertPackageIntact();
            fail("removing a referenced layout part must be detected");
        } catch (AssertionError expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("slideLayout4"));
        }
    }

    @Test
    public void invalidLayoutIdsFailCleanly() {
        assertFalse(layouts.renameLayout("not-a-layout", "x").isSuccess());
        assertFalse(layouts.deleteLayout("slideLayout99").isSuccess());
        assertFalse(layouts.duplicateLayout("slideLayout99", "x").isSuccess());
    }

    @Test
    public void duplicateStaysWithTheSourceMaster() throws Exception {
        addSecondMasterOwning(7);
        assertPackageIntact();
        assertEquals(2, layouts.ownerMaster(7));

        ExecutionResult<String> r = layouts.duplicateLayout("slideLayout7", "Second-master copy");
        assertTrue(r.getMessage(), r.isSuccess());
        int n = Integer.parseInt(r.getData().orElseThrow().replace("slideLayout", ""));
        assertEquals("copy belongs to master 2", 2, layouts.ownerMaster(n));
        assertPackageIntact();

        // The last layout of a master can't be deleted
        assertTrue(layouts.deleteLayout(r.getData().orElseThrow()).isSuccess());
        ExecutionResult<Void> last = layouts.deleteLayout("slideLayout7");
        assertFalse(last.isSuccess());
        assertTrue(last.getMessage().contains("last layout"));
    }

    private void addSecondMasterOwning(int layoutNum) throws Exception {
        com.excudo.test.utils.PackageIntegrity.addSecondMaster(doc, layoutNum);
    }
}
