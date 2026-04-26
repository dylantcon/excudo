package com.excudo.core.llm;

import com.google.gson.*;
import java.util.*;

/**
 * Defines tools available to the LLM for selective context retrieval.
 *
 * Tools are split into two tiers to minimize per-turn token cost:
 *
 *   CORE tools -- always declared with full JSON schemas in the API request.
 *   These are lightweight query tools the agent uses on almost every turn.
 *
 *   DEFERRED tools -- listed by name+description in the system prompt only.
 *   The agent calls fetch_tool_schemas to retrieve full schemas on demand,
 *   which are then injected into the tools array for subsequent turns.
 *
 * Token savings: ~1.5-2.5K input tokens per turn that does not use deferred
 * tools. Over a 10-turn conversation: 15-25K tokens saved.
 */
public class LLMToolDefinitions {

    private static final Gson GSON = new Gson();

    public record ToolDefinition(String name, String description, String inputSchema) {}

    // ========== CORE TOOLS (always declared) ==========

    /**
     * Tools that are always present in the API request with full schemas.
     * These are the lightweight, high-frequency tools the agent calls every session.
     */
    public static List<ToolDefinition> getCoreTools() {
        List<ToolDefinition> tools = new ArrayList<>();

        tools.add(new ToolDefinition(
            "get_presentation_overview",
            "Slide count, titles, layouts, theme. Call first.",
            "{\"type\":\"object\",\"properties\":{},\"required\":[]}"
        ));

        tools.add(new ToolDefinition(
            "get_slide_shapes",
            "All shapes on one or more slides: SPID, type, name, position, text. "
            + "Pass slideNumber for a single slide, or slideNumbers for a batch -- "
            + "either as an array ([1,3,7]), a range string (\"1-10\"), a "
            + "comma-separated mix (\"1,3,5-9,12\"), or \"all\" for the whole deck. "
            + "When a slide contains image content the response appends a NOTE so "
            + "the agent knows to call render_slide for visual context.",
            "{\"type\":\"object\",\"properties\":{"
            + "\"slideNumber\":{\"type\":\"integer\",\"description\":\"Single-slide form.\"},"
            + "\"slideNumbers\":{\"description\":\"Batch form: integer array, range string \\\"1-10\\\", comma-separated \\\"1,3,5-9\\\", or \\\"all\\\".\"}"
            + "},\"required\":[]}"
        ));

        tools.add(new ToolDefinition(
            "get_available_layouts",
            "Available slide layouts with IDs and names.",
            "{\"type\":\"object\",\"properties\":{},\"required\":[]}"
        ));

        tools.add(new ToolDefinition(
            "list_commands",
            "One-line summary of every command usable via execute_commands. "
            + "Cheap discovery -- pair with get_command_schemas for full parameter drill-down "
            + "on the 2-3 commands you actually need.",
            "{\"type\":\"object\",\"properties\":{},\"required\":[]}"
        ));

        tools.add(new ToolDefinition(
            "get_command_schemas",
            "Full parameter reference for one or more execute_commands command types. "
            + "When a name doesn't exist the response includes fuzzy suggestions. "
            + "Call list_commands first for discovery; use this for drill-down.",
            "{\"type\":\"object\",\"properties\":{\"commands\":{\"description\":\"Command name(s) to look up. String, array, or omit for all.\"}},\"required\":[]}"
        ));

        tools.add(new ToolDefinition(
            "execute_commands",
            "Execute a JSON command array.",
            "{\"type\":\"object\",\"properties\":{\"commands\":{\"type\":\"string\",\"description\":\"JSON array of command objects\"}},\"required\":[\"commands\"]}"
        ));

        tools.add(new ToolDefinition(
            "synthesize_slide_script",
            "Reverse-engineer the minimum DAG of canonical commands that, applied to a "
            + "slide's layout baseline, produces the slide's current state. Returns both a "
            + "numbered human-readable summary and a JSON array of CommandSpecs. PAIR WITH "
            + "run_slide_script for clone-with-overrides: synthesize slide S, edit specs by "
            + "index (e.g., swap text/geometry/color on specs 3, 7, 11), send the modified "
            + "JSON array to run_slide_script targeting a fresh slide. The invariants "
            + "reproduce exactly -- only the overridden specs differ. This is the fastest "
            + "way to compose a series of structurally-similar slides.",
            "{\"type\":\"object\",\"properties\":{"
            + "\"slideNumber\":{\"type\":\"integer\",\"description\":\"Source slide to synthesize.\"}"
            + "},\"required\":[\"slideNumber\"]}"
        ));

        tools.add(new ToolDefinition(
            "run_slide_script",
            "Apply a JSON array of CommandSpecs (from synthesize_slide_script, optionally "
            + "edited) to a target slide. SPID remapping is automatic: AddShapeSpec allocates "
            + "fresh SPIDs on the target slide, and downstream specs (animation targets, "
            + "group children, reorder targets) are rewritten to match. Full rollback on any "
            + "failing spec.",
            "{\"type\":\"object\",\"properties\":{"
            + "\"slideNumber\":{\"type\":\"integer\",\"description\":\"Target slide to apply the script to.\"},"
            + "\"script\":{\"type\":\"string\",\"description\":\"JSON array of CommandSpec objects.\"}"
            + "},\"required\":[\"slideNumber\",\"script\"]}"
        ));

        tools.add(new ToolDefinition(
            "fetch_tool_schemas",
            "Retrieve full schemas for additional tools listed in DEFERRED TOOLS. Pass tool names to activate them.",
            "{\"type\":\"object\",\"properties\":{\"tool_names\":{\"type\":\"array\",\"items\":{\"type\":\"string\"},\"description\":\"Tool names from DEFERRED TOOLS to retrieve\"}},\"required\":[\"tool_names\"]}"
        ));

        return tools;
    }

