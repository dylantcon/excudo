package com.excudo.core.commands;

import com.excudo.core.commands.mutating.slide.SetStyleCommand;

import com.excudo.core.model.ShapeStyle;
import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.results.ExecutionResult;
import com.excudo.test.utils.StubPPTXOrchestrator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SetStyleCommand.
 *
 * Verifies construction validation, execute/undo lifecycle, double-execute guard,
 * canUndo state transitions, and accessor methods.
 */
public class SetStyleCommandTest {

    private static final ShapeStyle DEFAULT_STYLE = ShapeStyle.defaultStyle();

    private Element createFakeElement() {
        try {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
            Element sp = doc.createElement("p:sp");
            sp.setAttribute("id", "20");
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
            () -> new SetStyleCommand(1, 5, DEFAULT_STYLE, null));
    }

    @Test
    @DisplayName("Rejects slide number zero or negative")
    void constructorRejectsInvalidSlide() {
        assertThrows(IllegalArgumentException.class,
            () -> new SetStyleCommand(0, 5, DEFAULT_STYLE, new StubPPTXOrchestrator()));
    }

    @Test
    @DisplayName("Rejects SPID zero or negative")
    void constructorRejectsInvalidSpid() {
        assertThrows(IllegalArgumentException.class,
            () -> new SetStyleCommand(1, -1, DEFAULT_STYLE, new StubPPTXOrchestrator()));
    }

    @Test
    @DisplayName("Rejects null ShapeStyle")
    void constructorRejectsNullStyle() {
        assertThrows(IllegalArgumentException.class,
            () -> new SetStyleCommand(1, 5, null, new StubPPTXOrchestrator()));
    }

    // ========== EXECUTE ==========

    @Test
    @DisplayName("Execute captures element then applies style")
    void executeCaptureThenApplyStyle() {
        Element fakeElement = createFakeElement();
        boolean[] captureCalled = {false};
        boolean[] styleCalled = {false};

        PPTXOrchestrator stub = new StubPPTXOrchestrator() {
            @Override
            public ExecutionResult<Element> captureShapeElement(int slide, int spid) {
                captureCalled[0] = true;
                assertEquals(1, slide);
                assertEquals(5, spid);
                return ExecutionResult.success("CaptureShape", fakeElement);
            }

            @Override
            public ExecutionResult<Void> updateShapeStyle(int slide, int spid, ShapeStyle style) {
                assertTrue(captureCalled[0], "capture must precede style update");
                styleCalled[0] = true;
                return ExecutionResult.success("UpdateStyle", null);
            }
        };

        SetStyleCommand cmd = new SetStyleCommand(1, 5, DEFAULT_STYLE, stub);

        assertFalse(cmd.isExecuted());
        assertFalse(cmd.canUndo());

        cmd.execute();

        assertTrue(captureCalled[0]);
        assertTrue(styleCalled[0]);
        assertTrue(cmd.isExecuted());
        assertTrue(cmd.canUndo());
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

        SetStyleCommand cmd = new SetStyleCommand(1, 5, DEFAULT_STYLE, stub);
        assertThrows(CommandExecutionException.class, cmd::execute);
        assertFalse(cmd.isExecuted());
        assertFalse(cmd.canUndo());
    }

    @Test
    @DisplayName("Execute throws on double execution")
    void executeThrowsOnDoubleExecution() {
        PPTXOrchestrator stub = new StubPPTXOrchestrator() {
            @Override
            public ExecutionResult<Element> captureShapeElement(int s, int sp) {
                return ExecutionResult.success("CaptureShape", createFakeElement());
            }

            @Override
            public ExecutionResult<Void> updateShapeStyle(int s, int sp, ShapeStyle style) {
                return ExecutionResult.success("UpdateStyle", null);
            }
        };

        SetStyleCommand cmd = new SetStyleCommand(1, 5, DEFAULT_STYLE, stub);
        cmd.execute();
        assertThrows(CommandExecutionException.class, cmd::execute);
    }

    // ========== UNDO ==========

    @Test
    @DisplayName("Undo removes styled shape then restores original element")
    void undoRemoveThenRestore() {
        Element fakeElement = createFakeElement();
        boolean[] removeCalled = {false};
        Element[] restoredElement = {null};

        PPTXOrchestrator stub = new StubPPTXOrchestrator() {
            @Override
            public ExecutionResult<Element> captureShapeElement(int s, int sp) {
                return ExecutionResult.success("CaptureShape", fakeElement);
            }

            @Override
            public ExecutionResult<Void> updateShapeStyle(int s, int sp, ShapeStyle style) {
                return ExecutionResult.success("UpdateStyle", null);
            }

            @Override
            public ExecutionResult<Void> removeShape(int slide, int spid) {
                removeCalled[0] = true;
                return ExecutionResult.success("RemoveShape", null);
            }

            @Override
            public ExecutionResult<Void> restoreShape(int slide, Element element) {
                restoredElement[0] = element;
                return ExecutionResult.success("RestoreShape", null);
            }
        };

        SetStyleCommand cmd = new SetStyleCommand(1, 5, DEFAULT_STYLE, stub);
        cmd.execute();
        assertTrue(cmd.canUndo());

        cmd.undo();

        assertTrue(removeCalled[0]);
        assertSame(fakeElement, restoredElement[0]);
        assertFalse(cmd.isExecuted());
        assertFalse(cmd.canUndo());
    }

    @Test
    @DisplayName("Undo throws when not yet executed")
    void undoThrowsWhenNotExecuted() {
        SetStyleCommand cmd = new SetStyleCommand(1, 5, DEFAULT_STYLE, new StubPPTXOrchestrator());
        assertThrows(CommandExecutionException.class, cmd::undo);
    }

    // ========== ACCESSORS ==========

    @Test
    @DisplayName("Getters return construction values")
    void gettersReturnCorrectValues() {
        SetStyleCommand cmd = new SetStyleCommand(2, 9, DEFAULT_STYLE, new StubPPTXOrchestrator());
        assertEquals(2, cmd.getSlideNumber());
        assertEquals(9, cmd.getSpid());
        assertSame(DEFAULT_STYLE, cmd.getStyle());
    }
}
