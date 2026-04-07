package com.excudo.utils;

import java.io.File;
import java.util.Collection;

/**
 * General-purpose validation utilities for method argument validation.
 * Throws exceptions immediately for fail-fast behavior.
 *
 * Use these for constructor/method entry validation where you want to fail fast.
 * For console commands where you want to return error messages to the user,
 * use ConsoleCommandValidator instead (returns ValidationResult).
 *
 * Pattern: All methods throw IllegalArgumentException on validation failure,
 * except where a more specific exception is appropriate (e.g., NullPointerException).
 */
public final class ValidationUtils {

    private ValidationUtils() {
        // Utility class - no instantiation
    }

    // ========== Null Checks ==========

    /**
     * Validates that an object is not null.
     *
     * @param obj the object to validate
     * @param parameterName the name of the parameter (for error message)
     * @param <T> the type of the object
     * @return the validated object (for fluent chaining)
     * @throws IllegalArgumentException if obj is null
     */
    public static <T> T requireNonNull(T obj, String parameterName) {
        if (obj == null) {
            throw new IllegalArgumentException(parameterName + " cannot be null");
        }
        return obj;
    }

    /**
     * Validates that an object is not null, with custom message.
     *
     * @param obj the object to validate
     * @param message the error message if validation fails
     * @param <T> the type of the object
     * @return the validated object (for fluent chaining)
     * @throws IllegalArgumentException if obj is null
     */
    public static <T> T requireNonNullWithMessage(T obj, String message) {
        if (obj == null) {
            throw new IllegalArgumentException(message);
        }
        return obj;
    }

    // ========== String Validation ==========

    /**
     * Validates that a string is not null or empty.
     *
     * @param str the string to validate
     * @param parameterName the name of the parameter (for error message)
     * @return the validated string (for fluent chaining)
     * @throws IllegalArgumentException if str is null or empty
     */
    public static String requireNonEmpty(String str, String parameterName) {
        if (str == null || str.isEmpty()) {
            throw new IllegalArgumentException(parameterName + " cannot be null or empty");
        }
        return str;
    }

    /**
     * Validates that a string is not null, empty, or blank (whitespace only).
     *
     * @param str the string to validate
     * @param parameterName the name of the parameter (for error message)
     * @return the validated string (for fluent chaining)
     * @throws IllegalArgumentException if str is null, empty, or blank
     */
    public static String requireNonBlank(String str, String parameterName) {
        if (str == null || str.isBlank()) {
            throw new IllegalArgumentException(parameterName + " cannot be null, empty, or blank");
        }
        return str;
    }

    // ========== Numeric Validation ==========

    /**
     * Validates that an integer is positive (greater than 0).
     *
     * @param value the value to validate
     * @param parameterName the name of the parameter (for error message)
     * @return the validated value (for fluent chaining)
     * @throws IllegalArgumentException if value is not positive
     */
    public static int requirePositive(int value, String parameterName) {
        if (value <= 0) {
            throw new IllegalArgumentException(parameterName + " must be positive, got: " + value);
        }
        return value;
    }

    /**
     * Validates that a long is positive (greater than 0).
     *
     * @param value the value to validate
     * @param parameterName the name of the parameter (for error message)
     * @return the validated value (for fluent chaining)
     * @throws IllegalArgumentException if value is not positive
     */
    public static long requirePositive(long value, String parameterName) {
        if (value <= 0) {
            throw new IllegalArgumentException(parameterName + " must be positive, got: " + value);
        }
        return value;
    }

    /**
     * Validates that an integer is non-negative (>= 0).
     *
     * @param value the value to validate
     * @param parameterName the name of the parameter (for error message)
     * @return the validated value (for fluent chaining)
     * @throws IllegalArgumentException if value is negative
     */
    public static int requireNonNegative(int value, String parameterName) {
        if (value < 0) {
            throw new IllegalArgumentException(parameterName + " cannot be negative, got: " + value);
        }
        return value;
    }