    // ========== DEFERRED TOOLS (on-demand schemas) ==========

    /**
     * Tools whose schemas are only sent after the agent explicitly requests them
     * via fetch_tool_schemas. Listed by name+description in the system prompt.
     */
    public static List<ToolDefinition> getDeferredTools() {
        List<ToolDefinition> tools = new ArrayList<>();

        tools.add(new ToolDefinition(
            "get_shape_detail",
            "Full detail for one shape: text, font, style, animations.",
            "{\"type\":\"object\",\"properties\":{\"slideNumber\":{\"type\":\"integer\"},\"spid\":{\"type\":\"integer\"}},\"required\":[\"slideNumber\",\"spid\"]}"
        ));

        tools.add(new ToolDefinition(
            "get_slide_animations",
            "Animation bindings on one or more slides. Each row: target SPID, "
            + "animation type, trigger (on-click/with-previous/after-previous), "
            + "duration in ms. Same slideNumber/slideNumbers shape as get_slide_shapes.",
            "{\"type\":\"object\",\"properties\":{"
            + "\"slideNumber\":{\"type\":\"integer\",\"description\":\"Single-slide form.\"},"
            + "\"slideNumbers\":{\"description\":\"Batch form: integer array, range string \\\"1-10\\\", comma-separated \\\"1,3,5-9\\\", or \\\"all\\\".\"}"
            + "},\"required\":[]}"
        ));

        tools.add(new ToolDefinition(
            "validate_layout",
            "Check slide for text overflow, shape overlap, off-slide, text too small.",
            "{\"type\":\"object\",\"properties\":{\"slideNumber\":{\"type\":\"integer\"}},\"required\":[\"slideNumber\"]}"
        ));

        tools.add(new ToolDefinition(
            "create_code_box",
            "Syntax-highlighted code box with line numbers and dark background. Width and height auto-size from content (longest line + line count) by default; pass explicit width/height EMU to fill a layout column or reserve space for future content. Pass lineNumberColor to recolor the gutter to match a theme accent.",
            "{\"type\":\"object\",\"properties\":"
            + "{\"slideNumber\":{\"type\":\"integer\"},"
            + "\"code\":{\"type\":\"string\"},"
            + "\"language\":{\"type\":\"string\"},"
            + "\"x\":{\"type\":\"integer\",\"description\":\"X position in EMU; default 838200\"},"
            + "\"y\":{\"type\":\"integer\",\"description\":\"Y position in EMU; default 1825625\"},"
            + "\"width\":{\"type\":\"integer\",\"description\":\"Optional total width in EMU. Default: auto-sized from longest code line + line-number gutter. Pass explicitly to fill a layout column when content is short.\"},"
            + "\"height\":{\"type\":\"integer\",\"description\":\"Optional height in EMU. Default: auto-sized from line count.\"},"
            + "\"lineNumberColor\":{\"type\":\"string\",\"description\":\"Optional hex color (no leading #) for the line-number gutter. Default: 858585 (dim gray).\"}"
            + "},\"required\":[\"slideNumber\",\"code\"]}"
        ));

        tools.add(new ToolDefinition(
            "create_diagram",
            "Native OOXML diagram from mermaid syntax. Flowcharts (graph TD) and sequence diagrams.",
            "{\"type\":\"object\",\"properties\":{\"slideNumber\":{\"type\":\"integer\"},\"mermaid\":{\"type\":\"string\",\"description\":\"Mermaid syntax\"},\"x\":{\"type\":\"integer\"},\"y\":{\"type\":\"integer\"},\"width\":{\"type\":\"integer\"},\"height\":{\"type\":\"integer\"}},\"required\":[\"slideNumber\",\"mermaid\"]}"
        ));

        tools.add(new ToolDefinition(
            "suggest_layout",
            "Suggest best existing layout match for your needs. Use execute_commands with duplicate-layout/add-placeholder to create custom layouts.",
            "{\"type\":\"object\",\"properties\":{\"layoutName\":{\"type\":\"string\"},\"placeholders\":{\"type\":\"string\"}},\"required\":[\"layoutName\"]}"
        ));

        tools.add(new ToolDefinition(
            "create_slide_from_layout",
            "New slide from existing layout with optional title.",
            "{\"type\":\"object\",\"properties\":{\"layoutName\":{\"type\":\"string\"},\"afterSlide\":{\"type\":\"integer\"},\"title\":{\"type\":\"string\"}},\"required\":[\"layoutName\",\"afterSlide\"]}"
        ));

        tools.add(new ToolDefinition(
            "render_slide",
            "Render a slide to PNG. Pass 'output' to control where the file is written "
            + "(useful when running over MCP where the server's temp dir is invisible to the "
            + "client). If omitted, writes to a server-local temp file. Returns the absolute "
            + "path written.",
            "{\"type\":\"object\",\"properties\":{"
            + "\"slideNumber\":{\"type\":\"integer\"},"
            + "\"width\":{\"type\":\"integer\"},"
            + "\"height\":{\"type\":\"integer\"},"
            + "\"output\":{\"type\":\"string\",\"description\":"
            + "\"Optional absolute path for the PNG. Server falls back to a temp file if omitted.\"}"
            + "},\"required\":[\"slideNumber\"]}"
        ));

        tools.add(new ToolDefinition(
            "inject_icon",
            "Place an icon on a slide by keyword search. Returns SPID of injected image.",
            "{\"type\":\"object\",\"properties\":{\"slideNumber\":{\"type\":\"integer\"},\"query\":{\"type\":\"string\"},\"placement\":{\"type\":\"string\",\"description\":\"auto|top-right|top-left|bottom-right|bottom-left\"}},\"required\":[\"slideNumber\",\"query\"]}"
        ));

        tools.add(new ToolDefinition(
            "render_slides",
            "Render multiple slides as a contact sheet PNG (grid of thumbnails). Defaults to "
            + "all slides, 3 columns, 320x180 thumbnails. Useful for 'show me the deck at a glance' "
            + "without N separate render_slide calls.",
            "{\"type\":\"object\",\"properties\":{"
            + "\"slideNumbers\":{\"type\":\"array\",\"items\":{\"type\":\"integer\"},\"description\":\"1-indexed slide numbers to include. Omit for all slides.\"},"
            + "\"thumbWidth\":{\"type\":\"integer\",\"description\":\"Pixel width per thumbnail; default 320\"},"
            + "\"thumbHeight\":{\"type\":\"integer\",\"description\":\"Pixel height per thumbnail; default 180\"},"
            + "\"columns\":{\"type\":\"integer\",\"description\":\"Grid columns; default 3\"},"
            + "\"gutter\":{\"type\":\"integer\",\"description\":\"Pixels of transparent padding between thumbnails; default 8\"},"
            + "\"output\":{\"type\":\"string\",\"description\":\"Optional absolute path. Falls back to a server-local temp file.\"}"
            + "},\"required\":[]}"
        ));

        tools.add(new ToolDefinition(
            "get_shape_style",
            "Typed style (fill, line, themeStyleRef) for one shape. Returns the shape's "
            + "own declared overrides -- what the synthesizer would emit as CommandSpecs to "
            + "reproduce the current styling from the layout baseline.",
            "{\"type\":\"object\",\"properties\":{\"slideNumber\":{\"type\":\"integer\"},\"spid\":{\"type\":\"integer\"}},\"required\":[\"slideNumber\",\"spid\"]}"
        ));

        tools.add(new ToolDefinition(
            "get_transition",
            "Effective slide transition, resolved through the slide > layout > master "
            + "inheritance chain. Reports the type, speed, duration, auto-advance timer, "
            + "AND which level of the chain declared it (SLIDE override vs. inherited).",
            "{\"type\":\"object\",\"properties\":{\"slideNumber\":{\"type\":\"integer\"}},\"required\":[\"slideNumber\"]}"
        ));

        tools.add(new ToolDefinition(
            "get_group_bounds",
            "Slide-space bounding box (x, y, width, height in EMU + rotation in 60000ths of a "
            + "degree) for a group shape. Reads the parser's post-transform geometry -- cheap, no "
            + "re-walk of the tree. Use when laying out multi-group compositions without "
            + "instantiating a full get_slide_shapes call.",
            "{\"type\":\"object\",\"properties\":{\"slideNumber\":{\"type\":\"integer\"},\"groupSpid\":{\"type\":\"integer\"}},\"required\":[\"slideNumber\",\"groupSpid\"]}"
        ));

        tools.add(new ToolDefinition(
            "get_layout_baseline",
            "Layout baseline for a slide: the placeholder geometries, master txStyles, "
            + "color map, and background color that the slide would have if no user edits "
            + "had been applied. The synthesizer diffs current state against this.",
            "{\"type\":\"object\",\"properties\":{\"slideNumber\":{\"type\":\"integer\"}},\"required\":[\"slideNumber\"]}"
        ));

        tools.add(new ToolDefinition(
            "list_animation_types",
            "Available animation effects grouped by category (entrance, emphasis, exit, motion). "
            + "Use to discover valid effect names before calling add-animation via execute_commands.",
            "{\"type\":\"object\",\"properties\":{},\"required\":[]}"
        ));

        tools.add(new ToolDefinition(
            "list_trigger_types",
            "Available animation trigger types (on-click, with-previous, after-previous) with their "
            + "semantics. Use to pick the right sequencing mode when authoring animations.",
            "{\"type\":\"object\",\"properties\":{},\"required\":[]}"
        ));

        tools.add(new ToolDefinition(
            "list_shape_types",
            "Every value of the SlideShape.ShapeType enum, grouped by category, valid as the "
            + "shape-type / shapeType parameter on add-shape. Use to discover available autoshape "
            + "presets without guessing names like OVAL (which is ELLIPSE).",
            "{\"type\":\"object\",\"properties\":{},\"required\":[]}"
        ));

        return tools;
    }

