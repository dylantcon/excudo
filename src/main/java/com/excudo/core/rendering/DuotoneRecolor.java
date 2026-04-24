package com.excudo.core.rendering;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Applies the ECMA-376 {@code a:duotone} effect (§20.1.8.23) to an image.
 * The effect replaces the source's luminance range with a linear
 * interpolation between a shadow color (mapped to black) and a highlight
 * color (mapped to white). Alpha is preserved from the source.
 *
 * <p>Implementation notes:
 * <ul>
 *   <li>Luminance uses the Rec. 601 weights 0.299 R + 0.587 G + 0.114 B.
 *       These weights match what POI and LibreOffice emit; subtle
 *       differences vs Rec. 709 are imperceptible on the grayscale
 *       wallpaper patterns duotone usually targets.</li>
 *   <li>The output PNG is re-encoded so the existing
 *       {@code RenderSurface#decodeImage} path can consume it without
 *       any duotone awareness downstream.</li>
 *   <li>{@link #apply} is stateless and safe to call from any thread;
 *       each call allocates its own {@link BufferedImage}.</li>
 * </ul>
 */
public final class DuotoneRecolor {

    private DuotoneRecolor() {}

    /**
     * Recolor an image using duotone. Returns PNG-encoded bytes of the
     * result so the caller can feed it through a normal image decoder.
     *
     * @param sourceBytes  the original encoded image (JPEG/PNG/etc.)
     * @param shadowHex    '#'-prefixed color replacing black
     * @param highlightHex '#'-prefixed color replacing white
     * @return PNG bytes of the recolored image
     * @throws IOException when the source can't be decoded or the output
     *         can't be encoded
     */
    public static byte[] apply(byte[] sourceBytes, String shadowHex, String highlightHex)
            throws IOException {
        BufferedImage src = ImageIO.read(new ByteArrayInputStream(sourceBytes));
        if (src == null) {
            throw new IOException("ImageIO could not decode background image");
        }

        int shadow = parseHex(shadowHex);
        int highlight = parseHex(highlightHex);
        int sR = (shadow >>> 16) & 0xFF;
        int sG = (shadow >>> 8)  & 0xFF;
        int sB =  shadow         & 0xFF;
        int hR = (highlight >>> 16) & 0xFF;
        int hG = (highlight >>> 8)  & 0xFF;
        int hB =  highlight         & 0xFF;

        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            src.getRGB(0, y, w, 1, row, 0, w);
            for (int x = 0; x < w; x++) {
                int argb = row[x];
                int a = (argb >>> 24) & 0xFF;
                int r = (argb >>> 16) & 0xFF;
                int g = (argb >>> 8)  & 0xFF;
                int b =  argb         & 0xFF;

                // Rec. 601 luma, scaled to [0,1].
                double lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0;
                int outR = clamp((int) Math.round(sR + (hR - sR) * lum));
                int outG = clamp((int) Math.round(sG + (hG - sG) * lum));
                int outB = clamp((int) Math.round(sB + (hB - sB) * lum));

                row[x] = (a << 24) | (outR << 16) | (outG << 8) | outB;
            }
            out.setRGB(0, y, w, 1, row, 0, w);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream(w * h / 4);
        if (!ImageIO.write(out, "png", baos)) {
            throw new IOException("No ImageIO PNG writer available for duotone output");
        }
        return baos.toByteArray();
    }

    private static int parseHex(String hex) {
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        if (h.length() == 8) h = h.substring(2); // strip alpha if ARGB
        return Integer.parseInt(h, 16);
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }
}
