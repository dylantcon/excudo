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
 * Ollama API client using the native /api/chat endpoint.
 * Connects to a local or remote Ollama instance (default: http://localhost:11434).
 *
 * Configuration:
 *   OLLAMA_BASE_URL  - base URL (default: http://localhost:11434)
 *   OLLAMA_MODEL     - model name (default: llama3)
 *   OLLAMA_API_KEY   - API key (Ollama ignores this, but LLMClient requires non-empty;
 *                      set to any value like "ollama" or "local")
 */
public class OllamaClient extends LLMClient {

    private static final ComponentLogger logger = Logger.getLogger(OllamaClient.class);
    private static final Gson GSON = new Gson();

    private static final String DEFAULT_BASE_URL = "http://localhost:11434";
    private static final String DEFAULT_MODEL = "llama3";
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 300_000; // 5 min for slow local models

    private final String baseUrl;

    public OllamaClient(String apiKey, String model, String baseUrl) {
        super(apiKey, model);
        this.baseUrl = baseUrl != null ? baseUrl.replaceAll("/+$", "") : DEFAULT_BASE_URL;
    }

    public OllamaClient(String apiKey, String model) {
        this(apiKey, model, EnvLoader.get("OLLAMA_BASE_URL"));
    }

    /**
     * Create a client from environment variables.
     */
    public static OllamaClient fromEnvironment() {
        String apiKey = EnvLoader.get("OLLAMA_API_KEY");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            apiKey = "ollama"; // Ollama doesn't require a real key
        }
        String model = EnvLoader.get("OLLAMA_MODEL");
        if (model == null || model.trim().isEmpty()) {
            model = DEFAULT_MODEL;
        }
        String baseUrl = EnvLoader.get("OLLAMA_BASE_URL");
        return new OllamaClient(apiKey, model, baseUrl);
    }

    @Override
    public String getProviderName() {
        return "Ollama (" + model + ")";
    }

    @Override
    public boolean isLocalModel() {
        return true;
    }

    @Override
    public ExecutionResult<String> sendMessage(String systemPrompt, String userMessage) {
        try {
            String endpoint = baseUrl + "/api/chat";
            String requestBody = buildChatRequest(systemPrompt, userMessage);

            HttpURLConnection conn = openConnection(endpoint);
            writeBody(conn, requestBody);

            int code = conn.getResponseCode();
            if (code == HttpURLConnection.HTTP_OK) {
                String response = readResponse(conn.getInputStream());
                String content = extractContent(response);
                return ExecutionResult.success("Ollama API Call", content);
            } else {
                String error = readResponse(conn.getErrorStream());
                return ExecutionResult.failure("Ollama API Call",
                    "Ollama returned HTTP " + code + ": " + error);
            }
        } catch (ConnectException e) {
            return ExecutionResult.failure("Ollama API Call",
                "Cannot connect to Ollama at " + baseUrl
                + ". Is Ollama running? (ollama serve)");
        } catch (Exception e) {
            return ExecutionResult.failure("Ollama API Call",
                "Ollama request failed: " + e.getMessage());
        }
    }

    /**
     * Tool-use support via Ollama's native /api/chat endpoint.
     * Models that support function calling (e.g. llama3.1, deepseek-r1)
     * will return tool_call entries; others fall back to plain text.
     */
    @Override
    public List<LLMClient.APIResponse> sendMessageWithTools(
            String systemPrompt, List<Map<String, String>> messages, String toolsJson) {
        try {
            String endpoint = baseUrl + "/api/chat";
            String requestBody = buildChatRequestWithTools(systemPrompt, messages, toolsJson);

            HttpURLConnection conn = openConnection(endpoint);
            writeBody(conn, requestBody);

            int code = conn.getResponseCode();
            if (code == HttpURLConnection.HTTP_OK) {
                String response = readResponse(conn.getInputStream());
                return parseResponse(response);
            } else {
                String error = readResponse(conn.getErrorStream());
                List<LLMClient.APIResponse> errors = new ArrayList<>();
                errors.add(new LLMClient.APIResponse(
                    "text", "Ollama error " + code + ": " + error, null, null, null));
                return errors;
            }
        } catch (ConnectException e) {
            List<LLMClient.APIResponse> errors = new ArrayList<>();
            errors.add(new LLMClient.APIResponse(
                "text", "Cannot connect to Ollama at " + baseUrl, null, null, null));
            return errors;
        } catch (Exception e) {
            List<LLMClient.APIResponse> errors = new ArrayList<>();
            errors.add(new LLMClient.APIResponse(
                "text", "Ollama error: " + e.getMessage(), null, null, null));
            return errors;
        }
    }

    // ------------------------------------------------------------------
    // Request building -- Ollama native /api/chat format
    // ------------------------------------------------------------------

    private String buildChatRequest(String systemPrompt, String userMessage) {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("stream", false);

        JsonArray messages = new JsonArray();
        JsonObject sysMsg = new JsonObject();
        sysMsg.addProperty("role", "system");
        sysMsg.addProperty("content", systemPrompt);
        messages.add(sysMsg);

        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", userMessage);
        messages.add(userMsg);

        body.add("messages", messages);
        return GSON.toJson(body);
    }

    private String buildChatRequestWithTools(String systemPrompt,
                                              List<Map<String, String>> messages,
                                              String toolsJson) {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("stream", false);

        // Messages array -- convert Anthropic message format to Ollama format.
        JsonArray msgArray = new JsonArray();

        JsonObject sysMsg = new JsonObject();
        sysMsg.addProperty("role", "system");
        sysMsg.addProperty("content", systemPrompt);
        msgArray.add(sysMsg);

        for (Map<String, String> msg : messages) {
            String role = msg.get("role");
            String content = msg.get("content");

            if (content != null && content.trim().startsWith("[")) {
                String trimmed = content.trim();

                if ("assistant".equals(role) && trimmed.contains("\"tool_use\"")) {
                    // Assistant message with tool_use blocks -> Ollama tool_calls format
                    appendAssistantToolCallMessage(msgArray, trimmed);
                } else if ("user".equals(role) && trimmed.contains("\"tool_result\"")) {
                    // Tool result message -> Ollama "tool" role messages
                    appendToolResultMessages(msgArray, trimmed);
                } else {
                    // Unknown array content -- extract text and send as string
                    JsonObject msgObj = new JsonObject();
                    msgObj.addProperty("role", role);
                    msgObj.addProperty("content", extractTextFromBlocks(trimmed));
                    msgArray.add(msgObj);
                }
            } else {
                JsonObject msgObj = new JsonObject();
                msgObj.addProperty("role", role);
                msgObj.addProperty("content", content != null ? content : "");
                msgArray.add(msgObj);
            }
        }
        body.add("messages", msgArray);

        body.add("tools", convertToolsToOllamaFormat(toolsJson));
        return GSON.toJson(body);
    }

    /**
     * Convert Anthropic assistant tool_use blocks to Ollama's tool_calls format.
     */
    private void appendAssistantToolCallMessage(JsonArray msgArray, String blocksJson) {
        JsonArray blocks = JsonParser.parseString(blocksJson).getAsJsonArray();
        StringBuilder textContent = new StringBuilder();
        JsonArray toolCalls = new JsonArray();

        for (JsonElement el : blocks) {
            JsonObject block = el.getAsJsonObject();
            String type = block.get("type").getAsString();

            if ("text".equals(type) && block.has("text")) {
                textContent.append(block.get("text").getAsString());
            } else if ("tool_use".equals(type)) {
                JsonObject toolCall = new JsonObject();
                JsonObject function = new JsonObject();
                function.addProperty("name", block.has("name") ? block.get("name").getAsString() : "");
                function.add("arguments", block.has("input") ? block.get("input") : new JsonObject());
                toolCall.add("function", function);
                toolCalls.add(toolCall);
            }
        }

        JsonObject msgObj = new JsonObject();
        msgObj.addProperty("role", "assistant");
        msgObj.addProperty("content", textContent.toString());
        if (toolCalls.size() > 0) {
            msgObj.add("tool_calls", toolCalls);
        }
        msgArray.add(msgObj);
    }

    /**
     * Convert Anthropic tool_result blocks to Ollama "tool" role messages.
     */
    private void appendToolResultMessages(JsonArray msgArray, String blocksJson) {
        JsonArray blocks = JsonParser.parseString(blocksJson).getAsJsonArray();
        for (JsonElement el : blocks) {
            JsonObject block = el.getAsJsonObject();
            if (block.has("type") && "tool_result".equals(block.get("type").getAsString())) {
                JsonObject msgObj = new JsonObject();
                msgObj.addProperty("role", "tool");
                String resultContent = block.has("content") ? block.get("content").getAsString() : "";
                msgObj.addProperty("content", resultContent);
                msgArray.add(msgObj);
            }
        }
    }

    /**
     * Extract plain text from a JSON content blocks array.
     */
    private String extractTextFromBlocks(String blocksJson) {
        try {
            JsonArray blocks = JsonParser.parseString(blocksJson).getAsJsonArray();
            StringBuilder text = new StringBuilder();
            for (JsonElement el : blocks) {
                JsonObject block = el.getAsJsonObject();
                if (block.has("type") && "text".equals(block.get("type").getAsString())
                        && block.has("text")) {
                    text.append(block.get("text").getAsString());
                }
            }
            return text.toString();
        } catch (Exception e) {
            return blocksJson;
        }
    }

    /**
     * Convert Anthropic-style tool definitions to Ollama/OpenAI function calling format.
     * Anthropic: [{"name":"x","description":"y","input_schema":{...}}]
     * Ollama:    [{"type":"function","function":{"name":"x","description":"y","parameters":{...}}}]
     */
    private JsonArray convertToolsToOllamaFormat(String anthropicToolsJson) {
        if (anthropicToolsJson == null || anthropicToolsJson.trim().isEmpty()) {
            return new JsonArray();
        }

        JsonArray anthropicTools = JsonParser.parseString(anthropicToolsJson).getAsJsonArray();
        JsonArray ollamaTools = new JsonArray();

        for (JsonElement el : anthropicTools) {
            JsonObject tool = el.getAsJsonObject();
            JsonObject ollamaTool = new JsonObject();
            ollamaTool.addProperty("type", "function");

            JsonObject function = new JsonObject();
            if (tool.has("name")) function.addProperty("name", tool.get("name").getAsString());
            if (tool.has("description")) function.addProperty("description", tool.get("description").getAsString());
            if (tool.has("input_schema")) {
                function.add("parameters", tool.get("input_schema"));
            } else {
                function.add("parameters", new JsonObject());
            }
            ollamaTool.add("function", function);
            ollamaTools.add(ollamaTool);
        }

        return ollamaTools;
    }

    // ------------------------------------------------------------------
    // Response parsing -- Ollama native format
    // ------------------------------------------------------------------

    /**
     * Extract assistant content from Ollama /api/chat response.
     * Format: {"message":{"role":"assistant","content":"..."}}
     */
    private String extractContent(String response) {
        try {
            JsonObject root = JsonParser.parseString(response).getAsJsonObject();
            JsonObject message = root.getAsJsonObject("message");
            if (message == null || !message.has("content")) return null;
            String content = message.get("content").getAsString();
            return content.isEmpty() ? null : content;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Parse Ollama response into APIResponse list.
     * Handles structured tool_calls, plain text, and text-embedded tool calls.
     */
    private List<LLMClient.APIResponse> parseResponse(String response) {
        List<LLMClient.APIResponse> results = new ArrayList<>();

        try {
            JsonObject root = JsonParser.parseString(response).getAsJsonObject();
            JsonObject message = root.getAsJsonObject("message");

            if (message != null && message.has("tool_calls")) {
                JsonArray toolCalls = message.getAsJsonArray("tool_calls");
                for (JsonElement el : toolCalls) {
                    JsonObject tc = el.getAsJsonObject();
                    JsonObject function = tc.getAsJsonObject("function");
                    if (function != null) {
                        String name = function.has("name") ? function.get("name").getAsString() : "";
                        String arguments = function.has("arguments")
                            ? GSON.toJson(function.get("arguments")) : "{}";
                        String id = "ollama_" + name + "_" + System.nanoTime();
                        results.add(new LLMClient.APIResponse(
                            "tool_use", null, name, arguments, id));
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Failed to parse structured tool_calls: {}", e.getMessage());
        }

        // Extract text content
        String content = extractContent(response);

        // If the model emitted tool calls as text (common with smaller models),
        // try to extract them from the text content.
        if (results.isEmpty() && content != null) {
            extractToolCallsFromText(content, results);
        }

        // Add remaining text content (strip any tool-call JSON that was extracted)
        if (content != null) {
            String cleanText = cleanToolCallText(content);
            if (!cleanText.isEmpty()) {
                results.add(new LLMClient.APIResponse("text", cleanText, null, null, null));
            }
        }

        // Fallback: return empty text response rather than raw API JSON
        if (results.isEmpty()) {
            results.add(new LLMClient.APIResponse("text", "", null, null, null));
        }

        return results;
    }

    /**
     * Extract tool calls that the model emitted as text content rather than
     * structured API tool_calls. Handles patterns like:
     *   {"name": "tool_name", "arguments": {...}}
     * This intentionally uses hand-rolled parsing because the input is malformed
     * model output where Gson can't help.
     */
    private void extractToolCallsFromText(String text, List<LLMClient.APIResponse> results) {
        int pos = 0;
        while (pos < text.length()) {
            int nameIdx = text.indexOf("\"name\":", pos);
            if (nameIdx == -1) break;

            int braceStart = text.lastIndexOf("{", nameIdx);
            if (braceStart == -1) { pos = nameIdx + 7; continue; }

            int braceEnd = findMatchingBrace(text, braceStart);
            if (braceEnd == -1) { pos = nameIdx + 7; continue; }

            String block = text.substring(braceStart, braceEnd + 1);

            if (!block.contains("\"arguments\"")) {
                pos = nameIdx + 7;
                continue;
            }

            try {
                JsonObject parsed = JsonParser.parseString(block).getAsJsonObject();
                String name = parsed.has("name") ? parsed.get("name").getAsString() : "";
                String arguments = parsed.has("arguments")
                    ? GSON.toJson(parsed.get("arguments")) : "{}";
                String id = "ollama_text_" + name + "_" + System.nanoTime();
                results.add(new LLMClient.APIResponse("tool_use", null, name, arguments, id));
            } catch (JsonSyntaxException e) {
                // Malformed -- skip
            }
            pos = braceEnd + 1;
        }
    }

    /**
     * Remove tool-call JSON blocks and surrounding garbage from text content.
     */
    private String cleanToolCallText(String text) {
        String cleaned = text.replaceAll("\\{\\s*\"name\"\\s*:.*?\"arguments\"\\s*:.*?\\}", "");
        cleaned = cleaned.replaceAll("</?tool_call>", "");
        cleaned = cleaned.replaceAll("\\\\u003c/?tool_call\\\\u003e", "");
        cleaned = cleaned.replaceAll("[\\p{Co}\\p{Cn}]", "");
        return cleaned.trim();
    }

    // ------------------------------------------------------------------
    // HTTP helpers
    // ------------------------------------------------------------------

    private HttpURLConnection openConnection(String endpoint) throws IOException {
        URL url = java.net.URI.create(endpoint).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setDoOutput(true);
        return conn;
    }

    private void writeBody(HttpURLConnection conn, String body) throws IOException {
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Find matching closing brace for text-embedded tool call extraction.
     * Kept as hand-rolled because it operates on malformed model output.
     */
    private int findMatchingBrace(String json, int start) {
        int depth = 0;
        boolean inString = false;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                if (c == '"' && json.charAt(i - 1) != '\\') inString = false;
            } else {
                if (c == '"') inString = true;
                else if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) return i;
                }
            }
        }
        return -1;
    }
}