    // ========== COMBINED (backward compat) ==========

    /**
     * All tools -- core + deferred. Retained for backward compatibility.
     */
    public static List<ToolDefinition> getContextTools() {
        List<ToolDefinition> all = new ArrayList<>(getCoreTools());
        all.addAll(getDeferredTools());
        return all;
    }

    // ========== LOOKUP ==========

    /**
     * Look up a tool definition by name across both core and deferred sets.
     * @return the ToolDefinition, or null if not found
     */
    public static ToolDefinition getToolByName(String name) {
        for (ToolDefinition t : getCoreTools()) {
            if (t.name().equals(name)) return t;
        }
        for (ToolDefinition t : getDeferredTools()) {
            if (t.name().equals(name)) return t;
        }
        return null;
    }

    // ========== JSON BUILDERS ==========

    /**
     * Build the tools JSON array containing core tools + any discovered deferred tools.
     *
     * @param activeToolNames set of tool names to include beyond the core set.
     *                        Typically populated by fetch_tool_schemas calls.
     * @return JSON array string for the Anthropic API tools parameter
     */
    public static String buildToolsJson(Set<String> activeToolNames) {
        JsonArray array = new JsonArray();

        // Always include core tools
        for (ToolDefinition tool : getCoreTools()) {
            array.add(toolToJson(tool));
        }

        // Include any deferred tools that have been discovered
        if (activeToolNames != null && !activeToolNames.isEmpty()) {
            for (ToolDefinition tool : getDeferredTools()) {
                if (activeToolNames.contains(tool.name())) {
                    array.add(toolToJson(tool));
                }
            }
        }

        return GSON.toJson(array);
    }

