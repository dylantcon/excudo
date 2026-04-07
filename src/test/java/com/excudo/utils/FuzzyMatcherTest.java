package com.excudo.utils;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class FuzzyMatcherTest {

    // --- editDistance tests ---

    @Test
    public void editDistance_identicalStrings_returnsZero() {
        assertEquals(0, FuzzyMatcher.editDistance("hello", "hello"));
    }

    @Test
    public void editDistance_caseInsensitive() {
        assertEquals(0, FuzzyMatcher.editDistance("Hello", "HELLO"));
    }

    @Test
    public void editDistance_singleInsertion() {
        assertEquals(1, FuzzyMatcher.editDistance("cat", "cats"));
    }

    @Test
    public void editDistance_singleDeletion() {
        assertEquals(1, FuzzyMatcher.editDistance("cats", "cat"));
    }

    @Test
    public void editDistance_singleSubstitution() {
        assertEquals(1, FuzzyMatcher.editDistance("cat", "car"));
    }

    @Test
    public void editDistance_emptyFirstString() {
        assertEquals(5, FuzzyMatcher.editDistance("", "hello"));
    }

    @Test
    public void editDistance_emptySecondString() {
        assertEquals(5, FuzzyMatcher.editDistance("hello", ""));
    }

    @Test
    public void editDistance_bothEmpty() {
        assertEquals(0, FuzzyMatcher.editDistance("", ""));
    }

    @Test
    public void editDistance_multipleEdits() {
        assertEquals(3, FuzzyMatcher.editDistance("kitten", "sitting"));
    }

    // --- findClosestMatch tests ---

    @Test
    public void findClosestMatch_exactHit() {
        List<String> candidates = Arrays.asList("on-click", "with-previous", "after-previous");
        assertEquals("on-click", FuzzyMatcher.findClosestMatch("on-click", candidates, 3));
    }

    @Test
    public void findClosestMatch_closeTypo() {
        List<String> candidates = Arrays.asList("on-click", "with-previous", "after-previous");
        assertEquals("on-click", FuzzyMatcher.findClosestMatch("on-clck", candidates, 3));
    }

    @Test
    public void findClosestMatch_tooFarReturnsNull() {
        List<String> candidates = Arrays.asList("on-click", "with-previous", "after-previous");
        assertNull(FuzzyMatcher.findClosestMatch("zzzzzzzzz", candidates, 3));
    }

    @Test
    public void findClosestMatch_caseInsensitive() {
        List<String> candidates = Arrays.asList("on-click", "with-previous", "after-previous");
        assertEquals("with-previous", FuzzyMatcher.findClosestMatch("WITH-PREVIOUS", candidates, 3));
    }

    @Test
    public void findClosestMatch_returnsOriginalCandidate() {
        List<String> candidates = Arrays.asList("On-Click", "With-Previous");
        String result = FuzzyMatcher.findClosestMatch("on-click", candidates, 3);
        assertEquals("On-Click", result);
    }

    @Test
    public void findClosestMatch_nullInput_returnsNull() {
        assertNull(FuzzyMatcher.findClosestMatch(null, Arrays.asList("a"), 3));
    }

    @Test
    public void findClosestMatch_nullCandidates_returnsNull() {
        assertNull(FuzzyMatcher.findClosestMatch("test", null, 3));
    }

    @Test
    public void findClosestMatch_emptyCandidates_returnsNull() {
        assertNull(FuzzyMatcher.findClosestMatch("test", Collections.emptyList(), 3));
    }

    // --- Trigger-specific fuzzy matching tests ---

    @Test
    public void triggerFuzzy_wthprevious() {
        List<String> pool = Arrays.asList("onclick", "withprevious", "afterprevious", "click", "with", "after");
        assertEquals("withprevious", FuzzyMatcher.findClosestMatch("wthprevious", pool, 3));
    }

    @Test
    public void triggerFuzzy_aferPrevious() {
        List<String> pool = Arrays.asList("onclick", "withprevious", "afterprevious");
        assertEquals("afterprevious", FuzzyMatcher.findClosestMatch("aferprevious", pool, 3));
    }

    @Test
    public void triggerFuzzy_onClck() {
        List<String> pool = Arrays.asList("onclick", "withprevious", "afterprevious", "click");
        assertEquals("onclick", FuzzyMatcher.findClosestMatch("onclck", pool, 3));
    }

    @Test
    public void triggerFuzzy_clck() {
        List<String> pool = Arrays.asList("onclick", "withprevious", "afterprevious", "click");
        assertEquals("click", FuzzyMatcher.findClosestMatch("clck", pool, 3));
    }
}
