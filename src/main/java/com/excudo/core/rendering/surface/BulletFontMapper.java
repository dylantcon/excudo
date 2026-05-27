package com.excudo.core.rendering.surface;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Translates bullet characters from legacy symbol fonts (Wingdings,
 * Wingdings 2, Wingdings 3, Symbol, Webdings) into their visually
 * equivalent Unicode codepoints.
 *
 * <p>PowerPoint stores list bullets as a (character, font) pair: e.g.
 * {@code <a:buChar char="l"/> <a:buFont typeface="Wingdings"/>}. The
 * codepoint {@code 0x6C} ({@code l}) in Wingdings renders as a black
 * circle (U+25CF). On a host without Wingdings installed (most Linux
 * boxes, headless CI), naively rendering the literal codepoint in the
 * fallback Latin font produces garbage glyphs -- the user sees the
 * letter {@code l} instead of {@code ●}.
 *
 * <p>This mapper preempts that by translating the symbol-font codepoint
 * to its Unicode equivalent at render time. The caller renders the
 * translated string in the body font (DejaVu Sans / Calibri / etc.),
 * which has the Unicode glyphs natively. Result: PowerPoint-style bullet
 * appearance regardless of host font availability.
 *
 * <p>Coverage is bullet-focused -- only the codepoints commonly chosen
 * for list bullets are mapped (geometric shapes, stars, check/cross
 * marks). Less common Wingdings codepoints (clocks, scissors, mailbox)
 * pass through untranslated; rendering those at all from raw OOXML is
 * out of scope for the bullet path.
 */
public final class BulletFontMapper {

    /** Symbol-font family names recognized for translation. Case-insensitive. */
    private static final Set<String> SYMBOL_FONTS = Set.of(
        "wingdings",
        "wingdings 2",
        "wingdings 3",
        "symbol",
        "webdings"
    );

    /** Wingdings codepoint -> Unicode replacement. Compiled at class init. */
    private static final Map<Integer, String> WINGDINGS = buildWingdings();

    /** Wingdings 2 codepoint -> Unicode replacement. */
    private static final Map<Integer, String> WINGDINGS_2 = buildWingdings2();

    /** Wingdings 3 codepoint -> Unicode replacement. */
    private static final Map<Integer, String> WINGDINGS_3 = buildWingdings3();

    /** Symbol codepoint -> Unicode replacement. */
    private static final Map<Integer, String> SYMBOL = buildSymbol();

    private BulletFontMapper() {}

