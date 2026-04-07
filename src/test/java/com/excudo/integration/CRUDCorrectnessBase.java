package com.excudo.integration;

import com.excudo.xml.validation.OOXMLSchemaValidator;
import com.excudo.xml.writers.WriterTestFixtures;

import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Base class for per-category CRUD correctness tests.
 *
 * Subclasses override {@link #category()} to target a specific CRUD directory
 * (e.g. "animations-crud"). Provides helpers for extracting XML parts from PPTX
 * zip archives, running schema validation, and comparing native vs. raw output.
 */
public abstract class CRUDCorrectnessBase {

    private static final String SAMPLES_ROOT = "test-pptx-samples";

    /** Category directory name (e.g. "animations-crud"). */
    protected abstract String category();

    // ---- path helpers ----

    protected File nativeDir() {
        return new File(SAMPLES_ROOT, category() + "/native");
    }

    protected File rawDir() {
        return new File(SAMPLES_ROOT, category() + "/raw");
    }

    protected File repairedDir() {
        return new File(SAMPLES_ROOT, category() + "/repaired");
    }

    protected File nativeFile(String name) {
        return new File(nativeDir(), name);
    }

    protected File rawFile(String name) {
        return new File(rawDir(), name);
    }

    protected File repairedFile(String name) {
        return new File(repairedDir(), name);
    }

    // ---- extraction helpers ----

    /**
     * Extracts a single XML part from a PPTX zip and parses it into a DOM Document.
     *
     * @param pptxFile the PPTX file (zip archive)
     * @param partPath path inside the zip (e.g. "ppt/slides/slide1.xml")
     * @return parsed Document, or null if the part does not exist in the archive
     */
    protected Document extractXMLPart(File pptxFile, String partPath) throws Exception {
        try (ZipFile zip = new ZipFile(pptxFile)) {
            ZipEntry entry = zip.getEntry(partPath);
            if (entry == null) {
                return null;
            }
            DocumentBuilder db = WriterTestFixtures.createDocumentBuilder();
            try (InputStream is = zip.getInputStream(entry)) {
                return db.parse(is);
            }
        }
    }

    /**
     * Extracts raw bytes of a part from the PPTX zip.
     */
    protected String extractPartText(File pptxFile, String partPath) throws Exception {
        try (ZipFile zip = new ZipFile(pptxFile)) {
            ZipEntry entry = zip.getEntry(partPath);
            if (entry == null) {
                return null;
            }
            try (InputStream is = zip.getInputStream(entry)) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
    }

    // ---- validation helpers ----

    /**
     * Validates slide1.xml (or any slide) against the ECMA-376 PML XSD schema.
     * Skips gracefully if the schema is not available on classpath.
     */
    protected void assertSchemaValid(File pptxFile, String slidePart) throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                OOXMLSchemaValidator.isSchemaAvailable(),
                "PresentationML XSD schema not available on classpath");

        Document slideDoc = extractXMLPart(pptxFile, slidePart);
        assertNotNull(slideDoc, "Part not found in archive: " + slidePart);

        OOXMLSchemaValidator.SchemaValidationResult result = OOXMLSchemaValidator.validate(slideDoc);
        assertTrue(result.isValid(),
                "XSD validation failed for " + pptxFile.getName() + "/" + slidePart + ": " + result.getSummary());
    }

    /**
     * Compares the timing XML of the same slide across native and raw files.
     * Returns true if both files have timing and the raw output differs (potential issue).
     */
    protected boolean hasTimingDifferences(File nativePptx, File rawPptx, int slideNum) throws Exception {
        String part = "ppt/slides/slide" + slideNum + ".xml";
        String nativeXml = extractPartText(nativePptx, part);
        String rawXml = extractPartText(rawPptx, part);

        if (nativeXml == null || rawXml == null) {
            return false;
        }

        boolean nativeHasTiming = nativeXml.contains("<p:timing");
        boolean rawHasTiming = rawXml.contains("<p:timing");

        if (!nativeHasTiming && !rawHasTiming) {
            return false;
        }
        if (nativeHasTiming != rawHasTiming) {
            return true;
        }

        // Both have timing -- compare the timing sections
        String nativeTiming = extractTimingSection(nativeXml);
        String rawTiming = extractTimingSection(rawXml);
        return !nativeTiming.equals(rawTiming);
    }

    private String extractTimingSection(String xml) {
        int start = xml.indexOf("<p:timing");
        int end = xml.indexOf("</p:timing>");
        if (start < 0 || end < 0) {
            return "";
        }
        return xml.substring(start, end + "</p:timing>".length());
    }

    // ---- semantic assertion delegates ----

    protected void assertTimingStructureValid(Document slideDoc) throws Exception {
        WriterTestFixtures.assertValidTimingHierarchy(slideDoc);
        WriterTestFixtures.assertUniqueTimingNodeIds(slideDoc);
        WriterTestFixtures.assertClickTriggersValid(slideDoc);
        WriterTestFixtures.assertAnimationsTargetExistingShapes(slideDoc);
        WriterTestFixtures.assertBuildListComplete(slideDoc);
        WriterTestFixtures.assertGrpIdBldLstLinkage(slideDoc);
    }
}
