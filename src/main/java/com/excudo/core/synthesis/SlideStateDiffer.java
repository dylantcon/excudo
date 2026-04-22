package com.excudo.core.synthesis;

import com.excudo.core.model.AnimationBinding;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure function: given two {@link SlideState}s (baseline and current),
 * produce the typed {@link SlideStateDiff} describing what changed.
 *
 * <p>No IO, no orchestrator access, no logging. All state comparisons
 * go through {@link StateEquality} so that a single semantic change
 * to equality behavior propagates consistently. Deterministic output
 * ordering: shape-related deltas are sorted by SPID so two runs over
 * the same input produce byte-identical JSON downstream.
 */
public final class SlideStateDiffer {

    private SlideStateDiffer() {}

    public static SlideStateDiff diff(SlideState baseline, SlideState current) {
        if (baseline == null || current == null) {
            throw new IllegalArgumentException("baseline and current must both be non-null");
        }

        List<SlideStateDiff.ShapeAdded> added = new ArrayList<>();
        List<SlideStateDiff.ShapeRemoved> removed = new ArrayList<>();
        List<SlideStateDiff.ShapeModified> modified = new ArrayList<>();
        diffShapes(baseline, current, added, removed, modified);

        List<SlideStateDiff.AnimationAdded> animsAdded = new ArrayList<>();
        List<SlideStateDiff.AnimationRemoved> animsRemoved = new ArrayList<>();
        List<SlideStateDiff.AnimationTimingChanged> animsTimingChanged = new ArrayList<>();
        diffAnimations(baseline, current, animsAdded, animsRemoved, animsTimingChanged);

        SlideStateDiff.TransitionChange tc = diffTransition(baseline, current);

        List<SlideStateDiff.GroupDelta> groupDeltas = diffGroups(baseline, current);

        return new SlideStateDiff(added, removed, modified,
            animsAdded, animsRemoved, animsTimingChanged, tc, groupDeltas);
    }

    // ========== Shape diffing ==========

    private static void diffShapes(SlideState baseline, SlideState current,
            List<SlideStateDiff.ShapeAdded> added,
            List<SlideStateDiff.ShapeRemoved> removed,
            List<SlideStateDiff.ShapeModified> modified) {
        Map<Integer, ShapeSnapshot> base = baseline.shapes();
        Map<Integer, ShapeSnapshot> curr = current.shapes();

        // Added: in current, not in baseline. Sorted by SPID for
        // deterministic output.
        List<Integer> addedSpids = new ArrayList<>();
        for (int spid : curr.keySet()) if (!base.containsKey(spid)) addedSpids.add(spid);
        addedSpids.sort(Integer::compare);
        for (int spid : addedSpids) {
            added.add(new SlideStateDiff.ShapeAdded(curr.get(spid)));
        }

        // Removed: in baseline, not in current.
        List<Integer> removedSpids = new ArrayList<>();
        for (int spid : base.keySet()) if (!curr.containsKey(spid)) removedSpids.add(spid);
        removedSpids.sort(Integer::compare);
        for (int spid : removedSpids) {
            removed.add(new SlideStateDiff.ShapeRemoved(base.get(spid)));
        }

        // Modified: in both, differ by at least one field.
        List<Integer> commonSpids = new ArrayList<>();
        for (int spid : base.keySet()) if (curr.containsKey(spid)) commonSpids.add(spid);
        commonSpids.sort(Integer::compare);
        for (int spid : commonSpids) {
            ShapeSnapshot b = base.get(spid);
            ShapeSnapshot c = curr.get(spid);
            Set<SlideStateDiff.ShapeField> changed = fieldsDiffering(b, c);
            if (!changed.isEmpty()) {
                modified.add(new SlideStateDiff.ShapeModified(b, c, changed));
            }
        }
    }

    private static Set<SlideStateDiff.ShapeField> fieldsDiffering(
            ShapeSnapshot b, ShapeSnapshot c) {
        Set<SlideStateDiff.ShapeField> changed = EnumSet.noneOf(SlideStateDiff.ShapeField.class);

        // Position / size / rotation split onto separate specs so the
        // synthesizer can emit only the one that actually changed.
        if (!StateEquality.samePosition(b.geometry(), c.geometry())) {
            changed.add(SlideStateDiff.ShapeField.GEOMETRY_POSITION);
        }
        if (!StateEquality.sameSize(b.geometry(), c.geometry())) {
            changed.add(SlideStateDiff.ShapeField.GEOMETRY_SIZE);
        }
        if (!StateEquality.sameRotation(b.geometry(), c.geometry())) {
            changed.add(SlideStateDiff.ShapeField.GEOMETRY_ROTATION);
        }

        if (!StateEquality.sameStyle(b.style(), c.style())) {
            changed.add(SlideStateDiff.ShapeField.STYLE);
        }

        if (!StateEquality.sameTextBody(b.textBody(), c.textBody())) {
            changed.add(SlideStateDiff.ShapeField.TEXT);
        }

        if (!java.util.Objects.equals(b.name(), c.name())) {
            changed.add(SlideStateDiff.ShapeField.NAME);
        }

        if (b.isTextBox() != c.isTextBox()) {
            changed.add(SlideStateDiff.ShapeField.TEXT_BOX_FLAG);
        }

        if (!java.util.Objects.equals(b.parentGroupSpid(), c.parentGroupSpid())) {
            changed.add(SlideStateDiff.ShapeField.PARENT_GROUP);
        }

        return changed;
    }

