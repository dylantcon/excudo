package com.excudo.core.llm;

import com.excudo.core.commands.RequestSchema;
import com.excudo.core.parsing.CommandParameters;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.*;

/**
 * Tests for LLMRequestBridge - verifies that LLM ActionRequests are correctly
 * converted to CommandParameters using CommandSchema as the single source of truth.
 */
public class LLMRequestBridgeTest {

    // ========== ACTION TYPE RESOLUTION ==========

    @Test
    public void testResolveCanonicalCommandName() {
        // Direct command name should work
        assertEquals("create-slide", LLMRequestBridge.resolveCommandName("create-slide"));
        assertEquals("delete-slide", LLMRequestBridge.resolveCommandName("delete-slide"));
        assertEquals("content-edit", LLMRequestBridge.resolveCommandName("content-edit"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testResolveLegacyAliasIsNoLongerSupported() {
        // Legacy alias action-types (shape-addition, slide-creation,
        // slide-deletion, slide-copy) never resolved and still don't.
        // Note: add-animation, content-edit, bullet-point-edit, enhanced-content
        // are now CANONICAL names (derived from their classes) after the
        // class-registry sweep -- they DO resolve.
        LLMRequestBridge.resolveCommandName("shape-addition");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testResolveUnknownActionType() {
        LLMRequestBridge.resolveCommandName("nonexistent-action");
    }

    @Test
    public void testIsRecognizedActionType() {
        assertTrue(LLMRequestBridge.isRecognizedActionType("create-slide"));
        // content-edit is the canonical name (derived from ContentEditCommand)
        // after the class-registry sweep; the old "edit-content" alias was
        // intentionally not preserved.
        assertTrue(LLMRequestBridge.isRecognizedActionType("content-edit"));
        assertFalse(LLMRequestBridge.isRecognizedActionType("slide-creation"));
        assertFalse(LLMRequestBridge.isRecognizedActionType("edit-content"));
        assertFalse(LLMRequestBridge.isRecognizedActionType("totally-unknown"));
    }

    // ========== PARAMETER NAME MAPPING (CANONICAL NAMES ONLY) ==========

    @Test
    public void testBridgeCreate() {
        RequestSchema.ActionRequest action = new RequestSchema.ActionRequest(
            "create-slide",
            Map.of("position", 3, "title", "My Slide", "layoutId", "slideLayout2"),
            "Create a slide", null
        );

        CommandParameters cmd = LLMRequestBridge.bridge(action);
        assertEquals("create-slide", cmd.getCommandName());
        assertEquals("3", cmd.getString("position"));
        assertEquals("My Slide", cmd.getString("title"));
        // layoutId -> layout (via llmName mapping on the schema parameter)
        assertEquals("slideLayout2", cmd.getString("layout"));
    }

    @Test
    public void testBridgeEditContent() {
        RequestSchema.ActionRequest action = new RequestSchema.ActionRequest(
            "content-edit",
            Map.of("slideNumber", 1, "targetSpid", 5, "newText", "Hello World"),
            "Edit text", null
        );

        CommandParameters cmd = LLMRequestBridge.bridge(action);
        assertEquals("content-edit", cmd.getCommandName());
        // slideNumber -> slide, targetSpid -> spid, newText -> text
        assertEquals("1", cmd.getString("slide"));
        assertEquals("5", cmd.getString("spid"));
        assertEquals("Hello World", cmd.getString("text"));
    }

    @Test
    public void testBridgeDelete() {
        RequestSchema.ActionRequest action = new RequestSchema.ActionRequest(
            "delete-slide",
            Map.of("slideNumber", 3),
            "Delete slide", null
        );

        CommandParameters cmd = LLMRequestBridge.bridge(action);
        assertEquals("delete-slide", cmd.getCommandName());
        assertEquals("3", cmd.getString("slide"));
    }

    @Test
    public void testBridgeCopy() {
        RequestSchema.ActionRequest action = new RequestSchema.ActionRequest(
            "copy-slide",
            Map.of("sourceSlide", 2, "targetPosition", 5, "newTitle", "Copy"),
            "Copy slide", null
        );

        CommandParameters cmd = LLMRequestBridge.bridge(action);
        assertEquals("copy-slide", cmd.getCommandName());
        assertEquals("2", cmd.getString("slide"));
        assertEquals("5", cmd.getString("position"));
        assertEquals("Copy", cmd.getString("title"));
    }

    // ========== DIRECT COMMAND NAMES (NEW FORMAT) ==========

    @Test
    public void testBridgeWithCanonicalNames() {
        // LLM using new unified command names
        RequestSchema.ActionRequest action = new RequestSchema.ActionRequest(
            "content-edit",
            Map.of("slideNumber", 2, "targetSpid", 7, "newText", "Updated"),
            "Edit content", null
        );

        CommandParameters cmd = LLMRequestBridge.bridge(action);
        assertEquals("content-edit", cmd.getCommandName());
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

        CommandParameters cmd = LLMRequestBridge.bridge(action);
        assertEquals("apply-theme", cmd.getCommandName());
        assertEquals("corporate", cmd.getString("themeId"));
    }

    @Test
    public void testBridgeMove() {
        RequestSchema.ActionRequest action = new RequestSchema.ActionRequest(
            "move-shape",
            Map.of("slideNumber", 1, "targetSpid", 5, "x", "100pt", "y", "200pt"),
            "Move shape", null
        );

        CommandParameters cmd = LLMRequestBridge.bridge(action);
        assertEquals("move-shape", cmd.getCommandName());
        assertEquals("1", cmd.getString("slide"));
        assertEquals("5", cmd.getString("spid"));
        assertEquals("100pt", cmd.getString("x"));
        assertEquals("200pt", cmd.getString("y"));
    }

    @Test
    public void testBridgeArrange() {
        RequestSchema.ActionRequest action = new RequestSchema.ActionRequest(
            "arrange",
            Map.of("slideNumber", 1, "operation", "align-left", "targets", "all"),
            "Align shapes", null
        );

        CommandParameters cmd = LLMRequestBridge.bridge(action);
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
                new RequestSchema.ActionRequest("create-slide",
                    Map.of("position", 1, "title", "Intro"), "Create", null),
                new RequestSchema.ActionRequest("content-edit",
                    Map.of("slideNumber", 1, "targetSpid", 2, "newText", "Hello"), "Edit", null)
            ),
            null
        );

        List<CommandParameters> commands = LLMRequestBridge.bridgeAll(request);
        assertEquals(2, commands.size());
        assertEquals("create-slide", commands.get(0).getCommandName());
        assertEquals("content-edit", commands.get(1).getCommandName());
    }

    // ========== SCHEMA GENERATION ==========

    @Test
    public void testGenerateLLMToolsSchema() {
        String schema = LLMRequestBridge.generateLLMToolsSchema();
        assertNotNull(schema);
        assertTrue(schema.startsWith("["));
        assertTrue(schema.endsWith("]"));
        // Should contain LLM-enabled commands
        assertTrue(schema.contains("\"create-slide\""));
        assertTrue(schema.contains("\"content-edit\""));
        assertTrue(schema.contains("\"add-shape\""));
    }

    @Test
    public void testGenerateLLMCommandReference() {
        String ref = LLMRequestBridge.generateLLMCommandReference();
        assertNotNull(ref);
        assertTrue(ref.contains("COMMANDS:"));
        assertTrue(ref.contains("create-slide"));
        assertTrue(ref.contains("content-edit"));
        assertTrue(ref.contains("add-animation"));
        assertTrue(ref.contains("arrange"));
    }

    @Test
    public void testGetLLMEnabledCommandNames() {
        List<String> names = LLMRequestBridge.getLLMEnabledCommandNames();
        assertFalse(names.isEmpty());
        assertTrue(names.contains("create-slide"));
        assertTrue(names.contains("content-edit"));
        assertTrue(names.contains("add-shape"));
        assertTrue(names.contains("add-animation"));
        assertTrue(names.contains("arrange"));
        assertTrue(names.contains("move-shape"));
        assertTrue(names.contains("resize-shape"));
        assertTrue(names.contains("reorder-shape"));
        // Console-only commands should NOT be LLM-enabled
        assertFalse(names.contains("help"));
        assertFalse(names.contains("undo"));
        assertFalse(names.contains("list"));
    }
}
