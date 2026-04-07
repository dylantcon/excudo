package com.excudo.core.geometry;

/**
 * Parses coordinate strings with unit suffixes to EMUs (English Metric Units).
 *
 * Supported formats:
 *   "100pt" or "100"  -> 100 * 12700 = 1270000 EMU (points, default)
 *   "1270000emu"      -> 1270000 EMU (raw EMU passthrough)
 *   "1.5in"           -> 1.5 * 914400 = 1371600 EMU (inches)
 */
public class UnitParser {

    public static final long EMU_PER_POINT = 12700;
    public static final long EMU_PER_INCH = 914400;

    /**
     * Parse a coordinate string to EMUs.
     *
     * @param value the coordinate string (e.g. "100pt", "1270000emu", "1.5in", "100")
     * @return the value in EMUs
     * @throws IllegalArgumentException if the input is null, empty, or malformed
     */
    public static long parseToEmu(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Coordinate value cannot be null or empty");
        }

        String trimmed = value.trim().toLowerCase();

        if (trimmed.endsWith("emu")) {
            return parseLong(trimmed.substring(0, trimmed.length() - 3), value);
        }

        if (trimmed.endsWith("in")) {
            double inches = parseDouble(trimmed.substring(0, trimmed.length() - 2), value);
            return Math.round(inches * EMU_PER_INCH);
        }

        if (trimmed.endsWith("pt")) {
            double points = parseDouble(trimmed.substring(0, trimmed.length() - 2), value);
            return Math.round(points * EMU_PER_POINT);
        }

        // Bare number defaults to points
        double points = parseDouble(trimmed, value);
        return Math.round(points * EMU_PER_POINT);
    }

    private static long parseLong(String numeric, String original) {
        try {
            return Long.parseLong(numeric.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid coordinate value: \"" + original + "\"");
        }
    }

    private static double parseDouble(String numeric, String original) {
        try {
            return Double.parseDouble(numeric.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid coordinate value: \"" + original + "\"");
        }
    }
}