    // ========== Animation diffing ==========

    private static void diffAnimations(SlideState baseline, SlideState current,
            List<SlideStateDiff.AnimationAdded> added,
            List<SlideStateDiff.AnimationRemoved> removed,
            List<SlideStateDiff.AnimationTimingChanged> timingChanged) {
        List<AnimationBinding> baseAnims = baseline.animations();
        List<AnimationBinding> currAnims = current.animations();

        // Match by identity tuple (target SPID + type + trigger + pRg).
        // Three outcomes per current binding:
        //  - No baseline match: "added".
        //  - Baseline match with equal timing: nothing to emit.
        //  - Baseline match with different timing: "timing changed".
        // Baseline bindings that never matched a current become "removed".
        List<AnimationBinding> remainingBase = new ArrayList<>(baseAnims);
        for (AnimationBinding c : currAnims) {
            AnimationBinding match = null;
            for (AnimationBinding b : remainingBase) {
                if (StateEquality.sameAnimationIdentity(b, c)) {
                    match = b;
                    break;
                }
            }
            if (match == null) {
                added.add(new SlideStateDiff.AnimationAdded(c));
                continue;
            }
            remainingBase.remove(match);
            if (!StateEquality.sameAnimationTiming(match, c)) {
                timingChanged.add(new SlideStateDiff.AnimationTimingChanged(match, c));
            }
        }
        for (AnimationBinding b : remainingBase) {
            removed.add(new SlideStateDiff.AnimationRemoved(b));
        }
    }

    // ========== Transition diffing ==========

    private static SlideStateDiff.TransitionChange diffTransition(
            SlideState baseline, SlideState current) {
        if (StateEquality.sameTransition(baseline.transition(), current.transition())) {
            return null;
        }
        return new SlideStateDiff.TransitionChange(baseline.transition(), current.transition());
    }

    // ========== Group diffing ==========

    /**
     * Derive group membership from ShapeSnapshot.parentGroupSpid().
     * For each group SPID, compute the child set in baseline vs. current
     * and emit a {@link SlideStateDiff.GroupDelta} for any difference.
     */
    private static List<SlideStateDiff.GroupDelta> diffGroups(
            SlideState baseline, SlideState current) {
        Map<Integer, Set<Integer>> baseGroups = groupMembership(baseline);
        Map<Integer, Set<Integer>> currGroups = groupMembership(current);

        List<SlideStateDiff.GroupDelta> deltas = new ArrayList<>();
        Set<Integer> allGroupSpids = new HashSet<>();
        allGroupSpids.addAll(baseGroups.keySet());
        allGroupSpids.addAll(currGroups.keySet());

        List<Integer> sorted = new ArrayList<>(allGroupSpids);
        sorted.sort(Integer::compare);

        for (int groupSpid : sorted) {
            Set<Integer> b = baseGroups.getOrDefault(groupSpid, new HashSet<>());
            Set<Integer> c = currGroups.getOrDefault(groupSpid, new HashSet<>());
            boolean newGroup = b.isEmpty() && !c.isEmpty();
            boolean dissolved = !b.isEmpty() && c.isEmpty();

            List<Integer> addedKids = new ArrayList<>();
            for (int sp : c) if (!b.contains(sp)) addedKids.add(sp);
            addedKids.sort(Integer::compare);

            List<Integer> removedKids = new ArrayList<>();
            for (int sp : b) if (!c.contains(sp)) removedKids.add(sp);
            removedKids.sort(Integer::compare);

            if (newGroup || dissolved || !addedKids.isEmpty() || !removedKids.isEmpty()) {
                deltas.add(new SlideStateDiff.GroupDelta(
                    groupSpid, newGroup, dissolved, addedKids, removedKids));
            }
        }
        return deltas;
    }

    private static Map<Integer, Set<Integer>> groupMembership(SlideState state) {
        Map<Integer, Set<Integer>> groups = new HashMap<>();
        for (ShapeSnapshot s : state.shapes().values()) {
            Integer parent = s.parentGroupSpid();
            if (parent == null) continue;
            groups.computeIfAbsent(parent, k -> new HashSet<>()).add(s.spid());
        }
        return groups;
    }
}
