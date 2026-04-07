package com.excudo.core.llm;

import com.excudo.core.commands.*;
import com.excudo.core.metrics.LayoutIssue;
import com.excudo.core.metrics.LayoutValidator;
import com.excudo.core.orchestration.*;
import com.excudo.core.results.SlideExecutionResult;
import com.excudo.core.services.ContextService;
import com.excudo.core.model.*;
import com.excudo.core.utils.JsonHelper;
import com.excudo.core.utils.Logger;
import com.excudo.core.utils.ComponentLogger;
import com.google.gson.*;

import java.util.*;

/**
 * Handles tool dispatch for the agentic LLM conversation loop.
 *
 * All tool handler methods, the dispatch switch, the MUTATING_TOOLS set,
 * and the invalidation/orchestrator-update logic live here. This class is
 * a pure delegation target -- it owns no conversation state and has no
 * knowledge of the round-trip loop or token tracking.
 *
 * AgenticLLMService creates one ToolDispatcher instance and delegates all
 * dispatchToolCall() work to it.
 */
public class ToolDispatcher {

    private static final ComponentLogger logger = Logger.llm();
    private static final Gson GSON = new Gson();

    private PPTXOrchestrator orchestrator;
    private final CommandFactory commandFactory;
    private final CommandInvoker commandInvoker;
    private CommandDisplay displayAdapter;
    private CompoundShapeTools compoundShapeTools;
    private MermaidDiagramTool mermaidDiagramTool;
    private boolean presentationCreated = false;

    private static final Set<String> MUTATING_TOOLS = Set.of(
        "execute_commands", "create_code_box", "create_diagram",
        "create_slide_from_layout", "inject_icon"
    );

    public ToolDispatcher(PPTXOrchestrator orchestrator, CommandFactory commandFactory,
                          CommandInvoker commandInvoker) {
        this.orchestrator = orchestrator;
        this.commandFactory = commandFactory;
        this.commandInvoker = commandInvoker;
        this.compoundShapeTools = new CompoundShapeTools(orchestrator);
        this.mermaidDiagramTool = new MermaidDiagramTool(orchestrator);
    }

    public void setDisplayAdapter(CommandDisplay adapter) {
        this.displayAdapter = adapter;
    }

    public void updateOrchestrator(PPTXOrchestrator newOrch) {
        this.orchestrator = newOrch;
        this.compoundShapeTools = new CompoundShapeTools(newOrch);
        this.mermaidDiagramTool = new MermaidDiagramTool(newOrch);
    }

    public void setPresentationCreated(boolean created) {
        this.presentationCreated = created;
    }

    public PPTXOrchestrator getOrchestrator() {
        return orchestrator;
    }

    // ------------------------------------------------------------------
    // Tool dispatch
    // ------------------------------------------------------------------

