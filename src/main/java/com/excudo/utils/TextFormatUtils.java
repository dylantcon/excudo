package com.excudo.utils;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Utility methods for markdown-style text detection and cleaning.
 * Text-to-DOM conversion has moved to TextBody + TextBodyXMLWriter.
 */
public class TextFormatUtils {

    private static final Pattern MARKDOWN_BULLET_PATTERN = Pattern.compile("^(\\s*)- (.+)$");
    private static final Pattern NUMBERED_PATTERN = Pattern.compile("^(\\s*)(\\d+)\\. (.+)$");

    /**
     * Checks if text contains markdown bullet markers that need formatting.
     */
    public static boolean containsBulletMarkers(String text) {
        if (text == null) return false;
        String[] lines = text.split("\\n");
        for (String line : lines) {
            if (MARKDOWN_BULLET_PATTERN.matcher(line).matches() ||
                NUMBERED_PATTERN.matcher(line).matches()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Cleans text by removing bullet markers but preserving the structure.
     * Useful for preview purposes.
     */
    public static String cleanBulletMarkers(String text) {
        if (text == null) return null;

        String[] lines = text.split("\\n");
        StringBuilder cleaned = new StringBuilder();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            Matcher bulletMatcher = MARKDOWN_BULLET_PATTERN.matcher(line);
            if (bulletMatcher.matches()) {
                String leadingSpaces = bulletMatcher.group(1);
                cleaned.append(leadingSpaces).append("• ").append(bulletMatcher.group(2).trim());
            } else {
                Matcher numberedMatcher = NUMBERED_PATTERN.matcher(line);
                if (numberedMatcher.matches()) {
                    String leadingSpaces = numberedMatcher.group(1);
                    String number = numberedMatcher.group(2);
                    cleaned.append(leadingSpaces).append(number).append(". ")
                           .append(numberedMatcher.group(3).trim());
                } else {
                    cleaned.append(line);
                }
            }

            if (i < lines.length - 1) {
                cleaned.append("\n");
            }
        }

        return cleaned.toString();
    }
}
