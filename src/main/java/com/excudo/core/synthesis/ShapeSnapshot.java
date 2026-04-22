package com.excudo.core.synthesis;

import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.ShapeStyle;
import com.excudo.core.model.SlideShape;
import com.excudo.core.model.TextBody;

import java.util.Objects;

/**
 * Immutable value-projection of a {@link SlideShape} capturing only the
 * fields the synthesizer diffs on. Deliberately separate from
 * {@code SlideShape} because the live model carries a DOM {@code Element}
 * reference, is mutated in place by writers, and has no value-based
 * equality. Snapshots are pure records, suitable for use as diff inputs
 * and as DAG node comparisons.
 *
 * <p>All fields except {@code spid}, {@code name}, and {@code type}
 * may be null. Null means "not set" and should be distinguished from
 * "set to default" only when the diff rule for that field says so.
 */
public record ShapeSnapshot(
        int spid,
        String name,
        SlideShape.ShapeType type,
        ShapeGeometry geometry,
        ShapeStyle style,
        TextBody textBody,
        boolean isTextBox,
        Integer parentGroupSpid) {

    public ShapeSnapshot {
        if (spid <= 0) throw new IllegalArgumentException("spid must be positive");
        Objects.requireNonNull(type, "type");
    }
}
