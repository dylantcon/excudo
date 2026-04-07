package com.excudo.core.results;

import com.excudo.core.results.ExecutionResult;
import java.io.File;
import java.time.Instant;

/**
 * Specialized operation result for presentation saving operations
 * Provides save-specific metadata and file information
 */
public class SavePresentationResult {
    
    private final ExecutionResult<SavePresentationData> executionResult;
    
    private SavePresentationResult(ExecutionResult<SavePresentationData> executionResult) {
        this.executionResult = executionResult;
    }
    
    // Delegate methods to wrapped ExecutionResult
    public boolean isSuccess() { return executionResult.isSuccess(); }
    public java.util.Optional<SavePresentationData> getData() { return executionResult.getData(); }
    public String getExecutionType() { return executionResult.getExecutionType(); }
    public Instant getTimestamp() { return executionResult.getTimestamp(); }
    public java.util.Optional<Exception> getException() { return executionResult.getException(); }
    public String getMessage() { return executionResult.getMessage(); }
    
    // Static factory methods for presentation saving
    
    public static SavePresentationResult success(File outputFile, long fileSize, int slideCount) {
        SavePresentationData data = new SavePresentationData(outputFile, fileSize, slideCount);
        return new SavePresentationResult(ExecutionResult.success("Save Presentation", data));
    }
    
    public static SavePresentationResult failure(String error) {
        return new SavePresentationResult(ExecutionResult.failure("Save Presentation", error));
    }
    
    public static SavePresentationResult failure(Exception exception) {
        return new SavePresentationResult(ExecutionResult.failure("Save Presentation", exception));
    }
    
    // Save-specific helper methods
    
    /**
     * Get the output file that was saved to
     */
    public java.util.Optional<File> getOutputFile() {
        return getData().map(SavePresentationData::getOutputFile);
    }
    
    /**
     * Get the size of the saved file in bytes
     */
    public long getFileSize() {
        return getData().map(SavePresentationData::getFileSize).orElse(0L);
    }
    
    /**
     * Get the number of slides that were saved
     */
    public int getSlideCount() {
        return getData().map(SavePresentationData::getSlideCount).orElse(0);
    }
    
    /**
     * Check if save operation completed successfully and file exists
     */
    public boolean isFileSaved() {
        return isSuccess() && getOutputFile().map(File::exists).orElse(false);
    }
}

/**
 * Data container for save presentation operation results
 */
class SavePresentationData {
    private final File outputFile;
    private final long fileSize;
    private final int slideCount;
    
    public SavePresentationData(File outputFile, long fileSize, int slideCount) {
        this.outputFile = outputFile;
        this.fileSize = fileSize;
        this.slideCount = slideCount;
    }
    
    public File getOutputFile() { return outputFile; }
    public long getFileSize() { return fileSize; }
    public int getSlideCount() { return slideCount; }
    
    @Override
    public String toString() {
        return String.format("SaveData[file='%s', size=%d bytes, slides=%d]", 
                           outputFile.getName(), fileSize, slideCount);
    }
}