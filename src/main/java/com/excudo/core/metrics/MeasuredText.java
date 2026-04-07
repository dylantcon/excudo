package com.excudo.core.metrics;

import java.util.Collections;
import java.util.List;

/**
 * Result of measuring a TextBody against available dimensions.
 * All values in EMUs.
 */
public final class MeasuredText {

    private final long totalHeightEmu;
    private final long maxLineWidthEmu;
    private final List<ParagraphMeasurement> paragraphs;

    public MeasuredText(long totalHeightEmu, long maxLineWidthEmu,
                        List<ParagraphMeasurement> paragraphs) {
        this.totalHeightEmu = totalHeightEmu;
        this.maxLineWidthEmu = maxLineWidthEmu;
        this.paragraphs = Collections.unmodifiableList(paragraphs);
    }

    public long getTotalHeightEmu() { return totalHeightEmu; }
    public long getMaxLineWidthEmu() { return maxLineWidthEmu; }
    public List<ParagraphMeasurement> getParagraphs() { return paragraphs; }

    /**
     * Check if the measured text overflows the given available height.
     */
    public boolean overflows(long availableHeightEmu) {
        return totalHeightEmu > availableHeightEmu;
    }

    /**
     * Measurement data for a single paragraph.
     */
    public static final class ParagraphMeasurement {
        private final int lineCount;
        private final long heightEmu;
        private final List<Long> lineWidths;

        public ParagraphMeasurement(int lineCount, long heightEmu, List<Long> lineWidths) {
            this.lineCount = lineCount;
            this.heightEmu = heightEmu;
            this.lineWidths = Collections.unmodifiableList(lineWidths);
        }

        public int getLineCount() { return lineCount; }
        public long getHeightEmu() { return heightEmu; }
        public List<Long> getLineWidths() { return lineWidths; }
    }
}
