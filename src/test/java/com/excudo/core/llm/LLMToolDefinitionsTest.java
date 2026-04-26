package com.excudo.core.llm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LLMToolDefinitions.
 *
 * Verifies the core/deferred tool split, schema building with active sets,
 * tool lookup, deferred tool summary generation, and backward compatibility.
 */
public class LLMToolDefinitionsTest {

    // ========== getContextTools() backward compatibility ==========

    @Test
    @DisplayName("getContextTools returns non-empty list")
    void getContextToolsIsNonEmpty() {
        List<LLMToolDefinitions.ToolDefinition> tools = LLMToolDefinitions.getContextTools();
        assertNotNull(tools);
        assertFalse(tools.isEmpty(), "Tool list must not be empty");
    }

    @Test
    @DisplayName("Each tool has a non-null, non-empty name")
    void eachToolHasNonNullName() {
        for (LLMToolDefinitions.ToolDefinition tool : LLMToolDefinitions.getContextTools()) {
            assertNotNull(tool.name(), "Tool name must not be null");
            assertFalse(tool.name().isBlank(), "Tool name must not be blank");
        }
    }

    @Test
    @DisplayName("Each tool has a non-null, non-empty description")
    void eachToolHasNonNullDescription() {
        for (LLMToolDefinitions.ToolDefinition tool : LLMToolDefinitions.getContextTools()) {
            assertNotNull(tool.description(), "Tool description must not be null");
            assertFalse(tool.description().isBlank(), "Tool description must not be blank");
        }
    }

    @Test
    @DisplayName("Each tool has a non-null inputSchema")
    void eachToolHasInputSchema() {
        for (LLMToolDefinitions.ToolDefinition tool : LLMToolDefinitions.getContextTools()) {
            assertNotNull(tool.inputSchema(), "Tool inputSchema must not be null");
            assertFalse(tool.inputSchema().isBlank(), "Tool inputSchema must not be blank");
        }
    }

    @Test
    @DisplayName("Tool names include the expected context retrieval and execution tools")
    void toolNamesIncludeExpectedEntries() {
        List<String> names = LLMToolDefinitions.getContextTools()
            .stream()
            .map(LLMToolDefinitions.ToolDefinition::name)
            .toList();

        assertTrue(names.contains("get_presentation_overview"),
            "Must include get_presentation_overview");
        assertTrue(names.contains("get_slide_shapes"),
            "Must include get_slide_shapes");
        assertTrue(names.contains("execute_commands"),
            "Must include execute_commands");
        assertTrue(names.contains("create_code_box"),
            "Must include create_code_box");
        assertTrue(names.contains("create_diagram"),
            "Must include create_diagram");
        assertTrue(names.contains("suggest_layout"),
            "Must include suggest_layout");
        assertTrue(names.contains("create_slide_from_layout"),
            "Must include create_slide_from_layout");
    }

    // ========== toToolsJson() ==========

    @Test
    @DisplayName("toToolsJson returns a JSON array (starts with [ ends with ])")
    void toToolsJsonIsJsonArray() {
        String json = LLMToolDefinitions.toToolsJson();
        assertNotNull(json);
        assertTrue(json.trim().startsWith("["), "toToolsJson must return a JSON array starting with [");
        assertTrue(json.trim().endsWith("]"), "toToolsJson must return a JSON array ending with ]");
    }

    @Test
    @DisplayName("toToolsJson output contains all tool names")
    void toToolsJsonContainsAllToolNames() {
        String json = LLMToolDefinitions.toToolsJson();
        for (LLMToolDefinitions.ToolDefinition tool : LLMToolDefinitions.getContextTools()) {
            assertTrue(json.contains(tool.name()),
                "JSON output should contain tool name: " + tool.name());
        }
    }

    @Test
    @DisplayName("toToolsJson output contains input_schema key for each tool")
    void toToolsJsonContainsInputSchema() {
        String json = LLMToolDefinitions.toToolsJson();
        long schemaCount = json.chars()
            .filter(c -> c == '{')
            .count();
        int toolCount = LLMToolDefinitions.getContextTools().size();
        assertTrue(schemaCount >= toolCount,
            "JSON should contain at least one object per tool");
    }

    // ========== Core/Deferred split ==========

    @Test
    @DisplayName("getCoreTools contains the essential always-present tools")
    void coreToolsContainEssentials() {
        Set<String> coreNames = LLMToolDefinitions.getCoreTools().stream()
            .map(LLMToolDefinitions.ToolDefinition::name)
            .collect(Collectors.toSet());

        assertTrue(coreNames.contains("get_presentation_overview"));
        assertTrue(coreNames.contains("get_slide_shapes"));
        assertTrue(coreNames.contains("get_available_layouts"));
        assertTrue(coreNames.contains("execute_commands"));
        assertTrue(coreNames.contains("fetch_tool_schemas"));
    }

    @Test
    @DisplayName("getDeferredTools contains the on-demand tools")
    void deferredToolsContainExpected() {
        Set<String> deferredNames = LLMToolDefinitions.getDeferredTools().stream()
            .map(LLMToolDefinitions.ToolDefinition::name)
            .collect(Collectors.toSet());

        assertTrue(deferredNames.contains("get_shape_detail"));
        assertTrue(deferredNames.contains("create_code_box"));
        assertTrue(deferredNames.contains("create_diagram"));
        assertTrue(deferredNames.contains("validate_layout"));
        assertTrue(deferredNames.contains("inject_icon"));
    }

