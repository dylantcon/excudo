package com.excudo.core.llm;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.excudo.core.results.ExecutionResult;
import com.excudo.core.utils.EnvLoader;
import com.excudo.core.utils.Logger;
import com.excudo.core.utils.ComponentLogger;
import com.google.gson.*;

/**
 * Anthropic Claude API client.
 * Handles Anthropic-specific HTTP protocol, authentication headers, and response parsing.
 */
public class AnthropicAPIClient extends LLMClient {

    private static final ComponentLogger logger = Logger.llm();
    private static final Gson GSON = new Gson();

    // TokenUsage and APIResponse are now defined on LLMClient (provider-agnostic).
    // lastTokenUsage field and getLastTokenUsage() inherited from LLMClient.

    private static final String API_ENDPOINT = "https://api.anthropic.com/v1/messages";
    private static final String API_VERSION = "2023-06-01";
    private static final String DEFAULT_MODEL = "claude-sonnet-4-6";
    private static final int TOOL_MAX_TOKENS = 16_000;
    private static final int CONNECT_TIMEOUT_MS = 30_000;
    private static final int READ_TIMEOUT_MS = 600_000;
    private static final int MAX_RETRIES = 3;

    private boolean extendedThinking = false;

    public AnthropicAPIClient(String apiKey) {
        this(apiKey, DEFAULT_MODEL);
    }

    public AnthropicAPIClient(String apiKey, String model) {
        super(apiKey, model);

        String thinkingEnabled = EnvLoader.get("ANTHROPIC_EXTENDED_THINKING");
        this.extendedThinking = "true".equalsIgnoreCase(thinkingEnabled);
    }

    /**
     * Create a client using API key from environment variable
     */
    public static AnthropicAPIClient fromEnvironment() {
        String apiKey = EnvLoader.get("ANTHROPIC_API_KEY");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException("ANTHROPIC_API_KEY environment variable is not set");
        }

        String model = EnvLoader.get("ANTHROPIC_MODEL");
        if (model != null && !model.trim().isEmpty()) {
            return new AnthropicAPIClient(apiKey, model);
        }

