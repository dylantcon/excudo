package com.excudo.test.utils;

import com.excudo.core.commands.CommandInvoker;
import com.excudo.core.commands.mutating.slide.RunSlideScriptCommand;
import com.excudo.core.model.AnimationBinding;
import com.excudo.core.model.ConnectorAttachment;
import com.excudo.core.orchestration.PPTXOrchestratorImpl;
import com.excudo.core.synthesis.ScriptSynthesizer;
import com.excudo.core.synthesis.ShapeSnapshot;
import com.excudo.core.synthesis.SlideState;
import com.excudo.core.synthesis.SlideStateBuilder;
import com.excudo.core.synthesis.SlideStateDiff;
import com.excudo.core.synthesis.SlideStateDiffer;
import com.excudo.core.synthesis.spec.CommandSpec;
import com.excudo.core.synthesis.spec.CommandSpecJson;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Strict end-to-end round-trip helper for the synthesis stack: run the
 * full GUI-equivalent apply (synth -> JSON -> RunSlideScriptCommand on
 * a target slide), then renormalize the target's snapshot back into
 * source-SPID space via the runner's spidMap + layout-placeholder
 * positional pairing, and assert the two {@link SlideState}s are
 * value-equal in the fields the synthesis vocabulary is supposed to
 * carry: shape geometries, styles, text bodies, connector attachments,
 * blip refs, group memberships, animations, transitions.
 *
 * <p>The bar is "full SlideState equality after SPID normalization"
 * (decided with the user, plan: happy-wandering-octopus.md). Tighter
 * than the legacy pragmatic helper (shape count + type + geometry +
 * transition only); catches text-formatting drift, group-membership
 * bugs, animation-param divergence, etc.
 *
 * <p>Caller must ensure source and target use the SAME layout so
 * baseline placeholders pair positionally. Cross-layout round-trip is
 * out of scope here (tracked under P3 cross-layout retarget).
 */
public final class StrictRoundTrip {

    private StrictRoundTrip() {}

    /**
     * Run the full GUI apply flow from {@code sourceSlide} to
     * {@code targetSlide} and assert the result is structurally
     * equivalent. Returns the {@link RunSlideScriptCommand} so callers
     * can inspect the spidMap / appliedSpecCount when needed.
     */
    public static RunSlideScriptCommand assertStrictRoundTrip(
            PPTXOrchestratorImpl orch, int sourceSlide, int targetSlide) {
        // Take source snapshot BEFORE applying so subsequent reparses
        // (target slide's apply mutates the deck and bumps the revision
        // counter, invalidating per-slide caches but not the snapshot
        // record we already extracted).
        SlideStateBuilder builder = new SlideStateBuilder(orch);
        SlideState source = builder.current(sourceSlide);
        assertNotNull("Source slide must exist: " + sourceSlide, source);

        // Synthesize + JSON + apply.
        SlideStateDiff diff = SlideStateDiffer.diff(
            builder.baseline(sourceSlide), source);
        ScriptSynthesizer.Result synth = ScriptSynthesizer.synthesize(diff, sourceSlide);
        List<CommandSpec> specs = synth.script().topologicalOrder();
        String json = CommandSpecJson.toJsonArray(specs);
        RunSlideScriptCommand cmd = new RunSlideScriptCommand(targetSlide, json, orch, null);
        new CommandInvoker().executeCommand(cmd);
        assertTrue("Script run must succeed: " + cmd.getDescription(), cmd.isExecuted());

        SlideState target = builder.current(targetSlide);
        assertNotNull("Target slide must exist after apply: " + targetSlide, target);

        Map<Integer, Integer> sourceToTarget = buildFullSpidMap(source, target, cmd.getSpidMap());
        SlideState renormalized = renormalize(target, sourceToTarget, sourceSlide);

        // Compare in pieces for actionable diffs: count first, then per-shape,
        // then animations, transition. The catch-everything equals at the
        // end is the strict bar.
        assertEquals("Shape count must match after strict round-trip",
            source.shapes().size(), renormalized.shapes().size());
        for (Map.Entry<Integer, ShapeSnapshot> e : source.shapes().entrySet()) {
            ShapeSnapshot got = renormalized.shapes().get(e.getKey());
            assertNotNull("Missing shape after round-trip: SPID " + e.getKey()
                + " (source=" + e.getValue() + ")", got);
            assertEquals("Shape SPID " + e.getKey() + " must value-equal source after "
                + "renormalization", e.getValue(), got);
        }

        assertEquals("Animation list must match after strict round-trip",
            source.animations(), renormalized.animations());
        assertEquals("Transition must match",
            source.transition(), renormalized.transition());
        return cmd;
    }

    // ===================================================================
    // SPID renormalization
    // ===================================================================

