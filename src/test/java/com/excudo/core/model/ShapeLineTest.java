package com.excudo.core.model;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests ShapeLine factory methods and accessors.
 */
public class ShapeLineTest {

    @Test
    public void solidLineWithWidthAndColor() {
        TextColor color = TextColor.hex("0000FF");
        ShapeLine line = ShapeLine.solid(12700, color);

        assertEquals(Integer.valueOf(12700), line.getWidthEMU());
        assertEquals("0000FF", line.getColor().getHexVal());
        // solid() sets dash style to "solid"
        assertEquals("solid", line.getDashStyle());
    }

    @Test
    public void lineWithDashStyle() {
        TextColor color = TextColor.hex("FF0000");
        ShapeLine line = ShapeLine.of(25400, color, "dash");

        assertEquals(Integer.valueOf(25400), line.getWidthEMU());
        assertEquals("dash", line.getDashStyle());
    }

    @Test
    public void thinLineFromHex() {
        ShapeLine line = ShapeLine.thin("333333");

        assertNotNull(line.getWidthEMU());
        assertEquals("333333", line.getColor().getHexVal());
    }

    @Test
    public void widthInEMU() {
        // 12700 EMU = 1pt, 25400 = 2pt
        ShapeLine onePt = ShapeLine.solid(12700, TextColor.hex("000000"));
        ShapeLine twoPt = ShapeLine.solid(25400, TextColor.hex("000000"));

        assertTrue(twoPt.getWidthEMU() > onePt.getWidthEMU());
    }
}
