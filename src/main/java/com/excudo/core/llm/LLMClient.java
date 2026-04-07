package com.excudo.core.llm;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.excudo.core.config.ConfigurationManager;
import com.excudo.core.results.ExecutionResult;
import com.excudo.core.utils.Logger;
import com.excudo.core.utils.ComponentLogger;

/**
 * Abstract base class for LLM API clients.
 * Provides shared infrastructure (JSON escaping, HTTP response reading, message formatting)
 * while delegating provider-specific communication to subclasses.
 */
public abstract class LLMClient {

    private static final ComponentLogger logger = Logger.getLogger(LLMClient.class);

    protected final String apiKey;
    protected final String model;

    // ------------------------------------------------------------------
    // Provider-agnostic response types
    // ------------------------------------------------------------------

    /**
     * Token usage from an API response. Provider-agnostic.
     */
    public record TokenUsage(int inputTokens, int outputTokens, double cost) {
        public static final TokenUsage ZERO = new TokenUsage(0, 0, 0.0);
        /** Backward-compatible constructor for providers that don't report cost. */
        public TokenUsage(int inputTokens, int outputTokens) {
            this(inputTokens, outputTokens, 0.0);
        }
    }

    /**
     * Represents a single content block in an LLM API response.
     * A response may contain multiple blocks of different types (text, tool_use).
     * Provider-agnostic -- each client converts its native format to this.
     */
    public record APIResponse(String type, String content, String toolName, String toolInput, String toolUseId) {
        public boolean isToolUse() { return "tool_use".equals(type); }
        public boolean isText() { return "text".equals(type); }
    }

    /**
     * Last token usage from the most recent API call.
     * Subclasses should update this after each request.
     */
    protected volatile TokenUsage lastTokenUsage = TokenUsage.ZERO;

    public TokenUsage getLastTokenUsage() {
        return lastTokenUsage;
    }

    // ------------------------------------------------------------------
    // Multimodal content blocks (VLM architectural prep)
    // ------------------------------------------------------------------

    /**
     * Sealed interface representing a content block in a multimodal message.
     * Supports text and image content for future VLM integration.
     */
    public sealed interface ContentBlock permits ContentBlock.Text, ContentBlock.Image {
        record Text(String text) implements ContentBlock {}
        record Image(String mediaType, String base64Data) implements ContentBlock {}
    }

    /**
     * Send a multimodal message with tool definitions.
     * Default implementation extracts text from content blocks and delegates to
     * the text-only sendMessageWithTools. Override in subclasses with native
     * multimodal support.
     */
    public List<LLMClient.APIResponse> sendMessageWithToolsMultimodal(
            String systemPrompt, List<ContentBlock> contentBlocks,
            List<Map<String, String>> messages, String toolsJson) {
        // Extract text content and delegate to text-only path
        StringBuilder textContent = new StringBuilder();
        for (ContentBlock block : contentBlocks) {
            if (block instanceof ContentBlock.Text t) {
                textContent.append(t.text());
            }
            // Image blocks are silently dropped in the default implementation
        }
        // Prepend extracted text to the first user message if present
        if (textContent.length() > 0 && !messages.isEmpty()) {
            List<Map<String, String>> augmented = new ArrayList<>(messages);
            Map<String, String> first = new HashMap<>(augmented.get(0));
            String existing = first.getOrDefault("content", "");
            first.put("content", textContent + "\n" + existing);
            augmented.set(0, first);
            return sendMessageWithTools(systemPrompt, augmented, toolsJson);
        }
        return sendMessageWithTools(systemPrompt, messages, toolsJson);
    }

