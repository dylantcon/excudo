package com.excudo.core.metrics;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Resolves font family names to font file paths.
 *
 * Primary lookup goes through {@link FontIndex}, which scans known font
 * directories once and keys an in-memory map by TTF name-table family +
 * OS/2 fsSelection bold/italic flags. That turns every resolve into an
 * O(1) map get after the one-time scan.
 *
 * On Linux, if the index misses (e.g. PPTX requests "Calibri" and
 * fontconfig has it aliased to DejaVu via /etc/fonts/), we still consult
 * {@code fc-match} as a fallback. Windows and macOS skip that branch
 * entirely -- {@code fc-match} only ships with fontconfig, which lives
 * on Linux by default.
 *
 * Negative lookups are cached using a {@link #NOT_FOUND} sentinel so a
 * genuinely missing font doesn't re-pay the index + fc-match round trip
 * on every render.
 */
public final class FontResolver {

    // Sentinel Path (empty string) so "miss" entries short-circuit the
    // second call for the same key without returning a valid Path.
    private static final Path NOT_FOUND = Path.of("");
    private static final Map<String, Path> cache = new ConcurrentHashMap<>();

    private static final boolean FC_MATCH_AVAILABLE = detectFcMatch();

    private static boolean detectFcMatch() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win") || os.contains("mac")) return false;
        try {
            ProcessBuilder pb = new ProcessBuilder("fc-match", "--version");
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            return proc.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
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

        Path resolved = FontIndex.lookup(family, bold, italic);
        if (resolved == null && FC_MATCH_AVAILABLE) {
            resolved = resolveViaFcMatch(family, bold, italic);
            if (resolved == null) {
                resolved = resolveViaFcMatch(family, false, false);
            }
        }
        // Cross-platform last-resort fallback: bundled DejaVu Sans. On
        // platforms without fontconfig (macOS, Windows) an unrecognized
        // family like "Calibri" would otherwise return null, breaking
        // every downstream consumer that treats a font path as a required
        // invariant. DejaVu is shipped in lib/fonts/ via `pc.py deps` so
        // this path exists on every dev + CI host.
        if (resolved == null) {
            resolved = FontIndex.lookup("DejaVu Sans", bold, italic);
        }
        cache.put(key, resolved != null ? resolved : NOT_FOUND);
        return resolved;
    }

    /**
     * Ask fontconfig to resolve the family. Returns the substitute path
     * (e.g. "Calibri" -> DejaVu Sans on a Linux box without Calibri).
     * Only called when {@link #FC_MATCH_AVAILABLE} is true.
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
            // fc-match vanished or crashed -- next call will skip via cache
        }
        return null;
    }

    /** Test-only: drop the positive/negative cache. */
    public static void clearCache() {
        cache.clear();
    }
}
