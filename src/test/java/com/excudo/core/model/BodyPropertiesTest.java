package com.excudo.core.model;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests BodyProperties builder, defaults, and nullable field handling.
 */
public class BodyPropertiesTest {

    @Test
    public void defaultInstanceHasNullFields() {
        BodyProperties bp = BodyProperties.DEFAULT;
        assertNull(bp.getVerticalAlignment());
        assertNull(bp.getWrap());
        assertNull(bp.getVerticalText());
        assertNull(bp.getAutofit());
        assertNull(bp.getFontScale());
        assertNull(bp.getLeftInset());
        assertNull(bp.getTopInset());
        assertNull(bp.getRightInset());
        assertNull(bp.getBottomInset());
        assertNull(bp.getNumColumns());
        assertFalse(bp.isRtlCol());
    }

    @Test
    public void builderSetsAllFields() {
        BodyProperties bp = BodyProperties.builder()
            .verticalAlignment("ctr")
            .wrap("square")
            .verticalText("vert")
            .autofit(AutofitType.NORMAL)
            .fontScale(80000)
            .leftInset(91440)
            .topInset(45720)
            .rightInset(91440)
            .bottomInset(45720)
            .numColumns(2)
            .rtlCol(true)
            .build();

        assertEquals("ctr", bp.getVerticalAlignment());
        assertEquals("square", bp.getWrap());
        assertEquals("vert", bp.getVerticalText());
        assertEquals(AutofitType.NORMAL, bp.getAutofit());
        assertEquals(Integer.valueOf(80000), bp.getFontScale());
        assertEquals(Integer.valueOf(91440), bp.getLeftInset());
        assertEquals(Integer.valueOf(45720), bp.getTopInset());
        assertEquals(Integer.valueOf(91440), bp.getRightInset());
        assertEquals(Integer.valueOf(45720), bp.getBottomInset());
        assertEquals(Integer.valueOf(2), bp.getNumColumns());
        assertTrue(bp.isRtlCol());
    }

    @Test
    public void partialBuilderLeavesOtherFieldsNull() {
        BodyProperties bp = BodyProperties.builder()
            .verticalAlignment("b")
            .autofit(AutofitType.SHAPE)
            .build();

        assertEquals("b", bp.getVerticalAlignment());
        assertEquals(AutofitType.SHAPE, bp.getAutofit());
        assertNull(bp.getWrap());
        assertNull(bp.getLeftInset());
        assertNull(bp.getNumColumns());
    }

    @Test
    public void verticalAlignmentValues() {
        for (String anchor : new String[]{"t", "ctr", "b"}) {
            BodyProperties bp = BodyProperties.builder()
                .verticalAlignment(anchor).build();
            assertEquals(anchor, bp.getVerticalAlignment());
        }
    }

    @Test
    public void autofitEnumValues() {
        for (AutofitType type : AutofitType.values()) {
            BodyProperties bp = BodyProperties.builder().autofit(type).build();
            assertEquals(type, bp.getAutofit());
        }
    }

    @Test
    public void insetsAreIndependent() {
        BodyProperties bp = BodyProperties.builder()
            .leftInset(100)
            .rightInset(200)
            .build();

        assertEquals(Integer.valueOf(100), bp.getLeftInset());
        assertEquals(Integer.valueOf(200), bp.getRightInset());
        assertNull(bp.getTopInset());
        assertNull(bp.getBottomInset());
    }
}
