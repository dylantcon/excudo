package com.excudo.core.llm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LLMClient.APIResponse.
 *
 * Tests the public record directly without making any HTTP calls.
 * Covers isText(), isToolUse(), and the field accessors for both content block types.
 */
public class AnthropicAPIClientToolUseTest {

    // ========== text block ==========

    @Test
    @DisplayName("isText returns true for text-type response")
    void isTextReturnsTrueForTextType() {
        LLMClient.APIResponse response =
            new LLMClient.APIResponse("text", "hello", null, null, null);
        assertTrue(response.isText());
        assertFalse(response.isToolUse());
    }

    @Test
    @DisplayName("Text response content is accessible")
    void textResponseContentAccessible() {
        LLMClient.APIResponse response =
            new LLMClient.APIResponse("text", "hello world", null, null, null);
        assertEquals("hello world", response.content());
    }

    @Test
    @DisplayName("Text response has null tool fields")
    void textResponseHasNullToolFields() {
        LLMClient.APIResponse response =
            new LLMClient.APIResponse("text", "some text", null, null, null);
        assertNull(response.toolName());
        assertNull(response.toolInput());
        assertNull(response.toolUseId());
    }

    // ========== tool_use block ==========

    @Test
    @DisplayName("isToolUse returns true for tool_use-type response")
    void isToolUseReturnsTrueForToolUseType() {
        LLMClient.APIResponse response =
            new LLMClient.APIResponse("tool_use", null, "get_slide_shapes",
                "{\"slideNumber\":1}", "tu_abc123");
        assertTrue(response.isToolUse());
        assertFalse(response.isText());
    }

    @Test
    @DisplayName("Tool use response fields are accessible")
    void toolUseFieldsAccessible() {
        LLMClient.APIResponse response =
            new LLMClient.APIResponse("tool_use", null, "get_slide_shapes",
                "{\"slideNumber\":2}", "tu_xyz");
        assertEquals("get_slide_shapes", response.toolName());
        assertEquals("{\"slideNumber\":2}", response.toolInput());
        assertEquals("tu_xyz", response.toolUseId());
        assertNull(response.content());
    }

    // ========== unknown type ==========

    @Test
    @DisplayName("Neither isText nor isToolUse returns true for unknown type")
    void unknownTypeReturnsFalseBothChecks() {
        LLMClient.APIResponse response =
            new LLMClient.APIResponse("thinking", "...", null, null, null);
        assertFalse(response.isText());
        assertFalse(response.isToolUse());
    }

    // ========== equality and record semantics ==========

    @Test
    @DisplayName("Two APIResponse records with identical fields are equal")
    void recordEqualityHoldsForIdenticalFields() {
        LLMClient.APIResponse r1 =
            new LLMClient.APIResponse("text", "hi", null, null, null);
        LLMClient.APIResponse r2 =
            new LLMClient.APIResponse("text", "hi", null, null, null);
        assertEquals(r1, r2);
    }

    @Test
    @DisplayName("APIResponse records with different content are not equal")
    void recordInequalityForDifferentContent() {
        LLMClient.APIResponse r1 =
            new LLMClient.APIResponse("text", "hello", null, null, null);
        LLMClient.APIResponse r2 =
            new LLMClient.APIResponse("text", "goodbye", null, null, null);
        assertNotEquals(r1, r2);
    }

    @Test
    @DisplayName("APIResponse type field is exposed via record accessor")
    void typeFieldAccessible() {
        LLMClient.APIResponse response =
            new LLMClient.APIResponse("text", "body", null, null, null);
        assertEquals("text", response.type());
    }
}