    /**
     * Build the full tools JSON array with ALL tools (core + all deferred).
     * Used for backward compat and the full-fat path.
     */
    public static String toToolsJson() {
        JsonArray array = new JsonArray();
        for (ToolDefinition tool : getContextTools()) {
            array.add(toolToJson(tool));
        }
        return GSON.toJson(array);
    }

    /**
     * Compact deferred tool summary for inclusion in the system prompt.
     * Lists name + one-line description so the agent knows what's available.
     */
    public static String getDeferredToolSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("DEFERRED TOOLS (call fetch_tool_schemas with tool names to activate):\n");
        for (ToolDefinition tool : getDeferredTools()) {
            sb.append("- ").append(tool.name()).append(": ").append(tool.description()).append("\n");
        }
        return sb.toString();
    }

    // ========== SMALL MODEL (unchanged) ==========

    /**
     * Reduced tool set for small/local models (e.g. Ollama with 7-14B params).
     * Small models cannot handle meta-tool patterns, so they get a flat set.
     */
    public static List<ToolDefinition> getSmallModelTools() {
        List<ToolDefinition> tools = new ArrayList<>();

        tools.add(new ToolDefinition(
            "get_presentation_overview",
            "Slide count, titles, layouts, theme.",
            "{\"type\":\"object\",\"properties\":{},\"required\":[]}"
        ));

        tools.add(new ToolDefinition(
            "get_slide_shapes",
            "All shapes on a slide: SPID, type, name, text.",
            "{\"type\":\"object\",\"properties\":{\"slideNumber\":{\"type\":\"integer\"}},\"required\":[\"slideNumber\"]}"
        ));

        tools.add(new ToolDefinition(
            "get_available_layouts",
            "Available slide layouts with IDs and names.",
            "{\"type\":\"object\",\"properties\":{},\"required\":[]}"
        ));

        tools.add(new ToolDefinition(
            "create_slide_from_layout",
            "New slide from existing layout with optional title.",
            "{\"type\":\"object\",\"properties\":{\"layoutName\":{\"type\":\"string\"},\"afterSlide\":{\"type\":\"integer\"},\"title\":{\"type\":\"string\"}},\"required\":[\"layoutName\",\"afterSlide\"]}"
        ));

        tools.add(new ToolDefinition(
            "execute_commands",
            "Execute a JSON command array.",
            "{\"type\":\"object\",\"properties\":{\"commands\":{\"type\":\"string\"}},\"required\":[\"commands\"]}"
        ));

        return tools;
    }

    /**
     * Build the tools JSON array for small/local models.
     */
    public static String toSmallModelToolsJson() {
        JsonArray array = new JsonArray();
        for (ToolDefinition tool : getSmallModelTools()) {
            array.add(toolToJson(tool));
        }
        return GSON.toJson(array);
    }

    // ========== INTERNAL ==========

    private static JsonObject toolToJson(ToolDefinition tool) {
        JsonObject obj = new JsonObject();
        obj.addProperty("name", tool.name());
        obj.addProperty("description", tool.description());
        obj.add("input_schema", JsonParser.parseString(tool.inputSchema()));
        return obj;
    }
}
