package com.excudo.core.metrics.math;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Binary parser for the OpenType MATH table.
 *
 * <p>Mirrors {@link com.excudo.core.metrics.TrueTypeFontParser}'s shape:
 * static {@link #parse} factory, results cached per absolute font path,
 * one subtable parser per OpenType subtable.
 *
 * <p>Returns {@code null} when the supplied font has no MATH table at
 * all -- callers (e.g. layout code that only kicks in when math runs
 * are present) treat the absence as "this font isn't a math font; pick
 * a different one or fall back to plain text rendering."
 *
 * <p>Reference: {@code docs/ms-specs/OT-MATH-table.txt} -- the
 * OpenType spec the binary layout follows verbatim. The parser is
 * structured to mirror the spec's section ordering so the two stay
 * easy to keep in sync.
 */
public final class MathTableParser {

    private static final Map<String, MathTable> cache = new ConcurrentHashMap<>();

    private MathTableParser() {}

    /**
     * Parse the MATH table from the supplied OpenType font, or return
     * null if the font has no MATH table.
     *
     * <p>Results are cached by absolute path so successive parses of
     * the same font (one per math run during a render) don't re-read
     * the file.
     */
    public static MathTable parse(Path fontPath) throws IOException {
        String key = fontPath.toAbsolutePath().toString();
        MathTable cached = cache.get(key);
        if (cached != null) return cached;

        ByteBuffer buf;
        try (FileChannel ch = FileChannel.open(fontPath, StandardOpenOption.READ)) {
            buf = ByteBuffer.allocate((int) ch.size());
            ch.read(buf);
            buf.flip();
        }

        int mathOffset = findMathTableOffset(buf);
        if (mathOffset < 0) return null;

        MathTable table = parseInternal(buf, mathOffset);
        if (table != null) cache.put(key, table);
        return table;
    }

    public static void clearCache() {
        cache.clear();
    }

    /** Locate the MATH table in the offset table, or -1 when absent.
     *  Handles plain TTF/OTF and TTC (collection) files. */
    private static int findMathTableOffset(ByteBuffer buf) {
        int sfVersion = buf.getInt(0);
        boolean isTTC = sfVersion == 0x74746366; // "ttcf"
        int tableOffset = 0;
        if (isTTC) {
            int numFonts = buf.getInt(8);
            if (numFonts < 1) return -1;
            tableOffset = buf.getInt(12);
        }
        int numTables = buf.getShort(tableOffset + 4) & 0xFFFF;
        for (int i = 0; i < numTables; i++) {
            int recordOffset = tableOffset + 12 + i * 16;
            String tag = readTag(buf, recordOffset);
            if ("MATH".equals(tag)) {
                return buf.getInt(recordOffset + 8);
            }
        }
        return -1;
    }

    private static MathTable parseInternal(ByteBuffer buf, int mathOff) {
        int majorVersion = buf.getShort(mathOff) & 0xFFFF;
        int minorVersion = buf.getShort(mathOff + 2) & 0xFFFF;
        int constantsOffset = buf.getShort(mathOff + 4) & 0xFFFF;
        int glyphInfoOffset = buf.getShort(mathOff + 6) & 0xFFFF;
        int variantsOffset  = buf.getShort(mathOff + 8) & 0xFFFF;

        MathConstants constants = constantsOffset > 0
            ? parseMathConstants(buf, mathOff + constantsOffset)
            : null;
        MathGlyphInfo glyphInfo = glyphInfoOffset > 0
            ? parseMathGlyphInfo(buf, mathOff + glyphInfoOffset)
            : new MathGlyphInfo(Map.of(), Map.of(), Set.of(), Map.of());
        MathVariants variants = variantsOffset > 0
            ? parseMathVariants(buf, mathOff + variantsOffset)
            : new MathVariants(0, Map.of(), Map.of(), Map.of(), Map.of());

        return new MathTable(majorVersion, minorVersion, constants, glyphInfo, variants);
    }

    // ------------------------------------------------------------------
    // MathConstants
    // ------------------------------------------------------------------

    private static MathConstants parseMathConstants(ByteBuffer buf, int off) {
        // Layout: 4 simple int16/uint16 fields, then 51 MathValueRecord
        // (4 bytes each), then 1 trailing int16. See
        // docs/ms-specs/OT-MATH-table.txt lines 188-256.
        int p = off;
        int scriptPercentScaleDown          = buf.getShort(p);     p += 2;
        int scriptScriptPercentScaleDown    = buf.getShort(p);     p += 2;
        int delimitedSubFormulaMinHeight    = buf.getShort(p) & 0xFFFF; p += 2;
        int displayOperatorMinHeight        = buf.getShort(p) & 0xFFFF; p += 2;

        // From here every field is a MathValueRecord (4 bytes each).
        MathValueRecord mathLeading                       = readMVR(buf, p); p += 4;
        MathValueRecord axisHeight                        = readMVR(buf, p); p += 4;
        MathValueRecord accentBaseHeight                  = readMVR(buf, p); p += 4;
        MathValueRecord flattenedAccentBaseHeight         = readMVR(buf, p); p += 4;
        MathValueRecord subscriptShiftDown                = readMVR(buf, p); p += 4;
        MathValueRecord subscriptTopMax                   = readMVR(buf, p); p += 4;
        MathValueRecord subscriptBaselineDropMin          = readMVR(buf, p); p += 4;
        MathValueRecord superscriptShiftUp                = readMVR(buf, p); p += 4;
        MathValueRecord superscriptShiftUpCramped         = readMVR(buf, p); p += 4;
        MathValueRecord superscriptBottomMin              = readMVR(buf, p); p += 4;
        MathValueRecord superscriptBaselineDropMax        = readMVR(buf, p); p += 4;
        MathValueRecord subSuperscriptGapMin              = readMVR(buf, p); p += 4;
        MathValueRecord superscriptBottomMaxWithSubscript = readMVR(buf, p); p += 4;
        MathValueRecord spaceAfterScript                  = readMVR(buf, p); p += 4;
        MathValueRecord upperLimitGapMin                  = readMVR(buf, p); p += 4;
        MathValueRecord upperLimitBaselineRiseMin         = readMVR(buf, p); p += 4;
        MathValueRecord lowerLimitGapMin                  = readMVR(buf, p); p += 4;
        MathValueRecord lowerLimitBaselineDropMin         = readMVR(buf, p); p += 4;
        MathValueRecord stackTopShiftUp                   = readMVR(buf, p); p += 4;
        MathValueRecord stackTopDisplayStyleShiftUp       = readMVR(buf, p); p += 4;
        MathValueRecord stackBottomShiftDown              = readMVR(buf, p); p += 4;
        MathValueRecord stackBottomDisplayStyleShiftDown  = readMVR(buf, p); p += 4;
        MathValueRecord stackGapMin                       = readMVR(buf, p); p += 4;
        MathValueRecord stackDisplayStyleGapMin           = readMVR(buf, p); p += 4;
        MathValueRecord stretchStackTopShiftUp            = readMVR(buf, p); p += 4;
        MathValueRecord stretchStackBottomShiftDown       = readMVR(buf, p); p += 4;
        MathValueRecord stretchStackGapAboveMin           = readMVR(buf, p); p += 4;
        MathValueRecord stretchStackGapBelowMin           = readMVR(buf, p); p += 4;
        MathValueRecord fractionNumeratorShiftUp          = readMVR(buf, p); p += 4;
        MathValueRecord fractionNumeratorDisplayStyleShiftUp = readMVR(buf, p); p += 4;
        MathValueRecord fractionDenominatorShiftDown      = readMVR(buf, p); p += 4;
        MathValueRecord fractionDenominatorDisplayStyleShiftDown = readMVR(buf, p); p += 4;
        MathValueRecord fractionNumeratorGapMin           = readMVR(buf, p); p += 4;
        MathValueRecord fractionNumDisplayStyleGapMin     = readMVR(buf, p); p += 4;
        MathValueRecord fractionRuleThickness             = readMVR(buf, p); p += 4;
        MathValueRecord fractionDenominatorGapMin         = readMVR(buf, p); p += 4;
        MathValueRecord fractionDenomDisplayStyleGapMin   = readMVR(buf, p); p += 4;
        MathValueRecord skewedFractionHorizontalGap       = readMVR(buf, p); p += 4;
        MathValueRecord skewedFractionVerticalGap         = readMVR(buf, p); p += 4;
        MathValueRecord overbarVerticalGap                = readMVR(buf, p); p += 4;
        MathValueRecord overbarRuleThickness              = readMVR(buf, p); p += 4;
        MathValueRecord overbarExtraAscender              = readMVR(buf, p); p += 4;
        MathValueRecord underbarVerticalGap               = readMVR(buf, p); p += 4;
        MathValueRecord underbarRuleThickness             = readMVR(buf, p); p += 4;
        MathValueRecord underbarExtraDescender            = readMVR(buf, p); p += 4;
        MathValueRecord radicalVerticalGap                = readMVR(buf, p); p += 4;
        MathValueRecord radicalDisplayStyleVerticalGap    = readMVR(buf, p); p += 4;
        MathValueRecord radicalRuleThickness              = readMVR(buf, p); p += 4;
        MathValueRecord radicalExtraAscender              = readMVR(buf, p); p += 4;
        MathValueRecord radicalKernBeforeDegree           = readMVR(buf, p); p += 4;
        MathValueRecord radicalKernAfterDegree            = readMVR(buf, p); p += 4;
        int radicalDegreeBottomRaisePercent = buf.getShort(p);

        return new MathConstants(
            scriptPercentScaleDown,
            scriptScriptPercentScaleDown,
            delimitedSubFormulaMinHeight,
            displayOperatorMinHeight,
            mathLeading, axisHeight, accentBaseHeight, flattenedAccentBaseHeight,
            subscriptShiftDown, subscriptTopMax, subscriptBaselineDropMin,
            superscriptShiftUp, superscriptShiftUpCramped, superscriptBottomMin,
            superscriptBaselineDropMax, subSuperscriptGapMin,
            superscriptBottomMaxWithSubscript, spaceAfterScript,
            upperLimitGapMin, upperLimitBaselineRiseMin,
            lowerLimitGapMin, lowerLimitBaselineDropMin,
            stackTopShiftUp, stackTopDisplayStyleShiftUp,
            stackBottomShiftDown, stackBottomDisplayStyleShiftDown,
            stackGapMin, stackDisplayStyleGapMin,
            stretchStackTopShiftUp, stretchStackBottomShiftDown,
            stretchStackGapAboveMin, stretchStackGapBelowMin,
            fractionNumeratorShiftUp, fractionNumeratorDisplayStyleShiftUp,
            fractionDenominatorShiftDown, fractionDenominatorDisplayStyleShiftDown,
            fractionNumeratorGapMin, fractionNumDisplayStyleGapMin,
            fractionRuleThickness,
            fractionDenominatorGapMin, fractionDenomDisplayStyleGapMin,
            skewedFractionHorizontalGap, skewedFractionVerticalGap,
            overbarVerticalGap, overbarRuleThickness, overbarExtraAscender,
            underbarVerticalGap, underbarRuleThickness, underbarExtraDescender,
            radicalVerticalGap, radicalDisplayStyleVerticalGap,
            radicalRuleThickness, radicalExtraAscender,
            radicalKernBeforeDegree, radicalKernAfterDegree,
            radicalDegreeBottomRaisePercent
        );
    }

    /** Read one MathValueRecord (int16 value + Offset16 deviceTable). */
    private static MathValueRecord readMVR(ByteBuffer buf, int off) {
        int value = buf.getShort(off);
        int devOff = buf.getShort(off + 2) & 0xFFFF;
        return new MathValueRecord(value, devOff);
    }

    // ------------------------------------------------------------------
    // MathGlyphInfo
    // ------------------------------------------------------------------

    private static MathGlyphInfo parseMathGlyphInfo(ByteBuffer buf, int gOff) {
        int italicsOff      = buf.getShort(gOff)     & 0xFFFF;
        int topAccentOff    = buf.getShort(gOff + 2) & 0xFFFF;
        int extendedShapeOff = buf.getShort(gOff + 4) & 0xFFFF;
        int kernInfoOff     = buf.getShort(gOff + 6) & 0xFFFF;

        Map<Integer, Integer> italics = italicsOff > 0
            ? parseGlyphValueMap(buf, gOff + italicsOff) : Map.of();
        Map<Integer, Integer> topAccent = topAccentOff > 0
            ? parseGlyphValueMap(buf, gOff + topAccentOff) : Map.of();
        Set<Integer> extendedShapes = extendedShapeOff > 0
            ? parseCoverage(buf, gOff + extendedShapeOff) : Set.of();
        Map<Integer, MathKernRecord> kernRecords = kernInfoOff > 0
            ? parseKernInfo(buf, gOff + kernInfoOff) : Map.of();

        return new MathGlyphInfo(italics, topAccent, extendedShapes, kernRecords);
    }

    /**
     * Parse a "MathItalicsCorrectionInfo" or "MathTopAccentAttachment"
     * subtable. Both share the same shape:
     *
     * <pre>
     *   Offset16 coverageOffset
     *   uint16   valueCount
     *   MathValueRecord values[valueCount]
     * </pre>
     *
     * The coverage table enumerates the glyph IDs in the same index
     * order as {@code values[]}; we zip them into a map.
     */
    private static Map<Integer, Integer> parseGlyphValueMap(ByteBuffer buf, int subOff) {
        int coverageOff = buf.getShort(subOff) & 0xFFFF;
        int valueCount  = buf.getShort(subOff + 2) & 0xFFFF;
        List<Integer> glyphs = parseCoverageOrdered(buf, subOff + coverageOff);
        Map<Integer, Integer> out = new HashMap<>(valueCount * 2);
        int valuesOff = subOff + 4;
        int n = Math.min(valueCount, glyphs.size());
        for (int i = 0; i < n; i++) {
            int v = buf.getShort(valuesOff + i * 4); // value; skip device offset
            out.put(glyphs.get(i), (int) v);
        }
        return out;
    }

    /**
     * Parse a Coverage table (OpenType GSUB/GPOS shared format) into
     * a glyph-ID list in coverage order. Both formats supported:
     * format 1 (GlyphArray) and format 2 (RangeRecord).
     */
    private static List<Integer> parseCoverageOrdered(ByteBuffer buf, int off) {
        int format = buf.getShort(off) & 0xFFFF;
        List<Integer> out = new ArrayList<>();
        if (format == 1) {
            int glyphCount = buf.getShort(off + 2) & 0xFFFF;
            for (int i = 0; i < glyphCount; i++) {
                out.add(buf.getShort(off + 4 + i * 2) & 0xFFFF);
            }
        } else if (format == 2) {
            int rangeCount = buf.getShort(off + 2) & 0xFFFF;
            for (int i = 0; i < rangeCount; i++) {
                int recOff = off + 4 + i * 6;
                int start = buf.getShort(recOff) & 0xFFFF;
                int end   = buf.getShort(recOff + 2) & 0xFFFF;
                for (int g = start; g <= end; g++) out.add(g);
            }
        }
        return out;
    }

    /** Coverage table → unordered set view. Faster lookup when caller
     *  only needs membership (e.g. the extended-shape coverage). */
    private static Set<Integer> parseCoverage(ByteBuffer buf, int off) {
        return new HashSet<>(parseCoverageOrdered(buf, off));
    }

    /**
     * Parse the MathKernInfo subtable:
     *
     * <pre>
     *   Offset16 mathKernCoverageOffset
     *   uint16   mathKernCount
     *   MathKernInfoRecord records[mathKernCount]
     * </pre>
     *
     * Each {@code MathKernInfoRecord} is four Offset16 to per-corner
     * MathKern subtables (top-right, top-left, bottom-right,
     * bottom-left).
     */
    private static Map<Integer, MathKernRecord> parseKernInfo(ByteBuffer buf, int kOff) {
        int coverageOff = buf.getShort(kOff) & 0xFFFF;
        int kernCount   = buf.getShort(kOff + 2) & 0xFFFF;
        List<Integer> glyphs = parseCoverageOrdered(buf, kOff + coverageOff);
        Map<Integer, MathKernRecord> out = new HashMap<>(kernCount * 2);
        int recordsOff = kOff + 4;
        int n = Math.min(kernCount, glyphs.size());
        for (int i = 0; i < n; i++) {
            int recOff = recordsOff + i * 8;
            int trOff = buf.getShort(recOff)     & 0xFFFF;
            int tlOff = buf.getShort(recOff + 2) & 0xFFFF;
            int brOff = buf.getShort(recOff + 4) & 0xFFFF;
            int blOff = buf.getShort(recOff + 6) & 0xFFFF;
            MathKernRecord.MathKern tr = trOff > 0 ? parseKern(buf, kOff + trOff) : null;
            MathKernRecord.MathKern tl = tlOff > 0 ? parseKern(buf, kOff + tlOff) : null;
            MathKernRecord.MathKern br = brOff > 0 ? parseKern(buf, kOff + brOff) : null;
            MathKernRecord.MathKern bl = blOff > 0 ? parseKern(buf, kOff + blOff) : null;
            out.put(glyphs.get(i), new MathKernRecord(tr, tl, br, bl));
        }
        return out;
    }

    /**
     * Parse one MathKern subtable:
     *
     * <pre>
     *   uint16 heightCount
     *   MathValueRecord correctionHeight[heightCount]
     *   MathValueRecord kernValues[heightCount + 1]
     * </pre>
     *
     * Note the kerns array has one more entry than correction heights
     * -- the trailing kern is the value used when the script's height
     * exceeds every correction-height boundary.
     */
    private static MathKernRecord.MathKern parseKern(ByteBuffer buf, int off) {
        int heightCount = buf.getShort(off) & 0xFFFF;
        int p = off + 2;
        int[] heights = new int[heightCount];
        for (int i = 0; i < heightCount; i++) {
            heights[i] = buf.getShort(p);
            p += 4; // skip device-table offset
        }
        int[] kerns = new int[heightCount + 1];
        for (int i = 0; i < heightCount + 1; i++) {
            kerns[i] = buf.getShort(p);
            p += 4;
        }
        return new MathKernRecord.MathKern(heights, kerns);
    }

    // ------------------------------------------------------------------
    // MathVariants
    // ------------------------------------------------------------------

    private static MathVariants parseMathVariants(ByteBuffer buf, int vOff) {
        int minConnectorOverlap = buf.getShort(vOff) & 0xFFFF;
        int vCovOff             = buf.getShort(vOff + 2) & 0xFFFF;
        int hCovOff             = buf.getShort(vOff + 4) & 0xFFFF;
        int vGlyphCount         = buf.getShort(vOff + 6) & 0xFFFF;
        int hGlyphCount         = buf.getShort(vOff + 8) & 0xFFFF;
        int vConstrOff = vOff + 10;
        int hConstrOff = vConstrOff + vGlyphCount * 2;

        Map<Integer, List<MathVariants.MathGlyphVariantRecord>> verticalVariants = new HashMap<>();
        Map<Integer, List<MathVariants.MathGlyphVariantRecord>> horizontalVariants = new HashMap<>();
        Map<Integer, MathVariants.GlyphAssembly> verticalAssemblies = new HashMap<>();
        Map<Integer, MathVariants.GlyphAssembly> horizontalAssemblies = new HashMap<>();

        if (vCovOff > 0) {
            List<Integer> glyphs = parseCoverageOrdered(buf, vOff + vCovOff);
            int n = Math.min(vGlyphCount, glyphs.size());
            for (int i = 0; i < n; i++) {
                int constrOff = buf.getShort(vConstrOff + i * 2) & 0xFFFF;
                if (constrOff > 0) {
                    parseGlyphConstruction(buf, vOff + constrOff,
                        glyphs.get(i), verticalVariants, verticalAssemblies);
                }
            }
        }
        if (hCovOff > 0) {
            List<Integer> glyphs = parseCoverageOrdered(buf, vOff + hCovOff);
            int n = Math.min(hGlyphCount, glyphs.size());
            for (int i = 0; i < n; i++) {
                int constrOff = buf.getShort(hConstrOff + i * 2) & 0xFFFF;
                if (constrOff > 0) {
                    parseGlyphConstruction(buf, vOff + constrOff,
                        glyphs.get(i), horizontalVariants, horizontalAssemblies);
                }
            }
        }

        return new MathVariants(minConnectorOverlap,
            verticalVariants, horizontalVariants,
            verticalAssemblies, horizontalAssemblies);
    }

    /**
     * Parse one MathGlyphConstruction subtable:
     *
     * <pre>
     *   Offset16 glyphAssemblyOffset
     *   uint16   variantCount
     *   MathGlyphVariantRecord variants[variantCount]   (uint16 + uint16 each)
     * </pre>
     *
     * Populates the supplied variant + assembly maps for the base glyph.
     */
    private static void parseGlyphConstruction(ByteBuffer buf, int gcOff, int baseGlyph,
            Map<Integer, List<MathVariants.MathGlyphVariantRecord>> variantsMap,
            Map<Integer, MathVariants.GlyphAssembly> assembliesMap) {
        int assemblyOff = buf.getShort(gcOff) & 0xFFFF;
        int variantCount = buf.getShort(gcOff + 2) & 0xFFFF;
        List<MathVariants.MathGlyphVariantRecord> variants = new ArrayList<>(variantCount);
        int variantsOff = gcOff + 4;
        for (int i = 0; i < variantCount; i++) {
            int recOff = variantsOff + i * 4;
            int variantGlyph = buf.getShort(recOff) & 0xFFFF;
            int advance = buf.getShort(recOff + 2) & 0xFFFF;
            variants.add(new MathVariants.MathGlyphVariantRecord(variantGlyph, advance));
        }
        if (!variants.isEmpty()) variantsMap.put(baseGlyph, variants);

        if (assemblyOff > 0) {
            MathVariants.GlyphAssembly asm = parseAssembly(buf, gcOff + assemblyOff);
            if (asm != null) assembliesMap.put(baseGlyph, asm);
        }
    }

    /**
     * Parse one GlyphAssembly subtable:
     *
     * <pre>
     *   MathValueRecord italicsCorrection
     *   uint16          partCount
     *   GlyphPart       partRecords[partCount]   (10 bytes each)
     * </pre>
     *
     * Each {@code GlyphPart} is glyphID + startConn + endConn +
     * fullAdvance + partFlags. partFlags's low bit marks an extender
     * (repeatable middle piece).
     */
    private static MathVariants.GlyphAssembly parseAssembly(ByteBuffer buf, int aOff) {
        int italicsCorr = buf.getShort(aOff); // value only; skip dev offset
        int partCount = buf.getShort(aOff + 4) & 0xFFFF;
        List<MathVariants.GlyphPart> parts = new ArrayList<>(partCount);
        int partsOff = aOff + 6;
        for (int i = 0; i < partCount; i++) {
            int recOff = partsOff + i * 10;
            int glyphId         = buf.getShort(recOff)     & 0xFFFF;
            int startConnLength = buf.getShort(recOff + 2) & 0xFFFF;
            int endConnLength   = buf.getShort(recOff + 4) & 0xFFFF;
            int fullAdvance     = buf.getShort(recOff + 6) & 0xFFFF;
            int flags           = buf.getShort(recOff + 8) & 0xFFFF;
            boolean isExtender  = (flags & 0x0001) != 0;
            parts.add(new MathVariants.GlyphPart(
                glyphId, startConnLength, endConnLength, fullAdvance, isExtender));
        }
        return new MathVariants.GlyphAssembly(italicsCorr, parts);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static String readTag(ByteBuffer buf, int offset) {
        byte[] tag = new byte[4];
        for (int i = 0; i < 4; i++) tag[i] = buf.get(offset + i);
        return new String(tag);
    }
}
