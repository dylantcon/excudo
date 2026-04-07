package com.excudo.console;

import com.excudo.core.llm.*;
import com.excudo.core.config.ConfigurationManager;
import com.excudo.core.orchestration.*;
import com.excudo.core.commands.CommandInvoker;
import com.excudo.core.commands.LLMHandler;
import com.excudo.core.results.ExecutionResult;
import com.excudo.core.validation.ValidationResult;
import com.excudo.core.utils.Logger;
import com.excudo.core.utils.ComponentLogger;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import java.io.*;
import java.util.*;

/**
 * Console handler for LLM-powered presentation editing.
 * Supports provider-agnostic LLM interaction via LLMClient abstraction.
 */
public class LLMConsoleHandler implements LLMHandler {

    private static final ComponentLogger logger = Logger.console();
    private final PPTXOrchestrator orchestrator;
    private final ConfigurationManager configManager;
    public final LLMIntegrationService llmService;
    private LLMClient apiClient;
    private boolean verboseMode = false;

    public LLMConsoleHandler(PPTXOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
        this.configManager = new ConfigurationManager();
        this.llmService = new LLMIntegrationService();
        initializeAPIClient();
    }

    /**
     * Initialize the API client using provider-agnostic configuration,
     * falling back to Anthropic environment variables for backward compatibility.
     */
    private void initializeAPIClient() {
        try {
            apiClient = LLMClient.fromConfiguration(configManager);
            return;
        } catch (Exception e) {
            logger.debug("Provider-based init failed: {}", e.getMessage());
        }

        try {
            apiClient = AnthropicAPIClient.fromEnvironment();
            return;
        } catch (IllegalStateException e) {
            // Environment variable not set either
        }

        if (configManager.hasApiKey()) {
            apiClient = new AnthropicAPIClient(configManager.getApiKey(), configManager.getModel());
        }
    }

    public void handleCommand(String command, Scanner scanner, CommandInvoker commandInvoker) {
        String[] parts = command.split("\\s+", 2);
        String subCommand = parts[0].toLowerCase();

        switch (subCommand) {
            case "help":
                showHelp();
                break;

            case "config":
                handleConfig(parts.length > 1 ? parts[1] : "", scanner);
                break;

            case "edit":
                if (parts.length > 1) {
                    handleEdit(parts[1], scanner, commandInvoker);
                } else {
                    System.out.println("Please provide an edit request. Example: llm edit add fade animation to all titles");
                }
                break;

            case "analyze":
                handleAnalyze(parts.length > 1 ? parts[1] : "all");
                break;

            case "suggest":
                handleSuggest();
                break;

            case "verbose":
                verboseMode = !verboseMode;
                System.out.println("Verbose mode: " + (verboseMode ? "ON" : "OFF"));
                break;

            default:
                System.out.println("Unknown LLM command: " + subCommand);
                System.out.println("Type 'llm help' for available commands");
        }
    }

    private void showHelp() {
        System.out.println("""
            LLM Commands:

            llm config              - Show configuration status
            llm config set          - Set API key interactively
            llm config clear        - Clear stored API key
            llm config provider <n> - Set LLM provider (anthropic, ollama, openai, gemini)

            llm edit <request>      - Edit presentation using natural language
            llm analyze [slide#]    - Analyze current presentation or specific slide
            llm suggest             - Get AI suggestions for improvements
            llm verbose             - Toggle verbose output

            Examples:
              llm edit add a fade animation to the title on slide 1
              llm edit change all bullet points to appear one by one
              llm edit create a new slide after slide 3 with a comparison chart
              llm config provider ollama
              llm analyze 2
              llm suggest
            """);
    }

    private void handleConfig(String action, Scanner scanner) {
        String[] actionParts = action.split("\\s+", 2);
        String subAction = actionParts[0].toLowerCase();

        switch (subAction) {
            case "set":
                System.out.print("Enter your API key: ");
                String apiKey = scanner.nextLine().trim();
                if (!apiKey.isEmpty()) {
                    configManager.setApiKey(apiKey);
                    initializeAPIClient();
                    System.out.println("API key saved successfully!");
                }
                break;

            case "clear":
                configManager.clearApiKey();
                apiClient = null;
                System.out.println("API key cleared.");
                break;

            case "provider":
                if (actionParts.length > 1) {
                    String provider = actionParts[1].trim().toLowerCase();
                    configManager.setProviderName(provider);
                    System.out.println("LLM provider set to: " + provider);
                    initializeAPIClient();
                    if (apiClient != null) {
                        System.out.println("Client initialized: " + apiClient.getProviderName()
                            + " (model: " + apiClient.getModel() + ")");
                    } else {
                        System.out.println("API key not configured for provider '" + provider + "'.");
                        System.out.println("Use 'llm config set' or set the appropriate environment variable.");
                    }
                } else {
                    System.out.println("Current provider: " + configManager.getProviderName());
                    System.out.println("Supported: anthropic, ollama, openai, gemini");
                    System.out.println("Usage: llm config provider <name>");
                }
                break;

            default:
                System.out.println(configManager.getConfigurationStatus());
                System.out.println();
                System.out.println(configManager.getConfigurationInstructions());
        }
    }

