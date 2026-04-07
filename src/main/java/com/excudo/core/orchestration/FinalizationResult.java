package com.excudo.core.orchestration;

import java.util.List;

/**
 * Result of finalization operations
 */
public class FinalizationResult {
    private final boolean success;
    private final List<String> completedTasks;
    private final List<String> pendingTasks;
    private final String summary;
    
    public FinalizationResult(boolean success, List<String> completedTasks, 
                            List<String> pendingTasks, String summary) {
        this.success = success;
        this.completedTasks = List.copyOf(completedTasks);
        this.pendingTasks = List.copyOf(pendingTasks);
        this.summary = summary;
    }
    
    public boolean isSuccess() { return success; }
    public List<String> getCompletedTasks() { return completedTasks; }
    public List<String> getPendingTasks() { return pendingTasks; }
    public String getSummary() { return summary; }
}