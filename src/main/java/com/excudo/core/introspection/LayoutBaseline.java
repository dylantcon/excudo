package com.excudo.core.introspection;

import com.excudo.core.model.LayoutInfo;
import com.excudo.core.themes.TextLevelStyle;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * What a slide would look like if nothing had been added or modified
 * since it was created from its layout. The synthesizer uses this as
 * the "left operand" of the slide-state diff: any property of the
 * current slide that differs from its baseline becomes a CommandSpec.
 *
 * <p>Four components, each sourced from an already-parsed part of the
 * document so construction is pure composition (no XML IO):
 *
 * <ul>
 *   <li>{@link #layout()} — the slide's {@link LayoutInfo}, including
 *       placeholder geometries and type flags.</li>
 *   <li>{@link #masterStyles()} — text-level styles (title/body/other,
 *       9 levels each) inherited from the slide master. Null-safe:
 *       {@link Collections#emptyMap()} if the master has no styles
 *       parsed.</li>
 *   <li>{@link #colorMap()} — the clrMap from the slide master (bg1,
 *       tx1, accent1..6, etc. → scheme color slot). Needed to resolve
 *       style references the synthesizer will later compare against.</li>
 *   <li>{@link #backgroundHex()} — resolved background color hex for
 *       the slide. Null if inherited from master without override.</li>
 * </ul>
 *
 * <p>This is v1 of the baseline. In 4.1 it will be folded into a
 * richer {@code SlideState} value type that also represents "the
 * current state" via the same shape. For 4.0 the narrower record is
 * sufficient and lets the synthesizer prototype happen before the
 * wider type lands.
 */
public record LayoutBaseline(
        LayoutInfo layout,
        Map<String, TextLevelStyle[]> masterStyles,
        Map<String, String> colorMap,
        String backgroundHex) {

    public LayoutBaseline {
        Objects.requireNonNull(layout, "layout");
        masterStyles = masterStyles != null ? Map.copyOf(masterStyles) : Collections.emptyMap();
        colorMap = colorMap != null ? Map.copyOf(colorMap) : Collections.emptyMap();
    }
}
