package com.excudo.core.synthesis.spec;

import java.util.Objects;

/**
 * One row in the SlideSpec editor: a working {@link CommandSpec} paired
 * with the synthesized baseline it came from, plus its disclosure state.
 *
 * <p>Pure data, no JavaFX -- the dirty contract is the load-bearing logic
 * of the editor and it lives here, in the spec package, precisely so it
 * compiles and unit-tests on the headless classpath. (The build drops
 * everything under {@code com.excudo.view} in headless mode, so a model
 * placed there could only be exercised under {@code --gui}.)
 *
 * <p>A row is <b>dirty</b> when its current {@link #spec()} differs from
 * the {@link #original()} baseline it was synthesized from. Because every
 * {@code CommandSpec} subtype is a record (value equality), editing a
 * field and then editing it back to the baseline auto-clears dirty with
 * no bookkeeping. A row created via {@link #added(CommandSpec)} (e.g. a
 * Duplicate) has no baseline and is dirty for its whole life -- a brand
 * new command is a deviation from the synthesized script by definition.
 */
public final class SpecRow {

    private CommandSpec spec;
    private final CommandSpec original; // null => no baseline (newly added row)
    private boolean expanded;

    private SpecRow(CommandSpec spec, CommandSpec original) {
        this.spec = Objects.requireNonNull(spec, "spec");
        this.original = original; // nullable by design
    }

    /** A synthesized row whose baseline IS its current value -- the common
     *  case when (re)building rows from a fresh synthesis. */
    public static SpecRow synthesized(CommandSpec spec) {
        return new SpecRow(spec, spec);
    }

    /** A row with no synthesized baseline (e.g. produced by Duplicate).
     *  Always {@link #isDirty()}. */
    public static SpecRow added(CommandSpec spec) {
        return new SpecRow(spec, null);
    }

    public CommandSpec spec() { return spec; }

    /** The synthesized baseline, or {@code null} for an added row. */
    public CommandSpec original() { return original; }

    /** Replace the working value (write-back from an inline edit). */
    public void setSpec(CommandSpec next) {
        this.spec = Objects.requireNonNull(next, "next");
    }

    public boolean isExpanded() { return expanded; }

    public void setExpanded(boolean expanded) { this.expanded = expanded; }

    /** True when this row deviates from its synthesized baseline: either
     *  it has no baseline (added) or its current value no longer equals
     *  the baseline. Cheap and exact -- records compare by value. */
    public boolean isDirty() {
        return original == null || !spec.equals(original);
    }
}
