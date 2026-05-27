package com.excudo.core.parsing;

import com.excudo.core.commands.mutating.slide.ResizeShapeCommand;
import com.excudo.core.commands.mutating.slide.ReorderShapeCommand;
import com.excudo.core.commands.mutating.slide.MoveShapeCommand;
import com.excudo.core.commands.mutating.slide.ContentEditCommand;
import com.excudo.core.commands.mutating.slide.AddShapeCommand;
import com.excudo.core.commands.mutating.slide.AddAnimationCommand;
import com.excudo.core.commands.mutating.deck.DeleteSlideCommand;
import com.excudo.core.commands.mutating.deck.CreateSlideCommand;
import com.excudo.core.commands.mutating.deck.CopySlideCommand;
import com.excudo.core.commands.meta.UndoCommand;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.*;

/**
 * Tests for {@link CommandSchema#bridgeLlmParams(Map)} and the registry
 * helpers that back the LLM dispatch path: schema lookup, LLM-name
 * canonicalization, and value coercion.
 */
public class CommandSchemaBridgeTest {

    // ========== ACTION TYPE RESOLUTION ==========

    @Test
    public void testRegistryResolvesCanonicalCommandName() {
        assertNotNull(CommandRegistry.getSchema(CreateSlideCommand.NAME));
        assertNotNull(CommandRegistry.getSchema(DeleteSlideCommand.NAME));
        assertNotNull(CommandRegistry.getSchema(ContentEditCommand.NAME));
    }

    @Test
    public void testRegistryRejectsLegacyAliases() {
        // Legacy alias action-types (shape-addition, slide-creation,
        // slide-deletion, slide-copy, edit-content) were not preserved
        // by the class-registry sweep -- only class-derived names resolve.
        assertNull(CommandRegistry.getSchema("shape-addition"));
        assertNull(CommandRegistry.getSchema("slide-creation"));
        assertNull(CommandRegistry.getSchema("edit-content"));
        assertNull(CommandRegistry.getSchema("nonexistent-action"));
    }

    @Test
    public void testHasCommand() {
        assertTrue(CommandRegistry.hasCommand(CreateSlideCommand.NAME));
        assertTrue(CommandRegistry.hasCommand(ContentEditCommand.NAME));
        assertFalse(CommandRegistry.hasCommand("slide-creation"));
        assertFalse(CommandRegistry.hasCommand("edit-content"));
        assertFalse(CommandRegistry.hasCommand("totally-unknown"));
    }

    // ========== PARAMETER NAME MAPPING ==========

    @Test
    public void testBridgeCreate() {
        CommandSchema schema = CommandRegistry.getSchema(CreateSlideCommand.NAME);
        CommandParameters cmd = schema.bridgeLlmParams(
            Map.of("position", 3, "title", "My Slide", "layoutId", "slideLayout2"));

        assertEquals(CreateSlideCommand.NAME, cmd.getCommandName());
        assertEquals("3", cmd.getString("position"));
        assertEquals("My Slide", cmd.getString("title"));
        // layoutId -> layout (via llmName mapping on the schema parameter)
        assertEquals("slideLayout2", cmd.getString("layout"));
    }

    @Test
    public void testBridgeEditContent() {
        CommandSchema schema = CommandRegistry.getSchema(ContentEditCommand.NAME);
        CommandParameters cmd = schema.bridgeLlmParams(
            Map.of("slideNumber", 1, "targetSpid", 5, "newText", "Hello World"));

        assertEquals(ContentEditCommand.NAME, cmd.getCommandName());
        // slideNumber -> slide, targetSpid -> spid, newText -> text
        assertEquals("1", cmd.getString("slide"));
        assertEquals("5", cmd.getString("spid"));
        assertEquals("Hello World", cmd.getString("text"));
    }

    @Test
    public void testBridgeDelete() {
        CommandSchema schema = CommandRegistry.getSchema(DeleteSlideCommand.NAME);
        CommandParameters cmd = schema.bridgeLlmParams(Map.of("slideNumber", 3));

        assertEquals(DeleteSlideCommand.NAME, cmd.getCommandName());
        assertEquals("3", cmd.getString("slide"));
    }

