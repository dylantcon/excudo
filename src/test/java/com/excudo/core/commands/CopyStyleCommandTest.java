package com.excudo.core.commands;

import com.excudo.core.orchestration.PPTXOrchestrator;
import com.excudo.core.results.ExecutionResult;
import com.excudo.test.utils.StubPPTXOrchestrator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CopyStyleCommand.
 *
 * Verifies construction validation, execute captures each target before applying
 * the style, undo restores targets in reverse, canUndo state, and double-execute guard.
 */
public class CopyStyleCommandTest {

    private static final int SOURCE_SPID = 5;
    private static final List<Integer> TWO_TARGETS = Arrays.asList(10, 20);

    private Element createFakeElement(String id) {
        try {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
            Element sp = doc.createElement("p:sp");
            sp.setAttribute("id", id);
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
            () -> new CopyStyleCommand(1, SOURCE_SPID, TWO_TARGETS, null));
    }

    @Test
    @DisplayName("Rejects slide number zero or negative")
    void constructorRejectsInvalidSlide() {
        assertThrows(IllegalArgumentException.class,
            () -> new CopyStyleCommand(0, SOURCE_SPID, TWO_TARGETS, new StubPPTXOrchestrator()));
    }

    @Test
    @DisplayName("Rejects source SPID zero or negative")
    void constructorRejectsInvalidSourceSpid() {
        assertThrows(IllegalArgumentException.class,
            () -> new CopyStyleCommand(1, 0, TWO_TARGETS, new StubPPTXOrchestrator()));
    }

    @Test
    @DisplayName("Rejects null target list")
    void constructorRejectsNullTargetList() {
        assertThrows(IllegalArgumentException.class,
            () -> new CopyStyleCommand(1, SOURCE_SPID, null, new StubPPTXOrchestrator()));
    }

    @Test
    @DisplayName("Rejects empty target list")
    void constructorRejectsEmptyTargetList() {
        assertThrows(IllegalArgumentException.class,
            () -> new CopyStyleCommand(1, SOURCE_SPID, Collections.emptyList(),
                new StubPPTXOrchestrator()));
    }

    // ========== EXECUTE ==========

    @Test
    @DisplayName("Execute captures each target element before applying style")
    void executeCapturesTargetsThenAppliesStyle() {
        Element elem10 = createFakeElement("10");
        Element elem20 = createFakeElement("20");
        int[] captureCount = {0};
        boolean[] styleCopied = {false};

        PPTXOrchestrator stub = new StubPPTXOrchestrator() {
            @Override
            public ExecutionResult<Element> captureShapeElement(int slide, int spid) {
                captureCount[0]++;
                if (spid == 10) return ExecutionResult.success("CaptureShape", elem10);
                if (spid == 20) return ExecutionResult.success("CaptureShape", elem20);
                return ExecutionResult.failure("CaptureShape", "Unknown SPID");
            }

            @Override
            public ExecutionResult<Void> copyShapeStyle(int slide, int sourceSpid,
                                                         List<Integer> targetSpids) {
                assertEquals(2, captureCount[0], "All targets must be captured before style copy");
                assertEquals(SOURCE_SPID, sourceSpid);
                assertEquals(TWO_TARGETS, targetSpids);
                styleCopied[0] = true;
                return ExecutionResult.success("CopyStyle", null);
            }
        };

        CopyStyleCommand cmd = new CopyStyleCommand(1, SOURCE_SPID, TWO_TARGETS, stub);

        assertFalse(cmd.isExecuted());
        assertFalse(cmd.canUndo());

        cmd.execute();

        assertEquals(2, captureCount[0], "Should capture each target once");
        assertTrue(styleCopied[0]);
        assertTrue(cmd.isExecuted());
        assertTrue(cmd.canUndo());
    }

    @Test
    @DisplayName("Execute throws when a target capture fails")
    void executeThrowsOnCaptureFail() {
        PPTXOrchestrator stub = new StubPPTXOrchestrator() {
            @Override
            public ExecutionResult<Element> captureShapeElement(int slide, int spid) {
                if (spid == 20) return ExecutionResult.failure("CaptureShape", "Missing");
                return ExecutionResult.success("CaptureShape", createFakeElement(String.valueOf(spid)));
            }
        };

        CopyStyleCommand cmd = new CopyStyleCommand(1, SOURCE_SPID, TWO_TARGETS, stub);
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
                return ExecutionResult.success("CaptureShape", createFakeElement(String.valueOf(sp)));
            }

            @Override
            public ExecutionResult<Void> copyShapeStyle(int s, int src, List<Integer> targets) {
                return ExecutionResult.success("CopyStyle", null);
            }
        };

        CopyStyleCommand cmd = new CopyStyleCommand(1, SOURCE_SPID, TWO_TARGETS, stub);
        cmd.execute();
        assertThrows(CommandExecutionException.class, cmd::execute);
    }

    // ========== UNDO ==========

    @Test
    @DisplayName("Undo removes each restyled target and restores the captured original")
    void undoRestoresEachTarget() {
        Element elem10 = createFakeElement("10");
        Element elem20 = createFakeElement("20");
        Map<Integer, Element> restored = new HashMap<>();
        int[] removeCount = {0};

        PPTXOrchestrator stub = new StubPPTXOrchestrator() {
            @Override
            public ExecutionResult<Element> captureShapeElement(int s, int sp) {
                if (sp == 10) return ExecutionResult.success("Capture", elem10);
                if (sp == 20) return ExecutionResult.success("Capture", elem20);
                return ExecutionResult.failure("Capture", "Unknown");
            }

            @Override
            public ExecutionResult<Void> copyShapeStyle(int s, int src, List<Integer> targets) {
                return ExecutionResult.success("CopyStyle", null);
            }

            @Override
            public ExecutionResult<Void> removeShape(int slide, int spid) {
                removeCount[0]++;
                return ExecutionResult.success("RemoveShape", null);
            }

            @Override
            public ExecutionResult<Void> restoreShape(int slide, Element element) {
                // Record which element was restored
                if (element == elem10) restored.put(10, element);
                if (element == elem20) restored.put(20, element);
                return ExecutionResult.success("RestoreShape", null);
            }
        };

        CopyStyleCommand cmd = new CopyStyleCommand(1, SOURCE_SPID, TWO_TARGETS, stub);
        cmd.execute();

        cmd.undo();

        assertEquals(2, removeCount[0], "Each target should be removed once");
        assertSame(elem10, restored.get(10), "Original element for SPID 10 should be restored");
        assertSame(elem20, restored.get(20), "Original element for SPID 20 should be restored");
        assertFalse(cmd.isExecuted());
        assertFalse(cmd.canUndo());
    }

    @Test
    @DisplayName("Undo throws when not yet executed")
    void undoThrowsWhenNotExecuted() {
        CopyStyleCommand cmd = new CopyStyleCommand(1, SOURCE_SPID, TWO_TARGETS,
            new StubPPTXOrchestrator());
        assertThrows(CommandExecutionException.class, cmd::undo);
    }

    // ========== ACCESSORS ==========

    @Test
    @DisplayName("Getters return construction values")
    void gettersReturnCorrectValues() {
        CopyStyleCommand cmd = new CopyStyleCommand(2, 7, TWO_TARGETS, new StubPPTXOrchestrator());
        assertEquals(2, cmd.getSlideNumber());
        assertEquals(7, cmd.getSourceSpid());
        assertEquals(TWO_TARGETS, cmd.getTargetSpids());
    }
}
