package com.excudo.core.commands;

import com.excudo.core.commands.mutating.slide.GroupShapesCommand;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.results.ExecutionResult;
import com.excudo.test.utils.StubPPTXOrchestrator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GroupShapesCommand.
 *
 * Verifies construction validation (minimum 2 SPIDs), execute stores group SPID,
 * undo calls ungroupShape, and double-execute guard.
 */
public class GroupShapesCommandTest {

    private static final List<Integer> TWO_SPIDS = Arrays.asList(10, 20);

    // ========== CONSTRUCTION ==========

    @Test
    @DisplayName("Rejects null orchestrator")
    void constructorRejectsNullOrchestrator() {
        assertThrows(IllegalArgumentException.class,
            () -> new GroupShapesCommand(1, TWO_SPIDS, null));
    }

    @Test
    @DisplayName("Rejects slide number zero or negative")
    void constructorRejectsInvalidSlide() {
        assertThrows(IllegalArgumentException.class,
            () -> new GroupShapesCommand(0, TWO_SPIDS, new StubPPTXOrchestrator()));
    }

    @Test
    @DisplayName("Rejects null SPID list")
    void constructorRejectsNullSpidList() {
        assertThrows(IllegalArgumentException.class,
            () -> new GroupShapesCommand(1, null, new StubPPTXOrchestrator()));
    }

    @Test
    @DisplayName("Rejects SPID list with fewer than two entries")
    void constructorRejectsSingleSpid() {
        assertThrows(IllegalArgumentException.class,
            () -> new GroupShapesCommand(1, Collections.singletonList(5), new StubPPTXOrchestrator()));
    }

    @Test
    @DisplayName("Rejects empty SPID list")
    void constructorRejectsEmptySpidList() {
        assertThrows(IllegalArgumentException.class,
            () -> new GroupShapesCommand(1, Collections.emptyList(), new StubPPTXOrchestrator()));
    }

    // ========== EXECUTE ==========

    @Test
    @DisplayName("Execute calls groupShapes and stores returned group SPID")
    void executeGroupsShapesAndStoresGroupSpid() {
        final int groupSpid = 50;
        boolean[] groupCalled = {false};

        PPTXOrchestrator stub = new StubPPTXOrchestrator() {
            @Override
            public ExecutionResult<Integer> groupShapes(int slide, List<Integer> spids) {
                groupCalled[0] = true;
                assertEquals(1, slide);
                assertEquals(TWO_SPIDS, spids);
                return ExecutionResult.success("GroupShapes", groupSpid);
            }
        };

        GroupShapesCommand cmd = new GroupShapesCommand(1, TWO_SPIDS, stub);

        assertFalse(cmd.isExecuted());
        assertFalse(cmd.canUndo());
        assertNull(cmd.getGroupSpid());

        cmd.execute();

        assertTrue(groupCalled[0]);
        assertTrue(cmd.isExecuted());
        assertTrue(cmd.canUndo());
        assertEquals(groupSpid, cmd.getGroupSpid());
    }

    @Test
    @DisplayName("Execute throws when groupShapes fails")
    void executeThrowsOnFailure() {
        PPTXOrchestrator stub = new StubPPTXOrchestrator() {
            @Override
            public ExecutionResult<Integer> groupShapes(int slide, List<Integer> spids) {
                return ExecutionResult.failure("GroupShapes", "DOM error");
            }
        };

        GroupShapesCommand cmd = new GroupShapesCommand(1, TWO_SPIDS, stub);
        assertThrows(CommandExecutionException.class, cmd::execute);
        assertFalse(cmd.isExecuted());
    }

    @Test
    @DisplayName("Execute throws on double execution")
    void executeThrowsOnDoubleExecution() {
        PPTXOrchestrator stub = new StubPPTXOrchestrator() {
            @Override
            public ExecutionResult<Integer> groupShapes(int slide, List<Integer> spids) {
                return ExecutionResult.success("GroupShapes", 55);
            }
        };

        GroupShapesCommand cmd = new GroupShapesCommand(1, TWO_SPIDS, stub);
        cmd.execute();
        assertThrows(CommandExecutionException.class, cmd::execute);
    }

    // ========== UNDO ==========

    @Test
    @DisplayName("Undo calls ungroupShape with stored group SPID")
    void undoCallsUngroup() {
        final int groupSpid = 50;
        int[] ungroupedSpid = {-1};

        PPTXOrchestrator stub = new StubPPTXOrchestrator() {
            @Override
            public ExecutionResult<Integer> groupShapes(int slide, List<Integer> spids) {
                return ExecutionResult.success("GroupShapes", groupSpid);
            }

            @Override
            public ExecutionResult<List<Integer>> ungroupShape(int slide, int spid) {
                ungroupedSpid[0] = spid;
                return ExecutionResult.success("UngroupShape", TWO_SPIDS);
            }
        };

        GroupShapesCommand cmd = new GroupShapesCommand(1, TWO_SPIDS, stub);
        cmd.execute();

        cmd.undo();

        assertEquals(groupSpid, ungroupedSpid[0], "Should ungroup by the stored group SPID");
        assertFalse(cmd.isExecuted());
        assertNull(cmd.getGroupSpid());
        assertFalse(cmd.canUndo());
    }

    @Test
    @DisplayName("Undo throws when not yet executed")
    void undoThrowsWhenNotExecuted() {
        GroupShapesCommand cmd = new GroupShapesCommand(1, TWO_SPIDS, new StubPPTXOrchestrator());
        assertThrows(CommandExecutionException.class, cmd::undo);
    }

    // ========== DESCRIPTION AND GETTERS ==========

    @Test
    @DisplayName("Getters return construction values")
    void gettersReturnCorrectValues() {
        GroupShapesCommand cmd = new GroupShapesCommand(2, TWO_SPIDS, new StubPPTXOrchestrator());
        assertEquals(2, cmd.getSlideNumber());
        assertEquals(TWO_SPIDS, cmd.getSpids());
    }
}
