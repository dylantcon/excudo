package com.excudo.core.commands;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.orchestration.BatchExecutionResult;

/**
 * Interface abstracting LLM functionality from console implementations.
 * Commands depend on this interface instead of concrete console classes.
 */
public interface LLMHandler {

    /**
     * Process a natural language edit request and return a proposal.
     */
    LLMEditProposal processEditRequest(String request) throws Exception;

    /**
     * Execute an approved proposal through the command pipeline.
     */
    BatchExecutionResult executeApprovedProposal(LLMEditProposal proposal, CommandInvoker commandInvoker) throws Exception;

    /**
     * Process an edit request using the agentic multi-turn approach.
     * Returns a summary string of what was done.
     * Default implementation falls back to the non-agentic path.
     */
    default String processEditRequestAgentic(String request, CommandInvoker commandInvoker) throws Exception {
        LLMEditProposal proposal = processEditRequest(request);
        BatchExecutionResult result = executeApprovedProposal(proposal, commandInvoker);
        return result.isSuccess() ? "Edit completed" : "Edit failed";
    }

    /**
     * Check if agentic (multi-turn tool-use) mode is available.
     */
    default boolean isAgenticAvailable() {
        return false;
    }

    /**
     * Whether the active LLM client targets a local/small model (e.g. Ollama).
     */
    default boolean isLocalModel() {
        return false;
    }

    /**
     * Generate enhanced context for the current presentation state.
     */
    String generateContext(PPTXOrchestrator orchestrator, boolean detailed) throws Exception;

    /**
     * Handle an LLM config operation (show, set, clear, provider).
     * @param action the config action string (e.g. "set <key>", "clear", "provider anthropic", or empty for show)
     */
    default void handleConfigAction(String action) {
        System.out.println("LLM configuration not available in this context.");
    }

    /**
     * Data class representing an LLM-generated edit proposal.
     */
    class LLMEditProposal {
        private final String originalRequest;
        private final String jsonCommand;
        private final String fullLLMResponse;
        private final String operationsSummary;

        public LLMEditProposal(String originalRequest, String jsonCommand, String fullLLMResponse, String operationsSummary) {
            this.originalRequest = originalRequest;
            this.jsonCommand = jsonCommand;
            this.fullLLMResponse = fullLLMResponse;
            this.operationsSummary = operationsSummary;
        }

        public String getOriginalRequest() { return originalRequest; }
        public String getJsonCommand() { return jsonCommand; }
        public String getFullLLMResponse() { return fullLLMResponse; }
        public String getOperationsSummary() { return operationsSummary; }
    }
}
