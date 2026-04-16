package com.excudo.core.model;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests ShapeFill factory methods and type/color accessors.
 */
public class ShapeFillTest {

    @Test
    public void solidFromHex() {
        ShapeFill fill = ShapeFill.solid("FF0000");
        assertEquals(FillType.SOLID, fill.getType());
        assertNotNull(fill.getColor());
        assertEquals("FF0000", fill.getColor().getHexVal());
    }

    @Test
    public void solidFromTextColor() {
        TextColor color = TextColor.hex("00FF00");
        ShapeFill fill = ShapeFill.solid(color);
        assertEquals(FillType.SOLID, fill.getType());
        assertEquals("00FF00", fill.getColor().getHexVal());
    }

    @Test
    public void schemeColor() {
        ShapeFill fill = ShapeFill.scheme("accent1");
        assertEquals(FillType.SOLID, fill.getType());
        assertTrue(fill.getColor().isScheme());
        assertEquals("accent1", fill.getColor().getSchemeVal());
    }

    @Test
    public void noFill() {
        ShapeFill fill = ShapeFill.noFill();
        assertEquals(FillType.NO_FILL, fill.getType());
    }
}
