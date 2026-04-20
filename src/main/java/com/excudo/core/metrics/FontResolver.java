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

    // Sentinel for "we looked and couldn't find this font" so the next
    // resolve for the same key short-circuits instead of re-walking
    // C:\Windows\Fonts and re-spawning fc-match. Path.of("") is legal but
    // not equal to any real font path, so `== NOT_FOUND` identity checks
    // suffice.
    private static final Path NOT_FOUND = Path.of("");
    private static final Map<String, Path> cache = new ConcurrentHashMap<>();

    // fc-match only exists on Linux (and sometimes macOS via Homebrew).
    // On Windows every ProcessBuilder.start() attempt pays CreateProcess +
    // Defender cost before failing -- we hit that path twice per miss, so
    // skipping the whole branch on Windows removes the worst offender.
    private static final boolean FC_MATCH_AVAILABLE = detectFcMatch();

    private static boolean detectFcMatch() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) return false;
        try {
            ProcessBuilder pb = new ProcessBuilder("fc-match", "--version");
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            return proc.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static final Path[] FALLBACK_SEARCH_DIRS = buildFallbackDirs();

    private static Path[] buildFallbackDirs() {
        Path userDir = Path.of(System.getProperty("user.dir", "."));
        java.util.List<Path> dirs = new java.util.ArrayList<>();
        dirs.add(userDir.resolve("lib/fonts"));
        dirs.add(Path.of("/usr/share/fonts"));
        dirs.add(Path.of("/usr/local/share/fonts"));
        dirs.add(Path.of(System.getProperty("user.home"), ".fonts"));
        // macOS system fonts
        dirs.add(Path.of("/Library/Fonts"));
        dirs.add(Path.of("/System/Library/Fonts"));
        // Windows system fonts
        String windir = System.getenv("SystemRoot");
        if (windir == null) windir = System.getenv("windir");
        if (windir != null) {
            dirs.add(Path.of(windir, "Fonts"));
        } else {
            dirs.add(Path.of("C:\\Windows\\Fonts"));
        }
        return dirs.toArray(new Path[0]);
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
        if (cached != null) return cached == NOT_FOUND ? null : cached;

        // Check bundled fonts first for consistent cross-platform behavior
        Path resolved = resolveViaFileSearch(family);
        if (resolved == null && FC_MATCH_AVAILABLE) {
            resolved = resolveViaFcMatch(family, bold, italic);
            if (resolved == null) {
                resolved = resolveViaFcMatch(family, false, false);
            }
        }
        // Always cache -- a miss cached as NOT_FOUND saves a full
        // Files.walk on every subsequent render of the same text run.
        cache.put(key, resolved != null ? resolved : NOT_FOUND);
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
        String normalized = family.replaceAll("\\s+", "").toLowerCase();
        String familyLower = family.toLowerCase();
        for (Path dir : FALLBACK_SEARCH_DIRS) {
            if (!Files.isDirectory(dir)) continue;
            try (var stream = Files.walk(dir, 4)) {
                Path match = stream
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase();
                        return (name.contains(normalized) || name.contains(familyLower))
                            && (name.endsWith(".ttf") || name.endsWith(".otf"));
                    })
                    .findFirst()
                    .orElse(null);
                if (match != null) return match;
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public static void clearCache() {
        cache.clear();
    }
}
