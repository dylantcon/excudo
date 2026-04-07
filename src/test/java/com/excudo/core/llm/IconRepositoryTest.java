package com.excudo.core.llm;

import com.excudo.core.results.ExecutionResult;
import com.excudo.core.smartcontent.IconRepository;
import com.excudo.core.utils.Logger;
import com.excudo.core.utils.ComponentLogger;
import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import static org.junit.Assert.*;
import java.util.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Test the IconRepository functionality for multi-source icon management.
 * Tests Devicon integration, local uploads, and Freepik API preparation.
 * 
 * @author Excudo Test Suite
 * @version 1.0 - Icon Repository Testing
 */
public class IconRepositoryTest {
    
    private static final ComponentLogger logger = Logger.getLogger("TEST-IconRepository");
    private IconRepository repository;
    private String tempCacheDir;
    
    @Before
    public void setUp() throws Exception {
        // Create temporary cache directory
        tempCacheDir = Files.createTempDirectory("icon-repo-test").toString();
        
        // Initialize repository from environment (will use API key if available)
        repository = IconRepository.fromEnvironment(tempCacheDir);
    }
    
    @After
    public void tearDown() {
        try {
            // Clean up temp directory
            deleteDirectory(new File(tempCacheDir));
        } catch (Exception e) {
            // Ignore cleanup errors in tests
        }
    }
    
    @Test
    public void testRepositoryInitialization() {
        assertNotNull("Repository should be initialized", repository);
        assertEquals("Should have expected cache directory", tempCacheDir, repository.getCacheDirectory());
        assertTrue("Should have Devicon icons available", repository.getDeviconIconCount() > 0);
        assertEquals("Should start with no local uploads", 0, repository.getLocalIconCount());
        
        // Note: API key availability depends on environment configuration
        // This test passes regardless of API key presence
        assertTrue("Should be Freepik enabled", repository.isFreepikAPIEnabled());
    }
    
    @Test
    public void testDeviconSearch() {
        ExecutionResult<IconRepository.IconSearchResult> result = repository.searchIcons("java", 5);
        
        assertTrue("Search should succeed", result.isSuccess());
        assertNotNull("Should return search result", result.getData().get());
        
        IconRepository.IconSearchResult searchResult = result.getData().get();
        assertEquals("Should use correct query", "java", searchResult.getSearchQuery());
        assertTrue("Should find at least one Java icon", searchResult.getIcons().size() >= 1);
        
        // Check that Java icon is found
        boolean foundJavaIcon = searchResult.getIcons().stream()
            .anyMatch(icon -> icon.getName().toLowerCase().contains("java"));
        assertTrue("Should find Java icon in Devicon", foundJavaIcon);
    }
    
    @Test
    public void testToolNameSearch() {
        // Devicon icons match by tool name, not generic concepts
        ExecutionResult<IconRepository.IconSearchResult> result = repository.searchIcons("javascript", 10);

        assertTrue("Search should succeed", result.isSuccess());

        IconRepository.IconSearchResult searchResult = result.getData().get();
        List<IconRepository.IconAsset> icons = searchResult.getIcons();

        assertTrue("Should find multiple JavaScript-related icons", icons.size() > 1);

        // Verify relevance scoring works
        if (icons.size() > 1) {
            for (int i = 1; i < icons.size(); i++) {
                double prevScore = icons.get(i - 1).getRelevanceScore();
                double currentScore = icons.get(i).getRelevanceScore();
                assertTrue("Icons should be sorted by relevance", prevScore >= currentScore);
            }
        }
    }
    
    @Test
    public void testDataScienceIconSearch() {
        ExecutionResult<IconRepository.IconSearchResult> result = repository.searchIcons("python", 3);
        
        assertTrue("Search should succeed", result.isSuccess());
        
        IconRepository.IconSearchResult searchResult = result.getData().get();
        List<IconRepository.IconAsset> icons = searchResult.getIcons();
        
        assertFalse("Should find Python icons", icons.isEmpty());
        
        // Check that Python icon has its tool-name tag
        IconRepository.IconAsset pythonIcon = icons.get(0);
        assertTrue("Python icon should have python tag",
            pythonIcon.getTags().contains("python"));
    }
    
