package com.excudo.core.model;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for shape styling model classes: ShapeStyle, ShapeFill, ShapeLine, ThemeStyleRef
 */
public class ShapeStyleTest {

    // ===== ShapeFill =====

    @Test
    public void testSolidFillWithHex() {
        ShapeFill fill = ShapeFill.solid("FF0000");
        assertEquals(FillType.SOLID, fill.getType());
        assertFalse(fill.getColor().isScheme());
        assertEquals("FF0000", fill.getColor().getHexVal());
    }

    @Test
    public void testSolidFillWithHashHex() {
        ShapeFill fill = ShapeFill.solid("#00FF00");
        assertEquals("00FF00", fill.getColor().getHexVal());
    }

    @Test
    public void testSolidFillWithScheme() {
        ShapeFill fill = ShapeFill.scheme("accent1");
        assertEquals(FillType.SOLID, fill.getType());
        assertTrue(fill.getColor().isScheme());
        assertEquals("accent1", fill.getColor().getSchemeVal());
    }

    @Test
    public void testNoFill() {
        ShapeFill fill = ShapeFill.noFill();
        assertEquals(FillType.NO_FILL, fill.getType());
        assertNull(fill.getColor());
    }

    @Test
    public void testSolidFillWithTextColor() {
        TextColor color = TextColor.hex("ABCDEF");
        ShapeFill fill = ShapeFill.solid(color);
        assertEquals(FillType.SOLID, fill.getType());
        assertEquals("ABCDEF", fill.getColor().getHexVal());
    }

    // ===== ShapeLine =====

    @Test
    public void testSolidLine() {
        ShapeLine line = ShapeLine.solid(25400, TextColor.hex("000000"));
        assertEquals(Integer.valueOf(25400), line.getWidthEMU());
        assertEquals("000000", line.getColor().getHexVal());
        assertEquals("solid", line.getDashStyle());
    }

    @Test
    public void testThinLine() {
        ShapeLine line = ShapeLine.thin("FF0000");
        assertEquals(Integer.valueOf(12700), line.getWidthEMU());
        assertEquals("FF0000", line.getColor().getHexVal());
    }

    @Test
    public void testCustomDashLine() {
        ShapeLine line = ShapeLine.of(19050, TextColor.scheme("dk1"), "dash");
        assertEquals("dash", line.getDashStyle());
        assertTrue(line.getColor().isScheme());
    }

    // ===== ThemeStyleRef =====

    @Test
    public void testDefaultStyleWithText() {
        ThemeStyleRef ref = ThemeStyleRef.defaultStyle(true);
        assertEquals(2, ref.getLineRefIdx());
        assertEquals("accent1", ref.getLineColor());
        assertEquals("15000", ref.getLineShadeVal());
        assertEquals(1, ref.getFillRefIdx());
        assertEquals("lt1", ref.getFillColor()); // text-bearing: light bg
        assertEquals(0, ref.getEffectRefIdx());
        assertEquals("minor", ref.getFontRefIdx());
        assertEquals("tx1", ref.getFontColor()); // logical text color, resolved via clrMap
    }

    @Test
    public void testDefaultStyleWithoutText() {
        ThemeStyleRef ref = ThemeStyleRef.defaultStyle(false);
        assertEquals("accent1", ref.getFillColor()); // no text: accent fill
    }

    @Test
    public void testCustomThemeRef() {
        ThemeStyleRef ref = ThemeStyleRef.of(1, "accent2", 3, "accent3", 0, "accent4", "major", "dk1");
        assertEquals(1, ref.getLineRefIdx());
        assertEquals("accent2", ref.getLineColor());
        assertNull(ref.getLineShadeVal()); // custom style has no shade
        assertEquals(3, ref.getFillRefIdx());
        assertEquals("accent3", ref.getFillColor());
        assertEquals("major", ref.getFontRefIdx());
    }

    // ===== ShapeStyle =====

    @Test
    public void testDefaultStyle() {
        ShapeStyle style = ShapeStyle.defaultStyle();
        assertNull(style.getFill());
        assertNull(style.getLine());
        assertNull(style.getThemeStyle());
    }

    @Test
    public void testStyleWithFill() {
        ShapeStyle style = ShapeStyle.withFill(ShapeFill.solid("FF0000"));
        assertNotNull(style.getFill());
        assertEquals(FillType.SOLID, style.getFill().getType());
        assertNull(style.getLine());
    }

    @Test
    public void testStyleWithFillAndLine() {
        ShapeStyle style = ShapeStyle.withFillAndLine(
            ShapeFill.scheme("accent2"),
            ShapeLine.thin("000000")
        );
        assertNotNull(style.getFill());
        assertNotNull(style.getLine());
    }

    @Test
    public void testFullySpecifiedStyle() {
        ShapeStyle style = ShapeStyle.of(
            ShapeFill.noFill(),
            ShapeLine.solid(25400, TextColor.hex("FF0000")),
            ThemeStyleRef.defaultStyle(false)
        );
        assertEquals(FillType.NO_FILL, style.getFill().getType());
        assertNotNull(style.getLine());
        assertNotNull(style.getThemeStyle());
    }
}
