package com.excudo.console;

import com.excudo.core.orchestration.OrchestrationContext;
import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.orchestration.PresentationMetadata;
import com.excudo.core.orchestration.SessionManager;
import com.excudo.core.model.AnimationType;
import com.excudo.core.results.*;
import com.excudo.core.commands.CommandFactory;
import com.excudo.core.commands.CommandInvoker;
import com.excudo.core.commands.Command;
import com.excudo.core.commands.CommandExecutionException;
import com.excudo.core.commands.CommandDisplay;
import com.excudo.core.commands.CommandSessionManager;
import com.excudo.core.commands.CommandSessionContext;
import com.excudo.core.commands.IconContext;
import com.excudo.core.commands.LLMContext;
import com.excudo.core.commands.LLMHandler;
import com.excudo.core.smartcontent.IconRepository;
import com.excudo.core.commands.SessionResult;
import com.excudo.core.results.ExecutionResult;
import com.excudo.xml.writers.SlideCreator;
import com.excudo.xml.writers.SlideNotesWriter;
import com.excudo.core.smartcontent.SmartContentEnhancer;
import com.excudo.core.model.SlideShape;
import com.excudo.core.model.ParsedSlideData;
import com.excudo.core.model.ShapeGeometry;
import com.excudo.core.model.ShapeRegistry;
import com.excudo.core.model.TimingTree;
import com.excudo.core.model.AnimationBinding;
import com.excudo.exceptions.XMLParsingException;
import com.excudo.console.utils.ConsoleCommandValidator;
import com.excudo.core.utils.Logger;
import com.excudo.core.utils.ComponentLogger;
import com.excudo.console.utils.ConsoleArgumentParser;
import com.excudo.console.utils.ConsoleSessionManager;
import com.excudo.core.inspection.SlideInspector;
import com.excudo.console.utils.ConsoleOutputFormatter;
import com.excudo.core.inspection.PresentationInspector;
import com.excudo.console.utils.TransactionManager;
import com.excudo.core.inspection.NotesManager;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;
import java.io.File;
import java.util.Optional;
import java.util.Set;

/**
 * Abstract base class for ConsoleEngine implementations.
 * Provides common functionality shared between TTY and UI console engines.
 * Follows DRY principle by centralizing shared code.
 * 
 * CRITICAL INTEGRATION: Implements unified CommandDisplay interface to bridge
 * Command pattern with console display methods.
 */
