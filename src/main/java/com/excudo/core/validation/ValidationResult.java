package com.excudo.core.validation;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Generic validation result container for operation validation
 * Provides structured feedback for LLM integration and error handling
 */
public class ValidationResult {
  
  private final boolean valid;
  private final List<String> errors;
  private final List<String> warnings;
  private final List<String> infos;

  private ValidationResult(boolean valid, List<String> errors, List<String> warnings, List<String> infos) {
    this.valid = valid;
    this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
    this.warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
    this.infos = Collections.unmodifiableList(new ArrayList<>(infos));
  }

  public boolean isValid() {
    return valid;
  }

  public boolean hasErrors() {
    return !errors.isEmpty();
  }

  public boolean hasWarnings() {
    return !warnings.isEmpty();
  }

  public List<String> getErrors() {
    return errors;
  }

  public List<String> getWarnings() {
    return warnings;
  }

  public List<String> getInfos() {
    return infos;
  }

  /**
   * Get first error message, or null if no errors
   */
  public String getFirstError() {
    return errors.isEmpty() ? null : errors.get(0);
  }

  /**
   * Get summary of all issues for logging/debugging
   */
  public String getSummary() {
    StringBuilder summary = new StringBuilder();
    summary.append("ValidationResult[valid=").append(valid);
    if (!errors.isEmpty()) {
      summary.append(", errors=").append(errors.size());
    }
    if (!warnings.isEmpty()) {
      summary.append(", warnings=").append(warnings.size());
    }
    summary.append("]");
    return summary.toString();
  }

  // Static factory methods
  
  public static ValidationResult success() {
    return new ValidationResult(true, List.of(), List.of(), List.of());
  }

  public static ValidationResult successWithWarnings(List<String> warnings) {
    return new ValidationResult(true, List.of(), warnings, List.of());
  }

  public static ValidationResult failure(String error) {
    return new ValidationResult(false, List.of(error), List.of(), List.of());
  }

  public static ValidationResult failure(List<String> errors) {
    return new ValidationResult(false, errors, List.of(), List.of());
  }

  public static ValidationResult failure(String error, List<String> warnings) {
    return new ValidationResult(false, List.of(error), warnings, List.of());
  }

  // Builder for complex validation results
  public static class Builder {
    private boolean valid = true;
    private final List<String> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private final List<String> infos = new ArrayList<>();

    public Builder addError(String error) {
      errors.add(error);
      valid = false;
      return this;
    }

    public Builder addWarning(String warning) {
      warnings.add(warning);
      return this;
    }

    public Builder addInfo(String info) {
      infos.add(info);
      return this;
    }

    public Builder addErrors(List<String> errors) {
      this.errors.addAll(errors);
      if (!errors.isEmpty()) {
        valid = false;
      }
      return this;
    }

    public ValidationResult build() {
      return new ValidationResult(valid, errors, warnings, infos);
    }
  }

  public static Builder builder() {
    return new Builder();
  }
}