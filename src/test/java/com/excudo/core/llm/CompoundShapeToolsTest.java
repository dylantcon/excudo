package com.excudo.core.llm;

import com.excudo.core.commands.CommandExecutionException;
import com.excudo.core.commands.mutating.slide.CreateCodeBoxCommand;
import com.excudo.core.model.*;
import com.excudo.core.results.ExecutionResult;
import com.excudo.test.utils.StubPPTXOrchestrator;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Pins the behavior of the code-box compound primitive.
 *
 * <p>Pre-2026-04-24 this exercised {@code CompoundShapeTools.createCodeBox(json)},
 * a JSON-input adapter that no longer exists. The orchestration is
 * now {@link CreateCodeBoxCommand}; tests construct it directly with
 * typed args. The token-color and Prism4j tokenizer cases below still
 * cover the static helpers on {@link CompoundShapeTools} (the only
 * thing left in that class).
 */
public class CompoundShapeToolsTest {

    static class RecordingOrchestrator extends StubPPTXOrchestrator {
        int nextSpid = 100;

        record AddShapeCall(int slideNumber, SlideShape.ShapeType shapeType,
                            ShapeGeometry geometry, String text, String name, ShapeStyle style) {}
        record SetTextBodyCall(int slideNumber, int spid, TextBody textBody) {}
        record SetStyleCall(int slideNumber, int spid, ShapeStyle style) {}
        record RemoveShapeCall(int slideNumber, int spid) {}

        final List<AddShapeCall> addShapeCalls = new ArrayList<>();
        final List<SetTextBodyCall> setTextBodyCalls = new ArrayList<>();
        final List<SetStyleCall> setStyleCalls = new ArrayList<>();
        final List<RemoveShapeCall> removeShapeCalls = new ArrayList<>();

        // Failure injection knobs for transactional-safety testing.
        int failAddShapeAfterCalls = -1;        // -1 = never fail
        int failSetTextBodyAfterCalls = -1;     // -1 = never fail
        boolean groupShapesShouldFail = false;

        @Override
        public ExecutionResult<Integer> addShape(int slideNumber, SlideShape.ShapeType shapeType,
                                                  ShapeGeometry geometry, String text, String shapeName) {
            if (failAddShapeAfterCalls >= 0 && addShapeCalls.size() >= failAddShapeAfterCalls) {
                addShapeCalls.add(new AddShapeCall(slideNumber, shapeType, geometry, text, shapeName, null));
                return ExecutionResult.failure("AddShape", "injected failure");
            }
            addShapeCalls.add(new AddShapeCall(slideNumber, shapeType, geometry, text, shapeName, null));
            return ExecutionResult.success("AddShape", nextSpid++);
        }

        @Override
        public ExecutionResult<Integer> addShape(int slideNumber, SlideShape.ShapeType shapeType,
                                                  ShapeGeometry geometry, String text, String shapeName,
                                                  ShapeStyle style) {
            if (failAddShapeAfterCalls >= 0 && addShapeCalls.size() >= failAddShapeAfterCalls) {
                addShapeCalls.add(new AddShapeCall(slideNumber, shapeType, geometry, text, shapeName, style));
                return ExecutionResult.failure("AddShape", "injected failure");
            }
            addShapeCalls.add(new AddShapeCall(slideNumber, shapeType, geometry, text, shapeName, style));
            return ExecutionResult.success("AddShape", nextSpid++);
        }

        @Override
        public ExecutionResult<Void> setTextBody(int slideNumber, int spid, TextBody textBody) {
            if (failSetTextBodyAfterCalls >= 0 && setTextBodyCalls.size() >= failSetTextBodyAfterCalls) {
                setTextBodyCalls.add(new SetTextBodyCall(slideNumber, spid, textBody));
                return ExecutionResult.failure("SetTextBody", "injected failure");
            }
            setTextBodyCalls.add(new SetTextBodyCall(slideNumber, spid, textBody));
            return ExecutionResult.success("SetTextBody", null);
        }

        @Override
        public ExecutionResult<Void> updateShapeStyle(int slideNumber, int spid, ShapeStyle style) {
            setStyleCalls.add(new SetStyleCall(slideNumber, spid, style));
            return ExecutionResult.success("UpdateShapeStyle", null);
        }