public abstract class AbstractConsoleEngine implements ConsoleEngine,
        // Unified command display interface for all Command pattern integration
        CommandDisplay,
        // Session management interfaces
        CommandSessionManager,
        CommandSessionContext,
        // LLM functionality interface
        LLMContext,
        // Icon management interface
        IconContext {
    
    private static final ComponentLogger logger = Logger.console();

    // Common fields
    protected PPTXOrchestrator orchestrator;
    protected LLMConsoleHandler llmHandler;
    protected File currentFile;
    protected CommandFactory commandFactory;
    // Note: CommandInvoker is now session-scoped, accessed via getCurrentCommandInvoker()
    protected SlideCreator slideCreator;

    // Session management
    protected SessionManager sessionManager;
    protected ConsoleSessionManager consoleSessionManager;
    protected String currentSessionId;
    protected SmartContentEnhancer contentEnhancer;

    // Transaction management
    protected TransactionManager transactionManager;

    // Notes management
    protected NotesManager notesManager;

    // Arrange mode state
    protected boolean arrangeMode = false;

    // MCP server mode state. Null when not serving; holds the live
    // transport reference while a server is running so /exit can stop it
    // and isMcpMode() reports truthfully.
    protected com.excudo.mcp.MCPHttpSseTransport activeMcpTransport;
    
    /**
     * Get the CommandInvoker for the current session.
     * Returns null if no session is active.
     */
    public CommandInvoker getCurrentCommandInvoker() {
        if (currentSessionId == null) {
            return null;
        }
        return sessionManager.getSession(currentSessionId)
                .map(SessionManager.ManagedSession::getCommandInvoker)
                .orElse(null);
    }
    
    /**
     * Determine if a command requires an active presentation session.
     * Commands that can work without loaded presentations return false.
     */
    private boolean requiresSession(String commandName) {
        // Commands that work without a session (utility/help/session management)
        switch (commandName.toLowerCase()) {
            case "help":
            case "?":
            case "list-animation-types":
            case "session":
            case "load":
            case "open":
            case "undo":
            case "redo":
            case "list-themes":
            case "show-theme":
            case "create-theme":
            case "edit-theme":
            case "delete-theme":
            case "list-layouts":
            case "list-shape-types":
            case "new":
            case "llm":
            case "icon":
                return false;
            default:
                return true;
        }
    }
    
    // ------------------------------------------------------------------
    // Arrange mode (modal LLM interaction)
    // ------------------------------------------------------------------

    public boolean isArrangeMode() { return arrangeMode; }

    public void enterArrangeMode() {
        if (llmHandler == null || !llmHandler.isAgenticAvailable()) {
            displayError("Arrange mode requires a configured LLM provider. Use 'llm config set' to configure.");
            return;
        }
        if (llmHandler.isLocalModel()) {
            displayMessage("  [NOTE] Local model detected. Arrange mode uses multi-turn agentic editing");
            displayMessage("         which may produce unreliable results with small models.");
            displayMessage("         Consider using 'llm edit <request>' for single-step edits instead.");
        }
        this.arrangeMode = true;
        displaySuccess("Entered arrange mode. Type or paste, then press Enter on a blank line to send.");
        displayMessage("  /exit       - leave arrange mode");
        displayMessage("  /<command>  - run a console command directly");
    }

    public void exitArrangeMode() {
        this.arrangeMode = false;
        displaySuccess("Exited arrange mode.");
    }

    /**
     * Handle input while in arrange mode.
     * Raw text goes to the agentic LLM; /commands go to the normal pipeline.
     */
    protected void handleArrangeModeInput(String input) {
        if (llmHandler == null) {
            displayError("LLM handler not available.");
            return;
        }
        // Use session invoker if available, otherwise create a temporary one.
        // The agent may call 'new' during this turn, which creates the session.
        CommandInvoker invoker = getCurrentCommandInvoker();
        if (invoker == null) {
            invoker = new CommandInvoker();
        }
        try {
            com.excudo.core.llm.AgenticLLMService.ProgressListener progressListener =
                new com.excudo.core.llm.AgenticLLMService.ProgressListener() {
                    @Override
                    public void onProgress(int round, int maxRounds, String toolName) {
                        onProgress(round, maxRounds, toolName, null);
                    }
                    @Override
                    public void onProgress(int round, int maxRounds, String toolName, String detail) {
                        String label = com.excudo.core.llm.AgenticLLMService.TOOL_LABELS
                            .getOrDefault(toolName, toolName);
                        if (detail != null && !detail.isEmpty()) {
                            label += ": " + detail;
                        }
                        displayStyled("  " + label + "...", ConsoleStyle.DIM);
                    }
                };

            com.excudo.core.llm.AgenticLLMService.AgenticResult result =
                llmHandler.processEditRequestAgenticWithUsage(input, invoker, this, progressListener);
            displayMessage(result.summary());
            if (result.inputTokens() > 0 || result.outputTokens() > 0) {
                String tokenInfo = "Tokens: " + result.inputTokens() + " in / " + result.outputTokens() + " out";
                if (result.cost() > 0) {
                    tokenInfo += String.format(" ($%.4f)", result.cost());
                }
                displayStyled(tokenInfo, ConsoleStyle.DIM);
            }

        } catch (Exception e) {
            displayError("Arrange mode error: " + e.getMessage());
        }
    }

    /**
     * Get the PPTXOrchestrator for the current session.
     * Returns null if no session is active.
     * CRITICAL: Console commands should use this instead of the standalone orchestrator
     * to ensure they share the same orchestrator as LLM commands.
     */
    protected PPTXOrchestrator getCurrentSessionOrchestrator() {
        if (currentSessionId == null) {
            return null;
        }
        return sessionManager.getSession(currentSessionId)
                .map(SessionManager.ManagedSession::getOrchestrator)
                .orElse(null);
    }
    
    // Removed ParsedArgs - now using ConsoleArgumentParser.ParsedArgs
    
    // Removed parseQuotedArgs - now using ConsoleArgumentParser.parseQuotedArgs
    
    // Stream fields
    protected InputStream inputStream = System.in;
    protected PrintStream outputStream = System.out;
    protected PrintStream errorStream = System.err;
    
    // Shared scanner - should be set by the console that creates this engine
    protected Scanner sharedScanner;
    
    @Override
    public void initialize(PPTXOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
        this.commandFactory = new CommandFactory(orchestrator);
        // CommandInvoker is now session-scoped - access via getCurrentCommandInvoker()
        // slideCreator will be initialized when context is available
        this.llmHandler = new LLMConsoleHandler(orchestrator);

        // Register headless slide renderer for the 'render' command (via reflection
        // to avoid compile-time dependency on the view layer)
        try {
            Class<?> rendererClass = Class.forName("com.excudo.view.rendering.HeadlessSlideRenderer");
            com.excudo.core.commands.UtilityCommandFactory.setSlideRenderFunction(
                (doc, slideNum, outFile, w, h, theme, clrMap, bgHex, masterStyles) -> {
                    Object renderer = rendererClass.getConstructor(int.class, int.class).newInstance(w, h);
                    // Use renderToFileWithReport so we can surface font
                    // substitution warnings to the caller; view-layer types
                    // are handled reflectively so this class doesn't need
                    // to compile against JavaFX/AWT. Convert the returned
                    // RenderReport's substitution list into pre-formatted
                    // strings before handing back to core/commands.
                    Object report = rendererClass.getMethod("renderToFileWithReport",
                        com.excudo.core.model.PPTXDocument.class, int.class, java.io.File.class,
                        com.excudo.core.themes.ThemeDefinition.class, java.util.Map.class,
                        String.class, java.util.Map.class)
                        .invoke(renderer, doc, slideNum, outFile, theme, clrMap, bgHex, masterStyles);
                    Object subsObj = report.getClass().getMethod("substitutions").invoke(report);
                    java.util.List<?> subs = (java.util.List<?>) subsObj;
                    java.util.List<String> warnings = new java.util.ArrayList<>(subs.size());
                    for (Object s : subs) {
                        Class<?> subCls = s.getClass();
                        String requested = (String) subCls.getMethod("requested").invoke(s);
                        String actual = (String) subCls.getMethod("actual").invoke(s);
                        warnings.add("font '" + requested
                            + "' not available on render host; substituted with '" + actual
                            + "'. The saved .pptx will display correctly wherever '"
                            + requested + "' is installed.");
                    }
                    return warnings;
                });
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            // View layer not available (headless build without JavaFX)
        }

        // Register contact-sheet render function via the same reflection trick
        // so core/llm can composite multiple slides into a grid PNG without
        // depending on view/rendering at compile time.
        try {
            Class<?> rendererClass = Class.forName("com.excudo.view.rendering.HeadlessSlideRenderer");
            com.excudo.core.commands.UtilityCommandFactory.setContactSheetRenderFunction(
                (doc, slideNumbers, outFile, thumbW, thumbH, cols, gutter, theme, clrMap, bgFn, masterStyles) -> {
                    // Allocate renderer at full slide size (1280x720 default); the
                    // contact-sheet path scales each cached per-slide render down
                    // to thumbW x thumbH before compositing, so text metrics stay
                    // consistent with the normal render path.
                    Object renderer = rendererClass.getConstructor().newInstance();
                    Object sheet = rendererClass.getMethod("renderContactSheet",
                        com.excudo.core.model.PPTXDocument.class, int[].class,
                        int.class, int.class, int.class, int.class,
                        com.excudo.core.themes.ThemeDefinition.class, java.util.Map.class,
                        java.util.function.IntFunction.class, java.util.Map.class)
                        .invoke(renderer, doc, slideNumbers, thumbW, thumbH, cols, gutter,
                            theme, clrMap, bgFn, masterStyles);
                    java.awt.image.BufferedImage bi = (java.awt.image.BufferedImage) sheet;
                    if (outFile.getParentFile() != null) outFile.getParentFile().mkdirs();
                    javax.imageio.ImageIO.write(bi, "png", outFile);
                    return new int[]{bi.getWidth(), bi.getHeight()};
                });
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            // View layer not available (headless build without JavaFX)
        }
        this.sessionManager = SessionManager.getInstance();
        this.consoleSessionManager = new ConsoleSessionManager(
            sessionManager,
            this::displayMessage,
            this::displayError,
            this::displaySuccess
        );
        this.transactionManager = new TransactionManager(
            this::displayMessage,
            this::displayError
        );
        this.notesManager = new NotesManager(
            this::displayMessage,
            this::displayError
        );
        initializeSmartContent();
    }
    
    /**
     * Set the shared scanner from the main console loop
     * CRITICAL: This prevents multiple Scanner instances from competing for the same InputStream
     */
    public void setSharedScanner(Scanner scanner) {
        this.sharedScanner = scanner;
    }
    
    // Initialize SmartContent with default cache directory - moved from above
    private void initializeSmartContent() {
        try {
            String cacheDir = System.getProperty("user.home") + "/..excudo/icon-cache";
            this.contentEnhancer = new SmartContentEnhancer(cacheDir, "");
        } catch (Exception e) {
            displayMessage("Warning: SmartContent features may be limited - " + e.getMessage());
        }
    }
    
    // Removed parseCommand - now using ConsoleArgumentParser.parseCommand
    
    // Removed buildArgsString - now using ConsoleArgumentParser.buildArgsString
    
    @Override
    public void executeCommand(String commandString) {
        // MCP server mode: the HTTP server is driving tool calls; the TTY
        // only accepts server control commands until /exit stops it.
        if (isMcpMode()) {
            handleMcpModeInput(commandString);
            return;
        }

        // Arrange mode intercept: raw text goes to LLM, /commands go to normal pipeline
        if (arrangeMode) {
            if (commandString.equals("/exit")) {
                exitArrangeMode();
                return;
            }
            if (commandString.startsWith("/")) {
                // Strip leading slash and route to normal command pipeline
                String normalCommand = commandString.substring(1);
                executeCommandNormal(normalCommand);
                return;
            }
            // Raw text -> agentic LLM
            handleArrangeModeInput(commandString);
            return;
        }

        String trimmed = commandString.trim();

        // Handle "arrange mcp" subcommand to start an in-process MCP HTTP server.
        // Check BEFORE bare "arrange" so the longer match wins.
        if (trimmed.equalsIgnoreCase("arrange mcp")) {
            startMCPHttpServer();
            return;
        }

        // Handle bare "arrange" command to enter arrange mode
        if (trimmed.equalsIgnoreCase("arrange")) {
            enterArrangeMode();
            return;
        }

        // Remove Excudo's entry from Claude Desktop's config file.
        if (trimmed.equalsIgnoreCase("mcp-deregister")) {
            deregisterFromClaudeDesktop();
            return;
        }

        executeCommandNormal(commandString);
    }

    /**
     * Remove the Excudo entry from Claude Desktop's config file. Surfaces
     * the result on the TTY so the user knows whether anything changed.
     * No-op without errors when the config file doesn't exist.
     */
    protected void deregisterFromClaudeDesktop() {
        java.nio.file.Path path =
            com.excudo.mcp.config.ClaudeDesktopConfigWriter.detectConfigPath();
        com.excudo.mcp.config.ClaudeDesktopConfigWriter.Result result =
            com.excudo.mcp.config.ClaudeDesktopConfigWriter.deregister(path);
        if (result.written()) {
            displaySuccess(result.message() + " (" + result.configPath() + ")");
            displayMessage("Restart Claude Desktop for the change to take effect.");
        } else if (!result.configFound()) {
            displayMessage("No Claude Desktop config found at " + path + ". Nothing to do.");
        } else {
            displayMessage(result.message());
        }
    }

    // ========== MCP server mode ==========

    /** True while an in-process MCP HTTP server is running. */
    public boolean isMcpMode() {
        return activeMcpTransport != null;
    }

    /**
     * Start an in-process MCP HTTP/SSE server on 127.0.0.1:ephemeral
     * wired to the current session's orchestrator. Returns immediately
     * after the server is listening; the transport runs on a daemon
     * thread so the console stays responsive. Use /exit, /stop, or
     * {@link #stopMCPHttpServer()} to stop it.
     */
    public void startMCPHttpServer() {
        if (activeMcpTransport != null) {
            String where = activeMcpTransport.isBound()
                ? " at " + activeMcpTransport.getUrl()
                : " (still starting)";
            displayError("MCP server already running" + where
                + ". Use /exit to stop it first.");
            return;
        }

        com.excudo.core.orchestration.PPTXOrchestrator orch = getCurrentSessionOrchestrator();
        if (orch == null) orch = this.orchestrator;
        if (orch == null) {
            displayError("MCP server needs an orchestrator. Load a file or create a session first.");
            return;
        }

        // Create the transport shell synchronously (cheap, no I/O) and claim
        // the session immediately so a racing second arrange-mcp call is
        // rejected by the activeMcpTransport != null check above. Actual
        // port binding, HttpServer setup, and the serve loop all happen on
        // the worker thread below so the caller (FX thread in GUI mode)
        // never stalls on network setup.
        final com.excudo.core.orchestration.PPTXOrchestrator finalOrch = orch;
        com.excudo.mcp.MCPHttpSseTransport transport = new com.excudo.mcp.MCPHttpSseTransport();
        transport.setFrameListener(new com.excudo.mcp.MCPTTYEchoFormatter(this::displayStyled));
        activeMcpTransport = transport;
        claimSessionOwnership();

        displayMessage("Starting MCP server...");

        Thread serverThread = new Thread(() -> {
            try {
                transport.bind();

                CommandInvoker invoker = getCurrentCommandInvoker();
                if (invoker == null) invoker = new CommandInvoker();
                com.excudo.core.llm.ToolDispatcher dispatcher =
                    new com.excudo.core.llm.ToolDispatcher(finalOrch,
                        new CommandFactory(finalOrch), invoker);
                dispatcher.setDisplayAdapter(this);
                com.excudo.mcp.MCPProtocolHandler handler =
                    new com.excudo.mcp.MCPProtocolHandler(dispatcher);

                displaySuccess("MCP server listening at " + transport.getUrl());
                // Write the endpoint file BEFORE registerWithClaudeDesktop:
                // a freshly-launched bridge that races us would otherwise
                // see the .json missing on its first proxied call.
                com.excudo.mcp.MCPEndpointFile.write(
                    transport.getUrl(), transport.getToken());
                registerWithClaudeDesktop();
                displayMessage("  /exit or /stop  - stop the server and return to the console");
                displayMessage("  /status         - show current endpoint and token");

                transport.serve(handler::handleRequest);
            } catch (Exception e) {
                displayError("MCP server error: " + e.getMessage());
            } finally {
                // Whether serve returned cleanly or bind threw, the server
                // is no longer live -- clear the active ref so the user can
                // start another one. Remove the discovery file too so the
                // bridge falls back to "Excudo not running" on the next call.
                com.excudo.mcp.MCPEndpointFile.delete();
                if (activeMcpTransport == transport) {
                    activeMcpTransport = null;
                    releaseSessionOwnership();
                }
            }
        }, "mcp-server");
        serverThread.setDaemon(true);
        serverThread.start();
    }

    /** Stop the running MCP server, if any. Safe to call when no server is running. */
    public void stopMCPHttpServer() {
        if (activeMcpTransport == null) return;
        com.excudo.mcp.MCPHttpSseTransport t = activeMcpTransport;
        activeMcpTransport = null;
        releaseSessionOwnership();
        t.stop();
        displaySuccess("MCP server stopped.");
    }

    /**
     * Claim MCP exclusivity on the current session. Called from
     * {@link #startMCPHttpServer} so other consoles attached to the same
     * file can see the session is owned and refuse to mutate it. No-op
     * when there is no active session.
     */
    protected void claimSessionOwnership() {
        if (currentSessionId == null || sessionManager == null) return;
        sessionManager.getSession(currentSessionId)
            .ifPresent(s -> s.setMcpOwner(this));
    }

    /** Release MCP exclusivity on the current session. No-op when unowned. */
    protected void releaseSessionOwnership() {
        if (currentSessionId == null || sessionManager == null) return;
        sessionManager.getSession(currentSessionId)
            .ifPresent(SessionManager.ManagedSession::clearMcpOwner);
    }

    /**
     * Best-effort registration of Excudo's stdio bridge with Claude
     * Desktop. The config entry is per-install (path to the bridge
     * script), not per-session, so it survives every Excudo restart --
     * the bridge discovers the live URL via {@code ~/.excudo/mcp-endpoint.json}.
     *
     * <p>Never blocks server startup -- a missing config just gets a
     * status line and lets the user paste a manual config into a
     * different client if they want one.
     */
    protected void registerWithClaudeDesktop() {
        java.nio.file.Path path =
            com.excudo.mcp.config.ClaudeDesktopConfigWriter.detectConfigPath();
        if (!java.nio.file.Files.exists(path)) {
            displayMessage("  Claude Desktop config not found at " + path + ".");
            displayMessage("  Point your MCP client at: python3 " + locateBridgeScript());
            return;
        }
        java.nio.file.Path bridge = locateBridgeScript();
        if (!java.nio.file.Files.exists(bridge)) {
            displayMessage("  Bridge script not found at " + bridge + ".");
            displayMessage("  Skipping Claude Desktop registration.");
            return;
        }
        com.excudo.mcp.config.ClaudeDesktopConfigWriter.Result result =
            com.excudo.mcp.config.ClaudeDesktopConfigWriter.register(path, bridge);
        if (result.written()) {
            displayMessage("  Registered with Claude Desktop at " + result.configPath());
            displayMessage("  Bridge: python3 " + bridge);
            displayMessage("  Restart Claude Desktop once -- subsequent Excudo restarts are auto-detected.");
        } else {
            displayMessage("  Claude Desktop config update skipped: " + result.message());
        }
    }

    /**
     * Resolve the stdio bridge script path. The bridge ships in the
     * project repo at {@code tools/mcp-server/excudo_bridge.py}; walks
     * up from the current working directory to find it (handles the
     * common case of running via {@code pc.py} from project root, and
     * the edge case of being launched from a subdirectory).
     */
    private java.nio.file.Path locateBridgeScript() {
        java.nio.file.Path relative =
            java.nio.file.Path.of("tools", "mcp-server", "excudo_bridge.py");
        java.nio.file.Path cwd =
            java.nio.file.Path.of(System.getProperty("user.dir", "."));
        java.nio.file.Path candidate = cwd.resolve(relative);
        if (java.nio.file.Files.exists(candidate)) {
            return candidate.toAbsolutePath().normalize();
        }
        java.nio.file.Path parent = cwd.getParent();
        while (parent != null) {
            candidate = parent.resolve(relative);
            if (java.nio.file.Files.exists(candidate)) {
                return candidate.toAbsolutePath().normalize();
            }
            parent = parent.getParent();
        }
        // Fall back to the cwd-relative path even if it doesn't exist;
        // the caller logs a friendlier message via the existence check.
        return cwd.resolve(relative).toAbsolutePath().normalize();
    }

    /**
     * Handle a line typed at the TTY while the MCP server is running.
     * Only server-control commands are accepted; anything else is
     * rejected so the user can't accidentally mutate the session out
     * from under the connected MCP client.
     */
    private void handleMcpModeInput(String input) {
        String trimmed = input.trim();
        if ("/exit".equalsIgnoreCase(trimmed) || "/stop".equalsIgnoreCase(trimmed)) {
            stopMCPHttpServer();
            return;
        }
        if ("/status".equalsIgnoreCase(trimmed)) {
            showMcpStatus();
            return;
        }
        displayError("MCP server is running. Only /exit, /stop, and /status are accepted here. "
            + "(Got: " + trimmed + ")");
    }

    private void showMcpStatus() {
        if (activeMcpTransport == null) {
            displayMessage("No MCP server running.");
            return;
        }
        if (!activeMcpTransport.isBound()) {
            displayStyled("MCP server: starting... (port not yet allocated)",
                ConsoleStyle.HEADER);
            displayMessage("Token prefix: " + activeMcpTransport.getToken().substring(0, 8) + "...");
            return;
        }
        displayStyled("MCP server: " + activeMcpTransport.getUrl(), ConsoleStyle.HEADER);
        displayMessage("Token prefix: " + activeMcpTransport.getToken().substring(0, 8) + "...");
    }

    private void executeCommandNormal(String commandString) {
        try {
            // Use sophisticated CommandRegistry parsing instead of primitive string splitting
            com.excudo.core.parsing.CommandParameters parameters =
                com.excudo.core.parsing.CommandRegistry.parse(commandString);
            
            // Determine if this command requires a session or can work sessionless
            String commandName = parameters.getCommandName();
            boolean requiresSession = requiresSession(commandName);
            
            // Create Command object via appropriate CommandFactory
            CommandFactory factory;
            CommandInvoker invoker;
            
            if (requiresSession) {
                // Session-dependent commands
                if (currentSessionId == null) {
                    displayError("No active session. Use 'load <filename>' or 'session create' first.");
                    return;
                }

                // MCP exclusivity: only the engine that started the MCP
                // server can mutate its session. Other consoles attached
                // to the same file get a read-only error.
                Optional<SessionManager.ManagedSession> sessionOpt =
                    sessionManager.getSession(currentSessionId);
                if (sessionOpt.isPresent() && sessionOpt.get().isMcpExclusive()
                        && !sessionOpt.get().isOwnedBy(this)) {
                    displayError("Session is owned by MCP client (read-only). Cannot execute commands.");
                    return;
                }

                factory = getSessionCommandFactory();
                invoker = getCurrentCommandInvoker();
                if (factory == null || invoker == null) {
                    displayError("No active session. Use 'load <filename>' or 'session create' first.");
                    return;
                }
            } else {
                // Sessionless commands: prefer session factory if available (has loaded context)
                if (currentSessionId != null) {
                    factory = getSessionCommandFactory();
                } else {
                    factory = this.commandFactory;
                }
                if (factory == null) {
                    factory = new CommandFactory(this.orchestrator);
                }
                invoker = new CommandInvoker();
            }
            
            Command command = factory.createCommand(parameters, this);
            invoker.executeCommand(command);
            
        } catch (com.excudo.core.parsing.CommandParseException e) {
            displayError("Command error: " + e.getMessage());
        } catch (CommandExecutionException e) {
            displayError("Execution failed: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            displayError("Command not supported: " + e.getMessage());
            displayMessage("Some commands require additional implementation work.");
        } catch (Exception e) {
            String errorMsg = e.getMessage();
            if (errorMsg == null || errorMsg.isEmpty()) {
                errorMsg = e.getClass().getSimpleName() + " at " + 
                           (e.getStackTrace().length > 0 ? e.getStackTrace()[0].toString() : "unknown location");
            }
            displayError("Unexpected error: " + errorMsg);
            if (e.getCause() != null) {
                displayError("Caused by: " + e.getCause().getMessage());
            }
            logger.error("Failed to execute command: " + e.getMessage(), e);
        }
        
        
    }
    
    
    /**
     * Handle LLM commands - implementation may vary between TTY and UI
     */
    protected abstract void handleLLMCommand(String subCommand);
    
    
    
    
    // ------------------------------------------------------------------
    // Display methods
    //
    // displayStyled is the single rendering hook each subclass must
    // implement; displayMessage/displayError/displaySuccess are concrete
    // forwarders that attach a semantic ConsoleStyle. Callers that need
    // finer control (DIM progress lines, ACCENT prompts, etc.) call
    // displayStyled directly rather than embedding ANSI escapes in the
    // message text.
    // ------------------------------------------------------------------

    public abstract void displayStyled(String message, ConsoleStyle style);

    public void displayMessage(String message) {
        displayStyled(message, ConsoleStyle.NONE);
    }

    public void displayError(String message) {
        displayStyled(message, ConsoleStyle.ERROR);
    }

    public void displaySuccess(String message) {
        displayStyled(message, ConsoleStyle.SUCCESS);
    }
    
    // CommandSessionManager interface implementation
    @Override
    public SessionResult createSession(String filename) {
        try {
            ConsoleSessionManager.SessionCreationResult result = consoleSessionManager.createSession(filename);
            return new LoadSessionResultAdapter(result);
        } catch (Exception e) {
            return new LoadSessionResultAdapter(null, e.getMessage());
        }
    }
    
    @Override
    public SessionResult createEmptySession() {
        try {
            ConsoleSessionManager.SessionCreationResult result = consoleSessionManager.createEmptySession();
            return new LoadSessionResultAdapter(result);
        } catch (Exception e) {
            return new LoadSessionResultAdapter(null, "Failed to create empty session: " + e.getMessage());
        }
    }
    
    @Override
    public java.util.Map<String, CommandSessionManager.SessionInfo> getAllSessions() {
        java.util.Map<String, CommandSessionManager.SessionInfo> result = new java.util.HashMap<>();
        for (String id : sessionManager.getActiveSessionIds()) {
            sessionManager.getSession(id).ifPresent(session ->
                result.put(id, new SessionInfoAdapter(session)));
        }
        return result;
    }

    @Override
    public CommandSessionManager.SessionInfo getSessionInfo(String sessionId) {
        return sessionManager.getSession(sessionId)
                .map(SessionInfoAdapter::new)
                .orElse(null);
    }
    
    @Override
    public SessionResult switchToSession(String sessionId) {
        ConsoleSessionManager.SessionSwitchResult result = consoleSessionManager.switchSession(sessionId);
        if (result.isSuccess()) {
            this.currentSessionId = result.getSessionId();
            this.orchestrator = result.getOrchestrator();
            this.llmHandler = result.getLlmHandler();
            // Announce the active-session move to every SessionManager
            // subscriber (GUI MainController, PresentationExplorer, etc).
            // Unified replacement for the old per-engine
            // orchestratorChangeHandler fan-out.
            if (sessionManager != null) sessionManager.setActiveSession(this.currentSessionId);
        }
        return new SwitchSessionResultAdapter(result);
    }

    @Override
    public boolean closeSession(String sessionId) {
        return consoleSessionManager.closeSession(sessionId, currentSessionId);
    }
    
    @Override
    public void clearCurrentSession() {
        this.currentSessionId = null;
        this.orchestrator = null;
        this.llmHandler = null;
        this.currentFile = null;
        if (sessionManager != null) sessionManager.setActiveSession(null);
    }

    @Override
    public void shutdown() {
        logger.info("Console engine shutting down");

        // Shut down SessionManager first (cleans up all session-scoped temp dirs)
        if (sessionManager != null) {
            sessionManager.shutdown();
        }

        // Clean up orchestrator temp directory (for sessionless loads via InteractiveConsole)
        if (orchestrator != null) {
            try {
                java.util.Optional<OrchestrationContext> ctxOpt = orchestrator.getContext();
                if (ctxOpt.isPresent()) {
                    com.excudo.core.model.PPTXDocument doc = ctxOpt.get().getDocument();
                    java.io.File extractDir = (doc != null) ? doc.getExtractedDir() : null;
                    java.io.File tempDir = (extractDir != null) ? extractDir.getParentFile() : null;
                    if (tempDir != null && tempDir.exists() && tempDir.getName().startsWith("pptx_")) {
                        deleteDirectoryRecursively(tempDir.toPath());
                        logger.debug("Deleted orchestrator temp dir: {}", tempDir.getName());
                    }
                }
            } catch (Exception e) {
                logger.warn("Error during orchestrator cleanup: {}", e.getMessage());
            }
        }
    }

    private void deleteDirectoryRecursively(java.nio.file.Path directory) {
        try {
            java.nio.file.Files.walk(directory)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(path -> {
                        try {
                            java.nio.file.Files.delete(path);
                        } catch (Exception e) {
                            logger.debug("Failed to delete {}: {}", path, e.getMessage());
                        }
                    });
        } catch (Exception e) {
            logger.warn("Failed to walk directory for deletion: {}", e.getMessage());
        }
    }
    
    // CommandSessionContext interface implementation
    @Override
    public String getCurrentSessionId() {
        return currentSessionId;
    }
    
    @Override
    public void setCurrentSession(String sessionId, PPTXOrchestrator orchestrator, Object llmHandler, File currentFile) {
        this.currentSessionId = sessionId;
        this.orchestrator = orchestrator;
        this.llmHandler = (LLMConsoleHandler) llmHandler;
        this.currentFile = currentFile;
        if (sessionManager != null) sessionManager.setActiveSession(sessionId);
    }
    
    @Override
    public PPTXOrchestrator getCurrentOrchestrator() {
        return getCurrentSessionOrchestrator();
    }
    
    // LLMContext interface implementation
    @Override
    public LLMHandler getLLMHandler() {
        return llmHandler;
    }

    // IconContext interface implementation
    @Override
    public IconRepository getIconRepository() {
        if (contentEnhancer != null) {
            return contentEnhancer.getIconRepository();
        }
        return null;
    }

    @Override
    public String promptUser(String prompt) {
        if (sharedScanner == null) return "";
        System.out.print(prompt);
        System.out.flush();
        return sharedScanner.hasNextLine() ? sharedScanner.nextLine().trim() : "";
    }
    
    // Utility methods for session context
    public File getCurrentFile() {
        return currentFile;
    }
    
    public void setCurrentFile(File file) {
        this.currentFile = file;
    }
    
    /**
     * Adapter to convert ConsoleSessionManager result to LoadCommand interface
     */
    private static class LoadSessionResultAdapter implements SessionResult {
        private final ConsoleSessionManager.SessionCreationResult result;
        private final String errorMessage;
        
        public LoadSessionResultAdapter(ConsoleSessionManager.SessionCreationResult result) {
            this.result = result;
            this.errorMessage = null;
        }
        
        public LoadSessionResultAdapter(ConsoleSessionManager.SessionCreationResult result, String errorMessage) {
            this.result = result;
            this.errorMessage = errorMessage;
        }
        
        @Override
        public boolean isSuccess() {
            return result != null && result.isSuccess() && errorMessage == null;
        }
        
        @Override
        public String getSessionId() {
            return result != null ? result.getSessionId() : null;
        }
        
        @Override
        public PPTXOrchestrator getOrchestrator() {
            return result != null ? result.getOrchestrator() : null;
        }
        
        @Override
        public Object getLlmHandler() {
            return result != null ? result.getLlmHandler() : null;
        }
        
        @Override
        public File getCurrentFile() {
            return result != null ? result.getCurrentFile() : null;
        }
        
        @Override
        public String getErrorMessage() {
            return errorMessage != null ? errorMessage : (result != null ? result.getErrorMessage() : "Unknown error");
        }
    }
    
    /**
     * Adapter to convert ConsoleSessionManager switch result to SessionResult interface
     */
    private static class SwitchSessionResultAdapter implements SessionResult {
        private final ConsoleSessionManager.SessionSwitchResult result;

        public SwitchSessionResultAdapter(ConsoleSessionManager.SessionSwitchResult result) {
            this.result = result;
        }

        @Override public boolean isSuccess() { return result != null && result.isSuccess(); }
        @Override public String getSessionId() { return result != null ? result.getSessionId() : null; }
        @Override public PPTXOrchestrator getOrchestrator() { return result != null ? result.getOrchestrator() : null; }
        @Override public Object getLlmHandler() { return result != null ? result.getLlmHandler() : null; }
        @Override public File getCurrentFile() { return null; }
        @Override public String getErrorMessage() { return result != null ? result.getErrorMessage() : "Unknown error"; }
    }

    /**
     * Session-scoped CommandFactory cache to maintain state across commands.
     * Maps sessionId to CommandFactory to ensure GroupIdManager state persists.
     */
    private final java.util.Map<String, CommandFactory> sessionCommandFactories = new java.util.concurrent.ConcurrentHashMap<>();
    
    /**
     * Get or create session-scoped CommandFactory to maintain state across commands.
     * This ensures GroupIdManager state persists across animation commands.
     */
    private CommandFactory getSessionCommandFactory() {
        return sessionCommandFactories.computeIfAbsent(currentSessionId, 
            sessionId -> new CommandFactory(getCurrentSessionOrchestrator()));
    }
    
    // Stream interface implementation
    @Override
    public void setInputStream(InputStream inputStream) {
        this.inputStream = inputStream;
    }
    
    @Override
    public void setOutputStream(PrintStream outputStream) {
        this.outputStream = outputStream;
    }
    
    @Override
    public void setErrorStream(PrintStream errorStream) {
        this.errorStream = errorStream;
    }
    
    /**
     * Session management helper methods
     */
    protected void createSessionDirect(String filename) {
        ConsoleSessionManager.SessionCreationResult result = consoleSessionManager.createSession(filename);

        if (result.isSuccess()) {
            this.currentSessionId = result.getSessionId();
            this.orchestrator = result.getOrchestrator();
            this.llmHandler = result.getLlmHandler();
            this.currentFile = result.getCurrentFile();
            if (sessionManager != null) sessionManager.setActiveSession(this.currentSessionId);
        } else {
            displayError(result.getErrorMessage());
        }
    }

    protected void createEmptySessionDirect() {
        ConsoleSessionManager.SessionCreationResult result = consoleSessionManager.createEmptySession();

        if (result.isSuccess()) {
            this.currentSessionId = result.getSessionId();
            this.orchestrator = result.getOrchestrator();
            this.llmHandler = result.getLlmHandler();
            this.currentFile = null;
            if (sessionManager != null) sessionManager.setActiveSession(this.currentSessionId);
        } else {
            displayError(result.getErrorMessage());
        }
    }
    
    protected void listSessions() {
        consoleSessionManager.listSessions(currentSessionId);
    }
    
    protected void showSessionInfo(String sessionId) {
        consoleSessionManager.showSessionInfo(sessionId);
    }
    
    
    protected void switchSession(String sessionId) {
        ConsoleSessionManager.SessionSwitchResult result = consoleSessionManager.switchSession(sessionId);
        
        if (result.isSuccess()) {
            this.currentSessionId = result.getSessionId();
            this.orchestrator = result.getOrchestrator(); // Keep for compatibility with legacy methods
            this.llmHandler = result.getLlmHandler();
            // currentFile will be determined from session context if needed
        } else {
            displayError(result.getErrorMessage());
        }
    }
    
    @Override
    public List<String> getAvailableCommands() {
        return Arrays.asList(
            "help", "load", "save", "render-slide", "list-slides", "show-slide", "create-slide", "delete-slide", "llm",
            "session", "inject-icon", "enhanced-content", "list-layouts", "list-spids", "list-animations",
            "list-animation-types", "list-shape-types", "dump-timing", "dump-shape",
            "show-shape", "content-edit", "add-shape", "remove-shape", "bullet-point-edit",
            "add-animation", "remove-animation",
            "update-animation", "list-notes", "list-themes", "apply-theme", "new",
            "show-theme", "create-theme", "edit-theme", "delete-theme",
            "undo", "redo", "history", "arrange"
        );
    }
    
    @Override
    public String getCommandHelp(String command) {
        return ConsoleOutputFormatter.getCommandHelp(command);
    }
    
    @Override
    public boolean isPresentationLoaded() {
        return PresentationInspector.isPresentationLoaded(getCurrentSessionOrchestrator());
    }
    
    @Override
    public String getPresentationStatus() {
        return PresentationInspector.getPresentationContextSummary(getCurrentSessionOrchestrator());
    }

    @Override
    public boolean hasUnsavedChanges() {
        CommandInvoker invoker = getCurrentCommandInvoker();
        return invoker != null && invoker.hasUnsavedChanges();
    }
    
    
    
    
    
    
    
    
    
    
    
}
