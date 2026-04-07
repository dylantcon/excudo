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
 * CRUD correctness tests for animations-crud category.
 *
 * Validates that raw (pipeline-generated) PPTX files produce structurally
 * correct animation timing XML, pass XSD validation, and match expected
 * patterns from native PowerPoint reference files.
 */
public class AnimationsCRUDCorrectnessTest extends CRUDCorrectnessBase {

    @Override
    protected String category() {
        return "animations-crud";
    }

    @Test
    public void rawFilesPassSchemaValidation() throws Exception {
        File[] rawFiles = rawDir().listFiles((d, name) -> name.endsWith(".pptx"));
        Assumptions.assumeTrue(rawFiles != null && rawFiles.length > 0,
                "No raw PPTX files in animations-crud/raw/");

        for (File rawFile : rawFiles) {
            assertSchemaValid(rawFile, "ppt/slides/slide1.xml");
        }
    }

    @Test
    public void rawFilesHaveValidTimingStructure() throws Exception {
        File[] rawFiles = rawDir().listFiles((d, name) -> name.endsWith(".pptx"));
        Assumptions.assumeTrue(rawFiles != null && rawFiles.length > 0,
                "No raw PPTX files in animations-crud/raw/");

        for (File rawFile : rawFiles) {
            Document slideDoc = extractXMLPart(rawFile, "ppt/slides/slide1.xml");
            Assumptions.assumeTrue(slideDoc != null,
                    "No slide1.xml in " + rawFile.getName());

            assertTimingStructureValid(slideDoc);
        }
    }

    @Test
    public void nativeReferenceFilesExist() {
        File nativeDir = nativeDir();
        Assumptions.assumeTrue(nativeDir.isDirectory(),
                "animations-crud/native/ does not exist");

        File[] nativeFiles = nativeDir.listFiles((d, name) -> name.endsWith(".pptx"));
        assertNotNull(nativeFiles, "Failed to list native directory");
        assertTrue(nativeFiles.length > 0,
                "animations-crud/native/ should contain at least one reference file");
    }

    @Test
    public void rawUpdateDurationHasCorrectTiming() throws Exception {
        File rawFile = rawFile("crud_update_duration.pptx");
        Assumptions.assumeTrue(rawFile.exists(), "crud_update_duration.pptx not generated");

        Document doc = extractXMLPart(rawFile, "ppt/slides/slide1.xml");
        XPath xpath = WriterTestFixtures.createConfiguredXPath();

        // The animEffect's cTn should have dur=1000 (updated from 500)
        String dur = xpath.evaluate(
            "//p:animEffect/p:cBhvr/p:cTn/@dur", doc);
        assertEquals("1000", dur, "update-animation should have changed duration to 1000");
    }

    @Test
    public void rawMultiTriggerHasAllThreeTypes() throws Exception {
        File rawFile = rawFile("crud_multi_trigger.pptx");
        Assumptions.assumeTrue(rawFile.exists(), "crud_multi_trigger.pptx not generated");

        Document doc = extractXMLPart(rawFile, "ppt/slides/slide1.xml");
        XPath xpath = WriterTestFixtures.createConfiguredXPath();

        // Should have clickEffect, withEffect, and afterEffect nodeTypes
        NodeList animations = (NodeList) xpath.evaluate(
            "//p:cTn[@presetID]", doc, javax.xml.xpath.XPathConstants.NODESET);
        assertEquals(3, animations.getLength(), "Should have 3 animations");

        boolean hasClick = false, hasWith = false, hasAfter = false;
        for (int i = 0; i < animations.getLength(); i++) {
            String nodeType = animations.item(i).getAttributes()
                .getNamedItem("nodeType").getTextContent();
            if ("clickEffect".equals(nodeType)) hasClick = true;
            if ("withEffect".equals(nodeType)) hasWith = true;
            if ("afterEffect".equals(nodeType)) hasAfter = true;
        }
        assertTrue(hasClick, "Should have clickEffect");
        assertTrue(hasWith, "Should have withEffect");
        assertTrue(hasAfter, "Should have afterEffect");
    }

    @Test
    public void rawEmphasisEntranceMixHasCorrectStructure() throws Exception {
        File rawFile = rawFile("crud_emphasis_entrance_mix.pptx");
        Assumptions.assumeTrue(rawFile.exists(), "crud_emphasis_entrance_mix.pptx not generated");

        Document doc = extractXMLPart(rawFile, "ppt/slides/slide1.xml");
        XPath xpath = WriterTestFixtures.createConfiguredXPath();

        // Should have both emphasis and entrance animations
        NodeList animations = (NodeList) xpath.evaluate(
            "//p:cTn[@presetClass]", doc, javax.xml.xpath.XPathConstants.NODESET);

        boolean hasEmph = false, hasEntr = false;
        for (int i = 0; i < animations.getLength(); i++) {
            String presetClass = animations.item(i).getAttributes()
                .getNamedItem("presetClass").getTextContent();
            if ("emph".equals(presetClass)) hasEmph = true;
            if ("entr".equals(presetClass)) hasEntr = true;
        }
        assertTrue(hasEmph, "Should have emphasis animation");
        assertTrue(hasEntr, "Should have entrance animation");

        // Emphasis should NOT have grpId; entrance should
        String emphGrpId = xpath.evaluate(
            "//p:cTn[@presetClass='emph']/@grpId", doc);
        assertTrue(emphGrpId == null || emphGrpId.isEmpty(),
            "Emphasis animation should not have grpId");

        String entrGrpId = xpath.evaluate(
            "//p:cTn[@presetClass='entr']/@grpId", doc);
        assertFalse(entrGrpId.isEmpty(),
            "Entrance animation should have grpId");
    }

    @Test
    public void repairedFilesDiffFromRaw() throws Exception {
        File[] repairedFiles = repairedDir().listFiles((d, name) -> name.endsWith(".pptx"));
        Assumptions.assumeTrue(repairedFiles != null && repairedFiles.length > 0,
                "No repaired PPTX files in animations-crud/repaired/");

        for (File repaired : repairedFiles) {
            String baseName = repaired.getName().replace("_repaired.pptx", ".pptx");
            File raw = rawFile(baseName);
            Assumptions.assumeTrue(raw.exists(),
                    "No matching raw file for " + repaired.getName());

            boolean hasDiff = hasTimingDifferences(raw, repaired, 1);
            if (hasDiff) {
                System.out.println("[REPAIR DIFF] " + baseName
                        + " -- PowerPoint modified timing on slide 1");
            } else {
                System.out.println("[REPAIR MATCH] " + baseName
                        + " -- timing preserved through PowerPoint repair");
            }
        }
    }
}
