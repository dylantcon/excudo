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
            "All shapes on a slide: SPID, type, name, position, text.",
            "{\"type\":\"object\",\"properties\":{\"slideNumber\":{\"type\":\"integer\"}},\"required\":[\"slideNumber\"]}"
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
            "Animation bindings on a slide: targets, types, sequences.",
            "{\"type\":\"object\",\"properties\":{\"slideNumber\":{\"type\":\"integer\"}},\"required\":[\"slideNumber\"]}"
        ));

        tools.add(new ToolDefinition(
            "validate_layout",
            "Check slide for text overflow, shape overlap, off-slide, text too small.",
            "{\"type\":\"object\",\"properties\":{\"slideNumber\":{\"type\":\"integer\"}},\"required\":[\"slideNumber\"]}"
        ));

        tools.add(new ToolDefinition(
            "create_code_box",
            "Syntax-highlighted code box with line numbers and dark background.",
            "{\"type\":\"object\",\"properties\":{\"slideNumber\":{\"type\":\"integer\"},\"code\":{\"type\":\"string\"},\"language\":{\"type\":\"string\"},\"x\":{\"type\":\"integer\"},\"y\":{\"type\":\"integer\"},\"height\":{\"type\":\"integer\"}},\"required\":[\"slideNumber\",\"code\"]}"
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
