package com.excudo.core.synthesis.spec;

import com.excudo.core.introspection.TransitionDescriptor;
import com.excudo.core.model.AnimationBinding;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.ShapeStyle;
import com.excudo.core.model.SlideShape;
import com.excudo.core.model.TextBody;
import com.excudo.core.model.TransitionType;

import java.util.List;
import java.util.Objects;

/**
 * Sealed interface for the v1 {@code CommandSpec} vocabulary -- the
 * immutable value nodes of a {@link com.excudo.core.synthesis.CommandScript}
 * DAG. Each record below is a pure, hashable, JSON-serializable
 * description of one canonical Command; the
 * {@link SpecToCommandMapper} turns each spec into a concrete
 * {@code Command} instance at script-run time.
 *
 * <p><b>Why records, not Commands?</b> Live Command instances carry a
 * {@code PPTXOrchestrator} reference and aren't safe to compare, hash,
 * or serialize. Specs are pure data -- you can write them to disk,
 * hash them into a set, compute a DAG diff, ship them over the wire,
 * all without touching the document.
 *
 * <p><b>v1 vocabulary (14 specs):</b>
 * <ul>
 *   <li>Shapes: {@link AddShapeSpec}, {@link RemoveShapeSpec},
 *       {@link MoveSpec}, {@link ResizeSpec}, {@link RotateSpec},
 *       {@link SetTextSpec}, {@link SetShapeStyleSpec},
 *       {@link ReorderSpec}</li>
 *   <li>Animations: {@link AddAnimationSpec}, {@link RemoveAnimationSpec}</li>
 *   <li>Transitions: {@link SetTransitionSpec}, {@link ClearTransitionSpec}</li>
 *   <li>Groups: {@link CreateGroupSpec}, {@link UngroupSpec}</li>
 * </ul>
 *
 * <p><b>Deferred for v2:</b> {@code SetAnimationTimingSpec} (timing-only
 * updates to existing animations -- currently the synthesizer emits
 * remove+add when timing changes, mirroring what the writer does today)
 * and {@code SetShapeFontOverrideSpec} (run-level font deviations --
 * requires TextRun-level addressability not yet in the command surface).
 * Both are tracked in the plan's follow-up backlog.
 */
