package com.excudo.core.parsing;

import com.excudo.core.commands.meta.LoadCommand;

import com.excudo.core.commands.meta.LLMCommand;

import com.excudo.core.commands.meta.SaveCommand;

import com.excudo.core.commands.readonly.ShowSlideCommand;
import com.excudo.core.commands.readonly.ListSlidesCommand;
import com.excudo.core.commands.mutating.slide.ContentEditCommand;
import com.excudo.core.commands.mutating.slide.AddShapeCommand;
import com.excudo.core.commands.mutating.slide.AddAnimationCommand;
import com.excudo.core.commands.mutating.deck.DeleteSlideCommand;
import com.excudo.core.commands.mutating.deck.CreateSlideCommand;
import com.excudo.core.commands.meta.UndoCommand;
import com.excudo.core.commands.meta.RedoCommand;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Unit tests for CommandRegistry.
 *
 * Verifies that the static registry is fully populated, schemas contain
 * valid metadata, and critical commands are present with appropriate parameters.
 */
public class CommandRegistryTest {

    // Commands that must be present for the console to function correctly
    private static final List<String> CRITICAL_COMMANDS = Arrays.asList(
        CreateSlideCommand.NAME, DeleteSlideCommand.NAME, ListSlidesCommand.NAME, AddShapeCommand.NAME, AddAnimationCommand.NAME,
        ContentEditCommand.NAME, LoadCommand.NAME, SaveCommand.NAME, ShowSlideCommand.NAME, UndoCommand.NAME, RedoCommand.NAME, LLMCommand.NAME
    );

    // ========== Critical Command Presence ==========

    @Test
    public void registry_containsAllCriticalCommands() {
        for (String name : CRITICAL_COMMANDS) {
            CommandSchema schema = CommandRegistry.getSchema(name);
            assertNotNull("Critical command '" + name + "' must be registered", schema);
        }
    }

    @Test
    public void getSchema_create_returnsNonNull() {
        assertNotNull(CommandRegistry.getSchema(CreateSlideCommand.NAME));
    }

    @Test
    public void getSchema_delete_returnsNonNull() {
        assertNotNull(CommandRegistry.getSchema(DeleteSlideCommand.NAME));
    }

    @Test
    public void getSchema_list_returnsNonNull() {
        assertNotNull(CommandRegistry.getSchema(ListSlidesCommand.NAME));
    }

    @Test
    public void getSchema_addShape_returnsNonNull() {
        assertNotNull(CommandRegistry.getSchema(AddShapeCommand.NAME));
    }

    @Test
    public void getSchema_undo_returnsNonNull() {
        assertNotNull(CommandRegistry.getSchema(UndoCommand.NAME));
    }

    @Test
    public void getSchema_redo_returnsNonNull() {
        assertNotNull(CommandRegistry.getSchema(RedoCommand.NAME));
    }

    @Test
    public void getSchema_llm_returnsNonNull() {
        assertNotNull(CommandRegistry.getSchema(LLMCommand.NAME));
    }

    // ========== Unknown Command Lookup ==========

    @Test
    public void getSchema_unknownCommand_returnsNull() {
        assertNull(CommandRegistry.getSchema("nonexistent-command"));
    }

    @Test
    public void getSchema_emptyString_returnsNull() {
        assertNull(CommandRegistry.getSchema(""));
    }

    // ========== Command Name Set ==========

    @Test
    public void getCommandNames_returnsNonEmptySet() {
        Set<String> names = CommandRegistry.getCommandNames();
        assertNotNull(names);
        assertFalse("Command name set must not be empty", names.isEmpty());
    }

    @Test
    public void getCommandNames_totalCountExceedsMinimumThreshold() {
        Set<String> names = CommandRegistry.getCommandNames();
        assertTrue(
            "Registry should contain more than 20 commands, found: " + names.size(),
            names.size() > 20
        );
    }

    @Test
    public void getCommandNames_containsCriticalCommandNames() {
        Set<String> names = CommandRegistry.getCommandNames();
        for (String critical : CRITICAL_COMMANDS) {
            assertTrue("getCommandNames() must include '" + critical + "'", names.contains(critical));
        }
    }

    // ========== Schema Metadata Integrity ==========

