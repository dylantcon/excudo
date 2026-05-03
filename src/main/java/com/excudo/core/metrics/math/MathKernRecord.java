package com.excudo.core.metrics.math;

/**
 * Per-glyph math kern data for one of four glyph corners.
 *
 * <p>The OpenType MATH spec attaches up to four kern arrays to each
 * glyph: one for each corner the layout engine might place a script
 * against (top-right for default-position superscripts, bottom-right
 * for default subscripts, top-left and bottom-left for pre-scripts).
 * Each corner's array maps "correction height in design units" to
 * "horizontal kern in design units" -- so the further a script is
 * shifted vertically, the differently it can tuck into the base
 * glyph's negative space.
 *
 * <p>Example: an italic {@code f} has a deep top-right ink concavity.
 * Its top-right kern record might say "for height 0..200, kern -50;
 * for height 200..400, kern -20; otherwise kern 0." Layout uses this
 * by walking up the height axis until correctionHeights[i] >= the
 * script's vertical placement, then taking kerns[i].
 *
 * <p>A null array means "no kerning information" -- the layout engine
 * falls back to italic correction (or zero for upright glyphs).
 */
public record MathKernRecord(
        MathKern topRight,
        MathKern topLeft,
        MathKern bottomRight,
        MathKern bottomLeft
) {

    /**
     * One corner's kern data: parallel arrays of correction heights
     * and kern values, both in font design units. Heights are sorted
     * ascending; the layout engine looks up by walking the heights
     * array and taking the kern at the index whose correction height
     * is the smallest one greater than or equal to the script's
     * vertical placement.
     *
     * <p>{@code kerns} has exactly one more entry than
     * {@code correctionHeights} (the final entry is the kern used
     * when the script's height exceeds every correction height) --
     * this matches the OpenType spec's array layout.
     */
    public record MathKern(int[] correctionHeights, int[] kerns) {

        /** Look up the kern value to apply for a script placed at the
         *  given height (in design units, measured from the glyph's
         *  baseline). Walks the correction-height boundaries; returns
         *  the kern from the band that contains {@code height}. */
        public int kernAt(int height) {
            for (int i = 0; i < correctionHeights.length; i++) {
                if (height < correctionHeights[i]) return kerns[i];
            }
            return kerns[correctionHeights.length];
        }
    }
}
