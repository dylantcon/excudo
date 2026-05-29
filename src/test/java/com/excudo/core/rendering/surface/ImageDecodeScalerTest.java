package com.excudo.core.rendering.surface;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for {@link ImageDecodeScaler#subsampleFactor}: downscale only,
 * never upsample, conservative on the binding dimension.
 */
public class ImageDecodeScalerTest {

    @Test
    public void testSourceWithinCapDecodesFull() {
        // 800x600 into a 1280x720 surface -- no downscale.
        assertEquals(1, ImageDecodeScaler.subsampleFactor(800, 600, 1280, 720));
    }

    @Test
    public void testSourceEqualToCapDecodesFull() {
        assertEquals(1, ImageDecodeScaler.subsampleFactor(1280, 720, 1280, 720));
    }

    @Test
    public void testUniformlyOversizedPhoto() {
        // 6000x4000 into 1280x720: min(6000/1280=4, 4000/720=5) = 4.
        // Decoded ~1500x1000 -- both >= cap.
        assertEquals(4, ImageDecodeScaler.subsampleFactor(6000, 4000, 1280, 720));
    }

    @Test
    public void testDecodedStaysAtOrAboveCap() {
        // The decoded dimensions after applying the factor must never drop
        // below the cap on either axis -- that's the no-quality-loss invariant.
        int srcW = 6000, srcH = 4000, maxW = 1280, maxH = 720;
        int sub = ImageDecodeScaler.subsampleFactor(srcW, srcH, maxW, maxH);
        assertTrue("decoded width below cap", srcW / sub >= maxW);
        assertTrue("decoded height below cap", srcH / sub >= maxH);
    }

    @Test
    public void testPanoramaOversizedOnOneAxisStaysFull() {
        // 6000x500 into 1280x720: height (500) is already under the cap, so
        // any subsampling would under-sample it. Conservative => factor 1.
        assertEquals(1, ImageDecodeScaler.subsampleFactor(6000, 500, 1280, 720));
    }

    @Test
    public void testNonPositiveDimensionsYieldOne() {
        assertEquals(1, ImageDecodeScaler.subsampleFactor(0, 4000, 1280, 720));
        assertEquals(1, ImageDecodeScaler.subsampleFactor(6000, 0, 1280, 720));
        assertEquals(1, ImageDecodeScaler.subsampleFactor(6000, 4000, 0, 720));
        assertEquals(1, ImageDecodeScaler.subsampleFactor(6000, 4000, 1280, 0));
    }

    @Test
    public void testExtremeOversizeScalesHard() {
        // 12800x7200 into 1280x720 -> factor 10.
        assertEquals(10, ImageDecodeScaler.subsampleFactor(12800, 7200, 1280, 720));
    }
}
