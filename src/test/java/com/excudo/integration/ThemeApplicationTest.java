package com.excudo.integration;

import com.excudo.core.model.PPTXDocument;
import com.excudo.core.themes.BundledThemes;
import com.excudo.core.themes.ThemeDefinition;
import com.excudo.core.themes.ThemeManager;
import com.excudo.core.utils.OOXMLAttributeOrder;
import com.excudo.core.utils.XMLFactoryProvider;
import com.excudo.xml.writers.themes.ThemeXMLGenerator;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.Assert.*;

/**
 * Integration test for theme application pipeline.
 * Generates theme files into a temp directory and validates structure.
 */
public class ThemeApplicationTest {

    private File tempDir;
    private ThemeXMLGenerator generator;
    private XPath xpath;

    @Before
    public void setUp() throws Exception {
        tempDir = Files.createTempDirectory("theme-test-").toFile();
        generator = new ThemeXMLGenerator();
        xpath = XMLFactoryProvider.createXPathWithoutNamespace();

        // Create required directory structure
        new File(tempDir, "ppt/theme").mkdirs();
        new File(tempDir, "ppt/slideMasters/_rels").mkdirs();
        new File(tempDir, "ppt/slideLayouts/_rels").mkdirs();
    }

    @After
    public void tearDown() throws Exception {
        if (tempDir != null && tempDir.exists()) {
            Files.walk(tempDir.toPath())
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }

    @Test
    public void writeAndReadThemeXMLRoundTrip() throws Exception {
        for (ThemeDefinition theme : BundledThemes.getAll()) {
            // Generate and write theme XML
            Document themeDoc = generator.generateThemeXML(theme);
            File themeFile = new File(tempDir, "ppt/theme/theme1.xml");
            OOXMLAttributeOrder.serialize(themeDoc, themeFile);

            assertTrue("Theme file should exist for " + theme.getId(), themeFile.exists());
            assertTrue("Theme file should not be empty for " + theme.getId(), themeFile.length() > 0);

            // Re-parse and verify
            Document parsed = XMLFactoryProvider.parseDocument(themeFile);
            Element root = parsed.getDocumentElement();
            assertEquals("a:theme", root.getTagName());

            // Verify color scheme
            NodeList colors = (NodeList) xpath.evaluate(
                    "//*[local-name()='clrScheme']/*[local-name()!='']",
                    parsed, XPathConstants.NODESET);
            assertEquals("Should have 12 colors for " + theme.getId(), 12, colors.getLength());
        }
    }

    @Test
    public void writeFullThemeBundleForEachTheme() throws Exception {
        for (ThemeDefinition theme : BundledThemes.getAll()) {
            // Write theme
            OOXMLAttributeOrder.serialize(
                    generator.generateThemeXML(theme),
                    new File(tempDir, "ppt/theme/theme1.xml"));

            // Write slide master
            OOXMLAttributeOrder.serialize(
                    generator.generateSlideMasterXML(theme),
                    new File(tempDir, "ppt/slideMasters/slideMaster1.xml"));

            // Write master rels
            OOXMLAttributeOrder.serialize(
                    generator.generateSlideMasterRels(theme, 1),
                    new File(tempDir, "ppt/slideMasters/_rels/slideMaster1.xml.rels"));

            // Write all layouts + rels
            for (int i = 0; i < theme.getLayouts().size(); i++) {
                int layoutNum = i + 1;
                OOXMLAttributeOrder.serialize(
                        generator.generateSlideLayoutXML(theme, theme.getLayouts().get(i)),
                        new File(tempDir, "ppt/slideLayouts/slideLayout" + layoutNum + ".xml"));
                OOXMLAttributeOrder.serialize(
                        generator.generateSlideLayoutRels(1),
                        new File(tempDir, "ppt/slideLayouts/_rels/slideLayout" + layoutNum + ".xml.rels"));
            }

            // Verify file counts
            File[] layoutFiles = new File(tempDir, "ppt/slideLayouts").listFiles(
                    (dir, name) -> name.endsWith(".xml") && !name.contains("_rels"));
            assertNotNull(layoutFiles);
            assertEquals("Should have 10 layout files for " + theme.getId(),
                    10, layoutFiles.length);

            // Verify master rels
            Document masterRels = XMLFactoryProvider.parseDocument(
                    new File(tempDir, "ppt/slideMasters/_rels/slideMaster1.xml.rels"));
            NodeList rels = (NodeList) xpath.evaluate(
                    "//*[local-name()='Relationship']", masterRels, XPathConstants.NODESET);
            assertEquals("Master should have 11 relationships for " + theme.getId(),
                    11, rels.getLength());
        }
    }

    @Test
    public void themeManagerReloadsAfterWrite() throws Exception {
        ThemeDefinition theme = BundledThemes.get("minimal");

        // Build an in-memory PPTXDocument with generated XML parts
        Document themeDoc = generator.generateThemeXML(theme);
        Document masterDoc = generator.generateSlideMasterXML(theme);
        Document masterRelsDoc = generator.generateSlideMasterRels(theme, 1);

        PPTXDocument pptxDoc = PPTXDocument.createEmpty();
        pptxDoc.putXmlPart("ppt/theme/theme1.xml", themeDoc);
        pptxDoc.putXmlPart("ppt/slideMasters/slideMaster1.xml", masterDoc);
        pptxDoc.putXmlPart("ppt/slideMasters/_rels/slideMaster1.xml.rels", masterRelsDoc);

        // Initialize ThemeManager from in-memory document
        ThemeManager.initialize(pptxDoc);
        assertTrue("Theme should be loaded", ThemeManager.isThemeLoaded());
        assertEquals("Minimal", ThemeManager.getCurrentThemeName());
    }

    @Test
    public void slideMasterXMLStructureValid() throws Exception {
        ThemeDefinition theme = BundledThemes.get("corporate");
        Document doc = generator.generateSlideMasterXML(theme);

        // Verify root element
        Element root = doc.getDocumentElement();
        assertEquals("p:sldMaster", root.getTagName());

        // Verify namespace declarations
        String pNs = root.getAttribute("xmlns:p");
        String aNs = root.getAttribute("xmlns:a");
        assertFalse("Should have p: namespace", pNs.isEmpty());
        assertFalse("Should have a: namespace", aNs.isEmpty());

        // Verify structure: cSld -> spTree with group props + shapes
        Element cSld = (Element) xpath.evaluate(
                "//*[local-name()='cSld']", doc, XPathConstants.NODE);
        assertNotNull("cSld should exist", cSld);

        Element spTree = (Element) xpath.evaluate(
                "//*[local-name()='spTree']", doc, XPathConstants.NODE);
        assertNotNull("spTree should exist", spTree);

        // Verify group shape properties exist
        assertNotNull("nvGrpSpPr should exist",
                xpath.evaluate("//*[local-name()='nvGrpSpPr']", doc, XPathConstants.NODE));
        assertNotNull("grpSpPr should exist",
                xpath.evaluate("//*[local-name()='grpSpPr']", doc, XPathConstants.NODE));
    }

    @Test
    public void titleStyleFontSizeMatchesDefinition() throws Exception {
        ThemeDefinition theme = BundledThemes.get("academic");
        Document doc = generator.generateSlideMasterXML(theme);

        // Academic title level 0 = 40pt = 4000 hundredths
        Element lvl1pPr = (Element) xpath.evaluate(
                "//*[local-name()='titleStyle']/*[local-name()='lvl1pPr']",
                doc, XPathConstants.NODE);
        assertNotNull("lvl1pPr in titleStyle should exist", lvl1pPr);

        Element defRPr = (Element) xpath.evaluate(
                "*[local-name()='defRPr']", lvl1pPr, XPathConstants.NODE);
        assertNotNull("defRPr should exist", defRPr);
        assertEquals("4000", defRPr.getAttribute("sz"));
    }
}
