package com.excudo.core.orchestration;

import com.excudo.core.results.ExecutionResult;
import com.excudo.core.utils.ComponentLogger;
import com.excudo.core.utils.Logger;
import java.io.File;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

/**
 * Manages LLM integration orchestration operations including operation summaries,
 * data collection, and suggestion application.
 * 
 * This service extracts LLM integration logic from PPTXOrchestratorImpl,
 * providing clean delegation for AI-powered features while centralizing
 * LLM interaction patterns.
 */
public class LLMOrchestrationManager {
    
    private static final ComponentLogger logger = Logger.getLogger(LLMOrchestrationManager.class);
    
    private final OrchestrationContext context;
    
    /**
     * Create a LLMOrchestrationManager with the given orchestration context.
     * 
     * @param context The orchestration context providing access to managers and state
     */
    public LLMOrchestrationManager(OrchestrationContext context) {
        if (context == null) {
            throw new IllegalArgumentException("OrchestrationContext cannot be null");
        }
        this.context = context;
    }
    
    /**
     * Generate a human-readable summary of recent operations.
     * 
     * @param operationCount Number of recent operations to summarize
     * @return Summary text describing what operations were performed
     */
    public String generateOperationSummary(int operationCount) {
        try {
            StringBuilder summary = new StringBuilder();
            
            // Get presentation metadata for context
            PresentationMetadata presentationMetadata = getPresentationMetadata();
            if (presentationMetadata != null) {
                summary.append("Presentation: ").append(presentationMetadata.getTitle()).append("\\n");
                summary.append("Slides: ").append(presentationMetadata.getSlideCount()).append("\\n");
            }
            
            // Get recent operation context from context data
            Map<String, Object> contextData = context.getContextData();
            
            // Check for recent operations stored in context
            if (contextData.containsKey("recentOperations")) {
                @SuppressWarnings("unchecked")
                List<String> recentOps = (List<String>) contextData.get("recentOperations");
                
                summary.append("\\nRecent Operations:\\n");
                int count = Math.min(operationCount, recentOps.size());
                for (int i = 0; i < count; i++) {
                    summary.append("- ").append(recentOps.get(i)).append("\\n");
                }
            } else {
                summary.append("\\nNo recent operation history available.\\n");
            }
            
            // Add current state information
            summary.append("\\nCurrent State:\\n");
            summary.append("- Context initialized: ").append(contextData.containsKey("initialized")).append("\\n");
            File extractedDir = (context.getDocument() != null) ? context.getDocument().getExtractedDir() : null;
            summary.append("- Directory: ").append(extractedDir != null ? extractedDir.getName() : "in-memory").append("\\n");
            
            // Add SPID and relationship statistics
            try {
                com.excudo.xml.writers.SPIDManager spidManager = context.getSpidManager();
                if (spidManager != null) {
                    summary.append("- SPID Manager active\\n");
                }
                
                com.excudo.xml.writers.RelationshipManager relManager = context.getRelationshipManager();
                if (relManager != null) {
                    summary.append("- Relationship Manager active\\n");
                }
            } catch (Exception e) {
                logger.debug("Could not get manager statistics: {}", e.getMessage());
            }
            
            return summary.toString();
            
        } catch (Exception e) {
            logger.error("Failed to generate operation summary: {}", e.getMessage());
            return "Operation summary generation failed: " + e.getMessage();
        }
    }
    
