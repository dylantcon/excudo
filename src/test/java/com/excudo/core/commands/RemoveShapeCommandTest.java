package com.excudo.core.commands;

import com.excudo.core.commands.mutating.slide.RemoveShapeCommand;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.results.ExecutionResult;
import com.excudo.test.utils.StubPPTXOrchestrator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RemoveShapeCommand -- verifies the capture/remove/restore
 * lifecycle and proper undo/redo stack integration via CommandInvoker.
 */
public class RemoveShapeCommandTest {

    /**
     * Create a fake DOM element to simulate a captured shape.
     */
    private Element createFakeShapeElement() {
        try {
            Document doc = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder().newDocument();
            Element sp = doc.createElement("p:sp");
            Element cNvPr = doc.createElement("p:cNvPr");
            cNvPr.setAttribute("id", "5");
            cNvPr.setAttribute("name", "TestShape");
            sp.appendChild(cNvPr);
            return sp;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ========== CONSTRUCTION ==========

    @Test
    @DisplayName("Rejects null orchestrator")
    void constructorRejectsNullOrchestrator() {
        assertThrows(IllegalArgumentException.class,
            () -> new RemoveShapeCommand(1, 5, null));
    }

    @Test
    @DisplayName("Rejects invalid slide number")
    void constructorRejectsInvalidSlide() {
        assertThrows(IllegalArgumentException.class,
            () -> new RemoveShapeCommand(0, 5, new StubPPTXOrchestrator()));
    }

    @Test
    @DisplayName("Rejects invalid SPID")
    void constructorRejectsInvalidSpid() {
        assertThrows(IllegalArgumentException.class,
            () -> new RemoveShapeCommand(1, -1, new StubPPTXOrchestrator()));
    }

    // ========== EXECUTE ==========

    @Test
    @DisplayName("Execute captures shape element then removes it")
    void executeCaptureThenRemove() {
        Element fakeElement = createFakeShapeElement();
        boolean[] captureCalled = {false};
        boolean[] removeCalled = {false};

        PPTXOrchestrator stub = new StubPPTXOrchestrator() {
            @Override
            public ExecutionResult<Element> captureShapeElement(int slide, int spid) {
                captureCalled[0] = true;
                assertEquals(1, slide);
                assertEquals(5, spid);
                return ExecutionResult.success("CaptureShape", fakeElement);
            }

            @Override
            public ExecutionResult<Void> removeShape(int slide, int spid) {
                // Capture must happen before remove
                assertTrue(captureCalled[0], "captureShapeElement must be called before removeShape");
                removeCalled[0] = true;
                return ExecutionResult.success("RemoveShape", null);
            }
        };

        RemoveShapeCommand cmd = new RemoveShapeCommand(1, 5, stub);

        assertFalse(cmd.isExecuted());
        assertFalse(cmd.canUndo());

        cmd.execute();

        assertTrue(captureCalled[0], "Should have captured shape element");
        assertTrue(removeCalled[0], "Should have removed shape");
        assertTrue(cmd.isExecuted());
        assertTrue(cmd.canUndo(), "Should be undoable after successful capture");
    }

    @Test
    @DisplayName("Execute throws when capture fails")
    void executeThrowsOnCaptureFail() {
        PPTXOrchestrator stub = new StubPPTXOrchestrator() {
            @Override
            public ExecutionResult<Element> captureShapeElement(int slide, int spid) {
                return ExecutionResult.failure("CaptureShape", "Shape not found");
            }
        };

        RemoveShapeCommand cmd = new RemoveShapeCommand(1, 5, stub);
        assertThrows(CommandExecutionException.class, cmd::execute);
        assertFalse(cmd.isExecuted());
        assertFalse(cmd.canUndo());
    }

    @Test
    @DisplayName("Execute throws when remove fails after successful capture")
    void executeThrowsOnRemoveFail() {
        PPTXOrchestrator stub = new StubPPTXOrchestrator() {
            @Override
            public ExecutionResult<Element> captureShapeElement(int slide, int spid) {
                return ExecutionResult.success("CaptureShape", createFakeShapeElement());
            }

            @Override
            public ExecutionResult<Void> removeShape(int slide, int spid) {
                return ExecutionResult.failure("RemoveShape", "DOM error");
            }
        };

        RemoveShapeCommand cmd = new RemoveShapeCommand(1, 5, stub);
        assertThrows(CommandExecutionException.class, cmd::execute);
        assertFalse(cmd.isExecuted());
        assertFalse(cmd.canUndo(), "Should not be undoable when remove failed");
    }

    @Test
    @DisplayName("Execute throws on double execution")
    void executeThrowsOnDoubleExecution() {
        PPTXOrchestrator stub = new StubPPTXOrchestrator() {
            @Override
            public ExecutionResult<Element> captureShapeElement(int s, int sp) {
                return ExecutionResult.success("CaptureShape", createFakeShapeElement());
            }
            @Override
            public ExecutionResult<Void> removeShape(int s, int sp) {
                return ExecutionResult.success("RemoveShape", null);
            }
        };

        RemoveShapeCommand cmd = new RemoveShapeCommand(1, 5, stub);
        cmd.execute();
        assertThrows(CommandExecutionException.class, cmd::execute);
    }

    // ========== UNDO ==========

    @Test
    @DisplayName("Undo restores captured element")
    void undoRestoresShape() {
        Element fakeElement = createFakeShapeElement();
        Element[] restoredElement = {null};
        int[] restoreSlide = {0};

        PPTXOrchestrator stub = new StubPPTXOrchestrator() {
            @Override
            public ExecutionResult<Element> captureShapeElement(int s, int sp) {
                return ExecutionResult.success("CaptureShape", fakeElement);
            }
            @Override
            public ExecutionResult<Void> removeShape(int s, int sp) {
                return ExecutionResult.success("RemoveShape", null);
            }
            @Override
            public ExecutionResult<Void> restoreShape(int slide, Element element) {
                restoreSlide[0] = slide;
                restoredElement[0] = element;
                return ExecutionResult.success("RestoreShape", null);
            }
        };

        RemoveShapeCommand cmd = new RemoveShapeCommand(1, 5, stub);
        cmd.execute();
        assertTrue(cmd.canUndo());

        cmd.undo();

        assertEquals(1, restoreSlide[0], "Should restore to same slide");
        assertSame(fakeElement, restoredElement[0], "Should restore the exact captured element");
        assertFalse(cmd.isExecuted());
        assertFalse(cmd.canUndo(), "Should not be undoable after undo");
    }

    @Test
    @DisplayName("Undo throws when not executed")
    void undoThrowsWhenNotExecuted() {
        RemoveShapeCommand cmd = new RemoveShapeCommand(1, 5, new StubPPTXOrchestrator());
        assertThrows(CommandExecutionException.class, cmd::undo);
    }

    @Test
    @DisplayName("Undo throws when restore fails")
    void undoThrowsOnRestoreFail() {
        PPTXOrchestrator stub = new StubPPTXOrchestrator() {
            @Override
            public ExecutionResult<Element> captureShapeElement(int s, int sp) {
                return ExecutionResult.success("CaptureShape", createFakeShapeElement());
            }
            @Override
            public ExecutionResult<Void> removeShape(int s, int sp) {
                return ExecutionResult.success("RemoveShape", null);
            }
            @Override
            public ExecutionResult<Void> restoreShape(int slide, Element element) {
                return ExecutionResult.failure("RestoreShape", "spTree not found");
            }
        };

        RemoveShapeCommand cmd = new RemoveShapeCommand(1, 5, stub);
        cmd.execute();
        assertThrows(CommandExecutionException.class, cmd::undo);
        assertTrue(cmd.isExecuted(), "Should remain executed when undo fails");
    }

    // ========== REDO (execute after undo) ==========

    @Test
    @DisplayName("Redo re-captures and re-removes")
    void redoRecapturesAndReremoves() {
        int[] captureCount = {0};
        int[] removeCount = {0};

        PPTXOrchestrator stub = new StubPPTXOrchestrator() {
            @Override
            public ExecutionResult<Element> captureShapeElement(int s, int sp) {
                captureCount[0]++;
                return ExecutionResult.success("CaptureShape", createFakeShapeElement());
            }
            @Override
            public ExecutionResult<Void> removeShape(int s, int sp) {
                removeCount[0]++;
                return ExecutionResult.success("RemoveShape", null);
            }
            @Override
            public ExecutionResult<Void> restoreShape(int slide, Element element) {
                return ExecutionResult.success("RestoreShape", null);
            }
        };

        RemoveShapeCommand cmd = new RemoveShapeCommand(1, 5, stub);

        cmd.execute();  // capture #1, remove #1
        assertEquals(1, captureCount[0]);
        assertEquals(1, removeCount[0]);

        cmd.undo();     // restore
        assertFalse(cmd.isExecuted());

        cmd.execute();  // capture #2, remove #2 (redo)
        assertEquals(2, captureCount[0], "Redo should re-capture fresh state");
        assertEquals(2, removeCount[0], "Redo should re-remove");
        assertTrue(cmd.isExecuted());
        assertTrue(cmd.canUndo());
    }

    // ========== COMMANDINVOKER INTEGRATION ==========

    @Test
    @DisplayName("CommandInvoker pushes RemoveShapeCommand onto undo stack")
    void invokerPushesToUndoStack() {
        PPTXOrchestrator stub = new StubPPTXOrchestrator() {
            @Override
            public ExecutionResult<Element> captureShapeElement(int s, int sp) {
                return ExecutionResult.success("CaptureShape", createFakeShapeElement());
            }
            @Override
            public ExecutionResult<Void> removeShape(int s, int sp) {
                return ExecutionResult.success("RemoveShape", null);
            }
            @Override
            public ExecutionResult<Void> restoreShape(int slide, Element element) {
                return ExecutionResult.success("RestoreShape", null);
            }
        };

        CommandInvoker invoker = new CommandInvoker();
        RemoveShapeCommand cmd = new RemoveShapeCommand(1, 5, stub);

        assertEquals(0, invoker.getHistorySize());

        invoker.executeCommand(cmd);

        assertEquals(1, invoker.getHistorySize(), "Command should be on undo stack");
        assertTrue(invoker.canUndo());
        assertEquals("Remove shape SPID 5 from slide 1", invoker.getUndoDescription());
    }

    @Test
    @DisplayName("CommandInvoker undo/redo full cycle")
    void invokerUndoRedoCycle() {
        int[] captureCount = {0};
        int[] removeCount = {0};
        int[] restoreCount = {0};

        PPTXOrchestrator stub = new StubPPTXOrchestrator() {
            @Override
            public ExecutionResult<Element> captureShapeElement(int s, int sp) {
                captureCount[0]++;
                return ExecutionResult.success("CaptureShape", createFakeShapeElement());
            }
            @Override
            public ExecutionResult<Void> removeShape(int s, int sp) {
                removeCount[0]++;
                return ExecutionResult.success("RemoveShape", null);
            }
            @Override
            public ExecutionResult<Void> restoreShape(int slide, Element element) {
                restoreCount[0]++;
                return ExecutionResult.success("RestoreShape", null);
            }
        };

        CommandInvoker invoker = new CommandInvoker();
        invoker.executeCommand(new RemoveShapeCommand(1, 5, stub));

        // Verify execute happened
        assertEquals(1, captureCount[0]);
        assertEquals(1, removeCount[0]);
        assertEquals(0, restoreCount[0]);
        assertEquals(1, invoker.getHistorySize());
        assertEquals(0, invoker.getRedoHistorySize());

        // Undo
        assertTrue(invoker.undo());
        assertEquals(1, restoreCount[0], "Undo should restore");
        assertEquals(0, invoker.getHistorySize());
        assertEquals(1, invoker.getRedoHistorySize());

        // Redo
        assertTrue(invoker.redo());
        assertEquals(2, captureCount[0], "Redo should re-capture");
        assertEquals(2, removeCount[0], "Redo should re-remove");
        assertEquals(1, invoker.getHistorySize());
        assertEquals(0, invoker.getRedoHistorySize());

        // Undo again
        assertTrue(invoker.undo());
        assertEquals(2, restoreCount[0]);
    }

    // ========== DESCRIPTION AND GETTERS ==========

    @Test
    @DisplayName("Description includes slide and SPID")
    void descriptionFormat() {
        RemoveShapeCommand cmd = new RemoveShapeCommand(3, 42, new StubPPTXOrchestrator());
        assertEquals("Remove shape SPID 42 from slide 3", cmd.getDescription());
    }

    @Test
    @DisplayName("Getters return construction values")
    void gettersReturnCorrectValues() {
        RemoveShapeCommand cmd = new RemoveShapeCommand(2, 7, new StubPPTXOrchestrator());
        assertEquals(2, cmd.getSlideNumber());
        assertEquals(7, cmd.getSpid());
    }
}
