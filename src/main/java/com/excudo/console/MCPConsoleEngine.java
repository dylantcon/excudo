package com.excudo.console;

import com.excudo.core.commands.CommandFactory;
import com.excudo.core.commands.CommandInvoker;
import com.excudo.core.llm.LLMToolDefinitions;
import com.excudo.core.llm.LLMToolDefinitions.ToolDefinition;
import com.excudo.core.llm.ToolDispatcher;
import com.excudo.core.orchestration.PPTXOrchestratorImpl;
import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.utils.Logger;
import com.excudo.core.utils.ComponentLogger;
import com.excudo.exceptions.XMLParsingException;
import com.google.gson.*;

import java.io.*;
import java.util.List;

/**
 * MCP (Model Context Protocol) server implementation.
 * Extends AbstractConsoleEngine to share session management, command dispatch,
 * and orchestration with TTY and UI console implementations.
 *
 * Reads JSON-RPC 2.0 from stdin, dispatches tool calls via ToolDispatcher,
 * writes JSON-RPC responses to stdout. All diagnostic output goes to stderr.
 *
 * Usage:
 *   java -cp ... com.excudo.console.MCPConsoleEngine [file.pptx]
 *
 * MCP client config (e.g., Claude Desktop):
 *   { "command": "java", "args": ["-cp", "build/classes:lib/*", "com.excudo.console.MCPConsoleEngine"] }
 */
public class MCPConsoleEngine extends AbstractConsoleEngine {

    /** The real stdout, reserved exclusively for JSON-RPC responses. */
    private static final PrintStream jsonRpcOut;

    // CRITICAL: Capture stdout before any other static initializer can print to it.
    // Logger.getLogger() triggers LoggingManager which prints to System.out.
    static {
        jsonRpcOut = System.out;
        System.setOut(System.err);
    }

    private static final ComponentLogger logger = Logger.getLogger("MCP");
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final String PROTOCOL_VERSION = "2024-11-05";

    private ToolDispatcher toolDispatcher;

    // ========== ENTRY POINT ==========