    @Test
    public void testEmptyAndNullSearch() {
        // Test empty string
        ExecutionResult<IconRepository.IconSearchResult> emptyResult = repository.searchIcons("", 5);
        assertTrue("Empty search should succeed", emptyResult.isSuccess());
        IconRepository.IconSearchResult emptySearchResult = emptyResult.getData().get();
        assertTrue("Empty search should return no results", emptySearchResult.getIcons().isEmpty());
        
        // Test null
        ExecutionResult<IconRepository.IconSearchResult> nullResult = repository.searchIcons(null, 5);
        assertTrue("Null search should succeed", nullResult.isSuccess());
        IconRepository.IconSearchResult nullSearchResult = nullResult.getData().get();
        assertTrue("Null search should return no results", nullSearchResult.getIcons().isEmpty());
        
        // Test whitespace only
        ExecutionResult<IconRepository.IconSearchResult> spaceResult = repository.searchIcons("   ", 5);
        assertTrue("Whitespace search should succeed", spaceResult.isSuccess());
        IconRepository.IconSearchResult spaceSearchResult = spaceResult.getData().get();
        assertTrue("Whitespace search should return no results", spaceSearchResult.getIcons().isEmpty());
    }
    
    @Test
    public void testNonExistentIconSearch() {
        // Use local-only repository to ensure no external API results
        IconRepository localRepo = IconRepository.localOnly(tempCacheDir);
        
        ExecutionResult<IconRepository.IconSearchResult> result = localRepo.searchIcons("nonexistenticon123456", 5);
        
        assertTrue("Search should succeed", result.isSuccess());
        
        IconRepository.IconSearchResult searchResult = result.getData().get();
        assertTrue("Non-existent icon search should return no results in local-only mode", searchResult.getIcons().isEmpty());
    }
    
    @Test
    public void testLocalIconUpload() throws Exception {
        // Create a test icon file
        Path testIconPath = Paths.get(tempCacheDir, "test-icon.svg");
        Files.write(testIconPath, "<svg>test icon content</svg>".getBytes());
        
        Set<String> tags = Set.of("test", "custom", "upload");
        ExecutionResult<IconRepository.IconAsset> result = repository.uploadLocalIcon(
            testIconPath.toString(), "test-icon", tags);
        
        assertTrue("Upload should succeed", result.isSuccess());
        
        IconRepository.IconAsset uploadedIcon = result.getData().get();
        assertEquals("Should have correct name", "test-icon", uploadedIcon.getName());
        assertEquals("Should be local source", "local", uploadedIcon.getSource());
        assertTrue("Should contain upload tag", uploadedIcon.getTags().contains("upload"));
        assertEquals("Should have local attribution", "Local upload", uploadedIcon.getAttribution());
        
        // Verify it's searchable
        ExecutionResult<IconRepository.IconSearchResult> searchResult = repository.searchIcons("test-icon", 1);
        assertTrue("Search should find uploaded icon", 
            searchResult.getData().get().getIcons().stream()
                .anyMatch(icon -> icon.getName().equals("test-icon")));
    }
    
    @Test
    public void testUploadNonExistentFile() {
        ExecutionResult<IconRepository.IconAsset> result = repository.uploadLocalIcon(
            "/nonexistent/path/icon.svg", "test", Set.of());
        
        assertFalse("Upload should fail for non-existent file", result.isSuccess());
        assertTrue("Error message should mention file not found", 
            result.getErrorMessage().toLowerCase().contains("not exist"));
    }
    
    @Test
    public void testFreepikAPIConfiguration() {
        assertTrue("Should be Freepik enabled", repository.isFreepikAPIEnabled());
        
        // Test local-only repository
        IconRepository localOnlyRepo = IconRepository.localOnly(tempCacheDir);
        assertFalse("Local-only repo should not be Freepik enabled", localOnlyRepo.isFreepikAPIEnabled());
        assertFalse("Local-only repo should not have API key", localOnlyRepo.hasFreepikApiKey());
        
        // Test setting API key dynamically
        repository.setFreepikApiKey("test-api-key");
        assertTrue("Should have API key after setting", repository.hasFreepikApiKey());
        
        // Test clearing API key
        repository.setFreepikApiKey("");
        assertFalse("Should not have API key after clearing", repository.hasFreepikApiKey());
    }
    
