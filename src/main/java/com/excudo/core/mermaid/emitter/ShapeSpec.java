package com.excudo.core.mermaid.emitter;

/**
 * Specification for an OOXML shape to be created.
 * Contains all information needed to create a p:sp element.
 */
public record ShapeSpec(
    String nodeId,
    String ooxmlPreset,
    String label,
    long x,
    long y,
    long width,
    long height,
    String fillColor,
    String lineColor
) {}
