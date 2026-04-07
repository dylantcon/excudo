package com.excudo.integration;

import com.excudo.core.model.PPTXDocument;
import com.excudo.core.orchestration.PresentationScaffolder;
import com.excudo.core.orchestration.PPTXOrchestratorImpl;
import com.excudo.core.orchestration.OrchestrationContext;
import com.excudo.core.results.ExecutionResult;
import com.excudo.core.results.SlideExecutionResult;
import com.excudo.core.themes.ThemeDefinition;
import com.excudo.core.themes.ThemeLoader;
import com.excudo.core.utils.OOXMLAttributeOrder;
import org.junit.Test;
import org.w3c.dom.Document;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.File;

/**
 * Integration tests for PresentationScaffolder -- creating PPTX from scratch.
 * Uses in-memory PPTXDocument path to avoid temp directory creation.
 */
public class PresentationScaffolderTest {

    /** Serialize a DOM to string for assertion checks. */
    private static String xmlToString(Document doc) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        OOXMLAttributeOrder.serialize(doc, baos);
        return baos.toString("UTF-8");
    }

    @Test
    public void scaffoldMinimal_createsValidDocument() throws Exception {
        PPTXDocument doc = PresentationScaffolder.scaffoldDocument("minimal");
        assertNotNull(doc);

        // Verify essential parts exist
        assertNotNull("Missing [Content_Types].xml", doc.getXmlPart("[Content_Types].xml"));
        assertNotNull("Missing _rels/.rels", doc.getXmlPart("_rels/.rels"));
        assertNotNull("Missing ppt/presentation.xml", doc.getXmlPart("ppt/presentation.xml"));
        assertNotNull("Missing ppt/_rels/presentation.xml.rels",
                doc.getXmlPart("ppt/_rels/presentation.xml.rels"));
        assertNotNull("Missing ppt/theme/theme1.xml", doc.getXmlPart("ppt/theme/theme1.xml"));
        assertNotNull("Missing ppt/slideMasters/slideMaster1.xml",
                doc.getXmlPart("ppt/slideMasters/slideMaster1.xml"));
        assertNotNull("Missing ppt/slideMasters/_rels/slideMaster1.xml.rels",
                doc.getXmlPart("ppt/slideMasters/_rels/slideMaster1.xml.rels"));
        assertNotNull("Missing ppt/presProps.xml", doc.getXmlPart("ppt/presProps.xml"));
        assertNotNull("Missing ppt/viewProps.xml", doc.getXmlPart("ppt/viewProps.xml"));
        assertNotNull("Missing ppt/tableStyles.xml", doc.getXmlPart("ppt/tableStyles.xml"));
        assertNotNull("Missing docProps/core.xml", doc.getXmlPart("docProps/core.xml"));
        assertNotNull("Missing docProps/app.xml", doc.getXmlPart("docProps/app.xml"));

        // 10 layouts
        for (int i = 1; i <= 10; i++) {
            assertNotNull("Missing slideLayout" + i,
                    doc.getXmlPart("ppt/slideLayouts/slideLayout" + i + ".xml"));
            assertNotNull("Missing slideLayout" + i + " rels",
                    doc.getXmlPart("ppt/slideLayouts/_rels/slideLayout" + i + ".xml.rels"));
        }
    }

    @Test
    public void scaffoldAllThemes_initializeSuccessfully() throws Exception {
        for (String themeId : new String[]{"minimal", "corporate", "academic"}) {
            PPTXDocument doc = PresentationScaffolder.scaffoldDocument(themeId);
            PPTXOrchestratorImpl orchestrator = new PPTXOrchestratorImpl();

            ExecutionResult<OrchestrationContext> result = orchestrator.initialize(doc);
            assertTrue("Failed to initialize with theme " + themeId + ": " + result.getMessage(),
                    result.isSuccess());

            orchestrator.close();
        }
    }

    @Test
    public void scaffoldAndCreateSlide_roundTrip() throws Exception {
        PPTXDocument doc = PresentationScaffolder.scaffoldDocument("corporate");
        PPTXOrchestratorImpl orchestrator = new PPTXOrchestratorImpl();
        orchestrator.initialize(doc);

        // Create two slides
        SlideExecutionResult createResult1 = orchestrator.createSlide(1, "Welcome Slide");
        assertTrue("Slide 1 creation failed", createResult1.isSuccess());
        SlideExecutionResult createResult2 = orchestrator.createSlide(2, "Content Slide");
        assertTrue("Slide 2 creation failed", createResult2.isSuccess());

        // Save as PPTX
        File output = File.createTempFile("scaffold-test-", ".pptx");
        output.deleteOnExit();
        ExecutionResult<File> saveResult = orchestrator.savePresentation(output);
        assertTrue("Save failed: " + saveResult.getMessage(), saveResult.isSuccess());
        assertTrue("Output file should exist", output.exists());
        assertTrue("Output file should be non-empty", output.length() > 0);

        orchestrator.close();
    }

    @Test
    public void scaffoldAppProperties_matchesPowerPointExpectations() throws Exception {
        PPTXDocument doc = PresentationScaffolder.scaffoldDocument("corporate");
        String appXml = xmlToString(doc.getXmlPart("docProps/app.xml"));

        assertTrue("PresentationFormat must be Widescreen",
                appXml.contains("Widescreen"));
        assertTrue("app.xml must have Paragraphs element",
                appXml.contains("Paragraphs"));
    }

    @Test
    public void scaffoldPresProps_hasExtensionList() throws Exception {
        PPTXDocument doc = PresentationScaffolder.scaffoldDocument("corporate");
        String presProps = xmlToString(doc.getXmlPart("ppt/presProps.xml"));

        assertTrue("presProps must have extLst", presProps.contains("extLst"));
        assertTrue("presProps must have discardImageEditData",
                presProps.contains("discardImageEditData"));
        assertTrue("presProps must have defaultImageDpi",
                presProps.contains("defaultImageDpi"));
    }

    @Test
    public void presentationXml_hasSlideGuideExtension() throws Exception {
        PPTXDocument doc = PresentationScaffolder.scaffoldDocument("corporate");
        String presXml = xmlToString(doc.getXmlPart("ppt/presentation.xml"));

        assertTrue("presentation.xml must have extLst", presXml.contains("extLst"));
        assertTrue("presentation.xml must have sldGuideLst extension",
                presXml.contains("sldGuideLst"));
    }

    @Test
    public void scaffoldXmlParts_areCompactSingleLine() throws Exception {
        PPTXDocument doc = PresentationScaffolder.scaffoldDocument("corporate");

        String presXml = xmlToString(doc.getXmlPart("ppt/presentation.xml"));
        String[] presLines = presXml.split("\n");
        assertEquals("presentation.xml must be 2 lines (XML decl + body)", 2, presLines.length);

        String ctXml = xmlToString(doc.getXmlPart("[Content_Types].xml"));
        String[] ctLines = ctXml.split("\n");
        assertEquals("[Content_Types].xml must be 2 lines (XML decl + body)", 2, ctLines.length);

        String relsXml = xmlToString(doc.getXmlPart("ppt/_rels/presentation.xml.rels"));
        String[] relsLines = relsXml.split("\n");
        assertEquals("presentation.xml.rels must be 2 lines (XML decl + body)", 2, relsLines.length);
    }

    @Test
    public void scaffoldCoreProperties_hasValidTimestampFormat() throws Exception {
        PPTXDocument doc = PresentationScaffolder.scaffoldDocument("corporate");
        String coreXml = xmlToString(doc.getXmlPart("docProps/core.xml"));

        assertFalse("Timestamps must not have fractional seconds",
                coreXml.matches("(?s).*\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d+Z.*"));
    }

    @Test
    public void scaffoldWithThemeDefinition_works() throws Exception {
        ThemeLoader.initialize();
        ThemeDefinition theme = ThemeLoader.get("academic");
        PPTXDocument doc = PresentationScaffolder.scaffoldDocument(theme);
        assertNotNull(doc);

        String themeXml = xmlToString(doc.getXmlPart("ppt/theme/theme1.xml"));
        assertTrue(themeXml.contains("Academic"));
        assertTrue(themeXml.contains("Georgia"));
    }

    @Test
    public void contentTypesHaveCorrectFullNames() throws Exception {
        PPTXDocument doc = PresentationScaffolder.scaffoldDocument("minimal");
        String ct = xmlToString(doc.getXmlPart("[Content_Types].xml"));

        assertTrue("Presentation content type must include 'officedocument'",
                ct.contains("officedocument.presentationml.presentation.main+xml"));
        assertTrue("Slide master content type must include 'officedocument'",
                ct.contains("officedocument.presentationml.slideMaster+xml"));
        assertTrue("Slide layout content type must include 'officedocument'",
                ct.contains("officedocument.presentationml.slideLayout+xml"));
    }
}
