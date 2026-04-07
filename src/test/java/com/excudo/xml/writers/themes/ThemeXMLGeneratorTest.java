package com.excudo.xml.writers.themes;

import com.excudo.core.themes.BundledThemes;
import com.excudo.core.themes.LayoutDefinition;
import com.excudo.core.themes.ThemeDefinition;
import com.excudo.core.utils.XMLFactoryProvider;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import static org.junit.Assert.*;

/**
 * Tests for ThemeXMLGenerator XML output.
 */
public class ThemeXMLGeneratorTest {

    private ThemeXMLGenerator generator;
    private XPath xpath;

    @Before
    public void setUp() {
        generator = new ThemeXMLGenerator();
        xpath = XMLFactoryProvider.createXPathWithoutNamespace();
    }

    // ========== Theme XML Tests ==========

    @Test
    public void generateThemeXMLForAllBundledThemes() throws Exception {
        for (ThemeDefinition theme : BundledThemes.getAll()) {
            Document doc = generator.generateThemeXML(theme);
            assertNotNull("Theme XML should not be null for " + theme.getId(), doc);

            Element root = doc.getDocumentElement();
            assertEquals("a:theme", root.getTagName());
            assertEquals(theme.getDisplayName(), root.getAttribute("name"));
        }
    }

    @Test
    public void themeXMLHas12Colors() throws Exception {
        ThemeDefinition theme = BundledThemes.get("minimal");
        Document doc = generator.generateThemeXML(theme);

        NodeList colorElements = (NodeList) xpath.evaluate(
                "//*[local-name()='clrScheme']/*", doc, XPathConstants.NODESET);
        assertEquals("clrScheme should have 12 color entries", 12, colorElements.getLength());
    }

    @Test
    public void themeXMLHasFontScheme() throws Exception {
        ThemeDefinition theme = BundledThemes.get("corporate");
        Document doc = generator.generateThemeXML(theme);

        Element majorFont = (Element) xpath.evaluate(
                "//*[local-name()='majorFont']/*[local-name()='latin']",
                doc, XPathConstants.NODE);
        assertNotNull("majorFont latin element should exist", majorFont);
        assertEquals("Calibri", majorFont.getAttribute("typeface"));

        Element minorFont = (Element) xpath.evaluate(
                "//*[local-name()='minorFont']/*[local-name()='latin']",
                doc, XPathConstants.NODE);
        assertNotNull("minorFont latin element should exist", minorFont);
        assertEquals("Calibri", minorFont.getAttribute("typeface"));
    }

    @Test
    public void themeXMLHasFmtScheme() throws Exception {
        ThemeDefinition theme = BundledThemes.get("minimal");
        Document doc = generator.generateThemeXML(theme);

        Element fmtScheme = (Element) xpath.evaluate(
                "//*[local-name()='fmtScheme']", doc, XPathConstants.NODE);
        assertNotNull("fmtScheme should exist", fmtScheme);

        // Should have 4 child lists
        assertNotNull(xpath.evaluate("//*[local-name()='fillStyleLst']", doc, XPathConstants.NODE));
        assertNotNull(xpath.evaluate("//*[local-name()='lnStyleLst']", doc, XPathConstants.NODE));
        assertNotNull(xpath.evaluate("//*[local-name()='effectStyleLst']", doc, XPathConstants.NODE));
        assertNotNull(xpath.evaluate("//*[local-name()='bgFillStyleLst']", doc, XPathConstants.NODE));
    }

    // ========== Slide Master XML Tests ==========

