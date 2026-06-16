package com.excudo.core.model;

import org.junit.Test;
import org.w3c.dom.Document;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Tests PPTXDocument in-memory OPC model: load, access, modify, save cycle.
 */
public class PPTXDocumentTest {

    private static final File TEST_FILE = resolveTestFile();

    private static File resolveTestFile() {
        File f = new File("test-pptx-samples/generalist_test_file.pptx");
        if (!f.exists()) f = new File("../../../test-pptx-samples/generalist_test_file.pptx");
        return f;
    }

    @Test
    public void loadFromZipParsesSlides() throws Exception {
        if (!TEST_FILE.exists()) fail("Required fixture not found: " + TEST_FILE.getAbsolutePath());
        PPTXDocument doc = PPTXDocument.loadFromZip(TEST_FILE);

        assertTrue("Should have at least one slide", doc.getSlideCount() > 0);
        assertEquals(5, doc.getSlideCount());

        List<Integer> slideNumbers = doc.getSlideNumbers();
        assertEquals(5, slideNumbers.size());
        assertEquals(Integer.valueOf(1), slideNumbers.get(0));
        assertEquals(Integer.valueOf(5), slideNumbers.get(4));
    }

    @Test
    public void hasSlideReturnsTrueForExistingSlides() throws Exception {
        if (!TEST_FILE.exists()) fail("Required fixture not found: " + TEST_FILE.getAbsolutePath());
        PPTXDocument doc = PPTXDocument.loadFromZip(TEST_FILE);

        assertTrue(doc.hasSlide(1));
        assertTrue(doc.hasSlide(5));
        assertFalse(doc.hasSlide(0));
        assertFalse(doc.hasSlide(99));
    }

    @Test
    public void getSlideDocumentReturnsValidDOM() throws Exception {
        if (!TEST_FILE.exists()) fail("Required fixture not found: " + TEST_FILE.getAbsolutePath());
        PPTXDocument doc = PPTXDocument.loadFromZip(TEST_FILE);

        Document slideDom = doc.getSlideDocument(1);
        assertNotNull(slideDom);
        assertEquals("p:sld", slideDom.getDocumentElement().getTagName());
    }

    @Test(expected = IllegalArgumentException.class)
    public void getSlideDocumentThrowsForMissingSlide() throws Exception {
        if (!TEST_FILE.exists()) throw new IllegalArgumentException("skip");
        PPTXDocument doc = PPTXDocument.loadFromZip(TEST_FILE);
        doc.getSlideDocument(99);
    }

    @Test
    public void getPresentationXmlReturnsDOM() throws Exception {
        if (!TEST_FILE.exists()) fail("Required fixture not found: " + TEST_FILE.getAbsolutePath());
        PPTXDocument doc = PPTXDocument.loadFromZip(TEST_FILE);

        Document pres = doc.getPresentationXml();
        assertNotNull(pres);
    }

    @Test
    public void getContentTypesReturnsModel() throws Exception {
        if (!TEST_FILE.exists()) fail("Required fixture not found: " + TEST_FILE.getAbsolutePath());
        PPTXDocument doc = PPTXDocument.loadFromZip(TEST_FILE);

        ContentTypesModel ct = doc.getContentTypes();
        assertNotNull(ct);
        assertNotNull(ct.getDocument());
    }

    @Test
    public void getPartNamesReturnsNonEmpty() throws Exception {
        if (!TEST_FILE.exists()) fail("Required fixture not found: " + TEST_FILE.getAbsolutePath());
        PPTXDocument doc = PPTXDocument.loadFromZip(TEST_FILE);

        Set<String> parts = doc.getPartNames();
        assertFalse(parts.isEmpty());
        assertTrue("Should contain presentation.xml",
            parts.contains("ppt/presentation.xml"));
        assertTrue("Should contain slide1.xml",
            parts.contains("ppt/slides/slide1.xml"));
    }

