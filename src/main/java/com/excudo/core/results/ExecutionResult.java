package com.excudo.core.results;

import com.excudo.core.validation.ValidationResult;
import java.util.function.Function;
import java.util.Optional;
import java.time.Instant;

/**
 * Generic result container for executions that can succeed or fail.
 * Provides structured feedback for transaction management and error handling.
 */
public class ExecutionResult<T> {
    
    private final boolean success;
    private final T data;
    private final ValidationResult validation;
    private final String executionType;
    private final Exception exception;
    private final String message;
    private final Instant timestamp;
    
    private ExecutionResult(boolean success, T data, ValidationResult validation, 
                          String executionType, Exception exception, String message) {
        this.success = success;
        this.data = data;
        this.validation = validation;
        this.executionType = executionType;
        this.exception = exception;
        this.message = message;
        this.timestamp = Instant.now();
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public boolean isFailure() {
        return !success;
    }
    
    public Optional<T> getData() {
        return Optional.ofNullable(data);
    }
    
    public ValidationResult getValidation() {
        return validation;
    }
    
    public String getExecutionType() {
        return executionType;
    }
    
    public Optional<Exception> getException() {
        return Optional.ofNullable(exception);
    }
    
    public String getMessage() {
        return message;
    }
    
    public Instant getTimestamp() {
        return timestamp;
    }
    
    public String getDetailedSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("ExecutionResult[").append(executionType).append("] ");
        summary.append(success ? "SUCCESS" : "FAILURE");
        if (message != null) {
            summary.append(" - ").append(message);
        }
        if (validation != null && validation.hasErrors()) {
            summary.append(" (").append(validation.getSummary()).append(")");
        }
        return summary.toString();
    }
    
    /**
     * Get error message, either from validation or exception
     */
    public String getErrorMessage() {
        if (validation != null && validation.hasErrors()) {
            return validation.getFirstError();
        }
        if (exception != null) {
            return exception.getMessage();
        }
        return message;
    }
    
    /**
     * Transform the data if this is a success result
     */
    public <U> ExecutionResult<U> map(Function<T, U> mapper) {
        if (isSuccess()) {
            try {
                U mappedData = mapper.apply(data);
                return new ExecutionResult<>(true, mappedData, validation, executionType, null, message);
            } catch (Exception e) {
                return new ExecutionResult<>(false, null, 
                    ValidationResult.failure("Transformation failed: " + e.getMessage()),
                    executionType, e, "Transformation failed");
            }
        } else {
            return new ExecutionResult<>(false, null, validation, executionType, exception, message);
        }
    }
    
    /**
     * Chain another operation if this one succeeded
     */
    public <U> ExecutionResult<U> flatMap(Function<T, ExecutionResult<U>> mapper) {
        if (isSuccess()) {
            return mapper.apply(data);
        } else {
            return new ExecutionResult<>(false, null, validation, executionType, exception, message);
        }
    }
    
    // Static factory methods
    
    public static <T> ExecutionResult<T> success(String executionType, T data) {
        return new ExecutionResult<>(true, data, ValidationResult.success(), executionType, null, "Operation completed successfully");
    }
    
    public static <T> ExecutionResult<T> success(String executionType, T data, ValidationResult validation) {
        return new ExecutionResult<>(true, data, validation, executionType, null, "Operation completed successfully");
    }
    
    public static <T> ExecutionResult<T> failure(String executionType, String error) {
        return new ExecutionResult<>(false, null, ValidationResult.failure(error), executionType, null, error);
    }
    
    public static <T> ExecutionResult<T> failure(String executionType, ValidationResult validation) {
        String message = validation != null && !validation.getErrors().isEmpty()
            ? String.join("; ", validation.getErrors())
            : "Operation failed";
        return new ExecutionResult<>(false, null, validation, executionType, null, message);
    }
    
    public static <T> ExecutionResult<T> failure(String executionType, Exception error) {
        String errorMsg = error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();
        return new ExecutionResult<>(false, null, 
            ValidationResult.failure(errorMsg),
            executionType, error, errorMsg);
    }
    
    public static <T> ExecutionResult<T> failure(String executionType, String errorMsg, Exception error) {
        return new ExecutionResult<>(false, null, 
            ValidationResult.failure(errorMsg),
            executionType, error, errorMsg);
    }
    
    @Override
    public String toString() {
        return String.format("ExecutionResult[success=%s, type=%s, validation=%s]", 
            success, executionType, validation != null ? validation.getSummary() : "null");
    }
}