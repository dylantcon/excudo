package com.excudo.core.results;

import java.io.File;
import java.time.Instant;

/**
 * Simple operation result for presentation loading operations
 * Provides basic success/failure information and file paths
 */
public class LoadPresentationResult {
    
    private final boolean success;
    private final String message;
    private final File sourceFile;
    private final File extractedDirectory;
    private final Exception exception;
    private final Instant timestamp;
    
    private LoadPresentationResult(boolean success, String message, File sourceFile, 
                                 File extractedDirectory, Exception exception) {
        this.success = success;
        this.message = message;
        this.sourceFile = sourceFile;
        this.extractedDirectory = extractedDirectory;
        this.exception = exception;
        this.timestamp = Instant.now();
    }
    
    // Public interface methods
    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public Instant getTimestamp() { return timestamp; }
    public java.util.Optional<Exception> getException() { return java.util.Optional.ofNullable(exception); }
    
    // Static factory methods for presentation loading
    
    public static LoadPresentationResult success(File sourceFile, File extractedDirectory) {
        return new LoadPresentationResult(true, "Presentation loaded successfully", 
                                        sourceFile, extractedDirectory, null);
    }
    
    public static LoadPresentationResult failure(String error) {
        return new LoadPresentationResult(false, error, null, null, null);
    }
    
    public static LoadPresentationResult failure(Exception exception) {
        return new LoadPresentationResult(false, "Load failed: " + exception.getMessage(), 
                                        null, null, exception);
    }
    
    // Presentation-specific helper methods
    
    /**
     * Get the original presentation file that was loaded
     */
    public java.util.Optional<File> getSourceFile() {
        return java.util.Optional.ofNullable(sourceFile);
    }
    
    /**
     * Get the extracted directory where presentation content is stored
     */
    public java.util.Optional<File> getExtractedDirectory() {
        return java.util.Optional.ofNullable(extractedDirectory);
    }
}