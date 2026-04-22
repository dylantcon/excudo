package com.excudo.core.synthesis;

import com.excudo.core.introspection.TransitionDescriptor;
import com.excudo.core.model.AnimationBinding;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Typed delta between two {@link SlideState}s. The
 * {@link SlideStateDiffer} produces this; the
 * {@link ScriptSynthesizer} (landing in 4.4) consumes it.
 *
 * <p>Each delta type carries exactly the information the synthesizer
 * needs to emit its corresponding CommandSpec -- nothing more, to
 * prevent spec drift and nothing less, to prevent the synthesizer from
 * needing to re-read the live state. The two most nuanced types are
 * {@link ShapeModified} (tracks which <em>property fields</em>
 * changed, not just "something changed") and {@link GroupDelta}.
 */
public record SlideStateDiff(
        List<ShapeAdded> added,
        List<ShapeRemoved> removed,
        List<ShapeModified> modified,
        List<AnimationAdded> animationsAdded,
        List<AnimationRemoved> animationsRemoved,
        List<AnimationTimingChanged> animationsTimingChanged,
        TransitionChange transitionChange,
        List<GroupDelta> groupDeltas) {

    public SlideStateDiff {
        added                    = copyOrEmpty(added);
        removed                  = copyOrEmpty(removed);
        modified                 = copyOrEmpty(modified);
        animationsAdded          = copyOrEmpty(animationsAdded);
        animationsRemoved        = copyOrEmpty(animationsRemoved);
        animationsTimingChanged  = copyOrEmpty(animationsTimingChanged);
        groupDeltas              = copyOrEmpty(groupDeltas);
    }

    public boolean isEmpty() {
        return added.isEmpty() && removed.isEmpty() && modified.isEmpty()
            && animationsAdded.isEmpty() && animationsRemoved.isEmpty()
            && animationsTimingChanged.isEmpty()
            && transitionChange == null
            && groupDeltas.isEmpty();
    }

    // ========== Shape deltas ==========

    /** A shape in {@code current} that has no matching SPID in {@code baseline}. */
    public record ShapeAdded(ShapeSnapshot shape) {
        public ShapeAdded { Objects.requireNonNull(shape, "shape"); }
    }

    /** A shape in {@code baseline} that has no matching SPID in {@code current}. */
    public record ShapeRemoved(ShapeSnapshot shape) {
        public ShapeRemoved { Objects.requireNonNull(shape, "shape"); }
    }

    /**
     * A shape present in both states but differing in one or more
     * property fields. {@code changedFields} names each field whose
     * {@code baseline} value is structurally unequal to its
     * {@code current} value, matching {@link ShapeField} vocabulary.
     *
     * <p>Both snapshots are included so the synthesizer can form the
     * "from" and "to" values for every spec without re-reading state.
     */
    public record ShapeModified(
            ShapeSnapshot baseline,
            ShapeSnapshot current,
            Set<ShapeField> changedFields) {
        public ShapeModified {
            Objects.requireNonNull(baseline, "baseline");
            Objects.requireNonNull(current, "current");
            Objects.requireNonNull(changedFields, "changedFields");
            if (baseline.spid() != current.spid()) {
                throw new IllegalArgumentException("SPID mismatch between snapshots");
            }
            changedFields = Set.copyOf(changedFields);
        }
    }

    /** Which property of a shape changed. One-to-one with the CommandSpec
     *  that the synthesizer emits for that change. */
    public enum ShapeField {
        GEOMETRY_POSITION,   // off only (MoveSpec)
        GEOMETRY_SIZE,       // ext only (ResizeSpec)
        GEOMETRY_ROTATION,   // rot only (RotateSpec)
        STYLE,               // fill/line/themeStyleRef (SetShapeStyleSpec)
        TEXT,                // textBody (SetTextSpec)
        NAME,                // name (metadata; usually triggers a rewrite-in-place)
        TEXT_BOX_FLAG,       // isTextBox (structural; requires RemoveShape + AddShape)
        PARENT_GROUP         // parentGroupSpid (GroupDelta)
    }

    // ========== Animation deltas ==========

    public record AnimationAdded(AnimationBinding binding) {
        public AnimationAdded { Objects.requireNonNull(binding, "binding"); }
    }

    public record AnimationRemoved(AnimationBinding binding) {
        public AnimationRemoved { Objects.requireNonNull(binding, "binding"); }
    }

    /**
     * An animation matched by identity (target SPID + type + trigger
     * + paragraph range) but with different timing (duration, delay).
     * Synthesizer emits a {@code SetAnimationTimingSpec} rather than a
     * full remove+add, which is minimum-DAG and preserves the cTn id
     * across the edit.
     */
    public record AnimationTimingChanged(AnimationBinding before, AnimationBinding after) {
        public AnimationTimingChanged {
            Objects.requireNonNull(before, "before");
            Objects.requireNonNull(after, "after");
        }
    }

    // ========== Transition delta ==========

    /**
     * Transition changed between states. Either side may be null to
     * encode "became set" ({@code before} null) or "became unset"
     * ({@code after} null). If both are non-null and equal, the
     * differ must NOT emit a {@code TransitionChange} at all.
     */
    public record TransitionChange(
            TransitionDescriptor before,
            TransitionDescriptor after) {
    }

    // ========== Group delta ==========

    /**
     * A group's membership changed. Either the group itself is new
     * ({@code groupAdded}), an existing group dissolved
     * ({@code groupRemoved}), or individual children moved in or out
     * ({@code childrenAdded} / {@code childrenRemoved}).
     */
    public record GroupDelta(
            int groupSpid,
            boolean groupAdded,
            boolean groupRemoved,
            List<Integer> childrenAdded,
            List<Integer> childrenRemoved) {
        public GroupDelta {
            childrenAdded   = copyOrEmpty(childrenAdded);
            childrenRemoved = copyOrEmpty(childrenRemoved);
            if (groupAdded && groupRemoved) {
                throw new IllegalArgumentException("groupAdded and groupRemoved are mutually exclusive");
            }
        }
    }

    // ========== Helpers ==========

    private static <T> List<T> copyOrEmpty(List<T> xs) {
        return xs != null ? List.copyOf(xs) : Collections.emptyList();
    }
}
