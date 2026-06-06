package com.excudo.core.model;

/**
 * Reference to an embedded media part in a {@code <p:pic>} or
 * {@code <a:blipFill>}. Held off-shape (snapshot-side) because most
 * shapes don't have one and adding a nullable field to {@link SlideShape}
 * would noise every other consumer.
 *
 * <p>{@code mediaPartName} is the canonical OPC part path of the media
 * (e.g. {@code "ppt/media/image1.png"}) -- the same key used by
 * {@link PPTXDocument#getMediaPart(String)}. On apply, the runner
 * confirms the part exists in the target deck, allocates a new
 * slide-local rId via the {@code RelationshipManager}, and links the
 * new {@code <p:pic>}'s {@code <a:blip r:embed=...>} to that rId.
 * The same media bytes are shared across slides via separate rIds, the
 * shape model OOXML uses natively.
 *
 * <p>{@code mimeType} is optional and only consulted when the media part
 * doesn't already exist in the target deck (cross-deck export path,
 * deferred). Within the same deck the existing content-type registration
 * is reused.
 *
 * <p>{@code srcRect} carries the {@code <a:srcRect>} cropping rectangle
 * (left/top/right/bottom percentages × 1000) as a single 4-tuple string
 * in OOXML's native form, or null when no cropping is set. Round-tripped
 * verbatim through the spec so authoring intent isn't silently rewritten.
 */
public record BlipRef(
        String mediaPartName,
        String mimeType,
        String srcRect) {

    public BlipRef {
        if (mediaPartName == null || mediaPartName.isBlank()) {
            throw new IllegalArgumentException("mediaPartName must be set");
        }
    }

    /** Convenience for the common case: no cropping, no explicit mime. */
    public static BlipRef of(String mediaPartName) {
        return new BlipRef(mediaPartName, null, null);
    }
}
