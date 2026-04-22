package com.excudo.core.commands;

import com.excudo.core.commands.mutating.slide.SetFontCommand;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.results.ExecutionResult;
import com.excudo.test.utils.StubPPTXOrchestrator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SetFontCommand.
 *
 * Verifies construction validation, execute/undo lifecycle, double-execute guard,
 * canUndo state transitions, and description format.
 */
public class SetFontCommandTest {

    private static final Map<String, Object> FONT_PROPS = Map.of("family", "Arial", "bold", true);

    private Element createFakeElement() {
        try {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
            Element sp = doc.createElement("p:sp");
            sp.setAttribute("id", "10");
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
            () -> new SetFontCommand(1, 5, FONT_PROPS, null));
    }

    @Test
    @DisplayName("Rejects slide number zero or negative")
    void constructorRejectsInvalidSlide() {
        assertThrows(IllegalArgumentException.class,
            () -> new SetFontCommand(0, 5, FONT_PROPS, new StubPPTXOrchestrator()));
    }

    @Test
    @DisplayName("Rejects SPID zero or negative")
    void constructorRejectsInvalidSpid() {
        assertThrows(IllegalArgumentException.class,
            () -> new SetFontCommand(1, 0, FONT_PROPS, new StubPPTXOrchestrator()));
    }

    @Test
    @DisplayName("Rejects null font properties map")
    void constructorRejectsNullFontProps() {
        assertThrows(IllegalArgumentException.class,
            () -> new SetFontCommand(1, 5, null, new StubPPTXOrchestrator()));
    }

    @Test
    @DisplayName("Rejects empty font properties map")
    void constructorRejectsEmptyFontProps() {
        assertThrows(IllegalArgumentException.class,
            () -> new SetFontCommand(1, 5, Map.of(), new StubPPTXOrchestrator()));
    }

    // ========== EXECUTE ==========

    @Test
    @DisplayName("Execute captures element then updates font properties")
    void executeCaptureThenUpdateFont() {
        Element fakeElement = createFakeElement();
        boolean[] captureCalled = {false};
        boolean[] updateCalled = {false};

        PPTXOrchestrator stub = new StubPPTXOrchestrator() {
            @Override
            public ExecutionResult<Element> captureShapeElement(int slide, int spid) {
                captureCalled[0] = true;
                assertEquals(1, slide);
                assertEquals(5, spid);
                return ExecutionResult.success("CaptureShape", fakeElement);
            }

            @Override
            public ExecutionResult<Void> updateShapeTextProperties(int slide, int spid,
                                                                    java.util.Map<String, Object> props) {
                assertTrue(captureCalled[0], "capture must precede update");
                updateCalled[0] = true;
                return ExecutionResult.success("UpdateFont", null);
            }
        };

        SetFontCommand cmd = new SetFontCommand(1, 5, FONT_PROPS, stub);

        assertFalse(cmd.isExecuted());
        assertFalse(cmd.canUndo());

        cmd.execute();

        assertTrue(captureCalled[0]);
        assertTrue(updateCalled[0]);
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

        SetFontCommand cmd = new SetFontCommand(1, 5, FONT_PROPS, stub);
        assertThrows(CommandExecutionException.class, cmd::execute);
        assertFalse(cmd.isExecuted());
        assertFalse(cmd.canUndo());
    }

    @Test
    @DisplayName("Execute throws on second call without undo")
    void executeThrowsOnDoubleExecution() {
        PPTXOrchestrator stub = new StubPPTXOrchestrator() {
            @Override
            public ExecutionResult<Element> captureShapeElement(int s, int sp) {
                return ExecutionResult.success("CaptureShape", createFakeElement());
            }

            @Override
            public ExecutionResult<Void> updateShapeTextProperties(int s, int sp,
                                                                    java.util.Map<String, Object> p) {
                return ExecutionResult.success("UpdateFont", null);
            }
        };

        SetFontCommand cmd = new SetFontCommand(1, 5, FONT_PROPS, stub);
        cmd.execute();
        assertThrows(CommandExecutionException.class, cmd::execute);
    }

    // ========== UNDO ==========

    @Test
    @DisplayName("Undo removes modified shape then restores original element")
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
            public ExecutionResult<Void> updateShapeTextProperties(int s, int sp,
                                                                    java.util.Map<String, Object> p) {
                return ExecutionResult.success("UpdateFont", null);
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

        SetFontCommand cmd = new SetFontCommand(1, 5, FONT_PROPS, stub);
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
        SetFontCommand cmd = new SetFontCommand(1, 5, FONT_PROPS, new StubPPTXOrchestrator());
        assertThrows(CommandExecutionException.class, cmd::undo);
    }

    // ========== DESCRIPTION ==========

    @Test
    @DisplayName("Description identifies slide and SPID")
    void descriptionFormat() {
        SetFontCommand cmd = new SetFontCommand(3, 42, FONT_PROPS, new StubPPTXOrchestrator());
        String desc = cmd.getDescription();
        assertTrue(desc.contains("42"), "Description should include SPID");
        assertTrue(desc.contains("3"), "Description should include slide number");
    }

    @Test
    @DisplayName("Getters return construction values")
    void gettersReturnCorrectValues() {
        SetFontCommand cmd = new SetFontCommand(2, 7, FONT_PROPS, new StubPPTXOrchestrator());
        assertEquals(2, cmd.getSlideNumber());
        assertEquals(7, cmd.getSpid());
    }
}
