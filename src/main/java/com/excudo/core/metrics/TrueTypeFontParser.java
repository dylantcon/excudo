package com.excudo.core.metrics;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Binary TrueType/OpenType font parser.
 * Reads the tables PowerPoint uses for text layout: head, OS/2, hhea, hmtx, cmap.
 * Results are cached per file path.
 */
public final class TrueTypeFontParser {

    private static final Map<String, FontData> cache = new ConcurrentHashMap<>();

    private TrueTypeFontParser() {}

    /**
     * Parse a TTF/OTF font file and return its metric data.
     * Results are cached by absolute path.
     *
     * @param fontPath path to the .ttf or .otf file
     * @return parsed font data
     * @throws IOException if the file cannot be read or is not a valid font
     */
    public static FontData parse(Path fontPath) throws IOException {
        String key = fontPath.toAbsolutePath().toString();
        FontData cached = cache.get(key);
        if (cached != null) return cached;

        FontData data = parseInternal(fontPath);
        cache.put(key, data);
        return data;
    }

    public static void clearCache() {
        cache.clear();
    }

    private static FontData parseInternal(Path fontPath) throws IOException {
        ByteBuffer buf;
        try (FileChannel ch = FileChannel.open(fontPath, StandardOpenOption.READ)) {
            buf = ByteBuffer.allocate((int) ch.size());
            ch.read(buf);
            buf.flip();
        }

        // Offset table
        int sfVersion = buf.getInt(0);
        boolean isTTC = sfVersion == 0x74746366; // "ttcf"
        int tableOffset = 0;

        if (isTTC) {
            // TrueType Collection -- use the first font
            int numFonts = buf.getInt(8);
            if (numFonts < 1) throw new IOException("Empty TTC file");
            tableOffset = buf.getInt(12); // offset to first font's offset table
        }

        int numTables = buf.getShort(tableOffset + 4) & 0xFFFF;

        // Build table directory
        Map<String, int[]> tables = new HashMap<>();
        for (int i = 0; i < numTables; i++) {
            int recordOffset = tableOffset + 12 + i * 16;
            String tag = readTag(buf, recordOffset);
            int offset = buf.getInt(recordOffset + 8);
            int length = buf.getInt(recordOffset + 12);
            tables.put(tag, new int[]{offset, length});
        }

        // head table: unitsPerEm
        int[] headLoc = requireTable(tables, "head");
        int unitsPerEm = buf.getShort(headLoc[0] + 18) & 0xFFFF;

        // OS/2 table: win metrics, typo metrics, fsSelection
        int[] os2Loc = requireTable(tables, "OS/2");
        int os2Off = os2Loc[0];
        int os2Version = buf.getShort(os2Off) & 0xFFFF;

        // sTypoAscender, sTypoDescender, sTypoLineGap at offsets 68, 70, 72
        int typoAscender = buf.getShort(os2Off + 68);
        int typoDescender = buf.getShort(os2Off + 70);
        int typoLineGap = buf.getShort(os2Off + 72);

        // fsSelection at offset 62
        int fsSelection = buf.getShort(os2Off + 62) & 0xFFFF;
        boolean useTypoMetrics = (fsSelection & 0x0080) != 0;

        // usWinAscent, usWinDescent at offsets 74, 76
        int winAscent = buf.getShort(os2Off + 74) & 0xFFFF;
        int winDescent = buf.getShort(os2Off + 76) & 0xFFFF;

        // hhea table: numberOfHMetrics
        int[] hheaLoc = requireTable(tables, "hhea");
        int numberOfHMetrics = buf.getShort(hheaLoc[0] + 34) & 0xFFFF;

        // hmtx table: advance widths
        int[] hmtxLoc = requireTable(tables, "hmtx");
        int hmtxOff = hmtxLoc[0];
        int[] advanceWidths = new int[numberOfHMetrics];
        for (int i = 0; i < numberOfHMetrics; i++) {
            advanceWidths[i] = buf.getShort(hmtxOff + i * 4) & 0xFFFF;
        }

        // cmap table: character to glyph mapping
        int[] cmapLoc = requireTable(tables, "cmap");
        Map<Integer, Integer> cmapLookup = parseCmap(buf, cmapLoc[0]);

        // Extract font family name from name table if available
        String fontFamily = extractFontFamily(buf, tables);

        return new FontData(fontFamily, unitsPerEm, winAscent, winDescent, 0,
                useTypoMetrics, typoAscender, typoDescender, typoLineGap,
                advanceWidths, cmapLookup);
    }

    private static Map<Integer, Integer> parseCmap(ByteBuffer buf, int cmapOffset) {
        int numSubtables = buf.getShort(cmapOffset + 2) & 0xFFFF;

        // Prefer platform 3 (Windows), encoding 1 (Unicode BMP) or encoding 10 (full Unicode)
        // Fall back to platform 0 (Unicode)
        int bestSubtableOffset = -1;
        int bestPriority = -1;

        for (int i = 0; i < numSubtables; i++) {
            int recOff = cmapOffset + 4 + i * 8;
            int platformId = buf.getShort(recOff) & 0xFFFF;
            int encodingId = buf.getShort(recOff + 2) & 0xFFFF;
            int subtableOffset = buf.getInt(recOff + 4);

            int priority = -1;
            if (platformId == 3 && encodingId == 1) priority = 10;
            else if (platformId == 3 && encodingId == 10) priority = 9;
            else if (platformId == 0) priority = 5;

            if (priority > bestPriority) {
                bestPriority = priority;
                bestSubtableOffset = cmapOffset + subtableOffset;
            }
        }

        if (bestSubtableOffset < 0) return Map.of();

        int format = buf.getShort(bestSubtableOffset) & 0xFFFF;
        if (format == 4) {
            return parseCmapFormat4(buf, bestSubtableOffset);
        } else if (format == 12) {
            return parseCmapFormat12(buf, bestSubtableOffset);
        }

        return Map.of();
    }

