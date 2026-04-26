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

            // render_slide / render_slides emit a PNG -- inline the bytes
            // as an MCP image content block so clients sandboxed away from
            // the server's filesystem (Claude Desktop via mcp-remote is the
            // canonical case) can actually see the render without having to
            // read a path that doesn't exist on their side. The contact
            // sheet from render_slides is what an agent actually wants for
            // a deck overview, so it has to round-trip the bytes too.
            if ("render_slide".equals(toolName) || "render_slides".equals(toolName)) {
                byte[] bytes = toolDispatcher.getLastRenderBytes();
                if (bytes != null && bytes.length > 0) {
                    return buildToolResultWithImage(id, result, bytes, "image/png");
                }
            }

            return buildToolResult(id, result, false);

        } catch (Exception e) {
            logger.error("Tool call error: {}", e.getMessage());
            return buildToolResult(id, "Error: " + e.getMessage(), true);
        }
    }

    /**
     * Build a tool-call response whose {@code content} array contains
     * both a text block (diagnostics from the tool) and an image block
     * (base64-encoded bytes of the rendered PNG). MCP spec: image
     * blocks have {@code type=image, data=&lt;base64&gt;, mimeType}.
     * Package-private for direct testing.
     */
    JsonObject buildToolResultWithImage(JsonElement id, String text,
                                        byte[] imageBytes, String mimeType) {
        JsonObject result = new JsonObject();
        JsonArray content = new JsonArray();

        JsonObject textBlock = new JsonObject();
        textBlock.addProperty("type", "text");
        textBlock.addProperty("text", text != null ? text : "");
        content.add(textBlock);

        JsonObject imageBlock = new JsonObject();
        imageBlock.addProperty("type", "image");
        imageBlock.addProperty("data", java.util.Base64.getEncoder().encodeToString(imageBytes));
        imageBlock.addProperty("mimeType", mimeType);
        content.add(imageBlock);

        result.add("content", content);
        return JsonRpcFrames.result(id, result);
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
