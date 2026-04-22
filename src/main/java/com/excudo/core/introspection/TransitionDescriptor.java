package com.excudo.core.introspection;

import com.excudo.core.model.TransitionType;

import java.util.Objects;

/**
 * Typed snapshot of a slide's transition resolved through the
 * slide &rarr; layout &rarr; master inheritance chain.
 *
 * <p>Records the origin of the resolution so the synthesizer can tell
 * an explicit slide-level override ({@link Source#SLIDE}) from an
 * inherited default ({@link Source#LAYOUT} / {@link Source#MASTER}).
 * That distinction matters: emitting a {@code SetTransitionSpec} for
 * an inherited transition would make the synthesized script include
 * a change that wasn't actually made.
 */
public record TransitionDescriptor(
        TransitionType type,
        String speed,
        Integer durationMs,
        Integer autoAdvanceMs,
        Source source) {

    public enum Source {
        /** Transition lives on the slide itself -- an explicit override. */
        SLIDE,
        /** Transition inherited from the slide's layout. */
        LAYOUT,
        /** Transition inherited from the slide master. */
        MASTER
    }

    public TransitionDescriptor {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(source, "source");
        // speed defaults to "med" when an element is present but
        // omits the attribute (matches PowerPoint's default).
        if (speed == null || speed.isBlank()) speed = "med";
    }
}
