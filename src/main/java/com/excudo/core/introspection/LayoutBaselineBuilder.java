package com.excudo.core.introspection;

import com.excudo.core.model.LayoutInfo;
import com.excudo.core.model.PPTXDocument;
import com.excudo.core.model.PPTXDocumentParser;
import com.excudo.core.orchestration.PPTXOrchestrator;

/**
 * Composes a {@link LayoutBaseline} for a given slide by pulling
 * already-parsed state from the document and already-resolved master
 * styles / clrMap / background from the orchestrator.
 *
 * <p>Pure composition: no XML IO, no re-parse. If a piece of data
 * isn't already available (e.g., a slide with no resolved layout),
 * returns {@code null} instead of fabricating a synthetic default.
 * The synthesizer treats null as "baseline unavailable — skip this
 * slide" rather than silently emitting against a fabricated baseline.
 */
public final class LayoutBaselineBuilder {

    private final PPTXOrchestrator orchestrator;

    public LayoutBaselineBuilder(PPTXOrchestrator orchestrator) {
        if (orchestrator == null) {
            throw new IllegalArgumentException("orchestrator must not be null");
        }
        this.orchestrator = orchestrator;
    }

    /**
     * Build the baseline for one slide. Returns {@code null} if the
     * slide has no resolvable layout (e.g., the document's parsed
     * state is absent or the slide's layout id was never recorded).
     */
    public LayoutBaseline build(int slideNumber) {
        var ctxOpt = orchestrator.getContext();
        if (ctxOpt.isEmpty()) return null;
        PPTXDocument doc = ctxOpt.get().getDocument();
        if (doc == null) return null;

        PPTXDocumentParser.ParsedPresentationState state = doc.getParsedState();
        if (state == null) return null;

        String layoutId = state.getSlideToLayoutId().get(slideNumber);
        if (layoutId == null) return null;

        LayoutInfo layout = state.getLayouts().get(layoutId);
        if (layout == null) return null;

        return new LayoutBaseline(
            layout,
            orchestrator.getMasterStyles(),
            orchestrator.getClrMap(),
            orchestrator.getBackgroundColorHex(slideNumber));
    }
}
