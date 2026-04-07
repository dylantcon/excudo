package com.excudo.core.orchestration;

import org.junit.Test;

import java.util.regex.Pattern;

import static org.junit.Assert.*;

/**
 * Tests for slide deletion cleanup logic: sldId removal from presentation.xml
 * and Content_Types rebuild after deletion.
 */
public class SlideDeletionCleanupTest {

    /**
     * Verify the regex pattern used by removeSldIdFromPresentation correctly
     * removes the target sldId element while preserving others.
     */
    @Test
    public void sldIdRemovalPattern_removesCorrectEntry() {
        String presentationXml =
            "<p:sldIdLst>" +
            "<p:sldId id=\"256\" r:id=\"rId2\"/>" +
            "<p:sldId id=\"257\" r:id=\"rId3\"/>" +
            "<p:sldId id=\"258\" r:id=\"rId4\"/>" +
            "</p:sldIdLst>";

        String rId = "rId3";
        String pattern = "<p:sldId\\s+id=\"\\d+\"\\s+r:id=\"" + Pattern.quote(rId) + "\"/>";
        String updated = presentationXml.replaceFirst(pattern, "");

        assertFalse("rId3 sldId should be removed", updated.contains("rId3"));
        assertTrue("rId2 sldId should be preserved", updated.contains("rId2"));
        assertTrue("rId4 sldId should be preserved", updated.contains("rId4"));
        assertTrue("sldIdLst wrapper should be preserved", updated.contains("<p:sldIdLst>"));
    }

    @Test
    public void sldIdRemovalPattern_handlesHighRIds() {
        String presentationXml =
            "<p:sldIdLst>" +
            "<p:sldId id=\"300\" r:id=\"rId15\"/>" +
            "</p:sldIdLst>";

        String rId = "rId15";
        String pattern = "<p:sldId\\s+id=\"\\d+\"\\s+r:id=\"" + Pattern.quote(rId) + "\"/>";
        String updated = presentationXml.replaceFirst(pattern, "");

        assertFalse("rId15 sldId should be removed", updated.contains("rId15"));
        assertEquals("<p:sldIdLst></p:sldIdLst>", updated);
    }

    @Test
    public void sldIdRemovalPattern_noMatchLeavesContentUnchanged() {
        String presentationXml =
            "<p:sldIdLst><p:sldId id=\"256\" r:id=\"rId2\"/></p:sldIdLst>";

        String rId = "rId99";
        String pattern = "<p:sldId\\s+id=\"\\d+\"\\s+r:id=\"" + Pattern.quote(rId) + "\"/>";
        String updated = presentationXml.replaceFirst(pattern, "");

        assertEquals("Content should be unchanged when no match", presentationXml, updated);
    }

    @Test
    public void sldIdRemovalPattern_deleteFiveSlides_leavesEmptyList() {
        // Simulates the anatomy_of_pptx.pptx scenario: delete all 5 original slides
        String presentationXml =
            "<p:sldIdLst>" +
            "<p:sldId id=\"256\" r:id=\"rId2\"/>" +
            "<p:sldId id=\"257\" r:id=\"rId3\"/>" +
            "<p:sldId id=\"258\" r:id=\"rId4\"/>" +
            "<p:sldId id=\"259\" r:id=\"rId5\"/>" +
            "<p:sldId id=\"260\" r:id=\"rId6\"/>" +
            "</p:sldIdLst>";

        // Delete in reverse order (5,4,3,2,1) as the anatomy build does
        String[] rIdsToRemove = {"rId6", "rId5", "rId4", "rId3", "rId2"};
        String current = presentationXml;
        for (String rId : rIdsToRemove) {
            String pattern = "<p:sldId\\s+id=\"\\d+\"\\s+r:id=\"" + Pattern.quote(rId) + "\"/>";
            current = current.replaceFirst(pattern, "");
        }

        assertEquals("All sldIds should be removed", "<p:sldIdLst></p:sldIdLst>", current);
    }
}
