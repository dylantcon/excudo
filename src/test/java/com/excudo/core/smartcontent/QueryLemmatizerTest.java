package com.excudo.core.smartcontent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for QueryLemmatizer -- plural-only normalizer for icon search queries.
 */
public class QueryLemmatizerTest {

    @Test
    @DisplayName("Regular plural -s stripped")
    void regularPlural() {
        assertEquals("database", QueryLemmatizer.lemmatize("databases"));
        assertEquals("network", QueryLemmatizer.lemmatize("networks"));
        assertEquals("chart", QueryLemmatizer.lemmatize("charts"));
        assertEquals("icon", QueryLemmatizer.lemmatize("icons"));
    }

    @Test
    @DisplayName("-es plurals")
    void esPlurals() {
        assertEquals("class", QueryLemmatizer.lemmatize("classes"));
        assertEquals("crash", QueryLemmatizer.lemmatize("crashes"));
        assertEquals("search", QueryLemmatizer.lemmatize("searches"));
        assertEquals("box", QueryLemmatizer.lemmatize("boxes"));
    }

    @Test
    @DisplayName("-ies -> -y")
    void iesPlurals() {
        assertEquals("technology", QueryLemmatizer.lemmatize("technologies"));
        assertEquals("battery", QueryLemmatizer.lemmatize("batteries"));
        assertEquals("security", QueryLemmatizer.lemmatize("securities"));
    }

    @Test
    @DisplayName("Irregular plurals")
    void irregulars() {
        assertEquals("analysis", QueryLemmatizer.lemmatize("analyses"));
        assertEquals("index", QueryLemmatizer.lemmatize("indices"));
        assertEquals("criterion", QueryLemmatizer.lemmatize("criteria"));
    }

    @Test
    @DisplayName("Derivational suffixes are NOT stripped")
    void noDerivationalStripping() {
        assertEquals("shipping", QueryLemmatizer.lemmatize("shipping"));
        assertEquals("networking", QueryLemmatizer.lemmatize("networking"));
        assertEquals("encryption", QueryLemmatizer.lemmatize("encryption"));
        assertEquals("deployment", QueryLemmatizer.lemmatize("deployment"));
        assertEquals("computing", QueryLemmatizer.lemmatize("computing"));
        assertEquals("programming", QueryLemmatizer.lemmatize("programming"));
    }

    @Test
    @DisplayName("Words ending in -s that are not plurals stay intact")
    void notPlural() {
        assertEquals("process", QueryLemmatizer.lemmatize("process"));
        assertEquals("access", QueryLemmatizer.lemmatize("access"));
        assertEquals("business", QueryLemmatizer.lemmatize("business"));
        assertEquals("analysis", QueryLemmatizer.lemmatize("analysis"));
        assertEquals("kubernetes", QueryLemmatizer.lemmatize("kubernetes"));
        assertEquals("analytics", QueryLemmatizer.lemmatize("analytics"));
        assertEquals("devops", QueryLemmatizer.lemmatize("devops"));
    }

    @Test
    @DisplayName("Already singular passes through")
    void alreadySingular() {
        assertEquals("database", QueryLemmatizer.lemmatize("database"));
        assertEquals("cloud", QueryLemmatizer.lemmatize("cloud"));
        assertEquals("security", QueryLemmatizer.lemmatize("security"));
    }

    @Test
    @DisplayName("Multi-word queries")
    void multiWord() {
        assertEquals("database security", QueryLemmatizer.lemmatize("databases security"));
        assertEquals("cloud network", QueryLemmatizer.lemmatize("cloud networks"));
    }

    @Test
    @DisplayName("Lowercased and trimmed")
    void normalization() {
        assertEquals("database", QueryLemmatizer.lemmatize("Database"));
        assertEquals("cloud", QueryLemmatizer.lemmatize("  CLOUD  "));
    }

    @Test
    @DisplayName("Null and blank handled")
    void nullBlank() {
        assertNull(QueryLemmatizer.lemmatize(null));
        assertEquals("   ", QueryLemmatizer.lemmatize("   "));
    }

    @Test
    @DisplayName("Short words not mangled")
    void shortWords() {
        assertEquals("go", QueryLemmatizer.lemmatize("go"));
        assertEquals("us", QueryLemmatizer.lemmatize("us"));
        assertEquals("is", QueryLemmatizer.lemmatize("is"));
    }
}
