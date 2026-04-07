package com.excudo.cli;

import com.excudo.core.orchestration.*;
import com.excudo.core.llm.*;
import com.excudo.core.smartcontent.IconRepository;
import com.excudo.core.smartcontent.SmartContentEnhancer;
import com.excudo.core.utils.Logger;
import com.excudo.core.utils.ComponentLogger;
import com.excudo.core.utils.JSONBuilder;
import com.excudo.core.results.ExecutionResult;
import com.excudo.core.validation.ValidationResult;
import com.excudo.core.model.*;
import com.excudo.exceptions.XMLParsingException;

import com.excudo.core.utils.XMLFactoryProvider;
import java.io.File;
import java.util.*;
import java.util.Optional;

/**
 * Headless CLI interface for programmatic access to Excudo
 * This class provides JSON-based communication between Python API and Java orchestrator
 */
public class HeadlessInterface {
    
    private static final ComponentLogger logger = Logger.cli();
    
    private SessionManager sessionManager;
    private boolean jsonOutput = false;
    
    public static void main(String[] args) {
        HeadlessInterface cli = new HeadlessInterface();
        int exitCode = cli.run(args);
        System.exit(exitCode);
    }
    
    public int run(String[] args) {
        try {
            // Parse arguments
            CLIArgs parsedArgs = parseArguments(args);
            
            // Set output mode
            this.jsonOutput = parsedArgs.jsonOutput;
            
            // Initialize orchestrator
            initialize();
            
            // Execute command
            return executeCommand(parsedArgs);
            
        } catch (Exception e) {
            logger.error("Headless interface error: {}", e.getMessage());
            outputError("Failed to execute command: " + e.getMessage());
            return 1;
        } finally {
            shutdown();
        }
    }
    
    private void initialize() throws XMLParsingException {
        logger.info("Initializing headless interface");
        this.sessionManager = SessionManager.getInstance();
    }
    
    private void shutdown() {
        logger.info("Shutting down headless interface");
        // Sessions are managed by SessionManager lifecycle
    }
    
    private int executeCommand(CLIArgs args) {
        try {
            switch (args.command) {
                case "create-session":
                    return handleCreateSession(args);
                case "close-session":
                    return handleCloseSession(args);
                case "list-sessions":
                    return handleListSessions(args);
                case "session-info":
                    return handleSessionInfo(args);
                case "load":
                    return handleLoad(args);
                case "save":
                    return handleSave(args);
                case "create-new":
                    return handleCreateNew(args);
                case "llm-command":
                    return handleLLMCommand(args);
                case "generate-context":
                    return handleGenerateContext(args);
                case "get-info":
                    return handleGetInfo(args);
                case "validate":
                    return handleValidate(args);
                case "dump-timing":
                    return handleDumpTiming(args);
                case "dump-shape":
                    return handleDumpShape(args);
                default:
                    outputError("Unknown command: " + args.command);
                    return 1;
            }
        } catch (Exception e) {
            logger.error("Command execution failed: {}", e.getMessage());
            outputError("Command failed: " + e.getMessage());
            return 1;
        }
    }
    
    private int handleCreateSession(CLIArgs args) {
        try {
            String sessionId;
            if (args.presentationPath != null) {
                // Create session from existing file
                File pptxFile = new File(args.presentationPath);
                if (!pptxFile.exists()) {
                    outputError("Presentation file not found: " + args.presentationPath);
                    return 1;
                }
                logger.info("Creating session from file: {}", args.presentationPath);
                sessionId = sessionManager.createSession(pptxFile);
            } else {
                // Create session with new empty presentation
                logger.info("Creating session with new empty presentation");
                sessionId = sessionManager.createNewSession();
            }
            
            JSONBuilder response = new JSONBuilder()
                .put("success", true)
                .put("message", "Session created successfully")
                .put("session_id", sessionId);
            
            // Get session info
            Optional<SessionManager.ManagedSession> sessionOpt = sessionManager.getSession(sessionId);
            if (sessionOpt.isPresent()) {
                SessionManager.ManagedSession session = sessionOpt.get();
                JSONBuilder sessionInfo = response.createNestedObject("session_info")
                    .put("slide_count", session.getSlideCount())
                    .put("has_original_file", session.hasOriginalFile());
                if (session.hasOriginalFile()) {
                    sessionInfo.put("original_file", session.getOriginalFile().getAbsolutePath());
                }
            }
            
            outputSuccess(response);
            return 0;
            
        } catch (Exception e) {
            logger.error("Create session failed: {}", e.getMessage());
            outputError("Create session failed: " + e.getMessage());
            return 1;
        }
    }
    
    private int handleCloseSession(CLIArgs args) {
        if (args.sessionId == null) {
            outputError("Missing session ID for close-session command");
            return 1;
        }
        
        try {
            boolean closed = sessionManager.closeSession(args.sessionId);
            
            JSONBuilder response = new JSONBuilder()
                .put("success", closed);
            if (closed) {
                response.put("message", "Session closed successfully");
                logger.info("Session {} closed", args.sessionId);
            } else {
                response.put("message", "Session not found: " + args.sessionId);
            }
            
            if (closed) {
                outputSuccess(response);
                return 0;
            } else {
                outputError(response);
                return 1;
            }
            
        } catch (Exception e) {
            logger.error("Close session failed: {}", e.getMessage());
            outputError("Close session failed: " + e.getMessage());
            return 1;
        }
    }
    