    /**
     * True if {@code fontFamily} is one of the recognized symbol fonts.
     * Case-insensitive; trims whitespace.
     */
    public static boolean isSymbolFont(String fontFamily) {
        if (fontFamily == null) return false;
        return SYMBOL_FONTS.contains(fontFamily.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * Translate {@code text} from {@code fontFamily}'s symbol encoding to
     * Unicode. Each codepoint in {@code text} is looked up in the symbol
     * font's table; misses pass through unchanged.
     *
     * <p>If {@code fontFamily} is not a recognized symbol font, returns
     * {@code text} unchanged.
     *
     * <p>Most callers will be translating single characters (bullet
     * markers), but the method handles arbitrary strings so the same
     * primitive can later cover inline symbol-font runs.
     */
    public static String translate(String fontFamily, String text) {
        if (text == null || text.isEmpty()) return text;
        Map<Integer, String> table = tableFor(fontFamily);
        if (table == null) return text;

        StringBuilder out = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            int cp = text.codePointAt(i);
            String replacement = table.get(cp);
            if (replacement != null) {
                out.append(replacement);
            } else {
                out.appendCodePoint(cp);
            }
            i += Character.charCount(cp);
        }
        return out.toString();
    }

    private static Map<Integer, String> tableFor(String fontFamily) {
        if (fontFamily == null) return null;
        String key = fontFamily.trim().toLowerCase(Locale.ROOT);
        return switch (key) {
            case "wingdings"   -> WINGDINGS;
            case "wingdings 2" -> WINGDINGS_2;
            case "wingdings 3" -> WINGDINGS_3;
            case "symbol"      -> SYMBOL;
            // Webdings is dominated by icon glyphs (cars, phones, hands) that
            // have no clean Unicode analogue. Pass through for now -- a
            // Webdings-bulleted list will still render the wrong glyph, but
            // it's the same broken state we had before; bullet use of
            // Webdings is rare.
            case "webdings"    -> null;
            default            -> null;
        };
    }

    /**
     * Wingdings 1.0 bullet-relevant character map.
     *
     * <p>Sources: Microsoft Typography's published Wingdings character
     * table, cross-checked against the Wikipedia Wingdings article. The
     * subset chosen here covers what PowerPoint's "Bullets and Numbering"
     * dialog offers as default Wingdings bullet options, plus the
     * check/cross marks ({@code üýþÿ} range) that appear in templated
     * decks.
     */
    private static Map<Integer, String> buildWingdings() {
        Map<Integer, String> m = new HashMap<>();
        // Geometric shapes (the canonical "bullet" range)
        m.put(0x6C, "●"); // l -> BLACK CIRCLE
        m.put(0x6D, "❍"); // m -> SHADOWED WHITE CIRCLE
        m.put(0x6E, "■"); // n -> BLACK SQUARE
        m.put(0x6F, "□"); // o -> WHITE SQUARE
        m.put(0x70, "❑"); // p -> LOWER RIGHT SHADOWED WHITE SQUARE
        m.put(0x71, "❒"); // q -> UPPER RIGHT SHADOWED WHITE SQUARE
        m.put(0x72, "◆"); // r -> BLACK DIAMOND
        m.put(0x73, "◇"); // s -> WHITE DIAMOND
        m.put(0x74, "❖"); // t -> BLACK DIAMOND MINUS WHITE X
        m.put(0x75, "✵"); // u -> EIGHT POINTED PINWHEEL STAR
        m.put(0x76, "❖"); // v -> BLACK DIAMOND MINUS WHITE X
        m.put(0x77, "✦"); // w -> BLACK FOUR POINTED STAR
        m.put(0x78, "★"); // x -> BLACK STAR
        m.put(0x79, "❂"); // y -> CIRCLED OPEN CENTRE EIGHT POINTED STAR
        m.put(0x7A, "✷"); // z -> EIGHT POINTED RECTILINEAR BLACK STAR

        // Sub-bullet diamond. PowerPoint's default level-2/3 bullet is
        // Wingdings 0xA7 ("§"), which renders as a small filled diamond
        // shape. The most visually consistent Unicode equivalent is
        // BLACK DIAMOND (U+25C6) -- a hollow-diamond mapping like the
        // shadowed-diamond glyph (U+2756) usually has worse cross-font
        // coverage and falls back to a missing-glyph box on DejaVu.
        m.put(0xA7, "◆"); // § -> BLACK DIAMOND

        // Bracketed numerals (also occasionally used as ordered-list bullets)
        m.put(0xA8, "○"); // ¨ -> WHITE CIRCLE

        // Check / cross marks (Wingdings 0xFB-0xFF range)
        m.put(0xFB, "✓"); // û -> CHECK MARK
        m.put(0xFC, "✔"); // ü -> HEAVY CHECK MARK
        m.put(0xFD, "✗"); // ý -> BALLOT X
        m.put(0xFE, "✘"); // þ -> HEAVY BALLOT X

        // Standalone ballot X (used as "no" marker in some PowerPoint templates)
        m.put(0xD8, "✗"); // Ø -> BALLOT X

        return Map.copyOf(m);
    }

    /**
     * Wingdings 2 -- predominantly arrow and shape variants. Smaller
     * subset because list-bullet usage is rarer than Wingdings 1.
     */
    private static Map<Integer, String> buildWingdings2() {
        Map<Integer, String> m = new HashMap<>();
        m.put(0xA1, "●"); // ¡ -> BLACK CIRCLE
        m.put(0xA2, "○"); // ¢ -> WHITE CIRCLE
        m.put(0xA3, "■"); // £ -> BLACK SQUARE
        m.put(0xA4, "□"); // ¤ -> WHITE SQUARE
        // Wingdings 2 keeps the same check/cross at lower codepoints
        m.put(0x50, "✓"); // P -> CHECK MARK
        m.put(0x51, "✔"); // Q -> HEAVY CHECK MARK
        m.put(0x52, "✗"); // R -> BALLOT X
        m.put(0x53, "✘"); // S -> HEAVY BALLOT X
        return Map.copyOf(m);
    }

    /**
     * Wingdings 3 -- arrow-heavy. Bullet codepoints overlap with arrows;
     * sparsely populated because lists rarely use this font.
     */
    private static Map<Integer, String> buildWingdings3() {
        Map<Integer, String> m = new HashMap<>();
        m.put(0xA8, "←"); // ¨ -> LEFTWARDS ARROW
        m.put(0xA9, "→"); // © -> RIGHTWARDS ARROW
        m.put(0xAA, "↑"); // ª -> UPWARDS ARROW
        m.put(0xAB, "↓"); // « -> DOWNWARDS ARROW
        return Map.copyOf(m);
    }

    /**
     * Symbol -- Adobe's mathematical / Greek font. Bullet uses are
     * limited to the bullet operator and a few geometric forms.
     */
    private static Map<Integer, String> buildSymbol() {
        Map<Integer, String> m = new HashMap<>();
        m.put(0xB7, "•"); // · -> BULLET (most common Symbol-font bullet)
        m.put(0xA8, "◇"); // ¨ -> WHITE DIAMOND
        m.put(0xB0, "°"); // ° -> DEGREE SIGN (passes through; here for clarity)
        m.put(0x2A, "∗"); // * -> ASTERISK OPERATOR
        return Map.copyOf(m);
    }
}
