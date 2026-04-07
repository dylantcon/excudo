package com.excudo.core.commands;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.List;

/**
 * Unit tests for CommandInvoker - the GoF Command Pattern Invoker.
 *
 * Verifies history management, undo/redo lifecycle, history size limits,
 * save point tracking, and guard clauses.
 */
public class CommandInvokerTest {

    // ========== Test Helpers ==========

    private Command createMockCommand(String description) {
        return new Command() {
            private boolean executed = false;

            public void execute() { executed = true; }
            public void undo() { executed = false; }
            public boolean canUndo() { return true; }
            public String getDescription() { return description; }
            public boolean isExecuted() { return executed; }
        };
    }

    private Command createNonUndoableCommand(String description) {
        return new Command() {
            private boolean executed = false;

            public void execute() { executed = true; }
            public void undo() { /* no-op */ }
            public boolean canUndo() { return false; }
            public String getDescription() { return description; }
            public boolean isExecuted() { return executed; }
        };
    }

    // ========== History Accumulation ==========

    @Test
    public void executeCommand_addsToHistory() {
        CommandInvoker invoker = new CommandInvoker();
        assertEquals(0, invoker.getHistorySize());

        invoker.executeCommand(createMockCommand("cmd-1"));
        assertEquals(1, invoker.getHistorySize());

        invoker.executeCommand(createMockCommand("cmd-2"));
        assertEquals(2, invoker.getHistorySize());
    }

    @Test
    public void executeCommand_nonUndoable_doesNotAddToHistory() {
        CommandInvoker invoker = new CommandInvoker();

        invoker.executeCommand(createNonUndoableCommand("list"));
        assertEquals(0, invoker.getHistorySize());
    }

    // ========== Undo ==========

    @Test
    public void undo_afterExecute_decrementsHistoryAndEnablesRedo() {
        CommandInvoker invoker = new CommandInvoker();
        invoker.executeCommand(createMockCommand("cmd-1"));

        boolean result = invoker.undo();

        assertTrue("undo() should return true when a command was undone", result);
        assertEquals(0, invoker.getHistorySize());
        assertTrue("canRedo() should be true after undo", invoker.canRedo());
        assertEquals(1, invoker.getRedoHistorySize());
    }

    @Test
    public void undo_onEmptyHistory_returnsFalse() {
        CommandInvoker invoker = new CommandInvoker();

        boolean result = invoker.undo();

        assertFalse("undo() on empty history should return false", result);
    }

    // ========== Redo ==========

    @Test
    public void redo_afterUndo_restoresHistory() {
        CommandInvoker invoker = new CommandInvoker();
        invoker.executeCommand(createMockCommand("cmd-1"));
        invoker.undo();

        boolean result = invoker.redo();

        assertTrue("redo() should return true when a command was redone", result);
        assertEquals(1, invoker.getHistorySize());
        assertEquals(0, invoker.getRedoHistorySize());
    }

    @Test
    public void redo_onEmptyRedoStack_returnsFalse() {
        CommandInvoker invoker = new CommandInvoker();

        boolean result = invoker.redo();

        assertFalse("redo() on empty redo stack should return false", result);
    }

    // ========== Full Execute-Undo-Redo Cycle ==========

    @Test
    public void fullCycle_executeUndoRedo_commandStateTracksCorrectly() {
        CommandInvoker invoker = new CommandInvoker();
        Command cmd = createMockCommand("cycle-cmd");

        invoker.executeCommand(cmd);
        assertTrue("Command should be executed after execute()", cmd.isExecuted());

        invoker.undo();
        assertFalse("Command should not be executed after undo()", cmd.isExecuted());

        invoker.redo();
        assertTrue("Command should be re-executed after redo()", cmd.isExecuted());
    }

    // ========== Redo History Cleared on New Execute ==========

    @Test
    public void executeAfterUndo_clearsRedoHistory() {
        CommandInvoker invoker = new CommandInvoker();
        invoker.executeCommand(createMockCommand("original"));
        invoker.undo();
        assertEquals(1, invoker.getRedoHistorySize());

        invoker.executeCommand(createMockCommand("new-branch"));

        assertEquals(0, invoker.getRedoHistorySize());
        assertFalse("canRedo() should be false after executing a new command", invoker.canRedo());
    }

    // ========== History Size Limit ==========

    @Test
    public void historySizeLimit_excessCommandsDroppedFromBottom() {
        CommandInvoker invoker = new CommandInvoker(3);

        for (int i = 1; i <= 5; i++) {
            invoker.executeCommand(createMockCommand("cmd-" + i));
        }

        assertEquals("History size must not exceed maxHistorySize", 3, invoker.getHistorySize());

        List<String> history = invoker.getUndoHistory();
        assertEquals("cmd-5", history.get(0));
        assertEquals("cmd-4", history.get(1));
        assertEquals("cmd-3", history.get(2));
    }

    // ========== canUndo / canRedo Guards ==========

    @Test
    public void canUndo_emptyHistory_returnsFalse() {
        assertFalse(new CommandInvoker().canUndo());
    }