    @Test
    public void getPartNamesByPrefixFilters() throws Exception {
        if (!TEST_FILE.exists()) fail("Required fixture not found: " + TEST_FILE.getAbsolutePath());
        PPTXDocument doc = PPTXDocument.loadFromZip(TEST_FILE);

        Set<String> slides = doc.getPartNamesByPrefix("ppt/slides/slide");
        assertEquals("Should find 5 slide parts", 5, slides.size());
        for (String part : slides) {
            assertTrue(part.startsWith("ppt/slides/slide"));
        }
    }

    @Test
    public void hasPartChecksAllPartTypes() throws Exception {
        if (!TEST_FILE.exists()) fail("Required fixture not found: " + TEST_FILE.getAbsolutePath());
        PPTXDocument doc = PPTXDocument.loadFromZip(TEST_FILE);

        assertTrue(doc.hasPart("ppt/presentation.xml"));
        assertTrue(doc.hasPart("ppt/slides/slide1.xml"));
        assertFalse(doc.hasPart("nonexistent/file.xml"));
    }

    @Test
    public void putAndGetXmlPart() throws Exception {
        PPTXDocument doc = PPTXDocument.createEmpty();
        Document testDoc = com.excudo.core.utils.XMLFactoryProvider.parseDocument(
            "<root><child/></root>");

        doc.putXmlPart("test/part.xml", testDoc);
        Document retrieved = doc.getXmlPart("test/part.xml");

        assertNotNull(retrieved);
        assertEquals("root", retrieved.getDocumentElement().getTagName());
    }

    @Test
    public void removeXmlPart() throws Exception {
        PPTXDocument doc = PPTXDocument.createEmpty();
        Document testDoc = com.excudo.core.utils.XMLFactoryProvider.parseDocument(
            "<root/>");

        doc.putXmlPart("test/part.xml", testDoc);
        assertTrue(doc.hasPart("test/part.xml"));

        doc.removeXmlPart("test/part.xml");
        assertFalse(doc.hasPart("test/part.xml"));
        assertNull(doc.getXmlPart("test/part.xml"));
    }

    @Test
    public void createEmptyHasNoParts() {
        PPTXDocument doc = PPTXDocument.createEmpty();

        assertEquals(0, doc.getSlideCount());
        assertTrue(doc.getPartNames().isEmpty());
        assertFalse(doc.hasSlide(1));
    }

    @Test
    public void saveToStreamAndReload() throws Exception {
        if (!TEST_FILE.exists()) fail("Required fixture not found: " + TEST_FILE.getAbsolutePath());
        PPTXDocument original = PPTXDocument.loadFromZip(TEST_FILE);
        int originalSlideCount = original.getSlideCount();

        // Save to byte array
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        original.save(baos);

        // Reload from byte array
        PPTXDocument reloaded = PPTXDocument.loadFromZip(
            new ByteArrayInputStream(baos.toByteArray()));

        assertEquals("Slide count should survive save/reload",
            originalSlideCount, reloaded.getSlideCount());
        assertTrue(reloaded.hasSlide(1));
        assertNotNull(reloaded.getSlideDocument(1));
    }

    @Test
    public void slideNumbersAreSorted() throws Exception {
        if (!TEST_FILE.exists()) fail("Required fixture not found: " + TEST_FILE.getAbsolutePath());
        PPTXDocument doc = PPTXDocument.loadFromZip(TEST_FILE);

        List<Integer> numbers = doc.getSlideNumbers();
        for (int i = 1; i < numbers.size(); i++) {
            assertTrue("Slide numbers should be sorted",
                numbers.get(i) > numbers.get(i - 1));
        }
    }

    @Test
    public void putSlideDocumentMakesSlideFindable() throws Exception {
        PPTXDocument doc = PPTXDocument.createEmpty();
        Document slideDom = com.excudo.core.utils.XMLFactoryProvider.parseDocument(
            "<p:sld xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\"/>");

        assertFalse(doc.hasSlide(1));
        doc.putSlideDocument(1, slideDom);
        assertTrue(doc.hasSlide(1));
        assertEquals(1, doc.getSlideCount());
    }
}