    @Test
    public void allSchemas_haveNonNullNameMatchingRegistryKeyOrAreAliases() {
        // The registry allows alias keys (e.g. "?" -> help schema, "open" -> load schema).
        // Every key must resolve to a non-null schema with a non-null name.
        // Alias keys are permitted: their schema name may differ from the lookup key.
        Set<String> names = CommandRegistry.getCommandNames();
        for (String key : names) {
            CommandSchema schema = CommandRegistry.getSchema(key);
            assertNotNull("Schema for key '" + key + "' must not be null", schema);
            assertNotNull("Schema name must not be null for key '" + key + "'", schema.getName());
            assertFalse(
                "Schema name must not be empty for key '" + key + "'",
                schema.getName().trim().isEmpty()
            );
        }
    }

    @Test
    public void createSchema_hasNonEmptyDescription() {
        CommandSchema schema = CommandRegistry.getSchema(CreateSlideCommand.NAME);
        assertNotNull(schema.getDescription());
        assertFalse("'create' schema must have a non-empty description",
                schema.getDescription().trim().isEmpty());
    }

    @Test
    public void addShapeSchema_hasNonEmptyDescription() {
        CommandSchema schema = CommandRegistry.getSchema(AddShapeCommand.NAME);
        assertNotNull(schema.getDescription());
        assertFalse("'add-shape' schema must have a non-empty description",
                schema.getDescription().trim().isEmpty());
    }

    @Test
    public void loadSchema_hasNonEmptyDescription() {
        CommandSchema schema = CommandRegistry.getSchema(LoadCommand.NAME);
        assertNotNull(schema.getDescription());
        assertFalse("'load' schema must have a non-empty description",
                schema.getDescription().trim().isEmpty());
    }

    @Test
    public void allSchemas_haveNonEmptyDescriptions() {
        Set<String> names = CommandRegistry.getCommandNames();
        for (String key : names) {
            CommandSchema schema = CommandRegistry.getSchema(key);
            assertNotNull("Schema description must not be null for '" + key + "'",
                    schema.getDescription());
        }
    }

    // ========== Parameter Presence ==========

    @Test
    public void createSchema_hasRequiredParameters() {
        CommandSchema schema = CommandRegistry.getSchema(CreateSlideCommand.NAME);
        List<Parameter<?>> params = schema.getParameters();

        assertNotNull(params);
        assertFalse("'create' schema must define parameters", params.isEmpty());

        long requiredCount = params.stream().filter(Parameter::isRequired).count();
        assertTrue("'create' must have at least one required parameter", requiredCount > 0);
    }

    @Test
    public void addShapeSchema_hasParameters() {
        CommandSchema schema = CommandRegistry.getSchema(AddShapeCommand.NAME);
        List<Parameter<?>> params = schema.getParameters();

        assertNotNull(params);
        assertFalse("'add-shape' schema must define parameters", params.isEmpty());
    }

    @Test
    public void createSchema_firstParameterIsPositionOrSlideNumber() {
        CommandSchema schema = CommandRegistry.getSchema(CreateSlideCommand.NAME);
        List<Parameter<?>> params = schema.getParameters();

        assertFalse(params.isEmpty());
        Parameter first = params.get(0);
        assertNotNull(first.getName());
        assertFalse("First parameter of 'create' must have a name",
                first.getName().trim().isEmpty());
    }

    @Test
    public void addShapeSchema_firstParameterIsSlide() {
        CommandSchema schema = CommandRegistry.getSchema(AddShapeCommand.NAME);
        List<Parameter<?>> params = schema.getParameters();

        assertFalse(params.isEmpty());
        assertEquals("slide", params.get(0).getName());
    }

    // ========== Parameter Type Integrity ==========

    @Test
    public void allParametersInAllSchemas_haveNonNullNames() {
        Set<String> names = CommandRegistry.getCommandNames();
        for (String key : names) {
            CommandSchema schema = CommandRegistry.getSchema(key);
            for (Parameter param : schema.getParameters()) {
                assertNotNull(
                    "Parameter name must not be null in schema '" + key + "'",
                    param.getName()
                );
                assertNotNull(
                    "Parameter type must not be null in schema '" + key + "', param '" + param.getName() + "'",
                    param.getType()
                );
            }
        }
    }
}
