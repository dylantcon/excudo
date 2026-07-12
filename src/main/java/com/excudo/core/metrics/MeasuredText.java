package com.excudo.core.metrics;

import com.excudo.core.model.TextRun;

import java.util.Collections;
import java.util.List;

/**
 * Result of measuring a TextBody against available dimensions.
 * All values in EMUs.
 *
 * <p>Since the A3 text-parity work this is the <b>single wrap
 * authority</b>: it carries not just per-paragraph heights but the full
 * line layout — which display-text segment lands on which line, at what
 * x-offset, in which effective font. {@code TextPainter} paints exactly
 * these lines; it never re-wraps, so the TTF-metric measurement engine
 * and the render-surface width engine cannot disagree about line breaks,
 * vertical centering, or alignment offsets.
 */
public final class MeasuredText {

    private final long totalHeightEmu;
    private final long maxLineWidthEmu;
    private final long textHeightEmu;
    private final List<ParagraphMeasurement> paragraphs;

    public MeasuredText(long totalHeightEmu, long maxLineWidthEmu,
                        List<ParagraphMeasurement> paragraphs) {
        this(totalHeightEmu, totalHeightEmu, maxLineWidthEmu, paragraphs);
    }

    public MeasuredText(long totalHeightEmu, long textHeightEmu, long maxLineWidthEmu,
                        List<ParagraphMeasurement> paragraphs) {
        this.totalHeightEmu = totalHeightEmu;
        this.textHeightEmu = textHeightEmu;
        this.maxLineWidthEmu = maxLineWidthEmu;
        this.paragraphs = Collections.unmodifiableList(paragraphs);
    }

    /** Text height including top+bottom body insets. */
    public long getTotalHeightEmu() { return totalHeightEmu; }

    /**
     * Height of the text block alone (sum of paragraph heights), without
     * body insets. This is the quantity vertical anchoring (anchor="ctr"
     * / "b") centers within the inset text area; using the inset-inclusive
     * total shifted centered text up by half the vertical insets.
     */
    public long getTextHeightEmu() { return textHeightEmu; }

    public long getMaxLineWidthEmu() { return maxLineWidthEmu; }
    public List<ParagraphMeasurement> getParagraphs() { return paragraphs; }

    /**
     * Check if the measured text overflows the given available height.
     */
    public boolean overflows(long availableHeightEmu) {
        return totalHeightEmu > availableHeightEmu;
    }

    /**
     * One positioned run fragment on a line.
     *
     * @param run             source model run (color, underline, strike,
     *                        highlight and other paint-only styling)
     * @param text            display text: capitalization transforms
     *                        (cap="all", the uppercased small-caps
     *                        sub-segments) already applied
     * @param fontFamily      resolved font family
     * @param fontSizeCentiPt effective font size in hundredths of a point —
     *                        includes autofit fontScale, small-caps 80%
     *                        reduction and super/subscript scaling
     * @param bold            effective bold
     * @param italic          effective italic
     * @param xEmu            offset of the segment's left edge from the
     *                        line's text origin
     * @param widthEmu        advance width (includes character tracking)
     * @param baselineShiftEmu vertical shift from the line baseline;
     *                        positive = down (subscript), negative = up
     * @param charAdvancesEmu per-character advance widths (tracking
     *                        included) when the run carries non-zero spc,
     *                        so the painter can place each glyph at its
     *                        measured position; null when the font's
     *                        natural advances apply
     */
    public record Segment(TextRun run, String text, String fontFamily, int fontSizeCentiPt,
                          boolean bold, boolean italic, long xEmu, long widthEmu,
                          long baselineShiftEmu, long[] charAdvancesEmu) {}

    /**
     * One laid-out line.
     *
     * @param segments    positioned fragments, left to right
     * @param widthEmu    ink width (trailing whitespace excluded) — the
     *                    quantity alignment and justification offsets use
     * @param advanceEmu  vertical space this line consumes (line height x
     *                    effective line-spacing factor)
     * @param baselineEmu baseline offset from the line's top
     * @param forcedBreak true when the line ends at an explicit
     *                    {@code <a:br/>} — justification skips such lines
     */
    public record Line(List<Segment> segments, long widthEmu, long advanceEmu,
                       long baselineEmu, boolean forcedBreak) {}

    /**
     * Measurement data for a single paragraph.
     */
    public static final class ParagraphMeasurement {
        private final int lineCount;
        private final long heightEmu;
        private final List<Long> lineWidths;
        private final List<Line> lines;
        private final long spaceBeforeEmu;
        private final long spaceAfterEmu;
        private final long marginLeftEmu;
        private final long indentEmu;
        private final long wrapWidthEmu;
        private final String alignment;

        public ParagraphMeasurement(int lineCount, long heightEmu, List<Long> lineWidths) {
            this(lineCount, heightEmu, lineWidths, List.of(), 0, 0, 0, 0, 0, "l");
        }

        public ParagraphMeasurement(int lineCount, long heightEmu, List<Long> lineWidths,
                                    List<Line> lines, long spaceBeforeEmu, long spaceAfterEmu,
                                    long marginLeftEmu, long indentEmu, long wrapWidthEmu,
                                    String alignment) {
            this.lineCount = lineCount;
            this.heightEmu = heightEmu;
            this.lineWidths = Collections.unmodifiableList(lineWidths);
            this.lines = Collections.unmodifiableList(lines);
            this.spaceBeforeEmu = spaceBeforeEmu;
            this.spaceAfterEmu = spaceAfterEmu;
            this.marginLeftEmu = marginLeftEmu;
            this.indentEmu = indentEmu;
            this.wrapWidthEmu = wrapWidthEmu;
            this.alignment = alignment;
        }

        public int getLineCount() { return lineCount; }
        /** Total paragraph height: space-before + line advances + space-after. */
        public long getHeightEmu() { return heightEmu; }
        public List<Long> getLineWidths() { return lineWidths; }
        /** Full line layout. Empty only for legacy-constructed measurements. */
        public List<Line> getLines() { return lines; }
        public long getSpaceBeforeEmu() { return spaceBeforeEmu; }
        public long getSpaceAfterEmu() { return spaceAfterEmu; }
        /** Resolved left margin (paragraph pPr or inherited level style). */
        public long getMarginLeftEmu() { return marginLeftEmu; }
        /** Resolved first-line indent relative to marL (negative = hanging). */
        public long getIndentEmu() { return indentEmu; }
        /** The width lines were wrapped against — alignment distributes within this. */
        public long getWrapWidthEmu() { return wrapWidthEmu; }
        /** Resolved alignment: "l", "ctr", "r", or "just". */
        public String getAlignment() { return alignment; }
    }
}
