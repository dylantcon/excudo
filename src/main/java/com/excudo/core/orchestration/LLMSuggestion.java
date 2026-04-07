package com.excudo.core.orchestration;

import java.util.Map;

/**
 * LLM-generated suggestion for presentation modifications
 */
public class LLMSuggestion {
    private final String suggestionId;
    private final String actionType;
    private final String description;
    private final Map<String, Object> parameters;
    private final double confidence;
    
    public LLMSuggestion(String suggestionId, String actionType, String description, 
                        Map<String, Object> parameters, double confidence) {
        this.suggestionId = suggestionId;
        this.actionType = actionType;
        this.description = description;
        this.parameters = Map.copyOf(parameters);
        this.confidence = confidence;
    }
    
    public String getSuggestionId() { return suggestionId; }
    public String getActionType() { return actionType; }
    public String getDescription() { return description; }
    public Map<String, Object> getParameters() { return parameters; }
    public double getConfidence() { return confidence; }
    public String getType() { return actionType; } // Alias for getActionType
}