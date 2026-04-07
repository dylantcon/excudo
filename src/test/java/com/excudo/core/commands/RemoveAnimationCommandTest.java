package com.excudo.core.commands;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.results.ExecutionResult;
import com.excudo.test.utils.StubPPTXOrchestrator;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RemoveAnimationCommand.
 */
public class RemoveAnimationCommandTest {

    @Test
    void executeCallsOrchestratorRemoveAnimation() {
        boolean[] removeCalled = {false};
        int[] capturedSlide = {0};
        int[] capturedNodeId = {0};

        PPTXOrchestrator stub = new StubPPTXOrchestrator() {
            @Override
            public ExecutionResult<Void> removeAnimation(int s, int n) {
                removeCalled[0] = true;
                capturedSlide[0] = s;
                capturedNodeId[0] = n;
                return ExecutionResult.success("RemoveAnimation", null);
            }
        };

        RemoveAnimationCommand cmd = new RemoveAnimationCommand(2, 15, stub);

        assertFalse(cmd.isExecuted());
        cmd.execute();

        assertTrue(removeCalled[0], "removeAnimation should have been called");
        assertEquals(2, capturedSlide[0]);
        assertEquals(15, capturedNodeId[0]);
        assertTrue(cmd.isExecuted());
    }

    @Test
    void executeThrowsOnOrchestratorFailure() {
        PPTXOrchestrator stub = new StubPPTXOrchestrator() {
            @Override
            public ExecutionResult<Void> removeAnimation(int s, int n) {
                return ExecutionResult.failure("RemoveAnimation", "Node not found");
            }
        };

        RemoveAnimationCommand cmd = new RemoveAnimationCommand(1, 99, stub);

        assertThrows(CommandExecutionException.class, cmd::execute);
        assertFalse(cmd.isExecuted());
    }

    @Test
    void canUndoReturnsFalse() {
        PPTXOrchestrator stub = new StubPPTXOrchestrator() {
            @Override
            public ExecutionResult<Void> removeAnimation(int s, int n) {
                return ExecutionResult.success("RemoveAnimation", null);
            }
        };

        RemoveAnimationCommand cmd = new RemoveAnimationCommand(1, 5, stub);
        assertFalse(cmd.canUndo());

        cmd.execute();
        assertFalse(cmd.canUndo(), "remove-animation should not be undoable");
    }

    @Test
    void undoThrowsException() {
        PPTXOrchestrator stub = new StubPPTXOrchestrator() {
            @Override
            public ExecutionResult<Void> removeAnimation(int s, int n) {
                return ExecutionResult.success("RemoveAnimation", null);
            }
        };

        RemoveAnimationCommand cmd = new RemoveAnimationCommand(1, 5, stub);
        cmd.execute();
        assertThrows(CommandExecutionException.class, cmd::undo);
    }

    @Test
    void doubleExecuteThrows() {
        PPTXOrchestrator stub = new StubPPTXOrchestrator() {
            @Override
            public ExecutionResult<Void> removeAnimation(int s, int n) {
                return ExecutionResult.success("RemoveAnimation", null);
            }
        };

        RemoveAnimationCommand cmd = new RemoveAnimationCommand(1, 5, stub);
        cmd.execute();
        assertThrows(CommandExecutionException.class, cmd::execute);
    }

    @Test
    void constructorValidation() {
        PPTXOrchestrator stub = new StubPPTXOrchestrator();

        assertThrows(IllegalArgumentException.class, () -> new RemoveAnimationCommand(0, 5, stub));
        assertThrows(IllegalArgumentException.class, () -> new RemoveAnimationCommand(1, 0, stub));
        assertThrows(IllegalArgumentException.class, () -> new RemoveAnimationCommand(1, 5, null));
    }

    @Test
    void descriptionContainsKeyInfo() {
        RemoveAnimationCommand cmd = new RemoveAnimationCommand(3, 42, new StubPPTXOrchestrator());
        String desc = cmd.getDescription();
        assertTrue(desc.contains("42"), "Description should contain timingNodeId");
        assertTrue(desc.contains("3"), "Description should contain slide number");
    }
}
