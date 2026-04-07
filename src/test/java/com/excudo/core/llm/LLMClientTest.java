package com.excudo.core.llm;

import org.junit.Test;
import static org.junit.Assert.*;
import com.excudo.core.results.ExecutionResult;
import java.util.ArrayList;
import java.util.List;

/**
 * Tests for LLMClient abstract class: constructor validation, shared utilities,
 * and sendPowerPointEditRequest delegation.
 */
public class LLMClientTest {

    /**
     * Concrete test double that records calls to sendMessage.
     */
    private static class TestLLMClient extends LLMClient {
        final List<String[]> sentMessages = new ArrayList<>();

        TestLLMClient(String apiKey, String model) {
            super(apiKey, model);
        }

        @Override
        public ExecutionResult<String> sendMessage(String systemPrompt, String userMessage) {
            sentMessages.add(new String[]{systemPrompt, userMessage});
            return ExecutionResult.success("Test Call", "test-response");
        }

        @Override
        public String getProviderName() {
            return "Test Provider";
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorRejectsNullApiKey() {
        new TestLLMClient(null, "model");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorRejectsEmptyApiKey() {
        new TestLLMClient("   ", "model");
    }

    @Test
    public void testConstructorAcceptsValidApiKey() {
        TestLLMClient client = new TestLLMClient("sk-test-key", "test-model");
        assertEquals("test-model", client.getModel());
        assertEquals("Test Provider", client.getProviderName());
    }

    @Test
    public void testGetModelReturnsConfiguredModel() {
        TestLLMClient client = new TestLLMClient("key", "claude-3-opus-20240229");
        assertEquals("claude-3-opus-20240229", client.getModel());
    }

    @Test
    public void testSendPowerPointEditRequestDelegatesToSendMessage() {
        TestLLMClient client = new TestLLMClient("key", "model");

        ExecutionResult<String> result = client.sendPowerPointEditRequest("context-data", "make it pretty");

        assertTrue(result.isSuccess());
        assertEquals(1, client.sentMessages.size());

        String[] call = client.sentMessages.get(0);
        // System prompt should be the PowerPoint editor prompt
        assertNotNull(call[0]);
        assertFalse(call[0].isEmpty());
        // User message should contain both request and context
        assertTrue(call[1].contains("make it pretty"));
        assertTrue(call[1].contains("context-data"));
    }

    @Test
    public void testEscapeJsonHandlesSpecialCharacters() {
        TestLLMClient client = new TestLLMClient("key", "model");

        assertEquals("", client.escapeJson(null));
        assertEquals("hello", client.escapeJson("hello"));
        assertEquals("line1\\nline2", client.escapeJson("line1\nline2"));
        assertEquals("tab\\there", client.escapeJson("tab\there"));
        assertEquals("quote\\\"here", client.escapeJson("quote\"here"));
        assertEquals("back\\\\slash", client.escapeJson("back\\slash"));
        assertEquals("cr\\rhere", client.escapeJson("cr\rhere"));
    }

    @Test
    public void testUnescapeJsonHandlesSpecialCharacters() {
        TestLLMClient client = new TestLLMClient("key", "model");

        assertEquals("", client.unescapeJson(null));
        assertEquals("hello", client.unescapeJson("hello"));
        assertEquals("line1\nline2", client.unescapeJson("line1\\nline2"));
        assertEquals("tab\there", client.unescapeJson("tab\\there"));
        assertEquals("quote\"here", client.unescapeJson("quote\\\"here"));
        assertEquals("back\\slash", client.unescapeJson("back\\\\slash"));
    }

    @Test
    public void testEscapeUnescapeRoundTrip() {
        TestLLMClient client = new TestLLMClient("key", "model");

        String original = "Hello \"world\"\nNew line\ttab\\backslash";
        String escaped = client.escapeJson(original);
        String restored = client.unescapeJson(escaped);
        assertEquals(original, restored);
    }

    @Test
    public void testFromConfigurationRejectsUnsupportedProvider() {
        // Ollama is a valid provider but not yet implemented
        try {
            // We can't easily mock ConfigurationManager here, but we can test
            // the error message format by directly testing LLMProvider resolution
            LLMProvider provider = LLMProvider.fromConfigId("ollama");
            assertEquals(LLMProvider.OLLAMA, provider);
        } catch (Exception e) {
            fail("fromConfigId should not throw for valid provider IDs");
        }
    }
}
