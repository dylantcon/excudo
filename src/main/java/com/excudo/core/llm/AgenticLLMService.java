package com.excudo.core.llm;

import com.excudo.core.commands.mutating.slide.AddShapeCommand;
import com.excudo.core.commands.*;
import com.excudo.core.orchestration.*;
import com.excudo.core.services.ContextService;
import com.excudo.core.utils.JsonHelper;
import com.excudo.core.utils.Logger;
import com.excudo.core.utils.ComponentLogger;
import com.google.gson.*;
import java.io.File;
import java.util.*;

/**
 * Agentic LLM service that uses the Anthropic tool_use protocol for selective context retrieval.
 *
 * Replaces the monolithic 30-50KB context dump in LLMIntegrationService with a multi-turn
 * conversation loop. The LLM calls context tools on demand, then submits commands via
 * execute_commands when ready. This reduces token usage and improves response accuracy
 * because the LLM only inspects the parts of the presentation it actually needs.
 *
 * Workflow per request:
 *   1. Send user request with tool definitions
 *   2. LLM calls get_presentation_overview (and other tools as needed)
 *   3. Dispatch tool, append result to conversation
 *   4. When LLM calls execute_commands, execute and return summary
 *   5. LLM responds with plain text once done
 */
public class AgenticLLMService {

    private static final ComponentLogger logger = Logger.llm();
    private static final int MAX_ROUND_TRIPS = 25;
    private static final int MAX_MODEL_RETRIES = 3;
    private static final Gson GSON = new Gson();

    /**
     * Result of an agentic processing run, including token usage statistics.
     */
    public record AgenticResult(String summary, int inputTokens, int outputTokens, double cost) {
        /** Backward-compatible constructor for providers without cost tracking. */
        public AgenticResult(String summary, int inputTokens, int outputTokens) {
            this(summary, inputTokens, outputTokens, 0.0);
        }
    }

    /**
     * Callback for progress updates during the agentic loop.
     */
    public interface ProgressListener {
        void onProgress(int round, int maxRounds, String toolName);

        /**
         * Progress with optional detail string (e.g. individual command names
         * within execute_commands). Default delegates to the 3-arg version.
         */
        default void onProgress(int round, int maxRounds, String toolName, String detail) {
            onProgress(round, maxRounds, toolName);
        }
    }

    public static final Map<String, String> TOOL_LABELS = Map.ofEntries(
        Map.entry("get_presentation_overview", "Inspecting presentation"),
        Map.entry("get_slide_shapes", "Reading shapes"),
        Map.entry("get_shape_detail", "Inspecting shape"),
        Map.entry("get_available_layouts", "Checking layouts"),
        Map.entry("get_command_schemas", "Loading command reference"),
        Map.entry("get_slide_animations", "Reading animations"),
        Map.entry("execute_commands", "Executing commands"),
        Map.entry("validate_layout", "Validating layout"),
        Map.entry("create_code_box", "Creating code box"),
        Map.entry("create_diagram", "Rendering diagram"),
        Map.entry("suggest_layout", "Suggesting layout"),
        Map.entry("create_slide_from_layout", "Creating slide"),
        Map.entry("inject_icon", "Injecting icon"),
        Map.entry("render_slide", "Rendering slide"),
        Map.entry("fetch_tool_schemas", "Loading tool schemas"),
        Map.entry("retry", "Retrying")
    );

    private final LLMClient llmClient;
    private PPTXOrchestrator orchestrator;
    private final CommandFactory commandFactory;
    private final CommandInvoker commandInvoker;
    private final ToolDispatcher toolDispatcher;
    private ProgressListener progressListener;
    private CommandDisplay displayAdapter;

    // Cumulative token and cost tracking across multi-turn conversation
    private int cumulativeInputTokens = 0;
    private int cumulativeOutputTokens = 0;
    private double cumulativeCost = 0.0;

    // Session-level guard: once 'new' has been called, block all subsequent 'new' calls
    private boolean presentationCreated = false;

    // Training data capture
    private List<Map<String, Object>> lastConversation;
    private String lastSystemPrompt;
    private String lastModelId;

    public void setProgressListener(ProgressListener listener) {
        this.progressListener = listener;
    }

    public void setDisplayAdapter(CommandDisplay adapter) {
        this.displayAdapter = adapter;
        this.toolDispatcher.setDisplayAdapter(adapter);
    }