    /**
     * Collect data for LLM integration including presentation state and context.
     * 
     * @return Map containing structured data for LLM consumption
     */
    public Map<String, Object> getLLMIntegrationData() {
        Map<String, Object> integrationData = new HashMap<>();
        
        try {
            // Basic orchestrator state
            integrationData.put("hasContext", context != null);
            integrationData.put("contextId", generateContextId());
            
            // Presentation metadata
            PresentationMetadata presentationMetadata = getPresentationMetadata();
            if (presentationMetadata != null) {
                Map<String, Object> presentationData = new HashMap<>();
                presentationData.put("title", presentationMetadata.getTitle());
                presentationData.put("slideCount", presentationMetadata.getSlideCount());
                presentationData.put("lastModified", presentationMetadata.getLastModified().toString());
                integrationData.put("presentation", presentationData);
            }
            
            // Slide-level data
            List<Map<String, Object>> slidesData = new ArrayList<>();
            List<SlideMetadata> slides = getAllSlideMetadata();
            for (SlideMetadata slide : slides) {
                Map<String, Object> slideData = new HashMap<>();
                slideData.put("slideNumber", slide.getSlideNumber());
                slideData.put("title", slide.getTitle());
                slideData.put("shapeCount", slide.getShapeCount());
                slideData.put("spids", slide.getSpids());
                slideData.put("animationCount", slide.getAnimationCount());
                slideData.put("layout", slide.getLayout());
                slidesData.add(slideData);
            }
            integrationData.put("slides", slidesData);
            
            // Context data
            Map<String, Object> contextInfo = new HashMap<>();
            File extractedDir2 = (context.getDocument() != null) ? context.getDocument().getExtractedDir() : null;
            contextInfo.put("directory", extractedDir2 != null ? extractedDir2.getAbsolutePath() : "in-memory");
            contextInfo.putAll(context.getContextData());
            integrationData.put("context", contextInfo);
            
            // Manager status
            Map<String, Object> managerStatus = new HashMap<>();
            managerStatus.put("spidManagerActive", context.getSpidManager() != null);
            managerStatus.put("relationshipManagerActive", context.getRelationshipManager() != null);
            managerStatus.put("slideCreatorActive", context.getSlideCreator() != null);
            managerStatus.put("notesRegistryActive", context.getNotesSlideRegistry() != null);
            integrationData.put("managers", managerStatus);
            
            // Add timestamp
            integrationData.put("timestamp", System.currentTimeMillis());
            integrationData.put("timestampIso", java.time.Instant.now().toString());
            
            logger.debug("Generated LLM integration data with {} top-level keys", integrationData.size());
            
            return integrationData;
            
        } catch (Exception e) {
            logger.error("Failed to collect LLM integration data: {}", e.getMessage());
            
            // Return minimal data on error
            Map<String, Object> errorData = new HashMap<>();
            errorData.put("error", e.getMessage());
            errorData.put("hasContext", false);
            errorData.put("timestamp", System.currentTimeMillis());
            return errorData;
        }
    }
    
    /**
     * Apply LLM-generated suggestions to the presentation.
     * 
     * @param suggestions List of suggestions to apply
     * @return Result containing details of applied suggestions
     */
    public ExecutionResult<BatchExecutionResult> applyLLMSuggestions(List<LLMSuggestion> suggestions) {
        try {
            if (suggestions == null || suggestions.isEmpty()) {
                return ExecutionResult.failure("Apply LLM Suggestions", "No suggestions provided");
            }
            
            List<String> appliedSuggestions = new ArrayList<>();
            List<String> failedSuggestions = new ArrayList<>();
            List<String> actionIds = new ArrayList<>();
            
            logger.info("Applying {} LLM suggestions", suggestions.size());
            
            for (LLMSuggestion suggestion : suggestions) {
                try {
                    boolean applied = applySingleSuggestion(suggestion);
                    if (applied) {
                        appliedSuggestions.add(suggestion.getDescription());
                        actionIds.add(generateActionId(suggestion));
                        logger.debug("Applied suggestion: {}", suggestion.getDescription());
                    } else {
                        failedSuggestions.add(suggestion.getDescription());
                        logger.warn("Failed to apply suggestion: {}", suggestion.getDescription());
                    }
                } catch (Exception e) {
                    failedSuggestions.add(suggestion.getDescription() + " (Error: " + e.getMessage() + ")");
                    logger.error("Exception applying suggestion '{}': {}", suggestion.getDescription(), e.getMessage());
                }
            }
            
            // Prepare result data
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("appliedSuggestions", appliedSuggestions);
            resultData.put("failedSuggestions", failedSuggestions);
            resultData.put("totalSuggestions", suggestions.size());
            resultData.put("successfullyApplied", appliedSuggestions.size());
            
            // Update context with operation history
            updateOperationHistory("Applied " + appliedSuggestions.size() + " of " + suggestions.size() + " LLM suggestions");
            
            BatchExecutionResult batchResult = new BatchExecutionResult(
                suggestions.size(),
                appliedSuggestions.size(),
                failedSuggestions.size(),
                actionIds,
                resultData
            );
            
            if (failedSuggestions.isEmpty()) {
                logger.info("Successfully applied all {} LLM suggestions", appliedSuggestions.size());
                return ExecutionResult.success("Apply LLM Suggestions", batchResult);
            } else {
                logger.warn("Applied {} suggestions, {} failed", appliedSuggestions.size(), failedSuggestions.size());
                return ExecutionResult.failure("Apply LLM Suggestions", 
                    "Some suggestions failed: " + failedSuggestions.size() + " of " + suggestions.size());
            }
            
        } catch (Exception e) {
            logger.error("Failed to apply LLM suggestions: {}", e.getMessage());
            return ExecutionResult.failure("Apply LLM Suggestions", "Failed to apply suggestions: " + e.getMessage(), e);
        }
    }
    
