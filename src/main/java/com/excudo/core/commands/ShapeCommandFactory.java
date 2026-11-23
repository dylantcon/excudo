package com.excudo.core.commands;

import com.excudo.core.model.ShapeFill;
import com.excudo.core.model.ShapeLine;
import com.excudo.core.model.ShapeStyle;
import com.excudo.core.model.TextColor;

/**
 * Static helpers shared by class-registered shape commands. After the
 * command-self-description sweep, this class is no longer a dispatch
 * factory -- it just owns shape-style + alignment parsing used by
 * AddShapeCommand / SetStyleCommand / etc. via static method references.
 */
public final class ShapeCommandFactory {

    private ShapeCommandFactory() {}

    /**
     * Normalize an alignment input ("left" / "l" / "center" / "ctr" /
     * "right" / "r" / "justify" / "just") to the canonical OOXML token
     * ("l" / "ctr" / "r" / "just"). Returns null if the input is null
     * or blank, signaling "use default alignment." Throws on
     * unrecognized values rather than silently dropping them so the
     * agent gets immediate feedback.
     */
    public static String normalizeAlignment(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String v = raw.trim().toLowerCase();
        switch (v) {
            case "l": case "left":    return "l";
            case "ctr": case "center": case "centre": return "ctr";
            case "r": case "right":   return "r";
            case "just": case "justify": return "just";
            default:
                throw new IllegalArgumentException(
                    "Unrecognised alignment: '" + raw
                    + "'. Use one of: l/left, ctr/center, r/right, just/justify.");
        }
    }

    public static ShapeStyle parseShapeStyle(String fillColor, String lineColor) {
        return parseShapeStyle(fillColor, lineColor, null, null);
    }

    /**
     * Build a ShapeStyle from explicit color + opacity params. Alpha values
     * are percentages 0-100; null leaves the channel fully opaque (no
     * a:alpha emitted).
     */
    public static ShapeStyle parseShapeStyle(String fillColor, String lineColor,
                                             Integer fillAlphaPercent, Integer lineAlphaPercent) {
        ShapeFill fill = null;
        ShapeLine line = null;

        if (fillColor != null && !fillColor.isEmpty()) {
            fill = isSchemeColor(fillColor)
                ? ShapeFill.scheme(fillColor)
                : ShapeFill.solid(fillColor);
            if (fillAlphaPercent != null) fill = fill.withAlphaPercent(fillAlphaPercent);
        }

        if (lineColor != null && !lineColor.isEmpty()) {
            TextColor lc = isSchemeColor(lineColor)
                ? TextColor.scheme(lineColor)
                : TextColor.hex(lineColor);
            line = ShapeLine.solid(12700, lc); // 1pt default width
            if (lineAlphaPercent != null) line = line.withAlphaPercent(lineAlphaPercent);
        }

        if (fill == null && line == null) return null;
        return ShapeStyle.withFillAndLine(fill, line);
    }

    private static boolean isSchemeColor(String val) {
        return com.excudo.core.model.TextColor.isSchemeName(val);
    }

    /**
     * A colour as written to OOXML: six hex digits (a leading # is dropped)
     * or a theme colour name. Anything else used to be written as-is
     * ({@code <a:srgbClr val="red"/>}), which PowerPoint has to repair.
     */
    public static String normalizeColor(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String v = raw.trim();
        if (v.startsWith("#")) v = v.substring(1);
        if (v.matches("[0-9A-Fa-f]{6}")) return v.toUpperCase();
        String scheme = com.excudo.core.model.TextColor.schemeName(v);
        if (scheme != null) return scheme;
        throw new IllegalArgumentException("Unrecognised colour '" + raw + "'. Use six hex digits (e.g. FF0000) "
            + "or a theme colour: accent1-accent6, dk1, lt1, dk2, lt2, tx1, bg1, tx2, bg2, hlink, folHlink.");
    }

    /** A font size in points, 1 to 4000 (what OOXML can hold). */
    public static Integer parseFontSize(String raw) {
        int size;
        try {
            size = (int) Math.round(Double.parseDouble(raw.trim()));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Font size must be a number of points, not '" + raw + "'");
        }
        if (size < 1 || size > 4000) {
            throw new IllegalArgumentException("Font size must be 1 to 4000 points, not " + raw);
        }
        return size;
    }

    /** true/false (or 1/0, yes/no); anything else is an error rather than false. */
    public static String normalizeBoolean(String raw) {
        if (raw == null || raw.isBlank()) return null;
        switch (raw.trim().toLowerCase()) {
            case "true": case "1": case "yes": return "true";
            case "false": case "0": case "no": return "false";
            default: throw new IllegalArgumentException("Expected true or false, not '" + raw + "'");
        }
    }
}