    private int handleListSessions(CLIArgs args) {
        try {
            Set<String> sessionIds = sessionManager.getActiveSessionIds();
            
            JSONBuilder response = new JSONBuilder()
                .put("success", true)
                .put("message", "Active sessions retrieved")
                .put("session_count", sessionIds.size());
            
            List<JSONBuilder> sessionsList = new ArrayList<>();
            for (String sessionId : sessionIds) {
                Optional<SessionManager.ManagedSession> sessionOpt = sessionManager.getSession(sessionId);
                if (sessionOpt.isPresent()) {
                    SessionManager.ManagedSession session = sessionOpt.get();
                    JSONBuilder sessionInfo = new JSONBuilder()
                        .put("session_id", sessionId)
                        .put("slide_count", session.getSlideCount())
                        .put("has_original_file", session.hasOriginalFile())
                        .put("last_accessed", session.getLastAccessed());
                    if (session.hasOriginalFile()) {
                        sessionInfo.put("original_file", session.getOriginalFile().getAbsolutePath());
                    }
                    sessionsList.add(sessionInfo);
                }
            }
            response.putObjectArray("sessions", sessionsList);
            
            outputSuccess(response);
            return 0;
            
        } catch (Exception e) {
            logger.error("List sessions failed: {}", e.getMessage());
            outputError("List sessions failed: " + e.getMessage());
            return 1;
        }
    }
    
    private int handleSessionInfo(CLIArgs args) {
        if (args.sessionId == null) {
            outputError("Missing session ID for session-info command");
            return 1;
        }
        
        try {
            Optional<SessionManager.ManagedSession> sessionOpt = sessionManager.getSession(args.sessionId);
            if (!sessionOpt.isPresent()) {
                outputError("Session not found: " + args.sessionId);
                return 1;
            }
            
            SessionManager.ManagedSession session = sessionOpt.get();
            Map<String, Object> status = session.getStatus();
            
            JSONBuilder response = new JSONBuilder()
                .put("success", true)
                .put("message", "Session info retrieved");
            
            JSONBuilder sessionInfo = response.createNestedObject("session_info");
            status.forEach((key, value) -> {
                if (value instanceof String) {
                    sessionInfo.put(key, (String) value);
                } else if (value instanceof Integer) {
                    sessionInfo.put(key, (Integer) value);
                } else if (value instanceof Long) {
                    sessionInfo.put(key, (Long) value);
                } else if (value instanceof Boolean) {
                    sessionInfo.put(key, (Boolean) value);
                } else if (value != null) {
                    sessionInfo.put(key, value.toString());
                }
            });
            
            outputSuccess(response);
            return 0;
            
        } catch (Exception e) {
            logger.error("Session info failed: {}", e.getMessage());
            outputError("Session info failed: " + e.getMessage());
            return 1;
        }
    }
    
    private int handleLoad(CLIArgs args) {
        // Load command now creates a session
        return handleCreateSession(args);
    }
    
    private int handleSave(CLIArgs args) {
        if (args.sessionId == null) {
            outputError("Missing session ID for save command");
            return 1;
        }
        if (args.outputPath == null) {
            outputError("Missing output path for save command");
            return 1;
        }
        
        try {
            Optional<SessionManager.ManagedSession> sessionOpt = sessionManager.getSession(args.sessionId);
            if (!sessionOpt.isPresent()) {
                outputError("Session not found: " + args.sessionId);
                return 1;
            }
            
            SessionManager.ManagedSession session = sessionOpt.get();
            logger.info("Saving presentation from session {} to: {}", args.sessionId, args.outputPath);
            
            File outputFile = new File(args.outputPath);
            ExecutionResult result = session.getOrchestrator().savePresentation(outputFile);
            
            if (result.isSuccess()) {
                JSONBuilder response = new JSONBuilder()
                    .put("success", true)
                    .put("message", "Presentation saved successfully")
                    .put("output_path", args.outputPath)
                    .put("session_id", args.sessionId);
                
                outputSuccess(response);
                return 0;
            } else {
                outputError("Failed to save presentation: " + result.getMessage());
                return 1;
            }
            
        } catch (Exception e) {
            logger.error("Save operation failed: {}", e.getMessage());
            outputError("Save failed: " + e.getMessage());
            return 1;
        }
    }
    
    private int handleCreateNew(CLIArgs args) {
        // Create-new command now creates a session with new presentation
        // Remove presentationPath to force new session creation
        CLIArgs newArgs = new CLIArgs();
        newArgs.command = "create-session";
        newArgs.presentationPath = null; // Force new presentation
        newArgs.jsonOutput = args.jsonOutput;
        return handleCreateSession(newArgs);
    }
    
    private int handleLLMCommand(CLIArgs args) {
        if (args.sessionId == null) {
            outputError("Missing session ID for llm-command");
            return 1;
        }
        if (args.llmCommand == null) {
            outputError("Missing LLM command");
            return 1;
        }
        
        try {
            logger.info("Executing LLM command in session {}: {}", args.sessionId, args.llmCommand);
            
            // Parse the LLM command JSON
            JSONBuilder commandBuilder = JSONBuilder.fromString(args.llmCommand);
            String operation = commandBuilder.getString("operation");
            
            if (operation == null) {
                outputError("Missing operation in LLM command");
                return 1;
            }
            
            switch (operation.toLowerCase()) {
                case "smart-content":
                case "icon-search":
                    // Check if session is needed for this operation
                    String action = commandBuilder.getString("action");
                    SessionManager.ManagedSession session = null;
                    
                    if ("inject".equals(action)) {
                        // Injection requires a valid session
                        Optional<SessionManager.ManagedSession> sessionOpt = sessionManager.getSession(args.sessionId);
                        if (!sessionOpt.isPresent()) {
                            outputError("Session not found: " + args.sessionId);
                            return 1;
                        }
                        session = sessionOpt.get();
                    } else {
                        // For search, upload, list - session is optional
                        Optional<SessionManager.ManagedSession> sessionOpt = sessionManager.getSession(args.sessionId);
                        session = sessionOpt.orElse(null);
                    }
                    
                    return handleSmartContentOperation(session, commandBuilder, args.sessionId);
                default:
                    JSONBuilder response = new JSONBuilder()
                        .put("success", false)
                        .put("message", "Unknown LLM operation: " + operation)
                        .put("session_id", args.sessionId);
                    
                    outputError(response);
                    return 1;
            }
            
        } catch (Exception e) {
            logger.error("LLM command failed: {}", e.getMessage());
            outputError("LLM command failed: " + e.getMessage());
            return 1;
        }
    }
    
