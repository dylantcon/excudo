package com.excudo.core.commands;

import com.excudo.core.commands.mutating.slide.DuplicateShapeCommand;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.results.ExecutionResult;
import com.excudo.test.utils.StubPPTXOrchestrator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DuplicateShapeCommand.
 *
 * Verifies construction validation, execute allocates new SPID, undo removes
 * the duplicate by that SPID, double-execute guard, and canUndo state.
 */
public class DuplicateShapeCommandTest {

    // ========== CONSTRUCTION ==========

    @Test
    @DisplayName("Rejects null orchestrator")
    void constructorRejectsNullOrchestrator() {
        assertThrows(IllegalArgumentException.class,
            () -> new DuplicateShapeCommand(1, 5, null));
    }

    @Test
    @DisplayName("Rejects slide number zero or negative")
    void constructorRejectsInvalidSlide() {
        assertThrows(IllegalArgumentException.class,
            () -> new DuplicateShapeCommand(0, 5, new StubPPTXOrchestrator()));
    }

    @Test
    @DisplayName("Rejects SPID zero or negative")
    void constructorRejectsInvalidSpid() {
        assertThrows(IllegalArgumentException.class,
            () -> new DuplicateShapeCommand(1, -3, new StubPPTXOrchestrator()));
    }

    @Test
    @DisplayName("Default constructor uses 0.5-inch offset constant")
    void defaultConstructorSetsOffset() {
        DuplicateShapeCommand cmd = new DuplicateShapeCommand(1, 5, new StubPPTXOrchestrator());
        assertEquals(DuplicateShapeCommand.DEFAULT_OFFSET_EMU, cmd.getOffsetX());
        assertEquals(DuplicateShapeCommand.DEFAULT_OFFSET_EMU, cmd.getOffsetY());
    }

    // ========== EXECUTE ==========

    @Test
    @DisplayName("Execute calls duplicateShape and stores returned SPID")
    void executeStoresNewSpid() {
        boolean[] duplicateCalled = {false};
        final int newSpid = 99;

        PPTXOrchestrator stub = new StubPPTXOrchestrator() {
            @Override
            public ExecutionResult<Integer> duplicateShape(int slide, int spid,
                                                           long offsetX, long offsetY) {
                duplicateCalled[0] = true;
                assertEquals(1, slide);
                assertEquals(5, spid);
                return ExecutionResult.success("DuplicateShape", newSpid);
            }
        };

        DuplicateShapeCommand cmd = new DuplicateShapeCommand(1, 5, stub);

        assertFalse(cmd.isExecuted());
        assertFalse(cmd.canUndo());
        assertNull(cmd.getNewSpid());

        cmd.execute();

        assertTrue(duplicateCalled[0]);
        assertTrue(cmd.isExecuted());
        assertTrue(cmd.canUndo());
        assertEquals(newSpid, cmd.getNewSpid());
    }

    @Test
    @DisplayName("Execute throws when duplicateShape fails")
    void executeThrowsOnFailure() {
        PPTXOrchestrator stub = new StubPPTXOrchestrator() {
            @Override
            public ExecutionResult<Integer> duplicateShape(int s, int sp, long ox, long oy) {
                return ExecutionResult.failure("DuplicateShape", "Shape not found");
            }
        };

        DuplicateShapeCommand cmd = new DuplicateShapeCommand(1, 5, stub);
        assertThrows(CommandExecutionException.class, cmd::execute);
        assertFalse(cmd.isExecuted());
        assertFalse(cmd.canUndo());
    }

    @Test
    @DisplayName("Execute throws on double execution")
    void executeThrowsOnDoubleExecution() {
        PPTXOrchestrator stub = new StubPPTXOrchestrator() {
            @Override
            public ExecutionResult<Integer> duplicateShape(int s, int sp, long ox, long oy) {
                return ExecutionResult.success("DuplicateShape", 77);
            }
        };

        DuplicateShapeCommand cmd = new DuplicateShapeCommand(1, 5, stub);
        cmd.execute();
        assertThrows(CommandExecutionException.class, cmd::execute);
    }

    // ========== UNDO ==========

    @Test
    @DisplayName("Undo removes the duplicated shape by its new SPID")
    void undoRemovesDuplicate() {
        final int newSpid = 88;
        int[] removedSpid = {-1};

        PPTXOrchestrator stub = new StubPPTXOrchestrator() {
            @Override
            public ExecutionResult<Integer> duplicateShape(int s, int sp, long ox, long oy) {
                return ExecutionResult.success("DuplicateShape", newSpid);
            }

            @Override
            public ExecutionResult<Void> removeShape(int slide, int spid) {
                removedSpid[0] = spid;
                return ExecutionResult.success("RemoveShape", null);
            }
        };

        DuplicateShapeCommand cmd = new DuplicateShapeCommand(1, 5, stub);
        cmd.execute();
        assertEquals(newSpid, cmd.getNewSpid());

        cmd.undo();

        assertEquals(newSpid, removedSpid[0], "Should remove the duplicated SPID, not the original");
        assertFalse(cmd.isExecuted());
        assertNull(cmd.getNewSpid());
        assertFalse(cmd.canUndo());
    }

    @Test
    @DisplayName("Undo throws when not yet executed")
    void undoThrowsWhenNotExecuted() {
        DuplicateShapeCommand cmd = new DuplicateShapeCommand(1, 5, new StubPPTXOrchestrator());
        assertThrows(CommandExecutionException.class, cmd::undo);
    }

    // ========== DESCRIPTION ==========

    @Test
    @DisplayName("Description identifies slide, SPID, and offset")
    void descriptionFormat() {
        DuplicateShapeCommand cmd = new DuplicateShapeCommand(2, 10, new StubPPTXOrchestrator());
        String desc = cmd.getDescription();
        assertTrue(desc.contains("10"), "Should include source SPID");
        assertTrue(desc.contains("2"), "Should include slide number");
    }

    @Test
    @DisplayName("Getters return construction values")
    void gettersReturnCorrectValues() {
        DuplicateShapeCommand cmd = new DuplicateShapeCommand(3, 7,
            1000L, 2000L, new StubPPTXOrchestrator());
        assertEquals(3, cmd.getSlideNumber());
        assertEquals(7, cmd.getSpid());
        assertEquals(1000L, cmd.getOffsetX());
        assertEquals(2000L, cmd.getOffsetY());
    }
}
