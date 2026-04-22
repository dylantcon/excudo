package com.excudo.core.synthesis;

import com.excudo.core.model.PPTXDocument;
import com.excudo.core.orchestration.OrchestrationStateListener;
import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.orchestration.SessionManager;
import com.excudo.core.synthesis.spec.CommandSpec;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Observable, cache-aware wrapper over {@link ScriptSynthesizer}.
 *
 * <p>GUI consumers (the SlideSpec tab, live diagnostics) want to react
 * to synthesized output as slides mutate. A raw on-demand synth call
 * per mutation burns CPU when the user drags a shape for 500ms (~30
 * synths fired for one drag). ReactiveSynthesizer folds three
 * behaviors on top of the pure synthesizer:
 *
 * <ol>
 *   <li><b>Per-slide cache</b> keyed on
 *       {@code (slideNumber, PPTXDocument.slideRevision)}. Mutation
 *       bumps revision, invalidating the entry.</li>
 *   <li><b>Debounce</b>: mutation events collapse into a single synth
 *       after 150ms of quiet time. A burst of shape-modifications
 *       triggers one synth at the tail.</li>
 *   <li><b>Observable result</b>: subscribers registered via
 *       {@link #subscribe} receive the latest
 *       {@link ScriptSynthesizer.Result} whenever it changes. The FX
 *       thread hand-off is the subscriber's responsibility so the
 *       class stays off the JavaFX classpath.</li>
 * </ol>
 *
 * <p>Thread-safe: subscriber list is copy-on-write; the pending
 * per-slide timer references are held in a concurrent map. The
 * synthesizer itself is pure and thread-safe.
 */
public final class ReactiveSynthesizer implements OrchestrationStateListener {

    private static final int DEBOUNCE_MS = 150;

    private final PPTXOrchestrator orchestrator;
    private final SlideStateBuilder builder;
    private final Timer debounceTimer = new Timer("reactive-synthesizer", true);

    /** Per-slide cache. Key is the slide number; value carries the
     *  result and the revision it was synthesized against. */
    private final ConcurrentHashMap<Integer, CachedResult> cache = new ConcurrentHashMap<>();

    /** Pending debounce task per slide -- scheduled on mutation, cancelled
     *  if another mutation arrives before it fires. */
    private final ConcurrentHashMap<Integer, TimerTask> pending = new ConcurrentHashMap<>();

    /** Subscriber callbacks for synth results. */
    private final CopyOnWriteArrayList<Consumer<ReactiveResult>> subscribers = new CopyOnWriteArrayList<>();

    public ReactiveSynthesizer(PPTXOrchestrator orchestrator) {
        if (orchestrator == null) {
            throw new IllegalArgumentException("orchestrator must not be null");
        }
        this.orchestrator = orchestrator;
        this.builder = new SlideStateBuilder(orchestrator);
        SessionManager.getInstance().addStateListener(this);
    }

    /**
     * Register a callback that fires whenever a fresh synthesis result
     * is computed. Fires on the thread that completed the synthesis,
     * not the FX thread -- subscribers that need FX-thread delivery
     * must do their own hand-off.
     */
    public void subscribe(Consumer<ReactiveResult> subscriber) {
        if (subscriber == null) return;
        subscribers.add(subscriber);
    }

    public void unsubscribe(Consumer<ReactiveResult> subscriber) {
        subscribers.remove(subscriber);
    }

    /**
     * Request an immediate synthesis for the given slide, bypassing
     * debounce. Returns the cached result if the slide's revision
     * hasn't advanced since last synthesis; otherwise synthesizes and
     * caches fresh. Does NOT fire subscribers -- callers that use
     * synthesizeNow are asking for a one-off value, not a push.
     */
    public ScriptSynthesizer.Result synthesizeNow(int slideNumber) {
        return synthesizeInternal(slideNumber);
    }

    /** Most-recently computed result for a slide, or null if never
     *  synthesized (or slide doesn't exist). Does not trigger synthesis. */
    public ScriptSynthesizer.Result cached(int slideNumber) {
        CachedResult c = cache.get(slideNumber);
        return c != null ? c.result : null;
    }

    // ========== OrchestrationStateListener ==========

    @Override
    public void onSlideModified(int slideNumber) {
        scheduleDebounced(slideNumber);
    }

    @Override
    public void onPresentationStructureChanged() {
        // New slide insertion or reorder: invalidate everything and
        // let subscribers re-pull as they become interested.
        cache.clear();
        for (TimerTask t : pending.values()) t.cancel();
        pending.clear();
    }

    @Override
    public void onPresentationClosed() {
        cache.clear();
        for (TimerTask t : pending.values()) t.cancel();
        pending.clear();
    }

    // ========== Internals ==========

    private void scheduleDebounced(int slideNumber) {
        TimerTask previous = pending.remove(slideNumber);
        if (previous != null) previous.cancel();
        TimerTask t = new TimerTask() {
            @Override public void run() {
                pending.remove(slideNumber);
                ScriptSynthesizer.Result r = synthesizeInternal(slideNumber);
                if (r == null) return;
                ReactiveResult payload = new ReactiveResult(slideNumber, r);
                for (Consumer<ReactiveResult> s : subscribers) {
                    try { s.accept(payload); }
                    catch (RuntimeException ignored) {
                        // Don't let one bad subscriber block the others.
                    }
                }
            }
        };
        pending.put(slideNumber, t);
        debounceTimer.schedule(t, DEBOUNCE_MS);
    }

    private ScriptSynthesizer.Result synthesizeInternal(int slideNumber) {
        PPTXDocument doc = orchestrator.getContext().isPresent()
            ? orchestrator.getContext().get().getDocument() : null;
        if (doc == null) return null;
        long rev = doc.getSlideRevision(slideNumber);

        CachedResult hit = cache.get(slideNumber);
        if (hit != null && hit.revision == rev) {
            return hit.result;
        }
        SlideState current = builder.current(slideNumber);
        SlideState baseline = builder.baseline(slideNumber);
        if (current == null || baseline == null) return null;
        SlideStateDiff diff = SlideStateDiffer.diff(baseline, current);
        ScriptSynthesizer.Result fresh = ScriptSynthesizer.synthesize(diff, slideNumber);
        cache.put(slideNumber, new CachedResult(fresh, rev));
        return fresh;
    }

    // ========== Value types ==========

    private record CachedResult(ScriptSynthesizer.Result result, long revision) {}

    /** Pushed to subscribers on every recomputation. */
    public record ReactiveResult(int slideNumber, ScriptSynthesizer.Result result) {
        /** Convenience -- topologically sorted specs the GUI renders. */
        public List<CommandSpec> specs() { return result.script().topologicalOrder(); }
    }
}
