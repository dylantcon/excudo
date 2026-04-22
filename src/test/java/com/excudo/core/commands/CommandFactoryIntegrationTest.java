package com.excudo.core.commands;

import com.excudo.core.commands.mutating.deck.CreateSlideCommand;

import com.excudo.core.commands.RequestSchema;
import com.excudo.core.model.PPTXDocument;
import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.orchestration.PPTXOrchestratorImpl;
import com.excudo.xml.writers.SlideCreator;
import com.excudo.xml.writers.SPIDManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for CommandFactory and Command pattern implementation.
 * Tests the complete flow from LLM requests to Command execution.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CommandFactoryIntegrationTest {

    private CommandFactory commandFactory;
    private CommandInvoker commandInvoker;
    private PPTXOrchestrator orchestrator;
    private SlideCreator slideCreator;

    @BeforeAll
    void setUp() throws Exception {
        SPIDManager.resetInstance();

        File sourceFile = new File("test-pptx-samples/generalist_test_file.pptx");
        org.junit.jupiter.api.Assumptions.assumeTrue(sourceFile.exists(),
            "Test skipped: Required test file not found: " + sourceFile);

        orchestrator = new PPTXOrchestratorImpl();
        PPTXDocument doc = PPTXDocument.loadFromZip(sourceFile);
        orchestrator.initialize(doc);

        slideCreator = orchestrator.getContext().get().getSlideCreator();
        commandFactory = new CommandFactory(orchestrator);
        commandInvoker = new CommandInvoker();
    }

    @BeforeEach
    void resetInvoker() {
        commandInvoker = new CommandInvoker();
    }

    @Test
    void testCreateSlideCommandFromLLMRequest() {
        RequestSchema.LLMRequest request = createTestLLMRequest("slide-creation",
            Map.of("position", 2, "title", "Test Slide from LLM"));

        List<Command> commands = commandFactory.createFromLLMRequest(request, slideCreator, null);

        assertNotNull(commands);
        assertEquals(1, commands.size());

        Command cmd = commands.get(0);
        assertNotNull(cmd);
        assertFalse(cmd.isExecuted());

        commandInvoker.executeCommand(cmd);
        assertTrue(cmd.isExecuted());
    }

    @Test
    void testSimpleCompositeInvokerDebug() {
        CreateSlideCommand singleCommand = commandFactory.createSlideCreation(
            5, "Single Test Slide", slideCreator, null);
        assertNotNull(singleCommand);

        commandInvoker.executeCommand(singleCommand);
        assertTrue(singleCommand.isExecuted());

        List<Command> commands = List.of(commandFactory.createSlideCreation(
            6, "Composite Test Slide", slideCreator, null));
        CompositeCommand composite = new CompositeCommand(commands, "Test composite");

        commandInvoker.executeCommand(composite);
        assertTrue(composite.isExecuted());
    }

    @Test
    void testCommandInvokerWithLLMCommands() {
        RequestSchema.LLMRequest request = createTestLLMRequest("slide-creation",
            Map.of("position", 3, "title", "Invoker Test Slide"));

        List<Command> commands = commandFactory.createFromLLMRequest(
            request, slideCreator, null);

        assertNotNull(commands);
        assertFalse(commands.isEmpty());

        CompositeCommand composite = commandFactory.createComposite(commands, "Test LLM composite");

        commandInvoker.executeCommand(composite);
        assertTrue(composite.isExecuted());

        // Test history
        assertTrue(commandInvoker.getHistorySize() > 0);
    }

    @Test
    void testUnsupportedOperationThrows() {
        // Unknown action types should throw
        RequestSchema.LLMRequest request = createTestLLMRequest("nonexistent-operation",
            Map.of("param1", "value1"));

        assertThrows(IllegalArgumentException.class, () ->
            commandFactory.createFromLLMRequest(request, slideCreator, null));

        // Null request should also throw
        assertThrows(IllegalArgumentException.class, () ->
            commandFactory.createFromLLMRequest(null, slideCreator, null));
    }

    @Test
    void testDeleteCommandCreation() {
        // Use a real command name that CommandRegistry knows
        RequestSchema.LLMRequest request = createTestLLMRequest("delete",
            Map.of("slideNumber", 1));

        List<Command> commands = commandFactory.createFromLLMRequest(
            request, slideCreator, null);
        assertNotNull(commands);
        assertFalse(commands.isEmpty());

        for (Command cmd : commands) {
            assertNotNull(cmd.getDescription());
            assertFalse(cmd.getDescription().isEmpty());
        }
    }

    @Test
    void testAddShapeCommandCreation() {
        RequestSchema.LLMRequest request = createTestLLMRequest("add-shape",
            Map.of("slideNumber", 1, "shapeType", "RECTANGLE",
                   "x", 1000000, "y", 1000000, "width", 2000000, "height", 1000000));

        List<Command> commands = commandFactory.createFromLLMRequest(
            request, slideCreator, null);
        assertNotNull(commands);
        assertFalse(commands.isEmpty());
    }

    @Test
    void testCommandHistoryAndDescription() {
        RequestSchema.LLMRequest request1 = createTestLLMRequest("slide-creation",
            Map.of("position", 4, "title", "History Test 1"));
        RequestSchema.LLMRequest request2 = createTestLLMRequest("slide-creation",
            Map.of("position", 5, "title", "History Test 2"));

        List<Command> commands1 = commandFactory.createFromLLMRequest(
            request1, slideCreator, null);
        List<Command> commands2 = commandFactory.createFromLLMRequest(
            request2, slideCreator, null);

        // Execute first set
        for (Command cmd : commands1) {
            commandInvoker.executeCommand(cmd);
        }

        // Execute second set
        for (Command cmd : commands2) {
            commandInvoker.executeCommand(cmd);
        }

        assertTrue(commandInvoker.getHistorySize() > 0);
    }

    // ==================== HELPERS ====================

    @SuppressWarnings("unchecked")
    private RequestSchema.LLMRequest createTestLLMRequest(String operationType,
                                                           Map<String, Object> params) {
        RequestSchema.ActionRequest action = new RequestSchema.ActionRequest();
        action.setType(operationType);
        action.setParameters(new HashMap<>(params));

        RequestSchema.LLMRequest request = new RequestSchema.LLMRequest();
        request.setActions(List.of(action));

        RequestSchema.RequestMetadata metadata = new RequestSchema.RequestMetadata();
        metadata.setReasoning("Test request for " + operationType);
        request.setMetadata(metadata);

        return request;
    }
}
