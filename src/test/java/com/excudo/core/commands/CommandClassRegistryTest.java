package com.excudo.core.commands;

import com.excudo.core.parsing.CommandParameters;
import com.excudo.core.parsing.CommandRegistry;
import com.excudo.core.parsing.CommandSchema;
import com.excudo.test.utils.StubPPTXOrchestrator;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Verifies the class-registry naming contract: the canonical command name is
 * derived from the Command class (the single source of truth), schemas are
 * declared nameless, and a hardcoded name that conflicts with the derivation
 * is rejected loudly.
 */
public class CommandClassRegistryTest {

    private final StubPPTXOrchestrator orchestrator = new StubPPTXOrchestrator();

    @Test
    public void namelessSchemaTakesCanonicalNameFromClass() {
        CommandClassRegistry.registerCommandClass(DerivedNameStubCommand.class);

        assertTrue("registers under the class-derived kebab name",
                CommandClassRegistry.getRegisteredCommandNames().contains("derived-name-stub"));
        assertNotNull("dispatches under the derived name",
                CommandClassRegistry.createFromParameters(
                    CommandParameters.builder("derived-name-stub").build(),
                    new CommandContext(orchestrator, null)));

        CommandSchema registered = CommandRegistry.getSchema("derived-name-stub");
        assertNotNull("schema is published under the derived name", registered);
        assertEquals("derived name is stamped onto the schema",
                "derived-name-stub", registered.getName());
    }

    @Test
    public void hardcodedNameConflictingWithClassIsRejected() {
        try {
            CommandClassRegistry.registerCommandClass(HardcodedNameStubCommand.class);
            fail("a hardcoded name fighting the class derivation must throw");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("nameless"));
        }
    }

    /** Nameless schema -> canonical name derives to "derived-name-stub". */
    public static final class DerivedNameStubCommand implements Command {
        public static final CommandSchema SCHEMA = CommandSchema.builder()
            .description("test stub").build();

        public static Command fromParameters(CommandParameters p, CommandContext ctx) {
            return new DerivedNameStubCommand();
        }

        @Override public void execute() {}
        @Override public void undo() {}
        @Override public boolean canUndo() { return false; }
        @Override public String getDescription() { return "stub"; }
        @Override public boolean isExecuted() { return false; }
    }

    /** Declares a hardcoded name that disagrees with the class derivation
     *  ("hardcoded-name-stub"); registration must reject it. */
    public static final class HardcodedNameStubCommand implements Command {
        public static final CommandSchema SCHEMA = CommandSchema.builder("totally-different")
            .description("test stub").build();

        public static Command fromParameters(CommandParameters p, CommandContext ctx) {
            return new HardcodedNameStubCommand();
        }

        @Override public void execute() {}
        @Override public void undo() {}
        @Override public boolean canUndo() { return false; }
        @Override public String getDescription() { return "stub"; }
        @Override public boolean isExecuted() { return false; }
    }
}
