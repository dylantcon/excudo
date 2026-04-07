package com.excudo.core.llm;

import com.excudo.core.commands.RequestSchema;
import com.excudo.core.parsing.ParsedCommand;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.*;

/**
 * Tests for LLMRequestBridge - verifies that LLM ActionRequests are correctly
 * converted to ParsedCommands using CommandSchema as the single source of truth.
 */
public class LLMRequestBridgeTest {

    private final LLMRequestBridge bridge = new LLMRequestBridge();

    // ========== ACTION TYPE RESOLUTION ==========

    @Test
    public void testResolveCanonicalCommandName() {
        // Direct command name should work
        assertEquals("create", bridge.resolveCommandName("create"));
        assertEquals("delete", bridge.resolveCommandName("delete"));
        assertEquals("edit-content", bridge.resolveCommandName("edit-content"));
    }

    @Test
    public void testResolveLegacyAlias() {
        // Legacy LLM action types should map to canonical names
        assertEquals("create", bridge.resolveCommandName("slide-creation"));
        assertEquals("delete", bridge.resolveCommandName("slide-deletion"));
        assertEquals("edit-content", bridge.resolveCommandName("content-edit"));
        assertEquals("add-shape", bridge.resolveCommandName("shape-addition"));
        assertEquals("add-animation", bridge.resolveCommandName("animation-edit"));
        assertEquals("enhance", bridge.resolveCommandName("enhanced-content"));
        assertEquals("copy", bridge.resolveCommandName("slide-copy"));
        assertEquals("apply-theme", bridge.resolveCommandName("apply-theme"));
        assertEquals("new", bridge.resolveCommandName("new-presentation"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testResolveUnknownActionType() {
        bridge.resolveCommandName("nonexistent-action");
    }

    @Test
    public void testIsRecognizedActionType() {
        assertTrue(bridge.isRecognizedActionType("create"));
        assertTrue(bridge.isRecognizedActionType("slide-creation"));
        assertTrue(bridge.isRecognizedActionType("content-edit"));
        assertFalse(bridge.isRecognizedActionType("totally-unknown"));
    }

    // ========== PARAMETER NAME MAPPING ==========

    @Test
    public void testBridgeSlideCreation() {
        RequestSchema.ActionRequest action = new RequestSchema.ActionRequest(
            "slide-creation",
            Map.of("position", 3, "title", "My Slide", "layoutId", "slideLayout2"),
            "Create a slide", null
        );

        ParsedCommand cmd = bridge.bridge(action);
        assertEquals("create", cmd.getCommandName());
        assertEquals("3", cmd.getString("position"));
        assertEquals("My Slide", cmd.getString("title"));
        // layoutId -> layout (via llmName mapping)
        assertEquals("slideLayout2", cmd.getString("layout"));
    }

    @Test
    public void testBridgeContentEdit() {
        RequestSchema.ActionRequest action = new RequestSchema.ActionRequest(
            "content-edit",
            Map.of("slideNumber", 1, "targetSpid", 5, "newText", "Hello World"),
            "Edit text", null
        );

        ParsedCommand cmd = bridge.bridge(action);
        assertEquals("edit-content", cmd.getCommandName());
        // slideNumber -> slide, targetSpid -> spid, newText -> text
        assertEquals("1", cmd.getString("slide"));
        assertEquals("5", cmd.getString("spid"));
        assertEquals("Hello World", cmd.getString("text"));
    }

    @Test
    public void testBridgeSlideDeletion() {
        RequestSchema.ActionRequest action = new RequestSchema.ActionRequest(
            "slide-deletion",
            Map.of("slideNumber", 3),
            "Delete slide", null
        );

        ParsedCommand cmd = bridge.bridge(action);
        assertEquals("delete", cmd.getCommandName());
        assertEquals("3", cmd.getString("slide"));
    }

    @Test
    public void testBridgeSlideCopy() {
        RequestSchema.ActionRequest action = new RequestSchema.ActionRequest(
            "slide-copy",
            Map.of("sourceSlide", 2, "targetPosition", 5, "newTitle", "Copy"),
            "Copy slide", null
        );

        ParsedCommand cmd = bridge.bridge(action);
        assertEquals("copy", cmd.getCommandName());
        assertEquals("2", cmd.getString("slide"));
        assertEquals("5", cmd.getString("position"));
        assertEquals("Copy", cmd.getString("title"));
    }

    // ========== NESTED OBJECT FLATTENING ==========

    @Test
    public void testBridgeAnimationEditWithNestedData() {
        Map<String, Object> animData = new HashMap<>();
        animData.put("animationType", "fade");
        animData.put("direction", "in");
        animData.put("clickTrigger", 1);

        Map<String, Object> params = new HashMap<>();
        params.put("slideNumber", 1);
        params.put("targetSpid", 5);
        params.put("animationData", animData);

        RequestSchema.ActionRequest action = new RequestSchema.ActionRequest(
            "animation-edit", params, "Add fade", null
        );

        ParsedCommand cmd = bridge.bridge(action);
        assertEquals("add-animation", cmd.getCommandName());
        assertEquals("1", cmd.getString("slide"));
        assertEquals("5", cmd.getString("spid"));
        assertEquals("fade", cmd.getString("type"));
        assertEquals("in", cmd.getString("direction"));
        // clickTrigger -> trigger
        assertEquals("1", cmd.getString("trigger"));
    }

    // ========== DIRECT COMMAND NAMES (NEW FORMAT) ==========

    @Test
    public void testBridgeWithCanonicalNames() {
        // LLM using new unified command names
        RequestSchema.ActionRequest action = new RequestSchema.ActionRequest(
            "edit-content",
            Map.of("slideNumber", 2, "targetSpid", 7, "newText", "Updated"),
            "Edit content", null
        );

        ParsedCommand cmd = bridge.bridge(action);
        assertEquals("edit-content", cmd.getCommandName());
        assertEquals("2", cmd.getString("slide"));
        assertEquals("7", cmd.getString("spid"));
        assertEquals("Updated", cmd.getString("text"));
    }

    @Test
    public void testBridgeApplyTheme() {
        RequestSchema.ActionRequest action = new RequestSchema.ActionRequest(
            "apply-theme",
            Map.of("themeId", "corporate"),
            "Apply theme", null
        );

        ParsedCommand cmd = bridge.bridge(action);
        assertEquals("apply-theme", cmd.getCommandName());
        assertEquals("corporate", cmd.getString("themeId"));
    }

    @Test
    public void testBridgeShapeMove() {
        RequestSchema.ActionRequest action = new RequestSchema.ActionRequest(
            "shape-move",
            Map.of("slideNumber", 1, "spid", 5, "x", "100pt", "y", "200pt"),
            "Move shape", null
        );

        ParsedCommand cmd = bridge.bridge(action);
        assertEquals("move", cmd.getCommandName());
        assertEquals("1", cmd.getString("slide"));
        assertEquals("5", cmd.getString("spid"));
        assertEquals("100pt", cmd.getString("x"));
        assertEquals("200pt", cmd.getString("y"));
    }

    @Test
    public void testBridgeShapeArrange() {
        RequestSchema.ActionRequest action = new RequestSchema.ActionRequest(
            "shape-arrange",
            Map.of("slideNumber", 1, "operation", "align-left", "targets", "all"),
            "Align shapes", null
        );

        ParsedCommand cmd = bridge.bridge(action);
        assertEquals("arrange", cmd.getCommandName());
        assertEquals("1", cmd.getString("slide"));
        assertEquals("align-left", cmd.getString("operation"));
        assertEquals("all", cmd.getString("targets"));
    }

    // ========== BATCH BRIDGING ==========

    @Test
    public void testBridgeAll() {
        RequestSchema.LLMRequest request = new RequestSchema.LLMRequest(
            "1.0",
            List.of(
                new RequestSchema.ActionRequest("slide-creation",
                    Map.of("position", 1, "title", "Intro"), "Create", null),
                new RequestSchema.ActionRequest("content-edit",
                    Map.of("slideNumber", 1, "targetSpid", 2, "newText", "Hello"), "Edit", null)
            ),
            null
        );

        List<ParsedCommand> commands = bridge.bridgeAll(request);
        assertEquals(2, commands.size());
        assertEquals("create", commands.get(0).getCommandName());
        assertEquals("edit-content", commands.get(1).getCommandName());
    }

    // ========== SCHEMA GENERATION ==========

    @Test
    public void testGenerateLLMToolsSchema() {
        String schema = LLMRequestBridge.generateLLMToolsSchema();
        assertNotNull(schema);
        assertTrue(schema.startsWith("["));
        assertTrue(schema.endsWith("]"));
        // Should contain LLM-enabled commands
        assertTrue(schema.contains("\"create\""));
        assertTrue(schema.contains("\"edit-content\""));
        assertTrue(schema.contains("\"add-shape\""));
    }

    @Test
    public void testGenerateLLMCommandReference() {
        String ref = LLMRequestBridge.generateLLMCommandReference();
        assertNotNull(ref);
        assertTrue(ref.contains("COMMANDS:"));
        assertTrue(ref.contains("create"));
        assertTrue(ref.contains("edit-content"));
        assertTrue(ref.contains("add-animation"));
        assertTrue(ref.contains("arrange"));
    }

    @Test
    public void testGetLLMEnabledCommandNames() {
        List<String> names = bridge.getLLMEnabledCommandNames();
        assertFalse(names.isEmpty());
        assertTrue(names.contains("create"));
        assertTrue(names.contains("edit-content"));
        assertTrue(names.contains("add-shape"));
        assertTrue(names.contains("add-animation"));
        assertTrue(names.contains("arrange"));
        assertTrue(names.contains("move"));
        assertTrue(names.contains("resize"));
        assertTrue(names.contains("reorder"));
        // Console-only commands should NOT be LLM-enabled
        assertFalse(names.contains("help"));
        assertFalse(names.contains("undo"));
        assertFalse(names.contains("list"));
    }
}
