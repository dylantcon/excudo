package com.excudo.console.utils;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.orchestration.SessionManager;
import com.excudo.core.commands.CommandInvoker;

import java.util.Optional;

/**
 * Utility class for console command validation.
 * Centralizes repetitive session and orchestrator validation logic
 * to eliminate DRY violations in AbstractConsoleEngine.
 */
public class ConsoleCommandValidator {
    
    /**
     * Validation result containing error message if validation failed
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;
        
        private ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }
        
        public static ValidationResult success() {
            return new ValidationResult(true, null);
        }
        
        public static ValidationResult failure(String errorMessage) {
            return new ValidationResult(false, errorMessage);
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public String getErrorMessage() {
            return errorMessage;
        }
    }
    
    /**
     * Validates that an active session exists
     */
    public static ValidationResult validateActiveSession(String currentSessionId) {
        if (currentSessionId == null) {
            return ValidationResult.failure("No active session. Use 'session create <file>' or 'session switch <id>' first.");
        }
        return ValidationResult.success();
    }
    
    /**
     * Validates that the session orchestrator is available and has a loaded presentation
     */
    public static ValidationResult validateSessionOrchestrator(PPTXOrchestrator sessionOrchestrator) {
        if (sessionOrchestrator == null) {
            return ValidationResult.failure("No active session. Use 'session create <file>' or 'session switch <id>' first.");
        }
        
        if (sessionOrchestrator.getContext().isEmpty()) {
            return ValidationResult.failure("No presentation loaded in current session");
        }
        
        return ValidationResult.success();
    }
    
    /**
     * Validates that the command invoker is available for command execution
     */
    public static ValidationResult validateCommandInvoker(CommandInvoker invoker) {
        if (invoker == null) {
            return ValidationResult.failure("No active session for command execution.");
        }
        return ValidationResult.success();
    }
    
    /**
     * Combined validation for session operations that require orchestrator
     */
    public static ValidationResult validateSessionOperation(String currentSessionId, 
                                                           PPTXOrchestrator sessionOrchestrator) {
        ValidationResult sessionResult = validateActiveSession(currentSessionId);
        if (!sessionResult.isValid()) {
            return sessionResult;
        }
        
        return validateSessionOrchestrator(sessionOrchestrator);
    }
    
    /**
     * Combined validation for command operations that require session, orchestrator, and invoker
     */
    public static ValidationResult validateCommandOperation(String currentSessionId,
                                                           PPTXOrchestrator sessionOrchestrator,
                                                           CommandInvoker invoker) {
        ValidationResult sessionResult = validateSessionOperation(currentSessionId, sessionOrchestrator);
        if (!sessionResult.isValid()) {
            return sessionResult;
        }
        
        return validateCommandInvoker(invoker);
    }
    
    /**
     * Validates that a session exists and can be retrieved
     */
    public static ValidationResult validateSessionExists(SessionManager sessionManager, String sessionId) {
        Optional<SessionManager.ManagedSession> sessionOpt = sessionManager.getSession(sessionId);
        if (sessionOpt.isEmpty()) {
            return ValidationResult.failure("Session not found: " + sessionId);
        }
        return ValidationResult.success();
    }
    
    /**
     * Validates slide number parameter format
     */
    public static ValidationResult validateSlideNumber(String slideNumStr) {
        if (slideNumStr == null || slideNumStr.trim().isEmpty()) {
            return ValidationResult.failure("Slide number cannot be empty");
        }
        
        try {
            int slideNum = Integer.parseInt(slideNumStr);
            if (slideNum < 1) {
                return ValidationResult.failure("Slide number must be positive: " + slideNum);
            }
            return ValidationResult.success();
        } catch (NumberFormatException e) {
            return ValidationResult.failure("Invalid slide number: " + slideNumStr);
        }
    }
    
    /**
     * Validates SPID parameter format
     */
    public static ValidationResult validateSpid(String spidStr) {
        if (spidStr == null || spidStr.trim().isEmpty()) {
            return ValidationResult.failure("SPID cannot be empty");
        }
        
        try {
            int spid = Integer.parseInt(spidStr);
            if (spid < 1) {
                return ValidationResult.failure("SPID must be positive: " + spid);
            }
            return ValidationResult.success();
        } catch (NumberFormatException e) {
            return ValidationResult.failure("Invalid SPID: " + spidStr);
        }
    }
    
    /**
     * Validates position parameter format
     */
    public static ValidationResult validatePosition(String positionStr) {
        if (positionStr == null || positionStr.trim().isEmpty()) {
            return ValidationResult.failure("Position cannot be empty");
        }
        
        try {
            int position = Integer.parseInt(positionStr);
            if (position < 1) {
                return ValidationResult.failure("Position must be positive: " + position);
            }
            return ValidationResult.success();
        } catch (NumberFormatException e) {
            return ValidationResult.failure("Invalid position: " + positionStr);
        }
    }
    
