package com.excudo.core.model;

/**
 * Endpoint and routing attributes of an OOXML {@code <p:cxnSp>} that
 * the structural {@link SlideShape}/{@link ShapeGeometry} pair cannot
 * carry on its own. Held off-shape (snapshot-side) because connectors
 * are the only shape kind that needs them, and adding nullable fields
 * to {@code SlideShape} would noise every other consumer.
 *
 * <p>{@code connectorType} is the preset family the writer wrote
 * ({@code "straight"}, {@code "elbow"}, {@code "curved"}, or
 * {@code "line"}) -- it controls which preset geometry the
 * {@link com.excudo.core.commands.mutating.slide.AddConnectorCommand}
 * picks on replay. {@code startSpid/startIdx} and {@code endSpid/endIdx}
 * come from {@code <a:stCxn>} and {@code <a:endCxn>}: the SPID of the
 * shape this end is anchored to and the index of the connection point
 * on that shape (preset-specific). Either side may be null when the
 * connector floats untethered. {@code headEnd}/{@code tailEnd} are the
 * arrowhead types written by the writer; {@code customPath} carries a
 * freeform path expression for connectors authored outside the four
 * presets.
 */
public record ConnectorAttachment(
        String connectorType,
        Integer startSpid,
        Integer startIdx,
        Integer endSpid,
        Integer endIdx,
        String headEnd,
        String tailEnd,
        String customPath) {

    public ConnectorAttachment {
        if (connectorType == null || connectorType.isBlank()) {
            connectorType = "line";
        }
    }
}
