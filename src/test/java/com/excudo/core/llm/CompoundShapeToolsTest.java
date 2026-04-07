package com.excudo.core.llm;

import com.excudo.core.model.*;
import com.excudo.core.orchestration.*;
import com.excudo.core.results.ExecutionResult;
import com.excudo.test.utils.StubPPTXOrchestrator;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

public class CompoundShapeToolsTest {

    static class RecordingOrchestrator extends StubPPTXOrchestrator {
        int nextSpid = 100;

        record AddShapeCall(int slideNumber, SlideShape.ShapeType shapeType,
                            ShapeGeometry geometry, String text, String name, ShapeStyle style) {}
        record SetTextBodyCall(int slideNumber, int spid, TextBody textBody) {}
        record SetStyleCall(int slideNumber, int spid, ShapeStyle style) {}

        final List<AddShapeCall> addShapeCalls = new ArrayList<>();
        final List<SetTextBodyCall> setTextBodyCalls = new ArrayList<>();
        final List<SetStyleCall> setStyleCalls = new ArrayList<>();

        @Override
        public ExecutionResult<Integer> addShape(int slideNumber, SlideShape.ShapeType shapeType,
                                                  ShapeGeometry geometry, String text, String shapeName) {
            addShapeCalls.add(new AddShapeCall(slideNumber, shapeType, geometry, text, shapeName, null));
            return ExecutionResult.success("AddShape", nextSpid++);
        }

        @Override
        public ExecutionResult<Integer> addShape(int slideNumber, SlideShape.ShapeType shapeType,
                                                  ShapeGeometry geometry, String text, String shapeName,
                                                  ShapeStyle style) {
            addShapeCalls.add(new AddShapeCall(slideNumber, shapeType, geometry, text, shapeName, style));
            return ExecutionResult.success("AddShape", nextSpid++);
        }

        @Override
        public ExecutionResult<Void> setTextBody(int slideNumber, int spid, TextBody textBody) {
            setTextBodyCalls.add(new SetTextBodyCall(slideNumber, spid, textBody));
            return ExecutionResult.success("SetTextBody", null);
        }

        @Override
        public ExecutionResult<Void> updateShapeStyle(int slideNumber, int spid, ShapeStyle style) {
            setStyleCalls.add(new SetStyleCall(slideNumber, spid, style));
            return ExecutionResult.success("UpdateShapeStyle", null);
        }
    }

    private CompoundShapeTools createTools(RecordingOrchestrator orch) {
        return new CompoundShapeTools(orch);
    }

    @Test
    public void createCodeBox_producesCorrectShapeCount() {
        RecordingOrchestrator orch = new RecordingOrchestrator();
        CompoundShapeTools tools = createTools(orch);

        String input = "{\"slideNumber\":1,\"code\":\"print('hello')\\nprint('world')\",\"language\":\"python\"}";
        String result = tools.createCodeBox(input);

        assertFalse("Should not return error", result.startsWith("Error"));
        assertEquals("Should create 2 shapes (line nums + code)", 2, orch.addShapeCalls.size());
        assertEquals("Should set 2 text bodies", 2, orch.setTextBodyCalls.size());
    }

    @Test
    public void createCodeBox_appliesSyntaxColoring() {
        RecordingOrchestrator orch = new RecordingOrchestrator();
        CompoundShapeTools tools = createTools(orch);

        String input = "{\"slideNumber\":1,\"code\":\"def foo():\\n    return 42\",\"language\":\"python\"}";
        tools.createCodeBox(input);

        // Code panel is the second setTextBody call (first is line numbers)
        TextBody codeBody = orch.setTextBodyCalls.get(1).textBody();
        assertNotNull(codeBody);
        assertTrue("Should have paragraphs", codeBody.getParagraphs().size() >= 2);

        // Collect all unique hex colors in the code body
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
        CompoundShapeTools tools = createTools(orch);

        String input = "{\"slideNumber\":1,\"code\":\"x = 1\",\"language\":\"python\"}";
        tools.createCodeBox(input);

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
        CompoundShapeTools tools = createTools(orch);

        String input = "{\"slideNumber\":1,\"code\":\"x = 1\",\"language\":\"text\"}";
        tools.createCodeBox(input);

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
        CompoundShapeTools tools = createTools(orch);

        String input = "{\"slideNumber\":1,\"code\":\"line1\\nline2\\nline3\",\"language\":\"text\"}";
        tools.createCodeBox(input);

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
}
