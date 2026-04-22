package com.excudo.core.commands;

import com.excudo.core.commands.mutating.slide.UngroupCommand;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.results.ExecutionResult;
import com.excudo.test.utils.StubPPTXOrchestrator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for UngroupCommand.
 *
 * Verifies construction validation, execute stores child SPIDs returned from
 * ungroupShape, undo re-groups those children, canUndo state, and double-execute guard.
 */
public class UngroupCommandTest {

    private static final List<Integer> CHILD_SPIDS = Arrays.asList(11, 22, 33);

    // ========== CONSTRUCTION ==========

    @Test
    @DisplayName("Rejects null orchestrator")
    void constructorRejectsNullOrchestrator() {
        assertThrows(IllegalArgumentException.class,
            () -> new UngroupCommand(1, 5, null));
    }

    @Test
    @DisplayName("Rejects slide number zero or negative")
    void constructorRejectsInvalidSlide() {
        assertThrows(IllegalArgumentException.class,
            () -> new UngroupCommand(0, 5, new StubPPTXOrchestrator()));
    }

    @Test
    @DisplayName("Rejects SPID zero or negative")
    void constructorRejectsInvalidSpid() {
        assertThrows(IllegalArgumentException.class,
            () -> new UngroupCommand(1, 0, new StubPPTXOrchestrator()));
    }

    // ========== EXECUTE ==========

    @Test
    @DisplayName("Execute calls ungroupShape and stores child SPIDs")
    void executeStoresChildSpids() {
        boolean[] ungroupCalled = {false};

        PPTXOrchestrator stub = new StubPPTXOrchestrator() {
            @Override
            public ExecutionResult<List<Integer>> ungroupShape(int slide, int spid) {
                ungroupCalled[0] = true;
                assertEquals(1, slide);
                assertEquals(5, spid);
                return ExecutionResult.success("UngroupShape", CHILD_SPIDS);
            }
        };

        UngroupCommand cmd = new UngroupCommand(1, 5, stub);

        assertFalse(cmd.isExecuted());
        assertFalse(cmd.canUndo());
        assertNull(cmd.getChildSpids());

        cmd.execute();

        assertTrue(ungroupCalled[0]);
        assertTrue(cmd.isExecuted());
        // canUndo requires at least 2 child SPIDs
        assertTrue(cmd.canUndo());
        assertEquals(CHILD_SPIDS, cmd.getChildSpids());
    }

    @Test
    @DisplayName("Execute throws when ungroupShape fails")
    void executeThrowsOnFailure() {
        PPTXOrchestrator stub = new StubPPTXOrchestrator() {
            @Override
            public ExecutionResult<List<Integer>> ungroupShape(int slide, int spid) {
                return ExecutionResult.failure("UngroupShape", "Not a group shape");
            }
        };

        UngroupCommand cmd = new UngroupCommand(1, 5, stub);
        assertThrows(CommandExecutionException.class, cmd::execute);
        assertFalse(cmd.isExecuted());
    }

    @Test
    @DisplayName("Execute throws on double execution")
    void executeThrowsOnDoubleExecution() {
        PPTXOrchestrator stub = new StubPPTXOrchestrator() {
            @Override
            public ExecutionResult<List<Integer>> ungroupShape(int slide, int spid) {
                return ExecutionResult.success("UngroupShape", CHILD_SPIDS);
            }
        };

        UngroupCommand cmd = new UngroupCommand(1, 5, stub);
        cmd.execute();
        assertThrows(CommandExecutionException.class, cmd::execute);
    }

    // ========== UNDO ==========

    @Test
    @DisplayName("Undo re-groups child SPIDs returned from execute")
    void undoRegroups() {
        List<Integer>[] groupedSpids = new List[1];

        PPTXOrchestrator stub = new StubPPTXOrchestrator() {
            @Override
            public ExecutionResult<List<Integer>> ungroupShape(int slide, int spid) {
                return ExecutionResult.success("UngroupShape", CHILD_SPIDS);
            }

            @Override
            public ExecutionResult<Integer> groupShapes(int slide, List<Integer> spids) {
                groupedSpids[0] = spids;
                return ExecutionResult.success("GroupShapes", 99);
            }
        };

        UngroupCommand cmd = new UngroupCommand(1, 5, stub);
        cmd.execute();
        assertTrue(cmd.canUndo());

        cmd.undo();

        assertEquals(CHILD_SPIDS, groupedSpids[0], "Should re-group the child SPIDs");
        assertFalse(cmd.isExecuted());
        assertNull(cmd.getChildSpids());
        assertFalse(cmd.canUndo());
    }

    @Test
    @DisplayName("Undo throws when not yet executed")
    void undoThrowsWhenNotExecuted() {
        UngroupCommand cmd = new UngroupCommand(1, 5, new StubPPTXOrchestrator());
        assertThrows(CommandExecutionException.class, cmd::undo);
    }

    // ========== DESCRIPTION AND GETTERS ==========

    @Test
    @DisplayName("Description identifies slide and SPID")
    void descriptionFormat() {
        UngroupCommand cmd = new UngroupCommand(3, 15, new StubPPTXOrchestrator());
        String desc = cmd.getDescription();
        assertTrue(desc.contains("15"), "Should include SPID");
        assertTrue(desc.contains("3"), "Should include slide number");
    }

    @Test
    @DisplayName("Getters return construction values")
    void gettersReturnCorrectValues() {
        UngroupCommand cmd = new UngroupCommand(4, 8, new StubPPTXOrchestrator());
        assertEquals(4, cmd.getSlideNumber());
        assertEquals(8, cmd.getSpid());
    }
}
