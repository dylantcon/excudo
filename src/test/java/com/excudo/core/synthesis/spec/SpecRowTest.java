package com.excudo.core.synthesis.spec;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Headless unit tests for {@link SpecRow}'s dirty contract -- the nucleus
 * of the SlideSpec inline editor. No JavaFX here on purpose: the dirty
 * logic is pure and must be verifiable without booting a stage. The
 * widget-level behavior (commit-on-blur, the dirty dot, reset gating)
 * sits on top of this and is covered by the {@code --gui} smoke test.
 */
public class SpecRowTest {

    /** Smallest spec that varies on a single scalar field, for exercising
     *  edit / revert without dragging in sub-models. */
    private static CommandSpec.MoveSpec move(long x) {
        return new CommandSpec.MoveSpec(1, 5, x, 200);
    }

    @Test
    public void synthesizedRow_isCleanInitially() {
        SpecRow row = SpecRow.synthesized(move(100));
        assertFalse("a freshly synthesized row equals its baseline", row.isDirty());
    }

    @Test
    public void editingToADifferentValue_makesDirty() {
        SpecRow row = SpecRow.synthesized(move(100));
        row.setSpec(move(101));
        assertTrue(row.isDirty());
        assertEquals(move(101), row.spec());
    }

    @Test
    public void editingBackToBaseline_clearsDirty() {
        // Records compare by value, so an edit-and-revert is invisible --
        // no per-field bookkeeping needed to un-dirty.
        SpecRow row = SpecRow.synthesized(move(100));
        row.setSpec(move(101));
        assertTrue(row.isDirty());
        row.setSpec(move(100));
        assertFalse("value-equal to baseline => not dirty", row.isDirty());
    }

    @Test
    public void addedRow_isAlwaysDirty() {
        SpecRow row = SpecRow.added(move(100));
        assertNull("an added row carries no baseline", row.original());
        assertTrue("no baseline => dirty", row.isDirty());
        // Re-editing an added row keeps it dirty: there is nothing to match.
        row.setSpec(move(101));
        assertTrue(row.isDirty());
    }

    @Test
    public void synthesizedRow_exposesBaselineAndValue() {
        CommandSpec.MoveSpec m = move(100);
        SpecRow row = SpecRow.synthesized(m);
        assertEquals(m, row.original());
        assertEquals(m, row.spec());
    }

    @Test
    public void expandedState_roundTrips() {
        SpecRow row = SpecRow.synthesized(move(100));
        assertFalse("rows start collapsed", row.isExpanded());
        row.setExpanded(true);
        assertTrue(row.isExpanded());
    }

    @Test
    public void deviationAcrossSpecTypes_usesValueEquality() {
        // Swapping to a different spec type with coincidentally similar
        // fields is still a deviation -- equality is type-aware.
        SpecRow row = SpecRow.synthesized(new CommandSpec.RemoveShapeSpec(1, 5));
        row.setSpec(new CommandSpec.UngroupSpec(1, 5));
        assertTrue(row.isDirty());
    }

    @Test(expected = NullPointerException.class)
    public void setSpec_rejectsNull() {
        SpecRow.synthesized(move(100)).setSpec(null);
    }
}
