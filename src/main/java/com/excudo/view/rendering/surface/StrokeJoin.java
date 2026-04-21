package com.excudo.view.rendering.surface;

/**
 * Backend-neutral stroke line-join. Mirrors
 * {@link javafx.scene.shape.StrokeLineJoin} / {@link java.awt.BasicStroke}
 * join constants. The backend translates.
 */
public enum StrokeJoin {
    MITER,
    ROUND,
    BEVEL
}