    @Test
    public void slideMasterHasClrMap() throws Exception {
        ThemeDefinition theme = BundledThemes.get("minimal");
        Document doc = generator.generateSlideMasterXML(theme);

        Element clrMap = (Element) xpath.evaluate(
                "//*[local-name()='clrMap']", doc, XPathConstants.NODE);
        assertNotNull("clrMap should exist", clrMap);

        // Verify all 12 mappings
        String[] mapAttrs = {"bg1", "tx1", "bg2", "tx2",
                "accent1", "accent2", "accent3", "accent4", "accent5", "accent6",
                "hlink", "folHlink"};
        for (String attr : mapAttrs) {
            assertTrue("clrMap should have attribute: " + attr,
                    clrMap.hasAttribute(attr));
        }
    }

    @Test
    public void slideMasterHasTxStylesWithAllLevels() throws Exception {
        ThemeDefinition theme = BundledThemes.get("corporate");
        Document doc = generator.generateSlideMasterXML(theme);

        // Check all 3 style types exist
        assertNotNull(xpath.evaluate("//*[local-name()='titleStyle']", doc, XPathConstants.NODE));
        assertNotNull(xpath.evaluate("//*[local-name()='bodyStyle']", doc, XPathConstants.NODE));
        assertNotNull(xpath.evaluate("//*[local-name()='otherStyle']", doc, XPathConstants.NODE));

        // Each should have 9 lvlNpPr elements (no defPPr -- PowerPoint removes it)
        for (String style : new String[]{"titleStyle", "bodyStyle", "otherStyle"}) {
            NodeList levels = (NodeList) xpath.evaluate(
                    "//*[local-name()='" + style + "']/*", doc, XPathConstants.NODESET);
            assertEquals(style + " should have 9 level children",
                    9, levels.getLength());
        }
    }

    @Test
    public void slideMasterHasPlaceholderShapes() throws Exception {
        ThemeDefinition theme = BundledThemes.get("academic");
        Document doc = generator.generateSlideMasterXML(theme);

        // Should have at least 2 sp elements (title + body)
        NodeList shapes = (NodeList) xpath.evaluate(
                "//*[local-name()='spTree']/*[local-name()='sp']",
                doc, XPathConstants.NODESET);
        assertEquals("Slide master should have 2 placeholder shapes", 2, shapes.getLength());
    }

    @Test
    public void slideMasterHasBackground() throws Exception {
        ThemeDefinition theme = BundledThemes.get("minimal");
        Document doc = generator.generateSlideMasterXML(theme);

        Element bg = (Element) xpath.evaluate(
                "//*[local-name()='bg']", doc, XPathConstants.NODE);
        assertNotNull("Background should exist", bg);
    }

    // ========== Slide Layout XML Tests ==========

    @Test
    public void slideLayoutsGenerateForAllTypes() throws Exception {
        ThemeDefinition theme = BundledThemes.get("minimal");
        for (LayoutDefinition layout : theme.getLayouts()) {
            Document doc = generator.generateSlideLayoutXML(theme, layout);
            assertNotNull("Layout XML should not be null for " + layout.getMatchingName(), doc);

            Element root = doc.getDocumentElement();
            assertEquals("p:sldLayout", root.getTagName());
            assertEquals(layout.getMatchingName(), root.getAttribute("matchingName"));
        }
    }

    @Test
    public void slideLayoutHasCorrectPlaceholderCount() throws Exception {
        ThemeDefinition theme = BundledThemes.get("corporate");

        // Title Slide should have 2 placeholders
        LayoutDefinition titleSlide = theme.getLayouts().get(0);
        Document titleDoc = generator.generateSlideLayoutXML(theme, titleSlide);
        NodeList titleShapes = (NodeList) xpath.evaluate(
                "//*[local-name()='spTree']/*[local-name()='sp']",
                titleDoc, XPathConstants.NODESET);
        assertEquals(2, titleShapes.getLength());

        // Blank should have 0 placeholders
        LayoutDefinition blank = theme.getLayouts().get(6);
        Document blankDoc = generator.generateSlideLayoutXML(theme, blank);
        NodeList blankShapes = (NodeList) xpath.evaluate(
                "//*[local-name()='spTree']/*[local-name()='sp']",
                blankDoc, XPathConstants.NODESET);
        assertEquals(0, blankShapes.getLength());
    }

