package com.excudo.core.color;

import com.excudo.core.themes.ColorModifierUtils;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Pins the exact RGB output of every OOXML color modifier against
 * PowerPoint's own numbers.
 *
 * <p>Where the expected values come from: the parity ground truth is
 * PowerPoint COM &rarr; PDF &rarr; raster. The PDF content streams of
 * {@code parity-corpus/fills-solid-theme/deck.pdf} carry the flattened
 * fill color of every modifier swatch as vector {@code rg} operators
 * (3-decimal floats), i.e. PowerPoint's computed bytes with no
 * anti-aliasing or resampling noise. Each expected constant below was
 * read from that PDF (slide 2 swatch grid; base colors accent1 #4F81BD
 * and accent2 #C0504D from the default python-pptx template theme) and
 * cross-checked against the committed truth raster at
 * {@code test-output/parity/truth/fills-solid-theme/slide-2.png}
 * (raster pixels may sit 1 unit below the PDF value because the PDF
 * stores 3-decimal floats and the rasterizer truncates).
 *
 * <p>Key semantics these values prove (and that this test locks in):
 * <ul>
 *   <li>shade/tint operate in piecewise-linearised sRGB, NOT plain RGB
 *       multiply and NOT HSL. 4F81BD shade 50% is #385D8A; the naive RGB
 *       multiply would give #28415F.</li>
 *   <li>lumMod/lumOff/satMod operate on standard HSL.</li>
 *   <li>modifier chains run unquantised; rounding happens once at the
 *       end (see the shade+satMod composite).</li>
 * </ul>
 */
public class ColorTransformsTest {

    private static final String ACCENT1 = "4F81BD";
    private static final String ACCENT2 = "C0504D";

    private static String apply(String hex, Object... nameValuePairs) {
        return result(hex, nameValuePairs).hex();
    }

    private static ColorTransforms.ResolvedColor result(String hex, Object... nameValuePairs) {
        java.util.ArrayList<ColorTransforms.Modifier> mods = new java.util.ArrayList<>();
        for (int i = 0; i < nameValuePairs.length; i += 2) {
            mods.add(new ColorTransforms.Modifier(
                (String) nameValuePairs[i], (Integer) nameValuePairs[i + 1]));
        }
        return ColorTransforms.apply(hex, mods);
    }

    // ==================== lumMod / lumOff (HSL) ====================

    @Test
    public void lumMod75_accent1() {
        // PDF: rg 0.216 0.376 0.573 -> (55, 96, 146)
        assertEquals("#376092", apply(ACCENT1, "lumMod", 75000));
    }

    @Test
    public void lumMod50_accent1() {
        // PDF: (37, 64, 97)
        assertEquals("#254061", apply(ACCENT1, "lumMod", 50000));
    }

    @Test
    public void lumMod60_lumOff40_accent1() {
        // python-pptx brightness +0.40 pattern. PDF: (149, 179, 215)
        assertEquals("#95B3D7", apply(ACCENT1, "lumMod", 60000, "lumOff", 40000));
    }

    // ==================== shade / tint (linearised sRGB) ====================

    @Test
    public void shade50_accent1_isLinearSpace() {
        // PDF: (56, 93, 138). Plain RGB multiply would yield #28415F --
        // that is the wrong-space result this test exists to reject.
        assertEquals("#385D8A", apply(ACCENT1, "shade", 50000));
    }

    @Test
    public void tint50_accent1_isLinearSpace() {
        // PDF: (194, 205, 225). Truth raster shows C2CDE0 (float-trunc).
        assertEquals("#C2CDE1", apply(ACCENT1, "tint", 50000));
    }

    @Test
    public void shade50_pureRed() {
        // Spec-derived: linear(1.0) * 0.5 -> sRGB-encode(0.5) = 0.7354 -> 188.
        // (A plain RGB multiply would give #800000.)
        assertEquals("#BC0000", apply("FF0000", "shade", 50000));
    }

    // ==================== satMod (HSL) ====================

    @Test
    public void satMod200_accent1() {
        // PDF: (24, 124, 244)
        assertEquals("#187CF4", apply(ACCENT1, "satMod", 200000));
    }

    @Test
    public void satMod50_accent1() {
        // PDF: (107, 132, 162)
        assertEquals("#6B84A2", apply(ACCENT1, "satMod", 50000));
    }

    // ==================== composites (order + no intermediate rounding) ==

    @Test
    public void shade75_satMod150_accent1() {
        // PDF: (44, 111, 190). Only matches when the chain runs on an
        // unquantised color state (byte-rounding between the two steps
        // drifts a channel).
        assertEquals("#2C6FBE", apply(ACCENT1, "shade", 75000, "satMod", 150000));
    }

    @Test
    public void tint75_alpha60_accent2() {
        // PDF: (210, 151, 150) at fill opacity 0.6.
        ColorTransforms.ResolvedColor r = result(ACCENT2, "tint", 75000, "alpha", 60000);
        assertEquals("#D29796", r.hex());
        assertEquals(0.6, r.alpha(), 0.001);
    }

    // ==================== alpha family ====================

    @Test
    public void alphaModifiers() {
        assertEquals(0.5, result("FF0000", "alpha", 50000).alpha(), 1e-9);
        assertEquals(0.25, result("FF0000", "alpha", 50000, "alphaMod", 50000).alpha(), 1e-9);
        assertEquals(0.75, result("FF0000", "alpha", 50000, "alphaOff", 25000).alpha(), 1e-9);
        // alpha modifiers never touch the RGB
        assertEquals("#FF0000", apply("FF0000", "alpha", 25000));
    }

    // ==================== hueMod (HSL) ====================

    @Test
    public void hueMod_halvesHue() {
        // Pure blue (hue 240) * 50% -> hue 120 = pure green.
        assertEquals("#00FF00", apply("0000FF", "hueMod", 50000));
    }

    // ==================== unknown modifiers skipped ====================

    @Test
    public void unknownModifierIsSkipped() {
        assertEquals("#4F81BD", apply(ACCENT1, "comp", 50000));
    }

    // ==================== theme path delegates here ====================

    /**
     * The p:style fillRef path (ThemeManager -> FmtSchemeResolver ->
     * ColorModifierUtils) must produce the same linear-space shade as the
     * direct path -- ONE implementation. Before ColorModifierUtils
     * delegated to ColorTransforms this returned the plain-RGB #28415F.
     */
    @Test
    public void themePathUsesSameShadeMath() {
        ColorModifierUtils.ColorWithAlpha out = ColorModifierUtils.applyModifiers(
            "#" + ACCENT1,
            List.of(new ColorModifierUtils.ColorModifier("shade", 50000)));
        assertEquals("#385D8A", out.hex());
    }

    // ==================== gradient interpolation primitives ============

    @Test
    public void gammaLerpMatchesPowerPointLut() {
        // fills-gradient deck.pdf, page-1 gradient LUT (C00000 -> 1F4E79),
        // 512-sample function: at eased t the LUT reads (141, 57, 89) at
        // ramp position 0.502 whose sine-eased fraction is ~0.4998, and
        // (82, 73, 113) at 0.753 (eased ~0.857).
        int c0 = 0xFFC00000;
        int c1 = 0xFF1F4E79;
        int mid = ColorTransforms.gammaLerpArgb(c0, c1, ColorTransforms.sineEase(0.502));
        assertChannelsClose(0xFF8D3959, mid, 2);
        int threeQ = ColorTransforms.gammaLerpArgb(c0, c1, ColorTransforms.sineEase(0.753));
        assertChannelsClose(0xFF524971, threeQ, 2);
    }

    private static void assertChannelsClose(int expected, int actual, int tol) {
        for (int shift = 0; shift <= 24; shift += 8) {
            int e = (expected >>> shift) & 0xFF;
            int a = (actual >>> shift) & 0xFF;
            if (Math.abs(e - a) > tol) {
                assertEquals(String.format("channel@%d of %08X vs %08X", shift, expected, actual), e, a);
            }
        }
    }
}
