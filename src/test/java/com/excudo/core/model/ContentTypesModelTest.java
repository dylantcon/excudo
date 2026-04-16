package com.excudo.core.model;

import com.excudo.core.utils.XMLFactoryProvider;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;

import static org.junit.Assert.*;

/**
 * Tests ContentTypesModel DOM wrapper for [Content_Types].xml operations.
 */
public class ContentTypesModelTest {

    private ContentTypesModel model;

    @Before
    public void setUp() throws Exception {
        String xml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                <Default Extension="xml" ContentType="application/xml"/>
                <Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/>
            </Types>
            """;
        Document doc = XMLFactoryProvider.parseDocument(xml);
        model = new ContentTypesModel(doc);
    }

    @Test
    public void hasDefaultFindsExistingExtension() {
        assertTrue(model.hasDefault("rels"));
        assertTrue(model.hasDefault("xml"));
        assertFalse(model.hasDefault("png"));
    }

    @Test
    public void hasOverrideFindsExistingPart() {
        assertTrue(model.hasOverride("/ppt/presentation.xml"));
        assertFalse(model.hasOverride("/ppt/slides/slide1.xml"));
    }

    @Test
    public void addDefaultAddsNewExtension() {
        assertFalse(model.hasDefault("png"));
        model.addDefault("png", "image/png");
        assertTrue(model.hasDefault("png"));
    }

    @Test
    public void addDefaultDoesNotDuplicate() {
        model.addDefault("png", "image/png");
        model.addDefault("png", "image/png");
        // Count Default elements with Extension="png"
        var defaults = model.getDocument().getElementsByTagName("Default");
        int pngCount = 0;
        for (int i = 0; i < defaults.getLength(); i++) {
            var el = (org.w3c.dom.Element) defaults.item(i);
            if ("png".equalsIgnoreCase(el.getAttribute("Extension"))) pngCount++;
        }
        assertEquals("Should only have one png Default", 1, pngCount);
    }

    @Test
    public void addOverrideAddsNewPart() {
        String partName = "/ppt/slides/slide1.xml";
        assertFalse(model.hasOverride(partName));
        model.addOverride(partName, "application/vnd.openxmlformats-officedocument.presentationml.slide+xml");
        assertTrue(model.hasOverride(partName));
    }

    @Test
    public void removeOverrideRemovesPart() {
        assertTrue(model.hasOverride("/ppt/presentation.xml"));
        model.removeOverride("/ppt/presentation.xml");
        assertFalse(model.hasOverride("/ppt/presentation.xml"));
    }

    @Test
    public void removeOverrideNoOpForMissing() {
        model.removeOverride("/nonexistent/part.xml");
        // Should not throw
    }

    @Test
    public void ensureMediaTypeAddsDefaultForExtension() {
        byte[] data = new byte[]{1, 2, 3};
        MediaElement media = new MediaElement("ppt/media/image1.png", "image/png", data);

        assertFalse(model.hasDefault("png"));
        model.ensureMediaType(media);
        assertTrue(model.hasDefault("png"));
    }

    @Test
    public void getDocumentReturnsDOM() {
        Document doc = model.getDocument();
        assertNotNull(doc);
        assertEquals("Types", doc.getDocumentElement().getLocalName());
    }
}