        @Override
        public ExecutionResult<Void> removeShape(int slideNumber, int spid) {
            removeShapeCalls.add(new RemoveShapeCall(slideNumber, spid));
            return ExecutionResult.success("RemoveShape", null);
        }

        @Override
        public ExecutionResult<Integer> groupShapes(int slideNumber, java.util.List<Integer> spids) {
            if (groupShapesShouldFail) {
                return ExecutionResult.failure("GroupShapes", "injected failure");
            }
            return ExecutionResult.success("GroupShapes", nextSpid++);
        }
    }

    /** Construct a CreateCodeBoxCommand with default geometry. */
    private static CreateCodeBoxCommand cmd(RecordingOrchestrator orch, String code, String language) {
        return new CreateCodeBoxCommand(1, code, language, 838200L, 1825625L, null, null, orch);
    }

    @Test
    public void createCodeBox_producesCorrectShapeCount() {
        RecordingOrchestrator orch = new RecordingOrchestrator();
        cmd(orch, "print('hello')\nprint('world')", "python").execute();

        assertEquals("Should create 2 shapes (line nums + code)", 2, orch.addShapeCalls.size());
        assertEquals("Should set 2 text bodies", 2, orch.setTextBodyCalls.size());
    }

    @Test
    public void createCodeBox_appliesSyntaxColoring() {
        RecordingOrchestrator orch = new RecordingOrchestrator();
        cmd(orch, "def foo():\n    return 42", "python").execute();

        TextBody codeBody = orch.setTextBodyCalls.get(1).textBody();
        assertNotNull(codeBody);
        assertTrue("Should have paragraphs", codeBody.getParagraphs().size() >= 2);

        java.util.Set<String> colors = new java.util.HashSet<>();
        for (TextParagraph para : codeBody.getParagraphs()) {
            for (TextRun run : para.getRuns()) {
                if (run.getColor() != null && run.getColor().getHexVal() != null) {
                    colors.add(run.getColor().getHexVal());
                }
            }
        }
        assertTrue("Should have multiple different colors from Prism4j tokenization (got: " + colors + ")",
            colors.size() >= 2);
    }

    @Test
    public void createCodeBox_usesMonospaceFont() {
        RecordingOrchestrator orch = new RecordingOrchestrator();
        cmd(orch, "x = 1", "python").execute();

        for (RecordingOrchestrator.SetTextBodyCall call : orch.setTextBodyCalls) {
            for (TextParagraph para : call.textBody().getParagraphs()) {
                for (TextRun run : para.getRuns()) {
                    assertEquals("All runs should use Consolas", "Consolas", run.getFontFamily());
                }
            }
        }
    }

    @Test
    public void createCodeBox_appliesDarkFill() {
        RecordingOrchestrator orch = new RecordingOrchestrator();
        cmd(orch, "x = 1", "text").execute();

        for (RecordingOrchestrator.AddShapeCall call : orch.addShapeCalls) {
            assertNotNull("Shape style should not be null", call.style());
            assertNotNull("Fill should not be null", call.style().getFill());
            assertEquals("Fill should be dark background",
                "3F3F3F", call.style().getFill().getColor().getHexVal());
        }
    }

    @Test
    public void createCodeBox_lineNumbersRightAligned() {
        RecordingOrchestrator orch = new RecordingOrchestrator();
        cmd(orch, "line1\nline2\nline3", "text").execute();

        TextBody lineNumBody = orch.setTextBodyCalls.get(0).textBody();
        assertEquals("Should have 3 line number paragraphs", 3, lineNumBody.getParagraphs().size());

        for (int i = 0; i < lineNumBody.getParagraphs().size(); i++) {
            TextParagraph para = lineNumBody.getParagraphs().get(i);
            assertEquals("Line number paragraph should be right-aligned", "r", para.getAlignment());
            assertEquals("Line number text should match",
                String.valueOf(i + 1), para.getRuns().get(0).getText());
        }
    }

    // ---- Prism4j tokenizer tests ----