    public AgenticLLMService(LLMClient llmClient, PPTXOrchestrator orchestrator,
                              CommandFactory commandFactory, CommandInvoker commandInvoker) {
        this.llmClient = llmClient;
        this.orchestrator = orchestrator;
        this.commandFactory = commandFactory;
        this.commandInvoker = commandInvoker;
        this.toolDispatcher = new ToolDispatcher(orchestrator, commandFactory, commandInvoker);
    }

    public int getCumulativeInputTokens() { return cumulativeInputTokens; }
    public int getCumulativeOutputTokens() { return cumulativeOutputTokens; }

    private ContextService getContextService() {
        return orchestrator.getContextService();
    }

    /**
     * Process a user request using the agentic multi-turn approach.
     *
     * @param userRequest the raw natural-language request from the user
     * @return a human-readable summary of what was done, or an error message
     */
    public String processRequest(String userRequest) {
        AgenticResult result = processRequestWithUsage(userRequest);
        return result.summary();
    }

    /**
     * Process a user request and return both the summary and token usage.
     */
    public AgenticResult processRequestWithUsage(String userRequest) {
        AgenticResult result = processRequestInternal(userRequest);
        dumpTrainingData();
        return result;
    }

    private AgenticResult processRequestInternal(String userRequest) {
        boolean smallModel = llmClient.isLocalModel();
        boolean hasPresentation = getContextService() != null;
        presentationCreated = hasPresentation; // Lock 'new' if presentation already exists
        toolDispatcher.setPresentationCreated(hasPresentation);
        String systemPrompt = buildSystemPrompt(smallModel);

        // If no presentation is loaded, tell the model to create one first
        if (!hasPresentation) {
            userRequest = "[No presentation loaded. Use the 'new' command (via execute_commands) "
                + "to create one before any other commands.]\n\n" + userRequest;
        }
        int maxRounds = smallModel ? 10 : MAX_ROUND_TRIPS;

        // For small models: use flat tool set (no deferred pattern).
        // For full models: start with core tools only; deferred tools are discovered on demand.
        Set<String> activeToolNames = new LinkedHashSet<>();
        String toolsJson;
        if (smallModel) {
            toolsJson = LLMToolDefinitions.toSmallModelToolsJson();
        } else {
            toolsJson = LLMToolDefinitions.buildToolsJson(activeToolNames);
        }
        boolean toolsJsonDirty = false;

        // Conversation history - starts with the user request as a simple string message
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", userRequest));

        // Capture for training data export
        this.lastSystemPrompt = systemPrompt;
        this.lastModelId = llmClient.getModel();
        this.lastConversation = messages; // Same reference -- will grow during the loop

        int modelRetries = 0;
        for (int round = 0; round < maxRounds; round++) {
            // Rebuild tools JSON if new schemas were discovered last round
            if (toolsJsonDirty && !smallModel) {
                toolsJson = LLMToolDefinitions.buildToolsJson(activeToolNames);
                toolsJsonDirty = false;
                logger.info("Rebuilt tools JSON with {} discovered tool(s): {}", activeToolNames.size(), activeToolNames);
            }

            logger.info("Agentic round {}/{}", round + 1, maxRounds);

            // Compact old tool results to reduce quadratic token growth.
            // Keep last 2 rounds verbatim; summarize everything older.
            List<Map<String, Object>> compactedMessages = compactConversationHistory(messages, round);

            List<LLMClient.APIResponse> responses = llmClient.sendMessageWithTools(
                systemPrompt, flattenMessages(compactedMessages), toolsJson);

            // Track token usage from this round
            accumulateTokenUsage();

            if (responses == null || responses.isEmpty()) {
                return new AgenticResult("Error: No response from LLM",
                    cumulativeInputTokens, cumulativeOutputTokens, cumulativeCost);
            }

            // Check for model-side errors (e.g. Gemini MALFORMED_FUNCTION_CALL).
            // These are retryable -- the model failed to generate valid output but the
            // request itself was fine. Silently retry without consuming a round.
            boolean isModelError = responses.size() == 1
                && responses.get(0).isText()
                && responses.get(0).content() != null
                && responses.get(0).content().startsWith("[MODEL_ERROR:");
            if (isModelError) {
                modelRetries++;
                logger.warn("Model-side error on round {} (retry {}/{}): {}",
                    round + 1, modelRetries, MAX_MODEL_RETRIES, responses.get(0).content());
                if (modelRetries > MAX_MODEL_RETRIES) {
                    return new AgenticResult(
                        "Model repeatedly failed to generate valid tool calls after "
                        + MAX_MODEL_RETRIES + " retries. Try a different model or simplify the request.",
                        cumulativeInputTokens, cumulativeOutputTokens, cumulativeCost);
                }
                if (progressListener != null) {
                    progressListener.onProgress(round + 1, maxRounds, "retry",
                        "Model error, retrying (" + modelRetries + "/" + MAX_MODEL_RETRIES + ")");
                }
                // Feed the error back to the model so it can reformulate
                messages.add(Map.of("role", "assistant", "content",
                    "I attempted a function call but it was malformed."));
                messages.add(Map.of("role", "user", "content",
                    "Your function call was malformed. Do not apologize. "
                    + "Escape special characters in parameters, use smaller batches, "
                    + "and immediately try again."));
                round--; // Don't count this as a used round
                continue;
            }
            modelRetries = 0; // Reset on successful response

            boolean hasToolUse = responses.stream().anyMatch(LLMClient.APIResponse::isToolUse);

            if (!hasToolUse) {
                // LLM is finished - collect text blocks and return
                StringBuilder result = new StringBuilder();
                for (LLMClient.APIResponse r : responses) {
                    if (r.isText() && r.content() != null) {
                        result.append(r.content());
                    }
                }
                return new AgenticResult(result.toString(),
                    cumulativeInputTokens, cumulativeOutputTokens, cumulativeCost);
            }

            // Append assistant's turn (may contain both text and tool_use blocks)
            messages.add(buildAssistantMessage(responses));

            // Process each tool call and append tool_result messages
            for (LLMClient.APIResponse response : responses) {
                if (response.isToolUse()) {
                    if (progressListener != null) {
                        String detail = summarizeToolInput(response.toolName(), response.toolInput());
                        progressListener.onProgress(round + 1, maxRounds, response.toolName(), detail);
                    }

                    // fetch_tool_schemas is handled in the loop body (not dispatchToolCall)
                    // because it mutates the active tool set -- a loop-level concern.
                    if ("fetch_tool_schemas".equals(response.toolName())) {
                        String toolResult = handleFetchToolSchemas(response.toolInput(), activeToolNames);
                        toolsJsonDirty = true;
                        messages.add(buildToolResultMessage(response.toolUseId(), toolResult));
                    } else {
                        String toolResult = toolDispatcher.dispatch(response.toolName(), response.toolInput());
                        toolResult = truncateToolResult(toolResult);
                        messages.add(buildToolResultMessage(response.toolUseId(), toolResult));
                        // Sync orchestrator reference back in case a 'new' command updated it
                        PPTXOrchestrator dispatchedOrch = toolDispatcher.getOrchestrator();
                        if (dispatchedOrch != this.orchestrator) {
                            this.orchestrator = dispatchedOrch;
                            presentationCreated = true;
                            toolDispatcher.setPresentationCreated(true);
                        }
                    }
                }
            }
        }

        // Budget exhausted -- send one final no-tools turn asking the LLM to summarize.
        // It has the full conversation context and knows what it accomplished vs. what remains.
        return requestBudgetSummary(systemPrompt, messages);
    }

