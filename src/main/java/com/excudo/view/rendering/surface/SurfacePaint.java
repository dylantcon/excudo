package com.excudo.view.rendering.surface;

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
        permits SurfacePaint.Solid, SurfacePaint.LinearGradient, SurfacePaint.Transparent {

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
     * Transparent sentinel. Equivalent to {@code Color.TRANSPARENT} but
     * comparable by identity ({@code paint == Transparent.INSTANCE}), so
     * the renderer's "skip fill" check is a single reference equality
     * check instead of a JavaFX Color equality round-trip.
     */
    enum Transparent implements SurfacePaint { INSTANCE }
}
