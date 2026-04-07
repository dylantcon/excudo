package com.excudo.view.components;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.TextArea;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Controller for managing the Output tab display.
 * Shows operation results, status messages, and execution logs.
 */
public class OutputController {
    
    @FXML
    private TextArea outputTextArea;
    
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    
    /**
     * Set the output text area component
     */
    public void setOutputTextArea(TextArea outputTextArea) {
        this.outputTextArea = outputTextArea;
    }
    
    /**
     * Initialize the output controller
     */
    public void initialize() {
        if (outputTextArea != null) {
            outputTextArea.setEditable(false);
            outputTextArea.setWrapText(true);
            appendMessage("Output tab initialized", MessageType.INFO);
        }
    }
    
    /**
     * Types of messages that can be displayed
     */
    public enum MessageType {
        INFO("[INFO]"),
        SUCCESS("[SUCCESS]"),
        WARNING("[WARNING]"),
        ERROR("[ERROR]"),
        OPERATION("[OPERATION]");
        
        private final String prefix;
        
        MessageType(String prefix) {
            this.prefix = prefix;
        }
        
        public String getPrefix() {
            return prefix;
        }
    }
    
    /**
     * Append a message to the output area
     */
    public void appendMessage(String message, MessageType type) {
        if (outputTextArea == null) {
            return;
        }
        
        Platform.runLater(() -> {
            String timestamp = LocalDateTime.now().format(TIME_FORMAT);
            String formattedMessage = String.format("[%s] %s %s%n", 
                timestamp, type.getPrefix(), message);
            
            outputTextArea.appendText(formattedMessage);
            
            // Auto-scroll to bottom
            outputTextArea.setScrollTop(Double.MAX_VALUE);
        });
    }
    
    /**
     * Show operation start message
     */
    public void showOperationStart(String operationName) {
        appendMessage("Starting operation: " + operationName, MessageType.OPERATION);
    }
    
    /**
     * Show operation completion message
     */
    public void showOperationComplete(String operationName, boolean success) {
        MessageType type = success ? MessageType.SUCCESS : MessageType.ERROR;
        String status = success ? "completed successfully" : "failed";
        appendMessage("Operation " + operationName + " " + status, type);
    }
    
    /**
     * Show operation result with details
     */
    public void showExecutionResult(String operationName, String result, boolean success) {
        showOperationComplete(operationName, success);
        if (result != null && !result.trim().isEmpty()) {
            appendMessage("Result: " + result, MessageType.INFO);
        }
    }
    
    /**
     * Show slide operation result
     */
    public void showSlideAction(String operation, int slideNumber, boolean success) {
        String message = String.format("Slide %d %s", slideNumber, operation);
        showOperationComplete(message, success);
    }
    
    /**
     * Show XML validation result
     */
    public void showValidationResult(String fileName, boolean isValid, String details) {
        MessageType type = isValid ? MessageType.SUCCESS : MessageType.ERROR;
        String status = isValid ? "is valid" : "has validation errors";
        appendMessage("XML validation: " + fileName + " " + status, type);
        
        if (details != null && !details.trim().isEmpty()) {
            appendMessage("Details: " + details, MessageType.INFO);
        }
    }
    
    /**
     * Show file operation result
     */
    public void showFileOperation(String operation, String fileName, boolean success) {
        String message = String.format("File %s: %s", operation, fileName);
        showOperationComplete(message, success);
    }
    
    /**
     * Show LLM operation result
     */
    public void showLLMOperation(String command, String result, boolean success) {
        appendMessage("LLM Command: " + command, MessageType.OPERATION);
        MessageType type = success ? MessageType.SUCCESS : MessageType.ERROR;
        appendMessage("LLM Response: " + (result != null ? result : "No response"), type);
    }
    
    /**
     * Clear the output area
     */
    public void clearOutput() {
        if (outputTextArea != null) {
            Platform.runLater(() -> {
                outputTextArea.clear();
                appendMessage("Output cleared", MessageType.INFO);
            });
        }
    }
    
    /**
     * Show error with exception details
     */
    public void showError(String operation, Exception e) {
        appendMessage("Error in " + operation + ": " + e.getMessage(), MessageType.ERROR);
        
        // Show stack trace for debugging
        if (e.getCause() != null) {
            appendMessage("Caused by: " + e.getCause().getMessage(), MessageType.ERROR);
        }
    }
    
    /**
     * Show general information message
     */
    public void showInfo(String message) {
        appendMessage(message, MessageType.INFO);
    }
    
    /**
     * Show warning message
     */
    public void showWarning(String message) {
        appendMessage(message, MessageType.WARNING);
    }
    
    /**
     * Append raw output text (for reports and logs)
     */
    public void appendOutput(String text) {
        if (outputTextArea == null) {
            return;
        }
        
        Platform.runLater(() -> {
            outputTextArea.appendText(text + "\n");
            // Auto-scroll to bottom
            outputTextArea.setScrollTop(Double.MAX_VALUE);
        });
    }
}