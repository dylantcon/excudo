package com.excudo.core.mermaid.emitter;

/**
 * Specification for an OOXML connector to be created.
 * Contains all information needed to create a p:cxnSp element with bound endpoints.
 */
public record ConnectorSpec(
    String startNodeId,
    String endNodeId,
    String connectorType,
    String headEnd,
    String tailEnd,
    String lineStyle,
    String lineColor,
    int startIdx,
    int endIdx,
    long x,
    long y,
    long width,
    long height,
    boolean flipH,
    boolean flipV,
    String label
) {
    /** Constructor with label but no flips (default: no flip). */
    public ConnectorSpec(String startNodeId, String endNodeId, String connectorType,
                         String headEnd, String tailEnd, String lineStyle, String lineColor,
                         int startIdx, int endIdx, long x, long y, long width, long height,
                         String label) {
        this(startNodeId, endNodeId, connectorType, headEnd, tailEnd, lineStyle, lineColor,
             startIdx, endIdx, x, y, width, height, false, false, label);
    }

    /** Constructor without flips or label. */
    public ConnectorSpec(String startNodeId, String endNodeId, String connectorType,
                         String headEnd, String tailEnd, String lineStyle, String lineColor,
                         int startIdx, int endIdx, long x, long y, long width, long height) {
        this(startNodeId, endNodeId, connectorType, headEnd, tailEnd, lineStyle, lineColor,
             startIdx, endIdx, x, y, width, height, false, false, null);
    }
}
