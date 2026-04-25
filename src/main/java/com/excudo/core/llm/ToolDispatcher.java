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
    // disableHtmlEscaping so apostrophes and &/</>  characters in re-
    // serialized user content (array/object params like `content`) don't
    // come out as \u0027 / \u0026 / etc. The default Gson constructor
    // escapes them "for HTML safety", which leaks into our DrawingML
    // output where the downstream reader treats the string as literal.
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private PPTXOrchestrator orchestrator;
    // Not final: updateOrchestrator rebuilds this with a fresh CommandFactory
    // bound to the new orchestrator so commands like RenderSlideCommand (which
    // snapshot the orchestrator in their constructor) don't see the pre-load
    // stale reference.
    private CommandFactory commandFactory;
    private final CommandInvoker commandInvoker;
    private CommandDisplay displayAdapter;
    private CompoundShapeTools compoundShapeTools;
    private MermaidDiagramTool mermaidDiagramTool;
    private boolean presentationCreated = false;

    /**
     * File written by the most recent successful {@code render_slide}
     * invocation, or null. The MCP protocol handler reads this after
     * a dispatch to inline the PNG bytes in the tool-call response so
     * clients sandboxed away from the server's filesystem (e.g. Claude
     * Desktop over mcp-remote) can still see the render.
     */
    private volatile java.io.File lastRenderFile;

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
        // Rebuild the factory so every command built afterwards (and every
        // sub-factory inside it) points at the new orchestrator. Without
        // this, commands that snapshot the orchestrator in their ctors
        // (RenderSlideCommand, many others) keep the stale pre-load ref.
        this.commandFactory = new CommandFactory(newOrch);
        this.compoundShapeTools = new CompoundShapeTools(newOrch);
        this.mermaidDiagramTool = new MermaidDiagramTool(newOrch);
    }

    /**
     * Sync this dispatcher's local caches to the current active session.
     * Called at the top of every dispatch so a session changed by
     * another engine (GUI Open, console load) is picked up on the next
     * tool call instead of waiting for an explicit updateOrchestrator.
     * No-op when the active orchestrator is already the one we cached.
     */
    private void syncToActiveSession() {
        PPTXOrchestrator active = com.excudo.core.orchestration.SessionManager
            .getInstance().getActiveOrchestrator();
        if (active != null && active != this.orchestrator) {
            updateOrchestrator(active);
        }
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
            // First thing every tool call does: align this dispatcher's
            // cached orchestrator + sub-factories to whatever session is
            // active right now. Covers the MCP-created-deck-then-GUI-
            // edits path that the old updateOrchestrator-on-demand dance
            // missed.
            syncToActiveSession();

            String inputSnippet = toolInput != null
                ? toolInput.substring(0, Math.min(toolInput.length(), 300))
                : "(none)";
            logger.info("TOOL_CALL {} | input: {}", toolName, inputSnippet);

            String result = switch (toolName) {
                case "get_presentation_overview" -> handleGetPresentationOverview();
                case "get_slide_shapes"          -> handleGetSlideShapes(toolInput);
                case "get_shape_detail"          -> handleGetShapeDetail(toolInput);
                case "get_available_layouts"     -> handleGetAvailableLayouts();
                case "list_commands"             -> handleListCommands(toolInput);
                case "get_command_schemas"      -> handleGetCommandSchemas(toolInput);
                case "get_slide_animations"      -> handleGetSlideAnimations(toolInput);
                case "execute_commands"          -> handleExecuteCommands(toolInput);
                case "validate_layout"           -> handleValidateLayout(toolInput);
                case "create_code_box"           -> handleCreateCodeBox(toolInput);
                case "create_diagram"            -> mermaidDiagramTool.createMermaidDiagram(toolInput);
                case "suggest_layout"            -> handleSuggestLayout(toolInput);
                case "create_slide_from_layout"  -> handleCreateSlideFromLayout(toolInput);
                case "inject_icon"               -> handleInjectIcon(toolInput);
                case "render_slide"              -> handleRenderSlide(toolInput);
                case "render_slides"             -> handleRenderSlides(toolInput);
                case "list_animation_types"      -> handleListAnimationTypes();
                case "list_trigger_types"        -> handleListTriggerTypes();
                case "get_shape_style"           -> handleGetShapeStyle(toolInput);
                case "get_transition"            -> handleGetTransition(toolInput);
                case "get_layout_baseline"       -> handleGetLayoutBaseline(toolInput);
                case "get_group_bounds"          -> handleGetGroupBounds(toolInput);
                case "synthesize_slide_script"   -> handleSynthesizeSlideScript(toolInput);
                case "run_slide_script"          -> handleRunSlideScript(toolInput);
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
            sb.append('\n');
            sb.append("TIP: to compose a slide that's structurally similar to an existing one, "
                + "use synthesize_slide_script(slideNumber=<source>) to get an indexed list of "
                + "canonical commands + a JSON array, edit the entries that should differ "
                + "(title text, geometry, animation params, etc), then run_slide_script "
                + "(slideNumber=<target>, script=<edited JSON>) to apply. Invariants reproduce "
                + "exactly -- far less agent effort than re-specifying every shape.\n");
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
            var registry = slideCtx.getSlideData().getShapeRegistry();
            StringBuilder sb = new StringBuilder();
            sb.append("Slide ").append(slideNumber).append(":\n");
            for (SlideShape shape : registry.getAllShapes()) {
                int depth = registry.getNestingDepth(shape.getSpid());
                sb.append("  ");
                for (int i = 0; i < depth; i++) sb.append("  ");
                sb.append(shape.getSpid()).append(" ");
                // Display "TEXT_BOX" when the OOXML cNvSpPr/@txBox marker
                // is set -- preserves the authorial-intent distinction
                // between a Text Box (Insert -> Text Box) and a styled
                // rectangle that happens to contain text. Both are
                // structurally RECTANGLE in the model.
                sb.append(shape.isTextBox() ? "TEXT_BOX" : shape.getType().toString());
                if (shape.getName() != null) sb.append(" \"").append(shape.getName()).append("\"");
                sb.append("\n");
                // Full paragraph-aware text (bullets and all), indented
                // under the shape's one-liner. Routed through the single
                // ShapeTextWriter so this view doesn't silently diverge
                // from show-slide / show-shape like it used to.
                if (shape.hasText()) {
                    String nested = (depth <= 0) ? "    " : "    ".repeat(depth + 1);
                    com.excudo.core.model.ShapeTextWriter.writeTo(shape, sb, nested);
                }
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
            if (shape.hasText()) {
                sb.append("Text:\n");
                com.excudo.core.model.ShapeTextWriter.writeTo(shape, sb, "  ");
            }

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

    /**
     * Parse the {@code commands} argument of {@code get_command_schemas} into
     * a set of command names. Accepts three shapes so we're resilient to MCP
     * client quirks around array serialisation:
     * <ul>
     *   <li>JSON array: {@code ["save","load"]} — the canonical form.</li>
     *   <li>Stringified JSON array: {@code "[\"save\",\"load\"]"} — some MCP
     *       clients flatten arrays to strings before send.</li>
     *   <li>Comma-separated string: {@code "save, load"} — a natural shape
     *       an LLM might emit if confused about JSON boundaries.</li>
     *   <li>Bare string: {@code "save"} — single command.</li>
     * </ul>
     * Returns null when no usable names are found so the caller can treat it
     * as "no filter" (list everything).
     * <p>Package-private for direct unit testing.
     */
    static Set<String> parseRequestedCommands(JsonElement cmds) {
        if (cmds == null || cmds.isJsonNull()) return null;
        Set<String> out = new java.util.LinkedHashSet<>();

        if (cmds.isJsonArray()) {
            for (JsonElement el : cmds.getAsJsonArray()) {
                String s = el.getAsString().trim();
                if (!s.isEmpty()) out.add(s);
            }
            return out.isEmpty() ? null : out;
        }

        if (cmds.isJsonPrimitive()) {
            String s = cmds.getAsString().trim();
            if (s.isEmpty()) return null;

            // Stringified JSON array -- MCP clients sometimes flatten arrays.
            if (s.startsWith("[") && s.endsWith("]")) {
                try {
                    JsonElement parsed = com.google.gson.JsonParser.parseString(s);
                    if (parsed.isJsonArray()) {
                        for (JsonElement el : parsed.getAsJsonArray()) {
                            String name = el.getAsString().trim();
                            if (!name.isEmpty()) out.add(name);
                        }
                        return out.isEmpty() ? null : out;
                    }
                } catch (Exception ignored) {
                    // fall through; treat as literal below
                }
            }

            // Comma-separated
            if (s.contains(",")) {
                for (String part : s.split(",")) {
                    String name = part.trim();
                    if (!name.isEmpty()) out.add(name);
                }
                return out.isEmpty() ? null : out;
            }

            // Single bare name
            out.add(s);
            return out;
        }

        return null;
    }

    /**
     * Return a one-line summary of every LLM-enabled command. Cheap
     * alternative to get_command_schemas for discovery -- the agent can
     * scan the list, pick the 2-3 it needs, then fetch full parameters
     * via get_command_schemas.
     */
    private String handleListCommands(String toolInput) {
        // Dedup via identity -- a single CommandSchema may be registered
        // under multiple keys (e.g. "load" and "open" share one schema).
        java.util.Set<com.excudo.core.parsing.CommandSchema> seen =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        List<com.excudo.core.parsing.CommandSchema> schemas = new ArrayList<>();
        for (com.excudo.core.parsing.CommandSchema s
                : com.excudo.core.parsing.CommandRegistry.getAllSchemas().values()) {
            if (seen.add(s)) schemas.add(s);
        }
        schemas.sort(java.util.Comparator.comparing(com.excudo.core.parsing.CommandSchema::getName));

        StringBuilder sb = new StringBuilder();
        sb.append("AVAILABLE COMMANDS (use with execute_commands, then get_command_schemas for parameters):\n\n");
        int count = 0;
        for (com.excudo.core.parsing.CommandSchema schema : schemas) {
            if (!schema.isLlmEnabled()) continue;
            count++;
            sb.append(schema.getName())
              .append(" — ")
              .append(schema.getDescription() == null ? "" : schema.getDescription())
              .append('\n');
        }
        sb.append('\n').append(count).append(" commands total.\n");
        return sb.toString();
    }

    private String handleGetCommandSchemas(String toolInput) {
        try {
            // Check if specific commands were requested (string, array, or omitted)
            Set<String> requested = null;
            if (toolInput != null && !toolInput.isEmpty()) {
                JsonObject input = JsonHelper.parseObject(toolInput);
                if (input.has("commands")) {
                    requested = parseRequestedCommands(input.get("commands"));
                }
            }

            StringBuilder sb = new StringBuilder();
            sb.append("COMMAND REFERENCE (use with execute_commands):\n\n");

            // Dedup same as list_commands (see there for reasoning).
            java.util.Set<com.excudo.core.parsing.CommandSchema> seenSchemas =
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            List<com.excudo.core.parsing.CommandSchema> schemas = new ArrayList<>();
            for (com.excudo.core.parsing.CommandSchema s
                    : com.excudo.core.parsing.CommandRegistry.getAllSchemas().values()) {
                if (seenSchemas.add(s)) schemas.add(s);
            }
            schemas.sort(java.util.Comparator.comparing(com.excudo.core.parsing.CommandSchema::getName));

            Set<String> matched = new java.util.LinkedHashSet<>();

            for (com.excudo.core.parsing.CommandSchema schema : schemas) {
                if (!schema.isLlmEnabled()) continue;
                if (requested != null && !requested.contains(schema.getName())) continue;
                matched.add(schema.getName());

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

            // If the caller asked for specific names and any didn't match,
            // suggest fuzzy alternatives instead of returning an empty result.
            if (requested != null) {
                Set<String> unmatched = new java.util.LinkedHashSet<>(requested);
                unmatched.removeAll(matched);
                if (!unmatched.isEmpty()) {
                    List<String> allLlmNames = schemas.stream()
                        .filter(com.excudo.core.parsing.CommandSchema::isLlmEnabled)
                        .map(com.excudo.core.parsing.CommandSchema::getName)
                        .toList();
                    sb.append("\nNo commands matched: ")
                      .append(String.join(", ", unmatched)).append("\n");
                    for (String name : unmatched) {
                        List<String> suggestions =
                            com.excudo.utils.FuzzyMatcher.findTopMatches(name, allLlmNames, 3, 4);
                        if (!suggestions.isEmpty()) {
                            sb.append("  Did you mean for '").append(name).append("': ")
                              .append(String.join(", ", suggestions)).append("?\n");
                        }
                    }
                    sb.append("Call list_commands to see all available commands.\n");
                }
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
                // Inline placeholder geometry so agents can pick a layout
                // without instantiating a slide just to inspect its bounds.
                // EMU first (machine-parseable), then a parenthesized
                // inches summary for human reads.
                var geometries = layout.getPlaceholderGeometries();
                if (geometries != null && !geometries.isEmpty()) {
                    for (var g : geometries) {
                        sb.append("    ");
                        // Identify by type when available, else by idx.
                        String label = g.getPlaceholderType() != null
                            ? g.getPlaceholderType()
                            : ("idx=" + g.getPlaceholderIndex());
                        // Strip the "type:" prefix used internally for type-keyed entries.
                        if (label.startsWith("type:")) label = label.substring(5);
                        sb.append(label);
                        // Pad to a roughly consistent column width so the
                        // listing is scannable across layouts.
                        int padTo = 12;
                        for (int i = label.length(); i < padTo; i++) sb.append(' ');
                        sb.append(" @ (").append(g.getX()).append(", ").append(g.getY()).append(") ")
                          .append(g.getWidth()).append("x").append(g.getHeight()).append(" EMU")
                          .append(String.format("  (~%.2fin x ~%.2fin)",
                              g.getWidth() / 914400.0, g.getHeight() / 914400.0))
                          .append('\n');
                    }
                }
            }
            sb.append("Use the layoutId value (e.g. slideLayout1) when creating slides.");
            return sb.toString();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private com.excudo.core.introspection.SlideIntrospector introspector() {
        return new com.excudo.core.introspection.SlideIntrospector(orchestrator);
    }

    private String handleGetShapeStyle(String toolInput) {
        try {
            JsonObject input = JsonHelper.parseObject(toolInput);
            int slideNumber = JsonHelper.getInt(input, "slideNumber", 0);
            int spid = JsonHelper.getInt(input, "spid", 0);
            if (slideNumber <= 0 || spid <= 0) {
                return "Error: slideNumber and spid are required positive integers.";
            }
            com.excudo.core.model.ShapeStyle style = introspector().getShapeStyle(slideNumber, spid);
            if (style == null) {
                return "No shape found at slide " + slideNumber + ", SPID " + spid + ".";
            }
            StringBuilder sb = new StringBuilder();
            sb.append("Shape style for slide ").append(slideNumber).append(", SPID ").append(spid).append(":\n");
            sb.append("  fill: ").append(formatFill(style.getFill())).append('\n');
            sb.append("  line: ").append(formatLine(style.getLine())).append('\n');
            sb.append("  themeStyle: ").append(formatThemeStyleRef(style.getThemeStyle())).append('\n');
            return sb.toString();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private String handleGetTransition(String toolInput) {
        try {
            JsonObject input = JsonHelper.parseObject(toolInput);
            int slideNumber = JsonHelper.getInt(input, "slideNumber", 0);
            if (slideNumber <= 0) {
                return "Error: slideNumber is required.";
            }
            com.excudo.core.introspection.TransitionDescriptor t =
                introspector().getTransition(slideNumber);
            if (t == null) {
                return "Slide " + slideNumber + " has no transition (slide, layout, and master all declare none).";
            }
            StringBuilder sb = new StringBuilder();
            sb.append("Transition for slide ").append(slideNumber).append(":\n");
            sb.append("  type: ").append(t.type().getUserFriendlyName()).append('\n');
            sb.append("  speed: ").append(t.speed()).append('\n');
            if (t.durationMs() != null) sb.append("  durationMs: ").append(t.durationMs()).append('\n');
            if (t.autoAdvanceMs() != null) sb.append("  autoAdvanceMs: ").append(t.autoAdvanceMs()).append('\n');
            sb.append("  source: ").append(t.source()).append(" (")
              .append(t.source() == com.excudo.core.introspection.TransitionDescriptor.Source.SLIDE
                  ? "explicit override on this slide"
                  : "inherited from " + t.source().name().toLowerCase())
              .append(")\n");
            return sb.toString();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private String handleGetLayoutBaseline(String toolInput) {
        try {
            JsonObject input = JsonHelper.parseObject(toolInput);
            int slideNumber = JsonHelper.getInt(input, "slideNumber", 0);
            if (slideNumber <= 0) {
                return "Error: slideNumber is required.";
            }
            com.excudo.core.introspection.LayoutBaseline baseline =
                introspector().getLayoutBaseline(slideNumber);
            if (baseline == null) {
                return "No layout baseline resolvable for slide " + slideNumber + ".";
            }
            StringBuilder sb = new StringBuilder();
            sb.append("Layout baseline for slide ").append(slideNumber).append(":\n");
            sb.append("  layout: ").append(baseline.layout().getLayoutId())
              .append(" - ").append(baseline.layout().getName()).append('\n');
            sb.append("  placeholders: ").append(baseline.layout().getPlaceholderGeometries().size()).append('\n');
            sb.append("  masterStyles: ").append(baseline.masterStyles().size())
              .append(" group(s) -- ").append(baseline.masterStyles().keySet()).append('\n');
            sb.append("  colorMap entries: ").append(baseline.colorMap().size()).append('\n');
            sb.append("  backgroundHex: ")
              .append(baseline.backgroundHex() != null ? baseline.backgroundHex() : "(inherited)")
              .append('\n');
            return sb.toString();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private String handleGetGroupBounds(String toolInput) {
        try {
            JsonObject input = JsonHelper.parseObject(toolInput);
            int slideNumber = JsonHelper.getInt(input, "slideNumber", 0);
            int groupSpid = JsonHelper.getInt(input, "groupSpid", 0);
            if (slideNumber <= 0 || groupSpid <= 0) {
                return "Error: slideNumber and groupSpid are required positive integers.";
            }
            com.excudo.core.model.ShapeGeometry geom =
                introspector().getGroupBounds(slideNumber, groupSpid);
            if (geom == null) {
                return "No group found at slide " + slideNumber + ", SPID " + groupSpid
                    + ". Confirm the SPID targets a GROUP-type shape.";
            }
            return String.format("Group bounds (slide %d, SPID %d): "
                    + "x=%d y=%d w=%d h=%d rot=%d (EMU, 60000ths of a degree)",
                slideNumber, groupSpid,
                geom.getX(), geom.getY(), geom.getWidth(), geom.getHeight(), geom.getRotation());
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private String handleSynthesizeSlideScript(String toolInput) {
        try {
            JsonObject input = JsonHelper.parseObject(toolInput);
            int slideNumber = JsonHelper.getInt(input, "slideNumber", 0);
            if (slideNumber <= 0) {
                return "Error: slideNumber is required (positive integer).";
            }
            com.excudo.core.commands.readonly.SynthesizeSlideScriptCommand cmd =
                new com.excudo.core.commands.readonly.SynthesizeSlideScriptCommand(slideNumber, orchestrator);
            try {
                commandInvoker.executeCommand(cmd);
            } catch (com.excudo.core.commands.CommandExecutionException e) {
                return "Error synthesizing script: " + e.getMessage();
            }
            StringBuilder sb = new StringBuilder();
            sb.append("Synthesized script for slide ").append(slideNumber)
              .append(" (").append(cmd.getSpecCount()).append(" command")
              .append(cmd.getSpecCount() == 1 ? "" : "s").append("):\n");
            sb.append(cmd.getScriptSummary());
            if (!cmd.getWarnings().isEmpty()) {
                sb.append("\nWarnings (deltas the synthesizer couldn't express -- these won't ")
                  .append("round-trip through run_slide_script):\n");
                for (String w : cmd.getWarnings()) sb.append("  - ").append(w).append('\n');
            }
            sb.append("\nJSON for run_slide_script (copy verbatim or edit specific entries):\n");
            sb.append(cmd.getScriptJson());
            sb.append("\n\nWorkflow: to compose a structurally-similar slide, create a fresh ")
              .append("target slide with the same layout, then call run_slide_script on the ")
              .append("target after editing the entries that differ (title text, geometry, ")
              .append("animation params, etc). SPIDs are automatically remapped.");
            return sb.toString();
        } catch (Exception e) {
            return "Error synthesizing script: " + e.getMessage();
        }
    }

    private String handleCreateCodeBox(String toolInput) {
        try {
            JsonObject input = JsonHelper.parseObject(toolInput);
            int slideNumber = JsonHelper.getInt(input, "slideNumber", 0);
            String code = JsonHelper.getString(input, "code");
            String language = JsonHelper.getString(input, "language");
            if (slideNumber <= 0) {
                return "Error: slideNumber is required (positive integer).";
            }
            if (code == null || code.isEmpty()) {
                return "Error: 'code' is required";
            }
            long x = JsonHelper.getLong(input, "x", 838200L);
            long y = JsonHelper.getLong(input, "y", 1825625L);
            Long widthOrNull = input.has("width") ? JsonHelper.getLong(input, "width", 0L) : null;
            Long heightOrNull = input.has("height") ? JsonHelper.getLong(input, "height", 0L) : null;

            com.excudo.core.commands.mutating.slide.CreateCodeBoxCommand cmd =
                new com.excudo.core.commands.mutating.slide.CreateCodeBoxCommand(
                    slideNumber, code, language, x, y, widthOrNull, heightOrNull, orchestrator);
            try {
                commandInvoker.executeCommand(cmd);
            } catch (com.excudo.core.commands.CommandExecutionException e) {
                return "Error creating code box: " + e.getMessage();
            }

            Integer groupSpid = cmd.getGroupSpid();
            String langDesc = cmd.getLanguage();
            int lineCount = cmd.getLineCount();
            if (groupSpid != null) {
                return "Created code box on slide " + slideNumber
                    + " (group SPID " + groupSpid + ")."
                    + " Language: " + langDesc + ", " + lineCount + " lines."
                    + " Use SPID " + groupSpid + " to move or resize the entire code box.";
            }
            // Grouping failed but the panels exist as siblings.
            java.util.List<Integer> spids = cmd.getAllocatedSpids();
            return "Created code box on slide " + slideNumber
                + ": line numbers (SPID " + spids.get(0) + ") + code (SPID " + spids.get(1) + ")."
                + " Language: " + langDesc + ", " + lineCount + " lines.";
        } catch (Exception e) {
            return "Error creating code box: " + e.getMessage();
        }
    }

    private String handleRunSlideScript(String toolInput) {
        try {
            JsonObject input = JsonHelper.parseObject(toolInput);
            int slideNumber = JsonHelper.getInt(input, "slideNumber", 0);
            String scriptJson = JsonHelper.getString(input, "script");
            if (slideNumber <= 0) {
                return "Error: slideNumber is required (positive integer).";
            }
            if (scriptJson == null || scriptJson.isBlank()) {
                return "Error: script is required (JSON array of CommandSpec objects).";
            }
            com.excudo.core.commands.mutating.slide.RunSlideScriptCommand cmd =
                new com.excudo.core.commands.mutating.slide.RunSlideScriptCommand(
                    slideNumber, scriptJson, orchestrator, displayAdapter);
            try {
                commandInvoker.executeCommand(cmd);
            } catch (com.excudo.core.commands.CommandExecutionException e) {
                return "Script run FAILED on slide " + slideNumber + ": " + e.getMessage();
            }
            return "Ran " + cmd.getAppliedSpecCount() + " spec"
                + (cmd.getAppliedSpecCount() == 1 ? "" : "s")
                + " on slide " + slideNumber + ". All successful (undoable).";
        } catch (Exception e) {
            return "Error running script: " + e.getMessage();
        }
    }

    private String formatFill(com.excudo.core.model.ShapeFill fill) {
        if (fill == null) return "(inherit)";
        return switch (fill.getType()) {
            case SOLID -> "solid " + formatColor(fill.getColor());
            case NO_FILL -> "none";
            case GRADIENT -> "gradient";
        };
    }

    private String formatLine(com.excudo.core.model.ShapeLine line) {
        if (line == null) return "(inherit)";
        StringBuilder sb = new StringBuilder();
        if (line.getWidthEMU() != null && line.getWidthEMU() > 0) {
            sb.append(line.getWidthEMU()).append(" EMU ");
        }
        sb.append(line.getDashStyle() != null ? line.getDashStyle() : "solid");
        if (line.getColor() != null) {
            sb.append(' ').append(formatColor(line.getColor()));
        }
        return sb.toString();
    }

    private String formatColor(com.excudo.core.model.TextColor c) {
        if (c == null) return "(no color)";
        return c.isScheme() ? "scheme:" + c.getSchemeVal() : "#" + c.getHexVal();
    }

    private String formatThemeStyleRef(com.excudo.core.model.ThemeStyleRef ref) {
        if (ref == null) return "(default)";
        if (ref == com.excudo.core.model.ThemeStyleRef.NONE) return "none (text box)";
        return String.format("lnRef idx=%d(%s), fillRef idx=%d(%s), effectRef idx=%d(%s), fontRef idx=%s(%s)",
            ref.getLineRefIdx(), ref.getLineColor(),
            ref.getFillRefIdx(), ref.getFillColor(),
            ref.getEffectRefIdx(), ref.getEffectColor(),
            ref.getFontRefIdx(), ref.getFontColor());
    }

    private String handleListAnimationTypes() {
        StringBuilder sb = new StringBuilder();
        sb.append("Animation effects grouped by category. Use these with the `animation-type` ");
        sb.append("parameter on add-animation.\n\n");

        sb.append("ENTRANCE (reveal content):\n");
        appendAnimationCategory(sb, AnimationType.getEntranceTypes(), false);

        sb.append("\nEMPHASIS (highlight existing content):\n");
        appendAnimationCategory(sb, AnimationType.getEmphasisTypes(), false);

        sb.append("\nEXIT (remove content):\n");
        appendAnimationCategory(sb, AnimationType.getExitTypes(), false);

        sb.append("\nMOTION PATHS (move along a path):\n");
        AnimationType[] motions = Arrays.stream(AnimationType.values())
            .filter(AnimationType::isMotionPath)
            .toArray(AnimationType[]::new);
        appendAnimationCategory(sb, motions, false);

        return sb.toString();
    }

    private void appendAnimationCategory(StringBuilder sb, AnimationType[] types, boolean unused) {
        for (AnimationType type : types) {
            sb.append("  ").append(type.getUserFriendlyName());
            String guidance = type.getLlmGuidance();
            if (guidance != null && !guidance.isEmpty()) {
                sb.append(" - ").append(guidance);
            }
            sb.append('\n');
        }
    }

    private String handleListTriggerTypes() {
        StringBuilder sb = new StringBuilder();
        sb.append("Animation trigger types. Pass one of these as the `animationGroup` parameter on add-animation.\n\n");
        sb.append("  on-click        Fires on the next user click. Each on-click animation advances ");
        sb.append("the click sequence by one. Default for most authored decks.\n");
        sb.append("  with-previous   Fires simultaneously with the preceding animation. No extra click ");
        sb.append("needed. Use for bundling coordinated motion (e.g. a title fading in while a subtitle ");
        sb.append("slides up).\n");
        sb.append("  after-previous  Fires automatically after the preceding animation completes. No ");
        sb.append("extra click needed. Use for sequential reveals where the agent wants pacing without ");
        sb.append("requiring the presenter to click for each step.\n");
        return sb.toString();
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
                    // load and open both create context -- they are valid when
                    // no presentation exists yet (initial open) AND when one
                    // already exists (switch to a different file). They must
                    // not be blocked by the no-context gate.
                    boolean isContextCreatingCommand = isNewCommand
                        || "load".equals(actionType) || "open".equals(actionType);

                    // Block 'new' if already called this session -- prevents reset loops
                    if (isNewCommand && (hasContext || presentationCreated)) {
                        failed++;
                        details.append("FAILED: new - A presentation already exists. ")
                               .append("Use 'create' to add slides. Use 'delete' to remove slides. ")
                               .append("Do NOT call 'new' again.\n");
                        continue;
                    }

                    // Block non-context-creating commands when no presentation is loaded
                    if (!hasContext && !isContextCreatingCommand) {
                        failed++;
                        details.append("FAILED: ").append(actionType)
                               .append(" - No presentation loaded. Call 'new' to create a fresh deck ")
                               .append("or 'load' to open an existing .pptx file.\n");
                        continue;
                    }

                    // Strict-keys validation: reject unknown command type,
                    // non-LLM-enabled commands, and unknown parameter keys.
                    // Catches the silent-accept bug class where
                    // `{"type": "show-shape"}` or `move targetSpid=…` (wrong
                    // alias) used to come back OK and no-op.
                    String strictError = validateActionStrictly(action, bridge);
                    if (strictError != null) {
                        failed++;
                        details.append("FAILED: ").append(actionType)
                               .append(" - ").append(strictError).append("\n");
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

                    // Save and render write external state that stateless
                    // MCP callers need to verify. Pin the resulting file's
                    // absolute path + byte count to the batch output so the
                    // agent can confirm the write landed where they expect.
                    if ("save".equals(actionType) || "render".equals(actionType)) {
                        String filename = actionFileParam(action);
                        if (filename != null) {
                            java.io.File f = new java.io.File(filename);
                            if (f.exists() && f.length() > 0) {
                                details.append("  ")
                                    .append("save".equals(actionType) ? "saved " : "rendered ")
                                    .append(f.length())
                                    .append(" bytes to ")
                                    .append(f.getAbsolutePath())
                                    .append("\n");
                            } else {
                                details.append("  WARNING: ").append(actionType)
                                    .append(" reported OK but file ")
                                    .append(filename)
                                    .append(" does not exist or is empty\n");
                            }
                        }
                    }

                    // Any command that creates or swaps context (new, load, open)
                    // creates a fresh session orchestrator that the dispatcher's
                    // orchestrator reference must be resynchronised against --
                    // otherwise subsequent tool calls (render_slide, get_*, etc.)
                    // see the empty pre-load orchestrator and report "No
                    // presentation loaded". Only 'new' also flips
                    // presentationCreated to lock out the reset path.
                    if (isContextCreatingCommand && displayAdapter instanceof CommandSessionContext ctx) {
                        if (isNewCommand) presentationCreated = true;
                        PPTXOrchestrator sessionOrch = ctx.getCurrentOrchestrator();
                        if (sessionOrch != null && sessionOrch != this.orchestrator) {
                            updateOrchestrator(sessionOrch);
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
                StringBuilder ok = new StringBuilder("OK: ")
                    .append(succeeded).append(" command(s) executed.");
                // Preserve verification lines (saved/rendered + bytes + path)
                // even on fully-successful batches -- stateless MCP agents
                // can't otherwise tell where writes landed.
                for (String line : details.toString().split("\n")) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("saved ")
                            || trimmed.startsWith("rendered ")
                            || trimmed.startsWith("WARNING:")) {
                        ok.append("\n  ").append(trimmed);
                    }
                }
                return ok.toString();
            } else {
                // Only include failure details -- successes don't need per-command output
                StringBuilder failDetails = new StringBuilder();
                failDetails.append(succeeded).append(" OK, ").append(failed).append(" failed:\n");
                for (String line : details.toString().split("\n")) {
                    if (line.startsWith("FAILED:")) {
                        failDetails.append(line.substring("FAILED: ".length())).append("\n");
                    }
                }
                // Also surface any successful save/render verifications in
                // the mixed-outcome case -- the agent still needs to know
                // whether at least one write landed even if others failed.
                for (String line : details.toString().split("\n")) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("saved ") || trimmed.startsWith("rendered ")) {
                        failDetails.append("  ").append(trimmed).append("\n");
                    }
                }
                return failDetails.toString();
            }
        } catch (Exception e) {
            return "Command execution error: " + e.getMessage();
        }
    }

    /**
     * Pull the file path out of an action's params. save uses "filename",
     * render uses "output" -- both canonical and llm-name variants.
     */
    private static String actionFileParam(RequestSchema.ActionRequest action) {
        java.util.Map<String, Object> p = action.getParameters();
        Object v;
        v = p.get("filename");
        if (v instanceof String s && !s.isEmpty()) return s;
        v = p.get("output");
        if (v instanceof String s && !s.isEmpty()) return s;
        return null;
    }

    /**
     * Validate an action's parameter values against schema constraints (e.g., validValues).
     * Returns an error message if validation fails, or null if the action is valid.
     */
    /**
     * Strict input validation: reject unknown command types, non-LLM-enabled
     * commands, and unknown parameter keys. Returns an error string with a
     * fuzzy-match suggestion when applicable, or null if the action is well-
     * formed enough to bridge.
     *
     * <p>This catches the silent-accept bug class documented in the 2026-04-22
     * beta findings: `{"type":"show-shape"}` and `move {"targetSpid":N}`
     * used to return OK and no-op because show-shape is registered (just not
     * llmEnabled) and unknown keys were passed through to the factory which
     * then read the canonical name and got null.
     */
    static String validateActionStrictly(RequestSchema.ActionRequest action, LLMRequestBridge bridge) {
        String actionType = action.getType();
        if (actionType == null || actionType.isBlank()) {
            return "Missing 'type' field on command.";
        }

        // Resolve to canonical command name. The bridge throws on truly
        // unknown types; catch it and add a fuzzy "did you mean" suggestion.
        String commandName;
        try {
            commandName = bridge.resolveCommandName(actionType);
        } catch (IllegalArgumentException e) {
            String closest = com.excudo.utils.FuzzyMatcher.findClosestMatch(
                actionType, bridge.getLLMEnabledCommandNames(), 4);
            return "Unknown command type '" + actionType + "'."
                + (closest != null ? " Did you mean '" + closest + "'?" : "")
                + " Use list_commands to see available commands.";
        }

        com.excudo.core.parsing.CommandSchema schema =
            com.excudo.core.parsing.CommandRegistry.getSchema(commandName);
        if (schema == null) {
            return "Internal error: schema for '" + commandName + "' not found.";
        }

        // Reject REPL-only commands. show-shape, show, list, etc. are
        // registered in CommandRegistry for the REPL but aren't llmEnabled,
        // so they shouldn't be callable via execute_commands.
        if (!schema.isLlmEnabled()) {
            return "'" + commandName + "' is a REPL/internal command and is not callable from execute_commands. "
                + "Use a dedicated MCP tool or get_command_schemas to find an LLM-callable equivalent.";
        }

        // Build the set of accepted parameter keys: canonical names + llmName
        // aliases + any nested-wrapper keys the bridge knows how to flatten
        // for this action type.
        Set<String> accepted = new HashSet<>();
        for (com.excudo.core.parsing.Parameter p : schema.getParameters()) {
            accepted.add(p.getName());
            if (p.getLlmName() != null) accepted.add(p.getLlmName());
        }

        Map<String, Object> params = action.getParameters();
        if (params == null || params.isEmpty()) return null;

        List<String> unknownKeys = new ArrayList<>();
        for (String key : params.keySet()) {
            if (!accepted.contains(key)) unknownKeys.add(key);
        }
        if (unknownKeys.isEmpty()) return null;

        StringBuilder msg = new StringBuilder("Unknown parameter(s) for '")
            .append(commandName).append("':");
        for (String u : unknownKeys) {
            String closest = com.excudo.utils.FuzzyMatcher.findClosestMatch(u, accepted, 3);
            msg.append(" '").append(u).append("'");
            if (closest != null) msg.append(" (did you mean '").append(closest).append("'?)");
            msg.append(",");
        }
        // strip trailing comma
        if (msg.charAt(msg.length() - 1) == ',') msg.setLength(msg.length() - 1);
        msg.append(". Valid keys: ");
        List<String> sorted = new ArrayList<>(accepted);
        Collections.sort(sorted);
        msg.append(String.join(", ", sorted));
        return msg.toString();
    }

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
            String outputArg = JsonHelper.getString(input, "output");

            var context = orchestrator.getContext()
                .orElseThrow(() -> new IllegalStateException("No presentation loaded"));
            com.excudo.core.model.PPTXDocument doc = context.getDocument();

            // Resolve theme
            com.excudo.core.themes.ThemeDefinition theme = null;
            String themeId = (String) context.getContextData().get("themeId");
            if (themeId != null) {
                try { theme = com.excudo.core.themes.ThemeLoader.get(themeId); } catch (Exception ignored) {}
            }

            // Use the caller's path if provided, otherwise fall back to a temp file.
            // Over MCP the server's temp dir is often invisible to the client, so
            // a caller-specified path is the usual mode -- but the MCP handler
            // also inlines the PNG bytes via getLastRenderBytes(), so even with
            // a server-local temp file the client still sees the image.
            lastRenderFile = null;
            java.io.File outputFile;
            String displayPath;
            if (outputArg != null && !outputArg.isBlank()) {
                outputFile = new java.io.File(outputArg);
                if (outputFile.getParentFile() != null) {
                    outputFile.getParentFile().mkdirs();
                }
                // Report the exact path the caller passed. getAbsolutePath()
                // on Windows prepends the current drive to posix-style paths
                // (e.g. /mnt/foo becomes C-colon-backslash mnt-backslash foo)
                // which is confusing when the caller's environment actually
                // has /mnt available as a real mount. NB: a literal backslash
                // followed by 'u' would parse as a unicode escape, hence the
                // verbose phrasing above.
                displayPath = outputArg;
            } else {
                outputFile = java.io.File.createTempFile("render-slide" + slideNumber + "-", ".png");
                displayPath = outputFile.getAbsolutePath();
            }

            com.excudo.core.commands.readonly.RenderSlideCommand.SlideRenderFunction renderFn =
                com.excudo.core.commands.UtilityCommandFactory.getSlideRenderFunction();
            if (renderFn == null) {
                return "Error: Render function not registered. Start from console to enable rendering.";
            }
            java.util.Map<String, String> clrMap = orchestrator.getClrMap();
            String bgHex = orchestrator.getBackgroundColorHex(slideNumber);
            var masterStyles = orchestrator.getMasterStyles();
            java.util.List<String> warnings = renderFn.render(
                doc, slideNumber, outputFile, width, height, theme, clrMap, bgHex, masterStyles);
            lastRenderFile = outputFile;

            StringBuilder out = new StringBuilder();
            out.append("Rendered slide ").append(slideNumber).append(" to ").append(displayPath)
               .append(" (").append(width).append("x").append(height).append(", ")
               .append(outputFile.length()).append(" bytes)");
            if (warnings != null && !warnings.isEmpty()) {
                for (String w : warnings) {
                    out.append("\nNOTE: ").append(w);
                }
            }
            return out.toString();
        } catch (Exception e) {
            return "Error rendering slide: " + e.getMessage();
        }
    }

    private String handleRenderSlides(String toolInput) {
        try {
            JsonObject input = JsonHelper.parseObject(toolInput);
            int thumbWidth  = JsonHelper.getInt(input, "thumbWidth",  320);
            int thumbHeight = JsonHelper.getInt(input, "thumbHeight", 180);
            int columns     = JsonHelper.getInt(input, "columns",     3);
            int gutter      = JsonHelper.getInt(input, "gutter",      8);
            String outputArg = JsonHelper.getString(input, "output");

            var context = orchestrator.getContext()
                .orElseThrow(() -> new IllegalStateException("No presentation loaded"));
            com.excudo.core.model.PPTXDocument doc = context.getDocument();

            int[] slideNumbers;
            if (input.has("slideNumbers") && input.get("slideNumbers").isJsonArray()) {
                var arr = input.getAsJsonArray("slideNumbers");
                slideNumbers = new int[arr.size()];
                for (int i = 0; i < arr.size(); i++) {
                    slideNumbers[i] = arr.get(i).getAsInt();
                }
            } else {
                int total = doc.getSlideCount();
                slideNumbers = new int[total];
                for (int i = 0; i < total; i++) slideNumbers[i] = i + 1;
            }
            if (slideNumbers.length == 0) {
                return "Error: presentation has no slides to render.";
            }

            com.excudo.core.themes.ThemeDefinition theme = null;
            String themeId = (String) context.getContextData().get("themeId");
            if (themeId != null) {
                try { theme = com.excudo.core.themes.ThemeLoader.get(themeId); } catch (Exception ignored) {}
            }

            java.io.File outputFile;
            String displayPath;
            if (outputArg != null && !outputArg.isBlank()) {
                outputFile = new java.io.File(outputArg);
                if (outputFile.getParentFile() != null) outputFile.getParentFile().mkdirs();
                displayPath = outputArg;
            } else {
                outputFile = java.io.File.createTempFile("contact-sheet-", ".png");
                displayPath = outputFile.getAbsolutePath();
            }

            java.util.Map<String, String> clrMap = orchestrator.getClrMap();
            var masterStyles = orchestrator.getMasterStyles();
            java.util.function.IntFunction<String> bgPerSlide = orchestrator::getBackgroundColorHex;

            com.excudo.core.commands.UtilityCommandFactory.ContactSheetRenderFunction fn =
                com.excudo.core.commands.UtilityCommandFactory.getContactSheetRenderFunction();
            if (fn == null) {
                return "Error: Contact-sheet render function not registered. Start from console to enable rendering.";
            }
            int[] dims = fn.render(doc, slideNumbers, outputFile,
                thumbWidth, thumbHeight, columns, gutter,
                theme, clrMap, bgPerSlide, masterStyles);
            lastRenderFile = outputFile;

            int sheetW = dims[0];
            int sheetH = dims[1];
            int rows = (slideNumbers.length + Math.max(columns, 1) - 1) / Math.max(columns, 1);
            return String.format(
                "Rendered contact sheet to %s (%d slide%s, %dx%d grid, sheet %dx%d, %d bytes)",
                displayPath, slideNumbers.length, slideNumbers.length == 1 ? "" : "s",
                Math.max(columns, 1), rows, sheetW, sheetH, outputFile.length());
        } catch (Exception e) {
            return "Error rendering contact sheet: " + e.getMessage();
        }
    }

    /**
     * Read the bytes of the most recently rendered PNG, or {@code null}
     * if no render has succeeded since construction. Called by
     * {@code MCPProtocolHandler} after {@code render_slide} to inline
     * the image in the tool-call response.
     */
    public byte[] getLastRenderBytes() {
        java.io.File f = this.lastRenderFile;
        if (f == null || !f.exists()) return null;
        try {
            return java.nio.file.Files.readAllBytes(f.toPath());
        } catch (java.io.IOException e) {
            logger.warn("Failed to read last render file {}: {}", f, e.getMessage());
            return null;
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
