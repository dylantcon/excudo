package com.excudo.core.orchestration;

import com.excudo.core.model.PPTXDocument;
import com.excudo.core.themes.ThemeLoader;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;

/**
 * Tests PresentationScaffolder from-scratch PPTX generation.
 */
public class PresentationScaffolderTest {

    @Test
    public void scaffoldDocumentCreatesValidDocument() throws Exception {
        PPTXDocument doc = PresentationScaffolder.scaffoldDocument("minimal");

        assertNotNull(doc);
        assertNotNull("Should have presentation.xml", doc.getPresentationXml());
        assertNotNull("Should have content types", doc.getContentTypes());
        assertTrue("Should have at least one part", doc.getPartNames().size() > 0);
    }

    @Test
    public void scaffoldDocumentContainsRequiredParts() throws Exception {
        PPTXDocument doc = PresentationScaffolder.scaffoldDocument("minimal");

        assertTrue("Missing presentation.xml", doc.hasPart("ppt/presentation.xml"));
        assertTrue("Missing theme1.xml", doc.hasPart("ppt/theme/theme1.xml"));
        assertTrue("Missing slideMaster1.xml", doc.hasPart("ppt/slideMasters/slideMaster1.xml"));
    }

    @Test
    public void scaffoldDocumentHasNoSlides() throws Exception {
        PPTXDocument doc = PresentationScaffolder.scaffoldDocument("minimal");

        assertEquals("Scaffolded document should have zero slides", 0, doc.getSlideCount());
    }

    @Test
    public void scaffoldToDirectoryCreatesValidStructure() throws Exception {
        File result = PresentationScaffolder.scaffold("minimal");
        // scaffold() creates pptx_scaffold_<n>/extracted/ and returns the
        // inner dir; clean up the whole temp root when we're done.
        File tempRoot = result.getParentFile();
        try {
            assertNotNull(result);
            assertTrue("Directory should exist", result.exists());
            assertTrue("Should be a directory", result.isDirectory());
            assertTrue("Should contain presentation.xml",
                new File(result, "ppt/presentation.xml").exists());
        } finally {
            deleteRecursive(tempRoot);
        }
    }

    private static void deleteRecursive(File root) {
        if (root == null || !root.exists()) return;
        try {
            java.nio.file.Files.walk(root.toPath())
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> {
                    try { java.nio.file.Files.delete(p); }
                    catch (java.io.IOException ignored) {}
                });
        } catch (java.io.IOException ignored) {}
    }

    @Test
    public void scaffoldWithDifferentThemes() throws Exception {
        for (String themeId : new String[]{"minimal", "corporate", "academic", "excudo"}) {
            PPTXDocument doc = PresentationScaffolder.scaffoldDocument(themeId);
            assertNotNull("Should scaffold with theme: " + themeId, doc);
            assertTrue("Should have theme XML for " + themeId,
                doc.hasPart("ppt/theme/theme1.xml"));
        }
    }

    @Test
    public void scaffoldWithThemeDefinition() throws Exception {
        var theme = ThemeLoader.get("excudo");
        PPTXDocument doc = PresentationScaffolder.scaffoldDocument(theme);
        assertNotNull(doc);
        assertTrue(doc.hasPart("ppt/theme/theme1.xml"));
    }

    @Test
    public void scaffoldedDocumentHasContentTypes() throws Exception {
        PPTXDocument doc = PresentationScaffolder.scaffoldDocument("minimal");

        var ct = doc.getContentTypes();
        assertNotNull(ct);
        assertTrue("Should have rels content type", ct.hasDefault("rels"));
        assertTrue("Should have xml content type", ct.hasDefault("xml"));
    }

    @Test
    public void scaffoldedDocumentCanAddSlides() throws Exception {
        PPTXDocument doc = PresentationScaffolder.scaffoldDocument("minimal");

        PPTXOrchestratorImpl orchestrator = new PPTXOrchestratorImpl();
        orchestrator.initialize(doc);
        orchestrator.createSlide(1, "Test Slide", "slideLayout2");

        doc = orchestrator.getContext().get().getDocument();
        assertEquals(1, doc.getSlideCount());
        assertTrue(doc.hasSlide(1));
    }
}
