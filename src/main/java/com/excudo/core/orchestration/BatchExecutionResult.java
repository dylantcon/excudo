package com.excudo.core.orchestration;

import java.util.List;
import java.util.Map;

/**
 * Result of batch operations containing multiple operation results
 */
public class BatchExecutionResult {
    private final int totalOperations;
    private final int successfulOperations;
    private final int failedOperations;
    private final List<String> actionIds;
    private final Map<String, Object> results;
    
    public BatchExecutionResult(int totalOperations, int successfulOperations, int failedOperations,
                              List<String> actionIds, Map<String, Object> results) {
        this.totalOperations = totalOperations;
        this.successfulOperations = successfulOperations;
        this.failedOperations = failedOperations;
        this.actionIds = List.copyOf(actionIds);
        this.results = Map.copyOf(results);
    }
    
    public int getTotalOperations() { return totalOperations; }
    public int getSuccessfulOperations() { return successfulOperations; }
    public int getFailedOperations() { return failedOperations; }
    public List<String> getActionIds() { return actionIds; }
    public Map<String, Object> getResults() { return results; }
    
    public boolean isSuccess() { return failedOperations == 0; }
    public double getSuccessRate() { return totalOperations > 0 ? (double) successfulOperations / totalOperations : 0.0; }
}