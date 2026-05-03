package com.excudo.core.model.math;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The contents of one OOXML math container -- the value of a
 * {@code <m:oMath>} (top-level inline math), {@code <m:oMathPara>}
 * (display-mode math paragraph), or any of the per-slot wrappers
 * ({@code <m:e>}, {@code <m:num>}, {@code <m:den>}, {@code <m:sub>},
 * {@code <m:sup>}, {@code <m:lim>}, {@code <m:deg>}, ...).
 *
 * <p>Holds an ordered list of {@link MathElement}s -- runs and the
 * structured math constructors (fractions, radicals, super/subscripts,
 * n-ary operators, delimiters, etc.) flow side-by-side just as text
 * and math siblings do in OMML.
 *
 * <p>Analogous to {@link com.excudo.core.model.TextBody} for the
 * regular-text side of the model: a value-typed container that the
 * extractor produces from DOM and the layout / painter pipeline
 * consumes.
 */
public final class MathBody {

    /** Inline math ({@code m:oMath}) versus display-mode math
     *  ({@code m:oMathPara}). The OpenType MATH-table layout pulls
     *  different constants for each (e.g. fraction shifts use
     *  {@code fractionNumeratorDisplayStyleShiftUp} when
     *  {@code displayMode == true}). */
    private final boolean displayMode;
    private final List<MathElement> elements;

    public MathBody(boolean displayMode, List<MathElement> elements) {
        this.displayMode = displayMode;
        this.elements = elements != null
            ? Collections.unmodifiableList(List.copyOf(elements))
            : List.of();
    }

    public static MathBody inline(List<MathElement> elements) {
        return new MathBody(false, elements);
    }

    public static MathBody display(List<MathElement> elements) {
        return new MathBody(true, elements);
    }

    public static MathBody empty() {
        return new MathBody(false, List.of());
    }

    public boolean isDisplayMode() { return displayMode; }
    public List<MathElement> getElements() { return elements; }
    public boolean isEmpty() { return elements.isEmpty(); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MathBody that)) return false;
        return displayMode == that.displayMode && elements.equals(that.elements);
    }

    @Override
    public int hashCode() {
        return Objects.hash(displayMode, elements);
    }

    @Override
    public String toString() {
        return (displayMode ? "MathBody[display]" : "MathBody[inline]")
            + "(" + elements.size() + " elements)";
    }
}