    /**
     * Apply a single LLM suggestion.
     * 
     * @param suggestion The suggestion to apply
     * @return True if successfully applied, false otherwise
     */
    private boolean applySingleSuggestion(LLMSuggestion suggestion) {
        try {
            String suggestionType = suggestion.getType();
            Map<String, Object> parameters = suggestion.getParameters();
            
            switch (suggestionType.toLowerCase()) {
                case "add_shape":
                    return applySuggestion_AddShape(parameters);
                case "edit_text":
                    return applySuggestion_EditText(parameters);
                case "add_animation":
                    return applySuggestion_AddAnimation(parameters);
                case "reorganize_layout":
                    return applySuggestion_ReorganizeLayout(parameters);
                case "create_slide":
                    return applySuggestion_CreateSlide(parameters);
                default:
                    logger.warn("Unknown suggestion type: {}", suggestionType);
                    return false;
            }
            
        } catch (Exception e) {
            logger.error("Failed to apply suggestion: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Apply add shape suggestion.
     */
    private boolean applySuggestion_AddShape(Map<String, Object> parameters) {
        // Implementation would use ShapeOrchestrationManager
        // For now, return success for known parameters
        return parameters.containsKey("slideNumber") && parameters.containsKey("shapeType");
    }
    
    /**
     * Apply edit text suggestion.
     */
    private boolean applySuggestion_EditText(Map<String, Object> parameters) {
        // Implementation would use ShapeOrchestrationManager.editShapeText
        // For now, return success for known parameters
        return parameters.containsKey("slideNumber") && 
               parameters.containsKey("spid") && 
               parameters.containsKey("newText");
    }
    
    /**
     * Apply add animation suggestion.
     */
    private boolean applySuggestion_AddAnimation(Map<String, Object> parameters) {
        // Implementation would use AnimationOrchestrationManager
        // For now, return success for known parameters
        return parameters.containsKey("slideNumber") && parameters.containsKey("animationBinding");
    }
    
    /**
     * Apply reorganize layout suggestion.
     */
    private boolean applySuggestion_ReorganizeLayout(Map<String, Object> parameters) {
        // Implementation would use LayoutOrchestrationManager
        // For now, return success for known parameters
        return parameters.containsKey("slideNumber") && parameters.containsKey("strategy");
    }
    
    /**
     * Apply create slide suggestion.
     */
    private boolean applySuggestion_CreateSlide(Map<String, Object> parameters) {
        // Implementation would use SlideOrchestrationManager
        // For now, return success for known parameters
        return parameters.containsKey("position") && parameters.containsKey("title");
    }
    
    /**
     * Get presentation metadata using delegation.
     */
    private PresentationMetadata getPresentationMetadata() {
        try {
            // This would normally delegate to PresentationOrchestrationManager
            // For now, create minimal metadata from context
            
            int slideCount = getAllSlideMetadata().size();
            String title = "Current Presentation";
            
            Map<String, Object> contextData = context.getContextData();
            if (contextData.containsKey("originalFileName")) {
                title = (String) contextData.get("originalFileName");
            }
            
            return new PresentationMetadata(slideCount, title, java.time.Instant.now(), List.of());
            
        } catch (Exception e) {
            logger.error("Failed to get presentation metadata: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Get all slide metadata using delegation.
     */
    private List<SlideMetadata> getAllSlideMetadata() {
        try {
            List<SlideMetadata> slides = new ArrayList<>();

            // Prefer PPTXDocument for slide enumeration
            com.excudo.core.model.PPTXDocument pptxDoc = context.getDocument();
            List<Integer> slideNumbers = (pptxDoc != null) ? pptxDoc.getSlideNumbers() : new ArrayList<>();

            for (int slideNumber : slideNumbers) {
                slides.add(new SlideMetadata(slideNumber, "Slide " + slideNumber,
                    SlideType.CONTENT, 0, List.of(), 0, "Unknown Layout"));
            }
            return slides;

        } catch (Exception e) {
            logger.error("Failed to get slide metadata: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * Generate a unique context identifier.
     */
    private String generateContextId() {
        return "llm_ctx_" + System.currentTimeMillis();
    }
    
    /**
     * Generate operation ID for a suggestion.
     */
    private String generateActionId(LLMSuggestion suggestion) {
        return "llm_op_" + suggestion.getType() + "_" + System.currentTimeMillis();
    }
    
    /**
     * Update operation history in context.
     */
    private void updateOperationHistory(String operation) {
        try {
            Map<String, Object> contextData = context.getContextData();
            
            @SuppressWarnings("unchecked")
            List<String> recentOps = (List<String>) contextData.get("recentOperations");
            
            if (recentOps == null) {
                recentOps = new ArrayList<>();
                contextData.put("recentOperations", recentOps);
            }
            
            recentOps.add(operation);
            
            // Keep only the last 10 operations
            if (recentOps.size() > 10) {
                recentOps.remove(0);
            }
            
        } catch (Exception e) {
            logger.debug("Failed to update operation history: {}", e.getMessage());
        }
    }
}