package com.excudo.core.parsing;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Map;
import java.util.Set;

/**
 * Guards the contract that TechnicalConsoleController's autocomplete
 * relies on: CommandRegistry.getAllSchemas() must return a substantial,
 * well-formed list of CommandSchemas with non-empty descriptions, and
 * CommandRegistry.getCommandNames() must match the schema keys.
 *
 * Headless test -- no JavaFX controller touched.
 */
public class CommandRegistryAutocompleteTest {

    @Test
    public void allSchemasExposePopulatedDescriptions() {
        Map<String, CommandSchema> schemas = CommandRegistry.getAllSchemas();
        assertNotNull("CommandRegistry.getAllSchemas() must not return null", schemas);
        assertTrue(
            "CommandRegistry should expose at least 50 schemas (found " + schemas.size() + ")",
            schemas.size() >= 50
        );

        for (Map.Entry<String, CommandSchema> entry : schemas.entrySet()) {
            String name = entry.getKey();
            CommandSchema schema = entry.getValue();

            assertNotNull("Schema for '" + name + "' is null", schema);
            assertNotNull("Schema name is null for key '" + name + "'", schema.getName());
            assertNotNull("Schema description is null for '" + name + "'", schema.getDescription());
            assertFalse(
                "Schema description is empty for '" + name + "'",
                schema.getDescription().trim().isEmpty()
            );
            assertNotNull("Schema parameters list is null for '" + name + "'", schema.getParameters());
        }
    }

    @Test
    public void commandNamesMatchSchemaKeys() {
        Set<String> names = CommandRegistry.getCommandNames();
        Map<String, CommandSchema> schemas = CommandRegistry.getAllSchemas();

        assertEquals(
            "getCommandNames() and getAllSchemas() must agree",
            schemas.keySet(), names
        );
    }

    @Test
    public void knownCommandsArePresent() {
        // Sanity check: the commands the GUI autocomplete most needs to show
        // must be in the registry. This guards against regressions where a
        // refactor silently drops a core command.
        Set<String> names = CommandRegistry.getCommandNames();
        String[] mustHave = {
            "load", "save", "new", "list", "show", "create-slide", "delete-slide",
            "add-shape", "remove-shape", "add-animation", "remove-animation",
            "list-layouts", "list-themes", "apply-theme",
            "undo", "redo", "arrange", "help"
        };
        for (String cmd : mustHave) {
            assertTrue("CommandRegistry must expose '" + cmd + "'", names.contains(cmd));
        }
    }

    // Bridge assertion for the non-JUnit-core import above
    private static void assertEquals(String msg, Object a, Object b) {
        org.junit.Assert.assertEquals(msg, a, b);
    }
}