    private static Map<Integer, Integer> parseCmapFormat4(ByteBuffer buf, int offset) {
        Map<Integer, Integer> result = new HashMap<>();
        int segCount = (buf.getShort(offset + 6) & 0xFFFF) / 2;

        int endCodeOff = offset + 14;
        int startCodeOff = endCodeOff + segCount * 2 + 2; // +2 for reservedPad
        int idDeltaOff = startCodeOff + segCount * 2;
        int idRangeOffsetOff = idDeltaOff + segCount * 2;

        for (int i = 0; i < segCount; i++) {
            int endCode = buf.getShort(endCodeOff + i * 2) & 0xFFFF;
            int startCode = buf.getShort(startCodeOff + i * 2) & 0xFFFF;
            int idDelta = buf.getShort(idDeltaOff + i * 2);
            int idRangeOffset = buf.getShort(idRangeOffsetOff + i * 2) & 0xFFFF;

            if (startCode == 0xFFFF) break;

            for (int c = startCode; c <= endCode; c++) {
                int glyphId;
                if (idRangeOffset == 0) {
                    glyphId = (c + idDelta) & 0xFFFF;
                } else {
                    int glyphIdArrayOffset = idRangeOffsetOff + i * 2 + idRangeOffset
                            + (c - startCode) * 2;
                    glyphId = buf.getShort(glyphIdArrayOffset) & 0xFFFF;
                    if (glyphId != 0) {
                        glyphId = (glyphId + idDelta) & 0xFFFF;
                    }
                }
                if (glyphId != 0) {
                    result.put(c, glyphId);
                }
            }
        }
        return result;
    }

    private static Map<Integer, Integer> parseCmapFormat12(ByteBuffer buf, int offset) {
        Map<Integer, Integer> result = new HashMap<>();
        int numGroups = buf.getInt(offset + 12);
        int groupOff = offset + 16;
        for (int i = 0; i < numGroups; i++) {
            int startCharCode = buf.getInt(groupOff + i * 12);
            int endCharCode = buf.getInt(groupOff + i * 12 + 4);
            int startGlyphId = buf.getInt(groupOff + i * 12 + 8);
            for (int c = startCharCode; c <= endCharCode; c++) {
                result.put(c, startGlyphId + (c - startCharCode));
            }
        }
        return result;
    }

    private static String extractFontFamily(ByteBuffer buf, Map<String, int[]> tables) {
        int[] nameLoc = tables.get("name");
        if (nameLoc == null) return "Unknown";

        int nameOff = nameLoc[0];
        int count = buf.getShort(nameOff + 2) & 0xFFFF;
        int stringOffset = nameOff + (buf.getShort(nameOff + 4) & 0xFFFF);

        for (int i = 0; i < count; i++) {
            int recOff = nameOff + 6 + i * 12;
            int platformId = buf.getShort(recOff) & 0xFFFF;
            int nameId = buf.getShort(recOff + 6) & 0xFFFF;
            int length = buf.getShort(recOff + 8) & 0xFFFF;
            int offset = buf.getShort(recOff + 10) & 0xFFFF;

            // nameID 1 = Font Family; prefer platform 1 (Mac) for ASCII
            if (nameId == 1 && platformId == 1 && length > 0) {
                byte[] bytes = new byte[length];
                int pos = stringOffset + offset;
                for (int j = 0; j < length; j++) {
                    bytes[j] = buf.get(pos + j);
                }
                return new String(bytes, java.nio.charset.StandardCharsets.US_ASCII).trim();
            }
        }

        // Fallback: try platform 3 (Windows, UTF-16BE)
        for (int i = 0; i < count; i++) {
            int recOff = nameOff + 6 + i * 12;
            int platformId = buf.getShort(recOff) & 0xFFFF;
            int nameId = buf.getShort(recOff + 6) & 0xFFFF;
            int length = buf.getShort(recOff + 8) & 0xFFFF;
            int strOff = buf.getShort(recOff + 10) & 0xFFFF;

            if (nameId == 1 && platformId == 3 && length > 0) {
                byte[] bytes = new byte[length];
                int pos = stringOffset + strOff;
                for (int j = 0; j < length; j++) {
                    bytes[j] = buf.get(pos + j);
                }
                return new String(bytes, java.nio.charset.StandardCharsets.UTF_16BE).trim();
            }
        }

        return "Unknown";
    }

    private static String readTag(ByteBuffer buf, int offset) {
        return "" + (char) buf.get(offset) + (char) buf.get(offset + 1)
                + (char) buf.get(offset + 2) + (char) buf.get(offset + 3);
    }

    private static int[] requireTable(Map<String, int[]> tables, String tag) throws IOException {
        int[] loc = tables.get(tag);
        if (loc == null) throw new IOException("Missing required table: " + tag);
        return loc;
    }
}