    /**
     * Build a full source-SPID -> target-SPID map.
     *
     * <p>The runner's spidMap covers user-added shapes (those that came
     * through Add*Spec with a sourceSpidHint). Layout-baseline
     * placeholders aren't in there because the synthesizer doesn't
     * re-emit them (they're already on the target via the layout).
     * They still pair across the two slides because the same layout
     * projects the same placeholders, and the writer allocates target
     * SPIDs in document order. Pair by (registry-order, type).
     */
    private static Map<Integer, Integer> buildFullSpidMap(
            SlideState source, SlideState target,
            Map<Integer, Integer> runnerSpidMap) {
        Map<Integer, Integer> map = new HashMap<>(runnerSpidMap);

        // Placeholder shapes that aren't in the runner map: pair by
        // (type, name) so we don't accidentally line up the wrong
        // placeholder when two share a type.
        var sourceUnmapped = new TreeMap<Integer, ShapeSnapshot>();
        for (var e : source.shapes().entrySet()) {
            if (!map.containsKey(e.getKey())) sourceUnmapped.put(e.getKey(), e.getValue());
        }
        var targetUnmapped = new TreeMap<Integer, ShapeSnapshot>();
        Map<Integer, Integer> reverseRunner = new HashMap<>();
        for (var e : runnerSpidMap.entrySet()) reverseRunner.put(e.getValue(), e.getKey());
        for (var e : target.shapes().entrySet()) {
            if (!reverseRunner.containsKey(e.getKey())) targetUnmapped.put(e.getKey(), e.getValue());
        }

        // For each source-unmapped placeholder, find the target with
        // matching (type, name) -- unique on a same-layout pair.
        for (var srcEntry : sourceUnmapped.entrySet()) {
            ShapeSnapshot src = srcEntry.getValue();
            Integer match = null;
            for (var tgtEntry : targetUnmapped.entrySet()) {
                ShapeSnapshot tgt = tgtEntry.getValue();
                if (tgt.type() == src.type()
                        && java.util.Objects.equals(tgt.name(), src.name())) {
                    match = tgtEntry.getKey();
                    break;
                }
            }
            if (match != null) {
                map.put(srcEntry.getKey(), match);
                targetUnmapped.remove(match);
            }
        }

        return map;
    }

    /**
     * Rewrite every SPID-bearing field on the target snapshot so it
     * matches the source's SPID space. After this pass, the
     * snapshot's {@code spid}, {@code parentGroupSpid}, animation
     * targets, and connector endpoints all line up with source.
     */
    private static SlideState renormalize(SlideState target,
            Map<Integer, Integer> sourceToTarget, int sourceSlideNumber) {
        Map<Integer, Integer> targetToSource = new HashMap<>();
        for (var e : sourceToTarget.entrySet()) targetToSource.put(e.getValue(), e.getKey());

        Map<Integer, ShapeSnapshot> rewritten = new java.util.LinkedHashMap<>();
        for (ShapeSnapshot s : target.shapes().values()) {
            Integer newSpid = targetToSource.getOrDefault(s.spid(), s.spid());
            Integer newParent = s.parentGroupSpid() == null ? null
                : targetToSource.getOrDefault(s.parentGroupSpid(), s.parentGroupSpid());
            ConnectorAttachment newConn = s.connectorAttachment() == null ? null
                : new ConnectorAttachment(
                    s.connectorAttachment().connectorType(),
                    remapNullable(s.connectorAttachment().startSpid(), targetToSource),
                    s.connectorAttachment().startIdx(),
                    remapNullable(s.connectorAttachment().endSpid(), targetToSource),
                    s.connectorAttachment().endIdx(),
                    s.connectorAttachment().headEnd(),
                    s.connectorAttachment().tailEnd(),
                    s.connectorAttachment().customPath());
            rewritten.put(newSpid, new ShapeSnapshot(
                newSpid, s.name(), s.type(), s.geometry(), s.style(),
                s.textBody(), s.isTextBox(), newParent, newConn, s.blipRef()));
        }

        // AnimationBinding doesn't have a public copy-with-target API
        // but the targetSpid is the only SPID-bearing field on it. Use
        // the builder pattern.
        List<AnimationBinding> rewrittenAnims = new java.util.ArrayList<>();
        for (AnimationBinding b : target.animations()) {
            Integer newTarget = targetToSource.getOrDefault(b.getTargetSpid(), b.getTargetSpid());
            if (newTarget == b.getTargetSpid()) {
                rewrittenAnims.add(b);
            } else {
                rewrittenAnims.add(rebindWithTarget(b, newTarget));
            }
        }

        return new SlideState(sourceSlideNumber, rewritten, rewrittenAnims,
            target.transition(), target.baseline());
    }

    private static Integer remapNullable(Integer v, Map<Integer, Integer> map) {
        if (v == null) return null;
        return map.getOrDefault(v, v);
    }

    private static AnimationBinding rebindWithTarget(AnimationBinding b, int newTarget) {
        AnimationBinding.Builder bld = AnimationBinding.builder()
            .target(newTarget)
            .type(b.getAnimationType())
            .clickTrigger(b.getClickTrigger());
        if (b.isEntranceAnimation())      bld.entrance();
        else if (b.isExitAnimation())     bld.exit();
        else if (b.isEmphasisAnimation()) bld.emphasis();
        if (b.getDuration() != null) {
            try { bld.durationMs(Integer.parseInt(b.getDuration())); }
            catch (NumberFormatException ignored) { bld.duration(b.getDuration()); }
        }
        if (b.getDelay() != null && !b.getDelay().isBlank()) bld.delay(b.getDelay());
        if (b.getAnimationGroup() != null && !b.getAnimationGroup().isBlank())
            bld.animationGroup(b.getAnimationGroup());
        if (b.getMotionPath() != null) bld.motionPath(b.getMotionPath());
        if (b.getAcceleration() != 0 || b.getDeceleration() != 0)
            bld.easing(b.getAcceleration(), b.getDeceleration());
        if (b.getParagraphStart() != null && b.getParagraphEnd() != null)
            bld.paragraphRange(b.getParagraphStart(), b.getParagraphEnd());
        if (b.getEffectParams() != null) {
            for (var e : b.getEffectParams().entrySet())
                bld.effectParam(e.getKey(), e.getValue());
        }
        return bld.build();
    }
}
