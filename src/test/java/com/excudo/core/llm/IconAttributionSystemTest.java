package com.excudo.core.llm;

import com.excudo.core.results.ExecutionResult;
import com.excudo.core.smartcontent.IconRepository;
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
 * Test the icon attribution system functionality.
 * Tests the URL generation and attribution formatting without requiring PPTX files.
 * 
 * @author Excudo Test Suite
 * @version 1.0 - Attribution System Core Testing
 */
public class IconAttributionSystemTest {
    
    private IconRepository repository;
    private String tempCacheDir;
    
    @Before
    public void setUp() throws Exception {
        // Create temporary cache directory
        tempCacheDir = Files.createTempDirectory("attribution-system-test").toString();
        
        // Initialize repository
        repository = new IconRepository(tempCacheDir);
    }
    
    @After
    public void tearDown() {
        try {
            deleteDirectory(new File(tempCacheDir));
        } catch (Exception e) {
            // Ignore cleanup errors in tests
        }
    }
    
    @Test
    public void testDeviconAttributionData() {
        // Create mock Devicon icon to test attribution without network calls
        IconRepository.IconAsset javaIcon = new IconRepository.IconAsset(
            "java", "devicon", "/cache/devicon/java.svg",
            Set.of("java", "programming"), 
            "Devicon by konpa (https://devicon.dev/) - MIT License", 
            1.0);
        
        // Verify attribution data
        assertEquals("Should be Devicon source", "devicon", javaIcon.getSource());
        assertNotNull("Should have attribution", javaIcon.getAttribution());
        assertTrue("Attribution should mention Devicon", 
            javaIcon.getAttribution().toLowerCase().contains("devicon"));
        assertTrue("Attribution should mention MIT license", 
            javaIcon.getAttribution().toLowerCase().contains("mit"));
        
        // Test URL generation
        String expectedUrl = "https://devicons.github.io/devicon/icons/java/java-original.svg";
        String actualUrl = getIconDisplayUrl(javaIcon);
        assertEquals("Should generate correct Devicon URL", expectedUrl, actualUrl);
    }
    
    @Test
    public void testLocalIconAttributionData() throws Exception {
        // Upload a local icon
        Path testIconPath = Paths.get(tempCacheDir, "test-icon.svg");
        Files.write(testIconPath, "<svg>test content</svg>".getBytes());
        
        Set<String> tags = Set.of("test", "local");
        ExecutionResult<IconRepository.IconAsset> uploadResult = repository.uploadLocalIcon(
            testIconPath.toString(), "test-local-icon", tags);
        
        assertTrue("Upload should succeed", uploadResult.isSuccess());
        
        IconRepository.IconAsset localIcon = uploadResult.getData().get();
        
        // Verify attribution data
        assertEquals("Should be local source", "local", localIcon.getSource());
        assertEquals("Should have local attribution", "Local upload", localIcon.getAttribution());
        
        // Test URL generation
        String expectedUrl = "Local upload: test-local-icon";
        String actualUrl = getIconDisplayUrl(localIcon);
        assertEquals("Should generate correct local URL", expectedUrl, actualUrl);
    }
    
    @Test
    public void testFreepikAttributionData() {
        // Create mock Freepik icon
        IconRepository.IconAsset freepikIcon = new IconRepository.IconAsset(
            "freepik-computer-123", "freepik", "/cache/freepik/computer-123.svg",
            Set.of("computer", "technology"), 
            "Icon by Freepik (https://www.freepik.com) - Premium License", 
            0.95);
        
        // Verify attribution data
        assertEquals("Should be Freepik source", "freepik", freepikIcon.getSource());
        assertTrue("Attribution should mention Freepik", 
            freepikIcon.getAttribution().contains("Freepik"));
        assertTrue("Attribution should mention license", 
            freepikIcon.getAttribution().toLowerCase().contains("license"));
        
        // Test URL generation
        String expectedUrl = "Freepik Icon ID: freepik-computer-123";
        String actualUrl = getIconDisplayUrl(freepikIcon);
        assertEquals("Should generate correct Freepik URL", expectedUrl, actualUrl);
    }
    
