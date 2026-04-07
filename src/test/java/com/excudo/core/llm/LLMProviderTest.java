package com.excudo.core.llm;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for LLMProvider enum resolution and configuration accessors.
 */
public class LLMProviderTest {

    @Test
    public void testFromConfigIdValidIds() {
        assertEquals(LLMProvider.ANTHROPIC, LLMProvider.fromConfigId("anthropic"));
        assertEquals(LLMProvider.OLLAMA, LLMProvider.fromConfigId("ollama"));
        assertEquals(LLMProvider.OPENAI, LLMProvider.fromConfigId("openai"));
        assertEquals(LLMProvider.GEMINI, LLMProvider.fromConfigId("gemini"));
    }

    @Test
    public void testFromConfigIdCaseInsensitive() {
        assertEquals(LLMProvider.ANTHROPIC, LLMProvider.fromConfigId("ANTHROPIC"));
        assertEquals(LLMProvider.ANTHROPIC, LLMProvider.fromConfigId("Anthropic"));
        assertEquals(LLMProvider.OPENAI, LLMProvider.fromConfigId("OpenAI"));
    }

    @Test
    public void testFromConfigIdTrimsWhitespace() {
        assertEquals(LLMProvider.ANTHROPIC, LLMProvider.fromConfigId("  anthropic  "));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFromConfigIdInvalidThrows() {
        LLMProvider.fromConfigId("nonexistent");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFromConfigIdNullThrows() {
        LLMProvider.fromConfigId(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFromConfigIdEmptyThrows() {
        LLMProvider.fromConfigId("");
    }

    @Test
    public void testEnvVarNames() {
        assertEquals("ANTHROPIC_API_KEY", LLMProvider.ANTHROPIC.getApiKeyEnvVar());
        assertEquals("ANTHROPIC_MODEL", LLMProvider.ANTHROPIC.getModelEnvVar());

        assertEquals("OLLAMA_API_KEY", LLMProvider.OLLAMA.getApiKeyEnvVar());
        assertEquals("OLLAMA_MODEL", LLMProvider.OLLAMA.getModelEnvVar());

        assertEquals("OPENAI_API_KEY", LLMProvider.OPENAI.getApiKeyEnvVar());
        assertEquals("OPENAI_MODEL", LLMProvider.OPENAI.getModelEnvVar());

        assertEquals("GEMINI_API_KEY", LLMProvider.GEMINI.getApiKeyEnvVar());
        assertEquals("GEMINI_MODEL", LLMProvider.GEMINI.getModelEnvVar());
    }

    @Test
    public void testDisplayNames() {
        assertEquals("Anthropic Claude", LLMProvider.ANTHROPIC.getDisplayName());
        assertEquals("Ollama (Local)", LLMProvider.OLLAMA.getDisplayName());
        assertEquals("OpenAI", LLMProvider.OPENAI.getDisplayName());
        assertEquals("Google Gemini", LLMProvider.GEMINI.getDisplayName());
    }

    @Test
    public void testConfigIds() {
        assertEquals("anthropic", LLMProvider.ANTHROPIC.getConfigId());
        assertEquals("ollama", LLMProvider.OLLAMA.getConfigId());
        assertEquals("openai", LLMProvider.OPENAI.getConfigId());
        assertEquals("gemini", LLMProvider.GEMINI.getConfigId());
    }
}
