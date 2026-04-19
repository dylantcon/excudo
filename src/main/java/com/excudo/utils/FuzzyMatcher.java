package com.excudo.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Levenshtein distance-based fuzzy string matching utility.
 * Provides case-insensitive edit distance calculation and closest-match lookup.
 */
public final class FuzzyMatcher {

    private FuzzyMatcher() {}

    /**
     * Calculate the Levenshtein edit distance between two strings (case-insensitive).
     *
     * @param a first string
     * @param b second string
     * @return the minimum number of single-character edits to transform a into b
     */
    public static int editDistance(String a, String b) {
        String s1 = a.toLowerCase();
        String s2 = b.toLowerCase();

        int[][] dp = new int[s1.length() + 1][s2.length() + 1];

        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }

        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = 1 + Math.min(dp[i - 1][j - 1],
                                   Math.min(dp[i - 1][j], dp[i][j - 1]));
                }
            }
        }

        return dp[s1.length()][s2.length()];
    }

    /**
     * Find the closest matching string from a collection of candidates.
     * Comparison is case-insensitive. Returns the original canonical candidate string.
     *
     * @param input       the input string to match
     * @param candidates  the collection of canonical candidate strings
     * @param maxDistance  the maximum edit distance to consider a match
     * @return the closest matching candidate, or null if none within maxDistance
     */
    public static String findClosestMatch(String input, Collection<String> candidates, int maxDistance) {
        if (input == null || candidates == null) {
            return null;
        }

        String closest = null;
        int minDistance = Integer.MAX_VALUE;

        for (String candidate : candidates) {
            int distance = editDistance(input, candidate);
            if (distance < minDistance && distance <= maxDistance) {
                minDistance = distance;
                closest = candidate;
            }
        }

        return closest;
    }

    /**
     * Find the top-N closest matches from a collection. Useful for
     * "did you mean?" suggestions where a single match is too narrow.
     * Returns matches sorted by edit distance ascending, with at most
     * {@code limit} entries and none above {@code maxDistance}.
     */
    public static List<String> findTopMatches(
            String input, Collection<String> candidates, int limit, int maxDistance) {
        if (input == null || candidates == null) return List.of();

        record Scored(String candidate, int distance) {}
        List<Scored> scored = new ArrayList<>();
        for (String candidate : candidates) {
            int d = editDistance(input, candidate);
            if (d <= maxDistance) scored.add(new Scored(candidate, d));
        }
        scored.sort(Comparator.comparingInt(Scored::distance));

        List<String> out = new ArrayList<>(Math.min(limit, scored.size()));
        for (Scored s : scored) {
            if (out.size() >= limit) break;
            out.add(s.candidate());
        }
        return out;
    }
}
