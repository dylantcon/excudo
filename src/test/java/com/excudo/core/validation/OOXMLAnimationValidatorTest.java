package com.excudo.core.validation;

import com.excudo.core.utils.XMLConstants;
import org.junit.jupiter.api.*;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.w3c.dom.NodeList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for OOXMLAnimationValidator including MS-OE376 Section 4.6.16 validations.
 */
class OOXMLAnimationValidatorTest {

    private static final String P_NS = XMLConstants.PRESENTATION_NS;
    private static final String A_NS = XMLConstants.DRAWING_NS;

    private OOXMLAnimationValidator validator;
    private DocumentBuilder documentBuilder;

    @BeforeEach
    void setUp() throws Exception {
        validator = new OOXMLAnimationValidator();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        documentBuilder = factory.newDocumentBuilder();
    }

    // ========== Helper: build a slide DOM with shapes, animations, and build entries ==========

    /**
     * Build a minimal slide document with configurable shapes, animation entries, and build list.
     *
     * @param shapeIds     shape IDs to add to spTree
     * @param animations   array of {spid, presetClass} pairs for animation targets
     * @param buildEntries array of {spid, grpId} pairs for bldLst entries (null = no bldLst)
     * @param fillValues   optional fill values for cTn nodes (null = use "hold")
     */
    private Document buildSlideDocument(int[] shapeIds, String[][] animations,
                                        String[][] buildEntries, String[] fillValues) throws Exception {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        xml.append("<p:sld xmlns:p=\"").append(P_NS).append("\"");
        xml.append(" xmlns:a=\"").append(A_NS).append("\">");
        xml.append("<p:cSld><p:spTree>");
        xml.append("<p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>");
        xml.append("<p:grpSpPr/>");

        // Add shapes
        if (shapeIds != null) {
            for (int id : shapeIds) {
                xml.append("<p:sp><p:nvSpPr><p:cNvPr id=\"").append(id)
                   .append("\" name=\"Shape ").append(id).append("\"/>");
                xml.append("<p:cNvSpPr/><p:nvPr/></p:nvSpPr><p:spPr/></p:sp>");
            }
        }

        xml.append("</p:spTree></p:cSld>");

        // Add timing tree if there are animations
        if (animations != null && animations.length > 0) {
            xml.append("<p:timing><p:tnLst><p:par>");
            xml.append("<p:cTn id=\"1\" dur=\"indefinite\" restart=\"never\" nodeType=\"tmRoot\">");
            xml.append("<p:childTnLst><p:seq concurrent=\"1\" nextAc=\"seek\">");
            xml.append("<p:cTn id=\"2\" dur=\"indefinite\" nodeType=\"mainSeq\">");
            xml.append("<p:childTnLst>");

            // Click trigger
            xml.append("<p:par><p:cTn id=\"3\" fill=\"hold\">");
            xml.append("<p:stCondLst><p:cond delay=\"indefinite\"/></p:stCondLst>");
            xml.append("<p:childTnLst><p:par><p:cTn id=\"4\" fill=\"hold\">");
            xml.append("<p:stCondLst><p:cond delay=\"0\"/></p:stCondLst>");
            xml.append("<p:childTnLst>");

            int nextId = 5;
            for (int i = 0; i < animations.length; i++) {
                String spid = animations[i][0];
                String presetClass = animations[i][1];
                String fill = (fillValues != null && i < fillValues.length) ? fillValues[i] : "hold";

                xml.append("<p:par><p:cTn id=\"").append(nextId++).append("\"");
                xml.append(" presetID=\"10\" presetClass=\"").append(presetClass).append("\"");
                xml.append(" presetSubtype=\"0\" fill=\"").append(fill).append("\"");
                if (!"emph".equals(presetClass)) {
                    xml.append(" grpId=\"").append(i).append("\"");
                }
                xml.append(" nodeType=\"clickEffect\">");
                xml.append("<p:stCondLst><p:cond delay=\"0\"/></p:stCondLst>");
                xml.append("<p:childTnLst>");

                // For entrance, add visibility set + animEffect
                if ("entr".equals(presetClass)) {
                    xml.append("<p:set><p:cBhvr><p:cTn id=\"").append(nextId++).append("\" dur=\"1\" fill=\"hold\"/>");
                    xml.append("<p:tgtEl><p:spTgt spid=\"").append(spid).append("\"><p:txEl><p:pRg st=\"0\" end=\"0\"/></p:txEl></p:spTgt></p:tgtEl>");
                    xml.append("<p:attrNameLst><p:attrName>style.visibility</p:attrName></p:attrNameLst>");
                    xml.append("</p:cBhvr><p:to><p:strVal val=\"visible\"/></p:to></p:set>");
                }

                xml.append("<p:animEffect transition=\"").append("entr".equals(presetClass) ? "in" : "emph".equals(presetClass) ? "none" : "out").append("\"");
                xml.append(" filter=\"fade\"><p:cBhvr><p:cTn id=\"").append(nextId++).append("\" dur=\"500\"/>");
                xml.append("<p:tgtEl><p:spTgt spid=\"").append(spid).append("\"><p:txEl><p:pRg st=\"0\" end=\"0\"/></p:txEl></p:spTgt></p:tgtEl>");
                xml.append("</p:cBhvr></p:animEffect>");

                xml.append("</p:childTnLst></p:cTn></p:par>");
            }

            xml.append("</p:childTnLst></p:cTn></p:par></p:childTnLst></p:cTn></p:par>");
            xml.append("</p:childTnLst></p:cTn>");
            xml.append("<p:prevCondLst><p:cond evt=\"onPrev\" delay=\"0\"><p:tgtEl><p:sldTgt/></p:tgtEl></p:cond></p:prevCondLst>");
            xml.append("<p:nextCondLst><p:cond evt=\"onNext\" delay=\"0\"><p:tgtEl><p:sldTgt/></p:tgtEl></p:cond></p:nextCondLst>");
            xml.append("</p:seq></p:childTnLst></p:cTn></p:par></p:tnLst>");

            // Build list
            if (buildEntries != null) {
                xml.append("<p:bldLst>");
                for (String[] entry : buildEntries) {
                    xml.append("<p:bldP spid=\"").append(entry[0]).append("\" grpId=\"").append(entry[1]).append("\"/>");
                }
                xml.append("</p:bldLst>");
            }

            xml.append("</p:timing>");
        }

        xml.append("</p:sld>");

        return documentBuilder.parse(new ByteArrayInputStream(xml.toString().getBytes(StandardCharsets.UTF_8)));
    }

