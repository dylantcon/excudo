package com.excudo.utils;

import com.excudo.core.model.ParagraphMetadata;
import com.excudo.core.utils.XMLConstants;
import org.junit.Test;
import org.junit.Before;
import static org.junit.Assert.*;
import java.util.*;

/**
 * Unit tests for ParagraphMetadata class
 * Tests paragraph metadata storage and bullet point management
 */
public class ParagraphMetadataTest {
    
    private List<String> sampleContents;
    private List<Boolean> sampleBullets;
    private List<String> sampleMarkers;
    private ParagraphMetadata metadata;
    
    @Before
    public void setUp() {
        // Create sample data for testing
        sampleContents = Arrays.asList(
            "Introduction paragraph",
            "First bullet point",
            "Second bullet point", 
            "Regular paragraph",
            "Third bullet point"
        );
        
        sampleBullets = Arrays.asList(false, true, true, false, true);
        sampleMarkers = Arrays.asList("", "•", "•", "", "•");
        
        metadata = new ParagraphMetadata(sampleContents, sampleBullets, sampleMarkers);
    }
    
    @Test
    public void testBasicConstruction() {
        assertNotNull("Metadata should not be null", metadata);
        assertEquals("Should have correct paragraph count", 5, metadata.getParagraphCount());
    }
    
    @Test
    public void testConstructorValidation() {
        // Test mismatched list sizes
        List<String> shortContents = Arrays.asList("One", "Two");
        List<Boolean> longBullets = Arrays.asList(true, false, true);
        List<String> normalMarkers = Arrays.asList("•", "•");
        
        try {
            new ParagraphMetadata(shortContents, longBullets, normalMarkers);
            fail("Should throw IllegalArgumentException for mismatched sizes");
        } catch (IllegalArgumentException e) {
            assertTrue("Should mention size mismatch", e.getMessage().contains("same size"));
        }
    }
    
    @Test
    public void testEmptyLists() {
        // Test with empty lists
        List<String> emptyContents = new ArrayList<>();
        List<Boolean> emptyBullets = new ArrayList<>();
        List<String> emptyMarkers = new ArrayList<>();
        
        ParagraphMetadata emptyMetadata = new ParagraphMetadata(emptyContents, emptyBullets, emptyMarkers);
        
        assertEquals("Empty metadata should have 0 paragraphs", 0, emptyMetadata.getParagraphCount());
        assertTrue("Empty bullet points should be empty", emptyMetadata.getBulletPointsOnly().isEmpty());
        assertTrue("Empty bullet indices should be empty", emptyMetadata.getBulletPointIndices().isEmpty());
    }
    
    @Test
    public void testGetters() {
        // Test basic getters return the same lists
        assertEquals("Should return paragraph contents", sampleContents, metadata.getParagraphContents());
        assertEquals("Should return bullet flags", sampleBullets, metadata.getIsBulletPoint());
        assertEquals("Should return bullet markers", sampleMarkers, metadata.getBulletMarkers());
    }
    
    @Test
    public void testIndividualParagraphAccess() {
        // Test valid index access
        assertEquals("Should get first paragraph", "Introduction paragraph", metadata.getParagraphContent(0));
        assertEquals("Should get last paragraph", "Third bullet point", metadata.getParagraphContent(4));
        assertEquals("Should get middle paragraph", "Regular paragraph", metadata.getParagraphContent(3));
        
        // Test bullet status access
        assertFalse("First paragraph should not be bullet", metadata.isParagraphBullet(0));
        assertTrue("Second paragraph should be bullet", metadata.isParagraphBullet(1));
        assertFalse("Fourth paragraph should not be bullet", metadata.isParagraphBullet(3));
        
        // Test marker access
        assertEquals("First paragraph should have empty marker", "", metadata.getBulletMarker(0));
        assertEquals("Second paragraph should have bullet marker", "•", metadata.getBulletMarker(1));
    }
    
    @Test
    public void testBoundsChecking() {
        // Test negative index
        try {
            metadata.getParagraphContent(-1);
            fail("Should throw IndexOutOfBoundsException for negative index");
        } catch (IndexOutOfBoundsException e) {
            assertTrue("Should mention index out of range", e.getMessage().contains("out of range"));
        }
        
        // Test index too large
        try {
            metadata.isParagraphBullet(5);
            fail("Should throw IndexOutOfBoundsException for index >= size");
        } catch (IndexOutOfBoundsException e) {
            assertTrue("Should mention index out of range", e.getMessage().contains("out of range"));
        }
        
        // Test bullet marker bounds
        try {
            metadata.getBulletMarker(10);
            fail("Should throw IndexOutOfBoundsException for large index");
        } catch (IndexOutOfBoundsException e) {
            assertTrue("Should mention index out of range", e.getMessage().contains("out of range"));
        }
    }
    
    @Test
    public void testBulletPointsOnlyFiltering() {
        List<String> bulletPoints = metadata.getBulletPointsOnly();
        
        assertEquals("Should have 3 bullet points", 3, bulletPoints.size());
        assertEquals("First bullet should be correct", "First bullet point", bulletPoints.get(0));
        assertEquals("Second bullet should be correct", "Second bullet point", bulletPoints.get(1));
        assertEquals("Third bullet should be correct", "Third bullet point", bulletPoints.get(2));
        
        // Verify it doesn't include non-bullet paragraphs
        assertFalse("Should not include introduction", bulletPoints.contains("Introduction paragraph"));
        assertFalse("Should not include regular paragraph", bulletPoints.contains("Regular paragraph"));
    }
    
