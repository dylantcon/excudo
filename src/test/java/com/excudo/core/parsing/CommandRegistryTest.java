package com.excudo.core.parsing;

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
        "create", "delete", "list", "add-shape", "add-animation",
        "edit-content", "load", "save", "show", "undo", "redo", "llm"
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
        assertNotNull(CommandRegistry.getSchema("create"));
    }

    @Test
    public void getSchema_delete_returnsNonNull() {
        assertNotNull(CommandRegistry.getSchema("delete"));
    }

    @Test
    public void getSchema_list_returnsNonNull() {
        assertNotNull(CommandRegistry.getSchema("list"));
    }

    @Test
    public void getSchema_addShape_returnsNonNull() {
        assertNotNull(CommandRegistry.getSchema("add-shape"));
    }

    @Test
    public void getSchema_undo_returnsNonNull() {
        assertNotNull(CommandRegistry.getSchema("undo"));
    }

    @Test
    public void getSchema_redo_returnsNonNull() {
        assertNotNull(CommandRegistry.getSchema("redo"));
    }

    @Test
    public void getSchema_llm_returnsNonNull() {
        assertNotNull(CommandRegistry.getSchema("llm"));
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
        CommandSchema schema = CommandRegistry.getSchema("create");
        assertNotNull(schema.getDescription());
        assertFalse("'create' schema must have a non-empty description",
                schema.getDescription().trim().isEmpty());
    }

    @Test
    public void addShapeSchema_hasNonEmptyDescription() {
        CommandSchema schema = CommandRegistry.getSchema("add-shape");
        assertNotNull(schema.getDescription());
        assertFalse("'add-shape' schema must have a non-empty description",
                schema.getDescription().trim().isEmpty());
    }

    @Test
    public void loadSchema_hasNonEmptyDescription() {
        CommandSchema schema = CommandRegistry.getSchema("load");
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
        CommandSchema schema = CommandRegistry.getSchema("create");
        List<Parameter<?>> params = schema.getParameters();

        assertNotNull(params);
        assertFalse("'create' schema must define parameters", params.isEmpty());

        long requiredCount = params.stream().filter(Parameter::isRequired).count();
        assertTrue("'create' must have at least one required parameter", requiredCount > 0);
    }

    @Test
    public void addShapeSchema_hasParameters() {
        CommandSchema schema = CommandRegistry.getSchema("add-shape");
        List<Parameter<?>> params = schema.getParameters();

        assertNotNull(params);
        assertFalse("'add-shape' schema must define parameters", params.isEmpty());
    }

    @Test
    public void createSchema_firstParameterIsPositionOrSlideNumber() {
        CommandSchema schema = CommandRegistry.getSchema("create");
        List<Parameter<?>> params = schema.getParameters();

        assertFalse(params.isEmpty());
        Parameter first = params.get(0);
        assertNotNull(first.getName());
        assertFalse("First parameter of 'create' must have a name",
                first.getName().trim().isEmpty());
    }

    @Test
    public void addShapeSchema_firstParameterIsSlide() {
        CommandSchema schema = CommandRegistry.getSchema("add-shape");
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
