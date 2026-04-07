package com.excudo.core.media;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;
import com.excudo.core.utils.Logger;
import com.excudo.core.utils.ComponentLogger;

/**
 * Provides icon search and download from open-source repositories
 * Supports multiple icon providers with proper attribution
 */
public class OpenSourceIconProvider {

    private static final ComponentLogger logger = Logger.system();

    // Icon providers configuration
    private static final List<IconProvider> PROVIDERS = Arrays.asList(
        new IconProvider(
            "Iconify",
            "https://api.iconify.design/search",
            "https://api.iconify.design/{collection}:{icon}.svg?download=1",
            true, // Free for commercial use
            "Iconify (https://iconify.design) - Open Source Icons"
        ),
        new IconProvider(
            "OpenMoji",
            "https://openmoji.org/data/openmoji.json",
            "https://raw.githubusercontent.com/hfg-gmuend/openmoji/master/color/svg/{icon}.svg",
            true,
            "OpenMoji (https://openmoji.org) - CC BY-SA 4.0"
        ),
        new IconProvider(
            "Feather",
            "https://unpkg.com/feather-icons/dist/icons.json",
            "https://raw.githubusercontent.com/feathericons/feather/master/icons/{icon}.svg",
            true,
            "Feather Icons (https://feathericons.com) - MIT License"
        )
    );
    
    private final String cacheDir;
    private final Map<String, List<IconSearchResult>> searchCache;
    private final ExecutorService executor;
    
    public OpenSourceIconProvider(String cacheDir) {
        this.cacheDir = cacheDir;
        this.searchCache = new ConcurrentHashMap<>();
        this.executor = Executors.newFixedThreadPool(3);
        
        try {
            Files.createDirectories(Paths.get(cacheDir));
        } catch (IOException e) {
            logger.error("Failed to create icon cache directory: " + e.getMessage(), e);
        }
    }

    /**
     * Search for icons across all providers
     */
    public CompletableFuture<List<IconSearchResult>> searchIcons(String keyword) {
        // Check cache first
        if (searchCache.containsKey(keyword)) {
            return CompletableFuture.completedFuture(searchCache.get(keyword));
        }
        
        // Search all providers in parallel
        List<CompletableFuture<List<IconSearchResult>>> searches = new ArrayList<>();
        
        for (IconProvider provider : PROVIDERS) {
            searches.add(searchProvider(provider, keyword));
        }
        
        // Combine results
        return CompletableFuture.allOf(searches.toArray(new CompletableFuture[0]))
            .thenApply(v -> {
                List<IconSearchResult> allResults = new ArrayList<>();
                for (CompletableFuture<List<IconSearchResult>> search : searches) {
                    try {
                        allResults.addAll(search.get());
                    } catch (Exception e) {
                        // Log error but continue with other results
                    }
                }
                
                // Sort by relevance
                allResults.sort((a, b) -> Double.compare(b.relevance, a.relevance));
                
                // Cache results
                searchCache.put(keyword, allResults);
                
                return allResults;
            });
    }
    
    /**
     * Download icon with proper attribution
     */
    public IconDownloadResult downloadIcon(IconSearchResult icon) {
        try {
            // Create unique filename
            String filename = icon.provider + "_" + icon.id + "_" + 
                            System.currentTimeMillis() + ".png";
            Path filePath = Paths.get(cacheDir, filename);
            
            // Download icon
            URL url = new URL(icon.downloadUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "Excudo/1.0");
            
            try (InputStream in = conn.getInputStream()) {
                // If SVG, convert to PNG
                if (icon.downloadUrl.endsWith(".svg")) {
                    // In production, use Batik or similar for SVG->PNG conversion
                    // For now, download as-is
                    Files.copy(in, filePath.resolveSibling(filename.replace(".png", ".svg")));
                    filePath = convertSvgToPng(filePath.resolveSibling(filename.replace(".png", ".svg")));
                } else {
                    Files.copy(in, filePath);
                }
            }
            
            // Create attribution text
            String attribution = String.format(
                "Icon: %s | Source: %s | License: %s | URL: %s",
                icon.name,
                icon.provider,
                icon.attribution,
                icon.sourceUrl
            );
            
            return new IconDownloadResult(
                true,
                filePath.toString(),
                attribution,
                icon
            );
            
        } catch (Exception e) {
            return new IconDownloadResult(
                false,
                null,
                "Failed to download icon: " + e.getMessage(),
                icon
            );
        }
    }
    
    /**
     * Search a specific provider
     */
    private CompletableFuture<List<IconSearchResult>> searchProvider(IconProvider provider, 
                                                                     String keyword) {
        return CompletableFuture.supplyAsync(() -> {
            List<IconSearchResult> results = new ArrayList<>();
            
            try {
                if (provider.name.equals("Iconify")) {
                    results = searchIconify(keyword);
                } else if (provider.name.equals("OpenMoji")) {
                    results = searchOpenMoji(keyword);
                } else if (provider.name.equals("Feather")) {
                    results = searchFeather(keyword);
                }
            } catch (Exception e) {
                // Log error
            }
            
            return results;
        }, executor);
    }
    
    /**
     * Search Iconify API
     */
    private List<IconSearchResult> searchIconify(String keyword) throws Exception {
        List<IconSearchResult> results = new ArrayList<>();
        
        String searchUrl = "https://api.iconify.design/search?query=" + 
                          URLEncoder.encode(keyword, "UTF-8") + "&limit=10";
        
        URL url = new URL(searchUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream()))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            
            // Parse JSON response
            JSONObject json = new JSONObject(response.toString());
            JSONArray icons = json.getJSONArray("icons");
            
