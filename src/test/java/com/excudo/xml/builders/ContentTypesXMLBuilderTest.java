package com.excudo.xml.builders;

import com.excudo.core.utils.XMLConstants;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for ContentTypesXMLBuilder slide deletion methods
 */
public class ContentTypesXMLBuilderTest {

    @Test
    public void removeSlide_removesMatchingEntry() {
        ContentTypesXMLBuilder builder = ContentTypesXMLBuilder.create()
            .withStandardDefaults()
            .addSlide("/ppt/slides/slide1.xml")
            .addSlide("/ppt/slides/slide2.xml")
            .addSlide("/ppt/slides/slide3.xml");

        builder.removeSlide("/ppt/slides/slide2.xml");
        String xml = builder.build();

        assertTrue(xml.contains("/ppt/slides/slide1.xml"));
        assertFalse(xml.contains("/ppt/slides/slide2.xml"));
        assertTrue(xml.contains("/ppt/slides/slide3.xml"));
    }

    @Test
    public void removeSlide_doesNotRemoveNonSlideOverride() {
        ContentTypesXMLBuilder builder = ContentTypesXMLBuilder.create()
            .addSlide("/ppt/slides/slide1.xml")
            .addNotesSlide("/ppt/notesSlides/notesSlide1.xml");

        builder.removeSlide("/ppt/notesSlides/notesSlide1.xml");
        String xml = builder.build();

        // Notes slide should survive because content type doesn't match
        assertTrue(xml.contains("/ppt/notesSlides/notesSlide1.xml"));
    }

    @Test
    public void removeAllSlides_removesOnlySlideEntries() {
        ContentTypesXMLBuilder builder = ContentTypesXMLBuilder.create()
            .withStandardDefaults()
            .addPresentation("/ppt/presentation.xml")
            .addSlide("/ppt/slides/slide1.xml")
            .addSlide("/ppt/slides/slide2.xml")
            .addSlide("/ppt/slides/slide3.xml")
            .addNotesSlide("/ppt/notesSlides/notesSlide1.xml")
            .addTheme("/ppt/theme/theme1.xml");

        builder.removeAllSlides();
        String xml = builder.build();

        assertFalse(xml.contains("/ppt/slides/slide1.xml"));
        assertFalse(xml.contains("/ppt/slides/slide2.xml"));
        assertFalse(xml.contains("/ppt/slides/slide3.xml"));
        assertTrue(xml.contains("/ppt/presentation.xml"));
        assertTrue(xml.contains("/ppt/notesSlides/notesSlide1.xml"));
        assertTrue(xml.contains("/ppt/theme/theme1.xml"));
    }

    @Test
    public void removeAllSlides_thenReaddMatchesFileSystem() {
        // Simulates the rebuildSlideContentTypes pattern
        ContentTypesXMLBuilder builder = ContentTypesXMLBuilder.create()
            .withStandardDefaults()
            .addPresentation("/ppt/presentation.xml")
            .addSlide("/ppt/slides/slide1.xml")
            .addSlide("/ppt/slides/slide2.xml")
            .addSlide("/ppt/slides/slide3.xml")
            .addSlide("/ppt/slides/slide4.xml")
            .addSlide("/ppt/slides/slide5.xml");

        // Delete slide 3, slides 4&5 rename to 3&4
        builder.removeAllSlides();
        builder.addSlide("/ppt/slides/slide1.xml");
        builder.addSlide("/ppt/slides/slide2.xml");
        builder.addSlide("/ppt/slides/slide3.xml");
        builder.addSlide("/ppt/slides/slide4.xml");

        String xml = builder.build();

        // Should have exactly 4 slide overrides
        int slideCount = countOccurrences(xml, XMLConstants.CONTENT_TYPE_SLIDE);
        assertEquals("Should have exactly 4 slide overrides", 4, slideCount);
        assertFalse(xml.contains("slide5.xml"));
    }

    @Test
    public void removeSlide_noopWhenNotPresent() {
        ContentTypesXMLBuilder builder = ContentTypesXMLBuilder.create()
            .addSlide("/ppt/slides/slide1.xml");

        builder.removeSlide("/ppt/slides/slide99.xml");
        String xml = builder.build();

        assertTrue(xml.contains("/ppt/slides/slide1.xml"));
    }

    @Test
    public void addImageDefaults_addsAllImageTypes() {
        ContentTypesXMLBuilder builder = ContentTypesXMLBuilder.create();
        builder.addImageDefaults();
        String xml = builder.build();

        assertTrue("Should contain png", xml.contains("Extension=\"png\""));
        assertTrue("Should contain jpeg", xml.contains("Extension=\"jpeg\""));
        assertTrue("Should contain jpg", xml.contains("Extension=\"jpg\""));
        assertTrue("Should contain gif", xml.contains("Extension=\"gif\""));
    }

    @Test
    public void addImageDefaults_skipsDuplicates() {
        ContentTypesXMLBuilder builder = ContentTypesXMLBuilder.create()
            .addDefault("png", "image/png");

        builder.addImageDefaults();
        String xml = builder.build();

        // Count png occurrences - should be exactly 1
        int pngCount = countOccurrences(xml, "Extension=\"png\"");
        assertEquals("Should not duplicate png", 1, pngCount);
    }

    @Test
    public void addImageDefaults_calledTwice_noDuplicates() {
        ContentTypesXMLBuilder builder = ContentTypesXMLBuilder.create();
        builder.addImageDefaults();
        builder.addImageDefaults();
        String xml = builder.build();

        assertEquals("png should appear once", 1, countOccurrences(xml, "Extension=\"png\""));
        assertEquals("jpeg should appear once", 1, countOccurrences(xml, "Extension=\"jpeg\""));
        assertEquals("jpg should appear once", 1, countOccurrences(xml, "Extension=\"jpg\""));
        assertEquals("gif should appear once", 1, countOccurrences(xml, "Extension=\"gif\""));
    }

    @Test
    public void build_containsAllDefaultEntries() {
        ContentTypesXMLBuilder builder = ContentTypesXMLBuilder.create()
            .withStandardDefaults()
            .addImageDefaults();
        String xml = builder.build();

        assertTrue("Should contain rels extension", xml.contains("Extension=\"rels\""));
        assertTrue("Should contain xml extension", xml.contains("Extension=\"xml\""));
        assertTrue("Should contain png", xml.contains("Extension=\"png\""));
        assertTrue("Should start with XML declaration", xml.startsWith("<?xml"));
        assertTrue("Should have Types root element", xml.contains("<Types"));
    }

    @Test
    public void createStandard_containsRequiredOverrides() {
        String xml = ContentTypesXMLBuilder.createStandard().build();

        assertTrue("Should contain presentation override", xml.contains("/ppt/presentation.xml"));
        assertTrue("Should contain slide master", xml.contains("/ppt/slideMasters/slideMaster1.xml"));
        assertTrue("Should contain slide layout", xml.contains("/ppt/slideLayouts/slideLayout1.xml"));
        assertTrue("Should contain theme", xml.contains("/ppt/theme/theme1.xml"));
    }

    private int countOccurrences(String text, String substring) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(substring, idx)) != -1) {
            count++;
            idx += substring.length();
        }
        return count;
    }
}
