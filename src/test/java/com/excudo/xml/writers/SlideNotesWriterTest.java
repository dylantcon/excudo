package com.excudo.xml.writers;

import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;
import com.excudo.exceptions.XMLParsingException;

/**
 * Tests for SlideNotesWriter functionality
 */
public class SlideNotesWriterTest {
    
    private static final String TEST_PPTX = "test-pptx-samples/generalist_test_file.pptx";
    private Path tempFile;
    private SlideNotesWriter notesWriter;
    
    @BeforeEach
    public void setUp() throws IOException, XMLParsingException {
        // Create a temporary copy of the test file
        Path sourceFile = Paths.get(TEST_PPTX);
        tempFile = Files.createTempFile("test_notes_", ".pptx");
        Files.copy(sourceFile, tempFile, StandardCopyOption.REPLACE_EXISTING);
        
        notesWriter = new SlideNotesWriter(tempFile.toString());
    }
    
    @AfterEach
    public void tearDown() throws IOException {
        if (tempFile != null && Files.exists(tempFile)) {
            Files.delete(tempFile);
        }
    }
    
    @Test
    public void testAppendToExistingNotes() throws Exception {
        // generalist_test_file.pptx fixture has existing notes on slide 2
        // ("Notes on second slide"). Append must leave the original intact.
        String attribution = "Test Icon Attribution";
        notesWriter.appendToSlideNotes(2, attribution);

        String notes = notesWriter.getSlideNotes(2);
        assertTrue(notes.contains(attribution),
            "Notes should contain the appended attribution");
        assertTrue(notes.contains("Notes on second slide"),
            "Original notes should still be present, got: " + notes);
    }

    @Test
    public void testCreateNotesForSlideWithoutNotes() throws Exception {
        // Slide 1 has no notes in the fixture. appendToSlideNotes must
        // create the notesSlide part from scratch.
        String attribution = "New Slide Notes";
        notesWriter.appendToSlideNotes(1, attribution);

        String notes = notesWriter.getSlideNotes(1);
        assertTrue(notes.contains(attribution),
            "Notes should contain the attribution, got: " + notes);
    }
    
    @Test
    public void testAddIconAttribution() throws Exception {
        String iconUrl = "https://example.com/icon.png";
        String attribution = "Icon by Author - License CC BY 4.0";
        
        notesWriter.addIconAttribution(1, iconUrl, attribution);
        
        String notes = notesWriter.getSlideNotes(1);
        assertTrue(notes.contains("Icon Attribution"), 
            "Notes should contain attribution header");
        assertTrue(notes.contains(iconUrl), 
            "Notes should contain icon URL");
        assertTrue(notes.contains(attribution), 
            "Notes should contain attribution text");
    }
    
    @Test
    public void testMultipleAttributions() throws Exception {
        // Add multiple icon attributions
        notesWriter.addIconAttribution(1, "https://example.com/icon1.png", 
            "Icon 1 by Author1");
        notesWriter.addIconAttribution(1, "https://example.com/icon2.png", 
            "Icon 2 by Author2");
        
        String notes = notesWriter.getSlideNotes(1);
        assertTrue(notes.contains("icon1.png"), 
            "Notes should contain first icon");
        assertTrue(notes.contains("icon2.png"), 
            "Notes should contain second icon");
    }
}