            for (int i = 0; i < icons.length(); i++) {
                String iconId = icons.getString(i);
                String[] parts = iconId.split(":");
                if (parts.length == 2) {
                    String collection = parts[0];
                    String name = parts[1];
                    
                    results.add(new IconSearchResult(
                        iconId,
                        name,
                        "Iconify",
                        String.format("https://api.iconify.design/%s:%s.svg?download=1", 
                                     collection, name),
                        "https://iconify.design/icon-sets/" + collection + "/" + name,
                        PROVIDERS.get(0).attribution,
                        calculateRelevance(keyword, name),
                        true
                    ));
                }
            }
        }
        
        return results;
    }
    
    /**
     * Search OpenMoji collection
     */
    private List<IconSearchResult> searchOpenMoji(String keyword) throws Exception {
        List<IconSearchResult> results = new ArrayList<>();
        
        // For OpenMoji, we'd need to download and cache their JSON catalog
        // Then search through it locally
        // This is a simplified implementation
        
        Map<String, String> commonEmojis = Map.of(
            "smile", "1F600",
            "heart", "2764",
            "star", "2B50",
            "check", "2705",
            "phone", "1F4F1",
            "email", "1F4E7",
            "time", "1F550",
            "money", "1F4B5"
        );
        
        String emojiCode = commonEmojis.get(keyword.toLowerCase());
        if (emojiCode != null) {
            results.add(new IconSearchResult(
                emojiCode,
                keyword + " emoji",
                "OpenMoji",
                "https://raw.githubusercontent.com/hfg-gmuend/openmoji/master/color/svg/" + 
                emojiCode + ".svg",
                "https://openmoji.org/library/#search=" + keyword,
                PROVIDERS.get(1).attribution,
                1.0,
                true
            ));
        }
        
        return results;
    }
    
    /**
     * Search Feather icons
     */
    private List<IconSearchResult> searchFeather(String keyword) throws Exception {
        List<IconSearchResult> results = new ArrayList<>();
        
        // Feather has a limited set of icons
        Map<String, String> featherIcons = Map.of(
            "email", "mail",
            "phone", "phone",
            "location", "map-pin",
            "time", "clock",
            "money", "dollar-sign",
            "chart", "bar-chart",
            "team", "users",
            "idea", "zap"
        );
        
        String iconName = featherIcons.get(keyword.toLowerCase());
        if (iconName != null) {
            results.add(new IconSearchResult(
                iconName,
                iconName,
                "Feather",
                "https://raw.githubusercontent.com/feathericons/feather/master/icons/" + 
                iconName + ".svg",
                "https://feathericons.com/?query=" + iconName,
                PROVIDERS.get(2).attribution,
                1.0,
                true
            ));
        }
        
        return results;
    }
    
    /**
     * Convert SVG to PNG (simplified - in production use Batik)
     */
    private Path convertSvgToPng(Path svgPath) {
        // In production, use Apache Batik or similar
        // For now, return the SVG path with .png extension
        String pngPath = svgPath.toString().replace(".svg", ".png");
        
        // Placeholder: copy file
        try {
            Files.copy(svgPath, Paths.get(pngPath), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            logger.error("Failed to convert SVG to PNG: " + e.getMessage(), e);
        }

        return Paths.get(pngPath);
    }
    
    /**
     * Calculate relevance score
     */
    private double calculateRelevance(String keyword, String iconName) {
        keyword = keyword.toLowerCase();
        iconName = iconName.toLowerCase();
        
        if (iconName.equals(keyword)) return 1.0;
        if (iconName.contains(keyword)) return 0.8;
        if (keyword.contains(iconName)) return 0.7;
        
        // Calculate similarity
        int commonChars = 0;
        for (char c : keyword.toCharArray()) {
            if (iconName.indexOf(c) >= 0) commonChars++;
        }
        
        return commonChars / (double) Math.max(keyword.length(), iconName.length());
    }
    
    /**
     * Shutdown executor service
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }
}

class IconProvider {
    final String name;
    final String searchApi;
    final String downloadUrlTemplate;
    final boolean freeForCommercial;
    final String attribution;
    
    IconProvider(String name, String searchApi, String downloadUrlTemplate,
                 boolean freeForCommercial, String attribution) {
        this.name = name;
        this.searchApi = searchApi;
        this.downloadUrlTemplate = downloadUrlTemplate;
        this.freeForCommercial = freeForCommercial;
        this.attribution = attribution;
    }
}

class IconSearchResult {
    final String id;
    final String name;
    final String provider;
    final String downloadUrl;
    final String sourceUrl;
    final String attribution;
    final double relevance;
    final boolean freeForCommercial;
    
    IconSearchResult(String id, String name, String provider, String downloadUrl,
                     String sourceUrl, String attribution, double relevance,
                     boolean freeForCommercial) {
        this.id = id;
        this.name = name;
        this.provider = provider;
        this.downloadUrl = downloadUrl;
        this.sourceUrl = sourceUrl;
        this.attribution = attribution;
        this.relevance = relevance;
        this.freeForCommercial = freeForCommercial;
    }
}

class IconDownloadResult {
    final boolean success;
    final String localPath;
    final String attribution;
    final IconSearchResult icon;
    
    IconDownloadResult(boolean success, String localPath, String attribution,
                       IconSearchResult icon) {
        this.success = success;
        this.localPath = localPath;
        this.attribution = attribution;
        this.icon = icon;
    }
}