    @Test
    public void testIconDownloadFailureHandling() {
        // Create a fake icon that doesn't exist
        IconRepository.IconAsset fakeIcon = new IconRepository.IconAsset(
            "fake-icon", "devicon", "/nonexistent/path.svg", 
            Set.of("fake"), "Test", 1.0);
        
        ExecutionResult<IconRepository.IconAsset> result = repository.downloadIcon(fakeIcon);
        
        // For local icons that don't exist, it should fail
        assertFalse("Download should fail for non-existent local icon", result.isSuccess());
    }
    
    @Test
    public void testRelevanceScoring() {
        ExecutionResult<IconRepository.IconSearchResult> result = repository.searchIcons("javascript", 5);
        
        assertTrue("Search should succeed", result.isSuccess());
        
        List<IconRepository.IconAsset> icons = result.getData().get().getIcons();
        
        if (!icons.isEmpty()) {
            // Check that exact matches get higher scores
            IconRepository.IconAsset firstIcon = icons.get(0);
            assertTrue("First result should have high relevance for exact match",
                firstIcon.getRelevanceScore() > 0.8);
            
            // All scores should be between 0 and 1
            for (IconRepository.IconAsset icon : icons) {
                double score = icon.getRelevanceScore();
                assertTrue("Relevance score should be between 0 and 1", 
                    score >= 0.0 && score <= 1.0);
            }
        }
    }
    
    @Test
    public void testIconAttributionInfo() {
        ExecutionResult<IconRepository.IconSearchResult> result = repository.searchIcons("java", 1);
        
        assertTrue("Search should succeed", result.isSuccess());
        
        List<IconRepository.IconAsset> icons = result.getData().get().getIcons();
        
        if (!icons.isEmpty()) {
            IconRepository.IconAsset icon = icons.get(0);
            assertNotNull("Icon should have attribution", icon.getAttribution());
            assertTrue("Devicon attribution should mention Devicon", 
                icon.getAttribution().toLowerCase().contains("devicon"));
            assertTrue("Attribution should mention license", 
                icon.getAttribution().toLowerCase().contains("mit"));
        }
    }
    
    @Test
    public void testSearchPerformance() {
        // Use local-only repository for consistent performance testing
        IconRepository localRepo = IconRepository.localOnly(tempCacheDir);
        
        long startTime = System.currentTimeMillis();
        
        ExecutionResult<IconRepository.IconSearchResult> result = localRepo.searchIcons("java", 10);
        
        long searchTime = System.currentTimeMillis() - startTime;
        
        assertTrue("Search should succeed", result.isSuccess());
        assertTrue("Local search should be reasonably fast (< 500ms)", searchTime < 500);
        
        IconRepository.IconSearchResult searchResult = result.getData().get();
        assertTrue("Search time should be recorded", searchResult.getSearchTimeMs() >= 0);
    }
    
    @Test
    public void testGetAllAvailableIcons() {
        List<IconRepository.IconAsset> allIcons = repository.getAllAvailableIcons();
        
        assertNotNull("Should return icon list", allIcons);
        assertTrue("Should have Devicon icons available", allIcons.size() > 0);
        
        // Verify all icons have required fields
        for (IconRepository.IconAsset icon : allIcons) {
            assertNotNull("Icon should have name", icon.getName());
            assertNotNull("Icon should have source", icon.getSource());
            assertNotNull("Icon should have path", icon.getPath());
            assertNotNull("Icon should have tags", icon.getTags());
            assertNotNull("Icon should have attribution", icon.getAttribution());
        }
    }
    
    @Test
    public void testComputerScienceEducationIcons() {
        // Test icons specifically useful for CS education
        String[] csTerms = {"algorithm", "data", "network", "security", "cloud"};
        
        for (String term : csTerms) {
            ExecutionResult<IconRepository.IconSearchResult> result = repository.searchIcons(term, 3);
            assertTrue("Should find icons for CS term: " + term, result.isSuccess());
            
            List<IconRepository.IconAsset> icons = result.getData().get().getIcons();
            // Note: Some terms might not have exact matches in Devicon, which is fine
            
            if (!icons.isEmpty()) {
                IconRepository.IconAsset firstIcon = icons.get(0);
                assertTrue("Icon for " + term + " should have relevant tags",
                    firstIcon.getTags().stream().anyMatch(tag -> 
                        tag.toLowerCase().contains(term.toLowerCase()) ||
                        term.toLowerCase().contains(tag.toLowerCase())));
            }
        }
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