    @Test
    public void canRedo_emptyRedoStack_returnsFalse() {
        assertFalse(new CommandInvoker().canRedo());
    }

    @Test
    public void canUndo_afterExecute_returnsTrue() {
        CommandInvoker invoker = new CommandInvoker();
        invoker.executeCommand(createMockCommand("cmd"));
        assertTrue(invoker.canUndo());
    }

    @Test
    public void canRedo_afterUndo_returnsTrue() {
        CommandInvoker invoker = new CommandInvoker();
        invoker.executeCommand(createMockCommand("cmd"));
        invoker.undo();
        assertTrue(invoker.canRedo());
    }

    // ========== Null Guard ==========

    @Test(expected = IllegalArgumentException.class)
    public void executeCommand_null_throwsIllegalArgumentException() {
        new CommandInvoker().executeCommand(null);
    }

    // ========== Description Peek ==========

    @Test
    public void getUndoDescription_returnsTopCommandDescription() {
        CommandInvoker invoker = new CommandInvoker();
        invoker.executeCommand(createMockCommand("first"));
        invoker.executeCommand(createMockCommand("second"));

        assertEquals("second", invoker.getUndoDescription());
    }

    @Test
    public void getUndoDescription_emptyHistory_returnsNull() {
        assertNull(new CommandInvoker().getUndoDescription());
    }

    @Test
    public void getRedoDescription_afterUndo_returnsUndoneCommandDescription() {
        CommandInvoker invoker = new CommandInvoker();
        invoker.executeCommand(createMockCommand("mine"));
        invoker.undo();

        assertEquals("mine", invoker.getRedoDescription());
    }

    @Test
    public void getRedoDescription_emptyRedoStack_returnsNull() {
        assertNull(new CommandInvoker().getRedoDescription());
    }

    // ========== Save Point Tracking ==========

    @Test
    public void markSavePoint_thenNoChanges_hasUnsavedChangesReturnsFalse() {
        CommandInvoker invoker = new CommandInvoker();
        invoker.executeCommand(createMockCommand("saved-cmd"));

        invoker.markSavePoint();

        assertFalse("No changes since save point - should report no unsaved changes",
                invoker.hasUnsavedChanges());
    }

    @Test
    public void hasUnsavedChanges_afterExecutePostSavePoint_returnsTrue() {
        CommandInvoker invoker = new CommandInvoker();
        invoker.executeCommand(createMockCommand("cmd-1"));
        invoker.markSavePoint();

        invoker.executeCommand(createMockCommand("cmd-2"));

        assertTrue("New command executed after save point - should have unsaved changes",
                invoker.hasUnsavedChanges());
    }

    @Test
    public void hasUnsavedChanges_initialState_returnsTrue() {
        CommandInvoker invoker = new CommandInvoker();
        invoker.executeCommand(createMockCommand("cmd-1"));

        // No markSavePoint() called - save point is at size 0, history size is 1
        assertTrue("History differs from default save point of 0 - should have unsaved changes",
                invoker.hasUnsavedChanges());
    }

    @Test
    public void hasUnsavedChanges_undoBackToSavePoint_returnsFalse() {
        CommandInvoker invoker = new CommandInvoker();
        invoker.markSavePoint(); // save point at 0

        invoker.executeCommand(createMockCommand("cmd-1"));
        assertTrue(invoker.hasUnsavedChanges());

        invoker.undo();
        assertFalse("Undone back to save point - should have no unsaved changes",
                invoker.hasUnsavedChanges());
    }

    // ========== clearHistory ==========

    @Test
    public void clearHistory_resetsUndoAndRedoStacks() {
        CommandInvoker invoker = new CommandInvoker();
        invoker.executeCommand(createMockCommand("cmd-1"));
        invoker.executeCommand(createMockCommand("cmd-2"));
        invoker.undo();

        invoker.clearHistory();

        assertEquals(0, invoker.getHistorySize());
        assertEquals(0, invoker.getRedoHistorySize());
        assertFalse(invoker.canUndo());
        assertFalse(invoker.canRedo());
    }

    // ========== History List Contents ==========

    @Test
    public void getUndoHistory_mostRecentFirst() {
        CommandInvoker invoker = new CommandInvoker();
        invoker.executeCommand(createMockCommand("alpha"));
        invoker.executeCommand(createMockCommand("beta"));
        invoker.executeCommand(createMockCommand("gamma"));

        List<String> history = invoker.getUndoHistory();

        assertEquals(3, history.size());
        assertEquals("gamma", history.get(0));
        assertEquals("beta", history.get(1));
        assertEquals("alpha", history.get(2));
    }

    @Test
    public void getRedoHistory_mostRecentlyUndoneFirst() {
        CommandInvoker invoker = new CommandInvoker();
        invoker.executeCommand(createMockCommand("alpha"));
        invoker.executeCommand(createMockCommand("beta"));
        invoker.undo(); // beta goes to redo
        invoker.undo(); // alpha goes to redo

        List<String> redoHistory = invoker.getRedoHistory();

        assertEquals(2, redoHistory.size());
        assertEquals("alpha", redoHistory.get(0));
        assertEquals("beta", redoHistory.get(1));
    }
}
