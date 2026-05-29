package com.excudo.core.rendering.surface;

/**
 * Computes how aggressively to downsample an oversized source image at
 * decode time.
 *
 * <p>Embedded PPTX media is often far larger than the render surface --
 * a 6000x4000 phone photo, a multi-megabyte animated GIF. Decoding such
 * an image to its full raster when it will only be drawn into a rect no
 * bigger than the surface wastes memory (and, on the JavaFX/Monocle
 * path, off-heap native memory that bypasses the JVM heap cap and can
 * crash the host). Capping the decode at the surface dimensions costs no
 * visible fidelity: the output cannot display more pixels than the
 * surface holds.
 */
public final class ImageDecodeScaler {

    private ImageDecodeScaler() {}

    /**
     * Integer subsample factor (decode every Nth pixel along each axis)
     * that shrinks an oversized source while keeping the decoded raster
     * no smaller than {@code (maxW, maxH)}.
     *
     * <p>Returns {@code 1} -- decode at full resolution -- when the source
     * already fits within the cap or any dimension is non-positive. Never
     * upsamples.
     *
     * <p>Conservative by design: it uses the smaller of the two oversize
     * ratios, so neither decoded dimension can drop below its cap. A
     * source oversized on only one axis (e.g. a panorama) is therefore
     * left at full resolution rather than risk under-sampling the other
     * axis. This trades some memory savings for a guarantee of no quality
     * loss on the binding dimension.
     */
    public static int subsampleFactor(int srcW, int srcH, int maxW, int maxH) {
        if (srcW <= 0 || srcH <= 0 || maxW <= 0 || maxH <= 0) return 1;
        int factor = Math.min(srcW / maxW, srcH / maxH);
        return Math.max(1, factor);
    }
}
