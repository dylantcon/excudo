package com.excudo.core.themes;

/**
 * Typed hex color value — the Adapter between stored theme data and
 * consumers that need different string formats ({@code #RRGGBB} vs
 * {@code RRGGBB}).
 *
 * <p>Before this type existed, theme color strings flowed through the
 * code as plain {@code String}, with an implicit contract "stored with
 * '#' prefix" that every call site had to remember. Multiple sites
 * forgot and produced {@code "#" + "#RRGGBB" = "##RRGGBB"} —
 * tripping the downstream validator at {@code SurfacePaint.fromHex}.
 *
 * <p>With this type, the contract is encoded in the API: the internal
 * storage is bare hex, and consumers explicitly choose {@link #withHash()}
 * or {@link #bare()} at the point of use. Double-prefix is a compile
 * impossibility — {@code "#" + hexColor} doesn't compile; you have to
 * pick a representation.
 */
public record HexColor(String bare) {

    public HexColor {
        if (bare == null) throw new NullPointerException("hex is null");
        if (bare.length() != 6 && bare.length() != 8) {
            throw new IllegalArgumentException(
                "HexColor bare form must be 6 or 8 hex chars (RGB/ARGB): " + bare);
        }
        for (int i = 0; i < bare.length(); i++) {
            char c = bare.charAt(i);
            boolean ok = (c >= '0' && c <= '9')
                      || (c >= 'a' && c <= 'f')
                      || (c >= 'A' && c <= 'F');
            if (!ok) throw new IllegalArgumentException(
                "Invalid hex character '" + c + "' in: " + bare);
        }
    }

    /** Tolerant parser: accepts either {@code "#RRGGBB"} or {@code "RRGGBB"}
     *  (and 8-char ARGB variants). Strips a single leading '#' if present. */
    public static HexColor from(String s) {
        if (s == null) throw new NullPointerException("hex is null");
        return new HexColor(s.startsWith("#") ? s.substring(1) : s);
    }

    /** Display / CSS-compatible form: {@code "#RRGGBB"}. */
    public String withHash() { return "#" + bare; }

    /** Bare form: {@code "RRGGBB"}. Use when writing OOXML {@code val}
     *  attributes or other contexts that must not include the '#'. */
    @Override
    public String bare() { return bare; }

    /** Return the display form. Intended for logging / error messages;
     *  prefer {@link #withHash()} at load-bearing call sites so intent
     *  is explicit. */
    @Override
    public String toString() { return withHash(); }
}