    private int handleSmartContentOperation(SessionManager.ManagedSession session, JSONBuilder command, String sessionId) {
        try {
            String action = command.getString("action");
            if (action == null) {
                outputError("Missing action in SmartContent operation");
                return 1;
            }
            
            // Get cache directory from command or use default
            String cacheDir = command.getString("cache_dir");
            if (cacheDir == null) {
                cacheDir = "./icon-cache";
            }
            
            switch (action.toLowerCase()) {
                case "search":
                    // Search doesn't need session
                    return handleIconSearch(command, cacheDir, sessionId);
                    
                case "inject":
                    // Inject needs session for presentation context
                    if (session == null) {
                        outputError("Icon injection requires a valid session with presentation");
                        return 1;
                    }
                    return handleIconInject(session, command, cacheDir, sessionId);
                    
                case "upload":
                    // Upload doesn't need session
                    return handleIconUpload(command, cacheDir, sessionId);
                    
                case "list":
                    // List doesn't need session
                    return handleIconList(command, cacheDir, sessionId);
                    
                default:
                    JSONBuilder response = new JSONBuilder()
                        .put("success", false)
                        .put("message", "Unknown SmartContent action: " + action)
                        .put("session_id", sessionId);
                    
                    outputError(response);
                    return 1;
            }
            
        } catch (Exception e) {
            logger.error("SmartContent operation failed: {}", e.getMessage());
            outputError("SmartContent operation failed: " + e.getMessage());
            return 1;
        }
    }
    
    private int handleIconSearch(JSONBuilder command, String cacheDir, String sessionId) throws Exception {
        String query = command.getString("query");
        if (query == null) {
            outputError("Missing query for icon search");
            return 1;
        }
        
        int maxResults = command.getInt("max_results", 10);
        String source = command.getString("source", "all");
        
        // Create repository and search
        IconRepository repository = IconRepository.fromEnvironment(cacheDir);
        ExecutionResult<IconRepository.IconSearchResult> result = repository.searchIcons(query, maxResults);
        
        if (result.isSuccess()) {
            IconRepository.IconSearchResult searchResult = result.getData().get();
            List<IconRepository.IconAsset> icons = searchResult.getIcons();
            
            JSONBuilder response = new JSONBuilder()
                .put("success", true)
                .put("message", "Icon search completed")
                .put("session_id", sessionId)
                .put("query", query)
                .put("result_count", icons.size())
                .put("search_time_ms", searchResult.getSearchTimeMs());
            
            List<JSONBuilder> iconsList = new ArrayList<>();
            for (IconRepository.IconAsset icon : icons) {
                JSONBuilder iconJson = new JSONBuilder()
                    .put("name", icon.getName())
                    .put("source", icon.getSource())
                    .put("path", icon.getPath())
                    .put("relevance_score", icon.getRelevanceScore())
                    .put("attribution", icon.getAttribution())
                    .put("tags", String.join(", ", icon.getTags()));
                
                if (icon.getAuthorName() != null) {
                    iconJson.put("author_name", icon.getAuthorName());
                }
                if (icon.getResourceId() != null) {
                    iconJson.put("resource_id", icon.getResourceId());
                }
                if (icon.getSourceUrl() != null) {
                    iconJson.put("source_url", icon.getSourceUrl());
                }
                
                iconsList.add(iconJson);
            }
            response.putObjectArray("icons", iconsList);
            
            outputSuccess(response);
            return 0;
        } else {
            outputError("Icon search failed: " + result.getErrorMessage());
            return 1;
        }
    }
    
    private int handleIconInject(SessionManager.ManagedSession session, JSONBuilder command, String cacheDir, String sessionId) throws Exception {
        String query = command.getString("query");
        int slideNumber = command.getInt("slide_number", 1);
        String position = command.getString("position", "auto");
        
        if (query == null) {
            outputError("Missing query for icon injection");
            return 1;
        }
        
        // Get presentation path from session
        Optional<OrchestrationContext> contextOpt = session.getOrchestrator().getContext();
        if (!contextOpt.isPresent()) {
            outputError("No presentation context available in session");
            return 1;
        }
        
        com.excudo.core.model.PPTXDocument doc = contextOpt.get().getDocument();
        java.io.File extractedDir = (doc != null) ? doc.getExtractedDir() : null;
        String presentationPath = (extractedDir != null) ? extractedDir.getAbsolutePath() : "in-memory";
        
        // Create enhancer and inject icon
        SmartContentEnhancer enhancer = new SmartContentEnhancer(cacheDir, presentationPath);
        
        // Create mock slide data - in production this would parse the actual slide
        ParsedSlideData slideData = createMockSlideData();
        
        ExecutionResult<SlideShape> result = enhancer.injectIcon(query, slideData, slideNumber);
        
        if (result.isSuccess()) {
            SlideShape injectedShape = result.getData().get();
            
            JSONBuilder response = new JSONBuilder()
                .put("success", true)
                .put("message", "Icon injected successfully")
                .put("session_id", sessionId)
                .put("query", query)
                .put("slide_number", slideNumber);
            
            JSONBuilder shapeInfo = response.createNestedObject("injected_shape")
                .put("shape_id", injectedShape.getSpid())
                .put("x", injectedShape.getGeometry().getX())
                .put("y", injectedShape.getGeometry().getY())
                .put("width", injectedShape.getGeometry().getWidth())
                .put("height", injectedShape.getGeometry().getHeight());
            
            outputSuccess(response);
            return 0;
        } else {
            outputError("Icon injection failed: " + result.getErrorMessage());
            return 1;
        }
    }
    