    @Test
    public void slideLayoutPlaceholdersHaveGeometry() throws Exception {
        ThemeDefinition theme = BundledThemes.get("corporate");
        LayoutDefinition titleSlide = theme.getLayouts().get(0);
        Document doc = generator.generateSlideLayoutXML(theme, titleSlide);

        NodeList shapes = (NodeList) xpath.evaluate(
                "//*[local-name()='spTree']/*[local-name()='sp']",
                doc, XPathConstants.NODESET);
        assertTrue("Layout should have placeholder shapes", shapes.getLength() > 0);
        for (int i = 0; i < shapes.getLength(); i++) {
            Element sp = (Element) shapes.item(i);
            // spPr should contain a:xfrm with position and size
            Element xfrm = (Element) xpath.evaluate(
                    "*[local-name()='spPr']/*[local-name()='xfrm']", sp, XPathConstants.NODE);
            assertNotNull("Layout placeholder should have a:xfrm geometry", xfrm);

            Element off = (Element) xpath.evaluate(
                    "*[local-name()='off']", xfrm, XPathConstants.NODE);
            assertNotNull("a:xfrm should have a:off", off);
            assertNotNull("a:off should have x attribute", off.getAttribute("x"));

            // spPr should contain a:prstGeom
            Element prstGeom = (Element) xpath.evaluate(
                    "*[local-name()='spPr']/*[local-name()='prstGeom']", sp, XPathConstants.NODE);
            assertNotNull("Layout placeholder should have a:prstGeom", prstGeom);
            assertEquals("Preset geometry should be rect", "rect", prstGeom.getAttribute("prst"));
        }
    }

    @Test
    public void slideLayoutHasClrMapOvr() throws Exception {
        ThemeDefinition theme = BundledThemes.get("minimal");
        LayoutDefinition layout = theme.getLayouts().get(0);
        Document doc = generator.generateSlideLayoutXML(theme, layout);

        Element clrMapOvr = (Element) xpath.evaluate(
                "//*[local-name()='clrMapOvr']", doc, XPathConstants.NODE);
        assertNotNull("clrMapOvr should exist", clrMapOvr);

        Element masterMapping = (Element) xpath.evaluate(
                "//*[local-name()='masterClrMapping']", doc, XPathConstants.NODE);
        assertNotNull("masterClrMapping should exist", masterMapping);
    }

    // ========== Relationships Tests ==========

    @Test
    public void slideMasterRelsHasCorrectRelationships() throws Exception {
        ThemeDefinition theme = BundledThemes.get("minimal");
        Document doc = generator.generateSlideMasterRels(theme, 1);

        NodeList rels = (NodeList) xpath.evaluate(
                "//*[local-name()='Relationship']", doc, XPathConstants.NODESET);
        // 10 layouts + 1 theme = 11 relationships
        assertEquals("Should have 11 relationships (10 layouts + 1 theme)",
                11, rels.getLength());

        // Last should be theme relationship
        Element themeRel = (Element) rels.item(rels.getLength() - 1);
        assertTrue("Last relationship should be theme",
                themeRel.getAttribute("Type").contains("theme"));
        assertEquals("../theme/theme1.xml", themeRel.getAttribute("Target"));
    }

    @Test
    public void slideLayoutRelsPointsToMaster() throws Exception {
        Document doc = generator.generateSlideLayoutRels(1);

        NodeList rels = (NodeList) xpath.evaluate(
                "//*[local-name()='Relationship']", doc, XPathConstants.NODESET);
        assertEquals(1, rels.getLength());

        Element rel = (Element) rels.item(0);
        assertTrue(rel.getAttribute("Type").contains("slideMaster"));
        assertEquals("../slideMasters/slideMaster1.xml", rel.getAttribute("Target"));
    }
}
