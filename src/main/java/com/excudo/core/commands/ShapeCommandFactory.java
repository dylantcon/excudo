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
        String lower = val.toLowerCase();
        return lower.startsWith("accent") || lower.startsWith("dk") || lower.startsWith("lt")
            || "hlink".equals(lower) || "folhlink".equals(lower);
    }
}
