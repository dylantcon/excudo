package com.excudo.view.components;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.ListView;
import javafx.scene.control.ListCell;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Controller for managing the Validation tab display.
 * Shows XML validation errors, warnings, and issues with line numbers.
 */
public class ValidationController {
    
    @FXML
    private ListView<ValidationItem> validationListView;
    
    private final ObservableList<ValidationItem> validationItems = FXCollections.observableArrayList();
    
    /**
     * Represents a validation issue
     */
    public static class ValidationItem {
        private final ValidationLevel level;
        private final String message;
        private final String fileName;
        private final int lineNumber;
        private final int columnNumber;
        
        public ValidationItem(ValidationLevel level, String message, String fileName, int lineNumber, int columnNumber) {
            this.level = level;
            this.message = message;
            this.fileName = fileName;
            this.lineNumber = lineNumber;
            this.columnNumber = columnNumber;
        }
        
        public ValidationItem(ValidationLevel level, String message, String fileName) {
            this(level, message, fileName, -1, -1);
        }
        
        public ValidationItem(ValidationLevel level, String message) {
            this(level, message, null, -1, -1);
        }
        
        public ValidationLevel getLevel() { return level; }
        public String getMessage() { return message; }
        public String getFileName() { return fileName; }
        public int getLineNumber() { return lineNumber; }
        public int getColumnNumber() { return columnNumber; }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("[").append(level.getDisplayName()).append("] ");
            
            if (fileName != null) {
                sb.append(fileName);
                if (lineNumber > 0) {
                    sb.append(":").append(lineNumber);
                    if (columnNumber > 0) {
                        sb.append(":").append(columnNumber);
                    }
                }
                sb.append(" - ");
            }
            