    private int handleIconUpload(JSONBuilder command, String cacheDir, String sessionId) throws Exception {
        String filePath = command.getString("file_path");
        String iconName = command.getString("icon_name");
        
        if (filePath == null || iconName == null) {
            outputError("Missing file_path or icon_name for upload");
            return 1;
        }
        
        // Parse tags
        Set<String> tags = new HashSet<>();
        String tagsStr = command.getString("tags");
        if (tagsStr != null) {
            tags.addAll(Arrays.asList(tagsStr.split(",")));
        }
        
        // Create repository and upload
        IconRepository repository = IconRepository.fromEnvironment(cacheDir);
        ExecutionResult<IconRepository.IconAsset> result = repository.uploadLocalIcon(filePath, iconName, tags);
        
        if (result.isSuccess()) {
            IconRepository.IconAsset uploadedIcon = result.getData().get();
            
            JSONBuilder response = new JSONBuilder()
                .put("success", true)
                .put("message", "Icon uploaded successfully")
                .put("session_id", sessionId);
            
            JSONBuilder iconInfo = response.createNestedObject("uploaded_icon")
                .put("name", uploadedIcon.getName())
                .put("source", uploadedIcon.getSource())
                .put("path", uploadedIcon.getPath())
                .put("tags", String.join(", ", uploadedIcon.getTags()))
                .put("attribution", uploadedIcon.getAttribution());
            
            outputSuccess(response);
            return 0;
        } else {
            outputError("Icon upload failed: " + result.getErrorMessage());
            return 1;
        }
    }
    
    private int handleIconList(JSONBuilder command, String cacheDir, String sessionId) throws Exception {
        String source = command.getString("source", "all");
        
        // Create repository and list icons
        IconRepository repository = IconRepository.fromEnvironment(cacheDir);
        List<IconRepository.IconAsset> allIcons = repository.getAllAvailableIcons();
        
        // Filter by source if specified
        if (!source.equals("all")) {
            String finalSource = source;
            allIcons = allIcons.stream()
                .filter(icon -> icon.getSource().equals(finalSource))
                .collect(java.util.stream.Collectors.toList());
        }
        
        JSONBuilder response = new JSONBuilder()
            .put("success", true)
            .put("message", "Icon list retrieved")
            .put("session_id", sessionId)
            .put("total_count", allIcons.size())
            .put("devicon_count", repository.getDeviconIconCount())
            .put("local_count", repository.getLocalIconCount())
            .put("freepik_available", repository.hasFreepikApiKey());
        
        List<JSONBuilder> iconsList = new ArrayList<>();
        for (IconRepository.IconAsset icon : allIcons) {
            JSONBuilder iconJson = new JSONBuilder()
                .put("name", icon.getName())
                .put("source", icon.getSource())
                .put("tags", String.join(", ", icon.getTags()))
                .put("attribution", icon.getAttribution());
            
            iconsList.add(iconJson);
        }
        response.putObjectArray("icons", iconsList);
        
        outputSuccess(response);
        return 0;
    }
    
    private ParsedSlideData createMockSlideData() {
        // Create minimal slide data for icon injection
        ShapeRegistry shapeRegistry = new ShapeRegistry();
        TimingTree timingTree = new TimingTree();
        List<AnimationBinding> animationBindings = new ArrayList<>();
        
        return new ParsedSlideData(shapeRegistry, timingTree, animationBindings);
    }
    
    private int handleGenerateContext(CLIArgs args) {
        if (args.sessionId == null) {
            outputError("Missing session ID for generate-context");
            return 1;
        }
        
        try {
            Optional<SessionManager.ManagedSession> sessionOpt = sessionManager.getSession(args.sessionId);
            if (!sessionOpt.isPresent()) {
                outputError("Session not found: " + args.sessionId);
                return 1;
            }
            
            SessionManager.ManagedSession session = sessionOpt.get();
            logger.info("Generating LLM context for session {} (detailed: {})", args.sessionId, args.detailed);
            
            String context = "Context generation not yet implemented";
            
            JSONBuilder response = new JSONBuilder()
                .put("success", true)
                .put("message", "Context generated successfully")
                .put("context", context)
                .put("context_length", context.length())
                .put("session_id", args.sessionId);
            
            outputSuccess(response);
            return 0;
            
        } catch (Exception e) {
            logger.error("Context generation failed: {}", e.getMessage());
            outputError("Context generation failed: " + e.getMessage());
            return 1;
        }
    }
    
