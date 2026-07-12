package com.excudo.core.rendering.surface;

import java.util.List;

/**
 * Backend-neutral paint primitive. Renderers construct and pass these to a
 * {@link RenderSurface}; the backend translates to its native paint type
 * ({@link javafx.scene.paint.Paint} for the Canvas backend, {@link java.awt.Paint}
 * for the AWT backend).
 *
 * Sealed so backends can exhaustively pattern-match without a default arm
 * and without parallel hierarchies drifting over time. ARGB-packed ints on
 * {@link Solid} keep equality comparison cheap (the old code did
 * {@code Color.TRANSPARENT.equals(paint)} which forced JavaFX Color
 * construction on every check).
 */
public sealed interface SurfacePaint
        permits SurfacePaint.Solid, SurfacePaint.LinearGradient, SurfacePaint.RadialGradient,
                SurfacePaint.PatternFill, SurfacePaint.ImageFill, SurfacePaint.Transparent {

    /**
     * Solid fill. {@code argb} is packed 0xAARRGGBB; alpha byte must be
     * set (use {@link #rgb} to default opaque, {@link #rgba} for alpha).
     */
    record Solid(int argb) implements SurfacePaint {

        public static Solid rgb(int r, int g, int b) {
            return new Solid(0xFF000000 | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF));
        }

        public static Solid rgba(int r, int g, int b, double alpha) {
            int a = (int) Math.round(Math.max(0, Math.min(1, alpha)) * 255);
            return new Solid((a << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF));
        }

        /**
         * Accepts "#RRGGBB", "RRGGBB", "#AARRGGBB", or "AARRGGBB". Defaults
         * to opaque alpha if the hex has no alpha component.
         */
        public static Solid fromHex(String hex) {
            if (hex == null) throw new IllegalArgumentException("hex is null");
            String s = hex.startsWith("#") ? hex.substring(1) : hex;
            if (s.length() == 6) {
                return new Solid(0xFF000000 | Integer.parseUnsignedInt(s, 16));
            }
            if (s.length() == 8) {
                return new Solid((int) Long.parseUnsignedLong(s, 16));
            }
            throw new IllegalArgumentException("invalid hex: " + hex);
        }

        /** Returned ARGB with alpha replaced by {@code alpha} (0..1). */
        public Solid withAlpha(double alpha) {
            int a = (int) Math.round(Math.max(0, Math.min(1, alpha)) * 255);
            return new Solid((a << 24) | (argb & 0x00FFFFFF));
        }

        public int alpha() { return (argb >>> 24) & 0xFF; }
        public int red()   { return (argb >>> 16) & 0xFF; }
        public int green() { return (argb >>> 8) & 0xFF; }
        public int blue()  { return argb & 0xFF; }
    }

    /**
     * Linear gradient fill. Stop positions are 0..1, coordinates are
     * normalised 0..1 (matching JavaFX {@code LinearGradient} with
     * {@code proportional=true}). The AWT backend denormalises to
     * absolute coords using the target fill's bounding box, because
     * {@link java.awt.LinearGradientPaint} only accepts absolute coords.
     */
    record LinearGradient(
            double startX, double startY,
            double endX,   double endY,
            List<Stop> stops) implements SurfacePaint {

        public record Stop(double position, Solid color) {}
    }

    /**
     * Radial gradient fill (OOXML {@code a:gradFill/a:path}). Center and
     * focus are normalised 0..1 within the fill's bounding box; stop
     * positions are 0..1 from focus (0) to the gradient boundary (1).
     * Reuses {@link LinearGradient.Stop} so the two gradient kinds cannot
     * drift apart.
     *
     * <p>Geometry selects how the boundary relates to the bbox:
     * <ul>
     *   <li>{@link Geometry#CIRCLE}: circular contours in absolute space;
     *       the radius is the largest distance from the center to a bbox
     *       corner, so the final stop lands exactly on the farthest
     *       corner. This is how PowerPoint's PDF pipeline (the parity
     *       ground truth) renders {@code a:path path="circle"} — measured
     *       as a ShadingType-3 circle centered on the shape with radius =
     *       half-diagonal.</li>
     *   <li>{@link Geometry#ELLIPSE}: contours scale with the bbox; the
     *       final stop is reached at the bbox edges through the center
     *       (corners clamp to the last color). Used to approximate
     *       {@code a:path} {@code "rect"}/{@code "shape"} contour
     *       gradients.</li>
     * </ul>
     */
    record RadialGradient(
            double centerX, double centerY,
            double focusX,  double focusY,
            Geometry geometry,
            List<LinearGradient.Stop> stops) implements SurfacePaint {

        public enum Geometry { CIRCLE, ELLIPSE }
    }

    /**
     * OOXML preset pattern fill ({@code a:pattFill}) as a monochrome 8x8
     * bit tile. Bit {@code (y * 8 + x)} of {@code bits} set means pixel
     * {@code (x, y)} of the tile is {@link #foreground}, else
     * {@link #background}.
     *
     * <p>Backends draw the tile at 8x8 device pixels anchored at the
     * canvas origin: PowerPoint rasterises preset patterns as 6pt tiles
     * (8px at 96 DPI) aligned to the <em>page</em> grid, not the shape
     * (measured from the ground-truth PDFs: tiling-pattern matrices are
     * always multiples of 6pt, independent of shape position).
     */
    record PatternFill(long bits, Solid foreground, Solid background) implements SurfacePaint {

        /**
         * The 8x8 tile as ARGB pixels (row-major), with the slight edge
         * softening PowerPoint's raster pipeline produces: it stores each
         * preset as a 16x16 image drawn at 6pt, and the downsampling
         * bleeds ~8% of each pixel into its orthogonal neighbours
         * (measured from the ground-truth rasters: boundary rows/columns
         * read as 92/8 blends of fg/bg). Both backends build their native
         * texture from this one grid so they cannot drift.
         */
        public int[] tileArgb() {
            final double bleed = 0.08;
            int[] crisp = new int[64];
            for (int i = 0; i < 64; i++) {
                crisp[i] = ((bits >>> i) & 1L) != 0 ? foreground.argb() : background.argb();
            }
            // Separable [bleed, 1-2*bleed, bleed] in x then y, wrapping
            // (the tile repeats, so neighbours wrap around the edges).
            double[][] chan = new double[64][4];
            for (int i = 0; i < 64; i++) {
                int y = i / 8, x = i % 8;
                int left = y * 8 + (x + 7) % 8;
                int right = y * 8 + (x + 1) % 8;
                for (int c = 0; c < 4; c++) {
                    chan[i][c] = (1 - 2 * bleed) * ch(crisp[i], c)
                        + bleed * ch(crisp[left], c) + bleed * ch(crisp[right], c);
                }
            }
            int[] out = new int[64];
            for (int i = 0; i < 64; i++) {
                int y = i / 8, x = i % 8;
                int up = ((y + 7) % 8) * 8 + x;
                int down = ((y + 1) % 8) * 8 + x;
                int argb = 0;
                for (int c = 0; c < 4; c++) {
                    double v = (1 - 2 * bleed) * chan[i][c]
                        + bleed * chan[up][c] + bleed * chan[down][c];
                    argb |= ((int) Math.round(Math.max(0, Math.min(255, v)))) << (24 - c * 8);
                }
                out[i] = argb;
            }
            return out;
        }

        private static int ch(int argb, int c) {
            return (argb >>> (24 - c * 8)) & 0xFF;
        }
    }

    /**
     * Picture fill ({@code a:blipFill} inside {@code p:spPr}). When
     * {@code tile} is false the image is stretched over the fill's
     * bounding box; when true it tiles from the bbox top-left, each tile
     * {@code tileFracW x tileFracH} of the bbox (fractions precomputed
     * from the image's native size and the OOXML sx/sy scaling, so they
     * stay correct at any render zoom).
     */
    record ImageFill(SurfaceImage image, boolean tile,
                     double tileFracW, double tileFracH) implements SurfacePaint {}

    /**
     * Transparent sentinel. Equivalent to {@code Color.TRANSPARENT} but
     * comparable by identity ({@code paint == Transparent.INSTANCE}), so
     * the renderer's "skip fill" check is a single reference equality
     * check instead of a JavaFX Color equality round-trip.
     */
    enum Transparent implements SurfacePaint { INSTANCE }
}