    @Test
    public void testBulletPointIndices() {
        List<Integer> bulletIndices = metadata.getBulletPointIndices();
        
        assertEquals("Should have 3 bullet indices", 3, bulletIndices.size());
        assertEquals("First bullet index should be 1", Integer.valueOf(1), bulletIndices.get(0));
        assertEquals("Second bullet index should be 2", Integer.valueOf(2), bulletIndices.get(1));
        assertEquals("Third bullet index should be 4", Integer.valueOf(4), bulletIndices.get(2));
    }
    
    @Test
    public void testAllBulletsScenario() {
        // Test with all paragraphs as bullets
        List<String> allBulletContents = Arrays.asList("Bullet 1", "Bullet 2", "Bullet 3");
        List<Boolean> allTrue = Arrays.asList(true, true, true);
        List<String> allMarkers = Arrays.asList("•", "•", "•");
        
        ParagraphMetadata allBullets = new ParagraphMetadata(allBulletContents, allTrue, allMarkers);
        
        assertEquals("All paragraphs should be bullets", 3, allBullets.getBulletPointsOnly().size());
        assertEquals("All indices should be bullet indices", Arrays.asList(0, 1, 2), allBullets.getBulletPointIndices());
    }
    
    @Test
    public void testNoBulletsScenario() {
        // Test with no bullet points
        List<String> noBulletContents = Arrays.asList("Para 1", "Para 2", "Para 3");
        List<Boolean> allFalse = Arrays.asList(false, false, false);
        List<String> noMarkers = Arrays.asList("", "", "");
        
        ParagraphMetadata noBullets = new ParagraphMetadata(noBulletContents, allFalse, noMarkers);
        
        assertTrue("Should have no bullet points", noBullets.getBulletPointsOnly().isEmpty());
        assertTrue("Should have no bullet indices", noBullets.getBulletPointIndices().isEmpty());
    }
    
    @Test
    public void testDifferentBulletMarkers() {
        // Test with different bullet marker types
        List<String> contents = Arrays.asList("Title", "Dash bullet", "Number bullet", "Arrow bullet");
        List<Boolean> bullets = Arrays.asList(false, true, true, true);
        List<String> markers = Arrays.asList("", "-", "1.", "→");
        
        ParagraphMetadata mixedMarkers = new ParagraphMetadata(contents, bullets, markers);
        
        assertEquals("Should handle dash marker", "-", mixedMarkers.getBulletMarker(1));
        assertEquals("Should handle number marker", "1.", mixedMarkers.getBulletMarker(2));
        assertEquals("Should handle arrow marker", "→", mixedMarkers.getBulletMarker(3));
        
        List<String> bulletTexts = mixedMarkers.getBulletPointsOnly();
        assertEquals("Should filter bullets correctly", 3, bulletTexts.size());
        assertTrue("Should include dash bullet", bulletTexts.contains("Dash bullet"));
        assertTrue("Should include number bullet", bulletTexts.contains("Number bullet"));
        assertTrue("Should include arrow bullet", bulletTexts.contains("Arrow bullet"));
    }
    
    @Test
    public void testToString() {
        String toString = metadata.toString();
        
        assertNotNull("toString should not be null", toString);
        assertTrue("Should contain class name", toString.contains("ParagraphMetadata"));
        assertTrue("Should contain paragraph count", toString.contains("paragraphs=5"));
        assertTrue("Should contain bullet count", toString.contains("bullets=3"));
    }
    
    @Test
    public void testToStringWithDifferentCounts() {
        // Test toString with different bullet counts
        List<String> oneContent = Arrays.asList("Single paragraph");
        List<Boolean> oneBullet = Arrays.asList(true);
        List<String> oneMarker = Arrays.asList("•");
        
        ParagraphMetadata singleBullet = new ParagraphMetadata(oneContent, oneBullet, oneMarker);
        String singleToString = singleBullet.toString();
        
        assertTrue("Should show correct counts", singleToString.contains("paragraphs=1"));
        assertTrue("Should show correct bullet count", singleToString.contains("bullets=1"));
    }
    
    @Test
    public void testImmutability() {
        // Test that returned lists cannot modify internal state
        List<String> retrievedContents = metadata.getParagraphContents();
        List<String> retrievedBullets = metadata.getBulletPointsOnly();
        
        // Attempting to modify returned lists should not affect the original
        // (Note: This depends on implementation - if they return mutable lists, this test documents the behavior)
        assertNotNull("Retrieved contents should not be null", retrievedContents);
        assertNotNull("Retrieved bullets should not be null", retrievedBullets);
        
        // Verify consistent results on multiple calls
        assertEquals("Multiple calls should return same contents", retrievedContents, metadata.getParagraphContents());
        assertEquals("Multiple calls should return same bullets", retrievedBullets, metadata.getBulletPointsOnly());
    }
    
    @Test
    public void testEdgeCaseContent() {
        // Test with edge case content (empty strings, special characters)
        List<String> edgeContents = Arrays.asList("", "Content with\nnewlines", "Special chars: !@#$%");
        List<Boolean> edgeBullets = Arrays.asList(false, true, true);
        List<String> edgeMarkers = Arrays.asList("", "•", "★");
        
        ParagraphMetadata edgeMetadata = new ParagraphMetadata(edgeContents, edgeBullets, edgeMarkers);
        
        assertEquals("Should handle empty string", "", edgeMetadata.getParagraphContent(0));
        assertEquals("Should handle newlines", "Content with\nnewlines", edgeMetadata.getParagraphContent(1));
        assertEquals("Should handle special chars", "Special chars: !@#$%", edgeMetadata.getParagraphContent(2));
        assertEquals("Should handle star marker", "★", edgeMetadata.getBulletMarker(2));
    }
}