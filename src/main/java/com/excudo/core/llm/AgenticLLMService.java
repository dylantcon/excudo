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
    /** Lookup-only rounds per turn that don't count against MAX_ROUND_TRIPS. */
    static final int MAX_FREE_LOOKUP_ROUNDS = 8;
    /** Tools that only discover what's available; rounds made of these alone are free. */
    static final Set<String> LOOKUP_TOOLS = Set.of(
        "fetch_tool_schemas", "get_command_schemas", "list_commands");

    /** True when every tool call in the round is a schema/command lookup. */
    static boolean isLookupOnlyRound(List<LLMClient.APIResponse> responses) {
        boolean anyTool = false;
        for (LLMClient.APIResponse r : responses) {
            if (!r.isToolUse()) continue;
            anyTool = true;
            if (!LOOKUP_TOOLS.contains(r.toolName())) return false;
        }
        return anyTool;
    }
    private static final Gson GSON = new Gson();

    /**
     * Result of an agentic processing run, including token usage statistics.
     */
    /**
     * Outcome of one request. {@code inputTokens} is uncached input; prompt-cache
     * reads / writes are counted separately (zero for providers without caching).
     */
    public record AgenticResult(String summary, int inputTokens, int outputTokens, double cost,
                                int cacheReadTokens, int cacheWriteTokens) {
        public AgenticResult(String summary, int inputTokens, int outputTokens, double cost) {
            this(summary, inputTokens, outputTokens, cost, 0, 0);
        }
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
    private int cumulativeCacheReadTokens = 0;
    private int cumulativeCacheWriteTokens = 0;
    private int cumulativeOutputTokens = 0;
    private double cumulativeCost = 0.0;

    // Session-level guard: once 'new' has been called, block all subsequent 'new' calls
    private boolean presentationCreated = false;

    // Arrange-mode memory across turns; null = single-shot request
    private ArrangeConversation conversation;

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
        if (conversation == null) {
            AgenticResult result = processRequestInternal(userRequest,
                new ArrayList<>(), new LinkedHashSet<>());
            dumpTrainingData();
            return result;
        }

        // Multi-turn: continue the session's history. On failure, roll back
        // to the last completed turn so no dangling tool_use survives.
        conversation.trimToRecentTurns(ArrangeConversation.MAX_REMEMBERED_TURNS);
        int checkpoint = conversation.checkpoint();
        try {
            AgenticResult result = processRequestInternal(
                conversation.notesPrefix() + userRequest,
                conversation.messages(), conversation.activeToolNames());
            conversation.clearNotes();
            conversation.completeTurn(result.inputTokens(), result.outputTokens(), result.cost(),
                result.cacheReadTokens());
            dumpTrainingData();
            return result;
        } catch (RuntimeException e) {
            conversation.rollbackTo(checkpoint);
            throw e;
        }
    }

    /**
     * Attach session memory: later {@link #processRequestWithUsage} calls
     * continue this conversation instead of starting fresh.
     */
    public void setConversation(ArrangeConversation conversation) {
        this.conversation = conversation;
    }

    private AgenticResult processRequestInternal(String userRequest,
                                                 List<Map<String, Object>> messages,
                                                 Set<String> activeToolNames) {
        boolean smallModel = llmClient.isLocalModel();
        boolean hasPresentation = getContextService() != null;
        presentationCreated = hasPresentation; // Lock 'new' if presentation already exists
        toolDispatcher.setPresentationCreated(hasPresentation);
        String systemPrompt = buildSystemPrompt(smallModel);

        // If no presentation is loaded, tell the model to create one first
        if (!hasPresentation) {
            userRequest = "[No presentation loaded. Use the 'new-presentation' command (via execute_commands) "
                + "to create one before any other commands.]\n\n" + userRequest;
        }
        int maxRounds = smallModel ? 10 : MAX_ROUND_TRIPS;

        // For small models: use flat tool set (no deferred pattern).
        // For full models: start with core tools only; deferred tools are discovered on demand.
        String toolsJson;
        if (smallModel) {
            toolsJson = LLMToolDefinitions.toSmallModelToolsJson();
        } else {
            toolsJson = LLMToolDefinitions.buildToolsJson(activeToolNames);
        }
        boolean toolsJsonDirty = false;

        // This turn's request, as a plain-string user message (turn boundary)
        messages.add(Map.of("role", "user", "content", userRequest));

        // Capture for training data export
        this.lastSystemPrompt = systemPrompt;
        this.lastModelId = llmClient.getModel();
        this.lastConversation = messages; // Same reference -- will grow during the loop

        int modelRetries = 0;
        int freeLookupRounds = 0;
        for (int round = 0; round < maxRounds; round++) {
            // Rebuild tools JSON if new schemas were discovered last round
            if (toolsJsonDirty && !smallModel) {
                toolsJson = LLMToolDefinitions.buildToolsJson(activeToolNames);
                toolsJsonDirty = false;
                logger.info("Rebuilt tools JSON with {} discovered tool(s): {}", activeToolNames.size(), activeToolNames);
            }

            logger.info("Agentic round {}/{}", round + 1, maxRounds);

            // Compact old tool results to reduce quadratic token growth.
            // Recent messages stay verbatim; older ones are summarized (in steps, for cache stability).
            List<Map<String, Object>> compactedMessages = compactConversationHistory(messages, round);

            List<LLMClient.APIResponse> responses;
            try {
                responses = llmClient.sendMessageWithTools(
                    systemPrompt, flattenMessages(compactedMessages), toolsJson);
            } catch (LLMApiException e) {
                if (round == 0) throw e;
                throw new LLMApiException(e.getMessage() + "\n(Stopped at round " + (round + 1)
                    + "; edits from earlier rounds were applied.)", e);
            }

            // Track token usage from this round
            accumulateTokenUsage();

            if (responses == null || responses.isEmpty()) {
                return endTurn(messages, "Error: No response from LLM");
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
                    return endTurn(messages,
                        "Model repeatedly failed to generate valid tool calls after "
                        + MAX_MODEL_RETRIES + " retries. Try a different model or simplify the request.");
                }
                if (progressListener != null) {
                    progressListener.onProgress(round + 1, maxRounds, "retry",
                        "Model error, retrying (" + modelRetries + "/" + MAX_MODEL_RETRIES + ")");
                }
                // Feed the error back to the model so it can reformulate
                messages.add(Map.of("role", "assistant", "content",
                    "I attempted a function call but it was malformed."));
                messages.add(Map.of("role", "user", "content", List.of(Map.of("type", "text", "text",
                    "Your function call was malformed. Do not apologize. "
                    + "Escape special characters in parameters, use smaller batches, "
                    + "and immediately try again."))));
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
                return endTurn(messages, result.toString());
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
                        rememberReference(response.toolName(), toolResult);
                        toolResult = truncateToolResult(response.toolName(), toolResult);
                        byte[] png = renderedImageFor(response.toolName(), toolResult);
                        messages.add(png != null
                            ? buildImageToolResultMessage(response.toolUseId(), toolResult, png)
                            : buildToolResultMessage(response.toolUseId(), toolResult));
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

            // Discovering schemas (progressive tool exposure) doesn't spend the
            // turn's budget: a round made only of lookups gives its round back,
            // up to a cap so a model stuck looking things up still stops.
            if (isLookupOnlyRound(responses) && freeLookupRounds < MAX_FREE_LOOKUP_ROUNDS) {
                freeLookupRounds++;
                logger.info("Lookup-only round; not counted ({}/{} free)", freeLookupRounds, MAX_FREE_LOOKUP_ROUNDS);
                round--;
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
        cumulativeCacheReadTokens += usage.cacheReadTokens();
        cumulativeCacheWriteTokens += usage.cacheWriteTokens();
        if (usage.inputTokens() > 0 || usage.outputTokens() > 0) {
            logger.info("Token usage this round: {} in / {} out, cache {} read / {} written (cumulative: {} in / {} out)",
                usage.inputTokens(), usage.outputTokens(), usage.cacheReadTokens(), usage.cacheWriteTokens(),
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
    /**
     * Keep command-reference lookups in the conversation's reference store
     * (separate from the history) so later turns don't re-fetch them.
     * Entries are regenerated from the live schemas, keyed by the command
     * names that appear as "name:" headers in the tool output.
     */
    private void rememberReference(String toolName, String toolResult) {
        if (conversation == null || toolResult == null) return;
        if ("list_commands".equals(toolName)) {
            conversation.markCommandListLoaded();
        } else if ("get_command_schemas".equals(toolName)) {
            for (String line : toolResult.split("\n")) {
                if (line.isEmpty() || Character.isWhitespace(line.charAt(0)) || !line.endsWith(":")) continue;
                String name = line.substring(0, line.length() - 1);
                com.excudo.core.parsing.CommandSchema schema =
                    com.excudo.core.parsing.CommandRegistry.getSchema(name);
                if (schema != null && name.equals(schema.getName())) {
                    conversation.rememberCommandReference(name, ToolDispatcher.formatCommandSchema(schema));
                }
            }
        }
    }

    /**
     * Finish the turn: record the assistant's final reply in the history (so
     * the next turn sees it and roles keep alternating) and package the result.
     */
    private AgenticResult endTurn(List<Map<String, Object>> messages, String summary) {
        // The API rejects empty assistant content; never store a blank reply.
        String text = (summary == null || summary.isBlank()) ? "(no reply)" : summary;
        messages.add(Map.of("role", "assistant", "content", text));
        return new AgenticResult(summary == null ? "" : summary,
            cumulativeInputTokens, cumulativeOutputTokens, cumulativeCost,
            cumulativeCacheReadTokens, cumulativeCacheWriteTokens);
    }

    private AgenticResult requestBudgetSummary(String systemPrompt,
                                                List<Map<String, Object>> messages) {
        // Marked as a block list so it isn't mistaken for a turn boundary
        // (plain-string user content) when trimming old turns.
        messages.add(Map.of("role", "user", "content",
            List.of(Map.of("type", "text", "text", BUDGET_SUMMARY_PROMPT))));

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
                    return endTurn(messages, result.toString());
                }
            }
        } catch (Exception e) {
            logger.error("Failed to get budget summary from LLM: {}", e.getMessage());
        }

        // Fallback if the summary call itself fails
        return endTurn(messages,
            "The agent used its full tool budget on this request. "
            + "Send a follow-up message to continue where it left off.");
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
            if (conversation != null) {
                // Each turn re-dumps the whole history; consumers keep the
                // highest turn per conversation_id.
                entry.addProperty("conversation_id", conversation.getId());
                entry.addProperty("turn", conversation.getTurnCount());
            }
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
                    // Renders are kept as text: base64 PNGs would bloat every re-dump
                    turn.addProperty("content", serializeContent(withoutImages(content)));
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

        if (conversation != null) {
            String reference = conversation.referencePromptSection();
            if (!reference.isEmpty()) {
                sb.append("\n\n").append(reference.strip());
                sb.append("\n\nThis is a continuing conversation: the first steps above are only ")
                  .append("needed for commands NOT in the loaded reference. Still re-check slide ")
                  .append("state (get_presentation_overview / get_slide_shapes) when it may have changed.");
            }
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Message construction helpers
    // ------------------------------------------------------------------

    private Map<String, Object> buildAssistantMessage(List<LLMClient.APIResponse> responses) {
        List<Map<String, Object>> content = new ArrayList<>();
        for (LLMClient.APIResponse r : responses) {
            if ("thinking".equals(r.type())) {
                // Returned verbatim with its signature: the API requires the
                // reasoning that led to a tool call when its results come back.
                Map<String, Object> block = new HashMap<>();
                block.put("type", "thinking");
                block.put("thinking", r.content() != null ? r.content() : "");
                block.put("signature", r.signature() != null ? r.signature() : "");
                content.add(block);
            } else if ("redacted_thinking".equals(r.type())) {
                content.add(Map.of("type", "redacted_thinking", "data", r.content() != null ? r.content() : ""));
            } else if (r.isText()) {
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

    /** Largest render handed to the model; bigger PNGs fall back to the text result. */
    private static final int MAX_INLINE_IMAGE_BYTES = 3_500_000;

    /**
     * The PNG a successful render_slide / render_slides call just wrote, so
     * the model can look at it, or null. The text result alone is only a temp
     * file path the model has no way to open. Local models get text only:
     * most aren't vision models.
     */
    private byte[] renderedImageFor(String toolName, String toolResult) {
        if (!"render_slide".equals(toolName) && !"render_slides".equals(toolName)) return null;
        if (toolResult == null || toolResult.startsWith("Error") || llmClient.isLocalModel()) return null;
        byte[] png = toolDispatcher.getLastRenderBytes();
        if (png == null || png.length == 0 || png.length > MAX_INLINE_IMAGE_BYTES) return null;
        return png;
    }

    /** tool_result whose content is the text result followed by the rendered image. */
    private Map<String, Object> buildImageToolResultMessage(String toolUseId, String result, byte[] png) {
        Map<String, Object> source = new HashMap<>();
        source.put("type", "base64");
        source.put("media_type", "image/png");
        source.put("data", java.util.Base64.getEncoder().encodeToString(png));
        List<Map<String, Object>> parts = new ArrayList<>();
        parts.add(Map.of("type", "text", "text", result));
        parts.add(Map.of("type", "image", "source", source));

        Map<String, Object> block = new HashMap<>();
        block.put("type", "tool_result");
        block.put("tool_use_id", toolUseId);
        block.put("content", parts);
        List<Map<String, Object>> content = new ArrayList<>();
        content.add(block);
        return Map.of("role", "user", "content", content);
    }

    // ------------------------------------------------------------------
    // Conversation compaction -- reduce quadratic token growth
    // ------------------------------------------------------------------

    /** Number of recent messages to keep verbatim (2 rounds = ~4 messages). */
    private static final int KEEP_RECENT_MESSAGES = 4;
    /** Compaction boundary granularity (messages); see compactConversationHistory. */
    static final int COMPACT_STEP = 6;
    /** Tool result content longer than this gets summarized in old rounds. */
    private static final int COMPACT_THRESHOLD_CHARS = 120;
    /**
     * Max chars for a tool result before truncation (prevents single huge
     * results). Was 1500, which cut the add-animation schema before its
     * placement parameters and an 11-click slide's animation list before half
     * its rows -- the agent then re-requested the same results in a loop.
     * Older results are still compacted, so this only bounds the recent ones.
     */
    static final int MAX_TOOL_RESULT_CHARS = 8000;
    /** Reference documentation: always delivered whole (rememberReference keeps it too). */
    private static final Set<String> UNTRUNCATED_TOOLS = Set.of("get_command_schemas");

    /**
     * Return a compacted copy of the conversation history.
     * The first message, at least the last KEEP_RECENT_MESSAGES, and the latest assistant
     * message are kept verbatim; the compacted region grows in COMPACT_STEP increments.
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

        // The boundary advances in steps of COMPACT_STEP messages rather than
        // one per round: between steps the compacted prefix is byte-identical,
        // so the prompt cache keeps hitting instead of being invalidated at
        // the message that just slid out of the verbatim window.
        int compactEnd = ((messages.size() - KEEP_RECENT_MESSAGES) / COMPACT_STEP) * COMPACT_STEP;
        // The latest assistant message stays verbatim (its reasoning blocks
        // must accompany the tool results being sent back), even when many
        // parallel tool results push it out of the recent window.
        for (int i = messages.size() - 1; i >= 0; i--) {
            if ("assistant".equals(messages.get(i).get("role"))) {
                compactEnd = Math.min(compactEnd, i);
                break;
            }
        }
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

            if ("thinking".equals(type) || "redacted_thinking".equals(type)) {
                // Old reasoning: dropped (allowed for earlier assistant turns)
                continue;
            } else if ("tool_result".equals(type) && block.get("content") instanceof List) {
                // A render from an earlier round: drop the image, keep the text
                Map<String, Object> compactBlock = new HashMap<>(block);
                compactBlock.put("content", textWithoutImages(block.get("content")));
                compactedBlocks.add(compactBlock);
            } else if ("tool_result".equals(type)) {
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

    /** Content blocks with every block-list tool_result reduced to its text. */
    @SuppressWarnings("unchecked")
    private static Object withoutImages(Object content) {
        if (!(content instanceof List)) return content;
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> block : (List<Map<String, Object>>) content) {
            if ("tool_result".equals(block.get("type")) && block.get("content") instanceof List) {
                Map<String, Object> copy = new HashMap<>(block);
                copy.put("content", textWithoutImages(block.get("content")));
                out.add(copy);
            } else {
                out.add(block);
            }
        }
        return out;
    }

    /** The text parts of a block-list tool_result, with each image noted as omitted. */
    @SuppressWarnings("unchecked")
    private static String textWithoutImages(Object content) {
        StringBuilder sb = new StringBuilder();
        for (Object part : (List<Object>) content) {
            if (!(part instanceof Map)) continue;
            Map<String, Object> p = (Map<String, Object>) part;
            if ("text".equals(p.get("type"))) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(p.get("text"));
            } else if ("image".equals(p.get("type"))) {
                if (sb.length() > 0) sb.append('\n');
                sb.append("[image from an earlier round omitted; render again to see it]");
            }
        }
        return sb.toString();
    }

    /**
     * Truncate a tool result to MAX_TOOL_RESULT_CHARS to prevent any single result
     * from dominating the conversation context.
     */
    static String truncateToolResult(String toolName, String result) {
        if (result == null || result.length() <= MAX_TOOL_RESULT_CHARS
                || UNTRUNCATED_TOOLS.contains(toolName)) {
            return result;
        }
        // Keep the beginning (most useful) and say how to see the rest
        return result.substring(0, MAX_TOOL_RESULT_CHARS)
            + "\n[... truncated, " + result.length() + " total chars. Narrow the request "
            + "(e.g. one slide, one shape) to see the rest.]";
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
                        // Only tool_use.input is a JSON object. Text and
                        // tool_result content must stay strings even when they
                        // look like JSON, or the API rejects the request.
                        if ("input".equals(entry.getKey())
                                && sVal.startsWith("{") && sVal.endsWith("}")) {
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
                    } else if (val instanceof List || val instanceof Map) {
                        // Nested blocks, e.g. a tool_result carrying text + image parts
                        obj.add(entry.getKey(), GSON.toJsonTree(val));
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
