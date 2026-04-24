package com.excudo.core.rendering;

import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.Assert.*;

/**
 * Nails down the linear-interpolation invariants of the duotone effect.
 * Black in the source MUST become the shadow color, white MUST become
 * the highlight, and mid-gray MUST land on the exact midpoint. These
 * boundaries are what consumers rely on when a theme supplies a
 * grayscale wallpaper and expects the recolor to preserve its silhouette.
 */
public class DuotoneRecolorTest {

    @Test
    public void blackSourcePixelBecomesShadowColor() throws IOException {
        byte[] src = pngOfSolidGray(0);
        byte[] out = DuotoneRecolor.apply(src, "#102030", "#C0D0E0");
        int argb = firstPixel(out);
        assertEquals(0x10, (argb >> 16) & 0xFF);
        assertEquals(0x20, (argb >> 8)  & 0xFF);
        assertEquals(0x30,  argb        & 0xFF);
    }

    @Test
    public void whiteSourcePixelBecomesHighlightColor() throws IOException {
        byte[] src = pngOfSolidGray(255);
        byte[] out = DuotoneRecolor.apply(src, "#102030", "#C0D0E0");
        int argb = firstPixel(out);
        assertEquals(0xC0, (argb >> 16) & 0xFF);
        assertEquals(0xD0, (argb >> 8)  & 0xFF);
        assertEquals(0xE0,  argb        & 0xFF);
    }

    @Test
    public void midGraySourceLandsNearMidpoint() throws IOException {
        // 128/255 ~= 0.502 -- output is (shadow + 0.502*(highlight-shadow))
        byte[] src = pngOfSolidGray(128);
        byte[] out = DuotoneRecolor.apply(src, "#000000", "#FF0000");
        int argb = firstPixel(out);
        int r = (argb >> 16) & 0xFF;
        // Luminance of pure gray 128: 0.299*128 + 0.587*128 + 0.114*128 = 128
        // Output R = 0 + 0.502 * 255 ≈ 128 (±1 for rounding)
        assertTrue("red channel was " + r, r >= 126 && r <= 130);
        assertEquals(0, (argb >> 8) & 0xFF);
        assertEquals(0,  argb       & 0xFF);
    }

    @Test
    public void sourceAlphaIsPreserved() throws IOException {
        // Build a 1x1 image with 50% alpha.
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, (0x80 << 24) | 0xFFFFFF); // 50% alpha white
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);

        byte[] out = DuotoneRecolor.apply(baos.toByteArray(), "#000000", "#FFFFFF");
        int argb = firstPixel(out);
        assertEquals("alpha must be preserved", 0x80, (argb >>> 24) & 0xFF);
    }

    private static byte[] pngOfSolidGray(int level) throws IOException {
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        int v = level & 0xFF;
        img.setRGB(0, 0, (0xFF << 24) | (v << 16) | (v << 8) | v);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }

    private static int firstPixel(byte[] pngBytes) throws IOException {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(pngBytes));
        return img.getRGB(0, 0);
    }
}