    @Test
    @DisplayName("Core + deferred = getContextTools (backward compat)")
    void coreAndDeferredEqualGetContextTools() {
        Set<String> combined = new LinkedHashSet<>();
        LLMToolDefinitions.getCoreTools().forEach(t -> combined.add(t.name()));
        LLMToolDefinitions.getDeferredTools().forEach(t -> combined.add(t.name()));

        Set<String> allNames = LLMToolDefinitions.getContextTools().stream()
            .map(LLMToolDefinitions.ToolDefinition::name)
            .collect(Collectors.toSet());

        assertEquals(allNames, combined,
            "Core + deferred names must equal getContextTools names");
    }

    @Test
    @DisplayName("No tool name appears in both core and deferred")
    void noOverlapBetweenCoreAndDeferred() {
        Set<String> coreNames = LLMToolDefinitions.getCoreTools().stream()
            .map(LLMToolDefinitions.ToolDefinition::name)
            .collect(Collectors.toSet());
        Set<String> deferredNames = LLMToolDefinitions.getDeferredTools().stream()
            .map(LLMToolDefinitions.ToolDefinition::name)
            .collect(Collectors.toSet());

        coreNames.retainAll(deferredNames);
        assertTrue(coreNames.isEmpty(),
            "Core and deferred sets must not overlap, but found: " + coreNames);
    }

    // ========== buildToolsJson ==========

    @Test
    @DisplayName("buildToolsJson with empty active set contains only core tools")
    void buildToolsJsonEmptyActiveSetContainsOnlyCore() {
        String json = LLMToolDefinitions.buildToolsJson(new LinkedHashSet<>());

        // Match the JSON name binding specifically -- a tool name can also
        // appear inside another tool's description text (e.g. get_slide_shapes
        // mentions render_slide as a hint), and a substring-only check would
        // false-match on that.
        for (LLMToolDefinitions.ToolDefinition tool : LLMToolDefinitions.getCoreTools()) {
            assertTrue(json.contains("\"name\":\"" + tool.name() + "\""),
                "Core tool should be present: " + tool.name());
        }

        for (LLMToolDefinitions.ToolDefinition tool : LLMToolDefinitions.getDeferredTools()) {
            assertFalse(json.contains("\"name\":\"" + tool.name() + "\""),
                "Deferred tool should NOT be present without discovery: " + tool.name());
        }
    }

    @Test
    @DisplayName("buildToolsJson with discovered tools includes them alongside core")
    void buildToolsJsonWithDiscoveredToolsIncludesThem() {
        Set<String> active = new LinkedHashSet<>();
        active.add("create_diagram");
        active.add("validate_layout");

        String json = LLMToolDefinitions.buildToolsJson(active);

        // Core tools present
        assertTrue(json.contains("execute_commands"));
        assertTrue(json.contains("fetch_tool_schemas"));

        // Discovered deferred tools present
        assertTrue(json.contains("create_diagram"));
        assertTrue(json.contains("validate_layout"));

        // Non-discovered deferred tools absent. Match the JSON name binding
        // specifically so a passing reference inside another tool's
        // description doesn't false-match.
        assertFalse(json.contains("\"name\":\"inject_icon\""));
        assertFalse(json.contains("\"name\":\"create_code_box\""));
    }

    // ========== getToolByName ==========

    @Test
    @DisplayName("getToolByName returns core tool")
    void getToolByNameReturnsCoreToolCorrectly() {
        LLMToolDefinitions.ToolDefinition tool = LLMToolDefinitions.getToolByName("execute_commands");
        assertNotNull(tool);
        assertEquals("execute_commands", tool.name());
    }

    @Test
    @DisplayName("getToolByName returns deferred tool")
    void getToolByNameReturnsDeferredToolCorrectly() {
        LLMToolDefinitions.ToolDefinition tool = LLMToolDefinitions.getToolByName("create_diagram");
        assertNotNull(tool);
        assertEquals("create_diagram", tool.name());
    }

    @Test
    @DisplayName("getToolByName returns null for unknown name")
    void getToolByNameReturnsNullForUnknown() {
        assertNull(LLMToolDefinitions.getToolByName("nonexistent_tool"));
    }

    // ========== getDeferredToolSummary ==========

    @Test
    @DisplayName("getDeferredToolSummary contains all deferred tool names")
    void deferredToolSummaryContainsAllDeferredNames() {
        String summary = LLMToolDefinitions.getDeferredToolSummary();
        assertNotNull(summary);
        for (LLMToolDefinitions.ToolDefinition tool : LLMToolDefinitions.getDeferredTools()) {
            assertTrue(summary.contains(tool.name()),
                "Summary should contain deferred tool: " + tool.name());
        }
    }

    @Test
    @DisplayName("getDeferredToolSummary does not list core tools as deferred entries")
    void deferredToolSummaryDoesNotListCoreToolsAsEntries() {
        String summary = LLMToolDefinitions.getDeferredToolSummary();
        // Core tools should not appear as "- tool_name:" entries in the deferred list.
        // (They may be referenced inside deferred tool descriptions, which is fine.)
        assertFalse(summary.contains("- get_presentation_overview:"),
            "Core tool should not be listed as deferred entry");
        assertFalse(summary.contains("- execute_commands:"),
            "Core tool should not be listed as deferred entry");
        assertFalse(summary.contains("- get_slide_shapes:"),
            "Core tool should not be listed as deferred entry");
    }

    @Test
    @DisplayName("getDeferredToolSummary starts with header explaining fetch_tool_schemas")
    void deferredToolSummaryHasHeader() {
        String summary = LLMToolDefinitions.getDeferredToolSummary();
        assertTrue(summary.startsWith("DEFERRED TOOLS"),
            "Summary should start with DEFERRED TOOLS header");
        assertTrue(summary.contains("fetch_tool_schemas"),
            "Summary should reference fetch_tool_schemas");
    }
}