    @Test
    public void testBridgeCopy() {
        CommandSchema schema = CommandRegistry.getSchema(CopySlideCommand.NAME);
        CommandParameters cmd = schema.bridgeLlmParams(
            Map.of("sourceSlide", 2, "targetPosition", 5, "newTitle", "Copy"));

        assertEquals(CopySlideCommand.NAME, cmd.getCommandName());
        assertEquals("2", cmd.getString("slide"));
        assertEquals("5", cmd.getString("position"));
        assertEquals("Copy", cmd.getString("title"));
    }

    @Test
    public void testBridgeApplyTheme() {
        CommandSchema schema = CommandRegistry.getSchema("apply-theme");
        CommandParameters cmd = schema.bridgeLlmParams(Map.of("themeId", "corporate"));

        assertEquals("apply-theme", cmd.getCommandName());
        assertEquals("corporate", cmd.getString("themeId"));
    }

    @Test
    public void testBridgeMove() {
        CommandSchema schema = CommandRegistry.getSchema(MoveShapeCommand.NAME);
        CommandParameters cmd = schema.bridgeLlmParams(
            Map.of("slideNumber", 1, "targetSpid", 5, "x", "100pt", "y", "200pt"));

        assertEquals(MoveShapeCommand.NAME, cmd.getCommandName());
        assertEquals("1", cmd.getString("slide"));
        assertEquals("5", cmd.getString("spid"));
        assertEquals("100pt", cmd.getString("x"));
        assertEquals("200pt", cmd.getString("y"));
    }

    @Test
    public void testBridgeArrange() {
        CommandSchema schema = CommandRegistry.getSchema("arrange");
        CommandParameters cmd = schema.bridgeLlmParams(
            Map.of("slideNumber", 1, "operation", "align-left", "targets", "all"));

        assertEquals("arrange", cmd.getCommandName());
        assertEquals("1", cmd.getString("slide"));
        assertEquals("align-left", cmd.getString("operation"));
        assertEquals("all", cmd.getString("targets"));
    }

    @Test
    public void testBridgeNullParamsTreatedAsEmpty() {
        CommandSchema schema = CommandRegistry.getSchema(DeleteSlideCommand.NAME);
        CommandParameters cmd = schema.bridgeLlmParams(null);
        assertEquals(DeleteSlideCommand.NAME, cmd.getCommandName());
    }

    @Test
    public void testBridgeNullValuesAreDropped() {
        CommandSchema schema = CommandRegistry.getSchema(ContentEditCommand.NAME);
        Map<String, Object> params = new HashMap<>();
        params.put("slideNumber", 1);
        params.put("targetSpid", 5);
        params.put("newText", null);
        CommandParameters cmd = schema.bridgeLlmParams(params);

        assertEquals("1", cmd.getString("slide"));
        assertEquals("5", cmd.getString("spid"));
        assertNull(cmd.getString("text"));
    }

    // ========== LLM-ENABLED COMMAND ENUMERATION ==========

    @Test
    public void testGetLlmEnabledCommandNames() {
        List<String> names = CommandRegistry.getLlmEnabledCommandNames();
        assertFalse(names.isEmpty());
        assertTrue(names.contains(CreateSlideCommand.NAME));
        assertTrue(names.contains(ContentEditCommand.NAME));
        assertTrue(names.contains(AddShapeCommand.NAME));
        assertTrue(names.contains(AddAnimationCommand.NAME));
        assertTrue(names.contains("arrange"));
        assertTrue(names.contains(MoveShapeCommand.NAME));
        assertTrue(names.contains(ResizeShapeCommand.NAME));
        assertTrue(names.contains(ReorderShapeCommand.NAME));
        // Console-only commands should NOT be LLM-enabled
        assertFalse(names.contains("help"));
        assertFalse(names.contains(UndoCommand.NAME));
        assertFalse(names.contains("list"));
    }
}
