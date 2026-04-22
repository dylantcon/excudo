package com.excudo.core.introspection;

import com.excudo.core.model.AnimationBinding;
import com.excudo.core.model.PPTXDocument;
import com.excudo.core.model.ParsedSlideData;
import com.excudo.core.model.ShapeStyle;
import com.excudo.core.orchestration.PPTXOrchestrator;

import java.util.Collections;
import java.util.List;

/**
 * Read-only facade for the four state-reading getters the ScriptSynthesizer
 * needs. Each getter returns typed data (no text formatting) drawn
 * directly from already-parsed state where possible, so the synthesizer
 * can compare against baseline values without re-parsing.
 *
 * <p>The four getters:
 *
 * <ul>
 *   <li>{@link #listAnimations(int)} — animation bindings on a slide,
 *       typed as {@code List<AnimationBinding>}.</li>
 *   <li>{@link #getLayoutBaseline(int)} — the slide's layout + master +
 *       clrMap + background composed into a {@link LayoutBaseline}.</li>
 *   <li>{@code getShapeStyle} and {@code getTransition} — wired through
 *       dedicated readers; see {@link ShapeStyleReader} and
 *       {@link TransitionReader}. (Staged: land alongside the DOM
 *       parsers that feed them.)</li>
 * </ul>
 *
 * <p>All getters return null-safe values. Missing data means "the
 * slide doesn't have it" rather than "error" — the synthesizer
 * distinguishes absent baseline (skip) from empty collections
 * (iterate zero times).
 */
public final class SlideIntrospector {

    private final PPTXOrchestrator orchestrator;
    private final LayoutBaselineBuilder layoutBaselineBuilder;
    private final ShapeStyleReader shapeStyleReader;
    private final TransitionReader transitionReader;

    public SlideIntrospector(PPTXOrchestrator orchestrator) {
        if (orchestrator == null) {
            throw new IllegalArgumentException("orchestrator must not be null");
        }
        this.orchestrator = orchestrator;
        this.layoutBaselineBuilder = new LayoutBaselineBuilder(orchestrator);
        this.shapeStyleReader = new ShapeStyleReader(orchestrator);
        this.transitionReader = new TransitionReader(orchestrator);
    }

    /**
     * Return the current animation bindings on a slide, typed. Draws
     * from the document's cached {@link ParsedSlideData} so the call
     * is free on a steady slide and only re-parses after mutation.
     *
     * @return immutable list of bindings, or empty list if the slide
     *         has no animations or no parsed data is available
     */
    public List<AnimationBinding> listAnimations(int slideNumber) {
        var ctxOpt = orchestrator.getContext();
        if (ctxOpt.isEmpty()) return Collections.emptyList();
        PPTXDocument doc = ctxOpt.get().getDocument();
        if (doc == null) return Collections.emptyList();

        ParsedSlideData parsed = doc.getParsedSlideData(slideNumber,
            (dom, n) -> new com.excudo.xml.parsers.SlideXMLParser().parseSlide(dom, n));
        if (parsed == null) return Collections.emptyList();

        List<AnimationBinding> bindings = parsed.getAnimationBindings();
        return bindings != null ? Collections.unmodifiableList(bindings) : Collections.emptyList();
    }

    /**
     * Build the "what-would-the-slide-look-like-untouched-from-its-layout"
     * snapshot. Returns null when the baseline can't be resolved --
     * caller treats that as "skip this slide", not an error.
     */
    public LayoutBaseline getLayoutBaseline(int slideNumber) {
        return layoutBaselineBuilder.build(slideNumber);
    }

    /**
     * Read the typed {@link ShapeStyle} of the shape identified by
     * (slide, spid). Returns null if the shape can't be located.
     */
    public ShapeStyle getShapeStyle(int slideNumber, int spid) {
        return shapeStyleReader.read(slideNumber, spid);
    }

    /**
     * Resolve the effective {@link TransitionDescriptor} for a slide,
     * walking the slide &rarr; layout &rarr; master inheritance chain.
     * Returns null when the entire chain is transition-free.
     */
    public TransitionDescriptor getTransition(int slideNumber) {
        return transitionReader.read(slideNumber);
    }

    /**
     * Return the slide-space bounding box of a group shape (or any shape
     * resolvable via the registry). The parser already projects grouped
     * children into slide-space coordinates via the ext/chExt transform
     * chain, so this is a cheap lookup -- no re-walk, no re-parse.
     *
     * <p>Returns null if the shape doesn't exist on the slide or isn't
     * a {@link com.excudo.core.model.SlideShape.ShapeType#GROUP}. The
     * null-is-not-a-group branch is intentional: clients that care
     * about group-specific semantics (enumerate children, compute
     * collective bounds) shouldn't accidentally get back a plain-shape
     * geometry thinking it was a group's bounds.
     */
    public com.excudo.core.model.ShapeGeometry getGroupBounds(int slideNumber, int groupSpid) {
        var ctxOpt = orchestrator.getContext();
        if (ctxOpt.isEmpty()) return null;
        var doc = ctxOpt.get().getDocument();
        if (doc == null) return null;
        var parsed = doc.getParsedSlideData(slideNumber,
            (dom, n) -> new com.excudo.xml.parsers.SlideXMLParser().parseSlide(dom, n));
        if (parsed == null) return null;
        var shape = parsed.getShapeRegistry().getShape(groupSpid);
        if (shape == null) return null;
        if (shape.getType() != com.excudo.core.model.SlideShape.ShapeType.GROUP) return null;
        return shape.getGeometry();
    }
}
