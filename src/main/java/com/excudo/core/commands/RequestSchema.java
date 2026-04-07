package com.excudo.core.commands;

import java.util.List;
import java.util.Map;

/**
 * Defines the JSON schema and structure for LLM requests.
 * Provides validation and parsing utilities for structured LLM output.
 */
public class RequestSchema {
    
    // ========== SCHEMA CONSTANTS ==========

    public static final String SCHEMA_VERSION = "1.0";

    // ========== REQUEST STRUCTURE CLASSES ==========
    
    /**
     * Root request structure from LLM
     */
    public static class LLMRequest {
        private String schemaVersion;
        private List<ActionRequest> actions;
        private RequestMetadata metadata;
        
        public LLMRequest() {}
        
        public LLMRequest(String schemaVersion, List<ActionRequest> actions, RequestMetadata metadata) {
            this.schemaVersion = schemaVersion;
            this.actions = actions;
            this.metadata = metadata;
        }
        
        public String getSchemaVersion() { return schemaVersion; }
        public void setSchemaVersion(String schemaVersion) { this.schemaVersion = schemaVersion; }
        
        public List<ActionRequest> getActions() { return actions; }
        public void setActions(List<ActionRequest> actions) { this.actions = actions; }
        
        public RequestMetadata getMetadata() { return metadata; }
        public void setMetadata(RequestMetadata metadata) { this.metadata = metadata; }
    }
    
    /**
     * Individual operation within a request
     */
    public static class ActionRequest {
        private String type;
        private Map<String, Object> parameters;
        private String description;
        private Integer priority;
        
        public ActionRequest() {}
        
        public ActionRequest(String type, Map<String, Object> parameters, String description, Integer priority) {
            this.type = type;
            this.parameters = parameters;
            this.description = description;
            this.priority = priority;
        }
        
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public Map<String, Object> getParameters() { return parameters; }
        public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public Integer getPriority() { return priority; }
        public void setPriority(Integer priority) { this.priority = priority; }
    }
    
    /**
     * Metadata about the request from LLM
     */
    public static class RequestMetadata {
        private Double confidence;
        private String reasoning;
        private List<String> warnings;
        private Boolean contextUsed;
        
        public RequestMetadata() {}
        
        public RequestMetadata(Double confidence, String reasoning, List<String> warnings, Boolean contextUsed) {
            this.confidence = confidence;
            this.reasoning = reasoning;
            this.warnings = warnings;
            this.contextUsed = contextUsed;
        }
        
        public Double getConfidence() { return confidence; }
        public void setConfidence(Double confidence) { this.confidence = confidence; }
        
        public String getReasoning() { return reasoning; }
        public void setReasoning(String reasoning) { this.reasoning = reasoning; }
        
        public List<String> getWarnings() { return warnings; }
        public void setWarnings(List<String> warnings) { this.warnings = warnings; }
        
        public Boolean getContextUsed() { return contextUsed; }
        public void setContextUsed(Boolean contextUsed) { this.contextUsed = contextUsed; }
    }
    
}