        return new AnthropicAPIClient(apiKey);
    }

    /**
     * Create a client with custom model using API key from environment variable
     */
    public static AnthropicAPIClient fromEnvironment(String model) {
        String apiKey = EnvLoader.get("ANTHROPIC_API_KEY");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException("ANTHROPIC_API_KEY environment variable is not set");
        }
        return new AnthropicAPIClient(apiKey, model);
    }

    public void setExtendedThinking(boolean enabled) {
        this.extendedThinking = enabled;
    }

    public boolean isExtendedThinkingEnabled() {
        return this.extendedThinking;
    }

    @Override
    public String getProviderName() {
        return "Anthropic Claude";
    }

    @Override
    public ExecutionResult<String> sendMessage(String systemPrompt, String userMessage) {
        try {
            String response = post(buildRequestBody(systemPrompt, userMessage));
            return ExecutionResult.success("Anthropic API Call", extractContentFromResponse(response));
        } catch (LLMApiException e) {
            return ExecutionResult.failure("Anthropic API Call", e.getMessage());
        }
    }

    /**
     * POST a Messages API request and return the 200 response body. Retries
     * rate-limit / overload / 5xx responses and connection failures with
     * backoff; anything else throws {@link LLMApiException} with the API's
     * own error message.
     */
    private String post(String requestBody) {
        LLMApiException last = null;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            if (attempt > 0) {
                long delayMs = Math.min(30_000L, 2_000L << (attempt - 1));
                logger.warn("Anthropic request failed ({}); retry {}/{} in {} ms",
                    last.getMessage(), attempt, MAX_RETRIES, delayMs);
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw last;
                }
            }
            HttpURLConnection connection = null;
            try {
                URL url = java.net.URI.create(API_ENDPOINT).toURL();
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
                connection.setReadTimeout(READ_TIMEOUT_MS);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("x-api-key", apiKey);
                connection.setRequestProperty("anthropic-version", API_VERSION);
                connection.setDoOutput(true);

                try (OutputStream os = connection.getOutputStream()) {
                    os.write(requestBody.getBytes(StandardCharsets.UTF_8));
                }

                int code = connection.getResponseCode();
                if (code == HttpURLConnection.HTTP_OK) {
                    return readResponse(connection.getInputStream());
                }
                String errorBody = connection.getErrorStream() != null
                    ? readResponse(connection.getErrorStream()) : "";
                last = new LLMApiException(describeError(code, errorBody), code);
                if (!isRetryable(code)) throw last;
            } catch (LLMApiException e) {
                throw e;
            } catch (IOException e) {
                last = new LLMApiException("Could not reach the Anthropic API: " + e.getMessage(), e);
            } finally {
                if (connection != null) connection.disconnect();
            }
        }
        throw last;
    }

    private static boolean isRetryable(int code) {
        return code == 429 || code == 500 || code == 502 || code == 503 || code == 504 || code == 529;
    }

    private String describeError(int code, String errorBody) {
        String detail = errorBody;
        try {
            JsonObject err = JsonParser.parseString(errorBody).getAsJsonObject().getAsJsonObject("error");
            if (err != null && err.has("message")) {
                detail = err.get("message").getAsString();
            }
        } catch (Exception ignored) {
            // Non-JSON body: report it raw.
        }
        String msg = "Anthropic API error " + code + ": " + detail;
        if (code == 401) {
            msg += "\nThe API key was rejected. Check ANTHROPIC_API_KEY in .env, and run "
                + "'llm config show' -- a key saved with 'llm config set' overrides .env "
                + "('llm config clear' removes it).";
        } else if (code == 404 && detail.contains("model")) {
            msg += "\nCheck ANTHROPIC_MODEL (currently '" + model + "').";
        }
        return msg;
    }

    private String buildRequestBody(String systemPrompt, String userMessage) {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("max_tokens", 4096);

        if (extendedThinking) {
            JsonObject thinking = new JsonObject();
            thinking.addProperty("type", "adaptive");
            body.add("thinking", thinking);
        }

        JsonArray messages = new JsonArray();
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", userMessage);
        messages.add(userMsg);
        body.add("messages", messages);

        body.addProperty("system", systemPrompt);

        return GSON.toJson(body);
    }

    private String extractContentFromResponse(String response) {
        try {
            JsonObject root = JsonParser.parseString(response).getAsJsonObject();
            JsonArray content = root.getAsJsonArray("content");
            if (content == null) return response;

            for (JsonElement el : content) {
                JsonObject block = el.getAsJsonObject();
                if ("text".equals(block.get("type").getAsString())) {
                    return block.get("text").getAsString();
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to parse response with Gson, returning raw: {}", e.getMessage());
        }
        return response;
    }

    // ------------------------------------------------------------------
    // Tool-use (agentic) API support
    // ------------------------------------------------------------------

    @Override
    public List<APIResponse> sendMessageWithTools(String systemPrompt,
                                                   List<Map<String, String>> messages,
                                                   String toolsJson) {
        String response = post(buildRequestBodyWithTools(systemPrompt, messages, toolsJson));
        this.lastTokenUsage = parseTokenUsage(response);
        return parseToolUseResponse(response);
    }

    private String buildRequestBodyWithTools(String systemPrompt,
                                              List<Map<String, String>> messages,
                                              String toolsJson) {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("max_tokens", TOOL_MAX_TOKENS);
        body.addProperty("system", systemPrompt);

        JsonArray msgArray = new JsonArray();
        for (Map<String, String> msg : messages) {
            JsonObject msgObj = new JsonObject();
            msgObj.addProperty("role", msg.get("role"));
            String content = msg.get("content");
            // Content that is already a JSON array (tool_use or tool_result blocks) must
            // not be quoted again - it is embedded verbatim.
            // Guard: only parse if it looks like a JSON array AND actually parses.
            // Compacted messages like "[prior result: ...]" start with [ but aren't JSON.
            if (content != null && content.trim().startsWith("[{")) {
                try {
                    msgObj.add("content", JsonParser.parseString(content));
                } catch (com.google.gson.JsonSyntaxException e) {
                    msgObj.addProperty("content", content);
                }
            } else {
                msgObj.addProperty("content", content != null ? content : "");
            }
            msgArray.add(msgObj);
        }
        body.add("messages", msgArray);

        body.add("tools", JsonParser.parseString(toolsJson));

        return GSON.toJson(body);
    }

    private List<APIResponse> parseToolUseResponse(String response) {
        List<APIResponse> results = new ArrayList<>();
        try {
            JsonObject root = JsonParser.parseString(response).getAsJsonObject();
            JsonArray content = root.getAsJsonArray("content");
            if (content == null) return results;

            String stopReason = root.has("stop_reason") && !root.get("stop_reason").isJsonNull()
                ? root.get("stop_reason").getAsString() : null;
            if ("max_tokens".equals(stopReason)) {
                logger.warn("Response hit max_tokens ({}); output may be truncated", TOOL_MAX_TOKENS);
            } else if ("refusal".equals(stopReason)) {
                logger.warn("Model refused the request");
            }

            for (JsonElement el : content) {
                JsonObject block = el.getAsJsonObject();
                String type = block.get("type").getAsString();

                if ("text".equals(type)) {
                    String text = block.has("text") ? block.get("text").getAsString() : "";
                    results.add(new APIResponse("text", text, null, null, null));
                } else if ("tool_use".equals(type)) {
                    String id = block.has("id") ? block.get("id").getAsString() : null;
                    String name = block.has("name") ? block.get("name").getAsString() : null;
                    String input = block.has("input") ? GSON.toJson(block.get("input")) : "{}";
                    results.add(new APIResponse("tool_use", null, name, input, id));
                }
            }
        } catch (Exception e) {
            logger.error("Failed to parse tool use response: {}", e.getMessage());
        }
        return results;
    }

    /**
     * Parse token usage from the API response.
     * Handles both top-level and nested usage blocks (e.g. cache-aware responses).
     */
    private TokenUsage parseTokenUsage(String response) {
        try {
            JsonObject root = JsonParser.parseString(response).getAsJsonObject();
            JsonObject usage = root.getAsJsonObject("usage");
            if (usage == null) {
                logger.warn("No \"usage\" key found in API response");
                return TokenUsage.ZERO;
            }

            int inputTokens = usage.has("input_tokens") ? usage.get("input_tokens").getAsInt() : 0;
            int outputTokens = usage.has("output_tokens") ? usage.get("output_tokens").getAsInt() : 0;
            logger.debug("Parsed usage: {} in / {} out", inputTokens, outputTokens);
            return new TokenUsage(inputTokens, outputTokens);
        } catch (Exception e) {
            logger.warn("Failed to parse token usage: {}", e.getMessage());
            return TokenUsage.ZERO;
        }
    }

    /**
     * Build a JSON content block for an image (base64-encoded).
     * For future VLM integration with the Anthropic vision API.
     */
    public static String buildImageContentBlock(String mediaType, String base64Data) {
        JsonObject block = new JsonObject();
        block.addProperty("type", "image");
        JsonObject source = new JsonObject();
        source.addProperty("type", "base64");
        source.addProperty("media_type", mediaType);
        source.addProperty("data", base64Data);
        block.add("source", source);
        return GSON.toJson(block);
    }
}
