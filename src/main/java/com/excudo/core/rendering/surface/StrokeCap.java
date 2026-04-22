package com.excudo.core.rendering.surface;

/**
 * Backend-neutral stroke line-cap. Mirrors
 * {@link javafx.scene.shape.StrokeLineCap} / {@link java.awt.BasicStroke}
 * cap constants. The backend translates.
 */
public enum StrokeCap {
    BUTT,
    ROUND,
    SQUARE
}