    public String dispatch(String toolName, String toolInput) {
        try {
            String inputSnippet = toolInput != null
                ? toolInput.substring(0, Math.min(toolInput.length(), 300))
                : "(none)";
            logger.info("TOOL_CALL {} | input: {}", toolName, inputSnippet);

            String result = switch (toolName) {
                case "get_presentation_overview" -> handleGetPresentationOverview();
                case "get_slide_shapes"          -> handleGetSlideShapes(toolInput);
                case "get_shape_detail"          -> handleGetShapeDetail(toolInput);
                case "get_available_layouts"     -> handleGetAvailableLayouts();
                case "get_command_schemas"      -> handleGetCommandSchemas(toolInput);
                case "get_slide_animations"      -> handleGetSlideAnimations(toolInput);
                case "execute_commands"          -> handleExecuteCommands(toolInput);
                case "validate_layout"           -> handleValidateLayout(toolInput);
                case "create_code_box"           -> compoundShapeTools.createCodeBox(toolInput);
                case "create_diagram"            -> mermaidDiagramTool.createMermaidDiagram(toolInput);
                case "suggest_layout"            -> handleSuggestLayout(toolInput);
                case "create_slide_from_layout"  -> handleCreateSlideFromLayout(toolInput);
                case "inject_icon"               -> handleInjectIcon(toolInput);
                case "render_slide"              -> handleRenderSlide(toolInput);
                default -> "Unknown tool: " + toolName;
            };

            // Invalidate cached slide data after mutating tools so subsequent
            // reads (e.g. validate_layout) see the current DOM state.
            invalidateAfterMutation(toolName, toolInput);

            String resultSnippet = result != null
                ? result.substring(0, Math.min(result.length(), 500))
                : "(null)";
            logger.info("TOOL_RESULT {} | output: {}", toolName, resultSnippet);

            return result;
        } catch (Exception e) {
            logger.error("Tool call '{}' failed: {}", toolName, e.getMessage());
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Invalidate ContextService slide cache after a mutating tool call.
     * For tools that target a single slide, only that entry is evicted.
     * For execute_commands (which can touch multiple slides), all entries are evicted.
     */
    private void invalidateAfterMutation(String toolName, String toolInput) {
        if (getContextService() == null || !MUTATING_TOOLS.contains(toolName)) return;

        if ("execute_commands".equals(toolName)) {
            // Commands can target any slide -- evict everything
            getContextService().invalidateAllSlides();
        } else {
            int slideNumber = getToolInputInt(toolInput, "slideNumber");
            if (slideNumber > 0) {
                getContextService().invalidateSlide(slideNumber);
            }
        }
    }

    // ------------------------------------------------------------------
    // Context accessor
    // ------------------------------------------------------------------

    private ContextService getContextService() {
        return orchestrator.getContextService();
    }

    // ------------------------------------------------------------------
    // Context/inspection handlers
    // ------------------------------------------------------------------

    private String handleGetPresentationOverview() {
        if (getContextService() == null) {
            return "No presentation loaded. Use execute_commands with {\"type\":\"new\",\"themeId\":\"<id>\"} to create one.\n"
                + getThemeCatalog();
        }
        try {
            ContextService.PresentationContext ctx = getContextService().getPresentationContext();
            StringBuilder sb = new StringBuilder();
            sb.append("Slides: ").append(ctx.getSlideNumbers().size()).append("\n");
            sb.append("Slide numbers: ").append(ctx.getSlideNumbers()).append("\n");
            // Include layout descriptions inline so the model can pick layouts
            // without needing a separate get_available_layouts call
            sb.append("Layouts (use exact layoutId in create commands):\n");
            try {
                List<LayoutInfo> layouts = getContextService().getAvailableLayoutsDetailed();
                for (LayoutInfo l : layouts) {
                    sb.append("  layoutId=\"").append(l.getLayoutId()).append("\" - ").append(l.getName());
                    sb.append(" (").append(l.getContentPlaceholderCount()).append(" content");
                    if (l.hasTitlePlaceholder()) sb.append("+title");
                    if (l.hasSubtitlePlaceholder()) sb.append("+subtitle");
                    sb.append(")\n");
                }
            } catch (Exception e) {
                sb.append("  ").append(ctx.getAvailableLayouts()).append("\n");
            }
            sb.append(getThemeCatalog());

            for (int slideNum : ctx.getSlideNumbers()) {
                try {
                    ContextService.SlideContext slideCtx = getContextService().getSlideContext(slideNum);
                    String title = "Untitled";
                    for (SlideShape shape : slideCtx.getSlideData().getShapeRegistry().getAllShapes()) {
                        if (shape.getName() != null
                                && shape.getName().toLowerCase().contains("title")
                                && shape.hasText()) {
                            title = shape.getTextContent();
                            break;
                        }
                    }
                    sb.append("Slide ").append(slideNum).append(": ").append(title).append("\n");
                } catch (Exception e) {
                    sb.append("Slide ").append(slideNum).append(": (error reading)\n");
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "Error getting overview: " + e.getMessage();
        }
    }

    private String handleGetSlideShapes(String toolInput) {
        if (getContextService() == null) {
            return "No presentation loaded. Create one first with the 'new' command.";
        }
        int slideNumber = getToolInputInt(toolInput, "slideNumber");
        try {
            ContextService.SlideContext slideCtx = getContextService().getSlideContext(slideNumber);
            StringBuilder sb = new StringBuilder();
            sb.append("Slide ").append(slideNumber).append(":\n");
            for (SlideShape shape : slideCtx.getSlideData().getShapeRegistry().getAllShapes()) {
                sb.append("  ").append(shape.getSpid()).append(" ");
                sb.append(shape.getType());
                if (shape.getName() != null) sb.append(" \"").append(shape.getName()).append("\"");
                if (shape.hasText()) {
                    String text = shape.getTextContent();
                    sb.append(": ")
                      .append(text.length() > 50 ? text.substring(0, 47) + "..." : text);
                }
                sb.append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return "Error getting shapes for slide " + slideNumber + ": " + e.getMessage();
        }
    }

    private String handleGetShapeDetail(String toolInput) {
        int slideNumber = getToolInputInt(toolInput, "slideNumber");
        int spid = getToolInputInt(toolInput, "spid");
        try {
            ContextService.SlideContext slideCtx = getContextService().getSlideContext(slideNumber);
            SlideShape shape = slideCtx.getSlideData().getShapeRegistry().getShape(spid);
            if (shape == null) {
                return "Shape SPID " + spid + " not found on slide " + slideNumber;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Shape Detail - SPID ").append(spid).append(" on slide ").append(slideNumber).append(":\n");
            sb.append("Type: ").append(shape.getType()).append("\n");
            if (shape.getName() != null) sb.append("Name: ").append(shape.getName()).append("\n");
            if (shape.getGeometry() != null) {
                ShapeGeometry g = shape.getGeometry();
                sb.append("Position: x=").append(g.getX()).append(" y=").append(g.getY()).append("\n");
                sb.append("Size: cx=").append(g.getWidth()).append(" cy=").append(g.getHeight()).append("\n");
            }
            if (shape.hasText()) sb.append("Text: ").append(shape.getTextContent()).append("\n");

            ContextService.AnimationContext animCtx = getContextService().getAnimationContext(slideNumber);
            for (AnimationBinding binding : animCtx.getAnimationBindings()) {
                if (binding.getTargetSpid() == spid) {
                    sb.append("Animation: ").append(binding.getAnimationType())
                      .append(" | Duration: ").append(binding.getDuration()).append("\n");
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "Error getting shape detail: " + e.getMessage();
        }
    }

    private String getThemeCatalog() {
        try {
            List<com.excudo.core.themes.ThemeDefinition> themes =
                com.excudo.core.themes.ThemeManager.getAvailableThemes();
            StringBuilder sb = new StringBuilder("Themes:\n");
            for (com.excudo.core.themes.ThemeDefinition t : themes) {
                sb.append("  ").append(t.getId()).append(": ");
                sb.append(t.getDisplayName());
                sb.append(" (").append(t.getMajorFont());
                sb.append("/").append(t.getMinorFont()).append(")");
                if (t.isDarkBackground()) sb.append(" [dark]");
                sb.append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return "Themes: minimal, corporate, academic, excudo\n";
        }
    }

    private String handleGetCommandSchemas(String toolInput) {
        try {
            // Check if specific commands were requested (string, array, or omitted)
            Set<String> requested = null;
            if (toolInput != null && !toolInput.isEmpty()) {
                JsonObject input = JsonHelper.parseObject(toolInput);
                if (input.has("commands")) {
                    requested = new java.util.LinkedHashSet<>();
                    JsonElement cmds = input.get("commands");
                    if (cmds.isJsonArray()) {
                        for (JsonElement el : cmds.getAsJsonArray()) {
                            requested.add(el.getAsString());
                        }
                    } else if (cmds.isJsonPrimitive()) {
                        requested.add(cmds.getAsString());
                    }
                }
            }

            StringBuilder sb = new StringBuilder();
            sb.append("COMMAND REFERENCE (use with execute_commands):\n\n");

            List<com.excudo.core.parsing.CommandSchema> schemas =
                new ArrayList<>(com.excudo.core.parsing.CommandRegistry.getAllSchemas().values());
            schemas.sort(java.util.Comparator.comparing(com.excudo.core.parsing.CommandSchema::getName));

            for (com.excudo.core.parsing.CommandSchema schema : schemas) {
                if (!schema.isLlmEnabled()) continue;
                if (requested != null && !requested.contains(schema.getName())) continue;

                sb.append(schema.getName()).append(":\n");
                for (com.excudo.core.parsing.Parameter p : schema.getParameters()) {
                    String llmName = p.getEffectiveLlmName();
                    sb.append("  ").append(llmName);
                    sb.append(" (").append(p.getType()).append(")");
                    if (p.isRequired()) sb.append(" REQUIRED");
                    if (p.getDefaultValue() != null) sb.append(" default=").append(p.getDefaultValue());
                    if (p.getValidValues() != null && !p.getValidValues().isEmpty()) {
                        sb.append(" values=[").append(String.join("|", p.getValidValues())).append("]");
                    }
                    if (p.getDescription() != null && !p.getDescription().isEmpty()) {
                        sb.append(" -- ").append(p.getDescription());
                    }
                    sb.append("\n");
                }
                String llmDesc = schema.getLlmDescription();
                if (llmDesc != null && !llmDesc.isEmpty()) {
                    sb.append("  NOTE: ").append(llmDesc).append("\n");
                }
                sb.append("\n");
            }

            return sb.toString();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private String handleGetAvailableLayouts() {
        try {
            List<LayoutInfo> layouts = getContextService().getAvailableLayoutsDetailed();
            if (layouts.isEmpty()) {
                return "No layouts available.";
            }
            StringBuilder sb = new StringBuilder("Available layouts:\n");
            for (LayoutInfo layout : layouts) {
                sb.append("  ").append(layout.getLayoutId())
                  .append(" - ").append(layout.getName()).append("\n");
            }
            sb.append("Use the layoutId value (e.g. slideLayout1) when creating slides.");
            return sb.toString();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private String handleGetSlideAnimations(String toolInput) {
        int slideNumber = getToolInputInt(toolInput, "slideNumber");
        try {
            ContextService.AnimationContext animCtx = getContextService().getAnimationContext(slideNumber);
            StringBuilder sb = new StringBuilder();
            sb.append("Animations on slide ").append(slideNumber).append(":\n");
            if (animCtx.getAnimationBindings().isEmpty()) {
                sb.append("  (none)\n");
            } else {
                for (AnimationBinding binding : animCtx.getAnimationBindings()) {
                    sb.append("  Target SPID ").append(binding.getTargetSpid());
                    sb.append(" | ").append(binding.getAnimationType());
                    sb.append(" | ").append(binding.getAnimationTypeName());
                    if (binding.getDuration() != null) {
                        sb.append(" | Duration: ").append(binding.getDuration());
                    }
                    sb.append("\n");
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    // ------------------------------------------------------------------
    // Command execution handler
    // ------------------------------------------------------------------

    private String handleExecuteCommands(String toolInput) {
        try {
            JsonObject inputObj = JsonHelper.parseObject(toolInput);
            String commandsJson = JsonHelper.getString(inputObj, "commands");

            // The LLM may embed the JSON array directly as the commands value,
            // or the whole toolInput may BE the commands array if the LLM passes it raw.
            if (commandsJson == null || commandsJson.isEmpty()) {
                if (toolInput != null && toolInput.trim().startsWith("[")) {
                    commandsJson = toolInput.trim();
                } else {
                    return "Error: No commands provided. Pass a JSON array of command objects in the 'commands' field.";
                }
            }

            // Parse JSON array into ActionRequests and bridge to ParsedCommands
            List<RequestSchema.ActionRequest> actions = parseActionsFromJsonArray(commandsJson);
            if (actions.isEmpty()) {
                return "Error: No valid commands parsed from JSON";
            }

            LLMRequestBridge bridge = new LLMRequestBridge();
            boolean hasContext = orchestrator.getContext().isPresent();

            int succeeded = 0;
            int failed = 0;
            StringBuilder details = new StringBuilder();

            for (RequestSchema.ActionRequest action : actions) {
                try {
                    String actionType = action.getType();
                    boolean isNewCommand = "new".equals(actionType) || "new-presentation".equals(actionType);

                    // Block 'new' if already called this session -- prevents reset loops
                    if (isNewCommand && (hasContext || presentationCreated)) {
                        failed++;
                        details.append("FAILED: new - A presentation already exists. ")
                               .append("Use 'create' to add slides. Use 'delete' to remove slides. ")
                               .append("Do NOT call 'new' again.\n");
                        continue;
                    }

                    // Block non-new commands when no presentation is loaded
                    if (!hasContext && !isNewCommand) {
                        failed++;
                        details.append("FAILED: ").append(actionType)
                               .append(" - No presentation loaded. Call 'new' first.\n");
                        continue;
                    }

                    // Pre-validate parameter values against schema constraints
                    String validationError = validateActionParameters(action, bridge);
                    if (validationError != null) {
                        failed++;
                        details.append("FAILED: ").append(actionType)
                               .append(" - ").append(validationError).append("\n");
                        continue;
                    }

                    com.excudo.core.parsing.ParsedCommand parsed = bridge.bridge(action);
                    Command command = commandFactory.createCommand(parsed, displayAdapter);
                    commandInvoker.executeCommand(command);
                    succeeded++;
                    details.append("OK: ").append(actionType).append("\n");

                    // After 'new', sync orchestrator reference and lock out future 'new' calls
                    if (isNewCommand && displayAdapter instanceof CommandSessionContext ctx) {
                        presentationCreated = true;
                        PPTXOrchestrator sessionOrch = ctx.getCurrentOrchestrator();
                        if (sessionOrch != null && sessionOrch != this.orchestrator) {
                            this.orchestrator = sessionOrch;
                            this.compoundShapeTools = new CompoundShapeTools(sessionOrch);
                            this.mermaidDiagramTool = new MermaidDiagramTool(sessionOrch);
                        }
                        hasContext = this.orchestrator.getContext().isPresent();
                    }
                } catch (Exception e) {
                    failed++;
                    details.append("FAILED: ").append(action.getType())
                           .append(" - ").append(e.getMessage()).append("\n");
                }
            }

            if (failed == 0) {
                return "OK: " + succeeded + " command(s) executed.";
            } else {
                // Only include failure details -- successes don't need per-command output
                StringBuilder failDetails = new StringBuilder();
                failDetails.append(succeeded).append(" OK, ").append(failed).append(" failed:\n");
                for (String line : details.toString().split("\n")) {
                    if (line.startsWith("FAILED:")) {
                        failDetails.append(line.substring("FAILED: ".length())).append("\n");
                    }
                }
                return failDetails.toString();
            }
        } catch (Exception e) {
            return "Command execution error: " + e.getMessage();
        }
    }

    /**
     * Validate an action's parameter values against schema constraints (e.g., validValues).
     * Returns an error message if validation fails, or null if the action is valid.
     */
    private String validateActionParameters(RequestSchema.ActionRequest action, LLMRequestBridge bridge) {
        try {
            String commandName = bridge.resolveCommandName(action.getType());
            com.excudo.core.parsing.CommandSchema schema =
                com.excudo.core.parsing.CommandRegistry.getSchema(commandName);
            if (schema == null) return null;

            Map<String, Object> params = action.getParameters();
            if (params == null) return null;

            for (com.excudo.core.parsing.Parameter p : schema.getParameters()) {
                if (p.getValidValues() == null || p.getValidValues().isEmpty()) continue;

                String llmName = p.getEffectiveLlmName();
                String canonicalName = p.getName();
                Object value = params.containsKey(llmName) ? params.get(llmName) : params.get(canonicalName);
                if (value == null) continue;

                String strValue = String.valueOf(value);
                if (!p.getValidValues().contains(strValue)) {
                    return "Invalid value for " + llmName + ": \"" + strValue
                        + "\". Must be one of: " + String.join(", ", p.getValidValues());
                }
            }
            return null;
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
    }

    /**
     * Parse a JSON array of command objects into ActionRequest list.
     * Handles the flat format: [{"type":"add-shape","slideNumber":1,...}]
     */
    private List<RequestSchema.ActionRequest> parseActionsFromJsonArray(String json) {
        List<RequestSchema.ActionRequest> actions = new ArrayList<>();
        try {
            JsonArray array = JsonHelper.parseArray(json);
            for (JsonElement el : array) {
                if (!el.isJsonObject()) continue;
                JsonObject obj = el.getAsJsonObject();
                RequestSchema.ActionRequest action = parseOneAction(obj);
                if (action != null) actions.add(action);
            }
        } catch (Exception e) {
            logger.error("Failed to parse actions array: {}", e.getMessage());
        }
        return actions;
    }

    /**
     * Parse a single JSON object into an ActionRequest.
     * The "type" (or "command") field becomes the action type; all other fields become parameters.
     */
    private RequestSchema.ActionRequest parseOneAction(JsonObject obj) {
        String type = obj.has("type") ? obj.get("type").getAsString() : null;
        if (type == null && obj.has("command")) {
            type = obj.get("command").getAsString();
        }
        if (type == null) return null;

        Map<String, Object> params = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            String key = entry.getKey();
            if ("type".equals(key) || "command".equals(key)) continue;

            JsonElement val = entry.getValue();
            if (val.isJsonPrimitive()) {
                JsonPrimitive prim = val.getAsJsonPrimitive();
                if (prim.isString()) {
                    params.put(key, prim.getAsString());
                } else if (prim.isNumber()) {
                    Number num = prim.getAsNumber();
                    // Preserve integer types for downstream code
                    if (num.doubleValue() == num.longValue()) {
                        params.put(key, num.longValue());
                    } else {
                        params.put(key, num.doubleValue());
                    }
                } else if (prim.isBoolean()) {
                    params.put(key, prim.getAsBoolean());
                }
            } else if (val.isJsonNull()) {
                // skip nulls
            } else {
                // Arrays or objects: store as raw JSON string for downstream
                params.put(key, GSON.toJson(val));
            }
        }

        return new RequestSchema.ActionRequest(type, params, null, null);
    }

    // ------------------------------------------------------------------
    // Layout validation handler
    // ------------------------------------------------------------------

    private String handleValidateLayout(String toolInput) {
        int slideNumber = getToolInputInt(toolInput, "slideNumber");
        try {
            ContextService.SlideContext slideCtx = getContextService().getSlideContext(slideNumber);
            List<SlideShape> shapes = slideCtx.getSlideData().getShapeRegistry().getAllShapes();

            LayoutValidator validator = new LayoutValidator();
            List<LayoutIssue> issues = validator.validate(shapes);

            if (issues.isEmpty()) {
                return "Slide " + slideNumber + " layout is valid. No issues detected.";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Slide ").append(slideNumber).append(" has ").append(issues.size()).append(" issue(s):\n");
            for (LayoutIssue issue : issues) {
                sb.append("  [").append(issue.getType()).append("] ").append(issue.getDescription()).append("\n");
                sb.append("    Suggestion: ").append(issue.getSuggestion()).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return "Error validating slide " + slideNumber + ": " + e.getMessage();
        }
    }

    // ------------------------------------------------------------------
    // Layout-first handlers
    // ------------------------------------------------------------------

    private String handleSuggestLayout(String toolInput) {
        try {
            List<String> availableLayouts = List.of();
            try {
                ContextService.PresentationContext ctx = getContextService().getPresentationContext();
                availableLayouts = ctx.getAvailableLayouts();
            } catch (Exception ignored) {}

            return "Available layouts: " + availableLayouts + ". "
                + "To create custom layouts, use execute_commands with: "
                + "1) duplicate-layout to clone an existing layout, "
                + "2) add-placeholder / remove-placeholder to modify placeholders, "
                + "3) create slides from the new layout with create_slide_from_layout.";
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private String handleCreateSlideFromLayout(String toolInput) {
        try {
            JsonObject input = JsonHelper.parseObject(toolInput);
            String layoutName = JsonHelper.getString(input, "layoutName");
            String title = JsonHelper.getString(input, "title");
            int afterSlide = JsonHelper.getInt(input, "afterSlide", 0);

            if (layoutName == null || layoutName.isEmpty()) {
                return "Error: layoutName is required";
            }
            if (title == null) title = "";

            // Resolve layout name to file-level ID (e.g. "Title Slide" -> "slideLayout1")
            String layoutId = resolveLayoutId(layoutName);

            // afterSlide is "insert after this slide number" (0 = beginning)
            // orchestrator.createSlide expects 1-based position
            int position = afterSlide + 1;

            SlideExecutionResult result = orchestrator.createSlide(position, title, layoutId);
            if (result == null) {
                return "Error: createSlide returned null -- orchestrator may not be initialized";
            }

            int newSlideNumber = result.getSlideNumber();
            StringBuilder sb = new StringBuilder();
            sb.append("Created slide ").append(newSlideNumber)
              .append(" from layout '").append(layoutId).append("'");
            if (!title.isEmpty()) {
                sb.append(" with title '").append(title).append("'");
            }
            sb.append(". Use execute_commands to add content.");
            return sb.toString();
        } catch (Exception e) {
            return "Error creating slide from layout: " + e.getMessage();
        }
    }

    /**
     * Resolve a layout name (human-readable or file ID) to a file-level layout ID.
     * Handles: "slideLayout1", "slideLayouts/slideLayout1.xml", "Title Slide", etc.
     */
    private String resolveLayoutId(String layoutName) {
        if (layoutName == null) return null;

        // Strip path prefix and .xml suffix if present
        String cleaned = layoutName;
        if (cleaned.contains("/")) {
            cleaned = cleaned.substring(cleaned.lastIndexOf('/') + 1);
        }
        if (cleaned.endsWith(".xml")) {
            cleaned = cleaned.substring(0, cleaned.length() - 4);
        }

        // If it already looks like a file ID (e.g. "slideLayout1"), use it directly
        if (cleaned.startsWith("slideLayout")) {
            return cleaned;
        }

        // Try to match human-readable name against available layouts
        try {
            List<LayoutInfo> layouts = getContextService().getAvailableLayoutsDetailed();
            for (LayoutInfo layout : layouts) {
                if (layout.getName().equalsIgnoreCase(layoutName)) {
                    return layout.getLayoutId();
                }
            }
            // Partial match fallback (e.g. "Title" matches "Title Slide")
            for (LayoutInfo layout : layouts) {
                if (layout.getName().toLowerCase().contains(layoutName.toLowerCase())
                        || layoutName.toLowerCase().contains(layout.getName().toLowerCase())) {
                    return layout.getLayoutId();
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to resolve layout name '{}': {}", layoutName, e.getMessage());
        }

        // Last resort: return as-is and let the downstream code handle the fallback
        logger.warn("Could not resolve layout name '{}' to a file ID. Passing through as-is (default fallback will apply).", layoutName);
        return layoutName;
    }

    // ------------------------------------------------------------------
    // Icon injection handler
    // ------------------------------------------------------------------

    private String handleInjectIcon(String toolInput) {
        try {
            JsonObject input = JsonHelper.parseObject(toolInput);
            int slideNumber = JsonHelper.getInt(input, "slideNumber", 1);
            String query = JsonHelper.getString(input, "query");
            String placement = JsonHelper.getString(input, "placement");

            if (query == null || query.isEmpty()) {
                return "Error: query is required";
            }

            Map<String, Object> geometry = null;
            if (placement != null && !placement.isEmpty()) {
                geometry = new HashMap<>();
                geometry.put("placement", placement);
            }

            com.excudo.core.results.ExecutionResult<List<Integer>> result =
                orchestrator.injectEnhancedContent(slideNumber, query, null, geometry);

            if (!result.isSuccess()) {
                return "Error injecting icon: " + result.getMessage();
            }

            List<Integer> spids = result.getData().orElse(List.of());
            if (spids.isEmpty()) {
                return "Icon injected on slide " + slideNumber + " but no SPID returned.";
            }

            int spid = spids.get(0);

            // Read back shape position so LLM can target it for animations
            try {
                var slideDataResult = orchestrator.getSlideData(slideNumber);
                if (slideDataResult.isSuccess()) {
                    com.excudo.core.model.ParsedSlideData slideData = slideDataResult.getData().orElse(null);
                    if (slideData != null) {
                        com.excudo.core.model.SlideShape shape = slideData.getShapeRegistry().getShape(spid);
                        if (shape != null && shape.getGeometry() != null) {
                            com.excudo.core.model.ShapeGeometry g = shape.getGeometry();
                            return "Injected icon '" + query + "' on slide " + slideNumber
                                + " with SPID " + spid
                                + " at position x=" + g.getX() + " y=" + g.getY()
                                + " size " + g.getWidth() + "x" + g.getHeight()
                                + ". You can target SPID " + spid + " for animations.";
                        }
                    }
                }
            } catch (Exception ignored) {}

            return "Injected icon '" + query + "' on slide " + slideNumber
                + " with SPID " + spid + ". You can target SPID " + spid + " for animations.";
        } catch (Exception e) {
            return "Error injecting icon: " + e.getMessage();
        }
    }

    private String handleRenderSlide(String toolInput) {
        try {
            JsonObject input = JsonHelper.parseObject(toolInput);
            int slideNumber = JsonHelper.getInt(input, "slideNumber", 1);
            int width = JsonHelper.getInt(input, "width", 1280);
            int height = JsonHelper.getInt(input, "height", 720);

            var context = orchestrator.getContext()
                .orElseThrow(() -> new IllegalStateException("No presentation loaded"));
            com.excudo.core.model.PPTXDocument doc = context.getDocument();

            // Resolve theme
            com.excudo.core.themes.ThemeDefinition theme = null;
            String themeId = (String) context.getContextData().get("themeId");
            if (themeId != null) {
                try { theme = com.excudo.core.themes.ThemeLoader.get(themeId); } catch (Exception ignored) {}
            }

            java.io.File outputFile = java.io.File.createTempFile("render-slide" + slideNumber + "-", ".png");

            com.excudo.core.commands.RenderSlideCommand.SlideRenderFunction renderFn =
                com.excudo.core.commands.UtilityCommandFactory.getSlideRenderFunction();
            if (renderFn == null) {
                return "Error: Render function not registered. Start from console to enable rendering.";
            }
            renderFn.render(doc, slideNumber, outputFile, width, height, theme);

            return "Rendered slide " + slideNumber + " to " + outputFile.getAbsolutePath()
                + " (" + width + "x" + height + ", " + outputFile.length() + " bytes)";
        } catch (Exception e) {
            return "Error rendering slide: " + e.getMessage();
        }
    }

    // ------------------------------------------------------------------
    // JSON extraction helpers
    // ------------------------------------------------------------------

    /**
     * Extract an integer from tool input JSON.
     */
    private int getToolInputInt(String toolInput, String key) {
        try {
            JsonObject obj = JsonHelper.parseObject(toolInput);
            return JsonHelper.getInt(obj, key, 1);
        } catch (Exception e) {
            return 1;
        }
    }
}