            sb.append(message);
            return sb.toString();
        }
    }
    
    /**
     * Validation issue levels
     */
    public enum ValidationLevel {
        ERROR("ERROR", "[X]"),
        WARNING("WARNING", "[WARN]"),
        INFO("INFO", "[i]"),
        SUCCESS("SUCCESS", "[OK]");
        
        private final String displayName;
        private final String icon;
        
        ValidationLevel(String displayName, String icon) {
            this.displayName = displayName;
            this.icon = icon;
        }
        
        public String getDisplayName() { return displayName; }
        public String getIcon() { return icon; }
    }
    
    /**
     * Set the validation list view component
     */
    public void setValidationListView(ListView<ValidationItem> validationListView) {
        this.validationListView = validationListView;
    }
    
    /**
     * Initialize the validation controller
     */
    public void initialize() {
        if (validationListView != null) {
            validationListView.setItems(validationItems);
            
            // Custom cell factory to show validation items with icons
            validationListView.setCellFactory(listView -> new ListCell<ValidationItem>() {
                @Override
                protected void updateItem(ValidationItem item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setStyle("");
                    } else {
                        setText(item.getLevel().getIcon() + " " + item.toString());
                        
                        // Set style based on validation level
                        switch (item.getLevel()) {
                            case ERROR:
                                setStyle("-fx-text-fill: #d32f2f;");
                                break;
                            case WARNING:
                                setStyle("-fx-text-fill: #f57c00;");
                                break;
                            case INFO:
                                setStyle("-fx-text-fill: #1976d2;");
                                break;
                            case SUCCESS:
                                setStyle("-fx-text-fill: #388e3c;");
                                break;
                        }
                    }
                }
            });
            
            addValidationItem(ValidationLevel.INFO, "Validation tab initialized");
        }
    }
    
    /**
     * Add a validation item
     */
    public void addValidationItem(ValidationLevel level, String message, String fileName, int lineNumber, int columnNumber) {
        ValidationItem item = new ValidationItem(level, message, fileName, lineNumber, columnNumber);
        Platform.runLater(() -> {
            validationItems.add(item);
            
            // Auto-scroll to show the latest item
            if (validationListView != null) {
                validationListView.scrollTo(validationItems.size() - 1);
            }
        });
    }
    
    /**
     * Add a validation item without line/column info
     */
    public void addValidationItem(ValidationLevel level, String message, String fileName) {
        addValidationItem(level, message, fileName, -1, -1);
    }
    
    /**
     * Add a validation item without file info
     */
    public void addValidationItem(ValidationLevel level, String message) {
        addValidationItem(level, message, null, -1, -1);
    }
    
    /**
     * Show XML validation results
     */
    public void showXMLValidationResult(String fileName, boolean isValid, String errorDetails) {
        if (isValid) {
            addValidationItem(ValidationLevel.SUCCESS, "XML is well-formed and valid", fileName);
        } else {
            addValidationItem(ValidationLevel.ERROR, "XML validation failed: " + errorDetails, fileName);
        }
    }
    
    /**
     * Show XML parsing error with line number
     */
    public void showXMLParsingError(String fileName, String error, int lineNumber, int columnNumber) {
        addValidationItem(ValidationLevel.ERROR, "XML parsing error: " + error, fileName, lineNumber, columnNumber);
    }
    
    /**
     * Show schema validation error
     */
    public void showSchemaValidationError(String fileName, String error, String elementPath) {
        String message = "Schema validation error in " + elementPath + ": " + error;
        addValidationItem(ValidationLevel.ERROR, message, fileName);
    }
    
    /**
     * Show warning about missing or deprecated elements
     */
    public void showCompatibilityWarning(String fileName, String warning) {
        addValidationItem(ValidationLevel.WARNING, "Compatibility warning: " + warning, fileName);
    }
    
    /**
     * Show information about successful operations
     */
    public void showOperationInfo(String operation, String details) {
        addValidationItem(ValidationLevel.INFO, operation + ": " + details);
    }
    
    /**
     * Clear all validation items
     */
    public void clearValidation() {
        Platform.runLater(() -> {
            validationItems.clear();
            addValidationItem(ValidationLevel.INFO, "Validation cleared");
        });
    }
    
    /**
     * Get count of errors
     */
    public long getErrorCount() {
        return validationItems.stream()
                .filter(item -> item.getLevel() == ValidationLevel.ERROR)
                .count();
    }
    
    /**
     * Get count of warnings
     */
    public long getWarningCount() {
        return validationItems.stream()
                .filter(item -> item.getLevel() == ValidationLevel.WARNING)
                .count();
    }
    
    /**
     * Check if there are any validation errors
     */
    public boolean hasErrors() {
        return getErrorCount() > 0;
    }
    
    /**
     * Check if validation is successful (no errors or warnings)
     */
    public boolean isValidationSuccessful() {
        return getErrorCount() == 0 && getWarningCount() == 0;
    }
    
    /**
     * Show validation summary
     */
    public void showValidationSummary(String fileName) {
        long errors = getErrorCount();
        long warnings = getWarningCount();
        
        if (errors == 0 && warnings == 0) {
            addValidationItem(ValidationLevel.SUCCESS, 
                "Validation completed successfully - no issues found", fileName);
        } else {
            String summary = String.format("Validation completed: %d error(s), %d warning(s)", 
                errors, warnings);
            ValidationLevel level = errors > 0 ? ValidationLevel.ERROR : ValidationLevel.WARNING;
            addValidationItem(level, summary, fileName);
        }
    }
    
    /**
     * Show a comprehensive validation report
     */
    public void showValidationReport(String report) {
        // Clear existing items first
        clearValidation();
        
        // Parse the report and extract validation items
        String[] lines = report.split("\n");
        for (String line : lines) {
            if (line.trim().isEmpty()) continue;
            
            if (line.contains("[X]") || line.toLowerCase().contains("error")) {
                addValidationItem(ValidationLevel.ERROR, line.trim());
            } else if (line.contains("[WARN]") || line.toLowerCase().contains("warning")) {
                addValidationItem(ValidationLevel.WARNING, line.trim());
            } else if (line.contains("[OK]") || line.toLowerCase().contains("success")) {
                addValidationItem(ValidationLevel.SUCCESS, line.trim());
            } else if (line.startsWith("  •") || line.startsWith("  [WARN]")) {
                // Detail line from validation report
                ValidationLevel level = line.contains("[WARN]") ? ValidationLevel.WARNING : ValidationLevel.ERROR;
                addValidationItem(level, line.trim());
            } else {
                // General info line
                addValidationItem(ValidationLevel.INFO, line.trim());
            }
        }
    }
}