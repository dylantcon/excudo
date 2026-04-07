package com.excudo.core.commands;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.results.ExecutionResult;
import com.excudo.test.utils.StubPPTXOrchestrator;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for UpdateAnimationCommand.
 */
public class UpdateAnimationCommandTest {

    @Test
    void executeCallsOrchestratorUpdateAnimation() {
        boolean[] updateCalled = {false};
        int[] capturedSlide = {0};
        int[] capturedNodeId = {0};
        String[] capturedProperty = {null};
        String[] capturedValue = {null};

        PPTXOrchestrator stub = new StubPPTXOrchestrator() {
            @Override
            public ExecutionResult<Void> updateAnimation(int s, int n, Map<String, String> p) {
                updateCalled[0] = true;
                capturedSlide[0] = s;
                capturedNodeId[0] = n;
                capturedProperty[0] = p.keySet().iterator().next();
                capturedValue[0] = p.values().iterator().next();
                return ExecutionResult.success("UpdateAnimation", null);
            }
        };

        UpdateAnimationCommand cmd = new UpdateAnimationCommand(2, 15, "duration", "1000", stub);

        assertFalse(cmd.isExecuted());
        cmd.execute();

        assertTrue(updateCalled[0], "updateAnimation should have been called");
        assertEquals(2, capturedSlide[0]);
        assertEquals(15, capturedNodeId[0]);
        assertEquals("duration", capturedProperty[0]);
        assertEquals("1000", capturedValue[0]);
        assertTrue(cmd.isExecuted());
    }

    @Test
    void executeThrowsOnOrchestratorFailure() {
        PPTXOrchestrator stub = new StubPPTXOrchestrator() {
            @Override
            public ExecutionResult<Void> updateAnimation(int s, int n, Map<String, String> p) {
                return ExecutionResult.failure("UpdateAnimation", "Invalid property");
            }
        };

        UpdateAnimationCommand cmd = new UpdateAnimationCommand(1, 10, "duration", "500", stub);

        assertThrows(CommandExecutionException.class, cmd::execute);
        assertFalse(cmd.isExecuted());
    }

    @Test
    void canUndoReturnsFalse() {
        PPTXOrchestrator stub = new StubPPTXOrchestrator() {
            @Override
            public ExecutionResult<Void> updateAnimation(int s, int n, Map<String, String> p) {
                return ExecutionResult.success("UpdateAnimation", null);
            }
        };

        UpdateAnimationCommand cmd = new UpdateAnimationCommand(1, 5, "delay", "200", stub);
        assertFalse(cmd.canUndo());

        cmd.execute();
        assertFalse(cmd.canUndo(), "update-animation undo not yet implemented");
    }

    @Test
    void undoThrowsException() {
        PPTXOrchestrator stub = new StubPPTXOrchestrator() {
            @Override
            public ExecutionResult<Void> updateAnimation(int s, int n, Map<String, String> p) {
                return ExecutionResult.success("UpdateAnimation", null);
            }
        };

        UpdateAnimationCommand cmd = new UpdateAnimationCommand(1, 5, "duration", "1000", stub);
        cmd.execute();
        assertThrows(CommandExecutionException.class, cmd::undo);
    }

    @Test
    void doubleExecuteThrows() {
        PPTXOrchestrator stub = new StubPPTXOrchestrator() {
            @Override
            public ExecutionResult<Void> updateAnimation(int s, int n, Map<String, String> p) {
                return ExecutionResult.success("UpdateAnimation", null);
            }
        };

        UpdateAnimationCommand cmd = new UpdateAnimationCommand(1, 5, "duration", "1000", stub);
        cmd.execute();
        assertThrows(CommandExecutionException.class, cmd::execute);
    }

    @Test
    void constructorValidation() {
        PPTXOrchestrator stub = new StubPPTXOrchestrator();

        assertThrows(IllegalArgumentException.class, () -> new UpdateAnimationCommand(0, 5, "dur", "500", stub));
        assertThrows(IllegalArgumentException.class, () -> new UpdateAnimationCommand(1, 0, "dur", "500", stub));
        assertThrows(IllegalArgumentException.class, () -> new UpdateAnimationCommand(1, 5, null, "500", stub));
        assertThrows(IllegalArgumentException.class, () -> new UpdateAnimationCommand(1, 5, "", "500", stub));
        assertThrows(IllegalArgumentException.class, () -> new UpdateAnimationCommand(1, 5, "dur", null, stub));
        assertThrows(IllegalArgumentException.class, () -> new UpdateAnimationCommand(1, 5, "dur", "500", null));
    }

    @Test
    void descriptionContainsKeyInfo() {
        UpdateAnimationCommand cmd = new UpdateAnimationCommand(3, 42, "duration", "750", new StubPPTXOrchestrator());
        String desc = cmd.getDescription();
        assertTrue(desc.contains("42"), "Description should contain timingNodeId");
        assertTrue(desc.contains("3"), "Description should contain slide number");
        assertTrue(desc.contains("duration"), "Description should contain property name");
        assertTrue(desc.contains("750"), "Description should contain value");
    }

    @Test
    void gettersReturnCorrectValues() {
        UpdateAnimationCommand cmd = new UpdateAnimationCommand(2, 10, "delay", "300", new StubPPTXOrchestrator());
        assertEquals(2, cmd.getSlideNumber());
        assertEquals(10, cmd.getTimingNodeId());
        assertEquals("delay", cmd.getProperty());
        assertEquals("300", cmd.getNewValue());
    }
}
