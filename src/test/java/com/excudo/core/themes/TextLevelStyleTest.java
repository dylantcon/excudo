package com.excudo.core.themes;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests TextLevelStyle builder, defaults, and accessors.
 */
public class TextLevelStyleTest {

    @Test
    public void builderCreatesWithDefaults() {
        TextLevelStyle style = TextLevelStyle.builder(0).build();

        assertEquals(0, style.getLevel());
        assertEquals(1800, style.getFontSize()); // 18pt default
        assertFalse(style.isBold());
        assertEquals("tx1", style.getColorRef());
        assertNull(style.getBulletChar());
        assertNull(style.getBulletFont());
        assertEquals(0, style.getMarginLeft());
        assertEquals(0, style.getIndent());
        assertEquals(100000, style.getLineSpacing());
        assertEquals(0, style.getSpaceBefore());
        assertEquals("l", style.getAlignment());
    }

    @Test
    public void builderSetsAllFields() {
        TextLevelStyle style = TextLevelStyle.builder(3)
            .fontSizePt(24)
            .bold(true)
            .colorRef("accent1")
            .bullet("\u2022", "Arial")
            .marginLeft(457200)
            .indent(-228600)
            .lineSpacing(120000)
            .spaceBefore(1000)
            .alignment("ctr")
            .build();

        assertEquals(3, style.getLevel());
        assertEquals(2400, style.getFontSize()); // 24pt * 100
        assertTrue(style.isBold());
        assertEquals("accent1", style.getColorRef());
        assertEquals("\u2022", style.getBulletChar());
        assertEquals("Arial", style.getBulletFont());
        assertEquals(457200, style.getMarginLeft());
        assertEquals(-228600, style.getIndent());
        assertEquals(120000, style.getLineSpacing());
        assertEquals(1000, style.getSpaceBefore());
        assertEquals("ctr", style.getAlignment());
    }

    @Test
    public void hasBulletReturnsCorrectly() {
        TextLevelStyle withBullet = TextLevelStyle.builder(0)
            .bullet("\u2022", "Arial").build();
        assertTrue(withBullet.hasBullet());

        TextLevelStyle noBullet = TextLevelStyle.builder(0).noBullet().build();
        assertFalse(noBullet.hasBullet());

        TextLevelStyle defaultStyle = TextLevelStyle.builder(0).build();
        assertFalse(defaultStyle.hasBullet());
    }

    @Test
    public void isSchemeColorDistinguishesSchemeFromHex() {
        TextLevelStyle schemeColor = TextLevelStyle.builder(0)
            .colorRef("tx1").build();
        assertTrue(schemeColor.isSchemeColor());
        assertNull(schemeColor.getHexColor());

        TextLevelStyle hexColor = TextLevelStyle.builder(0)
            .hexColor("#FF0000").build();
        assertFalse(hexColor.isSchemeColor());
        // getHexColor() strips the '#' prefix
        assertEquals("FF0000", hexColor.getHexColor());
    }

    @Test
    public void fontSizePtConvertsToHundredths() {
        TextLevelStyle style = TextLevelStyle.builder(0).fontSizePt(44).build();
        assertEquals(4400, style.getFontSize());
    }

    @Test
    public void fontSizeDirectSetsHundredths() {
        TextLevelStyle style = TextLevelStyle.builder(0).fontSize(3200).build();
        assertEquals(3200, style.getFontSize());
    }

    @Test(expected = IllegalArgumentException.class)
    public void builderRejectsNegativeLevel() {
        TextLevelStyle.builder(-1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void builderRejectsLevelAboveEight() {
        TextLevelStyle.builder(9);
    }

    @Test
    public void allNineLevelsAreValid() {
        for (int i = 0; i <= 8; i++) {
            TextLevelStyle style = TextLevelStyle.builder(i).build();
            assertEquals(i, style.getLevel());
        }
    }

    @Test
    public void noBulletClearsExistingBullet() {
        TextLevelStyle style = TextLevelStyle.builder(0)
            .bullet("\u2022", "Arial")
            .noBullet()
            .build();
        assertFalse(style.hasBullet());
        assertNull(style.getBulletChar());
    }
}