    /**
     * Validates numeric parameter format
     */
    public static ValidationResult validateNumeric(String value, String parameterName) {
        if (value == null || value.trim().isEmpty()) {
            return ValidationResult.failure(parameterName + " cannot be empty");
        }
        
        try {
            Double.parseDouble(value);
            return ValidationResult.success();
        } catch (NumberFormatException e) {
            return ValidationResult.failure("Invalid " + parameterName + ": " + value);
        }
    }
    
    /**
     * Functional interface for operations that need validation.
     * Used to eliminate repetitive validation + early return patterns.
     */
    @FunctionalInterface
    public interface ValidatedOperation {
        void execute() throws Exception;
    }
    
    /**
     * Helper method that validates session orchestrator and executes operation if valid.
     * Eliminates the repetitive pattern: get orchestrator -> validate -> handle error -> execute.
     * 
     * @param sessionOrchestrator the session orchestrator to validate
     * @param errorHandler function to call with error message if validation fails
     * @param operation the operation to execute if validation succeeds
     */
    public static void executeWithSessionValidation(PPTXOrchestrator sessionOrchestrator,
                                                   java.util.function.Consumer<String> errorHandler,
                                                   ValidatedOperation operation) {
        ValidationResult validation = validateSessionOrchestrator(sessionOrchestrator);
        if (!validation.isValid()) {
            errorHandler.accept(validation.getErrorMessage());
            return;
        }
        
        try {
            operation.execute();
        } catch (Exception e) {
            errorHandler.accept("Operation failed: " + e.getMessage());
        }
    }
    
    /**
     * Helper method that validates session, orchestrator, and command invoker, then executes operation.
     * Eliminates the repetitive pattern: get session objects -> validate all -> handle error -> execute.
     * 
     * @param currentSessionId the current session ID to validate
     * @param sessionOrchestrator the session orchestrator to validate
     * @param invoker the command invoker to validate
     * @param errorHandler function to call with error message if validation fails
     * @param operation the operation to execute if validation succeeds
     */
    public static void executeWithCommandValidation(String currentSessionId,
                                                   PPTXOrchestrator sessionOrchestrator,
                                                   CommandInvoker invoker,
                                                   java.util.function.Consumer<String> errorHandler,
                                                   ValidatedOperation operation) {
        ValidationResult validation = validateCommandOperation(currentSessionId, sessionOrchestrator, invoker);
        if (!validation.isValid()) {
            errorHandler.accept(validation.getErrorMessage());
            return;
        }
        
        try {
            operation.execute();
        } catch (Exception e) {
            errorHandler.accept("Operation failed: " + e.getMessage());
        }
    }
    
    /**
     * Validates slide number and returns parsed integer, or calls error handler if invalid.
     * Eliminates the repetitive pattern: validate slide number -> parse -> handle error.
     * 
     * @param slideNumStr the slide number string to validate and parse
     * @param errorHandler function to call with error message if validation fails
     * @return Optional containing parsed slide number if valid, empty if invalid
     */
    public static java.util.Optional<Integer> validateAndParseSlideNumber(String slideNumStr,
                                                                         java.util.function.Consumer<String> errorHandler) {
        ValidationResult validation = validateSlideNumber(slideNumStr);
        if (!validation.isValid()) {
            errorHandler.accept(validation.getErrorMessage());
            return java.util.Optional.empty();
        }
        
        try {
            return java.util.Optional.of(Integer.parseInt(slideNumStr));
        } catch (NumberFormatException e) {
            errorHandler.accept("Invalid slide number: " + slideNumStr);
            return java.util.Optional.empty();
        }
    }
    
    /**
     * Validates SPID and returns parsed integer, or calls error handler if invalid.
     * Eliminates the repetitive pattern: validate SPID -> parse -> handle error.
     * 
     * @param spidStr the SPID string to validate and parse
     * @param errorHandler function to call with error message if validation fails
     * @return Optional containing parsed SPID if valid, empty if invalid
     */
    public static java.util.Optional<Integer> validateAndParseSpid(String spidStr,
                                                                  java.util.function.Consumer<String> errorHandler) {
        ValidationResult validation = validateSpid(spidStr);
        if (!validation.isValid()) {
            errorHandler.accept(validation.getErrorMessage());
            return java.util.Optional.empty();
        }
        
        try {
            return java.util.Optional.of(Integer.parseInt(spidStr));
        } catch (NumberFormatException e) {
            errorHandler.accept("Invalid SPID: " + spidStr);
            return java.util.Optional.empty();
        }
    }
}