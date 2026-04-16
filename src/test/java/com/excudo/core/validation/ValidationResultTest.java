package com.excudo.core.validation;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests ValidationResult factory methods, state queries, and builder.
 */
public class ValidationResultTest {

    @Test
    public void successIsValid() {
        ValidationResult result = ValidationResult.success();
        assertTrue(result.isValid());
        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void failureWithSingleError() {
        ValidationResult result = ValidationResult.failure("Something broke");
        assertFalse(result.isValid());
        assertTrue(result.hasErrors());
        assertEquals(1, result.getErrors().size());
        assertEquals("Something broke", result.getFirstError());
    }

    @Test
    public void failureWithMultipleErrors() {
        List<String> errors = Arrays.asList("Error 1", "Error 2", "Error 3");
        ValidationResult result = ValidationResult.failure(errors);
        assertFalse(result.isValid());
        assertEquals(3, result.getErrors().size());
        assertEquals("Error 1", result.getFirstError());
    }

    @Test
    public void successWithWarnings() {
        List<String> warnings = Arrays.asList("Watch out", "Be careful");
        ValidationResult result = ValidationResult.successWithWarnings(warnings);
        assertTrue(result.isValid());
        assertFalse(result.hasErrors());
        assertTrue(result.hasWarnings());
        assertEquals(2, result.getWarnings().size());
    }

    @Test
    public void failureWithErrorAndWarnings() {
        ValidationResult result = ValidationResult.failure("Critical error",
            Arrays.asList("Minor warning"));
        assertFalse(result.isValid());
        assertTrue(result.hasErrors());
        assertTrue(result.hasWarnings());
        assertEquals("Critical error", result.getFirstError());
        assertEquals(1, result.getWarnings().size());
    }

    @Test
    public void getSummaryForSuccess() {
        ValidationResult result = ValidationResult.success();
        String summary = result.getSummary();
        assertNotNull(summary);
    }

    @Test
    public void getSummaryForFailure() {
        ValidationResult result = ValidationResult.failure("Bad XML structure");
        String summary = result.getSummary();
        assertNotNull(summary);
        assertTrue(summary.length() > 0);
    }

    @Test
    public void getFirstErrorReturnsNullWhenNoErrors() {
        ValidationResult result = ValidationResult.success();
        assertNull(result.getFirstError());
    }

    @Test
    public void getInfosReturnsEmptyByDefault() {
        ValidationResult result = ValidationResult.success();
        assertNotNull(result.getInfos());
        assertTrue(result.getInfos().isEmpty());
    }

    @Test
    public void builderAccumulatesErrors() {
        ValidationResult result = new ValidationResult.Builder()
            .addError("Error A")
            .addError("Error B")
            .addWarning("Warning 1")
            .addInfo("Info note")
            .build();

        assertFalse(result.isValid());
        assertEquals(2, result.getErrors().size());
        assertEquals(1, result.getWarnings().size());
        assertEquals(1, result.getInfos().size());
    }

    @Test
    public void builderWithNoErrorsIsValid() {
        ValidationResult result = new ValidationResult.Builder()
            .addWarning("Just a warning")
            .build();

        assertTrue(result.isValid());
        assertTrue(result.hasWarnings());
    }
}
