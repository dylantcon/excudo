package com.excudo.core.mermaid.emitter;

import com.excudo.core.mermaid.ast.EdgeArrowType;

/**
 * Maps diagram edge arrow types to OOXML connector properties.
 */
public class EdgeTypeMapper {

    private EdgeTypeMapper() {}

    /**
     * Result of mapping an edge type to connector properties.
     */
    public record ConnectorStyle(String connectorType, String headEnd, String tailEnd, String lineStyle) {}

    public static ConnectorStyle toConnectorStyle(EdgeArrowType type, boolean bidirectional) {
        return switch (type) {
            case ARROW -> bidirectional
                ? new ConnectorStyle("straight", "triangle", "triangle", "solid")
                : new ConnectorStyle("straight", "none", "triangle", "solid");
            case OPEN -> new ConnectorStyle("straight", "none", "none", "solid");
            case DOTTED_ARROW -> new ConnectorStyle("straight", "none", "triangle", "dash");
            case DOTTED -> new ConnectorStyle("straight", "none", "none", "dash");
            case THICK_ARROW -> new ConnectorStyle("straight", "none", "triangle", "solid");
            case THICK -> new ConnectorStyle("straight", "none", "none", "solid");
            case CROSS -> new ConnectorStyle("straight", "none", "diamond", "solid");
            case CIRCLE_END -> new ConnectorStyle("straight", "none", "oval", "solid");
        };
    }
}