    // ========== Tests: Build List Uniqueness (MS-OE376 4.6.16b) ==========

    @Test
    @DisplayName("Duplicate (spid, grpId) pair in build list detected")
    void testDuplicateBuildListEntryDetected() throws Exception {
        Document doc = buildSlideDocument(
            new int[]{3},
            new String[][]{{"3", "entr"}, {"3", "entr"}},
            new String[][]{{"3", "0"}, {"3", "0"}},  // duplicate
            null
        );

        ValidationResult result = validator.validateAnimationStructure(doc);
        assertFalse(result.isValid(), "Should detect duplicate build list entry");
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("Duplicate build list entry")),
            "Error should mention duplicate build list entry");
    }

    @Test
    @DisplayName("Same spid with different grpIds passes uniqueness check")
    void testUniqueBuildListEntriesPass() throws Exception {
        Document doc = buildSlideDocument(
            new int[]{3},
            new String[][]{{"3", "entr"}, {"3", "entr"}},
            new String[][]{{"3", "0"}, {"3", "1"}},  // different grpIds
            null
        );

        ValidationResult result = validator.validateAnimationStructure(doc);
        boolean hasDupError = result.getErrors().stream().anyMatch(e -> e.contains("Duplicate build list entry"));
        assertFalse(hasDupError, "Same spid with different grpIds should not trigger duplicate error");
    }

    // ========== Tests: Shape References (MS-OE376 4.6.16a) ==========

    @Test
    @DisplayName("Animation targeting non-existent shape detected")
    void testOrphanedShapeReferenceDetected() throws Exception {
        // Shape 99 does not exist in spTree, but animation targets it
        Document doc = buildSlideDocument(
            new int[]{3},
            new String[][]{{"99", "entr"}},
            new String[][]{{"99", "0"}},
            null
        );

        ValidationResult result = validator.validateAnimationStructure(doc);
        assertFalse(result.isValid(), "Should detect orphaned shape reference");
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("non-existent shape spid=99")),
            "Error should mention non-existent shape");
    }

    @Test
    @DisplayName("Animation targeting existing shape passes")
    void testValidShapeReferencesPasses() throws Exception {
        Document doc = buildSlideDocument(
            new int[]{3},
            new String[][]{{"3", "entr"}},
            new String[][]{{"3", "0"}},
            null
        );

        ValidationResult result = validator.validateAnimationStructure(doc);
        boolean hasOrphanError = result.getErrors().stream().anyMatch(e -> e.contains("non-existent shape"));
        assertFalse(hasOrphanError, "Valid shape reference should not trigger error");
    }

    // ========== Tests: Emphasis + Build List ==========

    @Test
    @DisplayName("Emphasis-only slide without build list passes validation")
    void testEmphasisOnlySlideWithoutBuildListPasses() throws Exception {
        Document doc = buildSlideDocument(
            new int[]{3},
            new String[][]{{"3", "emph"}},
            null,  // no bldLst
            null
        );

        ValidationResult result = validator.validateAnimationStructure(doc);
        boolean hasBldMissing = result.getErrors().stream().anyMatch(e -> e.contains("build list is missing"));
        assertFalse(hasBldMissing, "Emphasis-only slide should not require build list");
    }

    @Test
    @DisplayName("Entrance animation without build list detected")
    void testEntranceRequiresBuildList() throws Exception {
        Document doc = buildSlideDocument(
            new int[]{3},
            new String[][]{{"3", "entr"}},
            null,  // no bldLst -- should error
            null
        );

        ValidationResult result = validator.validateAnimationStructure(doc);
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("build list is missing")),
            "Entrance animation without build list should be an error");
    }

    // ========== Tests: Fill Attribute Values ==========

    @Test
    @DisplayName("Invalid fill value detected")
    void testInvalidFillValueDetected() throws Exception {
        Document doc = buildSlideDocument(
            new int[]{3},
            new String[][]{{"3", "entr"}},
            new String[][]{{"3", "0"}},
            new String[]{"freeze"}  // invalid
        );

        ValidationResult result = validator.validateAnimationStructure(doc);
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("Unsupported fill value 'freeze'")),
            "Should detect invalid fill value");
    }

    @Test
    @DisplayName("Valid fill values pass")
    void testValidFillValuesPass() throws Exception {
        Document doc = buildSlideDocument(
            new int[]{3},
            new String[][]{{"3", "entr"}},
            new String[][]{{"3", "0"}},
            new String[]{"hold"}
        );

        ValidationResult result = validator.validateAnimationStructure(doc);
        boolean hasFillError = result.getErrors().stream().anyMatch(e -> e.contains("Unsupported fill value"));
        assertFalse(hasFillError, "fill='hold' should be valid");
    }

    // ========== Tests: Timing Node ID Uniqueness ==========

    @Test
    @DisplayName("Existing timing node uniqueness check still works")
    void testTimingNodeIdUniqueness() throws Exception {
        // Build a document and manually add a duplicate ID
        Document doc = buildSlideDocument(
            new int[]{3},
            new String[][]{{"3", "entr"}},
            new String[][]{{"3", "0"}},
            null
        );

        // Inject a duplicate timing node ID by modifying DOM directly
        NodeList cTns = doc.getElementsByTagNameNS(P_NS, "cTn");
        if (cTns.getLength() >= 2) {
            // Set second cTn to same ID as first
            String firstId = ((Element) cTns.item(0)).getAttribute("id");
            ((Element) cTns.item(1)).setAttribute("id", firstId);
        }

        ValidationResult result = validator.validateAnimationStructure(doc);
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("Duplicate timing node ID")),
            "Should detect duplicate timing node IDs");
    }

    // ========== Tests: Motion Path Validation (MS-OE376 4.6.4) ==========

    @Test
    @DisplayName("Valid motion path animation passes validation")
    void testValidMotionPathPasses() throws Exception {
        Document doc = buildMotionPathDocument("layout", "relative",
            "M 0 0 L 0.25 0.25 ", "AA", "path", "50000", "50000");

        ValidationResult result = validator.validateAnimationStructure(doc);
        boolean hasMotionError = result.getErrors().stream().anyMatch(e -> e.contains("animMotion"));
        assertFalse(hasMotionError, "Valid motion path should not produce errors");
    }

    @Test
    @DisplayName("Motion path with missing origin detected")
    void testMotionPathMissingOriginDetected() throws Exception {
        Document doc = buildMotionPathDocument("", "relative",
            "M 0 0 L 0.25 0.25 ", "AA", "path", "50000", "50000");

        ValidationResult result = validator.validateAnimationStructure(doc);
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("origin")),
            "Should detect missing origin attribute");
    }

    @Test
    @DisplayName("Motion path with missing path attribute detected")
    void testMotionPathMissingPathDetected() throws Exception {
        Document doc = buildMotionPathDocument("layout", "relative",
            "", "AA", "path", "50000", "50000");

        ValidationResult result = validator.validateAnimationStructure(doc);
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("empty path")),
            "Should detect empty path attribute");
    }

    @Test
    @DisplayName("Motion path with wrong presetClass detected")
    void testMotionPathWrongPresetClassDetected() throws Exception {
        Document doc = buildMotionPathDocument("layout", "relative",
            "M 0 0 L 0.25 0.25 ", "AA", "entr", "50000", "50000");

        ValidationResult result = validator.validateAnimationStructure(doc);
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("presetClass='path'")),
            "Should detect wrong presetClass on motion path");
    }

    /**
     * Build a slide document with a motion path animation for validation testing.
     */
    private Document buildMotionPathDocument(String origin, String pathEditMode, String path,
                                              String ptsTypes, String presetClass,
                                              String accel, String decel) throws Exception {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        xml.append("<p:sld xmlns:p=\"").append(P_NS).append("\"");
        xml.append(" xmlns:a=\"").append(A_NS).append("\">");
        xml.append("<p:cSld><p:spTree>");
        xml.append("<p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>");
        xml.append("<p:grpSpPr/>");
        xml.append("<p:sp><p:nvSpPr><p:cNvPr id=\"3\" name=\"Shape 3\"/>");
        xml.append("<p:cNvSpPr/><p:nvPr/></p:nvSpPr><p:spPr/></p:sp>");
        xml.append("</p:spTree></p:cSld>");

        xml.append("<p:timing><p:tnLst><p:par>");
        xml.append("<p:cTn id=\"1\" dur=\"indefinite\" restart=\"never\" nodeType=\"tmRoot\">");
        xml.append("<p:childTnLst><p:seq concurrent=\"1\" nextAc=\"seek\">");
        xml.append("<p:cTn id=\"2\" dur=\"indefinite\" nodeType=\"mainSeq\">");
        xml.append("<p:childTnLst>");
        xml.append("<p:par><p:cTn id=\"3\" fill=\"hold\">");
        xml.append("<p:stCondLst><p:cond delay=\"indefinite\"/></p:stCondLst>");
        xml.append("<p:childTnLst><p:par><p:cTn id=\"4\" fill=\"hold\">");
        xml.append("<p:stCondLst><p:cond delay=\"0\"/></p:stCondLst>");
        xml.append("<p:childTnLst>");

        // The motion path animation container
        xml.append("<p:par><p:cTn id=\"5\" presetID=\"42\" presetClass=\"").append(presetClass).append("\"");
        xml.append(" presetSubtype=\"0\" fill=\"hold\" grpId=\"0\" nodeType=\"clickEffect\"");
        if (!accel.isEmpty()) xml.append(" accel=\"").append(accel).append("\"");
        if (!decel.isEmpty()) xml.append(" decel=\"").append(decel).append("\"");
        xml.append(">");
        xml.append("<p:stCondLst><p:cond delay=\"0\"/></p:stCondLst>");
        xml.append("<p:childTnLst>");

        // p:animMotion element
        xml.append("<p:animMotion");
        if (!origin.isEmpty()) xml.append(" origin=\"").append(origin).append("\"");
        if (!path.isEmpty()) xml.append(" path=\"").append(path).append("\"");
        if (!pathEditMode.isEmpty()) xml.append(" pathEditMode=\"").append(pathEditMode).append("\"");
        if (!ptsTypes.isEmpty()) xml.append(" ptsTypes=\"").append(ptsTypes).append("\"");
        xml.append(">");
        xml.append("<p:cBhvr><p:cTn id=\"6\" dur=\"500\" fill=\"hold\"/>");
        xml.append("<p:tgtEl><p:spTgt spid=\"3\"/></p:tgtEl>");
        xml.append("<p:attrNameLst><p:attrName>ppt_x</p:attrName><p:attrName>ppt_y</p:attrName></p:attrNameLst>");
        xml.append("</p:cBhvr></p:animMotion>");

        xml.append("</p:childTnLst></p:cTn></p:par>");
        xml.append("</p:childTnLst></p:cTn></p:par></p:childTnLst></p:cTn></p:par>");
        xml.append("</p:childTnLst></p:cTn>");
        xml.append("<p:prevCondLst><p:cond evt=\"onPrev\" delay=\"0\"><p:tgtEl><p:sldTgt/></p:tgtEl></p:cond></p:prevCondLst>");
        xml.append("<p:nextCondLst><p:cond evt=\"onNext\" delay=\"0\"><p:tgtEl><p:sldTgt/></p:tgtEl></p:cond></p:nextCondLst>");
        xml.append("</p:seq></p:childTnLst></p:cTn></p:par></p:tnLst>");

        xml.append("<p:bldLst><p:bldP spid=\"3\" grpId=\"0\"/></p:bldLst>");
        xml.append("</p:timing>");
        xml.append("</p:sld>");

        return documentBuilder.parse(new ByteArrayInputStream(xml.toString().getBytes(StandardCharsets.UTF_8)));
    }

    // ========== Tests: Mixed entrance + emphasis ==========

    @Test
    @DisplayName("Mixed entrance and emphasis: only entrance needs bldP entry")
    void testMixedEntranceEmphasisBuildList() throws Exception {
        Document doc = buildSlideDocument(
            new int[]{3, 4},
            new String[][]{{"3", "entr"}, {"4", "emph"}},
            new String[][]{{"3", "0"}},  // only entrance shape has bldP
            null
        );

        ValidationResult result = validator.validateAnimationStructure(doc);
        boolean hasBldMissing = result.getErrors().stream().anyMatch(
            e -> e.contains("build list is missing") || e.contains("Shape 4 is animated but missing"));
        assertFalse(hasBldMissing, "Emphasis shape should not need build list entry");
    }
}
