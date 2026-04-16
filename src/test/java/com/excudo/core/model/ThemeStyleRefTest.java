package com.excudo.core.model;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests ThemeStyleRef style matrix references for p:style elements.
 */
public class ThemeStyleRefTest {

    @Test
    public void noneHasZeroIndices() {
        ThemeStyleRef none = ThemeStyleRef.NONE;
        // NONE uses -1 to indicate no style reference
        assertEquals(-1, none.getLineRefIdx());
        assertEquals(-1, none.getFillRefIdx());
        assertEquals(-1, none.getEffectRefIdx());
    }

    @Test
    public void defaultStyleForShapeWithText() {
        ThemeStyleRef ref = ThemeStyleRef.defaultStyle(true);

        assertTrue("fillRef idx should be > 0", ref.getFillRefIdx() > 0);
        assertNotNull(ref.getFillColor());
        assertNotNull(ref.getLineColor());
        assertNotNull(ref.getFontRefIdx());
    }

    @Test
    public void defaultStyleForShapeWithoutText() {
        ThemeStyleRef ref = ThemeStyleRef.defaultStyle(false);
        assertTrue("fillRef idx should be > 0", ref.getFillRefIdx() > 0);
    }

    @Test
    public void customStyleRef() {
        ThemeStyleRef ref = ThemeStyleRef.of(
            2, "accent1",   // line
            1, "accent1",   // fill
            0, "accent1",   // effect
            "minor", "lt1"  // font
        );

        assertEquals(2, ref.getLineRefIdx());
        assertEquals("accent1", ref.getLineColor());
        assertEquals(1, ref.getFillRefIdx());
        assertEquals("accent1", ref.getFillColor());
        assertEquals(0, ref.getEffectRefIdx());
        assertEquals("minor", ref.getFontRefIdx());
        assertEquals("lt1", ref.getFontColor());
    }

    @Test
    public void gettersReturnSetValues() {
        ThemeStyleRef ref = ThemeStyleRef.of(
            1, "dk2",
            3, "accent3",
            2, "accent3",
            "major", "dk1"
        );

        assertEquals(1, ref.getLineRefIdx());
        assertEquals("dk2", ref.getLineColor());
        assertEquals(3, ref.getFillRefIdx());
        assertEquals("accent3", ref.getFillColor());
        assertEquals(2, ref.getEffectRefIdx());
        assertEquals("major", ref.getFontRefIdx());
        assertEquals("dk1", ref.getFontColor());
    }
}
