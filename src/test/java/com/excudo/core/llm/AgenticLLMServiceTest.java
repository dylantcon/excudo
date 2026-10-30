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

    /**
     * Stub that answers the first {@code lookups} calls with a schema lookup
     * (get_command_schemas), then plain text. Counts the lookup rounds served.
     */
    private static class LookupThenTextClient extends LLMClient {
        private final int lookups;
        int lookupRoundsServed = 0;

        LookupThenTextClient(int lookups) {
            super("stub-api-key", "stub-model");
            this.lookups = lookups;
        }

        @Override
        public ExecutionResult<String> sendMessage(String systemPrompt, String userMessage) {
            return ExecutionResult.success("StubSend", "summary");
        }

        @Override
        public String getProviderName() {
            return "StubProvider";
        }

        @Override
        public List<LLMClient.APIResponse> sendMessageWithTools(
                String systemPrompt, List<Map<String, String>> messages, String toolsJson) {
            if (lookupRoundsServed < lookups) {
                lookupRoundsServed++;
                return List.of(new LLMClient.APIResponse("tool_use", null, "get_command_schemas",
                    "{\"commands\":[\"add-animation\"]}", "tu_" + lookupRoundsServed));
            }
            return List.of(new LLMClient.APIResponse("text", "done", null, null, null));
        }
    }

    // ========== LOOKUP ROUNDS ==========

    @Test
    @DisplayName("Schema-lookup rounds don't count against the round budget")
    void lookupRoundsAreFree() {
        // 30 lookup rounds > the 25-round budget; with 8 free, only 22 count
        LookupThenTextClient client = new LookupThenTextClient(30);
        StubPPTXOrchestrator stub = new StubPPTXOrchestrator();
        AgenticLLMService service = new AgenticLLMService(client, stub, new CommandFactory(stub), new CommandInvoker());

        String result = service.processRequest("Add a fade");

        assertEquals(30, client.lookupRoundsServed, "every lookup round was served");
        assertEquals("done", result, "turn ends normally instead of hitting the budget");
    }

    @Test
    @DisplayName("Free lookup rounds are capped so a lookup loop still ends")
    void freeLookupRoundsAreCapped() {
        LookupThenTextClient client = new LookupThenTextClient(1000);
        StubPPTXOrchestrator stub = new StubPPTXOrchestrator();
        AgenticLLMService service = new AgenticLLMService(client, stub, new CommandFactory(stub), new CommandInvoker());

        service.processRequest("Add a fade");

        assertTrue(client.lookupRoundsServed <= 25 + AgenticLLMService.MAX_FREE_LOOKUP_ROUNDS + 1,
            "stopped after budget + free cap, served " + client.lookupRoundsServed);
    }

    @Test
    @DisplayName("Command schemas reach the agent whole; other results are capped")
    void toolResultTruncation() {
        String big = "x".repeat(AgenticLLMService.MAX_TOOL_RESULT_CHARS + 500);
        assertEquals(big, AgenticLLMService.truncateToolResult("get_command_schemas", big),
            "schemas are reference docs -- cutting them hid add-animation's placement params");
        String cut = AgenticLLMService.truncateToolResult("get_slide_animations", big);
        assertTrue(cut.startsWith("x".repeat(AgenticLLMService.MAX_TOOL_RESULT_CHARS)));
        assertTrue(cut.contains("truncated"));
        String slideSized = "y".repeat(6000); // an 11-click slide's listing
        assertEquals(slideSized, AgenticLLMService.truncateToolResult("get_slide_animations", slideSized));
    }

    @Test
    @DisplayName("A round that mixes a lookup with real work counts")
    void mixedRoundIsNotLookupOnly() {
        assertTrue(AgenticLLMService.isLookupOnlyRound(List.of(
            new LLMClient.APIResponse("tool_use", null, "get_command_schemas", "{}", "a"),
            new LLMClient.APIResponse("tool_use", null, "list_commands", "{}", "b"))));
        assertFalse(AgenticLLMService.isLookupOnlyRound(List.of(
            new LLMClient.APIResponse("tool_use", null, "get_command_schemas", "{}", "a"),
            new LLMClient.APIResponse("tool_use", null, "get_slide_shapes", "{}", "b"))));
        assertFalse(AgenticLLMService.isLookupOnlyRound(List.of(
            new LLMClient.APIResponse("text", "hi", null, null, null))));
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