    public static void main(String[] args) {

        try {
            MCPConsoleEngine engine = new MCPConsoleEngine();
            PPTXOrchestratorImpl orchestrator = new PPTXOrchestratorImpl();
            engine.initialize(orchestrator);

            // Create tool dispatcher
            engine.toolDispatcher = new ToolDispatcher(
                orchestrator, new CommandFactory(orchestrator), new CommandInvoker());
            engine.toolDispatcher.setDisplayAdapter(engine);

            // Optional: load file from command line args
            if (args.length > 0 && !args[0].startsWith("-")) {
                engine.executeCommand("load " + args[0]);
            }

            // Run the MCP server loop
            engine.runMCPLoop();

        } catch (Exception e) {
            System.err.println("MCP server fatal error: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    // ========== MCP LOOP ==========

    private void runMCPLoop() {
        logger.info("MCP server started, reading JSON-RPC from stdin");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                try {
                    JsonObject request = JsonParser.parseString(line).getAsJsonObject();
                    JsonObject response = handleRequest(request);
                    if (response != null) {
                        jsonRpcOut.println(GSON.toJson(response));
                        jsonRpcOut.flush();
                    }
                } catch (JsonSyntaxException e) {
                    sendError(null, -32700, "Parse error: " + e.getMessage());
                } catch (Exception e) {
                    logger.error("Error handling request: {}", e.getMessage());
                    sendError(null, -32603, "Internal error: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            logger.error("MCP stdin read error: {}", e.getMessage());
        } finally {
            logger.info("MCP server shutting down");
            shutdown();
        }
    }

    // ========== JSON-RPC DISPATCH ==========

    private JsonObject handleRequest(JsonObject request) {
        String method = request.has("method") ? request.get("method").getAsString() : "";
        JsonElement id = request.get("id"); // null for notifications

        switch (method) {
            case "initialize":
                return buildInitializeResponse(id);

            case "notifications/initialized":
                // Notification -- no response
                return null;

            case "tools/list":
                return buildToolsListResponse(id);

            case "tools/call":
                return handleToolsCall(request, id);

            case "ping":
                return buildResult(id, new JsonObject());

            default:
                return buildError(id, -32601, "Method not found: " + method);
        }
    }

    // ========== PROTOCOL RESPONSES ==========

    private JsonObject buildInitializeResponse(JsonElement id) {
        JsonObject result = new JsonObject();
        result.addProperty("protocolVersion", PROTOCOL_VERSION);

        JsonObject capabilities = new JsonObject();
        JsonObject tools = new JsonObject();
        capabilities.add("tools", tools);
        result.add("capabilities", capabilities);

        JsonObject serverInfo = new JsonObject();
        serverInfo.addProperty("name", "excudo");
        serverInfo.addProperty("version", "1.0.0");
        result.add("serverInfo", serverInfo);

        return buildResult(id, result);
    }

    private JsonObject buildToolsListResponse(JsonElement id) {
        JsonObject result = new JsonObject();
        JsonArray toolsArray = new JsonArray();

        // Include ALL tools (core + deferred) since MCP declares everything upfront
        addTools(toolsArray, LLMToolDefinitions.getCoreTools());
        addTools(toolsArray, LLMToolDefinitions.getDeferredTools());

        result.add("tools", toolsArray);
        return buildResult(id, result);
    }

    private void addTools(JsonArray array, List<ToolDefinition> tools) {
        for (ToolDefinition td : tools) {
            // Skip fetch_tool_schemas -- not meaningful in MCP (all schemas declared upfront)
            if ("fetch_tool_schemas".equals(td.name())) continue;

            JsonObject tool = new JsonObject();
            tool.addProperty("name", td.name());
            tool.addProperty("description", td.description());

            // Parse the input schema string into a JSON object
            try {
                tool.add("inputSchema", JsonParser.parseString(td.inputSchema()));
            } catch (Exception e) {
                // Fallback: empty schema
                JsonObject emptySchema = new JsonObject();
                emptySchema.addProperty("type", "object");
                tool.add("inputSchema", emptySchema);
            }

            array.add(tool);
        }
    }

    // ========== TOOL CALL HANDLING ==========

    private JsonObject handleToolsCall(JsonObject request, JsonElement id) {
        try {
            JsonObject params = request.getAsJsonObject("params");
            if (params == null) {
                return buildError(id, -32602, "Missing params");
            }

            String toolName = params.has("name") ? params.get("name").getAsString() : null;
            if (toolName == null || toolName.isEmpty()) {
                return buildError(id, -32602, "Missing tool name");
            }

            // Get arguments as JSON string for the dispatcher
            String argsJson = "{}";
            if (params.has("arguments") && !params.get("arguments").isJsonNull()) {
                argsJson = GSON.toJson(params.get("arguments"));
            }

            logger.info("Tool call: {} | args: {}", toolName,
                argsJson.length() > 200 ? argsJson.substring(0, 200) + "..." : argsJson);

            // Dispatch to shared tool handler
            String result = toolDispatcher.dispatch(toolName, argsJson);

            logger.info("Tool result: {}", result != null && result.length() > 200
                ? result.substring(0, 200) + "..." : result);

            // Build MCP tool result
            return buildToolResult(id, result, false);

        } catch (Exception e) {
            logger.error("Tool call error: {}", e.getMessage());
            return buildToolResult(id, "Error: " + e.getMessage(), true);
        }
    }

    // ========== JSON-RPC BUILDERS ==========

    private JsonObject buildResult(JsonElement id, JsonObject result) {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        if (id != null) response.add("id", id);
        response.add("result", result);
        return response;
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

        return buildResult(id, result);
    }

    private JsonObject buildError(JsonElement id, int code, String message) {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        if (id != null) response.add("id", id);

        JsonObject error = new JsonObject();
        error.addProperty("code", code);
        error.addProperty("message", message);
        response.add("error", error);

        return response;
    }

    private void sendError(JsonElement id, int code, String message) {
        JsonObject error = buildError(id, code, message);
        jsonRpcOut.println(GSON.toJson(error));
        jsonRpcOut.flush();
    }

    // ========== ABSTRACT METHOD OVERRIDES ==========

    @Override
    public void displayMessage(String message) {
        System.err.println(message);
    }

    @Override
    public void displayError(String message) {
        System.err.println("[ERROR] " + message);
    }

    @Override
    public void displaySuccess(String message) {
        System.err.println("[OK] " + message);
    }

    @Override
    protected void handleLLMCommand(String subCommand) {
        displayError("LLM commands not available in MCP mode. " +
            "The MCP client provides its own LLM reasoning.");
    }

    @Override
    public void enterArrangeMode() {
        displayError("Arrange mode not available in MCP mode. " +
            "The MCP client IS the agent -- use tools/call directly.");
    }

    @Override
    public List<String> getAvailableCommands() {
        // In MCP mode, commands are exposed as tools, not typed commands
        return List.of("Tools available via MCP tools/call protocol");
    }
}
