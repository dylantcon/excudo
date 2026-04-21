package com.excudo.view.rendering.surface;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Per-render collector of "requested font was not available, substituted X"
 * events. Thread-local so a headless renderer can begin a render (clearing
 * previous events), have the surface backends report substitutions as they
 * happen, then drain the list at end of render to attach to the response.
 *
 * Why thread-local rather than an injected collector: both render backends
 * (Canvas via JavaFX Font.font, AWT via new java.awt.Font) hit the
 * substitution check inside static helper methods that already thread the
 * SurfaceFont descriptor through. Adding a collector parameter would touch
 * every call site in TextPainter, PictureRenderer, and the shape paths.
 * The tracker is scoped to the current render thread, read-once-drained at
 * the end, so the cross-render bleed risk is minimal.
 */
public final class FontSubstitutionTracker {

    /** Single event: "I asked for X, got Y (from Z)." */
    public record Substitution(String requested, String actual, String backend) {}

    private static final ThreadLocal<Set<Substitution>> EVENTS =
        ThreadLocal.withInitial(LinkedHashSet::new);

    private FontSubstitutionTracker() {}

    /**
     * Begin a new render. Drops any events left from a previous render
     * (belt-and-suspenders; the caller should have drained them, but the
     * render entry is a natural reset point).
     */
    public static void beginRender() {
        EVENTS.get().clear();
    }

    /**
     * Record a substitution. Dedupes on (requested, actual, backend) so a
     * paragraph with the same requested family on every run doesn't emit
     * the same warning 20 times.
     */
    public static void record(String requested, String actual, String backend) {
        if (requested == null || actual == null) return;
        if (requested.equalsIgnoreCase(actual)) return; // no substitution
        EVENTS.get().add(new Substitution(requested, actual, backend));
    }

    /**
     * Drain the collected substitutions for the current thread. Returns
     * an unmodifiable snapshot and clears the thread-local so the next
     * render starts clean.
     */
    public static List<Substitution> drain() {
        Set<Substitution> current = EVENTS.get();
        if (current.isEmpty()) return List.of();
        List<Substitution> snapshot = List.copyOf(current);
        current.clear();
        return Collections.unmodifiableList(snapshot);
    }
}
