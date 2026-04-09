package com.excudo.core.metrics;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Resolves font family names to font file paths using fontconfig (fc-match) on Linux.
 * Falls back to known system font paths if fc-match is unavailable.
 * Results are cached per (family, bold, italic) tuple.
 */
public final class FontResolver {

    private static final Map<String, Path> cache = new ConcurrentHashMap<>();

    private static final Path[] FALLBACK_SEARCH_DIRS = buildFallbackDirs();

    private static Path[] buildFallbackDirs() {
        Path userDir = Path.of(System.getProperty("user.dir", "."));
        return new Path[] {
            userDir.resolve("lib/fonts"),
            Path.of("/usr/share/fonts"),
            Path.of("/usr/local/share/fonts"),
            Path.of(System.getProperty("user.home"), ".fonts"),
            // macOS system fonts
            Path.of("/Library/Fonts"),
            Path.of("/System/Library/Fonts")
        };
    }

    private FontResolver() {}

    /**
     * Resolve a font family name to a TTF/OTF file path.
     *
     * @param family font family name (e.g. "DejaVu Sans", "Calibri")
     * @param bold whether bold weight is requested
     * @param italic whether italic style is requested
     * @return path to the font file, or null if not found
     */
    public static Path resolve(String family, boolean bold, boolean italic) {
        String key = family + "|" + bold + "|" + italic;
        Path cached = cache.get(key);
        if (cached != null) return cached;

        // Check bundled fonts first for consistent cross-platform behavior
        Path resolved = resolveViaFileSearch(family);
        if (resolved == null) {
            resolved = resolveViaFcMatch(family, bold, italic);
        }
        if (resolved == null) {
            resolved = resolveViaFcMatch(family, false, false);
        }
        if (resolved != null) {
            cache.put(key, resolved);
        }
        return resolved;
    }

    /**
     * Resolve using fc-match, which handles fontconfig aliases and fallback chains.
     * When the PPTX says "Calibri" on a Linux system without Calibri, fc-match
     * returns the fontconfig substitute (typically DejaVu Sans).
     */
    private static Path resolveViaFcMatch(String family, boolean bold, boolean italic) {
        try {
            StringBuilder pattern = new StringBuilder(family);
            if (bold) pattern.append(":weight=bold");
            if (italic) pattern.append(":slant=italic");

            ProcessBuilder pb = new ProcessBuilder(
                "fc-match", "--format=%{file}", pattern.toString()
            );
            pb.redirectErrorStream(true);
            Process proc = pb.start();

            String output;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(proc.getInputStream()))) {
                output = reader.readLine();
            }

            int exitCode = proc.waitFor();
            if (exitCode == 0 && output != null && !output.isBlank()) {
                Path fontPath = Path.of(output.trim());
                if (Files.exists(fontPath)) {
                    return fontPath;
                }
            }
        } catch (Exception ignored) {
            // fc-match not available, fall through
        }
        return null;
    }

    /**
     * Search fallback directories for a font file matching the family name.
     * Handles platforms without fc-match (macOS, Windows).
     */
    private static Path resolveViaFileSearch(String family) {
        String normalized = family.replaceAll("\\s+", "");
        for (Path dir : FALLBACK_SEARCH_DIRS) {
            if (!Files.isDirectory(dir)) continue;
            try (var stream = Files.walk(dir, 4)) {
                return stream
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return (name.contains(normalized) || name.contains(family))
                            && (name.endsWith(".ttf") || name.endsWith(".otf"));
                    })
                    .findFirst()
                    .orElse(null);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public static void clearCache() {
        cache.clear();
    }
}
