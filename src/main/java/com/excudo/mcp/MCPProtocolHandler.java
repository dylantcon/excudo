package com.excudo.mcp;

import com.excudo.core.llm.LLMToolDefinitions;
import com.excudo.core.llm.LLMToolDefinitions.ToolDefinition;
import com.excudo.core.llm.ToolDispatcher;
import com.excudo.core.utils.ComponentLogger;
import com.excudo.core.utils.Logger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.List;

/**
 * Transport-agnostic MCP JSON-RPC 2.0 dispatcher.
 *
 * Given an inbound request, returns the outbound response as a JsonObject
 * (or null for notifications). Does no I/O, no parsing, no serialization
 * of outbound frames -- that's the transport's job. Tool calls are routed
 * through {@link ToolDispatcher} exactly as the old in-engine dispatcher did,
 * so behaviour is byte-identical to the pre-refactor stdio server.
 */
public class MCPProtocolHandler {

    private static final ComponentLogger logger = Logger.getLogger("MCP");
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final String PROTOCOL_VERSION = "2024-11-05";
    private static final String SERVER_NAME = "excudo";
    private static final String SERVER_VERSION = "1.0.0";

    private final ToolDispatcher toolDispatcher;

    public MCPProtocolHandler(ToolDispatcher toolDispatcher) {
        this.toolDispatcher = toolDispatcher;
    }

    /**
     * Dispatch a single inbound request. Returns the outbound response,
     * or {@code null} for notifications (e.g., notifications/initialized).
     */
    public JsonObject handleRequest(JsonObject request) {
        String method = request.has("method") ? request.get("method").getAsString() : "";
        JsonElement id = request.get("id"); // null for notifications

        switch (method) {
            case "initialize":
                return buildInitializeResponse(id);

            case "notifications/initialized":
                return null;

            case "tools/list":
                return buildToolsListResponse(id);

            case "tools/call":
                return handleToolsCall(request, id);

            case "ping":
                return JsonRpcFrames.result(id, new JsonObject());

            default:
                return JsonRpcFrames.error(id, JsonRpcFrames.METHOD_NOT_FOUND,
                    "Method not found: " + method);
        }
    }

    private JsonObject buildInitializeResponse(JsonElement id) {
        JsonObject result = new JsonObject();
        result.addProperty("protocolVersion", PROTOCOL_VERSION);

        JsonObject capabilities = new JsonObject();
        capabilities.add("tools", new JsonObject());
        result.add("capabilities", capabilities);

        JsonObject serverInfo = new JsonObject();
        serverInfo.addProperty("name", SERVER_NAME);
        serverInfo.addProperty("version", SERVER_VERSION);
        result.add("serverInfo", serverInfo);

        return JsonRpcFrames.result(id, result);
    }

    private JsonObject buildToolsListResponse(JsonElement id) {
        JsonObject result = new JsonObject();
        JsonArray toolsArray = new JsonArray();

        addTools(toolsArray, LLMToolDefinitions.getCoreTools());
        addTools(toolsArray, LLMToolDefinitions.getDeferredTools());

        result.add("tools", toolsArray);
        return JsonRpcFrames.result(id, result);
    }

    private void addTools(JsonArray array, List<ToolDefinition> tools) {
        for (ToolDefinition td : tools) {
            // fetch_tool_schemas is a lazy-load helper for LLM API flows;
            // MCP declares every schema upfront so it's redundant here.
            if ("fetch_tool_schemas".equals(td.name())) continue;

            JsonObject tool = new JsonObject();
            tool.addProperty("name", td.name());
            tool.addProperty("description", td.description());

            try {
                tool.add("inputSchema", JsonParser.parseString(td.inputSchema()));
            } catch (Exception e) {
                JsonObject emptySchema = new JsonObject();
                emptySchema.addProperty("type", "object");
                tool.add("inputSchema", emptySchema);
            }

            array.add(tool);
        }
    }

    private JsonObject handleToolsCall(JsonObject request, JsonElement id) {
        try {
            JsonObject params = request.getAsJsonObject("params");
            if (params == null) {
                return JsonRpcFrames.error(id, JsonRpcFrames.INVALID_PARAMS, "Missing params");
            }

            String toolName = params.has("name") ? params.get("name").getAsString() : null;
            if (toolName == null || toolName.isEmpty()) {
                return JsonRpcFrames.error(id, JsonRpcFrames.INVALID_PARAMS, "Missing tool name");
            }

            String argsJson = "{}";
            if (params.has("arguments") && !params.get("arguments").isJsonNull()) {
                argsJson = GSON.toJson(params.get("arguments"));
            }

            logger.info("Tool call: {} | args: {}", toolName,
                argsJson.length() > 200 ? argsJson.substring(0, 200) + "..." : argsJson);

            String result = toolDispatcher.dispatch(toolName, argsJson);

            logger.info("Tool result: {}", result != null && result.length() > 200
                ? result.substring(0, 200) + "..." : result);

            return buildToolResult(id, result, false);

        } catch (Exception e) {
            logger.error("Tool call error: {}", e.getMessage());
            return buildToolResult(id, "Error: " + e.getMessage(), true);
        }
    }

    private JsonObject buildToolResult(JsonElement id, String text, boolean isError) {
        JsonObject result = new JsonObject();

        JsonArray content = new JsonArray();
        JsonObject textBlock = new JsonObject();
        textBlock.addProperty("type", "text");
        textBlock.addProperty("text", text != null ? text : "");
        content.add(textBlock);

        result.add("content", content);
        if (isError) {
            result.addProperty("isError", true);
        }

        return JsonRpcFrames.result(id, result);
    }
}