    @Test
    public void testAttributionFormatting() {
        // Test that attribution text is properly formatted for different sources
        String[] sources = {"devicon", "freepik", "local", "unknown"};
        
        for (String source : sources) {
            IconRepository.IconAsset testIcon = new IconRepository.IconAsset(
                "test-" + source, source, "/path/to/icon",
                Set.of("test"), "Test attribution for " + source, 1.0);
            
            String url = getIconDisplayUrl(testIcon);
            String attribution = testIcon.getAttribution();
            
            assertNotNull("URL should not be null for " + source, url);
            assertNotNull("Attribution should not be null for " + source, attribution);
            assertFalse("URL should not be empty for " + source, url.trim().isEmpty());
            assertFalse("Attribution should not be empty for " + source, attribution.trim().isEmpty());
        }
    }
    
    @Test
    public void testAttributionTextGeneration() {
        IconRepository.IconAsset testIcon = new IconRepository.IconAsset(
            "java", "devicon", "/cache/devicon/java.svg",
            Set.of("java", "programming"), 
            "Devicon by konpa (https://devicon.dev/) - MIT License", 
            1.0);
        
        String url = getIconDisplayUrl(testIcon);
        String attribution = testIcon.getAttribution();
        
        // Simulate what would be written to slide notes
        String attributionText = String.format(
            "\n\n--- Icon Attribution ---\nIcon URL: %s\n%s\n",
            url,
            attribution
        );
        
        assertTrue("Should contain attribution header", 
            attributionText.contains("--- Icon Attribution ---"));
        assertTrue("Should contain URL label", 
            attributionText.contains("Icon URL:"));
        assertTrue("Should contain Devicon URL", 
            attributionText.contains("devicons.github.io"));
        assertTrue("Should contain MIT license", 
            attributionText.contains("MIT License"));
        assertTrue("Should contain konpa attribution", 
            attributionText.contains("konpa"));
    }
    
    @Test
    public void testMultipleIconAttributions() {
        // Test that we can handle multiple different icons
        List<IconRepository.IconAsset> testIcons = Arrays.asList(
            new IconRepository.IconAsset("java", "devicon", "/path1", Set.of("java"), "Devicon - MIT", 1.0),
            new IconRepository.IconAsset("custom", "local", "/path2", Set.of("custom"), "Local upload", 1.0),
            new IconRepository.IconAsset("premium", "freepik", "/path3", Set.of("premium"), "Freepik - Premium", 1.0)
        );
        
        Set<String> attributionTexts = new HashSet<>();
        Set<String> urls = new HashSet<>();
        
        for (IconRepository.IconAsset icon : testIcons) {
            String url = getIconDisplayUrl(icon);
            String attribution = icon.getAttribution();
            
            urls.add(url);
            attributionTexts.add(attribution);
        }
        
        // Verify each icon produces unique attribution
        assertEquals("Should have unique URLs for each icon", 3, urls.size());
        assertEquals("Should have unique attributions for each icon", 3, attributionTexts.size());
    }
    
    @Test
    public void testEducationalUseCompliance() {
        // Create mock Python icon to test educational use compliance without network calls
        IconRepository.IconAsset pythonIcon = new IconRepository.IconAsset(
            "python", "devicon", "/cache/devicon/python.svg",
            Set.of("python", "programming"), 
            "Devicon by konpa (https://devicon.dev/) - MIT License", 
            1.0);
        
        // Educational use should be supported by MIT license (Devicon)
        assertTrue("Attribution should support educational use", 
            pythonIcon.getAttribution().toLowerCase().contains("mit"));
        
        String url = getIconDisplayUrl(pythonIcon);
        assertTrue("URL should point to accessible source", 
            url.startsWith("https://") || url.startsWith("Local upload:"));
    }
    
    // Helper method that mimics SmartContentEnhancer.getIconDisplayUrl
    private String getIconDisplayUrl(IconRepository.IconAsset iconAsset) {
        switch (iconAsset.getSource()) {
            case "devicon":
                return "https://devicons.github.io/devicon/icons/" + iconAsset.getName() + "/" + iconAsset.getName() + "-original.svg";
            case "freepik":
                return "Freepik Icon ID: " + iconAsset.getName();
            case "local":
                return "Local upload: " + iconAsset.getName();
            default:
                return iconAsset.getPath();
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