package com.excudo.core.orchestration;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Tests BatchExecutionResult state tracking for composite operations.
 */
public class BatchExecutionResultTest {

    @Test
    public void allSuccessful() {
        BatchExecutionResult result = new BatchExecutionResult(
            3, 3, 0,
            Arrays.asList("add-shape", "edit-text", "add-animation"),
            Collections.emptyMap());

        assertTrue(result.isSuccess());
        assertEquals(3, result.getTotalOperations());
        assertEquals(3, result.getSuccessfulOperations());
        assertEquals(0, result.getFailedOperations());
        assertEquals(1.0, result.getSuccessRate(), 0.001);
    }

    @Test
    public void partialFailure() {
        BatchExecutionResult result = new BatchExecutionResult(
            4, 3, 1,
            Arrays.asList("op1", "op2", "op3", "op4"),
            Map.of("error", "op4 failed"));

        assertFalse(result.isSuccess());
        assertEquals(4, result.getTotalOperations());
        assertEquals(3, result.getSuccessfulOperations());
        assertEquals(1, result.getFailedOperations());
        assertEquals(0.75, result.getSuccessRate(), 0.001);
    }

    @Test
    public void totalFailure() {
        BatchExecutionResult result = new BatchExecutionResult(
            2, 0, 2,
            Arrays.asList("op1", "op2"),
            Map.of("error", "all failed"));

        assertFalse(result.isSuccess());
        assertEquals(0.0, result.getSuccessRate(), 0.001);
    }

    @Test
    public void emptyBatch() {
        BatchExecutionResult result = new BatchExecutionResult(
            0, 0, 0,
            Collections.emptyList(),
            Collections.emptyMap());

        assertTrue(result.isSuccess());
        assertEquals(0.0, result.getSuccessRate(), 0.001);
    }

    @Test
    public void actionIdsPreserved() {
        BatchExecutionResult result = new BatchExecutionResult(
            2, 2, 0,
            Arrays.asList("add-shape-rect", "set-fill-blue"),
            Collections.emptyMap());

        assertEquals(2, result.getActionIds().size());
        assertEquals("add-shape-rect", result.getActionIds().get(0));
    }

    @Test
    public void resultsMapAccessible() {
        Map<String, Object> results = Map.of("output", "slide1.xml updated", "count", 3);
        BatchExecutionResult result = new BatchExecutionResult(
            1, 1, 0,
            Collections.singletonList("op1"),
            results);

        assertEquals("slide1.xml updated", result.getResults().get("output"));
        assertEquals(3, result.getResults().get("count"));
    }
}
