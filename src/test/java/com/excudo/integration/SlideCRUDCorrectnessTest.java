package com.excudo.integration;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import java.io.File;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CRUD correctness tests for slide-crud category.
 * Tests slide creation and deletion pipeline output.
 */
public class SlideCRUDCorrectnessTest extends CRUDCorrectnessBase {

    @Override
    protected String category() {
        return "slide-crud";
    }

    @Test
    public void rawFilesPassSchemaValidation() throws Exception {
        File[] rawFiles = rawDir().listFiles((d, name) -> name.endsWith(".pptx"));
        Assumptions.assumeTrue(rawFiles != null && rawFiles.length > 0,
                "No raw PPTX files in slide-crud/raw/");

        for (File rawFile : rawFiles) {
            assertSchemaValid(rawFile, "ppt/slides/slide1.xml");
        }
    }

    @Test
    public void createSlideHasCorrectSlideCount() throws Exception {
        File rawFile = rawFile("crud_create_slide.pptx");
        Assumptions.assumeTrue(rawFile.exists(), "crud_create_slide.pptx not generated");

        // Original has 5 slides, we created one at position 6 -> should have 6
        int slideCount = countSlides(rawFile);
        assertEquals(6, slideCount, "Created slide file should have 6 slides");
    }

    @Test
    public void deleteSlideHasCorrectSlideCount() throws Exception {
        File rawFile = rawFile("crud_delete_slide.pptx");
        Assumptions.assumeTrue(rawFile.exists(), "crud_delete_slide.pptx not generated");

        // Original has 5 slides, we deleted slide 5 -> should have 4
        int slideCount = countSlides(rawFile);
        assertEquals(4, slideCount, "Deleted slide file should have 4 slides");
    }

    @Test
    public void rawFilesHaveValidContentTypes() throws Exception {
        File[] rawFiles = rawDir().listFiles((d, name) -> name.endsWith(".pptx"));
        Assumptions.assumeTrue(rawFiles != null && rawFiles.length > 0,
                "No raw PPTX files in slide-crud/raw/");

        for (File rawFile : rawFiles) {
            String contentTypes = extractPartText(rawFile, "[Content_Types].xml");
            assertNotNull(contentTypes, rawFile.getName() + " missing [Content_Types].xml");
            assertTrue(contentTypes.contains("application/vnd.openxmlformats-officedocument.presentationml.slide+xml"),
                "Content types should reference slide content type in " + rawFile.getName());
        }
    }

    @Test
    public void rawFilesHaveValidPresentationRels() throws Exception {
        File[] rawFiles = rawDir().listFiles((d, name) -> name.endsWith(".pptx"));
        Assumptions.assumeTrue(rawFiles != null && rawFiles.length > 0,
                "No raw PPTX files in slide-crud/raw/");

        for (File rawFile : rawFiles) {
            String rels = extractPartText(rawFile, "ppt/_rels/presentation.xml.rels");
            assertNotNull(rels, rawFile.getName() + " missing presentation.xml.rels");
            assertTrue(rels.contains("Relationship"),
                "Presentation rels should contain Relationship elements in " + rawFile.getName());
        }
    }

    private int countSlides(File pptxFile) throws Exception {
        int count = 0;
        try (ZipFile zip = new ZipFile(pptxFile)) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.matches("ppt/slides/slide\\d+\\.xml")) {
                    count++;
                }
            }
        }
        return count;
    }
}