    /**
     * Accumulate token usage from the last API call.
     */
    private void accumulateTokenUsage() {
        LLMClient.TokenUsage usage = llmClient.getLastTokenUsage();
        cumulativeInputTokens += usage.inputTokens();
        cumulativeOutputTokens += usage.outputTokens();
        cumulativeCost += usage.cost();
        if (usage.inputTokens() > 0 || usage.outputTokens() > 0) {
            logger.info("Token usage this round: {} in / {} out (cumulative: {} in / {} out)",
                usage.inputTokens(), usage.outputTokens(),
                cumulativeInputTokens, cumulativeOutputTokens);
        }
    }

    // ------------------------------------------------------------------
    // Budget summary
    // ------------------------------------------------------------------

    /**
     * When the agentic loop exhausts its tool budget, send one final turn
     * WITHOUT tools, asking the LLM to summarize what it accomplished and
     * what remains. The LLM has full conversation context so it produces
     * a much better report than we could assemble mechanically.
     */
    private AgenticResult requestBudgetSummary(String systemPrompt,
                                                List<Map<String, Object>> messages) {
        messages.add(Map.of("role", "user", "content", BUDGET_SUMMARY_PROMPT));

        try {
            // Send without tools so the LLM is forced to respond with text
            List<LLMClient.APIResponse> responses = llmClient.sendMessageWithTools(
                systemPrompt, flattenMessages(messages), "[]");
            accumulateTokenUsage();

            if (responses != null) {
                StringBuilder result = new StringBuilder();
                for (LLMClient.APIResponse r : responses) {
                    if (r.isText() && r.content() != null) {
                        result.append(r.content());
                    }
                }
                if (!result.isEmpty()) {
                    return new AgenticResult(result.toString(),
                        cumulativeInputTokens, cumulativeOutputTokens, cumulativeCost);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to get budget summary from LLM: {}", e.getMessage());
        }

        // Fallback if the summary call itself fails
        return new AgenticResult(
            "The agent used its full tool budget on this request. "
            + "Send a follow-up message to continue where it left off.",
            cumulativeInputTokens, cumulativeOutputTokens, cumulativeCost);
    }

    private static final String BUDGET_SUMMARY_PROMPT =
        "You have used all of your available tool calls for this request. "
        + "Do NOT call any tools. Respond with a concise summary:\n"
        + "1. What you accomplished (slides created, content added, animations set, etc.)\n"
        + "2. What remains to be done, if anything\n"
        + "3. If the task is complete, say so\n"
        + "Be specific about slide numbers and shape names. Keep it brief.";

    // ------------------------------------------------------------------
    // Deferred tool schema fetching
    // ------------------------------------------------------------------

    /**
     * Handle fetch_tool_schemas: look up requested tool definitions, add them to the
     * active set, and return their full schemas so the LLM can call them on subsequent turns.
     *
     * @param toolInput JSON with "tool_names" array
     * @param activeToolNames mutable set -- discovered names are added here
     * @return formatted schema text for the tool_result
     */
    private String handleFetchToolSchemas(String toolInput, Set<String> activeToolNames) {
        try {
            JsonObject input = JsonHelper.parseObject(toolInput);
            JsonArray namesArray = input.has("tool_names") ? input.getAsJsonArray("tool_names") : null;

            if (namesArray == null || namesArray.isEmpty()) {
                return "Error: tool_names array is required. Available deferred tools: "
                    + getDeferredToolNames();
            }

            StringBuilder sb = new StringBuilder();
            int found = 0;

            for (JsonElement el : namesArray) {
                String name = el.getAsString();
                LLMToolDefinitions.ToolDefinition tool = LLMToolDefinitions.getToolByName(name);
                if (tool != null) {
                    activeToolNames.add(name);
                    sb.append("Tool: ").append(tool.name()).append("\n");
                    sb.append("Description: ").append(tool.description()).append("\n");
                    sb.append("Schema: ").append(tool.inputSchema()).append("\n");
                    sb.append("Status: ACTIVATED -- you can now call this tool directly.\n\n");
                    found++;
                } else {
                    sb.append("Tool '").append(name).append("' not found. Available: ")
                      .append(getDeferredToolNames()).append("\n\n");
                }
            }

            logger.info("fetch_tool_schemas: activated {} tool(s), active set now: {}", found, activeToolNames);
            return sb.toString();
        } catch (Exception e) {
            return "Error parsing fetch_tool_schemas input: " + e.getMessage()
                + ". Expected: {\"tool_names\":[\"tool_name1\",\"tool_name2\"]}";
        }
    }

    private static String getDeferredToolNames() {
        List<String> names = new ArrayList<>();
        for (LLMToolDefinitions.ToolDefinition t : LLMToolDefinitions.getDeferredTools()) {
            names.add(t.name());
        }
        return String.join(", ", names);
    }

    // ------------------------------------------------------------------
    // System prompt
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // Training data export
    // ------------------------------------------------------------------

    private static final String TRAINING_DATA_DIR = ".excudo/training-data";

    /**
     * Dump the last conversation to a JSONL file for fine-tuning training data.
     * Each line is a complete conversation: system prompt + all turns.
     */
    private void dumpTrainingData() {
        if (lastConversation == null || lastConversation.isEmpty()) return;

        try {
            java.io.File dir = new java.io.File(System.getProperty("user.home"), TRAINING_DATA_DIR);
            dir.mkdirs();

            java.io.File outFile = new java.io.File(dir, "conversations.jsonl");

            JsonObject entry = new JsonObject();
            entry.addProperty("timestamp", java.time.Instant.now().toString());
            entry.addProperty("model", lastModelId != null ? lastModelId : "unknown");
            entry.addProperty("input_tokens", cumulativeInputTokens);
            entry.addProperty("output_tokens", cumulativeOutputTokens);
            entry.addProperty("cost", cumulativeCost);

            // System prompt
            entry.addProperty("system", lastSystemPrompt);

            // Full conversation turns (untruncated)
            JsonArray turns = new JsonArray();
            for (Map<String, Object> msg : lastConversation) {
                JsonObject turn = new JsonObject();
                turn.addProperty("role", (String) msg.get("role"));
                Object content = msg.get("content");
                if (content instanceof String s) {
                    turn.addProperty("content", s);
                } else {
                    turn.addProperty("content", serializeContent(content));
                }
                turns.add(turn);
            }
            entry.add("messages", turns);

            // Append as single JSONL line
            try (java.io.FileWriter fw = new java.io.FileWriter(outFile, true)) {
                fw.write(new Gson().toJson(entry));
                fw.write("\n");
            }

            logger.debug("Training data appended: {} turns, {} tokens",
                lastConversation.size(), cumulativeInputTokens + cumulativeOutputTokens);

        } catch (Exception e) {
            logger.warn("Failed to dump training data: {}", e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // System prompt
    // ------------------------------------------------------------------

    private String buildSystemPrompt(boolean smallModel) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a PowerPoint presentation editor. You have tools to inspect and modify presentations.\n\n");
        sb.append("RULES:\n");
        sb.append("- You MUST call tools to make changes. Never describe what you would do.\n");
        sb.append("- Always respond in English.\n");
        sb.append("- Be concise. Do not explain what you are about to do -- just do it.\n");
        sb.append("- Do not use emojis or emoticons.\n\n");
        sb.append("REQUIRED FIRST STEPS (do both before any execute_commands call):\n");
        sb.append("1. get_presentation_overview -- returns layouts, themes, and slide state.\n");
        sb.append("2. get_command_schemas -- returns the ONLY valid parameter names and types.\n");
        sb.append("   You do NOT know the command parameters without calling this.\n");
        sb.append("   Guessing parameters will cause failures.\n\n");
        sb.append("Then call execute_commands with a JSON array of command objects.\n\n");
        sb.append("TEXT FORMATTING (all text fields support markdown):\n");
        sb.append("- **bold** for bold, *italic* for italic\n");
        sb.append("- Bullets: lines starting with '- ' (indent 2 spaces per level)\n");
        sb.append("- Use \\n for line breaks within text fields\n\n");

        // For full models, include deferred tool catalog so the agent knows what's available
        if (!smallModel) {
            sb.append("\n").append(LLMToolDefinitions.getDeferredToolSummary()).append("\n");
        }

        sb.append("COORDINATES: 914400 EMU = 1 inch. Slide = 9144000 x 6858000 EMU.");
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Message construction helpers
    // ------------------------------------------------------------------

    private Map<String, Object> buildAssistantMessage(List<LLMClient.APIResponse> responses) {
        List<Map<String, Object>> content = new ArrayList<>();
        for (LLMClient.APIResponse r : responses) {
            if (r.isText()) {
                content.add(Map.of("type", "text", "text", r.content() != null ? r.content() : ""));
            } else if (r.isToolUse()) {
                Map<String, Object> block = new HashMap<>();
                block.put("type", "tool_use");
                block.put("id", r.toolUseId());
                block.put("name", r.toolName());
                block.put("input", r.toolInput() != null ? r.toolInput() : "{}");
                content.add(block);
            }
        }
        Map<String, Object> msg = new HashMap<>();
        msg.put("role", "assistant");
        msg.put("content", content);
        return msg;
    }

    private Map<String, Object> buildToolResultMessage(String toolUseId, String result) {
        List<Map<String, Object>> content = new ArrayList<>();
        Map<String, Object> block = new HashMap<>();
        block.put("type", "tool_result");
        block.put("tool_use_id", toolUseId);
        block.put("content", result);
        content.add(block);
        return Map.of("role", "user", "content", content);
    }

    // ------------------------------------------------------------------
    // Conversation compaction -- reduce quadratic token growth
    // ------------------------------------------------------------------

    /** Number of recent messages to keep verbatim (2 rounds = ~4 messages). */
    private static final int KEEP_RECENT_MESSAGES = 4;
    /** Tool result content longer than this gets summarized in old rounds. */
    private static final int COMPACT_THRESHOLD_CHARS = 120;
    /** Max chars for any tool result before truncation (prevents single huge results). */
    private static final int MAX_TOOL_RESULT_CHARS = 1500;

    /**
     * Return a compacted copy of the conversation history.
     * The first message (user request) and the last KEEP_RECENT_MESSAGES are kept verbatim.
     * Everything in between has verbose tool results and tool inputs replaced with
     * one-line summaries to prevent resending stale data every round.
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> compactConversationHistory(
            List<Map<String, Object>> messages, int currentRound) {
        if (messages.size() <= KEEP_RECENT_MESSAGES + 1) {
            return messages; // Nothing to compact
        }

        List<Map<String, Object>> compacted = new ArrayList<>(messages.size());

        // Always keep the first message (user request) verbatim
        compacted.add(messages.get(0));

        int compactEnd = messages.size() - KEEP_RECENT_MESSAGES;
        for (int i = 1; i < messages.size(); i++) {
            if (i < compactEnd) {
                compacted.add(compactMessage(messages.get(i)));
            } else {
                compacted.add(messages.get(i));
            }
        }

        return compacted;
    }

    /**
     * Compact a single message by summarizing verbose content blocks.
     * Tool results get replaced with "[result: OK]" or "[result: N chars]".
     * Tool use inputs get truncated to show just the tool name and type.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> compactMessage(Map<String, Object> message) {
        Object content = message.get("content");
        if (content instanceof String s) {
            // Plain text messages (user role) -- keep as-is, they're usually short
            return message;
        }

        if (!(content instanceof List)) {
            return message;
        }

        List<Map<String, Object>> blocks = (List<Map<String, Object>>) content;
        List<Map<String, Object>> compactedBlocks = new ArrayList<>();

        for (Map<String, Object> block : blocks) {
            String type = (String) block.get("type");

            if ("tool_result".equals(type)) {
                // Summarize tool results
                Object resultContent = block.get("content");
                String resultStr = resultContent != null ? resultContent.toString() : "";
                if (resultStr.length() > COMPACT_THRESHOLD_CHARS) {
                    // Extract first line as summary
                    String firstLine = resultStr.contains("\n")
                        ? resultStr.substring(0, resultStr.indexOf('\n'))
                        : resultStr.substring(0, Math.min(80, resultStr.length()));
                    Map<String, Object> compactBlock = new HashMap<>(block);
                    compactBlock.put("content", "[prior result: " + firstLine + "...]");
                    compactedBlocks.add(compactBlock);
                } else {
                    compactedBlocks.add(block);
                }
            } else if ("tool_use".equals(type)) {
                // Truncate tool input for old rounds
                String input = (String) block.get("input");
                if (input != null && input.length() > COMPACT_THRESHOLD_CHARS) {
                    Map<String, Object> compactBlock = new HashMap<>(block);
                    String toolName = (String) block.get("name");
                    compactBlock.put("input", "{\"_compacted\":\"" + toolName + " call, "
                        + input.length() + " chars\"}");
                    compactedBlocks.add(compactBlock);
                } else {
                    compactedBlocks.add(block);
                }
            } else {
                // Text blocks -- keep as-is
                compactedBlocks.add(block);
            }
        }

        Map<String, Object> compactedMsg = new HashMap<>(message);
        compactedMsg.put("content", compactedBlocks);
        return compactedMsg;
    }

    /**
     * Truncate a tool result to MAX_TOOL_RESULT_CHARS to prevent any single result
     * from dominating the conversation context.
     */
    private String truncateToolResult(String result) {
        if (result == null || result.length() <= MAX_TOOL_RESULT_CHARS) {
            return result;
        }
        // Keep the beginning (most useful) and note the truncation
        return result.substring(0, MAX_TOOL_RESULT_CHARS)
            + "\n[... truncated, " + result.length() + " total chars]";
    }

    /**
     * Flatten the internal message list into the format expected by sendMessageWithTools.
     *
     * Simple string content is passed through directly. Complex content (tool_use or
     * tool_result blocks) is serialized to a JSON array string so the HTTP layer can
     * embed it verbatim in the request body without re-quoting.
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, String>> flattenMessages(List<Map<String, Object>> messages) {
        List<Map<String, String>> flat = new ArrayList<>();
        for (Map<String, Object> msg : messages) {
            Map<String, String> flatMsg = new HashMap<>();
            flatMsg.put("role", (String) msg.get("role"));
            Object content = msg.get("content");
            if (content instanceof String) {
                flatMsg.put("content", (String) content);
            } else {
                flatMsg.put("content", serializeContent(content));
            }
            flat.add(flatMsg);
        }
        return flat;
    }

    /**
     * Serialize complex content blocks (tool_use, tool_result) to a JSON array string.
     */
    @SuppressWarnings("unchecked")
    private String serializeContent(Object content) {
        if (content instanceof List) {
            List<Map<String, Object>> blocks = (List<Map<String, Object>>) content;
            JsonArray array = new JsonArray();
            for (Map<String, Object> block : blocks) {
                JsonObject obj = new JsonObject();
                for (Map.Entry<String, Object> entry : block.entrySet()) {
                    Object val = entry.getValue();
                    if (val instanceof String sVal) {
                        // Embed raw JSON objects/arrays verbatim (e.g. tool_use input)
                        if ((sVal.startsWith("{") && sVal.endsWith("}"))
                                || (sVal.startsWith("[") && sVal.endsWith("]"))) {
                            try {
                                obj.add(entry.getKey(), JsonParser.parseString(sVal));
                            } catch (JsonSyntaxException e) {
                                obj.addProperty(entry.getKey(), sVal);
                            }
                        } else {
                            obj.addProperty(entry.getKey(), sVal);
                        }
                    } else if (val instanceof Number num) {
                        obj.addProperty(entry.getKey(), num);
                    } else if (val instanceof Boolean bool) {
                        obj.addProperty(entry.getKey(), bool);
                    } else {
                        obj.addProperty(entry.getKey(), String.valueOf(val));
                    }
                }
                array.add(obj);
            }
            return GSON.toJson(array);
        }
        return String.valueOf(content);
    }

    /**
     * Build a human-readable detail string for a tool call.
     * For execute_commands, lists the command types being executed.
     * For other tools, returns null (the label from TOOL_LABELS suffices).
     */
    private String summarizeToolInput(String toolName, String toolInput) {
        if (toolInput == null) return null;

        // Show which tools are being fetched
        if ("fetch_tool_schemas".equals(toolName)) {
            try {
                JsonObject obj = JsonHelper.parseObject(toolInput);
                JsonArray names = obj.getAsJsonArray("tool_names");
                if (names != null && !names.isEmpty()) {
                    List<String> toolNames = new ArrayList<>();
                    for (JsonElement el : names) toolNames.add(el.getAsString());
                    return String.join(", ", toolNames);
                }
            } catch (Exception e) { /* fall through */ }
            return null;
        }

        if (!"execute_commands".equals(toolName)) return null;
        try {
            JsonObject inputObj = JsonHelper.parseObject(toolInput);
            String commandsJson = JsonHelper.getString(inputObj, "commands");
            if (commandsJson == null || commandsJson.isEmpty()) {
                if (toolInput.trim().startsWith("[")) {
                    commandsJson = toolInput.trim();
                } else {
                    return null;
                }
            }
            JsonArray array = JsonHelper.parseArray(commandsJson);
            // Count occurrences of each command type
            Map<String, Integer> typeCounts = new java.util.LinkedHashMap<>();
            for (JsonElement el : array) {
                if (!el.isJsonObject()) continue;
                JsonObject obj = el.getAsJsonObject();
                String type = obj.has("type") ? obj.get("type").getAsString()
                            : obj.has("command") ? obj.get("command").getAsString() : null;
                if (type != null) {
                    String label = humanizeCommandType(type);
                    typeCounts.merge(label, 1, Integer::sum);
                }
            }
            if (typeCounts.isEmpty()) return null;
            // Format: "Add animation (x6), Edit content (x3)" or "Create slide" for singles
            List<String> parts = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : typeCounts.entrySet()) {
                if (entry.getValue() > 1) {
                    parts.add(entry.getKey() + " (x" + entry.getValue() + ")");
                } else {
                    parts.add(entry.getKey());
                }
            }
            return String.join(", ", parts);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Convert a kebab-case command type to a human-readable label.
     * E.g. AddShapeCommand.NAME -> "Add shape", "edit-content" -> "Edit content"
     */
    private static String humanizeCommandType(String type) {
        if (type == null || type.isEmpty()) return type;
        String spaced = type.replace('-', ' ');
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }
}
