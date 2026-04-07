package com.excudo.core.llm;

/**
 * Supported LLM providers for presentation editing.
 * Each provider maps to its configuration keys and environment variables.
 */
public enum LLMProvider {
    ANTHROPIC("anthropic", "Anthropic Claude", "ANTHROPIC_API_KEY", "ANTHROPIC_MODEL"),
    OLLAMA("ollama", "Ollama (Local)", "OLLAMA_API_KEY", "OLLAMA_MODEL"),
    OPENAI("openai", "OpenAI", "OPENAI_API_KEY", "OPENAI_MODEL"),
    GEMINI("gemini", "Google Gemini", "GEMINI_API_KEY", "GEMINI_MODEL"),
    OPENROUTER("openrouter", "OpenRouter", "OPENROUTER_API_KEY", "OPENROUTER_MODEL");

    private final String configId;
    private final String displayName;
    private final String apiKeyEnvVar;
    private final String modelEnvVar;

    LLMProvider(String configId, String displayName, String apiKeyEnvVar, String modelEnvVar) {
        this.configId = configId;
        this.displayName = displayName;
        this.apiKeyEnvVar = apiKeyEnvVar;
        this.modelEnvVar = modelEnvVar;
    }

    public String getConfigId() { return configId; }
    public String getDisplayName() { return displayName; }
    public String getApiKeyEnvVar() { return apiKeyEnvVar; }
    public String getModelEnvVar() { return modelEnvVar; }

    /**
     * Resolve a provider from its configuration ID (case-insensitive).
     * @throws IllegalArgumentException if no matching provider is found
     */
    public static LLMProvider fromConfigId(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Provider ID cannot be null or empty");
        }
        String normalized = id.trim().toLowerCase();
        for (LLMProvider provider : values()) {
            if (provider.configId.equals(normalized)) {
                return provider;
            }
        }
        throw new IllegalArgumentException("Unknown LLM provider: " + id
            + ". Supported providers: anthropic, ollama, openai, gemini, openrouter");
    }
}
