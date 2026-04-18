package com.excudo.console;

import com.excudo.core.commands.CommandFactory;
import com.excudo.core.commands.CommandInvoker;
import com.excudo.core.llm.ToolDispatcher;
import com.excudo.core.orchestration.PPTXOrchestratorImpl;
import com.excudo.core.utils.ComponentLogger;
import com.excudo.core.utils.Logger;
import com.excudo.mcp.MCPProtocolHandler;
import com.excudo.mcp.MCPStdioTransport;
import com.excudo.mcp.MCPTransport;

import java.io.PrintStream;
import java.util.List;

/**
 * MCP (Model Context Protocol) server implementation.
 *
 * Extends AbstractConsoleEngine to share session management, command dispatch,
 * and orchestration with the TTY and UI console implementations. Protocol
 * dispatch and wire framing live in {@link MCPProtocolHandler} and
 * {@link MCPTransport} respectively -- this class just wires them together
 * with a stdio transport for the classic {@code pc.py run mcp} path.
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

    // CRITICAL: capture stdout before any other static initialiser can print
    // to it. Logger.getLogger() triggers LoggingManager which prints to
    // System.out, which would otherwise corrupt the JSON-RPC stream.
    static {
        jsonRpcOut = System.out;
        System.setOut(System.err);
    }

    private static final ComponentLogger logger = Logger.getLogger("MCP");

    public static void main(String[] args) {
        MCPConsoleEngine engine = new MCPConsoleEngine();
        try {
            PPTXOrchestratorImpl orchestrator = new PPTXOrchestratorImpl();
            engine.initialize(orchestrator);

            ToolDispatcher dispatcher = new ToolDispatcher(
                orchestrator, new CommandFactory(orchestrator), new CommandInvoker());
            dispatcher.setDisplayAdapter(engine);

            MCPProtocolHandler handler = new MCPProtocolHandler(dispatcher);
            MCPTransport transport = new MCPStdioTransport(System.in, jsonRpcOut);

            // Optional: load file from command line args
            if (args.length > 0 && !args[0].startsWith("-")) {
                engine.executeCommand("load " + args[0]);
            }

            logger.info("MCP server started");
            transport.serve(handler::handleRequest);

        } catch (Exception e) {
            System.err.println("MCP server fatal error: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        } finally {
            logger.info("MCP server shutting down");
            engine.shutdown();
        }
    }

    // ========== ABSTRACT METHOD OVERRIDES ==========

    @Override
    public void displayStyled(String message, ConsoleStyle style) {
        // MCP mode routes all console output to stderr as plain text so the
        // MCP client's JSON-RPC stream on stdout stays clean. Style is advisory
        // only: ERROR and SUCCESS keep their markers for log readability.
        switch (style) {
            case ERROR:
                System.err.println("[ERROR] " + message);
                break;
            case SUCCESS:
                System.err.println("[OK] " + message);
                break;
            default:
                System.err.println(message);
                break;
        }
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
