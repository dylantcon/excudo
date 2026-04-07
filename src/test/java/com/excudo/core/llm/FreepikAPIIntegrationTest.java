package com.excudo.core.llm;

import com.excudo.core.results.ExecutionResult;
import com.excudo.core.smartcontent.IconRepository;
import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import org.junit.Assume;
import org.junit.BeforeClass;
import static org.junit.Assert.*;
import java.util.*;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Test the Freepik API integration for icon search and download.
 * Tests real API calls and attribution metadata extraction.
 *
 * Automatically skipped when:
 *   - FREEPIK_API_KEY is not set
 *   - Freepik API fails the health check (rate-limited, down, or unreachable)
 */
public class FreepikAPIIntegrationTest {

    private static boolean apiAvailable = false;

    private IconRepository repository;
    private String tempCacheDir;

    /**
     * One-time health check before any tests run.
     * Hits the Freepik API with a minimal request; if it returns anything
     * other than 200, the entire class is skipped via Assume.
     */
    @BeforeClass
    public static void checkApiHealth() {
        String apiKey = com.excudo.core.utils.EnvLoader.get("FREEPIK_API_KEY");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            System.out.println("FREEPIK_API_KEY not set -- skipping FreepikAPIIntegrationTest");
            return;
        }

        try {
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.freepik.com/v1/icons?term=test&per_page=1"))
                .header("x-freepik-api-key", apiKey)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                apiAvailable = true;
                System.out.println("Freepik API health check passed (HTTP 200)");
            } else {
                System.out.println("Freepik API health check failed (HTTP " + response.statusCode()
                    + ") -- skipping FreepikAPIIntegrationTest");
            }
        } catch (Exception e) {
            System.out.println("Freepik API unreachable (" + e.getMessage()
                + ") -- skipping FreepikAPIIntegrationTest");
        }
    }

    @Before
    public void setUp() throws Exception {
        Assume.assumeTrue("Freepik API not available", apiAvailable);
        tempCacheDir = Files.createTempDirectory("freepik-api-test").toString();
        repository = IconRepository.fromEnvironment(tempCacheDir);
    }

    @After
    public void tearDown() {
        try {
            deleteDirectory(new File(tempCacheDir));
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }

    @Test
    public void testFreepikAPIConfiguration() {
        assertTrue("Repository should have Freepik API enabled", repository.isFreepikAPIEnabled());
        assertTrue("Repository should have API key configured", repository.hasFreepikApiKey());
    }

    @Test
    public void testFreepikAPISearchBasic() {
        ExecutionResult<IconRepository.IconSearchResult> result = repository.searchIcons("computer", 5);

        assertTrue("Search should succeed", result.isSuccess());
        assertNotNull("Should return search result", result.getData().get());

        IconRepository.IconSearchResult searchResult = result.getData().get();
        assertEquals("Should use correct query", "computer", searchResult.getSearchQuery());
        assertTrue("Search should be reasonably fast", searchResult.getSearchTimeMs() < 10000);

        List<IconRepository.IconAsset> icons = searchResult.getIcons();

        for (IconRepository.IconAsset icon : icons) {
            if ("freepik".equals(icon.getSource())) {
                assertTrue("Freepik icon should have name", icon.getName() != null && !icon.getName().isEmpty());
                assertTrue("Freepik icon should have attribution",
                    icon.getAttribution().toLowerCase().contains("freepik"));
                assertTrue("Freepik icon should mention license",
                    icon.getAttribution().toLowerCase().contains("license"));
                assertTrue("Freepik icon should have relevance score",
                    icon.getRelevanceScore() > 0 && icon.getRelevanceScore() <= 1.0);
            }
        }
    }

    @Test
    public void testFreepikAPISearchEducationalTerms() {
        String[] eduTerms = {"education", "learning", "book", "student", "programming"};

        for (String term : eduTerms) {
            ExecutionResult<IconRepository.IconSearchResult> result = repository.searchIcons(term, 3);
            assertTrue("Search should succeed for term: " + term, result.isSuccess());

            List<IconRepository.IconAsset> icons = result.getData().get().getIcons();
            icons.stream()
                .filter(icon -> "freepik".equals(icon.getSource()))
                .forEach(icon -> {
                    assertNotNull("Freepik icon should have attribution", icon.getAttribution());
                    assertTrue("Attribution should mention Freepik",
                        icon.getAttribution().contains("Freepik"));
                });
        }
    }

    @Test
    public void testFreepikAttributionMetadata() {
        ExecutionResult<IconRepository.IconSearchResult> result = repository.searchIcons("technology", 1);
        assertTrue("Search should succeed", result.isSuccess());

        List<IconRepository.IconAsset> icons = result.getData().get().getIcons();

        Optional<IconRepository.IconAsset> freepikIcon = icons.stream()
            .filter(icon -> "freepik".equals(icon.getSource()))
            .findFirst();

        if (freepikIcon.isPresent()) {
            IconRepository.IconAsset icon = freepikIcon.get();
            String attribution = icon.getAttribution();
            assertTrue("Attribution should mention Freepik", attribution.contains("Freepik"));
            assertTrue("Attribution should mention website", attribution.contains("freepik.com"));
            assertTrue("Attribution should mention license",
                attribution.toLowerCase().contains("license"));
            assertTrue("Icon name should contain freepik prefix", icon.getName().startsWith("freepik-"));
            assertNotNull("Icon should have path", icon.getPath());
            assertTrue("Path should point to freepik cache directory",
                icon.getPath().contains("freepik"));
        }
    }

    @Test
    public void testFreepikAPIErrorHandling() {
        // Test with local-only repository (no API) -- doesn't need live API
        IconRepository localOnlyRepo = IconRepository.localOnly(tempCacheDir);
        ExecutionResult<IconRepository.IconSearchResult> result = localOnlyRepo.searchIcons("test", 1);

        assertTrue("Search should succeed with local-only repository", result.isSuccess());
        long freepikCount = result.getData().get().getIcons().stream()
            .filter(icon -> "freepik".equals(icon.getSource())).count();
        assertEquals("Should not return Freepik icons without API enabled", 0, freepikCount);
    }

    @Test
    public void testFreepikRelevanceScoring() {
        ExecutionResult<IconRepository.IconSearchResult> result = repository.searchIcons("database", 5);
        assertTrue("Search should succeed", result.isSuccess());

        List<IconRepository.IconAsset> icons = result.getData().get().getIcons();

        icons.stream()
            .filter(icon -> "freepik".equals(icon.getSource()))
            .forEach(icon -> {
                double score = icon.getRelevanceScore();
                assertTrue("Relevance score should be between 0 and 1", score >= 0.0 && score <= 1.0);
                assertTrue("Relevance score should be reasonable for search", score >= 0.1);
            });

        // Verify icons are sorted by relevance
        for (int i = 1; i < icons.size(); i++) {
            assertTrue("Icons should be sorted by relevance score",
                icons.get(i - 1).getRelevanceScore() >= icons.get(i).getRelevanceScore());
        }
    }

    @Test
    public void testFreepikDownloadURLGeneration() {
        IconRepository.IconAsset freepikIcon = new IconRepository.IconAsset(
            "freepik-54321", "freepik", "/cache/freepik/icon-54321.svg",
            Set.of("test", "icon"), "Icon by Freepik - Premium License", 0.8);

        ExecutionResult<IconRepository.IconAsset> downloadResult = repository.downloadIcon(freepikIcon);
        assertNotNull("Download result should not be null", downloadResult);
    }

    @Test
    public void testEducationalUseCaseCompliance() {
        String[] educationalQueries = {"lesson", "curriculum", "teaching", "classroom"};

        for (String query : educationalQueries) {
            ExecutionResult<IconRepository.IconSearchResult> result = repository.searchIcons(query, 2);

            if (result.isSuccess()) {
                result.getData().get().getIcons().stream()
                    .filter(icon -> "freepik".equals(icon.getSource()))
                    .forEach(icon -> {
                        assertNotNull("Educational icon should have attribution", icon.getAttribution());
                        String attribution = icon.getAttribution();
                        assertTrue("Attribution should be clear for educational use",
                            attribution.contains("Freepik") && attribution.contains("License"));
                    });
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
