package com.excudo.core.smartcontent;

import java.util.Map;
import java.util.Set;

/**
 * Lightweight query normalizer for icon search.
 *
 * Only handles plurals: "databases" -> "database", "technologies" -> "technology".
 * Does NOT strip derivational suffixes (-ing, -tion, -ment, etc.) because those
 * change meaning: "shipping" is not "ship", "networking" is not "network".
 */
final class QueryLemmatizer {

    private QueryLemmatizer() {}

    // Irregular plurals where suffix rules fail
    private static final Map<String, String> IRREGULARS = Map.ofEntries(
        Map.entry("children", "child"),
        Map.entry("people", "person"),
        Map.entry("mice", "mouse"),
        Map.entry("analyses", "analysis"),
        Map.entry("indices", "index"),
        Map.entry("matrices", "matrix"),
        Map.entry("vertices", "vertex"),
        Map.entry("appendices", "appendix"),
        Map.entry("crises", "crisis"),
        Map.entry("theses", "thesis"),
        Map.entry("criteria", "criterion"),
        Map.entry("phenomena", "phenomenon"),
        Map.entry("schemas", "schema"),
        Map.entry("automata", "automaton")
    );

    // Words ending in -s that are NOT plurals
    private static final Set<String> NOT_PLURAL = Set.of(
        "this", "his", "is", "has", "was", "does", "goes", "bus", "gas",
        "yes", "us", "plus", "minus", "atlas", "bias", "canvas", "class",
        "dress", "boss", "loss", "moss", "cross", "gross", "chess", "less",
        "mess", "pass", "miss", "kiss", "press", "stress", "access",
        "address", "process", "progress", "success", "business", "analysis",
        "basis", "series", "species", "thesis", "crisis", "diagnosis",
        "status", "campus", "focus", "virus", "bonus", "corpus", "consensus",
        "terminus", "apparatus", "nexus", "surplus", "radius", "genius",
        "kubernetes", "postgres", "jenkins", "graphcs", "dynamics",
        "graphics", "statistics", "mathematics", "physics", "economics",
        "electronics", "robotics", "logistics", "analytics", "devops"
    );

    /**
     * Normalize a search query: lowercase, trim, depluralize each token.
     */
    static String lemmatize(String query) {
        if (query == null || query.isBlank()) {
            return query;
        }

        String lower = query.trim().toLowerCase();

        if (lower.contains(" ")) {
            String[] tokens = lower.split("\\s+");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < tokens.length; i++) {
                if (i > 0) sb.append(' ');
                sb.append(depluralize(tokens[i]));
            }
            return sb.toString();
        }

        return depluralize(lower);
    }

    private static String depluralize(String word) {
        if (word.length() <= 3 || NOT_PLURAL.contains(word)) {
            return word;
        }

        String irregular = IRREGULARS.get(word);
        if (irregular != null) {
            return irregular;
        }

        // -sses -> -ss (classes -> class)
        if (word.endsWith("sses")) {
            return word.substring(0, word.length() - 2);
        }

        // -shes -> -sh (crashes -> crash)
        if (word.endsWith("shes")) {
            return word.substring(0, word.length() - 2);
        }

        // -ches -> -ch (searches -> search)
        if (word.endsWith("ches")) {
            return word.substring(0, word.length() - 2);
        }

        // -xes -> -x (boxes -> box)
        if (word.endsWith("xes")) {
            return word.substring(0, word.length() - 2);
        }

        // -zes -> -z (quizzes already handled by double-s rule above if applicable)
        if (word.endsWith("zes")) {
            return word.substring(0, word.length() - 2);
        }

        // -ies -> -y (technologies -> technology)
        if (word.endsWith("ies") && word.length() > 4) {
            return word.substring(0, word.length() - 3) + "y";
        }

        // -ves -> -fe (knives -> knife)
        if (word.endsWith("ves") && word.length() > 4) {
            return word.substring(0, word.length() - 3) + "fe";
        }

        // Plain -s but not -ss (databases -> database)
        if (word.endsWith("s") && !word.endsWith("ss")) {
            return word.substring(0, word.length() - 1);
        }

        return word;
    }
}