    private int handleGetInfo(CLIArgs args) {
        if (args.sessionId == null) {
            outputError("Missing session ID for get-info");
            return 1;
        }
        
        try {
            Optional<SessionManager.ManagedSession> sessionOpt = sessionManager.getSession(args.sessionId);
            if (!sessionOpt.isPresent()) {
                outputError("Session not found: " + args.sessionId);
                return 1;
            }
            
            SessionManager.ManagedSession session = sessionOpt.get();
            Optional<OrchestrationContext> context = session.getOrchestrator().getContext();
            
            JSONBuilder response = new JSONBuilder()
                .put("success", true)
                .put("message", "Information retrieved successfully")
                .put("session_id", args.sessionId);
            
            if (context.isPresent()) {
                JSONBuilder info = response.createNestedObject("info")
                    .put("has_presentation", true)
                    .put("slide_count", session.getSlideCount())
                    .put("pptx_directory", context.get().getDocument() != null && context.get().getDocument().getExtractedDir() != null
                        ? context.get().getDocument().getExtractedDir().getAbsolutePath() : "in-memory");
            } else {
                JSONBuilder info = response.createNestedObject("info")
                    .put("has_presentation", false)
                    .put("slide_count", 0);
            }
            
            outputSuccess(response);
            return 0;
            
        } catch (Exception e) {
            logger.error("Get info failed: {}", e.getMessage());
            outputError("Get info failed: " + e.getMessage());
            return 1;
        }
    }
    
    private int handleValidate(CLIArgs args) {
        if (args.sessionId == null) {
            outputError("Missing session ID for validate");
            return 1;
        }
        
        try {
            Optional<SessionManager.ManagedSession> sessionOpt = sessionManager.getSession(args.sessionId);
            if (!sessionOpt.isPresent()) {
                outputError("Session not found: " + args.sessionId);
                return 1;
            }
            
            SessionManager.ManagedSession session = sessionOpt.get();
            logger.info("Validating presentation in session {}", args.sessionId);
            
            // Perform validation through session orchestrator
            ValidationResult result = session.getOrchestrator().validatePresentation();
            
            JSONBuilder response = new JSONBuilder()
                .put("success", result.isValid())
                .put("message", result.isValid() ? "Presentation is valid" : "Validation failed")
                .put("session_id", args.sessionId);
            
            // Add validation details
            JSONBuilder validation = response.createNestedObject("validation")
                .put("error_count", result.getErrors().size())
                .put("warning_count", result.getWarnings().size());
            
            if (result.isValid()) {
                outputSuccess(response);
                return 0;
            } else {
                outputError(response);
                return 1;
            }
            
        } catch (Exception e) {
            logger.error("Validation failed: {}", e.getMessage());
            outputError("Validation failed: " + e.getMessage());
            return 1;
        }
    }
    
    private void outputSuccess(JSONBuilder response) {
        if (jsonOutput) {
            System.out.println(response.toString());
        } else {
            // Extract message from JSONBuilder for text output
            String message = extractMessage(response.toString());
            System.out.println("SUCCESS: " + message);
        }
    }
    
    private void outputError(String message) {
        if (jsonOutput) {
            JSONBuilder error = new JSONBuilder()
                .put("success", false)
                .put("message", message);
            System.err.println(error.toString());
        } else {
            System.err.println("ERROR: " + message);
        }
    }
    
    private void outputError(JSONBuilder response) {
        if (jsonOutput) {
            System.err.println(response.toString());
        } else {
            String message = extractMessage(response.toString());
            System.err.println("ERROR: " + message);
        }
    }
    
    private void outputMessage(String message) {
        if (jsonOutput) {
            JSONBuilder info = new JSONBuilder()
                .put("success", true)
                .put("message", message);
            System.out.println(info.toString());
        } else {
            System.out.println(message);
        }
    }
    
    private String extractMessage(String jsonString) {
        // Use JSONBuilder's static parser for message extraction
        return JSONBuilder.parseMessage(jsonString);
    }
    