    /**
     * Validates that a value is within a range (inclusive).
     *
     * @param value the value to validate
     * @param min the minimum allowed value (inclusive)
     * @param max the maximum allowed value (inclusive)
     * @param parameterName the name of the parameter (for error message)
     * @return the validated value (for fluent chaining)
     * @throws IllegalArgumentException if value is outside the range
     */
    public static int requireInRange(int value, int min, int max, String parameterName) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(
                parameterName + " must be between " + min + " and " + max + ", got: " + value);
        }
        return value;
    }

    // ========== Boolean/Condition Validation ==========

    /**
     * Validates that a condition is true.
     *
     * @param condition the condition to validate
     * @param message the error message if condition is false
     * @throws IllegalArgumentException if condition is false
     */
    public static void requireTrue(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * Validates that a condition is false.
     *
     * @param condition the condition to validate
     * @param message the error message if condition is true
     * @throws IllegalArgumentException if condition is true
     */
    public static void requireFalse(boolean condition, String message) {
        if (condition) {
            throw new IllegalArgumentException(message);
        }
    }

    // ========== Collection Validation ==========

    /**
     * Validates that a collection is not null or empty.
     *
     * @param collection the collection to validate
     * @param parameterName the name of the parameter (for error message)
     * @param <T> the collection type
     * @return the validated collection (for fluent chaining)
     * @throws IllegalArgumentException if collection is null or empty
     */
    public static <T extends Collection<?>> T requireNonEmpty(T collection, String parameterName) {
        if (collection == null || collection.isEmpty()) {
            throw new IllegalArgumentException(parameterName + " cannot be null or empty");
        }
        return collection;
    }

    /**
     * Validates that an array is not null or empty.
     *
     * @param array the array to validate
     * @param parameterName the name of the parameter (for error message)
     * @param <T> the array element type
     * @return the validated array (for fluent chaining)
     * @throws IllegalArgumentException if array is null or empty
     */
    public static <T> T[] requireNonEmpty(T[] array, String parameterName) {
        if (array == null || array.length == 0) {
            throw new IllegalArgumentException(parameterName + " cannot be null or empty");
        }
        return array;
    }

    // ========== File Validation ==========

    /**
     * Validates that a file exists.
     *
     * @param file the file to validate
     * @param parameterName the name of the parameter (for error message)
     * @return the validated file (for fluent chaining)
     * @throws IllegalArgumentException if file is null or doesn't exist
     */
    public static File requireExists(File file, String parameterName) {
        requireNonNull(file, parameterName);
        if (!file.exists()) {
            throw new IllegalArgumentException(parameterName + " does not exist: " + file.getAbsolutePath());
        }
        return file;
    }

    /**
     * Validates that a file exists and is a directory.
     *
     * @param dir the directory to validate
     * @param parameterName the name of the parameter (for error message)
     * @return the validated directory (for fluent chaining)
     * @throws IllegalArgumentException if dir is null, doesn't exist, or is not a directory
     */
    public static File requireDirectory(File dir, String parameterName) {
        requireExists(dir, parameterName);
        if (!dir.isDirectory()) {
            throw new IllegalArgumentException(parameterName + " is not a directory: " + dir.getAbsolutePath());
        }
        return dir;
    }

    /**
     * Validates that a file exists and is a regular file (not a directory).
     *
     * @param file the file to validate
     * @param parameterName the name of the parameter (for error message)
     * @return the validated file (for fluent chaining)
     * @throws IllegalArgumentException if file is null, doesn't exist, or is a directory
     */
    public static File requireFile(File file, String parameterName) {
        requireExists(file, parameterName);
        if (!file.isFile()) {
            throw new IllegalArgumentException(parameterName + " is not a file: " + file.getAbsolutePath());
        }
        return file;
    }

    // ========== State Validation ==========

    /**
     * Validates application state (not argument validation).
     * Use this for checking preconditions about object state.
     *
     * @param condition the state condition to validate
     * @param message the error message if condition is false
     * @throws IllegalStateException if condition is false
     */
    public static void requireState(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
