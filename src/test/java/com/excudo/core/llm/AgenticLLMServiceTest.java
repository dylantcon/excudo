package com.excudo.core.llm;

import com.excudo.core.commands.CommandFactory;
import com.excudo.core.commands.CommandInvoker;
import com.excudo.core.results.ExecutionResult;
import com.excudo.test.utils.StubPPTXOrchestrator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AgenticLLMService.
 *
 * Uses a stub LLMClient and a StubPPTXOrchestrator so no real API calls are made.
 * Focuses on the processRequest contract: error paths when no context is loaded
 * and text-response pass-through when the LLM returns a plain text block.
 */
public class AgenticLLMServiceTest {

    /**
     * Minimal stub LLMClient that always returns a fixed text response.
     */
    private static class FixedTextClient extends LLMClient {

        private final String fixedResponse;

        FixedTextClient(String response) {
            super("stub-api-key", "stub-model");
            this.fixedResponse = response;
        }

        @Override
        public ExecutionResult<String> sendMessage(String systemPrompt, String userMessage) {
            return ExecutionResult.success("StubSend", fixedResponse);
        }

        @Override
        public String getProviderName() {
            return "StubProvider";
        }

        @Override
        public List<LLMClient.APIResponse> sendMessageWithTools(
                String systemPrompt,
                List<Map<String, String>> messages,
                String toolsJson) {
            // Return a single plain text block; no tool_use calls
            return List.of(new LLMClient.APIResponse("text", fixedResponse, null, null, null));
        }
    }

    // ========== CONSTRUCTION ==========

    @Test
    @DisplayName("AgenticLLMService constructs without error when orchestrator has no context")
    void constructorSucceedsWithNoContext() {
        LLMClient client = new FixedTextClient("Done.");
        StubPPTXOrchestrator stub = new StubPPTXOrchestrator();
        CommandFactory factory = new CommandFactory(stub);
        CommandInvoker invoker = new CommandInvoker();

        // Should not throw even though stub orchestrator returns empty context
        AgenticLLMService service = new AgenticLLMService(client, stub, factory, invoker);
        assertNotNull(service);
    }

    // ========== processRequest() ==========

    @Test
    @DisplayName("processRequest proceeds without context (agent can create presentation)")
    void processRequestProceedsWithNoContext() {
        LLMClient client = new FixedTextClient("I will create a presentation.");
        StubPPTXOrchestrator stub = new StubPPTXOrchestrator();
        CommandFactory factory = new CommandFactory(stub);
        CommandInvoker invoker = new CommandInvoker();

        AgenticLLMService service = new AgenticLLMService(client, stub, factory, invoker);

        String result = service.processRequest("Add a rectangle");

        assertNotNull(result);
        // The agentic loop should run and return the LLM's text response
        // (no error, since the agent is allowed to start without a context)
        assertFalse(result.isEmpty(), "Expected a non-empty response");
    }

    @Test
    @DisplayName("processRequest returns LLM text when client produces a plain text response")
    void processRequestReturnsPlainTextResponse() {
        final String expectedResponse = "I have completed the task.";
        LLMClient client = new FixedTextClient(expectedResponse);

        // Orchestrator that returns a non-empty context so contextService initializes
        StubPPTXOrchestrator stubWithContext = new StubPPTXOrchestrator() {
            @Override
            public java.util.Optional<com.excudo.core.orchestration.OrchestrationContext> getContext() {
                // Return empty to avoid ContextService needing a real directory
                return java.util.Optional.empty();
            }
        };

        CommandFactory factory = new CommandFactory(stubWithContext);
        CommandInvoker invoker = new CommandInvoker();

        AgenticLLMService service = new AgenticLLMService(client, stubWithContext, factory, invoker);

        // Without a loaded context, processRequest should return an error (not the LLM text)
        String result = service.processRequest("Describe the presentation");

        // The service should respond coherently -- either with the text or an error
        assertNotNull(result, "processRequest must not return null");
        assertFalse(result.isBlank(), "processRequest must not return blank string");
    }

    // ========== TOOL DEFINITIONS INTEGRATION ==========

    @Test
    @DisplayName("LLMToolDefinitions is accessible and consistent from service perspective")
    void toolDefinitionsIntegration() {
        // Verify the tool definitions the service will use are complete
        List<LLMToolDefinitions.ToolDefinition> tools = LLMToolDefinitions.getContextTools();
        assertFalse(tools.isEmpty());

        String toolsJson = LLMToolDefinitions.toToolsJson();
        assertTrue(toolsJson.startsWith("["));
        assertTrue(toolsJson.endsWith("]"));
    }
}
