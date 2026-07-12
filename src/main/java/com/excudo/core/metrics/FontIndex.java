package com.excudo.core.metrics;

import com.excudo.core.utils.ComponentLogger;
import com.excudo.core.utils.Logger;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Build-once font index. On first lookup, walks known font directories and
 * parses each .ttf/.otf file's {@code name} table (family) + OS/2
 * {@code fsSelection} (bold/italic flags) to populate an in-memory map.
 * Subsequent lookups are O(1) map gets.
 *
 * Replaces the prior per-lookup {@code Files.walk} + {@code fc-match}
 * approach in {@link FontResolver}, which re-walked {@code C:\Windows\Fonts}
 * and spawned fc-match twice on every miss. On shape-heavy slides with 80+
 * text runs that produced 10-200s slide-switch latency on Windows; the
 * index amortises it to one 100-500ms scan on startup.
 *
 * The index reads only the {@code name} and {@code OS/2} tables -- a few
 * hundred bytes per file -- so scanning several hundred fonts finishes
 * quickly even on systems where Windows Defender scans each file open.
 * The full {@link TrueTypeFontParser} (which also reads hmtx + cmap) runs
 * lazily and only for fonts the renderer actually uses.
 *
 * Missing fonts return null; callers layer a negative cache on top so
 * repeat misses don't re-query the index.
 */
public final class FontIndex {

    private static final ComponentLogger logger = Logger.getLogger(FontIndex.class);

    // Key format: "familyLower|bold|italic". Values are absolute font paths.
    private static final Map<String, Path> index = new ConcurrentHashMap<>();
    private static volatile boolean built = false;

    private FontIndex() {}

    /**
     * Look up a font by family + style. Returns null if the (family, bold,
     * italic) triple isn't indexed; falls back to the regular-weight entry
     * in the same family (PowerPoint's behaviour: synthesise bold/italic
     * visually from the regular face if the dedicated file is missing).
     * Triggers index build on first call.
     */
    public static Path lookup(String family, boolean bold, boolean italic) {
        if (family == null || family.isBlank()) return null;
        ensureBuilt();
        String norm = normalize(family);
        Path exact = index.get(norm + "|" + bold + "|" + italic);
        if (exact != null) return exact;
        // Fallback: regular face in same family
        return index.get(norm + "|false|false");
    }

    /** Force rebuild. Test-only. */
    public static void rebuild() {
        synchronized (FontIndex.class) {
            index.clear();
            built = false;
            ensureBuilt();
        }
    }

    private static synchronized void ensureBuilt() {
        if (built) return;
        long start = System.currentTimeMillis();
        int scanned = 0;
        int indexed = 0;

        for (Path dir : fontDirs()) {
            if (!Files.isDirectory(dir)) continue;
            try (var stream = Files.walk(dir, 4)) {
                for (var it = stream.iterator(); it.hasNext(); ) {
                    Path p = it.next();
                    String lower = p.getFileName().toString().toLowerCase();
                    if (!lower.endsWith(".ttf") && !lower.endsWith(".otf")) continue;
                    scanned++;
                    try {
                        FontMeta meta = readMeta(p);
                        if (meta == null || meta.family == null || meta.family.isBlank()) continue;
                        String key = normalize(meta.family) + "|" + meta.bold + "|" + meta.italic;
                        if (index.putIfAbsent(key, p) == null) indexed++;
                    } catch (Exception ignored) {
                        // Malformed or unreadable font -- skip, keep scanning
                    }
                }
            } catch (Exception ignored) {
                // Directory walk failed (permissions, transient) -- skip dir
            }
        }
        built = true;
        logger.info("FontIndex built: scanned {} files, indexed {} unique fonts in {}ms",
            scanned, indexed, System.currentTimeMillis() - start);
    }

    private static String normalize(String family) {
        return family.toLowerCase().replaceAll("\\s+", "");
    }

