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
 * OpenRouter API client using the OpenAI-compatible /chat/completions endpoint.
 * Provides access to many models (Gemini Flash, DeepSeek, Llama, Qwen, etc.)
 * through a single API key.
 *
 * Configuration:
 *   OPENROUTER_API_KEY  - API key from openrouter.ai
 *   OPENROUTER_MODEL    - model ID (default: google/gemini-2.5-flash)
 *   OPENROUTER_BASE_URL - base URL (default: https://openrouter.ai/api/v1)
 */
public class OpenRouterClient extends LLMClient {

    private static final ComponentLogger logger = Logger.getLogger(OpenRouterClient.class);
    private static final Gson GSON = new Gson();

    private static final String DEFAULT_BASE_URL = "https://openrouter.ai/api/v1";
    private static final String DEFAULT_MODEL = "google/gemini-2.5-flash";
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 300_000;

    private final String baseUrl;

    public OpenRouterClient(String apiKey, String model, String baseUrl) {
        super(apiKey, model);
        this.baseUrl = baseUrl != null ? baseUrl.replaceAll("/+$", "") : DEFAULT_BASE_URL;
    }

    public OpenRouterClient(String apiKey, String model) {
        this(apiKey, model, null);
    }

    public static OpenRouterClient fromEnvironment() {
        String apiKey = EnvLoader.get("OPENROUTER_API_KEY");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("OPENROUTER_API_KEY not set");
        }
        String model = EnvLoader.get("OPENROUTER_MODEL");
        if (model == null || model.trim().isEmpty()) {
            model = DEFAULT_MODEL;
        }
        String baseUrl = EnvLoader.get("OPENROUTER_BASE_URL");
        return new OpenRouterClient(apiKey, model, baseUrl);
    }

    @Override
    public String getProviderName() {
        return "OpenRouter (" + model + ")";
    }

    @Override
    public ExecutionResult<String> sendMessage(String systemPrompt, String userMessage) {
        try {
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "user", "content", userMessage));
            List<APIResponse> responses = sendMessageWithTools(systemPrompt, messages, null);

            StringBuilder text = new StringBuilder();
            for (APIResponse r : responses) {
                if (r.isText() && r.content() != null) {
                    text.append(r.content());
                }
            }
            return ExecutionResult.success("OpenRouter API Call", text.toString());
        } catch (Exception e) {
            return ExecutionResult.failure("OpenRouter API Call",
                "OpenRouter request failed: " + e.getMessage());
        }
    }

    @Override
    public List<APIResponse> sendMessageWithTools(
            String systemPrompt, List<Map<String, String>> messages, String toolsJson) {
        try {
            String endpoint = baseUrl + "/chat/completions";
            String requestBody = buildRequest(systemPrompt, messages, toolsJson);

            logger.info("OpenRouter request body ({} chars): {}", requestBody.length(),
                requestBody.length() > 3000 ? requestBody.substring(0, 3000) + "..." : requestBody);

            HttpURLConnection conn = openConnection(endpoint);
            writeBody(conn, requestBody);

            int code = conn.getResponseCode();
            if (code == HttpURLConnection.HTTP_OK) {
                String response = readResponseStream(conn.getInputStream());
                logger.info("OpenRouter raw response: {}", response.length() > 2000
                    ? response.substring(0, 2000) + "..." : response);
                return parseResponse(response);
            } else {
                String error = readResponseStream(conn.getErrorStream());
                logger.error("OpenRouter returned HTTP {}: {}", code, error);
                return List.of(new APIResponse(
                    "text", "OpenRouter error " + code + ": " + error, null, null, null));
            }
        } catch (Exception e) {
            logger.error("OpenRouter request failed: {}", e.getMessage());
            return List.of(new APIResponse(
                "text", "OpenRouter error: " + e.getMessage(), null, null, null));
        }
    }

    // ------------------------------------------------------------------
    // Request building -- OpenAI-compatible format
    // ------------------------------------------------------------------

    private String buildRequest(String systemPrompt, List<Map<String, String>> messages, String toolsJson) {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("max_tokens", 16384);

        JsonArray msgArray = new JsonArray();

        // System message
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            JsonObject sysMsg = new JsonObject();
            sysMsg.addProperty("role", "system");
            sysMsg.addProperty("content", systemPrompt);
            msgArray.add(sysMsg);
        }

        // Conversation messages -- convert Anthropic format to OpenAI format
        for (Map<String, String> msg : messages) {
            String role = msg.get("role");
            String content = msg.get("content");

            if (content != null && content.startsWith("[")) {
                // Complex content (tool_use / tool_result arrays) -- convert
                convertComplexMessages(msgArray, role, content);
            } else {
                JsonObject m = new JsonObject();
                m.addProperty("role", role);
                m.addProperty("content", content != null ? content : "");
                msgArray.add(m);
            }
        }

        body.add("messages", msgArray);

        // Tools -- convert from Anthropic format to OpenAI format
        if (toolsJson != null && !toolsJson.isEmpty()) {
            JsonArray anthropicTools = JsonParser.parseString(toolsJson).getAsJsonArray();
            JsonArray openaiTools = new JsonArray();
            for (JsonElement tool : anthropicTools) {
                JsonObject t = tool.getAsJsonObject();
                JsonObject openaiTool = new JsonObject();
                openaiTool.addProperty("type", "function");
                JsonObject fn = new JsonObject();
                fn.addProperty("name", t.get("name").getAsString());
                fn.addProperty("description", t.get("description").getAsString());
                if (t.has("input_schema")) {
                    fn.add("parameters", t.get("input_schema"));
                }
                openaiTool.add("function", fn);
                openaiTools.add(openaiTool);
            }
            body.add("tools", openaiTools);
        }

        return GSON.toJson(body);
    }

    /**
     * Convert Anthropic-format complex content arrays to OpenAI message format.
     * Handles tool_use blocks (assistant) and tool_result blocks (user->tool role).
     */
    private void convertComplexMessages(JsonArray msgArray, String role, String contentJson) {
        try {
            JsonArray blocks = JsonParser.parseString(contentJson).getAsJsonArray();

            if ("assistant".equals(role)) {
                // Extract text and tool_calls from assistant content
                StringBuilder textParts = new StringBuilder();
                JsonArray toolCalls = new JsonArray();
                int callIndex = 0;

                for (JsonElement block : blocks) {
                    JsonObject b = block.getAsJsonObject();
                    String type = b.has("type") ? b.get("type").getAsString() : "";

                    if ("text".equals(type)) {
                        textParts.append(b.get("text").getAsString());
                    } else if ("tool_use".equals(type)) {
                        JsonObject call = new JsonObject();
                        call.addProperty("id", b.get("id").getAsString());
                        call.addProperty("type", "function");
                        JsonObject fn = new JsonObject();
                        fn.addProperty("name", b.get("name").getAsString());
                        fn.addProperty("arguments", b.get("input").toString());
                        call.add("function", fn);
                        toolCalls.add(call);
                        callIndex++;
                    }
                }

                JsonObject assistantMsg = new JsonObject();
                assistantMsg.addProperty("role", "assistant");
                if (textParts.length() > 0) {
                    assistantMsg.addProperty("content", textParts.toString());
                } else {
                    assistantMsg.add("content", JsonNull.INSTANCE);
                }
                if (toolCalls.size() > 0) {
                    assistantMsg.add("tool_calls", toolCalls);
                }
                msgArray.add(assistantMsg);

            } else if ("user".equals(role)) {
                // tool_result blocks become separate "tool" role messages
                for (JsonElement block : blocks) {
                    JsonObject b = block.getAsJsonObject();
                    String type = b.has("type") ? b.get("type").getAsString() : "";

                    if ("tool_result".equals(type)) {
                        JsonObject toolMsg = new JsonObject();
                        toolMsg.addProperty("role", "tool");
                        toolMsg.addProperty("tool_call_id", b.get("tool_use_id").getAsString());
                        toolMsg.addProperty("content",
                            b.has("content") ? b.get("content").getAsString() : "");
                        msgArray.add(toolMsg);
                    } else if ("text".equals(type)) {
                        JsonObject userMsg = new JsonObject();
                        userMsg.addProperty("role", "user");
                        userMsg.addProperty("content", b.get("text").getAsString());
                        msgArray.add(userMsg);
                    }
                }
            }
        } catch (Exception e) {
            // Fallback: pass as plain text
            JsonObject m = new JsonObject();
            m.addProperty("role", role);
            m.addProperty("content", contentJson);
            msgArray.add(m);
        }
    }

    // ------------------------------------------------------------------
    // Response parsing -- OpenAI format
    // ------------------------------------------------------------------

    private List<APIResponse> parseResponse(String response) {
        List<APIResponse> results = new ArrayList<>();
        try {
            JsonObject root = JsonParser.parseString(response).getAsJsonObject();

            // Parse usage
            if (root.has("usage")) {
                JsonObject usage = root.getAsJsonObject("usage");
                int inputTokens = usage.has("prompt_tokens") ? usage.get("prompt_tokens").getAsInt() : 0;
                int outputTokens = usage.has("completion_tokens") ? usage.get("completion_tokens").getAsInt() : 0;
                double cost = usage.has("cost") ? usage.get("cost").getAsDouble() : 0.0;
                this.lastTokenUsage = new TokenUsage(inputTokens, outputTokens, cost);
            }

            // Parse choices
            JsonArray choices = root.getAsJsonArray("choices");
            if (choices == null || choices.size() == 0) {
                results.add(new APIResponse("text", "No response from model", null, null, null));
                return results;
            }

            JsonObject choice = choices.get(0).getAsJsonObject();

            // Check finish_reason for model-side errors (e.g. Gemini MALFORMED_FUNCTION_CALL)
            if (choice.has("finish_reason")) {
                String finishReason = choice.get("finish_reason").getAsString();
                if ("error".equals(finishReason) || "MALFORMED_FUNCTION_CALL".equals(finishReason)) {
                    String nativeReason = root.has("native_finish_reason")
                        ? root.get("native_finish_reason").getAsString() : finishReason;
                    logger.warn("Model returned error finish_reason: {}", nativeReason);
                    results.add(new APIResponse("text",
                        "[MODEL_ERROR: " + nativeReason + "] Function call was malformed. "
                        + "Simplify parameters, escape special characters, and try smaller batches.",
                        null, null, null));
                    return results;
                }
            }

            JsonObject message = choice.getAsJsonObject("message");
            if (message == null) {
                results.add(new APIResponse("text", "Empty message in response", null, null, null));
                return results;
            }

            // Text content
            if (message.has("content") && !message.get("content").isJsonNull()) {
                String content = message.get("content").getAsString();
                if (!content.isEmpty()) {
                    results.add(new APIResponse("text", content, null, null, null));
                }
            }

            // Tool calls
            if (message.has("tool_calls")) {
                JsonArray toolCalls = message.getAsJsonArray("tool_calls");
                for (JsonElement tc : toolCalls) {
                    JsonObject call = tc.getAsJsonObject();
                    String id = call.has("id") ? call.get("id").getAsString() : "call_" + System.nanoTime();
                    JsonObject fn = call.getAsJsonObject("function");
                    String name = fn.get("name").getAsString();
                    String arguments = fn.has("arguments") ? fn.get("arguments").getAsString() : "{}";
                    results.add(new APIResponse("tool_use", null, name, arguments, id));
                }
            }

            if (results.isEmpty()) {
                results.add(new APIResponse("text", "", null, null, null));
            }

        } catch (Exception e) {
            logger.error("Failed to parse OpenRouter response: {}", e.getMessage());
            results.add(new APIResponse("text", "Parse error: " + e.getMessage(), null, null, null));
        }
        return results;
    }

    // ------------------------------------------------------------------
    // HTTP helpers
    // ------------------------------------------------------------------

    private HttpURLConnection openConnection(String endpoint) throws IOException {
        URL url = new URL(endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setRequestProperty("HTTP-Referer", "https://github.com/excudo");
        conn.setRequestProperty("X-Title", "Excudo PPTX Editor");
        conn.setDoOutput(true);
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        return conn;
    }

    private void writeBody(HttpURLConnection conn, String body) throws IOException {
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }
    }

    private String readResponseStream(InputStream stream) throws IOException {
        if (stream == null) return "";
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }
}
