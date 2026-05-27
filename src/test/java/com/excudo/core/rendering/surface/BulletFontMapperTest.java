package com.excudo.core.rendering.surface;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for {@link BulletFontMapper}: symbol-font detection, Wingdings
 * codepoint translation, pass-through for non-symbol fonts.
 */
public class BulletFontMapperTest {

    // ========== Symbol-font detection ==========

    @Test
    public void testIsSymbolFontRecognizesWingdingsFamily() {
        assertTrue(BulletFontMapper.isSymbolFont("Wingdings"));
        assertTrue(BulletFontMapper.isSymbolFont("Wingdings 2"));
        assertTrue(BulletFontMapper.isSymbolFont("Wingdings 3"));
    }

    @Test
    public void testIsSymbolFontRecognizesSymbolAndWebdings() {
        assertTrue(BulletFontMapper.isSymbolFont("Symbol"));
        assertTrue(BulletFontMapper.isSymbolFont("Webdings"));
    }

    @Test
    public void testIsSymbolFontIsCaseInsensitive() {
        assertTrue(BulletFontMapper.isSymbolFont("WINGDINGS"));
        assertTrue(BulletFontMapper.isSymbolFont("wingdings"));
        assertTrue(BulletFontMapper.isSymbolFont("wInGdInGs 2"));
    }

    @Test
    public void testIsSymbolFontTrimsWhitespace() {
        assertTrue(BulletFontMapper.isSymbolFont("  Wingdings  "));
    }

    @Test
    public void testIsSymbolFontRejectsNonSymbolFonts() {
        assertFalse(BulletFontMapper.isSymbolFont("Arial"));
        assertFalse(BulletFontMapper.isSymbolFont("Courier New"));
        assertFalse(BulletFontMapper.isSymbolFont("Calibri"));
        assertFalse(BulletFontMapper.isSymbolFont("DejaVu Sans"));
    }

    @Test
    public void testIsSymbolFontHandlesNull() {
        assertFalse(BulletFontMapper.isSymbolFont(null));
    }

    // ========== Wingdings translation ==========

    @Test
    public void testWingdingsCanonicalBullets() {
        // The most common bullet codepoints encountered in PowerPoint decks.
        assertEquals("●", BulletFontMapper.translate("Wingdings", "l"));   // 0x6C
        assertEquals("□", BulletFontMapper.translate("Wingdings", "o"));   // 0x6F
        assertEquals("❒", BulletFontMapper.translate("Wingdings", "q"));   // 0x71
        assertEquals("❖", BulletFontMapper.translate("Wingdings", "v"));   // 0x76
    }

    @Test
    public void testWingdingsSubBulletDiamond() {
        // 0xA7 (§) is PowerPoint's default level-2/3 sub-bullet.
        assertEquals("◆", BulletFontMapper.translate("Wingdings", "§"));
    }

    @Test
    public void testWingdingsCheckAndCrossMarks() {
        assertEquals("✗", BulletFontMapper.translate("Wingdings", "Ø"));   // 0xD8
        assertEquals("✔", BulletFontMapper.translate("Wingdings", "ü"));   // 0xFC
        assertEquals("✓", BulletFontMapper.translate("Wingdings", "û"));   // 0xFB
        assertEquals("✘", BulletFontMapper.translate("Wingdings", "þ"));   // 0xFE
    }

    @Test
    public void testWingdingsUntranslatedCodepointsPassThrough() {
        // 0x41 ("A") isn't in the bullet-relevant Wingdings range; the
        // mapper leaves it alone rather than guessing.
        assertEquals("A", BulletFontMapper.translate("Wingdings", "A"));
    }

    @Test
    public void testSymbolBulletOperator() {
        // 0xB7 in Symbol font is the bullet operator -- the canonical
        // "Symbol-font bullet" choice in older PowerPoint templates.
        assertEquals("•", BulletFontMapper.translate("Symbol", "·"));
    }

    // ========== Pass-through for non-symbol fonts ==========

    @Test
    public void testNonSymbolFontPassesThrough() {
        // A Latin "o" rendered in Courier New is the template author's
        // deliberate faux-bullet -- mapper must NOT translate it.
        assertEquals("o", BulletFontMapper.translate("Courier New", "o"));
        assertEquals("•", BulletFontMapper.translate("Arial", "•"));
    }

    @Test
    public void testNullFontPassesThrough() {
        assertEquals("•", BulletFontMapper.translate(null, "•"));
    }

    @Test
    public void testNullOrEmptyTextHandled() {
        assertNull(BulletFontMapper.translate("Wingdings", null));
        assertEquals("", BulletFontMapper.translate("Wingdings", ""));
    }

    // ========== Multi-character strings ==========

    @Test
    public void testMultiCharacterTranslation() {
        // Synthetic case (real bullets are single chars), but verifies the
        // codepoint iteration handles longer strings correctly.
        assertEquals("●□❖", BulletFontMapper.translate("Wingdings", "lov"));
    }

    @Test
    public void testMixedMappedAndUnmapped() {
        // "l" maps to ●; "A" doesn't map. Expect "●A".
        assertEquals("●A", BulletFontMapper.translate("Wingdings", "lA"));
    }
}