    @Test
    public void tokenizer_pythonKeywords() {
        List<CompoundShapeTools.Token> tokens =
            CompoundShapeTools.CodeTokenizer.tokenizeLine("def foo():", "python");

        assertFalse("Should produce tokens", tokens.isEmpty());
        // Find a keyword token
        boolean foundKeyword = tokens.stream()
            .anyMatch(t -> "keyword".equals(t.type()) && t.text().equals("def"));
        assertTrue("Should find 'def' as keyword token", foundKeyword);
    }

    @Test
    public void tokenizer_pythonStringLiterals() {
        List<CompoundShapeTools.Token> tokens =
            CompoundShapeTools.CodeTokenizer.tokenizeLine("x = \"hello\"", "python");

        boolean foundString = tokens.stream()
            .anyMatch(t -> "string".equals(t.type()) && t.text().contains("hello"));
        assertTrue("Should find a string token containing 'hello'", foundString);
    }

    @Test
    public void tokenizer_pythonComments() {
        List<CompoundShapeTools.Token> tokens =
            CompoundShapeTools.CodeTokenizer.tokenizeLine("x = 1 # comment", "python");

        boolean foundComment = tokens.stream()
            .anyMatch(t -> "comment".equals(t.type()) && t.text().contains("comment"));
        assertTrue("Should find a comment token", foundComment);
    }

    @Test
    public void tokenizer_javaAnnotation() {
        List<CompoundShapeTools.Token> tokens =
            CompoundShapeTools.CodeTokenizer.tokenizeLine("@Override", "java");

        boolean foundAnnotation = tokens.stream()
            .anyMatch(t -> "annotation".equals(t.type()) || "punctuation".equals(t.type()));
        assertTrue("Should tokenize @Override", foundAnnotation);
    }

    @Test
    public void tokenizer_javaNumbers() {
        List<CompoundShapeTools.Token> tokens =
            CompoundShapeTools.CodeTokenizer.tokenizeLine("int x = 42;", "java");

        boolean foundNumber = tokens.stream()
            .anyMatch(t -> "number".equals(t.type()));
        assertTrue("Should find a number token for '42'", foundNumber);
    }

    @Test
    public void tokenizer_javaClassNames() {
        List<CompoundShapeTools.Token> tokens =
            CompoundShapeTools.CodeTokenizer.tokenizeLine("new ArrayList()", "java");

        boolean foundClassName = tokens.stream()
            .anyMatch(t -> "class-name".equals(t.type()) || "function".equals(t.type()));
        assertTrue("Should find class-name or function token for 'ArrayList'", foundClassName);
    }

    @Test
    public void tokenizer_plainTextNoColoring() {
        List<CompoundShapeTools.Token> tokens =
            CompoundShapeTools.CodeTokenizer.tokenizeLine("def is just text", "text");

        assertEquals("Plain text should produce single null-type token", 1, tokens.size());
        assertNull("Token type should be null for plain text", tokens.get(0).type());
    }

    @Test
    public void tokenizer_multiLineCode() {
        List<List<CompoundShapeTools.Token>> lines =
            CompoundShapeTools.CodeTokenizer.tokenize("def foo():\n    return 42", "python");

        assertEquals("Should produce 2 lines", 2, lines.size());
        assertFalse("Line 1 should have tokens", lines.get(0).isEmpty());
        assertFalse("Line 2 should have tokens", lines.get(1).isEmpty());
    }

    @Test
    public void tokenizer_goLanguage() {
        List<CompoundShapeTools.Token> tokens =
            CompoundShapeTools.CodeTokenizer.tokenizeLine("func main() {", "go");

        boolean foundKeyword = tokens.stream()
            .anyMatch(t -> "keyword".equals(t.type()));
        assertTrue("Should find 'func' as keyword in Go", foundKeyword);
    }

    @Test
    public void tokenizer_sqlLanguage() {
        List<CompoundShapeTools.Token> tokens =
            CompoundShapeTools.CodeTokenizer.tokenizeLine("SELECT * FROM users WHERE id = 1;", "sql");

        boolean foundKeyword = tokens.stream()
            .anyMatch(t -> "keyword".equals(t.type()));
        assertTrue("Should find SQL keywords", foundKeyword);
    }

