package com.excudo.core.results;

import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;

/**
 * Tests SavePresentationResult factory methods and accessors.
 */
public class SavePresentationResultTest {

    @Test
    public void successResult() {
        File outFile = new File("/tmp/test.pptx");
        SavePresentationResult result = SavePresentationResult.success(outFile, 12345, 5);

        assertTrue(result.isSuccess());
        assertTrue(result.getOutputFile().isPresent());
        assertEquals(outFile, result.getOutputFile().get());
        assertEquals(12345, result.getFileSize());
    }

    @Test
    public void failureWithMessage() {
        SavePresentationResult result = SavePresentationResult.failure("Disk full");

        assertFalse(result.isSuccess());
        assertNotNull(result.getMessage());
    }

    @Test
    public void failureWithException() {
        Exception ex = new RuntimeException("IO error");
        SavePresentationResult result = SavePresentationResult.failure(ex);

        assertFalse(result.isSuccess());
        assertTrue(result.getException().isPresent());
    }

    @Test
    public void timestampPresent() {
        SavePresentationResult result = SavePresentationResult.success(
            new File("/tmp/out.pptx"), 100, 1);
        assertNotNull(result.getTimestamp());
    }
}
