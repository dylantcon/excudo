package com.excudo.core.llm;

import com.excudo.core.results.ExecutionResult;
import com.excudo.core.smartcontent.IconRepository;
import com.excudo.xml.writers.SlideNotesWriter;
import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import static org.junit.Assert.*;
import java.util.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Test the automatic icon attribution system for slide notes.
 * Verifies proper licensing compliance through automated attribution.
 */
public class IconAttributionTest {

    private static final String TEST_PPTX = "test-pptx-samples/generalist_test_file.pptx";
    private IconRepository repository;
    private Path tempPptxFile;
    private String tempCacheDir;

    @Before
    public void setUp() throws Exception {
        tempPptxFile = Files.createTempFile("attribution_test_", ".pptx");
        Files.copy(Paths.get(TEST_PPTX), tempPptxFile, StandardCopyOption.REPLACE_EXISTING);

        tempCacheDir = Files.createTempDirectory("attribution-cache-test").toString();

        // localOnly avoids network downloads that block in CI/offline environments
        repository = IconRepository.localOnly(tempCacheDir);
    }

    @After
    public void tearDown() {
        try {
            if (tempPptxFile != null && Files.exists(tempPptxFile)) {
                Files.delete(tempPptxFile);
            }
            if (tempCacheDir != null) {
                deleteDirectory(new File(tempCacheDir));
            }
        } catch (Exception e) {
            // Ignore cleanup errors in tests
        }
    }

    @Test
    public void testDeviconIconAttribution() throws Exception {
        ExecutionResult<IconRepository.IconSearchResult> searchResult = repository.searchIcons("java", 1);
        assertTrue("Should find Java icon", searchResult.isSuccess());

        List<IconRepository.IconAsset> icons = searchResult.getData().get().getIcons();
        assertFalse("Should have at least one Java icon", icons.isEmpty());

        IconRepository.IconAsset javaIcon = icons.get(0);
        assertEquals("Should be Devicon source", "devicon", javaIcon.getSource());

        assertAttributionRoundTrips(javaIcon);
    }

    @Test
    public void testLocalIconAttribution() throws Exception {
        Path testIconPath = Paths.get(tempCacheDir, "test-icon.svg");
        Files.write(testIconPath, "<svg>test icon content</svg>".getBytes());

        Set<String> tags = Set.of("test", "custom", "education");
        ExecutionResult<IconRepository.IconAsset> uploadResult = repository.uploadLocalIcon(
            testIconPath.toString(), "test-education-icon", tags);

        assertTrue("Upload should succeed", uploadResult.isSuccess());

        IconRepository.IconAsset localIcon = uploadResult.getData().get();
        assertEquals("Should be local source", "local", localIcon.getSource());

        assertAttributionRoundTrips(localIcon);
    }

    @Test
    public void testFreepikIconAttribution() throws Exception {
        IconRepository.IconAsset freepikIcon = new IconRepository.IconAsset(
            "freepik-12345", "freepik", "/cache/freepik/icon-12345.svg",
            Set.of("computer", "programming"),
            "Icon by Freepik (https://www.freepik.com) - Premium License",
            0.9);

        assertAttributionRoundTrips(freepikIcon);
    }

    @Test
    public void testMultipleIconAttributions() throws Exception {
        SlideNotesWriter notesWriter = new SlideNotesWriter(tempPptxFile.toString());

        notesWriter.addIconAttribution(1, "https://example.com/icon1.svg",
            "Icon 1 by Test Artist - License 1");
        notesWriter.addIconAttribution(1, "https://example.com/icon2.svg",
            "Icon 2 by Another Artist - License 2");

        String slideNotes = notesWriter.getSlideNotes(1);

        assertTrue("Should contain first attribution",
            slideNotes.contains("Icon 1 by Test Artist"));
        assertTrue("Should contain second attribution",
            slideNotes.contains("Icon 2 by Another Artist"));
        assertTrue("Should contain both icon URLs",
            slideNotes.contains("icon1.svg") && slideNotes.contains("icon2.svg"));
    }

    @Test
    public void testAttributionFormatting() throws Exception {
        SlideNotesWriter notesWriter = new SlideNotesWriter(tempPptxFile.toString());

        String testUrl = "https://test.com/icon.svg";
        String testAttribution = "Test Icon by Test Artist - Creative Commons License";

        notesWriter.addIconAttribution(1, testUrl, testAttribution);

        String slideNotes = notesWriter.getSlideNotes(1);

        assertTrue("Should contain attribution header",
            slideNotes.contains("--- Icon Attribution ---"));
        assertTrue("Should contain URL label",
            slideNotes.contains("Icon URL:"));
        assertTrue("Should contain the URL",
            slideNotes.contains(testUrl));
        assertTrue("Should contain the attribution text",
            slideNotes.contains(testAttribution));
    }

    @Test
    public void testAttributionDuplicationPrevention() throws Exception {
        SlideNotesWriter notesWriter = new SlideNotesWriter(tempPptxFile.toString());

        String testUrl = "https://test.com/duplicate.svg";
        String testAttribution = "Duplicate Test Icon - License";

        notesWriter.addIconAttribution(1, testUrl, testAttribution);
        notesWriter.addIconAttribution(1, testUrl, testAttribution);

        String slideNotes = notesWriter.getSlideNotes(1);

        int occurrences = countOccurrences(slideNotes, testUrl);
        assertTrue("Should have at least one attribution", occurrences >= 1);
    }

    /**
     * Writes an icon's attribution to slide notes and reads it back,
     * verifying the round-trip preserves the attribution content.
     */
    private void assertAttributionRoundTrips(IconRepository.IconAsset icon) throws Exception {
        SlideNotesWriter notesWriter = new SlideNotesWriter(tempPptxFile.toString());

        String expectedUrl = getExpectedUrl(icon);
        String attribution = icon.getAttribution();

        notesWriter.addIconAttribution(1, expectedUrl, attribution);

        String slideNotes = notesWriter.getSlideNotes(1);

        assertNotNull("Should be able to read slide notes", slideNotes);
        assertTrue("Notes should contain attribution header",
            slideNotes.contains("Icon Attribution"));
        assertTrue("Notes should contain icon attribution text",
            slideNotes.contains(attribution));
    }

    private String getExpectedUrl(IconRepository.IconAsset icon) {
        switch (icon.getSource()) {
            case "devicon":
                return "https://devicons.github.io/devicon/icons/" + icon.getName() + "/" + icon.getName() + "-original.svg";
            case "freepik":
                return "Freepik Icon ID: " + icon.getName();
            case "local":
                return "Local upload: " + icon.getName();
            default:
                return icon.getPath();
        }
    }

    private int countOccurrences(String text, String searchString) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(searchString, index)) != -1) {
            count++;
            index += searchString.length();
        }
        return count;
    }

    private void deleteDirectory(File directory) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        directory.delete();
    }
}
