package com.excudo.core.rendering.surface;

/**
 * Backend-neutral handle to a decoded raster image. Renderers obtain one
 * from {@link RenderSurface#decodeImage(byte[], String)} and hand it
 * back to {@link RenderSurface#drawImage(SurfaceImage, double, double, double, double)}
 * without inspecting the payload.
 *
 * The {@link #nativeHandle()} escape hatch is Object-typed on purpose:
 * the Canvas backend casts it to {@link javafx.scene.image.Image}, the
 * AWT backend casts it to {@link java.awt.image.BufferedImage}. Renderers
 * never call it -- the cast is confined to the implementing surface.
 */
public interface SurfaceImage {

    /** Decoded width in pixels. */
    int widthPx();

    /** Decoded height in pixels. */
    int heightPx();

    /** Backend-private payload. Renderers treat this as opaque. */
    Object nativeHandle();
}