    private CLIArgs parseArguments(String[] args) {
        CLIArgs parsedArgs = new CLIArgs();
        
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            
            switch (arg) {
                case "--json":
                    parsedArgs.jsonOutput = true;
                    break;
                case "--detailed":
                    parsedArgs.detailed = true;
                    break;
                case "--load":
                    parsedArgs.command = "load";
                    if (i + 1 < args.length) {
                        parsedArgs.presentationPath = args[++i];
                    }
                    break;
                case "--save":
                    parsedArgs.command = "save";
                    if (i + 1 < args.length) {
                        parsedArgs.outputPath = args[++i];
                    }
                    break;
                case "--create-new":
                    parsedArgs.command = "create-new";
                    if (i + 1 < args.length) {
                        parsedArgs.outputPath = args[++i];
                    }
                    break;
                case "--llm-command":
                    parsedArgs.command = "llm-command";
                    if (i + 1 < args.length) {
                        parsedArgs.llmCommand = args[++i];
                    }
                    break;
                case "--generate-context":
                    parsedArgs.command = "generate-context";
                    break;
                case "--get-info":
                    parsedArgs.command = "get-info";
                    break;
                case "--validate":
                    parsedArgs.command = "validate";
                    break;
                case "--dump-timing":
                    parsedArgs.command = "dump-timing";
                    if (i + 1 < args.length) {
                        parsedArgs.presentationPath = args[++i];
                    }
                    if (i + 1 < args.length) {
                        parsedArgs.slideSpec = args[++i];
                    }
                    break;
                case "--dump-shape":
                    parsedArgs.command = "dump-shape";
                    if (i + 1 < args.length) {
                        parsedArgs.presentationPath = args[++i];
                    }
                    if (i + 1 < args.length) {
                        parsedArgs.slideSpec = args[++i];
                    }
                    break;
                case "--create-session":
                    parsedArgs.command = "create-session";
                    if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                        parsedArgs.presentationPath = args[++i];
                    }
                    break;
                case "--close-session":
                    parsedArgs.command = "close-session";
                    if (i + 1 < args.length) {
                        parsedArgs.sessionId = args[++i];
                    }
                    break;
                case "--list-sessions":
                    parsedArgs.command = "list-sessions";
                    break;
                case "--session-info":
                    parsedArgs.command = "session-info";
                    if (i + 1 < args.length) {
                        parsedArgs.sessionId = args[++i];
                    }
                    break;
                case "--session":
                    if (i + 1 < args.length) {
                        parsedArgs.sessionId = args[++i];
                    }
                    break;
                case "--presentation":
                    if (i + 1 < args.length) {
                        parsedArgs.presentationPath = args[++i];
                    }
                    break;
                case "--output":
                    if (i + 1 < args.length) {
                        parsedArgs.outputPath = args[++i];
                    }
                    break;
                default:
                    if (!arg.startsWith("--") && parsedArgs.command == null) {
                        parsedArgs.command = arg;
                    }
                    break;
            }
        }
        
        // Default to JSON output for programmatic use
        if (parsedArgs.command != null) {
            parsedArgs.jsonOutput = true;
        }
        
        return parsedArgs;
    }
    
    private int handleDumpTiming(CLIArgs args) {
        try {
            if (args.presentationPath == null) {
                outputError("Presentation path is required for dump-timing command");
                return 1;
            }
            
            String slideSpec = args.slideSpec != null ? args.slideSpec : "all";
            
            // Create a temporary session to extract the PPTX
            File pptxFile = new File(args.presentationPath);
            if (!pptxFile.exists()) {
                outputError("Presentation file not found: " + args.presentationPath);
                return 1;
            }
            
            String sessionId = sessionManager.createSession(pptxFile);
            Optional<SessionManager.ManagedSession> sessionOpt = sessionManager.getSession(sessionId);
            
            if (!sessionOpt.isPresent()) {
                outputError("Failed to create session for timing dump");
                return 1;
            }
            
            SessionManager.ManagedSession session = sessionOpt.get();
            
            try {
                // Get the extracted directory from the session's PPTXDocument
                java.util.Optional<com.excudo.core.orchestration.OrchestrationContext> ctxOpt2 =
                    session.getOrchestrator().getContext();
                File extractedDir = null;
                if (ctxOpt2.isPresent() && ctxOpt2.get().getDocument() != null) {
                    extractedDir = ctxOpt2.get().getDocument().getExtractedDir();
                }
                if (extractedDir == null) {
                    outputError("Timing dump requires an extracted PPTX directory. Not available in in-memory mode.");
                    return 1;
                }
                File slidesDir = new File(extractedDir, "ppt/slides");

                if (!slidesDir.exists()) {
                    outputError("Slides directory not found: " + slidesDir.getAbsolutePath());
                    return 1;
                }
                
                // Parse slide specification
                List<Integer> slidesToDump = parseSlideRange(slideSpec, slidesDir);
                if (slidesToDump.isEmpty()) {
                    outputError("Invalid slide specification: " + slideSpec);
                    return 1;
                }
                
                // Create timing-dumps directory
                File logsDir = new File(".excudo/logs/timing-dumps");
                if (!logsDir.exists()) {
                    logsDir.mkdirs();
                }
                
                // Create timestamp-based subdirectory
                String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
                File sessionDir = new File(logsDir, timestamp);
                sessionDir.mkdirs();
                
                outputMessage("Dumping timing XML structures to: " + sessionDir.getAbsolutePath());
                outputMessage("Output directory: " + sessionDir.getAbsolutePath());
                
                int dumpedCount = 0;
                
                for (int slideNum : slidesToDump) {
                    File slideFile = new File(slidesDir, "slide" + slideNum + ".xml");
                    if (!slideFile.exists()) {
                        outputMessage("Slide " + slideNum + ": File not found");
                        continue;
                    }
                    
                    // Extract timing XML and save to file
                    if (extractTimingXML(slideFile, sessionDir, slideNum)) {
                        dumpedCount++;
                    }
                }
                
                outputMessage("Successfully dumped timing for " + dumpedCount + " slides");
                return 0;
                
            } finally {
                // Clean up session
                sessionManager.closeSession(sessionId);
            }
            
        } catch (Exception e) {
            logger.error("Error during timing dump: {}", e.getMessage());
            outputError("Timing dump failed: " + e.getMessage());
            return 1;
        }
    }
    
    private int handleDumpShape(CLIArgs args) {
        try {
            if (args.presentationPath == null) {
                outputError("Presentation path is required for dump-shape command");
                return 1;
            }
            
            String slideSpec = args.slideSpec != null ? args.slideSpec : "all";
            
            // Create session for presentation access
            SessionManager sessionManager = SessionManager.getInstance();
            String sessionId = sessionManager.createSession(new File(args.presentationPath));
            
            try {
                // Extract and validate presentation
                File extractedDir = new File(args.presentationPath + "_extracted");
                if (!extractedDir.exists()) {
                    outputError("Extracted presentation directory not found: " + extractedDir.getAbsolutePath());
                    outputMessage("Run extraction first: python3 pc.py extract " + args.presentationPath);
                    return 1;
                }
                
                File slidesDir = new File(extractedDir, "ppt/slides");
                if (!slidesDir.exists()) {
                    outputError("Slides directory not found: " + slidesDir.getAbsolutePath());
                    return 1;
                }
                
                // Parse slide specification
                List<Integer> slidesToDump = parseSlideRange(slideSpec, slidesDir);
                if (slidesToDump.isEmpty()) {
                    outputError("Invalid slide specification: " + slideSpec);
                    return 1;
                }
                
                // Create shape-dumps directory
                File logsDir = new File(".excudo/logs/shape-dumps");
                if (!logsDir.exists()) {
                    logsDir.mkdirs();
                }
                
                // Create timestamp-based subdirectory
                String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
                File sessionDir = new File(logsDir, timestamp);
                sessionDir.mkdirs();
                
                outputMessage("Dumping shape XML structures to: " + sessionDir.getAbsolutePath());
                outputMessage("Output directory: " + sessionDir.getAbsolutePath());
                
                int dumpedCount = 0;
                
                for (int slideNum : slidesToDump) {
                    File slideFile = new File(slidesDir, "slide" + slideNum + ".xml");
                    if (!slideFile.exists()) {
                        outputMessage("Slide " + slideNum + ": File not found");
                        continue;
                    }
                    
                    // Extract shape XML and save to files
                    if (extractShapeXML(slideFile, sessionDir, slideNum)) {
                        dumpedCount++;
                    }
                }
                
                outputMessage("Successfully dumped shapes for " + dumpedCount + " slides");
                return 0;
                
            } finally {
                // Clean up session
                sessionManager.closeSession(sessionId);
            }
            
        } catch (Exception e) {
            logger.error("Error during shape dump: {}", e.getMessage());
            outputError("Shape dump failed: " + e.getMessage());
            return 1;
        }
    }
    
    private List<Integer> parseSlideRange(String slideSpec, File slidesDir) {
        List<Integer> slides = new ArrayList<>();
        
        if ("all".equalsIgnoreCase(slideSpec)) {
            // Find all slide files
            File[] slideFiles = slidesDir.listFiles((dir, name) -> 
                name.startsWith("slide") && name.endsWith(".xml"));
            
            if (slideFiles != null) {
                for (File file : slideFiles) {
                    String name = file.getName();
                    try {
                        int slideNum = Integer.parseInt(name.substring(5, name.length() - 4));
                        slides.add(slideNum);
                    } catch (NumberFormatException e) {
                        // Skip invalid slide numbers
                    }
                }
            }
            slides.sort(Integer::compareTo);
        } else if (slideSpec.contains("-")) {
            // Range format: start-end
            String[] parts = slideSpec.split("-");
            if (parts.length == 2) {
                try {
                    int start = Integer.parseInt(parts[0].trim());
                    int end = Integer.parseInt(parts[1].trim());
                    for (int i = start; i <= end; i++) {
                        slides.add(i);
                    }
                } catch (NumberFormatException e) {
                    // Invalid range
                }
            }
        } else {
            // Single slide number
            try {
                slides.add(Integer.parseInt(slideSpec.trim()));
            } catch (NumberFormatException e) {
                // Invalid slide number
            }
        }
        
        return slides;
    }
    
    private boolean extractTimingXML(File slideFile, File outputDir, int slideNum) {
        try {
            // Parse the slide XML
            org.w3c.dom.Document doc = XMLFactoryProvider.parseDocument(slideFile);

            // Find timing root element
            org.w3c.dom.NodeList timingNodes = doc.getElementsByTagName("p:timing");
            if (timingNodes.getLength() == 0) {
                outputMessage("Slide " + slideNum + ": No timing information found");
                return false;
            }
            
            // Get slide title for filename
            String slideTitle = extractSlideTitle(doc);
            String safeTitle = slideTitle.replaceAll("[^a-zA-Z0-9_\\-]", "_");
            String filename = String.format("slide_%03d_%s.xml", slideNum, safeTitle);
            
            File outputFile = new File(outputDir, filename);
            
            // Write timing XML to file
            javax.xml.transform.TransformerFactory transformerFactory = 
                javax.xml.transform.TransformerFactory.newInstance();
            javax.xml.transform.Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            
            javax.xml.transform.dom.DOMSource source = 
                new javax.xml.transform.dom.DOMSource(timingNodes.item(0));
            javax.xml.transform.stream.StreamResult result = 
                new javax.xml.transform.stream.StreamResult(outputFile);
            
            transformer.transform(source, result);
            
            outputMessage("Slide " + slideNum + ": " + filename);
            return true;
            
        } catch (Exception e) {
            outputMessage("Slide " + slideNum + ": Error extracting timing - " + e.getMessage());
            return false;
        }
    }
    
    private boolean extractShapeXML(File slideFile, File outputDir, int slideNum) {
        try {
            // Parse the slide XML
            org.w3c.dom.Document doc = XMLFactoryProvider.parseDocument(slideFile);

            // Find all shape elements (p:sp, p:pic, p:grpSp)
            org.w3c.dom.NodeList spNodes = doc.getElementsByTagName("p:sp");
            org.w3c.dom.NodeList picNodes = doc.getElementsByTagName("p:pic");
            org.w3c.dom.NodeList grpSpNodes = doc.getElementsByTagName("p:grpSp");
            
            int totalShapes = spNodes.getLength() + picNodes.getLength() + grpSpNodes.getLength();
            if (totalShapes == 0) {
                outputMessage("Slide " + slideNum + ": No shapes found");
                return false;
            }
            
            // Get slide title for filename prefix
            String slideTitle = extractSlideTitle(doc);
            String safeTitle = slideTitle.replaceAll("[^a-zA-Z0-9_\\-]", "_");
            
            outputMessage("Slide " + slideNum + ": Found " + totalShapes + " shapes");
            
            int shapeCount = 0;
            
            // Extract text shapes (p:sp)
            for (int i = 0; i < spNodes.getLength(); i++) {
                org.w3c.dom.Element shape = (org.w3c.dom.Element) spNodes.item(i);
                String shapeId = extractShapeId(shape);
                String shapeName = extractShapeName(shape);
                boolean isPlaceholder = isPlaceholderShape(shape);
                
                String filename = String.format("slide_%03d_%s_shape_%s_%s_%s.xml", 
                    slideNum, safeTitle, shapeId, 
                    isPlaceholder ? "placeholder" : "regular",
                    shapeName.replaceAll("[^a-zA-Z0-9_\\-]", "_"));
                
                if (writeShapeXML(shape, new File(outputDir, filename), slideNum, shapeId, shapeName, isPlaceholder)) {
                    shapeCount++;
                }
            }
            
            // Extract picture shapes (p:pic)
            for (int i = 0; i < picNodes.getLength(); i++) {
                org.w3c.dom.Element shape = (org.w3c.dom.Element) picNodes.item(i);
                String shapeId = extractShapeId(shape);
                String shapeName = extractShapeName(shape);
                
                String filename = String.format("slide_%03d_%s_pic_%s_%s.xml", 
                    slideNum, safeTitle, shapeId, 
                    shapeName.replaceAll("[^a-zA-Z0-9_\\-]", "_"));
                
                if (writeShapeXML(shape, new File(outputDir, filename), slideNum, shapeId, shapeName, false)) {
                    shapeCount++;
                }
            }
            
            // Extract group shapes (p:grpSp)
            for (int i = 0; i < grpSpNodes.getLength(); i++) {
                org.w3c.dom.Element shape = (org.w3c.dom.Element) grpSpNodes.item(i);
                String shapeId = extractShapeId(shape);
                String shapeName = extractShapeName(shape);
                
                String filename = String.format("slide_%03d_%s_group_%s_%s.xml", 
                    slideNum, safeTitle, shapeId, 
                    shapeName.replaceAll("[^a-zA-Z0-9_\\-]", "_"));
                
                if (writeShapeXML(shape, new File(outputDir, filename), slideNum, shapeId, shapeName, false)) {
                    shapeCount++;
                }
            }
            
            outputMessage("Slide " + slideNum + ": Extracted " + shapeCount + " shapes");
            return shapeCount > 0;
            
        } catch (Exception e) {
            outputMessage("Slide " + slideNum + ": Error extracting shapes - " + e.getMessage());
            return false;
        }
    }
    
    private boolean writeShapeXML(org.w3c.dom.Element shape, File outputFile, 
                                 int slideNum, String shapeId, String shapeName, boolean isPlaceholder) {
        try {
            // Create a new document for the shape with metadata
            org.w3c.dom.Document newDoc = XMLFactoryProvider.createDocument();
            
            // Create root element with metadata
            org.w3c.dom.Element root = newDoc.createElement("shape-dump");
            root.setAttribute("slide", String.valueOf(slideNum));
            root.setAttribute("shape-id", shapeId);
            root.setAttribute("shape-name", shapeName);
            root.setAttribute("is-placeholder", String.valueOf(isPlaceholder));
            root.setAttribute("shape-type", shape.getLocalName());
            root.setAttribute("generated-by", ".excudo");
            root.setAttribute("timestamp", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()));
            
            // Import and append the shape XML
            org.w3c.dom.Node importedShape = newDoc.importNode(shape, true);
            root.appendChild(importedShape);
            newDoc.appendChild(root);
            
            // Write to file with pretty formatting
            javax.xml.transform.TransformerFactory transformerFactory = 
                javax.xml.transform.TransformerFactory.newInstance();
            javax.xml.transform.Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            
            javax.xml.transform.dom.DOMSource source = new javax.xml.transform.dom.DOMSource(newDoc);
            javax.xml.transform.stream.StreamResult result = 
                new javax.xml.transform.stream.StreamResult(outputFile);
            
            transformer.transform(source, result);
            return true;
            
        } catch (Exception e) {
            outputMessage("Failed to write shape " + shapeId + ": " + e.getMessage());
            return false;
        }
    }
    
    private String extractShapeId(org.w3c.dom.Element shape) {
        try {
            // Look for id attribute in cNvPr element
            org.w3c.dom.NodeList cNvPrNodes = shape.getElementsByTagName("p:cNvPr");
            if (cNvPrNodes.getLength() > 0) {
                org.w3c.dom.Element cNvPr = (org.w3c.dom.Element) cNvPrNodes.item(0);
                return cNvPr.getAttribute("id");
            }
            return "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }
    
    private String extractShapeName(org.w3c.dom.Element shape) {
        try {
            // Look for name attribute in cNvPr element
            org.w3c.dom.NodeList cNvPrNodes = shape.getElementsByTagName("p:cNvPr");
            if (cNvPrNodes.getLength() > 0) {
                org.w3c.dom.Element cNvPr = (org.w3c.dom.Element) cNvPrNodes.item(0);
                String name = cNvPr.getAttribute("name");
                return name.isEmpty() ? "unnamed" : name;
            }
            return "unnamed";
        } catch (Exception e) {
            return "unnamed";
        }
    }
    
    private boolean isPlaceholderShape(org.w3c.dom.Element shape) {
        try {
            // Look for p:ph element in nvPr
            org.w3c.dom.NodeList nvPrNodes = shape.getElementsByTagName("p:nvPr");
            if (nvPrNodes.getLength() > 0) {
                org.w3c.dom.Element nvPr = (org.w3c.dom.Element) nvPrNodes.item(0);
                org.w3c.dom.NodeList phNodes = nvPr.getElementsByTagName("p:ph");
                return phNodes.getLength() > 0;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
    
    private String extractSlideTitle(org.w3c.dom.Document doc) {
        try {
            // Try to find slide title in various locations
            org.w3c.dom.NodeList titleNodes = doc.getElementsByTagName("a:t");
            if (titleNodes.getLength() > 0) {
                String title = titleNodes.item(0).getTextContent().trim();
                if (!title.isEmpty()) {
                    return title;
                }
            }
            
            // Fallback to generic title
            return "no_title";
            
        } catch (Exception e) {
            return "no_title";
        }
    }
    
    private static class CLIArgs {
        String command;
        String sessionId;
        String presentationPath;
        String outputPath;
        String llmCommand;
        String slideSpec;  // For dump-timing: slide number, range, or "all"
        boolean jsonOutput = false;
        boolean detailed = false;
    }
}