package com.excudo.core.results;

import com.excudo.core.validation.ValidationResult;
import com.excudo.core.results.ExecutionResult;
import java.util.List;
import java.util.Set;

/**
 * Specialized operation result for slide-related operations
 * Provides slide-specific metadata and validation information
 */
public class SlideExecutionResult {
  
  private final ExecutionResult<SlideActionData> executionResult;

  private SlideExecutionResult(ExecutionResult<SlideActionData> executionResult) {
    this.executionResult = executionResult;
  }

  // Delegate methods to wrapped ExecutionResult
  public boolean isSuccess() { return executionResult.isSuccess(); }
  public java.util.Optional<SlideActionData> getData() { return executionResult.getData(); }
  public ValidationResult getValidation() { return executionResult.getValidation(); }
  public String getExecutionType() { return executionResult.getExecutionType(); }
  public java.time.Instant getTimestamp() { return executionResult.getTimestamp(); }
  public java.util.Optional<Exception> getException() { return executionResult.getException(); }
  public String getMessage() { return executionResult.getMessage(); }
  public String getDetailedSummary() { return executionResult.getDetailedSummary(); }

  // Static factory methods specific to slide operations

  public static SlideExecutionResult slideCreated(int slideNumber, String title, List<Integer> allocatedSpids) {
    SlideActionData data = new SlideActionData(slideNumber, title, allocatedSpids, Set.of());
    return createSuccess("Slide Creation", data);
  }

  public static SlideExecutionResult slideCopied(int sourceSlide, int targetSlide, 
                                                Set<Integer> originalSpids, Set<Integer> newSpids) {
    SlideActionData data = new SlideActionData(targetSlide, 
                                                     "Copied from slide " + sourceSlide, 
                                                     List.of(), newSpids);
    String action = String.format("Slide Copy (slide %d -> slide %d)", sourceSlide, targetSlide);
    return createSuccess(action, data);
  }

  public static SlideExecutionResult slideDeleted(int slideNumber) {
    SlideActionData data = new SlideActionData(slideNumber, "Deleted", List.of(), Set.of());
    return createSuccess("Slide Deletion", data);
  }

  public static SlideExecutionResult slideValidated(int slideNumber, ValidationResult validation) {
    SlideActionData data = new SlideActionData(slideNumber, "Validation", List.of(), Set.of());
    return createWithValidation("Slide Validation", data, validation);
  }

  public static SlideExecutionResult slideActionFailed(String action, int slideNumber, String error) {
    return createFailure(action + " (slide " + slideNumber + ")", error);
  }

  public static SlideExecutionResult slideActionFailed(String action, int slideNumber, Exception exception) {
    return createFailure(action + " (slide " + slideNumber + ")", exception);
  }

  // Helper methods using composition
  private static SlideExecutionResult createSuccess(String actionType, SlideActionData data) {
    return new SlideExecutionResult(ExecutionResult.success(actionType, data));
  }

  private static SlideExecutionResult createWithValidation(String actionType, SlideActionData data, 
                                                          ValidationResult validation) {
    return new SlideExecutionResult(ExecutionResult.success(actionType, data, validation));
  }

  private static SlideExecutionResult createFailure(String actionType, String error) {
    return new SlideExecutionResult(ExecutionResult.failure(actionType, error));
  }

  private static SlideExecutionResult createFailure(String actionType, Exception exception) {
    return new SlideExecutionResult(ExecutionResult.failure(actionType, exception));
  }

  // Slide-specific helper methods

  /**
   * Get the slide number from the operation data
   */
  public int getSlideNumber() {
    return getData().map(SlideActionData::getSlideNumber).orElse(-1);
  }

  /**
   * Get allocated SPIDs from the operation
   */
  public List<Integer> getAllocatedSpids() {
    return getData().map(SlideActionData::getAllocatedSpids).orElse(List.of());
  }

  /**
   * Get affected SPIDs from the operation
   */
  public Set<Integer> getAffectedSpids() {
    return getData().map(SlideActionData::getAffectedSpids).orElse(Set.of());
  }

  /**
   * Check if operation involved SPID allocation
   */
  public boolean hasSpidAllocation() {
    return getData().map(data -> !data.getAllocatedSpids().isEmpty()).orElse(false);
  }
}

/**
 * Data container for slide operation results
 */
class SlideActionData {
  private final int slideNumber;
  private final String title;
  private final List<Integer> allocatedSpids;
  private final Set<Integer> affectedSpids;

  public SlideActionData(int slideNumber, String title, List<Integer> allocatedSpids, Set<Integer> affectedSpids) {
    this.slideNumber = slideNumber;
    this.title = title;
    this.allocatedSpids = List.copyOf(allocatedSpids);
    this.affectedSpids = Set.copyOf(affectedSpids);
  }

  public int getSlideNumber() { return slideNumber; }
  public String getTitle() { return title; }
  public List<Integer> getAllocatedSpids() { return allocatedSpids; }
  public Set<Integer> getAffectedSpids() { return affectedSpids; }

  @Override
  public String toString() {
    return String.format("SlideData[slide=%d, title='%s', spids=%d, affected=%d]", 
                        slideNumber, title, allocatedSpids.size(), affectedSpids.size());
  }
}