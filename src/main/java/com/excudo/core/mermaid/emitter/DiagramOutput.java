package com.excudo.core.mermaid.emitter;

import java.util.List;

/**
 * Complete output from the OOXML emitter: shapes, connectors, and label shapes to create.
 */
public record DiagramOutput(
    List<ShapeSpec> shapes,
    List<ConnectorSpec> connectors,
    List<ShapeSpec> labelShapes
) {
    /** Backwards-compatible constructor without labelShapes. */
    public DiagramOutput(List<ShapeSpec> shapes, List<ConnectorSpec> connectors) {
        this(shapes, connectors, List.of());
    }
}