    @Test
    public void tokenColor_mapsAllTypes() {
        // Verify all known Prism4j token types map to non-default colors
        assertNotEquals(CompoundShapeTools.tokenColor(null),
            CompoundShapeTools.tokenColor("keyword"));
        assertNotEquals(CompoundShapeTools.tokenColor(null),
            CompoundShapeTools.tokenColor("string"));
        assertNotEquals(CompoundShapeTools.tokenColor(null),
            CompoundShapeTools.tokenColor("comment"));
        assertNotEquals(CompoundShapeTools.tokenColor(null),
            CompoundShapeTools.tokenColor("number"));
        assertNotEquals(CompoundShapeTools.tokenColor(null),
            CompoundShapeTools.tokenColor("function"));
    }

    // ===== Transactional safety on partial failure =====

    /**
     * The 2026-04-22 beta logs documented the worst-case API shape: an
     * error returned + partial state persisted. Agents had no way to
     * detect the divergence between the response shape ("Error: ...")
     * and the slide actually carrying a leaked LineNumbers panel. The
     * fix tracks every SPID we allocated and rolls them back on any
     * failure path so the slide is left exactly as it would have been
     * had the call been rejected outright.
     */
    @Test
    public void createCodeBox_rollsBackLineNumberPanelWhenCodePanelAddFails() {
        RecordingOrchestrator orch = new RecordingOrchestrator();
        // Allow the first addShape (line numbers), reject the second (code panel).
        orch.failAddShapeAfterCalls = 1;

        try {
            cmd(orch, "x = 1\ny = 2", "python").execute();
            fail("must surface error");
        } catch (CommandExecutionException expected) {
            // Line-numbers panel was created -- it MUST be removed.
            assertEquals("rollback removeShape called once for line-numbers SPID",
                1, orch.removeShapeCalls.size());
            assertEquals(1, orch.removeShapeCalls.get(0).slideNumber());
            // SPID 100 is the first allocated by RecordingOrchestrator.
            assertEquals(100, orch.removeShapeCalls.get(0).spid());
        }
    }

    @Test
    public void createCodeBox_rollsBackBothPanelsWhenCodeTextBodyFails() {
        RecordingOrchestrator orch = new RecordingOrchestrator();
        // Allow the line-numbers setTextBody, reject the code one.
        orch.failSetTextBodyAfterCalls = 1;

        try {
            cmd(orch, "x = 1\ny = 2", "python").execute();
            fail("must surface error");
        } catch (CommandExecutionException expected) {
            // Both panels were created before the failure -- both MUST be removed.
            assertEquals("rollback removes both SPIDs", 2, orch.removeShapeCalls.size());
            // Order: code panel first (LIFO removal preserves spTree integrity).
            assertEquals(101, orch.removeShapeCalls.get(0).spid());
            assertEquals(100, orch.removeShapeCalls.get(1).spid());
        }
    }

    @Test
    public void createCodeBox_doesNotRollBackOnGroupingFailure() {
        // Partial-success: the panels exist as siblings. The user can
        // still position them. Don't undo useful work because the group
        // affordance failed -- just communicate the partial result.
        RecordingOrchestrator orch = new RecordingOrchestrator();
        orch.groupShapesShouldFail = true;

        CreateCodeBoxCommand command = cmd(orch, "x = 1", "python");
        command.execute();

        assertNull("group SPID null when grouping failed", command.getGroupSpid());
        assertEquals("no rollback on grouping failure",
            0, orch.removeShapeCalls.size());
    }

    @Test
    public void createCodeBox_succeedsWithoutAnyRollback() {
        // Sanity: the happy path does not call removeShape.
        RecordingOrchestrator orch = new RecordingOrchestrator();
        cmd(orch, "x = 1", "python").execute();

        assertEquals(0, orch.removeShapeCalls.size());
    }

    @Test
    public void createCodeBox_undoRemovesGroupAndChildren() {
        // The Command-pattern win that the old wrapper couldn't deliver:
        // user-initiated undo after a successful create_code_box removes
        // every SPID the command allocated, not just the group.
        RecordingOrchestrator orch = new RecordingOrchestrator();
        CreateCodeBoxCommand command = cmd(orch, "x = 1", "python");
        command.execute();
        assertNotNull("group SPID set on success", command.getGroupSpid());

        command.undo();

        // 3 SPIDs allocated: line-num panel, code panel, group.
        // Undo removes group first, then panels in reverse.
        assertEquals(3, orch.removeShapeCalls.size());
    }
}
