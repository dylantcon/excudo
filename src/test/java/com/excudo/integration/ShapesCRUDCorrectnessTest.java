package com.excudo.integration;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.xpath.XPath;

import com.excudo.xml.writers.WriterTestFixtures;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CRUD correctness tests for shapes-crud category.
 * Tests shape addition pipeline output.
 */
public class ShapesCRUDCorrectnessTest extends CRUDCorrectnessBase {

    @Override
    protected String category() {
        return "shapes-crud";
    }

    @Test
    public void rawFilesPassSchemaValidation() throws Exception {
        File[] rawFiles = rawDir().listFiles((d, name) -> name.endsWith(".pptx"));
        Assumptions.assumeTrue(rawFiles != null && rawFiles.length > 0,
                "No raw PPTX files in shapes-crud/raw/");

        for (File rawFile : rawFiles) {
            assertSchemaValid(rawFile, "ppt/slides/slide1.xml");
        }
    }

    @Test
    public void addShapeProducesValidSlideXml() throws Exception {
        File rawFile = rawFile("crud_add_shape.pptx");
        Assumptions.assumeTrue(rawFile.exists(), "crud_add_shape.pptx not generated");

        Document doc = extractXMLPart(rawFile, "ppt/slides/slide1.xml");
        assertNotNull(doc, "slide1.xml should exist");

        XPath xpath = WriterTestFixtures.createConfiguredXPath();

        // Should have shapes in the spTree
        NodeList shapes = (NodeList) xpath.evaluate(
            "//p:sp", doc, javax.xml.xpath.XPathConstants.NODESET);
        assertTrue(shapes.getLength() > 0, "Slide should contain shapes after add-shape");
    }

    @Test
    public void addShapeHasUniqueSpids() throws Exception {
        File rawFile = rawFile("crud_add_shape.pptx");
        Assumptions.assumeTrue(rawFile.exists(), "crud_add_shape.pptx not generated");

        Document doc = extractXMLPart(rawFile, "ppt/slides/slide1.xml");
        assertNotNull(doc, "slide1.xml should exist");

        WriterTestFixtures.assertUniqueShapeSpids(doc);
    }

    @Test
    public void rawFilesHaveValidContentTypes() throws Exception {
        File[] rawFiles = rawDir().listFiles((d, name) -> name.endsWith(".pptx"));
        Assumptions.assumeTrue(rawFiles != null && rawFiles.length > 0,
                "No raw PPTX files in shapes-crud/raw/");

        for (File rawFile : rawFiles) {
            String contentTypes = extractPartText(rawFile, "[Content_Types].xml");
            assertNotNull(contentTypes, rawFile.getName() + " missing [Content_Types].xml");
        }
    }
}
