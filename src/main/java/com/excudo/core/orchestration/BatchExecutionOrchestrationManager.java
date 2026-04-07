package com.excudo.core.orchestration;
import com.excudo.core.results.SlideExecutionResult;

import com.excudo.core.results.ExecutionResult;
import com.excudo.core.results.ExecutionResult;
import com.excudo.core.utils.ComponentLogger;
import com.excudo.core.utils.Logger;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

/**
 * Manages batch execution operations including creating multiple slides
 * and executing multiple operations in sequence.
 * 
 * This service extracts batch execution logic from PPTXOrchestratorImpl,
 * providing clean delegation for bulk operations while centralizing
 * batch processing patterns and error handling.
 */
public class BatchExecutionOrchestrationManager {
    
    private static final ComponentLogger logger = Logger.getLogger(BatchExecutionOrchestrationManager.class);
    
    private final OrchestrationContext context;
    private final SlideOrchestrationManager slideOrchestrationManager;
    
    /**
     * Create a BatchExecutionOrchestrationManager with the given orchestration context.
     * 
     * @param context The orchestration context providing access to managers and state
     * @param slideOrchestrationManager The slide manager for slide creation operations
     */
    public BatchExecutionOrchestrationManager(OrchestrationContext context,
                                             SlideOrchestrationManager slideOrchestrationManager) {
        if (context == null) {
            throw new IllegalArgumentException("OrchestrationContext cannot be null");
        }
        if (slideOrchestrationManager == null) {
            throw new IllegalArgumentException("SlideOrchestrationManager cannot be null");
        }
        this.context = context;
        this.slideOrchestrationManager = slideOrchestrationManager;
    }
    
    /**
     * Create multiple slides in batch from specifications.
     * 
     * @param slideSpecs List of slide specifications to create
     * @return Batch operation result with details of successes and failures
     */
    public ExecutionResult<BatchExecutionResult> createSlidesInBatch(List<SlideSpecification> slideSpecs) {
        try {
            if (slideSpecs == null || slideSpecs.isEmpty()) {
                return ExecutionResult.failure("Batch Create Slides", "No slide specifications provided");
            }
            
            logger.info("Creating {} slides in batch", slideSpecs.size());
            
            List<SlideExecutionResult> results = new ArrayList<>();
            List<String> actionIds = new ArrayList<>();
            boolean allSuccessful = true;
            int successCount = 0;
            
            for (int i = 0; i < slideSpecs.size(); i++) {
                SlideSpecification spec = slideSpecs.get(i);
                
                try {
                    logger.debug("Creating slide {}/{}: position={}, title='{}'", 
                                i + 1, slideSpecs.size(), spec.getPosition(), spec.getTitle());
                    
                    // Create slide using slide orchestration manager
                    SlideExecutionResult result;
                    if (spec.getTemplateName() != null && !spec.getTemplateName().trim().isEmpty()) {
                        result = slideOrchestrationManager.createSlide(
                            spec.getPosition(), spec.getTitle(), spec.getTemplateName());
                    } else {
                        result = slideOrchestrationManager.createSlide(
                            spec.getPosition(), spec.getTitle());
                    }
                    
                    results.add(result);
                    
                    if (result.isSuccess()) {
                        successCount++;
                        actionIds.add(generateActionId("create_slide", spec.getPosition()));
                        logger.debug("Successfully created slide at position {}", spec.getPosition());
                    } else {
                        allSuccessful = false;
                        logger.warn("Failed to create slide at position {}: {}", 
                                   spec.getPosition(), result.getMessage());
                    }
                    
                } catch (Exception e) {
                    logger.error("Exception creating slide {}: {}", spec.getPosition(), e.getMessage());
                    
                    SlideExecutionResult errorResult = SlideExecutionResult.slideActionFailed(
                        "Create Slide", spec.getPosition(), e);
                    results.add(errorResult);
                    allSuccessful = false;
                }
            }
            
            int failCount = slideSpecs.size() - successCount;
            
            // Prepare batch result data
            Map<String, Object> batchResults = new HashMap<>();
            batchResults.put("results", results);
            batchResults.put("specifications", slideSpecs);
            batchResults.put("successRate", (double) successCount / slideSpecs.size());
            
            BatchExecutionResult batchResult = new BatchExecutionResult(
                slideSpecs.size(), successCount, failCount, actionIds, batchResults
            );
            
            // Update execution history
            updateExecutionHistory("Batch created " + successCount + " of " + slideSpecs.size() + " slides");
            
            if (allSuccessful) {
                logger.info("Successfully created all {} slides in batch", slideSpecs.size());
                return ExecutionResult.success("Batch Create Slides", batchResult);
            } else {
                logger.warn("Batch create completed with {} successes, {} failures", successCount, failCount);
                return ExecutionResult.failure("Batch Create Slides", 
                    "Some batch operations failed: " + failCount + " of " + slideSpecs.size());
            }
            
        } catch (Exception e) {
            logger.error("Batch slide creation failed: {}", e.getMessage());
            return ExecutionResult.failure("Batch Create Slides", "Batch operation failed: " + e.getMessage(), e);
        }
    }
    
    // ========== UTILITY METHODS ==========
    
    /**
     * Generate unique action ID for tracking batch operations.
     */
    private String generateActionId(String actionType, int index) {
        return "batch_" + actionType + "_" + index + "_" + System.currentTimeMillis();
    }
    
    /**
     * Update batch execution history in context.
     */
    private void updateExecutionHistory(String execution) {
        try {
            Map<String, Object> contextData = context.getContextData();
            
            @SuppressWarnings("unchecked")
            List<String> recentExecutions = (List<String>) contextData.get("recentExecutions");
            
            if (recentExecutions == null) {
                recentExecutions = new ArrayList<>();
                contextData.put("recentExecutions", recentExecutions);
            }
            
            recentExecutions.add(execution);
            
            // Keep only the last 10 executions
            if (recentExecutions.size() > 10) {
                recentExecutions.remove(0);
            }
            
        } catch (Exception e) {
            logger.debug("Failed to update execution history: {}", e.getMessage());
        }
    }
}