public sealed interface CommandSpec permits
        CommandSpec.AddShapeSpec,
        CommandSpec.RemoveShapeSpec,
        CommandSpec.MoveSpec,
        CommandSpec.ResizeSpec,
        CommandSpec.RotateSpec,
        CommandSpec.RenameShapeSpec,
        CommandSpec.SetTextBoxFlagSpec,
        CommandSpec.SetRunFormatSpec,
        CommandSpec.SetTextSpec,
        CommandSpec.SetShapeStyleSpec,
        CommandSpec.ReorderSpec,
        CommandSpec.AddAnimationSpec,
        CommandSpec.RemoveAnimationSpec,
        CommandSpec.SetAnimationTimingSpec,
        CommandSpec.SetTransitionSpec,
        CommandSpec.ClearTransitionSpec,
        CommandSpec.CreateGroupSpec,
        CommandSpec.UngroupSpec,
        CommandSpec.AddToGroupSpec,
        CommandSpec.DetachFromGroupSpec {

    /** Slide number this spec targets. Every spec carries it, so the
     *  DAG's node-to-slide mapping is self-contained. */
    int slideNumber();

    // ============================================================
    // Shapes
    // ============================================================

    /**
     * Create a new shape on a slide. Carries the add-time plain text
     * {@code text} plus the add-time {@code alignment} -- both match
     * {@code AddShapeCommand}'s parameter surface. Richer typed text
     * (multiple paragraphs, formatting runs) must be applied via a
     * follow-up {@link SetTextSpec} with a DAG edge depending on this
     * add; the synthesizer materializes that edge automatically. The
     * {@code style} and {@code name} are nullable and mean "use the
     * default" when null.
     *
     * <p>{@code sourceSpidHint} carries the SPID of the shape as it
     * existed in the source {@link com.excudo.core.synthesis.SlideState}
     * the synthesizer was diffing. Nullable because manually-constructed
     * specs won't have one. When non-null, the
     * {@link com.excudo.core.synthesis.SpecRewriter} uses it to record
     * "source SPID X maps to the fresh SPID this spec just allocated"
     * -- which in turn lets downstream specs that reference SPID X on
     * the source slide (animation targets, group children, etc.) get
     * rewritten to the post-add target SPID before execution.
     */
    record AddShapeSpec(
            int slideNumber,
            SlideShape.ShapeType shapeType,
            ShapeGeometry geometry,
            String text,
            String name,
            ShapeStyle style,
            String alignment,
            boolean isTextBox,
            Integer sourceSpidHint) implements CommandSpec {
        public AddShapeSpec {
            validateSlide(slideNumber);
            Objects.requireNonNull(shapeType, "shapeType");
            Objects.requireNonNull(geometry, "geometry");
        }

        /** Convenience constructor for callers that don't have a source
         *  SPID to thread through (manual tests, ad-hoc builders). */
        public AddShapeSpec(int slideNumber,
                SlideShape.ShapeType shapeType,
                ShapeGeometry geometry,
                String text,
                String name,
                ShapeStyle style,
                String alignment,
                boolean isTextBox) {
            this(slideNumber, shapeType, geometry, text, name, style, alignment, isTextBox, null);
        }
    }

    record RemoveShapeSpec(int slideNumber, int spid) implements CommandSpec {
        public RemoveShapeSpec {
            validateSlide(slideNumber);
            validateSpid(spid);
        }
    }

    /** Change a shape's position (off). Size + rotation preserved. */
    record MoveSpec(int slideNumber, int spid, long newX, long newY) implements CommandSpec {
        public MoveSpec {
            validateSlide(slideNumber);
            validateSpid(spid);
        }
    }

    /** Change a shape's size (ext). Position + rotation preserved. */
    record ResizeSpec(int slideNumber, int spid, long newWidth, long newHeight) implements CommandSpec {
        public ResizeSpec {
            validateSlide(slideNumber);
            validateSpid(spid);
            if (newWidth < 0 || newHeight < 0) {
                throw new IllegalArgumentException("size must be non-negative");
            }
        }
    }

    /** Change a shape's rotation in degrees (the ShapeGeometry convention,
     *  not OOXML's 60000ths-of-degree raw form). */
    record RotateSpec(int slideNumber, int spid, double newRotationDegrees) implements CommandSpec {
        public RotateSpec {
            validateSlide(slideNumber);
            validateSpid(spid);
        }
    }

    /** Rename a shape's {@code cNvPr/@name}. */
    record RenameShapeSpec(int slideNumber, int spid, String newName) implements CommandSpec {
        public RenameShapeSpec {
            validateSlide(slideNumber);
            validateSpid(spid);
            Objects.requireNonNull(newName, "newName");
        }
    }

    /** Toggle {@code cNvSpPr/@txBox} in place. Keeps the shape's SPID
     *  stable so downstream refs (animations, group children) don't
     *  need re-mapping. */
    record SetTextBoxFlagSpec(int slideNumber, int spid, boolean flag) implements CommandSpec {
        public SetTextBoxFlagSpec {
            validateSlide(slideNumber);
            validateSpid(spid);
        }
    }

    /** Replace one text run's formatting inside a shape's txBody in
     *  place, scoped by (paragraph index, run index). Emitted by the
     *  synthesizer when a {@link SetTextSpec} would rewrite the whole
     *  body just to tweak one run's formatting. */
    record SetRunFormatSpec(
            int slideNumber,
            int spid,
            int paragraphIdx,
            int runIdx,
            com.excudo.core.model.TextRun newRun) implements CommandSpec {
        public SetRunFormatSpec {
            validateSlide(slideNumber);
            validateSpid(spid);
            if (paragraphIdx < 0 || runIdx < 0) {
                throw new IllegalArgumentException("paragraph + run indices must be non-negative");
            }
            Objects.requireNonNull(newRun, "newRun");
        }
    }

    /**
     * Replace the full {@link TextBody} of a shape. The synthesizer
     * always emits this as a full replacement (not prepend/append): the
     * state diff works on the whole text body, so the minimum spec is
     * always the complete desired value. User-facing prepend/append
     * lives on {@code edit-content} at the command surface; the
     * synthesizer produces only REPLACE-equivalent specs.
     */
    record SetTextSpec(int slideNumber, int spid, TextBody textBody) implements CommandSpec {
        public SetTextSpec {
            validateSlide(slideNumber);
            validateSpid(spid);
            Objects.requireNonNull(textBody, "textBody");
        }
    }

    /** Idempotent full style set. The differ decomposes to this when
     *  any of fill / line / themeStyleRef differs -- one spec, not three. */
    record SetShapeStyleSpec(int slideNumber, int spid, ShapeStyle style) implements CommandSpec {
        public SetShapeStyleSpec {
            validateSlide(slideNumber);
            validateSpid(spid);
            Objects.requireNonNull(style, "style");
        }
    }

    /** Reorder a shape's z-order. Uses the existing {@code reorder}
     *  command's vocabulary. */
    record ReorderSpec(int slideNumber, int spid, Direction direction) implements CommandSpec {
        public enum Direction { FRONT, BACK, FORWARD, BACKWARD }
        public ReorderSpec {
            validateSlide(slideNumber);
            validateSpid(spid);
            Objects.requireNonNull(direction, "direction");
        }
    }

    // ============================================================
    // Animations
    // ============================================================

    /** Add an animation. The {@link AnimationBinding} carries the full
     *  effect + timing configuration. */
    record AddAnimationSpec(int slideNumber, AnimationBinding binding) implements CommandSpec {
        public AddAnimationSpec {
            validateSlide(slideNumber);
            Objects.requireNonNull(binding, "binding");
        }
    }

    /** Remove an animation by its timing-node cTn id (the same handle
     *  {@code orchestrator.removeAnimation} expects). */
    record RemoveAnimationSpec(int slideNumber, int timingNodeId) implements CommandSpec {
        public RemoveAnimationSpec {
            validateSlide(slideNumber);
            if (timingNodeId <= 0) {
                throw new IllegalArgumentException("timingNodeId must be positive");
            }
        }
    }

    /**
     * Update an existing animation's timing fields in place. Scoped by
     * the target animation's {@code cTn} id. Fields set to {@code null}
     * are left unchanged -- allowing a narrow duration-only update
     * without re-specifying delay.
     */
    record SetAnimationTimingSpec(
            int slideNumber,
            int timingNodeId,
            String newDuration,
            String newDelay) implements CommandSpec {
        public SetAnimationTimingSpec {
            validateSlide(slideNumber);
            if (timingNodeId <= 0) {
                throw new IllegalArgumentException("timingNodeId must be positive");
            }
        }
    }

    // ============================================================
    // Transitions
    // ============================================================

    /** Set an explicit slide-level transition. Inheritance is handled
     *  on the read side ({@link TransitionDescriptor#source()}); this
     *  spec always writes at the slide level. */
    record SetTransitionSpec(
            int slideNumber,
            TransitionType transitionType,
            String speed,
            Integer autoAdvanceMs) implements CommandSpec {
        public SetTransitionSpec {
            validateSlide(slideNumber);
            Objects.requireNonNull(transitionType, "transitionType");
            if (speed == null || speed.isBlank()) speed = "med";
        }
    }

    /** Remove any slide-level transition override; the slide will
     *  inherit from its layout (or master) afterward. */
    record ClearTransitionSpec(int slideNumber) implements CommandSpec {
        public ClearTransitionSpec {
            validateSlide(slideNumber);
        }
    }

    // ============================================================
    // Groups
    // ============================================================

    /** Create a group containing the named existing children. The
     *  synthesizer materializes DAG edges from this spec to each
     *  child's {@link AddShapeSpec}. */
    record CreateGroupSpec(
            int slideNumber,
            List<Integer> childSpids,
            String groupName) implements CommandSpec {
        public CreateGroupSpec {
            validateSlide(slideNumber);
            Objects.requireNonNull(childSpids, "childSpids");
            if (childSpids.isEmpty()) {
                throw new IllegalArgumentException("CreateGroupSpec needs at least one child SPID");
            }
            childSpids = List.copyOf(childSpids);
        }
    }

    /** Dissolve an existing group, leaving its children in the slide's
     *  top-level z-order. */
    record UngroupSpec(int slideNumber, int groupSpid) implements CommandSpec {
        public UngroupSpec {
            validateSlide(slideNumber);
            validateSpid(groupSpid);
        }
    }

    /** Move an existing top-level shape into an existing group while
     *  preserving visual position via the inverse coordinate transform. */
    record AddToGroupSpec(int slideNumber, int groupSpid, int childSpid) implements CommandSpec {
        public AddToGroupSpec {
            validateSlide(slideNumber);
            validateSpid(groupSpid);
            validateSpid(childSpid);
            if (groupSpid == childSpid) {
                throw new IllegalArgumentException("group and child SPIDs must differ");
            }
        }
    }

    /** Move a grouped child out to top-level, preserving visual position. */
    record DetachFromGroupSpec(int slideNumber, int childSpid) implements CommandSpec {
        public DetachFromGroupSpec {
            validateSlide(slideNumber);
            validateSpid(childSpid);
        }
    }

    // ============================================================
    // Invariants shared across specs
    // ============================================================

    private static void validateSlide(int slideNumber) {
        if (slideNumber < 1) {
            throw new IllegalArgumentException("slideNumber must be >= 1");
        }
    }

    private static void validateSpid(int spid) {
        if (spid <= 0) {
            throw new IllegalArgumentException("spid must be > 0");
        }
    }
}
