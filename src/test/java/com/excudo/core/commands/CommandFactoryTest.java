package com.excudo.core.commands;

import com.excudo.core.parsing.CommandParameters;
import com.excudo.test.utils.StubPPTXOrchestrator;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for CommandFactory.
 *
 * Verifies the GoF Factory pattern implementation: constructor validation,
 * command routing delegation to subfactories, and CompositeCommand assembly.
 */
public class CommandFactoryTest {

    private StubPPTXOrchestrator orchestrator;

    @Before
    public void setUp() {
        orchestrator = new StubPPTXOrchestrator();
    }

    // ========== CONSTRUCTOR ==========

    @Test(expected = IllegalArgumentException.class)
    public void constructorWithNullOrchestratorThrows() {
        new CommandFactory(null);
    }

    @Test
    public void constructorWithValidOrchestratorSucceeds() {
        CommandFactory factory = new CommandFactory(orchestrator);
        assertNotNull(factory);
    }

    // ========== handlesCommand ==========

    @Test
    public void handlesCommandReturnsFalseForListHandledByUtilityFactory() {
        CommandFactory factory = new CommandFactory(orchestrator);
        assertFalse("'list' is handled by UtilityCommandFactory, not CommandFactory",
                factory.handlesCommand("list"));
    }

    @Test
    public void handlesCommandReturnsFalseForHelpHandledBySystemFactory() {
        CommandFactory factory = new CommandFactory(orchestrator);
        assertFalse("'help' is handled by SystemCommandFactory, not CommandFactory",
                factory.handlesCommand("help"));
    }

    @Test
    public void addShapeRoutedThroughClassRegistry() {
        // After the class-keyed registry migration, AddShapeCommand owns its
        // SCHEMA + fromParameters and dispatches via CommandClassRegistry --
        // ShapeCommandFactory no longer claims "add-shape" in HANDLED_COMMANDS.
        // CommandFactory.handlesCommand falls through to true for any name no
        // sub-factory claims, but actual dispatch goes through the class
        // registry first (verified separately by the round-trip tests).
        assertNotNull("AddShapeCommand must be class-registered",
                CommandClassRegistry.createFromParameters(
                    CommandParameters.builder("add-shape")
                        .addParam("slide", 1)
                        .addParam("x", 0)
                        .addParam("y", 0)
                        .addParam("width", 100)
                        .addParam("height", 50)
                        .build(),
                    new CommandContext(orchestrator, null)));
    }

    @Test
    public void handlesCommandReturnsFalseForCreateHandledBySlideFactory() {
        CommandFactory factory = new CommandFactory(orchestrator);
        assertFalse("'create' is handled by SlideCommandFactory, not CommandFactory",
                factory.handlesCommand("create"));
    }

    @Test
    public void handlesCommandReturnsFalseForLoadHandledByPresentationFactory() {
        CommandFactory factory = new CommandFactory(orchestrator);
        assertFalse("'load' is handled by PresentationCommandFactory, not CommandFactory",
                factory.handlesCommand("load"));
    }

    @Test
    public void handlesCommandReturnsTrueForAddAnimation() {
        CommandFactory factory = new CommandFactory(orchestrator);
        assertTrue("'add-animation' is handled directly by CommandFactory",
                factory.handlesCommand("add-animation"));
    }

    @Test
    public void handlesCommandReturnsTrueForRemoveAnimation() {
        CommandFactory factory = new CommandFactory(orchestrator);
        assertTrue("'remove-animation' is handled directly by CommandFactory",
                factory.handlesCommand("remove-animation"));
    }

    @Test
    public void handlesCommandReturnsTrueForUpdateAnimation() {
        CommandFactory factory = new CommandFactory(orchestrator);
        assertTrue("'update-animation' is handled directly by CommandFactory",
                factory.handlesCommand("update-animation"));
    }

    @Test
    public void handlesCommandReturnsTrueForLlm() {
        CommandFactory factory = new CommandFactory(orchestrator);
        assertTrue("'llm' is handled directly by CommandFactory",
                factory.handlesCommand("llm"));
    }

    // ========== createComposite ==========

    @Test
    public void createCompositeReturnsCompositeCommandWithCorrectDescription() {
        CommandFactory factory = new CommandFactory(orchestrator);

        Command stub1 = buildStubCommand("Stub 1");
        Command stub2 = buildStubCommand("Stub 2");
        List<Command> commands = Arrays.asList(stub1, stub2);

        CompositeCommand composite = factory.createComposite(commands, "Test composite");

        assertNotNull(composite);
        assertTrue("Returned object must be a CompositeCommand",
                composite instanceof CompositeCommand);
        assertEquals("Test composite", composite.getDescription());
    }

    @Test
    public void createCompositePreservesAllChildCommands() {
        CommandFactory factory = new CommandFactory(orchestrator);

        Command stub1 = buildStubCommand("Alpha");
        Command stub2 = buildStubCommand("Beta");
        Command stub3 = buildStubCommand("Gamma");
        List<Command> commands = Arrays.asList(stub1, stub2, stub3);

        CompositeCommand composite = factory.createComposite(commands, "Three commands");

        assertEquals(3, composite.getCommandCount());
        List<Command> children = composite.getCommands();
        assertEquals("Alpha", children.get(0).getDescription());
        assertEquals("Beta", children.get(1).getDescription());
        assertEquals("Gamma", children.get(2).getDescription());
    }

    @Test(expected = IllegalArgumentException.class)
    public void createCompositeWithEmptyListThrows() {
        CommandFactory factory = new CommandFactory(orchestrator);
        factory.createComposite(Collections.emptyList(), "Empty");
    }

    // ========== helpers ==========

    private Command buildStubCommand(String description) {
        return new Command() {
            private boolean executed = false;

            @Override
            public void execute() {
                executed = true;
            }

            @Override
            public void undo() {
                executed = false;
            }

            @Override
            public boolean canUndo() {
                return executed;
            }

            @Override
            public String getDescription() {
                return description;
            }

            @Override
            public boolean isExecuted() {
                return executed;
            }
        };
    }
}
