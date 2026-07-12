package com.excudo.core.metrics;

import com.excudo.core.model.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Measures TextBody content against available dimensions using exact TTF metrics.
 * Performs greedy word wrap, line height calculation, paragraph spacing, and body insets.
 * All values in EMUs.
 *
 * <p>This is the single wrap authority for the render pipeline: the
 * produced {@link MeasuredText} carries the complete line layout
 * (per-line styled segments with x-offsets and advance widths) and
 * {@code TextPainter} paints exactly those lines. Everything that
 * affects glyph geometry is applied here, once:
 *
 * <ul>
 *   <li>per-run font family / size / bold / italic (with
 *       {@link TextStyleSource} supplying inherited lstStyle defaults),</li>
 *   <li>per-run character tracking ({@code spc}), with per-character
 *       advances recorded so painted glyph positions match measured
 *       line widths,</li>
 *   <li>{@code cap="small"}: lowercase letters become uppercase
 *       segments at 80% font size,</li>
 *   <li>super/subscript ({@code baseline}): glyphs scale to
 *       {@value #SUPERSUB_FONT_SCALE} of the run size and shift by the
 *       given percentage of the unscaled size (both calibrated against
 *       PowerPoint PDF export),</li>
 *   <li>{@code normAutofit}: the stored {@code fontScale} multiplies
 *       every run size and {@code lnSpcReduction} shrinks the effective
 *       line-spacing factor. PowerPoint persists the values it computed
 *       when it shrank the text, so applying them verbatim reproduces
 *       the fitted layout — no fitting algorithm runs here.
 *       {@code spAutoFit} ("resize shape to fit text") needs no
 *       adjustment at all: the stored shape geometry already reflects
 *       the grown box, and PowerPoint renders the text at full size
 *       even when the stored geometry is smaller (text simply
 *       overflows, unclipped, exactly as in PDF export),</li>
 *   <li>{@code wrap="none"}: lines break only at explicit
 *       {@code <a:br/>} runs.</li>
 * </ul>
 */
public final class TextMeasurer {

    private static final int DEFAULT_FONT_SIZE = 1800; // 18pt in hundredths
    private static final String DEFAULT_FONT_FAMILY = "DejaVu Sans";

    // OOXML default body insets when bodyPr omits them
    private static final long DEFAULT_LEFT_INSET = 91440;
    private static final long DEFAULT_TOP_INSET = 45720;
    private static final long DEFAULT_RIGHT_INSET = 91440;
    private static final long DEFAULT_BOTTOM_INSET = 45720;

    // Default line spacing: 100% = 100000 in OOXML
    private static final int DEFAULT_LINE_SPACING = 100000;

    /**
     * Glyph scale for baseline-shifted (super/subscript) runs. PowerPoint
     * keeps rPr/@sz unchanged but renders the glyphs at two thirds size:
     * its PDF export of the text-spacing-caps corpus deck selects a
     * 10.68pt font for the 16pt baseline-shifted runs (10.68 / 16.02 =
     * 0.667). The baseline offset itself stays a percentage of the
     * UNSCALED size.
     */
    public static final double SUPERSUB_FONT_SCALE = 2.0 / 3.0;

    /**
     * Small-caps glyph scale for lowercase-derived letters (cap="small").
     * PowerPoint's PDF export renders the lowercase letters of a 16pt
     * small-caps run at 12.864pt = 0.804x; 0.8 is the conventional value.
     */
    public static final double SMALLCAPS_FONT_SCALE = 0.8;

    /**
     * Single line spacing as a multiple of the font size. PowerPoint's
     * layout engine uses a flat 120% of the point size for percentage
     * line spacing — NOT the font's OS/2 line height (Calibri's is
     * 1.2207 em). Read straight from its PDF export: 20pt lines pitch at
     * exactly 24pt, 14pt at 16.8pt, 13pt at 15.6pt, and spcPct
     * multiplies that (14pt at 150% pitches at 25.2pt = 1.5 x 1.2 em).
     */
    public static final double SINGLE_LINE_PITCH = 1.2;

    /**
     * Fraction of the extra leading (pitch beyond single-spaced) that
     * lands ABOVE the baseline. Fitted to PowerPoint PDF export of 14pt
     * Calibri at 100/150/200% spacing (first baselines 13.09 / 18.97 /
     * 25.24 pt below the text-area top): observed per-50%-step shifts of
     * 5.88pt and 6.27pt against a 8.4pt pitch step, i.e. 0.70-0.75; 0.72
     * keeps every measured baseline within 0.25px at 96 DPI.
     */
    private static final double EXTRA_LEADING_ABOVE_FRACTION = 0.72;

    private TextMeasurer() {}

    /**
     * Measure a TextBody with no inherited list-style context (legacy
     * callers and layout validation). Prefer the {@link TextStyleSource}
     * overload in the render pipeline.
     */
    public static MeasuredText measure(TextBody body, long availableWidthEmu) {
        return measure(body, availableWidthEmu, TextStyleSource.EMPTY);
    }

    /**
     * Measure a TextBody to determine how much space it needs and how
     * its lines lay out.
     *
     * @param body              the text body to measure
     * @param availableWidthEmu total shape width in EMUs (body insets are subtracted internally)
     * @param styles            inherited per-level style defaults (never null)
     * @return measurement results
     */
    public static MeasuredText measure(TextBody body, long availableWidthEmu,
                                       TextStyleSource styles) {
        if (body == null || body.getParagraphs().isEmpty()) {
            return new MeasuredText(0, 0, 0, List.of());
        }
        if (styles == null) styles = TextStyleSource.EMPTY;

        BodyProperties props = body.getBodyProperties() != null
            ? body.getBodyProperties() : BodyProperties.DEFAULT;
        long leftInset = props.getLeftInset() != null ? props.getLeftInset() : DEFAULT_LEFT_INSET;
        long rightInset = props.getRightInset() != null ? props.getRightInset() : DEFAULT_RIGHT_INSET;
        long topInset = props.getTopInset() != null ? props.getTopInset() : DEFAULT_TOP_INSET;
        long bottomInset = props.getBottomInset() != null ? props.getBottomInset() : DEFAULT_BOTTOM_INSET;

        long textAreaWidth = availableWidthEmu - leftInset - rightInset;
        if (textAreaWidth <= 0) textAreaWidth = 1;

        boolean noWrap = "none".equals(props.getWrap());

        // Deterministic autofit: PowerPoint stored the computed values.
        double fontScale = 1.0;
        double lnSpcFactor = 1.0;
        if (props.getAutofit() == AutofitType.NORMAL) {
            if (props.getFontScale() != null && props.getFontScale() > 0) {
                fontScale = snapToAutofitLadder(props.getFontScale()) / 100000.0;
            }
            if (props.getLnSpcReduction() != null && props.getLnSpcReduction() > 0) {
                lnSpcFactor = 1.0 - props.getLnSpcReduction() / 100000.0;
            }
        }

        Map<String, FontData> fontCache = new HashMap<>();
        List<MeasuredText.ParagraphMeasurement> paraMeasurements = new ArrayList<>();
        long textHeight = 0;
        long maxLineWidth = 0;

        for (TextParagraph para : body.getParagraphs()) {
            TextStyleSource.LevelStyle ls = styles.levelStyle(para.getLevel());
            if (ls == null) ls = TextStyleSource.LevelStyle.EMPTY;
            MeasuredText.ParagraphMeasurement pm = measureParagraph(
                para, ls, textAreaWidth, noWrap, fontScale, lnSpcFactor, fontCache);
            paraMeasurements.add(pm);
            textHeight += pm.getHeightEmu();
            for (long lw : pm.getLineWidths()) {
                maxLineWidth = Math.max(maxLineWidth, lw);
            }
        }

        long totalHeight = textHeight + topInset + bottomInset;
        return new MeasuredText(totalHeight, textHeight, maxLineWidth, paraMeasurements);
    }

    /**
     * PowerPoint's autofit font-scale ladder: the only scales its own
     * shrink-to-fit engine produces (thousandths of a percent). Steps of
     * 7.5% from 100% down to 70%, then 5% down to 25%.
     *
     * <p>PowerPoint snaps off-ladder stored values to this ladder when it
     * lays the text out. Empirically verified against the text-autofit
     * corpus ground truth: the deck stores {@code fontScale="62500"} on a
     * 20pt run, and PowerPoint's own PDF export draws that box at 12.96pt
     * (= 65%, the nearest ladder step) while boxes stored at 40% and 100%
     * (both on-ladder) export at exactly 8pt and 16pt. Values already on
     * the ladder — i.e. everything PowerPoint itself authors — pass
     * through unchanged.
     */
    private static final int[] AUTOFIT_FONT_SCALE_LADDER = {
        25000, 30000, 35000, 40000, 45000, 50000, 55000, 60000,
        65000, 70000, 77500, 85000, 92500, 100000
    };

    static int snapToAutofitLadder(int fontScale) {
        if (fontScale >= 100000) return fontScale; // >=100%: nothing to snap
        int best = AUTOFIT_FONT_SCALE_LADDER[0];
        for (int step : AUTOFIT_FONT_SCALE_LADDER) {
            // Nearest step, ties resolved upward (62.5% -> 65%, per the
            // ground-truth calibration above).
            if (Math.abs(step - fontScale) <= Math.abs(best - fontScale)) {
                best = step;
            }
        }
        return best;
    }

    // ========== PER-PARAGRAPH LAYOUT ==========

    /** Effective style of one measurable token. */
    private record TokenStyle(TextRun run, String family, int sizeCentiPt,
                              boolean bold, boolean italic, int spcCentiPt,
                              long baselineShiftEmu, FontData fontData) {}

    /** One wrap-atomic token: a whitespace-delimited chunk or a forced break. */
    private record Token(String text, TokenStyle style, long widthEmu,
                         long[] charAdvances, boolean isBreak) {
        boolean isBlank() { return !isBreak && text.isBlank(); }
    }

    private static MeasuredText.ParagraphMeasurement measureParagraph(
            TextParagraph para, TextStyleSource.LevelStyle ls, long textAreaWidth,
            boolean noWrap, double fontScale, double lnSpcFactor,
            Map<String, FontData> fontCache) {

        // Resolved paragraph geometry: pPr overrides > level style > zero.
        long marL = para.getMarginLeft() != null ? para.getMarginLeft()
            : ls.marginLeftEmu() != null ? ls.marginLeftEmu() : 0;
        long marR = para.getMarginRight() != null ? Math.max(0, para.getMarginRight())
            : ls.marginRightEmu() != null ? Math.max(0, ls.marginRightEmu()) : 0;
        long indent = para.getIndent() != null ? para.getIndent()
            : ls.indentEmu() != null ? ls.indentEmu() : 0;
        String alignment = para.getAlignment() != null ? para.getAlignment()
            : ls.alignment() != null ? ls.alignment() : "l";

        long wrapWidth = noWrap
            ? Long.MAX_VALUE / 4
            : textAreaWidth - marL - marR - Math.max(0, indent);
        if (wrapWidth <= 0) wrapWidth = 1;

        // Effective line-spacing factor. Values <= 400000 are thousandths
        // of a percent; larger values are legacy absolute EMU line heights.
        int lineSpacingVal = para.getLineSpacing() != null ? para.getLineSpacing()
            : ls.lineSpacingPct() != null ? ls.lineSpacingPct() : DEFAULT_LINE_SPACING;
        boolean absoluteSpacing = lineSpacingVal > 400000;
        double spacingFactor = absoluteSpacing ? 1.0
            : (lineSpacingVal / 100000.0) * lnSpcFactor;

        long spaceBefore = para.getSpaceBefore() != null
            ? centipointsToEmu(para.getSpaceBefore())
            : ls.spaceBeforeCentiPt() != null ? centipointsToEmu(ls.spaceBeforeCentiPt()) : 0;
        long spaceAfter = para.getSpaceAfter() != null
            ? centipointsToEmu(para.getSpaceAfter())
            : ls.spaceAfterCentiPt() != null ? centipointsToEmu(ls.spaceAfterCentiPt()) : 0;
        if (spaceBefore < 0) spaceBefore = 0;
        if (spaceAfter < 0) spaceAfter = 0;

        // Default token style for empty paragraphs / empty lines.
        TokenStyle defaultStyle = resolveStyle(null, ls, fontScale, fontCache);

        if (para.isEmpty()) {
            MeasuredText.Line line = buildLine(List.of(), defaultStyle,
                spacingFactor, absoluteSpacing, lineSpacingVal, false);
            long height = spaceBefore + line.advanceEmu() + spaceAfter;
            return new MeasuredText.ParagraphMeasurement(1, height, List.of(0L),
                List.of(line), spaceBefore, spaceAfter, marL, indent, wrapWidth, alignment);
        }

        // --- Tokenize every run into wrap-atomic chunks ---
        List<Token> tokens = new ArrayList<>();
        for (TextRun run : para.getRuns()) {
            if (run.isLineBreak()) {
                tokens.add(new Token("\n", defaultStyle, 0, null, true));
                continue;
            }
            TokenStyle base = resolveStyle(run, ls, fontScale, fontCache);
            for (StyledText st : expandCapitalization(run, base, fontCache)) {
                tokenize(st.text, st.style, tokens);
            }
        }

        // --- Greedy wrap into lines ---
        List<MeasuredText.Line> lines = new ArrayList<>();
        List<Token> currentLine = new ArrayList<>();
        long currentAdvance = 0;

        for (Token token : tokens) {
            if (token.isBreak()) {
                lines.add(buildLine(currentLine, defaultStyle, spacingFactor,
                    absoluteSpacing, lineSpacingVal, true));
                currentLine = new ArrayList<>();
                currentAdvance = 0;
                continue;
            }

            if (currentAdvance + token.widthEmu() > wrapWidth && !currentLine.isEmpty()
                    && !token.isBlank()) {
                lines.add(buildLine(currentLine, defaultStyle, spacingFactor,
                    absoluteSpacing, lineSpacingVal, false));
                currentLine = new ArrayList<>();
                currentAdvance = 0;
            }

            // Skip leading whitespace at the start of a wrapped line.
            if (currentLine.isEmpty() && token.isBlank() && !lines.isEmpty()) {
                continue;
            }

            // A single token wider than the line: break at character
            // boundaries (PowerPoint's long-word fallback).
            if (token.widthEmu() > wrapWidth && currentLine.isEmpty()) {
                currentAdvance = breakLongToken(token, wrapWidth, currentLine, lines,
                    defaultStyle, spacingFactor, absoluteSpacing, lineSpacingVal);
                continue;
            }

            currentLine.add(token);
            currentAdvance += token.widthEmu();
        }
        if (!currentLine.isEmpty() || lines.isEmpty()) {
            lines.add(buildLine(currentLine, defaultStyle, spacingFactor,
                absoluteSpacing, lineSpacingVal, false));
        }

        long height = spaceBefore + spaceAfter;
        List<Long> lineWidths = new ArrayList<>(lines.size());
        for (MeasuredText.Line line : lines) {
            height += line.advanceEmu();
            lineWidths.add(line.widthEmu());
        }

        return new MeasuredText.ParagraphMeasurement(lines.size(), height, lineWidths,
            lines, spaceBefore, spaceAfter, marL, indent, wrapWidth, alignment);
    }

    /**
     * Assemble a {@link MeasuredText.Line} from tokens: position each
     * segment, compute ink width (trailing whitespace excluded), and
     * derive vertical metrics from the tallest segment.
     *
     * <p>Vertical model (read from PowerPoint's own PDF export of the
     * text-spacing-caps and text-autofit corpus decks):
     * <ul>
     *   <li>line advance = {@value #SINGLE_LINE_PITCH} x font size x
     *       spacing factor — a flat 120% of the point size, not the
     *       font's OS/2 line height;</li>
     *   <li>baseline offset from line top = single-spaced line height x
     *       the font's ascent fraction, plus
     *       {@value #EXTRA_LEADING_ABOVE_FRACTION} of any extra leading
     *       (or minus, for autofit's lnSpcReduction).</li>
     * </ul>
     */
    private static MeasuredText.Line buildLine(List<Token> tokens, TokenStyle defaultStyle,
                                               double spacingFactor, boolean absoluteSpacing,
                                               int lineSpacingVal, boolean forcedBreak) {
        List<MeasuredText.Segment> segments = new ArrayList<>(tokens.size());
        long x = 0;
        long inkEnd = 0;
        long maxSingleHeight = 0;
        double maxAscent = 0;
        boolean any = false;

        for (Token t : tokens) {
            TokenStyle s = t.style();
            segments.add(new MeasuredText.Segment(s.run(), t.text(), s.family(),
                s.sizeCentiPt(), s.bold(), s.italic(), x, t.widthEmu(),
                s.baselineShiftEmu(), t.charAdvances()));
            x += t.widthEmu();
            if (!t.isBlank()) inkEnd = x;
            long single = singleLineHeightEmu(s);
            maxSingleHeight = Math.max(maxSingleHeight, single);
            maxAscent = Math.max(maxAscent, single * ascentFraction(s));
            any = true;
        }
        if (!any) {
            maxSingleHeight = singleLineHeightEmu(defaultStyle);
            maxAscent = maxSingleHeight * ascentFraction(defaultStyle);
        }

        long advance;
        long baseline;
        if (absoluteSpacing) {
            advance = lineSpacingVal;
            baseline = Math.min(Math.round(maxAscent), advance);
        } else {
            advance = Math.round(maxSingleHeight * spacingFactor);
            baseline = Math.round(maxAscent
                + EXTRA_LEADING_ABOVE_FRACTION * (advance - maxSingleHeight));
        }

        return new MeasuredText.Line(segments, inkEnd, advance, baseline, forcedBreak);
    }

    /**
     * Character-boundary breaking for a token wider than the wrap width.
     * Emits full lines directly; returns the advance of the still-open
     * trailing chunk left in {@code currentLine}.
     */
    private static long breakLongToken(Token token, long wrapWidth, List<Token> currentLine,
                                       List<MeasuredText.Line> lines, TokenStyle defaultStyle,
                                       double spacingFactor, boolean absoluteSpacing,
                                       int lineSpacingVal) {
        TokenStyle s = token.style();
        String text = token.text();
        int chunkStart = 0;
        long chunkWidth = 0;
        for (int i = 0; i < text.length(); i++) {
            long charWidth = charAdvanceEmu(text.charAt(i), s);
            if (chunkWidth + charWidth > wrapWidth && chunkWidth > 0) {
                currentLine.add(makeToken(text.substring(chunkStart, i), s));
                lines.add(buildLine(currentLine, defaultStyle, spacingFactor,
                    absoluteSpacing, lineSpacingVal, false));
                currentLine.clear();
                chunkStart = i;
                chunkWidth = 0;
            }
            chunkWidth += charWidth;
        }
        if (chunkStart < text.length()) {
            currentLine.add(makeToken(text.substring(chunkStart), s));
        }
        return chunkWidth;
    }

    // ========== TOKENIZATION ==========

    private record StyledText(String text, TokenStyle style) {}

    /**
     * Expand a run into styled sub-texts, applying capitalization
     * transforms. {@code cap="all"} is already handled by
     * {@link TextRun#getDisplayText()}; {@code cap="small"} splits at
     * case boundaries: lowercase letters become uppercase glyphs at
     * {@value #SMALLCAPS_FONT_SCALE} of the run size, everything else
     * keeps the full size.
     */
    private static List<StyledText> expandCapitalization(TextRun run, TokenStyle base,
                                                         Map<String, FontData> fontCache) {
        String text = run.getDisplayText();
        if (text == null || text.isEmpty()) return List.of();
        if (!"small".equalsIgnoreCase(run.getCapitalization())) {
            return List.of(new StyledText(text, base));
        }

        TokenStyle small = derivedSize(base,
            (int) Math.round(base.sizeCentiPt() * SMALLCAPS_FONT_SCALE), fontCache);
        List<StyledText> out = new ArrayList<>();
        StringBuilder chunk = new StringBuilder();
        boolean chunkIsLower = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean lower = Character.isLowerCase(c);
            if (chunk.length() > 0 && lower != chunkIsLower) {
                out.add(styledChunk(chunk.toString(), chunkIsLower, base, small));
                chunk.setLength(0);
            }
            chunkIsLower = lower;
            chunk.append(c);
        }
        if (chunk.length() > 0) {
            out.add(styledChunk(chunk.toString(), chunkIsLower, base, small));
        }
        return out;
    }

    private static StyledText styledChunk(String chunk, boolean isLower,
                                          TokenStyle base, TokenStyle small) {
        return isLower
            ? new StyledText(chunk.toUpperCase(java.util.Locale.ROOT), small)
            : new StyledText(chunk, base);
    }

    /** Split styled text into whitespace-delimited tokens, measuring each. */
    private static void tokenize(String text, TokenStyle style, List<Token> out) {
        if (text == null || text.isEmpty()) return;
        for (String piece : text.split("(?<=\\s)|(?=\\s)")) {
            if (piece.isEmpty()) continue;
            out.add(makeToken(piece, style));
        }
    }

    private static Token makeToken(String piece, TokenStyle style) {
        long[] charAdv = null;
        long width;
        if (style.spcCentiPt() != 0) {
            // Tracking applies per character; record the per-char advances
            // so the painter can place each glyph at its measured position.
            charAdv = new long[piece.length()];
            long sum = 0;
            for (int i = 0; i < piece.length(); i++) {
                charAdv[i] = charAdvanceEmu(piece.charAt(i), style);
                sum += charAdv[i];
            }
            width = sum;
        } else {
            width = measureTextEmu(piece, style);
        }
        return new Token(piece, style, width, charAdv, false);
    }

    // ========== STYLE RESOLUTION ==========

    /**
     * Resolve the effective measurable style of a run: run properties
     * beat level-style defaults beat hard defaults. Autofit fontScale
     * multiplies the size; super/subscript scales glyphs and computes
     * the baseline shift from the pre-scale size.
     */
    private static TokenStyle resolveStyle(TextRun run, TextStyleSource.LevelStyle ls,
                                           double fontScale, Map<String, FontData> fontCache) {
        int size = run != null && run.getFontSize() != null ? run.getFontSize()
            : ls.fontSizeCentiPt() != null ? ls.fontSizeCentiPt() : DEFAULT_FONT_SIZE;
        String family = run != null && run.getFontFamily() != null ? run.getFontFamily()
            : ls.fontFamily() != null ? ls.fontFamily() : DEFAULT_FONT_FAMILY;
        boolean bold = run != null && run.getBold() != null ? run.getBold()
            : Boolean.TRUE.equals(ls.bold());
        boolean italic = run != null && run.getItalic() != null ? run.getItalic()
            : Boolean.TRUE.equals(ls.italic());
        int spc = run != null && run.getCharacterSpacing() != null
            ? run.getCharacterSpacing() : 0;

        size = (int) Math.round(size * fontScale);
        if (size < 100) size = 100; // floor at 1pt

        long baselineShiftEmu = 0;
        Integer baselineAttr = run != null ? run.getBaseline() : null;
        if (baselineAttr != null && baselineAttr != 0) {
            // Shift is a percentage of the (autofit-scaled) run size;
            // positive raises the run, so the screen-space shift is
            // negative. Glyphs render at the reduced super/subscript size.
            long sizeEmu = Math.round(size / 100.0 * 12700.0);
            baselineShiftEmu = -Math.round(baselineAttr / 100000.0 * sizeEmu);
            size = (int) Math.round(size * SUPERSUB_FONT_SCALE);
        }

        // Font substitution parity: when the requested family isn't
        // installed, PowerPoint substitutes a metrics-compatible font and
        // BOTH lays out and renders with it (its PDF export of the
        // integration corpus substitutes Calibri for the missing
        // "Inter"). Measure and paint with the same substitute so wrap
        // positions track what PowerPoint produces on this host; keeping
        // the phantom family would measure with one font's metrics and
        // paint with another's.
        String effectiveFamily = family;
        FontData fd = resolveFontData(family, bold, italic, fontCache);
        if (fd == null || !isInstalled(family, bold, italic)) {
            for (String substitute : FAMILY_SUBSTITUTES) {
                if (isInstalled(substitute, bold, italic)) {
                    FontData subFd = resolveFontData(substitute, bold, italic, fontCache);
                    if (subFd != null) {
                        effectiveFamily = substitute;
                        fd = subFd;
                        break;
                    }
                }
            }
        }
        return new TokenStyle(run, effectiveFamily, size, bold, italic, spc, baselineShiftEmu, fd);
    }

    /**
     * Substitutes tried, in order, when a requested family isn't
     * installed. Calibri first — it is what PowerPoint's own export
     * picked for a missing geometric sans on this corpus — then the
     * bundled DejaVu Sans, which exists on every dev/CI host.
     */
    private static final String[] FAMILY_SUBSTITUTES = { "Calibri", "DejaVu Sans" };

    private static boolean isInstalled(String family, boolean bold, boolean italic) {
        // FontIndex.lookup is a strict name match (with weight/style
        // handling); unlike FontResolver.resolve it does NOT silently
        // fall back to DejaVu, so a null reliably means "not installed".
        return FontIndex.lookup(family, bold, italic) != null
            || FontIndex.lookup(family, false, false) != null;
    }

    private static TokenStyle derivedSize(TokenStyle base, int newSizeCentiPt,
                                          Map<String, FontData> fontCache) {
        return new TokenStyle(base.run(), base.family(), newSizeCentiPt, base.bold(),
            base.italic(), base.spcCentiPt(), base.baselineShiftEmu(), base.fontData());
    }

    // ========== METRIC PRIMITIVES ==========

    private static long measureTextEmu(String text, TokenStyle style) {
        if (style.fontData() != null) {
            return style.fontData().measureStringWidthEmu(text, style.sizeCentiPt());
        }
        // Fallback: approximate at 0.5 * fontSize per character
        double ptPerChar = (style.sizeCentiPt() / 100.0) * 0.5;
        return Math.round(ptPerChar * text.length() * 12700.0);
    }

    private static long charAdvanceEmu(char ch, TokenStyle style) {
        long natural;
        if (style.fontData() != null) {
            double fontSizePt = style.sizeCentiPt() / 100.0;
            int advWidth = style.fontData().getCharAdvanceWidth(ch);
            natural = Math.round((double) advWidth * fontSizePt
                / style.fontData().getUnitsPerEm() * 12700.0);
        } else {
            natural = Math.round((style.sizeCentiPt() / 100.0) * 0.5 * 12700.0);
        }
        // spc is hundredths of a point of extra advance per character:
        // 1 spc unit = 127 EMU.
        return natural + style.spcCentiPt() * 127L;
    }

    /** Single-spaced line pitch: {@value #SINGLE_LINE_PITCH} x font size. */
    private static long singleLineHeightEmu(TokenStyle style) {
        return Math.round((style.sizeCentiPt() / 100.0) * SINGLE_LINE_PITCH * 12700.0);
    }

    private static double ascentFraction(TokenStyle style) {
        return style.fontData() != null ? style.fontData().getAscentFraction() : 0.78;
    }

    private static long centipointsToEmu(long centipoints) {
        return centipoints * 12700 / 100;
    }

    private static FontData resolveFontData(String fontFamily, boolean bold, boolean italic,
                                            Map<String, FontData> cache) {
        String key = fontFamily + "|" + bold + "|" + italic;
        if (cache.containsKey(key)) return cache.get(key);
        FontData fd = null;
        try {
            Path fontPath = FontResolver.resolve(fontFamily, bold, italic);
            if (fontPath != null) {
                fd = TrueTypeFontParser.parse(fontPath);
            }
        } catch (IOException ignored) {}
        cache.put(key, fd);
        return fd;
    }
}
