package com.excudo.view.rendering.surface;

/**
 * Backend-neutral font descriptor. The backend resolves this to its
 * native Font ({@link javafx.scene.text.Font} for the Canvas backend,
 * {@link java.awt.Font} for the AWT backend) and caches internally.
 *
 * {@code sizePx} is already scaled for the render target -- callers
 * multiply by {@link com.excudo.view.rendering.CoordinateMapper#getEffectiveScale()}
 * before constructing the descriptor, matching the convention in the
 * prior {@code TextPainter.resolveFont} path.
 *
 * {@code fallbackFamily} is consulted only when the backend determines
 * the primary family isn't resolvable (e.g. JavaFX substitutes to its
 * system default). May be null.
 */
public record SurfaceFont(
        String family,
        String fallbackFamily,
        Weight weight,
        Posture posture,
        double sizePx) {

    public enum Weight {
        NORMAL,
        BOLD
    }

    public enum Posture {
        REGULAR,
        ITALIC
    }

    /** Convenience: regular-weight, regular-posture, no fallback. */
    public static SurfaceFont of(String family, double sizePx) {
        return new SurfaceFont(family, null, Weight.NORMAL, Posture.REGULAR, sizePx);
    }
}