    /**
     * @param apiKey provider API key (must not be null or empty)
     * @param model  model identifier to use for requests
     */
    protected LLMClient(String apiKey, String model) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("API key cannot be null or empty");
        }
        this.apiKey = apiKey;
        this.model = model;
    }

    /**
     * Send a message to the LLM and return the response text.
     * Each subclass implements its own HTTP protocol and authentication.
     */
    public abstract ExecutionResult<String> sendMessage(String systemPrompt, String userMessage);

    /**
     * Human-readable name for the active provider (e.g. "Anthropic Claude").
     */
    public abstract String getProviderName();

    /**
     * @return the model identifier configured for this client
     */
    public String getModel() {
        return model;
    }

    /**
     * Whether this client targets a local/small model (e.g. Ollama) that needs
     * a reduced tool set, shorter system prompt, and fewer round trips.
     */
    public boolean isLocalModel() {
        return false;
    }

    /**
     * Send a message formatted for PowerPoint editing.
     * This is presentation-domain logic shared across all providers.
     */
    public ExecutionResult<String> sendPowerPointEditRequest(String presentationContext, String userRequest) {
        String systemPrompt = isLocalModel()
            ? AnthropicSystemPrompt.COMPACT_EDITOR_PROMPT
            : AnthropicSystemPrompt.POWERPOINT_EDITOR_PROMPT;

        String fullUserMessage = String.format("""
            USER REQUEST: %s

            CURRENT PRESENTATION STATE:
            %s

            Please generate JSON commands to fulfill the user's request.
            """, userRequest, presentationContext);

        return sendMessage(systemPrompt, fullUserMessage);
    }

    /**
     * Send a multi-turn message with tool definitions for agentic interaction.
     *
     * The default implementation falls back to single-shot sendMessage (tools ignored).
     * Override in provider subclasses that support the tool_use protocol.
     *
     * @param systemPrompt the system prompt
     * @param messages     conversation history as role/content pairs
     * @param toolsJson    JSON array of tool definitions (may be ignored by default)
     * @return list of content blocks; at minimum a single text block
     */
    public List<LLMClient.APIResponse> sendMessageWithTools(
            String systemPrompt, List<Map<String, String>> messages, String toolsJson) {
        String lastUserMsg = "";
        for (Map<String, String> msg : messages) {
            if ("user".equals(msg.get("role"))) {
                lastUserMsg = msg.get("content");
            }
        }
        ExecutionResult<String> result = sendMessage(systemPrompt, lastUserMsg);
        List<LLMClient.APIResponse> responses = new ArrayList<>();
        if (result.isSuccess()) {
            responses.add(new LLMClient.APIResponse(
                "text", result.getData().orElse(""), null, null, null));
        }
        return responses;
    }

    // ------------------------------------------------------------------
    // Shared utilities available to all provider subclasses
    // ------------------------------------------------------------------

    /**
     * Read an entire InputStream into a String (UTF-8).
     */
    protected String readResponse(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return "";
        }
        StringBuilder response = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line).append("\n");
            }
        }
        return response.toString();
    }

    /**
     * Escape a string for embedding inside a JSON value.
     */
    protected String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }

    /**
     * Unescape a JSON-encoded string back to its original form.
     */
    protected String unescapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\n", "\n")
                   .replace("\\r", "\r")
                   .replace("\\t", "\t")
                   .replace("\\\"", "\"")
                   .replace("\\\\", "\\");
    }

    // ------------------------------------------------------------------
    // Static factory
    // ------------------------------------------------------------------

    /**
     * Create an LLMClient from the active ConfigurationManager settings.
     * Reads the provider name, resolves API key and model, and instantiates
     * the correct subclass.
     *
     * @throws IllegalStateException if the API key is unavailable
     * @throws IllegalArgumentException if the provider is unsupported
     */
    public static LLMClient fromConfiguration(ConfigurationManager config) {
        String providerName = config.getProviderName();
        LLMProvider provider = LLMProvider.fromConfigId(providerName);

        String apiKey = config.getProviderApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            // Ollama doesn't require an API key -- use a placeholder.
            if (provider == LLMProvider.OLLAMA) {
                apiKey = "ollama";
            } else {
                throw new IllegalStateException(
                    provider.getDisplayName() + " API key is not configured. "
                    + "Set " + provider.getApiKeyEnvVar() + " or use 'llm config set'.");
            }
        }

        String model = config.getProviderModel();

        logger.info("Initializing LLM client: provider={}, model={}", provider.getDisplayName(), model);

        return switch (provider) {
            case ANTHROPIC -> new AnthropicAPIClient(apiKey, model);
            case OLLAMA -> new OllamaClient(apiKey, model);
            case OPENROUTER -> new OpenRouterClient(apiKey, model);
            default -> throw new IllegalArgumentException(
                "Provider '" + provider.getDisplayName() + "' is not yet implemented. "
                + "Currently supported: Anthropic Claude, Ollama, OpenRouter.");
        };
    }
}
