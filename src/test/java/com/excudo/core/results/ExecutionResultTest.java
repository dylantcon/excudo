package com.excudo.core.results;

import com.excudo.core.validation.ValidationResult;
import org.junit.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.Assert.*;

/**
 * Tests ExecutionResult factory methods, state queries, and map/transform.
 */
public class ExecutionResultTest {

    @Test
    public void successWithData() {
        ExecutionResult<String> result = ExecutionResult.success("AddShape", "Created SPID 5");
        assertTrue(result.isSuccess());
        assertFalse(result.isFailure());
        assertEquals("AddShape", result.getExecutionType());
        assertTrue(result.getData().isPresent());
        assertEquals("Created SPID 5", result.getData().get());
        assertNotNull(result.getTimestamp());
    }

    @Test
    public void successWithNullData() {
        ExecutionResult<Void> result = ExecutionResult.success("Save", null);
        assertTrue(result.isSuccess());
        assertFalse(result.getData().isPresent());
    }

    @Test
    public void failureWithMessage() {
        ExecutionResult<String> result = ExecutionResult.failure("AddShape", "Shape not found");
        assertFalse(result.isSuccess());
        assertTrue(result.isFailure());
        assertEquals("Shape not found", result.getMessage());
        assertFalse(result.getData().isPresent());
    }

    @Test
    public void failureWithException() {
        Exception ex = new RuntimeException("disk full");
        ExecutionResult<String> result = ExecutionResult.failure("Save", "Save failed", ex);
        assertFalse(result.isSuccess());
        assertTrue(result.getException().isPresent());
        assertEquals("disk full", result.getException().get().getMessage());
    }

    @Test
    public void getMessageForSuccess() {
        ExecutionResult<String> result = ExecutionResult.success("Op", "data");
        // getMessage returns a description even for success
        assertNotNull(result.getMessage());
    }

    @Test
    public void getErrorMessageForFailure() {
        ExecutionResult<String> result = ExecutionResult.failure("Op", "something broke");
        assertNotNull(result.getErrorMessage());
    }

    @Test
    public void timestampIsRecent() {
        Instant before = Instant.now();
        ExecutionResult<String> result = ExecutionResult.success("Op", "data");
        Instant after = Instant.now();

        assertFalse(result.getTimestamp().isBefore(before));
        assertFalse(result.getTimestamp().isAfter(after));
    }

    @Test
    public void getDetailedSummaryReturnsNonNull() {
        ExecutionResult<String> success = ExecutionResult.success("AddShape", "done");
        assertNotNull(success.getDetailedSummary());

        ExecutionResult<String> failure = ExecutionResult.failure("AddShape", "failed");
        assertNotNull(failure.getDetailedSummary());
    }

    @Test
    public void mapTransformsData() {
        ExecutionResult<Integer> original = ExecutionResult.success("Count", 42);
        ExecutionResult<String> mapped = original.map(n -> "count=" + n);

        assertTrue(mapped.isSuccess());
        assertEquals("count=42", mapped.getData().get());
    }

    @Test
    public void mapOnFailurePreservesError() {
        ExecutionResult<Integer> failure = ExecutionResult.failure("Count", "no data");
        ExecutionResult<String> mapped = failure.map(n -> "count=" + n);

        assertFalse(mapped.isSuccess());
        assertFalse(mapped.getData().isPresent());
    }
}