    private void handleEdit(String request, Scanner scanner, CommandInvoker commandInvoker) {
        if (apiClient == null) {
            System.out.println("API key not configured. Use 'llm config set' to configure.");
            return;
        }

        if (apiClient.isLocalModel()) {
            // Local models use single-shot path: propose -> approve -> execute
            handleEditSingleShot(request, scanner, commandInvoker);
        } else {
            // Cloud models use agentic multi-turn path
            System.out.println("Using agentic mode (selective context retrieval)...");
            handleEditAgentic(request, commandInvoker);
        }
    }

    /**
     * Single-shot edit path: dump full context, LLM returns a JSON command batch,
     * user approves, then execute. Better for local/small models that can't handle
     * multi-turn tool calling reliably.
     */
    private void handleEditSingleShot(String request, Scanner scanner, CommandInvoker commandInvoker) {
        System.out.println("Analyzing presentation...");

        try {
            LLMHandler.LLMEditProposal proposal = processEditRequest(request);

            if (proposal == null) {
                logger.error("Failed to generate proposal");
                return;
            }

            System.out.println("\nProposed operations:");
            System.out.println(proposal.getOperationsSummary());

            if (verboseMode) {
                System.out.println("\n=== JSON COMMAND ===");
                System.out.println(proposal.getJsonCommand());
                System.out.println("=== END JSON ===\n");
            }

            boolean autoApprove = "true".equalsIgnoreCase(System.getenv("PC_AUTO_APPROVE"));
            String confirm;

            if (autoApprove) {
                System.out.println("\nAuto-approving proposal (PC_AUTO_APPROVE=true)...");
                confirm = "y";
            } else {
                System.out.print("\nExecute? (y/n): ");
                confirm = scanner.nextLine().trim().toLowerCase();
            }

            if (confirm.equals("y") || confirm.equals("yes")) {
                System.out.println("Executing...");
                BatchExecutionResult result = executeApprovedProposal(proposal, commandInvoker);

                if (result.isSuccess()) {
                    System.out.println("[OK] Completed: " + result.getSuccessfulOperations()
                        + "/" + result.getTotalOperations() + " operations");
                } else {
                    logger.error("Failed: {} of {} operations failed",
                        result.getFailedOperations(), result.getTotalOperations());
                }
            } else {
                System.out.println("Cancelled.");
            }

        } catch (Exception e) {
            logger.error("Edit failed: {}", e.getMessage());
            if (verboseMode) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Agentic edit path: the LLM selectively retrieves context via tool calls,
     * then submits commands when it has enough information. This replaces the
     * monolithic context dump and eliminates the manual approve/reject step -
     * the LLM executes directly within its tool loop.
     */
    private void handleEditAgentic(String request, CommandInvoker commandInvoker) {
        try {
            com.excudo.core.commands.CommandFactory commandFactory =
                new com.excudo.core.commands.CommandFactory(orchestrator);

            com.excudo.core.llm.AgenticLLMService.AgenticResult result =
                llmService.processRequestAgenticWithUsage(
                    request, orchestrator, apiClient, commandFactory, commandInvoker);

            System.out.println();
            System.out.println(result.summary());
            if (result.inputTokens() > 0 || result.outputTokens() > 0) {
                System.out.println(com.excudo.console.utils.ConsoleColors.dim(
                    "Tokens: " + result.inputTokens() + " in / " + result.outputTokens() + " out"));
            }
        } catch (Exception e) {
            logger.error("Agentic edit failed: {}", e.getMessage());
        }
    }

    private void handleAnalyze(String target) {
        System.out.println("Analyzing presentation...");

        try {
            if (target.equals("all")) {
                String context = llmService.generateEnhancedLLMContext(orchestrator, true);
                System.out.println("\n=== PRESENTATION ANALYSIS ===");
                System.out.println(context);
            } else {
                try {
                    int slideNum = Integer.parseInt(target);
                    String context = llmService.generateSlideContext(orchestrator, List.of(slideNum));
                    System.out.println("\n=== SLIDE " + slideNum + " ANALYSIS ===");
                    System.out.println(context);
                } catch (NumberFormatException e) {
                    logger.warn("Invalid slide number: {}", target);
                }
            }
        } catch (Exception e) {
            logger.error("Error analyzing presentation: {}", e.getMessage());
        }
    }

    private void handleSuggest() {
        if (apiClient == null) {
            System.out.println("API key not configured. Use 'llm config set' to configure.");
            return;
        }

        System.out.println("Analyzing presentation for improvement suggestions...");

        try {
            String context = llmService.generateEnhancedLLMContext(orchestrator, false);

            String request = """
                Please analyze this presentation and suggest improvements for:
                1. Visual consistency
                2. Animation flow
                3. Content organization
                4. Professional appearance

                Provide specific, actionable suggestions.
                """;

            ExecutionResult<String> apiResult = apiClient.sendMessage(
                "You are a presentation design expert. Analyze presentations and suggest improvements.",
                request + "\n\nPresentation context:\n" + context
            );

            if (apiResult.isSuccess()) {
                System.out.println("\n=== AI SUGGESTIONS ===");
                System.out.println(apiResult.getData().orElse("No suggestions available"));
                System.out.println("=== END SUGGESTIONS ===");
            } else {
                logger.error("Failed to get suggestions: {}", apiResult.getMessage());
            }

        } catch (Exception e) {
            logger.error("Error getting suggestions: {}", e.getMessage());
        }
    }

    private String extractJsonFromResponse(String response) {
        // Try markdown fence first
        int startMarker = response.indexOf("```json");
        if (startMarker != -1) {
            int start = response.indexOf("\n", startMarker) + 1;
            int end = response.indexOf("```", start);
            if (end > start) {
                String candidate = response.substring(start, end).trim();
                try {
                    JsonParser.parseString(candidate);
                    return candidate;
                } catch (JsonSyntaxException ignored) {}
            }
        }

        // Fallback: find first valid JSON object in the response
        int jsonStart = response.indexOf("{");
        if (jsonStart != -1) {
            // Try progressively larger substrings starting from first {
            for (int i = response.length() - 1; i > jsonStart; i--) {
                if (response.charAt(i) == '}') {
                    String candidate = response.substring(jsonStart, i + 1);
                    try {
                        JsonParser.parseString(candidate);
                        return candidate;
                    } catch (JsonSyntaxException ignored) {}
                }
            }
        }

        return null;
    }

    @Override
    public LLMHandler.LLMEditProposal processEditRequest(String request) throws Exception {
        if (apiClient == null) {
            throw new Exception("API key not configured. Please set your API key or use 'llm config set'.");
        }

        boolean compact = apiClient != null && apiClient.isLocalModel();
        String context = compact
            ? llmService.generateCompactLLMContext(orchestrator)
            : llmService.generateEnhancedLLMContext(orchestrator, true);

        if (verboseMode) {
            System.out.println("Context size: " + context.length() + " chars"
                + (compact ? " (compact)" : " (full)"));
        }

        ExecutionResult<String> apiResult = apiClient.sendPowerPointEditRequest(context, request);

        if (!apiResult.isSuccess()) {
            throw new Exception("API request failed: " + apiResult.getMessage());
        }

        String llmResponse = apiResult.getData().orElse("");

        if (verboseMode) {
            System.out.println("\n=== RAW LLM RESPONSE ===");
            System.out.println(llmResponse);
            System.out.println("=== END RAW RESPONSE ===\n");
        }

        String jsonCommand = extractJsonFromResponse(llmResponse);
        if (jsonCommand == null) {
            String snippet = llmResponse.length() > 500
                ? llmResponse.substring(0, 500) + "..."
                : llmResponse;
            throw new Exception("Could not extract valid JSON from LLM response:\n" + snippet);
        }

        // Pre-validate the extracted JSON before presenting to user.
        // If validation fails, silently reprompt the LLM once with the errors.
        RequestParser preValidator = new RequestParser();
        ExecutionResult<com.excudo.core.commands.RequestSchema.LLMRequest> preValidation =
            preValidator.parseRequest(jsonCommand);

        if (!preValidation.isSuccess()) {
            String validationErrors = preValidation.getMessage();
            logger.debug("LLM response failed validation, attempting reprompt: {}", validationErrors);

            String correctionRequest = request + "\n\n"
                + "[SYSTEM: Your previous response was rejected due to validation errors. "
                + "Fix the following issues and respond with corrected JSON only.]\n"
                + "Errors: " + validationErrors + "\n"
                + "Your previous JSON:\n" + jsonCommand;

            ExecutionResult<String> retryResult = apiClient.sendPowerPointEditRequest(context, correctionRequest);

            if (retryResult.isSuccess()) {
                String retryResponse = retryResult.getData().orElse("");
                String retryJson = extractJsonFromResponse(retryResponse);

                if (retryJson != null) {
                    ExecutionResult<com.excudo.core.commands.RequestSchema.LLMRequest> retryValidation =
                        preValidator.parseRequest(retryJson);

                    if (retryValidation.isSuccess()) {
                        // Retry succeeded -- use corrected JSON transparently
                        jsonCommand = retryJson;
                        llmResponse = retryResponse;
                        logger.debug("Reprompt succeeded, using corrected response");
                    } else {
                        // Retry also failed -- surface both errors
                        throw new Exception("LLM response failed validation after retry.\n"
                            + "Original errors: " + validationErrors + "\n"
                            + "Retry errors: " + retryValidation.getMessage());
                    }
                } else {
                    throw new Exception("LLM response failed validation: " + validationErrors + "\n"
                        + "Retry did not produce valid JSON.");
                }
            } else {
                throw new Exception("LLM response failed validation: " + validationErrors + "\n"
                    + "Retry API call failed: " + retryResult.getMessage());
            }
        }

        String operationsSummary = parseOperationsForPreview(jsonCommand);

        return new LLMHandler.LLMEditProposal(request, jsonCommand, llmResponse, operationsSummary);
    }

    @Override
    public BatchExecutionResult executeApprovedProposal(LLMHandler.LLMEditProposal proposal, CommandInvoker commandInvoker) throws Exception {
        if (apiClient == null) {
            throw new Exception("API client not initialized");
        }

        return llmService.processLLMRequests(proposal.getJsonCommand(), orchestrator, commandInvoker);
    }

    @Override
    public boolean isAgenticAvailable() {
        return apiClient != null;
    }

    @Override
    public boolean isLocalModel() {
        return apiClient != null && apiClient.isLocalModel();
    }

    @Override
    public String processEditRequestAgentic(String request, CommandInvoker commandInvoker) throws Exception {
        if (apiClient == null) {
            throw new Exception("LLM client not initialized. Use 'llm config set' to configure.");
        }
        com.excudo.core.commands.CommandFactory commandFactory =
            new com.excudo.core.commands.CommandFactory(orchestrator);
        return llmService.processRequestAgentic(
            request, orchestrator, apiClient, commandFactory, commandInvoker);
    }

    /**
     * Process agentic request and return result with token usage.
     */
    public AgenticLLMService.AgenticResult processEditRequestAgenticWithUsage(
            String request, CommandInvoker commandInvoker) throws Exception {
        return processEditRequestAgenticWithUsage(request, commandInvoker, null, null);
    }

    public AgenticLLMService.AgenticResult processEditRequestAgenticWithUsage(
            String request, CommandInvoker commandInvoker,
            com.excudo.core.commands.CommandDisplay displayAdapter,
            AgenticLLMService.ProgressListener progressListener) throws Exception {
        if (apiClient == null) {
            throw new Exception("LLM client not initialized. Use 'llm config set' to configure.");
        }
        com.excudo.core.commands.CommandFactory commandFactory =
            new com.excudo.core.commands.CommandFactory(orchestrator);
        return llmService.processRequestAgenticWithUsage(
            request, orchestrator, apiClient, commandFactory, commandInvoker,
            (com.excudo.core.commands.CommandDisplay) displayAdapter, progressListener);
    }

    @Override
    public String generateContext(PPTXOrchestrator orchestrator, boolean detailed) throws Exception {
        return llmService.generateEnhancedLLMContext(orchestrator, detailed);
    }

    @Override
    public void handleConfigAction(String action) {
        String subAction = (action == null || action.trim().isEmpty()) ? "show" : action.trim().split("\\s+", 2)[0].toLowerCase();
        String[] parts = action != null ? action.trim().split("\\s+", 2) : new String[]{"show"};

        switch (subAction) {
            case "set":
                if (parts.length > 1 && !parts[1].trim().isEmpty()) {
                    configManager.setApiKey(parts[1].trim());
                    initializeAPIClient();
                    System.out.println("API key saved successfully!");
                    if (apiClient != null) {
                        System.out.println("Client initialized: " + apiClient.getProviderName()
                            + " (model: " + apiClient.getModel() + ")");
                    }
                } else {
                    System.out.println("Usage: llm config set <api-key>");
                }
                break;
            case "clear":
                configManager.clearApiKey();
                apiClient = null;
                System.out.println("API key cleared.");
                break;
            case "provider":
                if (parts.length > 1 && !parts[1].trim().isEmpty()) {
                    String provider = parts[1].trim().toLowerCase();
                    configManager.setProviderName(provider);
                    System.out.println("LLM provider set to: " + provider);
                    initializeAPIClient();
                    if (apiClient != null) {
                        System.out.println("Client initialized: " + apiClient.getProviderName()
                            + " (model: " + apiClient.getModel() + ")");
                    } else {
                        System.out.println("API key not configured for provider '" + provider + "'.");
                        System.out.println("Use 'llm config set <key>' or set the appropriate environment variable.");
                    }
                } else {
                    System.out.println("Current provider: " + configManager.getProviderName());
                    System.out.println("Supported: anthropic, ollama, openai, gemini");
                    System.out.println("Usage: llm config provider <name>");
                }
                break;
            case "env":
                if (parts.length > 1 && !parts[1].trim().isEmpty()) {
                    String envPath = parts[1].trim();
                    int count = com.excudo.core.utils.EnvLoader.loadFromPath(envPath);
                    if (count < 0) {
                        System.out.println("File not found: " + envPath);
                    } else {
                        System.out.println("Loaded env file: " + envPath + " (" + count + " new variables)");
                        // If the env file specifies LLM_PROVIDER, update the config manager
                        // so it takes precedence over the persisted properties file.
                        String envProvider = com.excudo.core.utils.EnvLoader.get("LLM_PROVIDER");
                        if (envProvider != null && !envProvider.trim().isEmpty()) {
                            configManager.setProviderName(envProvider.trim());
                            System.out.println("Provider set from env file: " + envProvider.trim());
                        }
                        initializeAPIClient();
                        if (apiClient != null) {
                            System.out.println("Client initialized: " + apiClient.getProviderName()
                                + " (model: " + apiClient.getModel() + ")");
                        } else {
                            System.out.println("No API key found in env file. Expected key: "
                                + configManager.getProviderName().toUpperCase() + "_API_KEY");
                        }
                    }
                } else {
                    System.out.println("Usage: llm config env <path-to-.env-file>");
                }
                break;
            case "show":
            default:
                System.out.println(configManager.getConfigurationStatus());
                System.out.println();
                System.out.println(configManager.getConfigurationInstructions());
                break;
        }
    }

    private String parseOperationsForPreview(String jsonCommand) {
        try {
            StringBuilder preview = new StringBuilder();

            if (jsonCommand.contains("\"operations\"")) {
                int slideCreations = countOccurrences(jsonCommand, "\"type\":\\s*\"slide-creation\"");
                int contentEdits = countOccurrences(jsonCommand, "\"type\":\\s*\"content-edit\"");
                int animationEdits = countOccurrences(jsonCommand, "\"type\":\\s*\"animation-edit\"");

                if (slideCreations > 0) preview.append("- ").append(slideCreations).append(" slide creation(s)\n");
                if (contentEdits > 0) preview.append("- ").append(contentEdits).append(" content edit(s)\n");
                if (animationEdits > 0) preview.append("- ").append(animationEdits).append(" animation edit(s)\n");

                String[] lines = jsonCommand.split("\n");
                for (String line : lines) {
                    if (line.contains("\"description\":")) {
                        String desc = line.replaceAll(".*\"description\":\\s*\"([^\"]+)\".*", "$1");
                        if (!desc.equals(line)) {
                            preview.append("  - ").append(desc).append("\n");
                        }
                    }
                }
            }

            return preview.length() > 0 ? preview.toString() : "Operations detected in JSON command";

        } catch (Exception e) {
            return "Could not parse operations: " + e.getMessage();
        }
    }

    private int countOccurrences(String text, String pattern) {
        return text.split(pattern, -1).length - 1;
    }
}
