package com.excudo.core.rendering.lines;

import com.excudo.core.rendering.surface.StrokeCap;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Pins the preset dash table against PowerPoint's PDF export of the
 * lines-dash-cap-join corpus deck: the truth content streams emit dash
 * arrays that are exact multiples of the line width ({@code [4 3] 0 d}
 * at 1pt, {@code [12 9] 0 d} at 3pt, {@code [24 18] 0 d} at 6pt for
 * "dash", etc). The pre-A5 renderer's table used 2w gaps -- these
 * values are the measured ones.
 */
public class DashPatternsTest {

    private static void assertPattern(String prst, double... expected) {
        assertArrayEquals(prst + " at width 4", expected,
            DashPatterns.pattern(prst, 4.0), 1e-9);
    }

    @Test
    public void calibratedMultiplesOfWidth() {
        assertPattern("dash", 16, 12);
        assertPattern("dashDot", 16, 12, 4, 12);
        assertPattern("dot", 4, 12);
        assertPattern("lgDash", 32, 12);
        assertPattern("lgDashDot", 32, 12, 4, 12);
        assertPattern("lgDashDotDot", 32, 12, 4, 12, 4, 12);
        assertPattern("sysDash", 12, 4);
        assertPattern("sysDashDot", 12, 4, 4, 4);
        assertPattern("sysDashDotDot", 12, 4, 4, 4, 4, 4);
        assertPattern("sysDot", 4, 4);
    }

    @Test
    public void solidAndUnknownAreNull() {
        assertNull(DashPatterns.pattern("solid", 4.0));
        assertNull(DashPatterns.pattern(null, 4.0));
    }

    @Test
    public void scalesLinearlyWithWidth() {
        // 6pt = 8 px: the truth PDF emits [48 18 6 18] for lgDashDot
        // (points); at 8 px width that is [64 24 8 24].
        assertArrayEquals(new double[]{64, 24, 8, 24},
            DashPatterns.pattern("lgDashDot", 8.0), 1e-9);
    }

    /**
     * Non-flat caps extend each dash by w/2 per end, and PowerPoint
     * compensates: every ON entry shrinks by 1w, every OFF entry grows
     * by 1w (pitch preserved). Calibrated from the integration-generalist
     * truth PDF: its 1.5pt cap="rnd" prstDash="dash" layout line strokes
     * with {@code 1 J [4.5 6] 0 d} -- [3w, 4w], not the flat-cap [4w, 3w].
     * Fail-first: verified red 2026-07-14 (cap was ignored, [8, 6] at 2px).
     */
    @Test
    public void roundCapShrinksDashesAndGrowsGaps() {
        assertArrayEquals(new double[]{6, 8},
            DashPatterns.pattern("dash", 2.0, StrokeCap.ROUND), 1e-9);
        assertArrayEquals(new double[]{6, 8},
            DashPatterns.pattern("dash", 2.0, StrokeCap.SQUARE), 1e-9);
        // flat cap keeps the measured base arrays
        assertArrayEquals(new double[]{8, 6},
            DashPatterns.pattern("dash", 2.0, StrokeCap.BUTT), 1e-9);
    }

    @Test
    public void roundCapDotsKeepPitchWithNearZeroOn() {
        // sysDot [1w,1w]: the ON entry would hit zero; it clamps to a
        // sliver and the gap absorbs the rest so the 2w pitch holds.
        double[] p = DashPatterns.pattern("sysDot", 4.0, StrokeCap.ROUND);
        assertEquals(2, p.length);
        assertTrue("on-sliver should be tiny, got " + p[0], p[0] > 0 && p[0] <= 0.1);
        assertEquals(8.0, p[0] + p[1], 1e-9); // pitch preserved
    }
}
