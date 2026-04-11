package com.excudo.core.results;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Tests SlideExecutionResult factory methods for slide CRUD operations.
 */
public class SlideExecutionResultTest {

    @Test
    public void slideCreatedSuccess() {
        SlideExecutionResult result = SlideExecutionResult.slideCreated(
            1, "New Slide", Arrays.asList(2, 3, 4));

        assertTrue(result.isSuccess());
        assertEquals("Slide Creation", result.getExecutionType());
        assertTrue(result.getData().isPresent());
    }

    @Test
    public void slideDeletedSuccess() {
        SlideExecutionResult result = SlideExecutionResult.slideDeleted(3);

        assertTrue(result.isSuccess());
        assertEquals("Slide Deletion", result.getExecutionType());
        assertTrue(result.getData().isPresent());
    }

    @Test
    public void slideCopiedSuccess() {
        SlideExecutionResult result = SlideExecutionResult.slideCopied(
            1, 2, Set.of(2, 3), Set.of(5, 6));

        assertTrue(result.isSuccess());
        assertTrue(result.getData().isPresent());
    }

    @Test
    public void resultHasTimestamp() {
        SlideExecutionResult result = SlideExecutionResult.slideCreated(
            1, "Test", Collections.emptyList());
        assertNotNull(result.getTimestamp());
    }

    @Test
    public void getDetailedSummaryNonNull() {
        SlideExecutionResult result = SlideExecutionResult.slideCreated(
            1, "Test", Arrays.asList(2, 3));
        assertNotNull(result.getDetailedSummary());
    }

    @Test
    public void getMessageNonNull() {
        SlideExecutionResult result = SlideExecutionResult.slideDeleted(1);
        assertNotNull(result.getMessage());
    }
}