    /**
     * Known font locations across platforms. Package-private so
     * {@link FontResolver} can reuse the same list for its fallback checks.
     */
    static List<Path> fontDirs() {
        List<Path> dirs = new ArrayList<>();
        Path userDir = Path.of(System.getProperty("user.dir", "."));
        dirs.add(userDir.resolve("lib/fonts"));
        dirs.add(Path.of("/usr/share/fonts"));
        dirs.add(Path.of("/usr/local/share/fonts"));
        dirs.add(Path.of(System.getProperty("user.home"), ".fonts"));
        // macOS
        dirs.add(Path.of("/Library/Fonts"));
        dirs.add(Path.of("/System/Library/Fonts"));
        // Windows
        String windir = System.getenv("SystemRoot");
        if (windir == null) windir = System.getenv("windir");
        if (windir != null) {
            dirs.add(Path.of(windir, "Fonts"));
        }
        // Per-user Windows fonts (Win10+ per-user install path)
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null) {
            dirs.add(Path.of(localAppData, "Microsoft", "Windows", "Fonts"));
            // Office cloud fonts. PowerPoint auto-downloads theme fonts it
            // doesn't find installed (Dosis, Inter, Aptos, ...) into this
            // cache and renders/exports with them — so decks that look
            // right in PowerPoint on this host reference fonts that live
            // ONLY here. Indexing it keeps our renders in the same font
            // the ground truth used instead of silently substituting.
            dirs.add(Path.of(localAppData, "Microsoft", "FontCache", "4", "CloudFonts"));
        }
        return dirs;
    }

    // ========== TTF NAME + OS/2 PARSING ==========
    //
    // Minimal parser covering only what the index needs. Unlike
    // TrueTypeFontParser, this doesn't touch hmtx or cmap -- it reads a
    // few hundred bytes per file and stops.

    private static FontMeta readMeta(Path fontPath) throws IOException {
        ByteBuffer buf;
        try (FileChannel ch = FileChannel.open(fontPath, StandardOpenOption.READ)) {
            long size = ch.size();
            if (size <= 0 || size > Integer.MAX_VALUE) return null;
            buf = ByteBuffer.allocate((int) size);
            ch.read(buf);
            buf.flip();
        }

        int sfVersion = buf.getInt(0);
        int tableOffset = 0;

        // TTC (TrueType Collection): index the first embedded font.
        if (sfVersion == 0x74746366) { // 'ttcf'
            int numFonts = buf.getInt(8);
            if (numFonts < 1) return null;
            tableOffset = buf.getInt(12);
        }

        int numTables = buf.getShort(tableOffset + 4) & 0xFFFF;
        int nameOff = -1;
        int os2Off = -1;
        for (int i = 0; i < numTables; i++) {
            int rec = tableOffset + 12 + i * 16;
            int tag = buf.getInt(rec);
            int off = buf.getInt(rec + 8);
            if (tag == 0x6E616D65) nameOff = off;        // 'name'
            else if (tag == 0x4F532F32) os2Off = off;    // 'OS/2'
        }
        if (nameOff < 0) return null;

        boolean bold = false, italic = false;
        if (os2Off >= 0) {
            int fsSelection = buf.getShort(os2Off + 62) & 0xFFFF;
            italic = (fsSelection & 0x0001) != 0;
            bold   = (fsSelection & 0x0020) != 0;
        }

        String family = readNameId(buf, nameOff, 16, 1);
        String subfamily = readNameId(buf, nameOff, 17, 2);

        // Weight variants beyond plain regular/bold (ExtraBold, Light,
        // SemiBold, ...) carry the bare family in nameID 16 with the
        // weight in the subfamily and REGULAR in fsSelection. Indexing
        // them under the bare family made whichever file scanned first
        // win: the Office CloudFonts cache's Dosis-ExtraBold shadowed
        // Dosis-Regular and every "Dosis" run rendered extra-bold.
        // Qualify the family with the subfamily instead — that is also
        // the name decks actually reference for such faces.
        if (family != null && subfamily != null && !subfamily.isBlank()
                && !isPlainSubfamily(subfamily)
                && !normalize(family).endsWith(normalize(subfamily))) {
            family = family + " " + subfamily;
        }
        return new FontMeta(family, bold, italic);
    }

    private static boolean isPlainSubfamily(String subfamily) {
        String s = subfamily.trim().toLowerCase();
        // "Book", "Roman", "Normal", "Plain" are regular-weight aliases
        // (DejaVu Sans ships as subfamily "Book"), not distinct weights.
        return s.isEmpty() || s.equals("regular") || s.equals("bold")
            || s.equals("italic") || s.equals("oblique")
            || s.equals("bold italic") || s.equals("bold oblique")
            || s.equals("book") || s.equals("roman")
            || s.equals("normal") || s.equals("plain");
    }

    /**
     * Read a name-table string, preferring {@code preferredNameId}
     * (typographic family 16 / subfamily 17) over {@code fallbackNameId}
     * (legacy family 1 / subfamily 2), with the usual platform and
     * English-language preference.
     */
    private static String readNameId(ByteBuffer buf, int nameOff,
                                     int preferredNameId, int fallbackNameId) {
        int count = buf.getShort(nameOff + 2) & 0xFFFF;
        int storageOff = buf.getShort(nameOff + 4) & 0xFFFF;

        String best = null;
        int bestPriority = -1;

        for (int i = 0; i < count; i++) {
            int rec = nameOff + 6 + i * 12;
            int platformId = buf.getShort(rec) & 0xFFFF;
            int encodingId = buf.getShort(rec + 2) & 0xFFFF;
            int languageId = buf.getShort(rec + 4) & 0xFFFF;
            int nameId = buf.getShort(rec + 6) & 0xFFFF;
            int length = buf.getShort(rec + 8) & 0xFFFF;
            int offset = buf.getShort(rec + 10) & 0xFFFF;

            if (nameId != preferredNameId && nameId != fallbackNameId) continue;

            int priority = 0;
            if (nameId == preferredNameId) priority += 10;
            if (platformId == 3) priority += 5;       // Microsoft
            else if (platformId == 0) priority += 3;  // Unicode
            else if (platformId == 1) priority += 1;  // Macintosh
            // English preference
            if ((platformId == 3 && languageId == 0x0409)
                || (platformId == 0)
                || (platformId == 1 && languageId == 0)) priority += 2;

            if (priority <= bestPriority) continue;

            String value = readNameString(buf, nameOff + storageOff + offset, length,
                platformId, encodingId);
            if (value != null && !value.isBlank()) {
                best = value;
                bestPriority = priority;
            }
        }
        return best;
    }

    private static String readNameString(ByteBuffer buf, int off, int len,
                                          int platformId, int encodingId) {
        if (len <= 0 || off + len > buf.limit()) return null;
        byte[] bytes = new byte[len];
        for (int i = 0; i < len; i++) bytes[i] = buf.get(off + i);
        // Platform 3 (Microsoft) and platform 0 encoding >= 3 use UTF-16BE.
        // Platform 1 (Mac) with language 0 is MacRoman -- US-ASCII is a safe
        // approximation for English font names.
        if (platformId == 3 || (platformId == 0 && encodingId >= 3)) {
            return new String(bytes, StandardCharsets.UTF_16BE).trim();
        }
        return new String(bytes, StandardCharsets.US_ASCII).trim();
    }

    private record FontMeta(String family, boolean bold, boolean italic) {}
}
