package com.excudo.core.synthesis;

import com.excudo.core.introspection.LayoutBaseline;
import com.excudo.core.introspection.TransitionDescriptor;
import com.excudo.core.model.AnimationBinding;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable description of the contents of a slide at one point in time.
 * Three kinds of "one point in time" the synthesizer works with:
 *
 * <ul>
 *   <li><b>Baseline</b> — what the slide looks like if no user edits
 *       have been applied: just the layout's placeholders and the
 *       master-derived defaults. Built from
 *       {@link com.excudo.core.introspection.LayoutBaselineBuilder}.</li>
 *   <li><b>Current</b> — what the slide actually looks like now, read
 *       from the parsed DOM via {@link com.excudo.core.introspection.SlideIntrospector}
 *       (plus shape snapshots from the shape registry).</li>
 *   <li><b>Hypothetical</b> — the result of applying a candidate
 *       CommandScript to the baseline; the round-trip test in 4.6
 *       compares hypothetical against current.</li>
 * </ul>
 *
 * <p>This record will grow in 4.2+ as new CommandSpecs require new
 * baseline properties to diff against. For 4.1 the four fields below
 * cover the v1 vocabulary: shapes, animations, transition, baseline.
 */
public record SlideState(
        int slideNumber,
        Map<Integer, ShapeSnapshot> shapes,
        List<AnimationBinding> animations,
        TransitionDescriptor transition,
        LayoutBaseline baseline) {

    public SlideState {
        if (slideNumber < 1) throw new IllegalArgumentException("slideNumber must be >= 1");
        shapes      = shapes      != null ? Map.copyOf(shapes)             : Collections.emptyMap();
        animations  = animations  != null ? List.copyOf(animations)        : Collections.emptyList();
        Objects.requireNonNull(baseline, "baseline");
    }
}
