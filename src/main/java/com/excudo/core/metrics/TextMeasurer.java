package com.excudo.core.metrics;

import com.excudo.core.model.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Measures TextBody content against available dimensions using exact TTF metrics.
 * Performs greedy word wrap, line height calculation, paragraph spacing, and body insets.
 * All values in EMUs.
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

    private TextMeasurer() {}

    /**
     * Measure a TextBody to determine how much space it needs.
     *
     * @param body the text body to measure
     * @param availableWidthEmu total shape width in EMUs (body insets are subtracted internally)
     * @return measurement results
     */
    public static MeasuredText measure(TextBody body, long availableWidthEmu) {
        if (body == null || body.getParagraphs().isEmpty()) {
            return new MeasuredText(0, 0, List.of());
        }

        BodyProperties props = body.getBodyProperties();
        long leftInset = props.getLeftInset() != null ? props.getLeftInset() : DEFAULT_LEFT_INSET;
        long rightInset = props.getRightInset() != null ? props.getRightInset() : DEFAULT_RIGHT_INSET;
        long topInset = props.getTopInset() != null ? props.getTopInset() : DEFAULT_TOP_INSET;
        long bottomInset = props.getBottomInset() != null ? props.getBottomInset() : DEFAULT_BOTTOM_INSET;

        long textAreaWidth = availableWidthEmu - leftInset - rightInset;
        if (textAreaWidth <= 0) textAreaWidth = 1;

        List<MeasuredText.ParagraphMeasurement> paraMeasurements = new ArrayList<>();
        long totalHeight = topInset + bottomInset;
        long maxLineWidth = 0;

        for (TextParagraph para : body.getParagraphs()) {
            MeasuredText.ParagraphMeasurement pm = measureParagraph(para, textAreaWidth);
            paraMeasurements.add(pm);
            totalHeight += pm.getHeightEmu();
            for (long lw : pm.getLineWidths()) {
                maxLineWidth = Math.max(maxLineWidth, lw);
            }
        }

        return new MeasuredText(totalHeight, maxLineWidth, paraMeasurements);
    }

    private static MeasuredText.ParagraphMeasurement measureParagraph(
            TextParagraph para, long textAreaWidth) {

        if (para.isEmpty()) {
            // Empty paragraph still has one line of height
            long lineHeight = getLineHeight(DEFAULT_FONT_SIZE, DEFAULT_FONT_FAMILY);
            return new MeasuredText.ParagraphMeasurement(1, lineHeight, List.of(0L));
        }

        // Determine effective font for this paragraph (from first run)
        int fontSize = DEFAULT_FONT_SIZE;
        String fontFamily = DEFAULT_FONT_FAMILY;
        for (TextRun run : para.getRuns()) {
            if (run.getFontSize() != null) fontSize = run.getFontSize();
            if (run.getFontFamily() != null) fontFamily = run.getFontFamily();
            break;
        }

        // Bullet/margin indent reduces available width
        long indentOffset = 0;
        if (para.getMarginLeft() != null) {
            indentOffset = para.getMarginLeft();
            if (para.getIndent() != null) {
                indentOffset += para.getIndent(); // indent is typically negative for hanging
            }
        }
        long effectiveWidth = textAreaWidth - Math.max(0, indentOffset);
        if (effectiveWidth <= 0) effectiveWidth = 1;

        // Concatenate all run text for word wrapping
        StringBuilder fullText = new StringBuilder();
        for (TextRun run : para.getRuns()) {
            fullText.append(run.getText());
        }

        FontData fontData = resolveFontData(fontFamily);
        long lineHeight = fontData != null
                ? fontData.calculateLineHeightEmu(fontSize)
                : getLineHeight(fontSize, fontFamily);

        // Apply line spacing
        int lineSpacingVal = para.getLineSpacing() != null ? para.getLineSpacing() : DEFAULT_LINE_SPACING;
        long scaledLineHeight;
        if (lineSpacingVal <= 400000) {
            // Percentage mode: value/100000 * lineHeight
            scaledLineHeight = lineHeight * lineSpacingVal / 100000;
        } else {
            // Absolute EMU mode
            scaledLineHeight = lineSpacingVal;
        }

        // Word wrap
        List<Long> lineWidths = wrapText(fullText.toString(), fontData, fontSize, effectiveWidth);
        int lineCount = lineWidths.size();
        if (lineCount == 0) lineCount = 1;

        long paragraphHeight = scaledLineHeight * lineCount;

        // Space before/after (in hundredths of a point -> EMUs)
        if (para.getSpaceBefore() != null) {
            paragraphHeight += (long) para.getSpaceBefore() * 12700 / 100;
        }
        if (para.getSpaceAfter() != null) {
            paragraphHeight += (long) para.getSpaceAfter() * 12700 / 100;
        }

        return new MeasuredText.ParagraphMeasurement(lineCount, paragraphHeight, lineWidths);
    }

    /**
     * Greedy word wrap. Returns per-line widths in EMUs.
     */
    private static List<Long> wrapText(String text, FontData fontData, int fontSize, long maxWidth) {
        if (text == null || text.isEmpty()) return List.of(0L);

        List<Long> lineWidths = new ArrayList<>();
        String[] words = text.split("(?<=\\s)|(?=\\s)"); // split preserving spaces as tokens

        long currentLineWidth = 0;

        for (String word : words) {
            long wordWidth = measureWord(word, fontData, fontSize);

            if (currentLineWidth + wordWidth > maxWidth && currentLineWidth > 0) {
                // Wrap to next line
                lineWidths.add(currentLineWidth);
                currentLineWidth = 0;

                // Skip leading whitespace on new line
                if (word.isBlank()) continue;
            }

            // If a single word exceeds line width, break at character boundary
            if (wordWidth > maxWidth && currentLineWidth == 0) {
                currentLineWidth = breakLongWord(word, fontData, fontSize, maxWidth, lineWidths);
                continue;
            }

            currentLineWidth += wordWidth;
        }

        if (currentLineWidth > 0 || lineWidths.isEmpty()) {
            lineWidths.add(currentLineWidth);
        }

        return lineWidths;
    }

    /**
     * Break a word that exceeds the line width at character boundaries.
     * Returns the width remaining on the last (incomplete) line.
     */
    private static long breakLongWord(String word, FontData fontData, int fontSize,
                                       long maxWidth, List<Long> lineWidths) {
        long lineWidth = 0;
        for (int i = 0; i < word.length(); i++) {
            long charWidth = measureChar(word.charAt(i), fontData, fontSize);
            if (lineWidth + charWidth > maxWidth && lineWidth > 0) {
                lineWidths.add(lineWidth);
                lineWidth = 0;
            }
            lineWidth += charWidth;
        }
        return lineWidth;
    }

    private static long measureWord(String word, FontData fontData, int fontSize) {
        if (fontData != null) {
            return fontData.measureStringWidthEmu(word, fontSize);
        }
        // Fallback: approximate at 0.5 * fontSize per character
        double ptPerChar = (fontSize / 100.0) * 0.5;
        return Math.round(ptPerChar * word.length() * 12700.0);
    }

    private static long measureChar(char ch, FontData fontData, int fontSize) {
        if (fontData != null) {
            double fontSizePt = fontSize / 100.0;
            int advWidth = fontData.getCharAdvanceWidth(ch);
            return Math.round((double) advWidth * fontSizePt / fontData.getUnitsPerEm() * 12700.0);
        }
        return Math.round((fontSize / 100.0) * 0.5 * 12700.0);
    }

    private static long getLineHeight(int fontSize, String fontFamily) {
        FontData fontData = resolveFontData(fontFamily);
        if (fontData != null) {
            return fontData.calculateLineHeightEmu(fontSize);
        }
        // Fallback: approximate line height as 1.2 * font size
        return Math.round((fontSize / 100.0) * 1.2 * 12700.0);
    }

    private static FontData resolveFontData(String fontFamily) {
        try {
            Path fontPath = FontResolver.resolve(fontFamily, false, false);
            if (fontPath != null) {
                return TrueTypeFontParser.parse(fontPath);
            }
        } catch (IOException ignored) {}
        return null;
    }
}
